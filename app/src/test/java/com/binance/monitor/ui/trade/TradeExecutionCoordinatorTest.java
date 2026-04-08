/*
 * 交易执行协调器单测，负责锁定统一确认、提交流程和强一致刷新收敛规则。
 * 与 TradeExecutionCoordinator、TradeCommandStateMachine 一起保证第一阶段最小交易闭环可回归。
 */
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.ExecutionError;
import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeReceipt;
import com.binance.monitor.ui.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.account.AccountTimeRange;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

public class TradeExecutionCoordinatorTest {

    @Test
    public void prepareExecutionShouldRequireConfirmationByDefault() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-confirm");
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                new FakeAccountRefreshGateway(),
                new TradeConfirmDialogController(),
                2
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-confirm"));

        assertEquals(TradeExecutionCoordinator.UiState.AWAITING_CONFIRMATION, prepared.getUiState());
        assertTrue(prepared.requiresConfirmation());
        assertFalse(prepared.isOneClickTradingEnabled());
        assertEquals(TradeCommandStateMachine.Step.CONFIRMING, prepared.getStateMachine().getStep());
    }

    @Test
    public void prepareExecutionShouldReturnUnconfirmedWhenCheckResultIsMissing() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = null;
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                new FakeAccountRefreshGateway(),
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-check-empty"));

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, prepared.getUiState());
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, prepared.getStateMachine().getStep());
        assertFalse(prepared.requiresConfirmation());
    }

    @Test
    public void submitAfterConfirmationShouldSettleWhenMarketOrderRefreshesWithoutPendingOrderChange() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-settle");
        gateway.submitReceipt = acceptedReceipt("req-settle", 7001L, 7002L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(1001L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,020.00", "$120.00"),
                positions("BTCUSD", 0.20d, 9002L, 0L),
                pendingOrders(),
                trades(1001L, 7002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                3
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-settle"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.SETTLED, result.getUiState());
        assertTrue(result.isSettled());
        assertEquals(1, refreshGateway.fetchCount);
        assertEquals(TradeCommandStateMachine.Step.SETTLED, result.getStateMachine().getStep());
        assertNotNull(result.getLatestCache());
        assertEquals(2000L, result.getLatestCache().getUpdatedAt());
        assertEquals(AccountTimeRange.ALL, refreshGateway.lastRange);
    }

    @Test
    public void submitAfterConfirmationShouldStayUnconfirmedWhenReceiptIdsDisappearAndOnlySameSymbolStructureChanges() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-settle-without-receipt-ids");
        gateway.submitReceipt = acceptedReceipt("req-settle-without-receipt-ids", 7001L, 7002L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(1001L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,020.00", "$120.00"),
                positions("BTCUSD", 0.20d, 9002L, 0L),
                pendingOrders(),
                trades(1001L, 1002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-settle-without-receipt-ids"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
        assertEquals(TradeCommandStateMachine.Step.ACCEPTED, result.getStateMachine().getStep());
    }

    @Test
    public void submitAfterConfirmationShouldSettleWhenModifySltpOnlyChangesPriceFields() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-modify-sltp");
        gateway.submitReceipt = acceptedReceipt("req-modify-sltp", 7001L, 0L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 7001L, 0, 0d, 64000d, 68000d)),
                pendingOrders(),
                trades(1001L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,000.00", "$100.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 7001L, 0, 0d, 64100d, 68100d)),
                pendingOrders(),
                trades(1001L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-modify-sltp", "MODIFY_SLTP", 64100.0d, 68100.0d));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.SETTLED, result.getUiState());
        assertTrue(result.isSettled());
        assertEquals(AccountTimeRange.ALL, refreshGateway.lastRange);
    }

    @Test
    public void submitAfterConfirmationShouldKeepUnknownStateWhenRefreshDoesNotCoverMarginAndEquity() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-missing-overview");
        gateway.submitReceipt = acceptedReceipt("req-missing-overview");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders("BTCUSD", 0.10d, 0L, 8001L),
                trades(1001L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                Collections.singletonList(new AccountMetric("累计盈亏", "+$20.00")),
                positions("BTCUSD", 0.20d, 9002L, 0L),
                pendingOrders(),
                trades(1001L, 1002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-missing-overview"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
        assertFalse(result.isSafeState());
        assertEquals(TradeCommandStateMachine.Step.ACCEPTED, result.getStateMachine().getStep());
    }

    @Test
    public void submitAfterConfirmationShouldKeepUnknownStateWhenGatewayTimesOut() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-timeout");
        gateway.submitReceipt = timeoutReceipt("req-timeout");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                2
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-timeout"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
        assertFalse(result.isSafeState());
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, result.getStateMachine().getStep());
        assertEquals(0, refreshGateway.fetchCount);
    }

    @Test
    public void submitAfterConfirmationShouldRecoverAcceptedResultByRequestIdAfterSubmitException() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-recover-by-result");
        gateway.submitException = new RuntimeException("socket timeout");
        gateway.resultReceipt = acceptedReceipt("req-recover-by-result", 7001L, 7002L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(1001L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,020.00", "$120.00"),
                positions("BTCUSD", 0.20d, 9002L, 0L),
                pendingOrders(),
                trades(1001L, 7002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-recover-by-result"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.SETTLED, result.getUiState());
        assertTrue(result.isSettled());
        assertEquals(1, gateway.resultCount);
        assertEquals(1, refreshGateway.fetchCount);
        assertEquals(TradeCommandStateMachine.Step.SETTLED, result.getStateMachine().getStep());
    }

    @Test
    public void submitAfterConfirmationShouldStayUnconfirmedWhenBaselineIsMissing() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-no-baseline");
        gateway.submitReceipt = acceptedReceipt("req-no-baseline");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,020.00", "$120.00"),
                positions("BTCUSD", 0.20d, 9002L, 0L),
                pendingOrders(),
                trades(1001L, 1002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-no-baseline"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
        assertFalse(result.isSafeState());
    }

    @Test
    public void submitAfterConfirmationShouldNotSettleFromFailedRefreshCacheWhenBaselineIsMissing() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-failed-refresh");
        gateway.submitReceipt = acceptedReceipt("req-failed-refresh");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        refreshGateway.enqueue(cache(
                false,
                2000L,
                metrics("$1,020.00", "$120.00"),
                positions("BTCUSD", 0.20d, 9002L, 0L),
                pendingOrders(),
                trades(1001L, 1002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-failed-refresh"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
    }

    @Test
    public void submitAfterConfirmationShouldExposeRejectReasonAndReturnToSafeState() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-rejected");
        gateway.submitReceipt = rejectedReceipt("req-rejected", "保证金不足");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                2
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-rejected"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.REJECTED, result.getUiState());
        assertTrue(result.isSafeState());
        assertEquals("保证金不足", result.getMessage());
        assertEquals(TradeCommandStateMachine.Step.REJECTED, result.getStateMachine().getStep());
        assertEquals(0, refreshGateway.fetchCount);
    }

    @Test
    public void submitAfterConfirmationShouldNotCallGatewaySubmitWhenTradeIsNotConfirmed() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-skip-confirm");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );
        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-skip-confirm"));

        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertTrue(result.isSafeState());
        assertEquals(0, gateway.submitCount);
        assertEquals(0, refreshGateway.fetchCount);
    }

    @Test
    public void submitAfterConfirmationShouldPreserveAcceptedStateWhenCalledAgainAfterAcceptance() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-accepted-repeat");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-accepted-repeat"));
        prepared.markConfirmed();
        TradeCommandStateMachine machine = prepared.getStateMachine();
        assertTrue(machine.beginSubmitting());
        assertTrue(machine.onSubmitCompleted(acceptedReceipt("req-accepted-repeat")));

        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
        assertFalse(result.isSafeState());
        assertEquals(TradeCommandStateMachine.Step.ACCEPTED, result.getStateMachine().getStep());
        assertEquals("结果未确认，请等待后续刷新", result.getMessage());
        assertEquals(0, gateway.submitCount);
        assertEquals(0, refreshGateway.fetchCount);
    }

    @Test
    public void submitAfterConfirmationShouldPreserveSettledStateWhenCalledAgainAfterSettlement() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-settled-repeat");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-settled-repeat"));
        prepared.markConfirmed();
        TradeCommandStateMachine machine = prepared.getStateMachine();
        assertTrue(machine.beginSubmitting());
        assertTrue(machine.onSubmitCompleted(acceptedReceipt("req-settled-repeat")));
        assertTrue(machine.markSettled());

        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.SETTLED, result.getUiState());
        assertTrue(result.isSettled());
        assertFalse(result.isSafeState());
        assertEquals(TradeCommandStateMachine.Step.SETTLED, result.getStateMachine().getStep());
        assertEquals("交易已受理并完成账户刷新", result.getMessage());
        assertEquals(0, gateway.submitCount);
        assertEquals(0, refreshGateway.fetchCount);
    }

    @Test
    public void prepareExecutionShouldReturnUnconfirmedWhenCheckStatusIsUnknown() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = new TradeCheckResult(
                "req-check-unknown",
                "OPEN_MARKET",
                "netting",
                "UNKNOWN",
                null,
                new JSONObject().put("retcode", 0),
                1L
        );
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                new FakeAccountRefreshGateway(),
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-check-unknown"));

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, prepared.getUiState());
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, prepared.getStateMachine().getStep());
    }

    @Test
    public void prepareExecutionShouldReturnUnconfirmedWhenCheckThrows() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkException = new RuntimeException("check crashed");
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                new FakeAccountRefreshGateway(),
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-check-throws"));

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, prepared.getUiState());
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, prepared.getStateMachine().getStep());
        assertEquals("check crashed", prepared.getMessage());
    }

    @Test
    public void prepareExecutionShouldUseUnconfirmedMessageWhenCheckStatusIsUnknownWithoutError() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = new TradeCheckResult(
                "req-check-unknown-message",
                "OPEN_MARKET",
                "netting",
                "UNKNOWN",
                null,
                new JSONObject().put("retcode", 0),
                1L
        );
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                new FakeAccountRefreshGateway(),
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-check-unknown-message"));

        assertEquals("检查结果未确认", prepared.getMessage());
    }

    @Test
    public void submitAfterConfirmationShouldNotSettleWhenOnlyTradeOrderChanges() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-reordered-trades");
        gateway.submitReceipt = acceptedReceipt("req-reordered-trades");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(1001L, 1002L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(1002L, 1001L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-reordered-trades"));
        prepared.markConfirmed();

        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
    }

    @Test
    public void submitAfterConfirmationShouldReturnUnconfirmedWhenSubmitThrows() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-submit-throws");
        gateway.submitException = new RuntimeException("submit crashed");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-submit-throws"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, result.getStateMachine().getStep());
        assertEquals("submit crashed", result.getMessage());
        assertEquals(0, refreshGateway.fetchCount);
    }

    @Test
    public void submitAfterConfirmationShouldReturnUnconfirmedWhenRefreshThrowsAfterAccepted() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-refresh-throws");
        gateway.submitReceipt = acceptedReceipt("req-refresh-throws");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        refreshGateway.fetchException = new RuntimeException("refresh crashed");
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(1001L)
        );
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-refresh-throws"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertEquals(TradeCommandStateMachine.Step.ACCEPTED, result.getStateMachine().getStep());
        assertEquals("refresh crashed", result.getMessage());
    }

    @Test
    public void submitAfterConfirmationShouldPreserveRejectedStateWhenPreparedTradeAlreadyRejected() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = new TradeCheckResult(
                "req-already-rejected",
                "OPEN_MARKET",
                "netting",
                "FAILED",
                ExecutionError.of("TRADE_INSUFFICIENT_MARGIN", "保证金不足"),
                new JSONObject().put("retcode", 10019),
                1L
        );
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-already-rejected"));
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, null);

        assertEquals(TradeExecutionCoordinator.UiState.REJECTED, result.getUiState());
        assertTrue(result.isSafeState());
        assertEquals("保证金不足", result.getMessage());
        assertEquals(0, gateway.submitCount);
    }

    @Test
    public void submitAfterConfirmationShouldNotSettleWhenOnlyOverviewFormatChanges() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-overview-format");
        gateway.submitReceipt = acceptedReceipt("req-overview-format", 7001L, 7002L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(7002L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1000.0", "$100"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(7002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-overview-format"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
    }

    @Test
    public void submitAfterConfirmationShouldNotSettleFromMarketNoiseOnly() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-market-noise");
        gateway.submitReceipt = acceptedReceipt("req-market-noise", 7001L, 7002L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 7001L, 0, 0d, 64000d, 68000d)),
                pendingOrders(),
                trades(7002L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,030.00", "$130.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 7001L, 0, 0d, 64000d, 68000d)),
                pendingOrders(),
                trades(7002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-market-noise"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
    }

    @Test
    public void submitAfterConfirmationShouldIgnoreOtherSymbolChanges() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-other-symbol");
        gateway.submitReceipt = acceptedReceipt("req-other-symbol", 7001L, 7002L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(1001L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,050.00", "$150.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 0L, 0),
                        position("XAUUSD", 0.20d, 9100L, 0L, 0)),
                pendingOrders(),
                mergeTrades(trades(1001L), trades("XAUUSD", 7002L))
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-other-symbol"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
    }

    @Test
    public void submitAfterConfirmationShouldStayUnconfirmedWhenAcceptedReceiptHasNoReferenceIds() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-accepted-no-reference");
        gateway.submitReceipt = acceptedReceiptWithoutReference("req-accepted-no-reference");
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions("BTCUSD", 0.10d, 9001L, 0L),
                pendingOrders(),
                trades(1001L)
        );
        refreshGateway.enqueue(cache(
                2000L,
                metrics("$1,020.00", "$120.00"),
                positions("BTCUSD", 0.20d, 9002L, 0L),
                pendingOrders(),
                trades(1001L, 1002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-accepted-no-reference"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.RESULT_UNCONFIRMED, result.getUiState());
        assertFalse(result.isSettled());
        assertEquals(TradeCommandStateMachine.Step.ACCEPTED, result.getStateMachine().getStep());
    }

    @Test
    public void submitAfterConfirmationShouldSettleIdempotentDuplicateWhenLatestSnapshotAlreadyContainsReceipt() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-duplicate-settled");
        gateway.submitReceipt = duplicateAcceptedReceipt("req-duplicate-settled", 7001L, 7002L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 7001L, 0, 0d, 64000d, 68000d)),
                pendingOrders(),
                trades(7002L)
        );
        refreshGateway.enqueue(cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 7001L, 0, 0d, 64000d, 68000d)),
                pendingOrders(),
                trades(7002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-duplicate-settled"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.SETTLED, result.getUiState());
        assertTrue(result.isSettled());
        assertEquals(TradeCommandStateMachine.Step.SETTLED, result.getStateMachine().getStep());
    }

    @Test
    public void submitAfterConfirmationShouldSettleIdempotentDuplicateWhenErrorCodeMarksDuplicate() throws Exception {
        FakeTradeGateway gateway = new FakeTradeGateway();
        gateway.checkResult = executableCheck("req-duplicate-error-code");
        gateway.submitReceipt = duplicateAcceptedReceiptWithAcceptedStatus("req-duplicate-error-code", 7001L, 7002L);
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        AccountStatsPreloadManager.Cache baselineCache = cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 7001L, 0, 0d, 64000d, 68000d)),
                pendingOrders(),
                trades(7002L)
        );
        refreshGateway.enqueue(cache(
                1000L,
                metrics("$1,000.00", "$100.00"),
                positions(position("BTCUSD", 0.10d, 9001L, 7001L, 0, 0d, 64000d, 68000d)),
                pendingOrders(),
                trades(7002L)
        ));
        TradeExecutionCoordinator coordinator = new TradeExecutionCoordinator(
                gateway,
                refreshGateway,
                new TradeConfirmDialogController(),
                1
        );

        TradeExecutionCoordinator.PreparedTrade prepared =
                coordinator.prepareExecution(buildCommand("req-duplicate-error-code"));
        prepared.markConfirmed();
        TradeExecutionCoordinator.ExecutionResult result =
                coordinator.submitAfterConfirmation(prepared, baselineCache);

        assertEquals(TradeExecutionCoordinator.UiState.SETTLED, result.getUiState());
        assertTrue(result.isSettled());
        assertEquals(TradeCommandStateMachine.Step.SETTLED, result.getStateMachine().getStep());
    }

    private static TradeCommand buildCommand(String requestId) {
        return buildCommand(requestId, "OPEN_MARKET", 64000.0d, 68000.0d);
    }

    private static TradeCommand buildCommand(String requestId, String action, double sl, double tp) {
        return new TradeCommand(
                requestId,
                "acc-001",
                "BTCUSD",
                action,
                0.1d,
                65000.0d,
                sl,
                tp
        );
    }

    private static TradeCheckResult executableCheck(String requestId) throws Exception {
        return new TradeCheckResult(
                requestId,
                "OPEN_MARKET",
                "netting",
                "EXECUTABLE",
                null,
                new JSONObject().put("retcode", 0),
                1L
        );
    }

    private static TradeReceipt acceptedReceipt(String requestId) throws Exception {
        return acceptedReceipt(requestId, 7001L, 7002L);
    }

    private static TradeReceipt acceptedReceipt(String requestId, long orderId, long dealId) throws Exception {
        return new TradeReceipt(
                requestId,
                "OPEN_MARKET",
                "netting",
                "ACCEPTED",
                null,
                new JSONObject().put("retcode", 0),
                new JSONObject().put("order", orderId).put("deal", dealId),
                false,
                2L
        );
    }

    private static TradeReceipt acceptedReceiptWithoutReference(String requestId) throws Exception {
        return new TradeReceipt(
                requestId,
                "OPEN_MARKET",
                "netting",
                "ACCEPTED",
                null,
                new JSONObject().put("retcode", 0),
                new JSONObject(),
                false,
                2L
        );
    }

    private static TradeReceipt duplicateAcceptedReceipt(String requestId, long orderId, long dealId) throws Exception {
        return new TradeReceipt(
                requestId,
                "OPEN_MARKET",
                "netting",
                "DUPLICATE",
                ExecutionError.of("TRADE_DUPLICATE_SUBMISSION", "duplicate"),
                new JSONObject().put("retcode", 0),
                new JSONObject().put("order", orderId).put("deal", dealId),
                true,
                2L
        );
    }

    private static TradeReceipt duplicateAcceptedReceiptWithAcceptedStatus(String requestId, long orderId, long dealId) throws Exception {
        return new TradeReceipt(
                requestId,
                "OPEN_MARKET",
                "netting",
                "ACCEPTED",
                ExecutionError.of("TRADE_DUPLICATE_SUBMISSION", "duplicate"),
                new JSONObject().put("retcode", 0),
                new JSONObject().put("order", orderId).put("deal", dealId),
                true,
                2L
        );
    }

    private static TradeReceipt timeoutReceipt(String requestId) throws Exception {
        return new TradeReceipt(
                requestId,
                "OPEN_MARKET",
                "netting",
                "TIMEOUT",
                ExecutionError.of("TRADE_TIMEOUT", "结果未确认"),
                new JSONObject().put("retcode", 0),
                null,
                false,
                2L
        );
    }

    private static TradeReceipt rejectedReceipt(String requestId, String message) throws Exception {
        return new TradeReceipt(
                requestId,
                "OPEN_MARKET",
                "netting",
                "FAILED",
                ExecutionError.of("TRADE_INSUFFICIENT_MARGIN", message),
                new JSONObject().put("retcode", 10019),
                null,
                false,
                2L
        );
    }

    private static AccountStatsPreloadManager.Cache cache(long updatedAt,
                                                          List<AccountMetric> overviewMetrics,
                                                          List<PositionItem> positions,
                                                          List<PositionItem> pendingOrders,
                                                          List<TradeRecordItem> trades) {
        return cache(true, updatedAt, overviewMetrics, positions, pendingOrders, trades);
    }

    private static AccountStatsPreloadManager.Cache cache(boolean connected,
                                                          long updatedAt,
                                                          List<AccountMetric> overviewMetrics,
                                                          List<PositionItem> positions,
                                                          List<PositionItem> pendingOrders,
                                                          List<TradeRecordItem> trades) {
        AccountSnapshot snapshot = new AccountSnapshot(
                overviewMetrics,
                new ArrayList<>(),
                new ArrayList<>(),
                positions,
                pendingOrders,
                trades,
                new ArrayList<>()
        );
        return new AccountStatsPreloadManager.Cache(
                connected,
                snapshot,
                "acc-001",
                "server-a",
                "V2网关",
                "http://gateway",
                updatedAt,
                connected ? "" : "refresh failed",
                System.currentTimeMillis(),
                "rev-" + trades.size()
        );
    }

    private static List<AccountMetric> metrics(String equity, String margin) {
        return Arrays.asList(
                new AccountMetric("总资产", equity),
                new AccountMetric("保证金", margin)
        );
    }

    private static List<PositionItem> positions(String code, double quantity, long positionTicket, long orderId) {
        return Collections.singletonList(position(code, quantity, positionTicket, orderId, 0));
    }

    private static List<PositionItem> positions(PositionItem item) {
        return Collections.singletonList(item);
    }

    private static List<PositionItem> positions(PositionItem... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static List<PositionItem> pendingOrders(String code, double quantity, long positionTicket, long orderId) {
        return Collections.singletonList(position(code, quantity, positionTicket, orderId, 1));
    }

    private static List<PositionItem> pendingOrders() {
        return new ArrayList<>();
    }

    private static PositionItem position(String code,
                                         double quantity,
                                         long positionTicket,
                                         long orderId,
                                         int pendingCount) {
        return position(code, quantity, positionTicket, orderId, pendingCount, 121d, 0d, 0d);
    }

    private static PositionItem position(String code,
                                         double quantity,
                                         long positionTicket,
                                         long orderId,
                                         int pendingCount,
                                         double pendingPrice,
                                         double takeProfit,
                                         double stopLoss) {
        return new PositionItem(
                code,
                code,
                "Buy",
                positionTicket,
                orderId,
                quantity,
                quantity,
                100d,
                120d,
                quantity * 120d,
                0.5d,
                10d,
                10d,
                0.2d,
                pendingCount > 0 ? quantity : 0d,
                pendingCount,
                pendingPrice,
                takeProfit,
                stopLoss,
                0d
        );
    }

    private static List<TradeRecordItem> trades(long... dealTickets) {
        return trades("BTCUSD", dealTickets);
    }

    private static List<TradeRecordItem> trades(String code, long... dealTickets) {
        List<TradeRecordItem> items = new ArrayList<>();
        long closeTime = 1_700_000_000_000L;
        for (long dealTicket : dealTickets) {
            items.add(new TradeRecordItem(
                    closeTime + dealTicket,
                    code,
                    code,
                    "Buy",
                    65000d,
                    0.1d,
                    6500d,
                    0d,
                    "",
                    10d,
                    closeTime + dealTicket - 1000L,
                    closeTime + dealTicket,
                    0d,
                    64000d,
                    65000d,
                    dealTicket,
                    dealTicket + 1000L,
                    dealTicket + 2000L,
                    1
            ));
        }
        return items;
    }

    private static List<TradeRecordItem> mergeTrades(List<TradeRecordItem> first, List<TradeRecordItem> second) {
        List<TradeRecordItem> merged = new ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            merged.addAll(second);
        }
        return merged;
    }

    private static class FakeTradeGateway implements TradeExecutionCoordinator.TradeGateway {
        private TradeCheckResult checkResult;
        private TradeReceipt submitReceipt;
        private TradeReceipt resultReceipt;
        private RuntimeException checkException;
        private RuntimeException submitException;
        private int submitCount;
        private int resultCount;

        @Override
        public TradeCheckResult check(TradeCommand command) {
            if (checkException != null) {
                throw checkException;
            }
            return checkResult;
        }

        @Override
        public TradeReceipt submit(TradeCommand command) {
            submitCount++;
            if (submitException != null) {
                throw submitException;
            }
            return submitReceipt;
        }

        @Override
        public TradeReceipt result(String requestId) {
            resultCount++;
            return resultReceipt;
        }
    }

    private static class FakeAccountRefreshGateway implements TradeExecutionCoordinator.AccountRefreshGateway {
        private final Queue<AccountStatsPreloadManager.Cache> queuedCaches = new ArrayDeque<>();
        private RuntimeException fetchException;
        private int fetchCount;
        private AccountTimeRange lastRange;

        private void enqueue(AccountStatsPreloadManager.Cache cache) {
            queuedCaches.offer(cache);
        }

        @Override
        public AccountStatsPreloadManager.Cache fetchForUi(AccountTimeRange range) {
            fetchCount++;
            lastRange = range;
            if (fetchException != null) {
                throw fetchException;
            }
            AccountStatsPreloadManager.Cache cache = queuedCaches.isEmpty() ? null : queuedCaches.poll();
            if (cache == null) {
                return null;
            }
            return new AccountStatsPreloadManager.Cache(
                    cache.isConnected(),
                    cache.getSnapshot(),
                    cache.getAccount(),
                    cache.getServer(),
                    cache.getSource(),
                    cache.getGateway(),
                    cache.getUpdatedAt(),
                    cache.getError(),
                    System.currentTimeMillis(),
                    cache.getHistoryRevision()
            );
        }
    }
}
