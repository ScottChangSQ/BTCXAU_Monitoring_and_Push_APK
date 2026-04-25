/*
 * 批量交易协调器单测，负责锁定第三阶段 batch 只消费正式 batch 契约。
 * 与 BatchTradeCoordinator、BatchTradePlan、BatchTradeReceipt 一起保证复杂交易不回退到页面层循环单笔提交。
 */
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradeItemResult;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.BatchTradeReceipt;
import com.binance.monitor.data.model.v2.trade.ExecutionError;
import com.binance.monitor.data.model.v2.trade.TradeAuditEntry;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BatchTradeCoordinatorTest {

    @Test
    public void submitBatchShouldExposePerItemResults() throws Exception {
        FakeBatchTradeGateway gateway = new FakeBatchTradeGateway();
        gateway.submitReceipt = BatchTradeReceipt.partial(
                "batch-close-001",
                "BEST_EFFORT",
                "hedging",
                Arrays.asList(
                        BatchTradeItemResult.accepted("item-1", "CLOSE_POSITION", "平仓 BTCUSD #1"),
                        BatchTradeItemResult.rejected(
                                "item-2",
                                "CLOSE_POSITION",
                                "平仓 BTCUSD #2",
                                new ExecutionError("TRADE_INVALID_POSITION", "position missing", null)
                        )
                )
        );
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        refreshGateway.cache = new AccountStatsPreloadManager.Cache(
                true,
                null,
                "acc-1",
                "Demo-Server",
                "remote",
                "gateway",
                123L,
                "",
                456L
        );
        BatchTradeCoordinator coordinator = new BatchTradeCoordinator(gateway, refreshGateway, 2);

        BatchTradeCoordinator.ExecutionResult result = coordinator.submit(buildCloseBatchPlan());

        assertEquals(BatchTradeCoordinator.UiState.PARTIAL, result.getUiState());
        assertEquals(1, refreshGateway.fetchCount);
        assertEquals(2, result.getReceipt().getItems().size());
        assertEquals("item-2", result.getReceipt().getItems().get(1).getItemId());
        assertEquals("平仓 BTCUSD #2", result.getReceipt().getItems().get(1).getDisplayLabel());
        assertNotNull(result.getLatestCache());
    }

    @Test
    public void submitBatchShouldNotRefreshWhenReceiptIsFailed() throws Exception {
        FakeBatchTradeGateway gateway = new FakeBatchTradeGateway();
        gateway.submitReceipt = BatchTradeReceipt.failed(
                "batch-close-002",
                "BEST_EFFORT",
                "hedging",
                new ExecutionError("TRADE_BATCH_INVALID_ID", "bad batch", null),
                Arrays.asList(
                        BatchTradeItemResult.rejected(
                                "item-1",
                                "CLOSE_POSITION",
                                "平仓 BTCUSD #1",
                                new ExecutionError("TRADE_BATCH_INVALID_ID", "bad batch", null)
                        )
                )
        );
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        BatchTradeCoordinator coordinator = new BatchTradeCoordinator(gateway, refreshGateway, 2);

        BatchTradeCoordinator.ExecutionResult result = coordinator.submit(buildCloseBatchPlan());

        assertEquals(BatchTradeCoordinator.UiState.FAILED, result.getUiState());
        assertEquals(0, refreshGateway.fetchCount);
        assertEquals("TRADE_BATCH_INVALID_ID", result.getReceipt().getError().getCode());
    }

    @Test
    public void submitBatchShouldRecordPartialAndFailedAuditEntries() throws Exception {
        FakeBatchTradeGateway gateway = new FakeBatchTradeGateway();
        gateway.submitReceipt = BatchTradeReceipt.partial(
                "batch-close-001",
                "BEST_EFFORT",
                "hedging",
                Arrays.asList(
                        BatchTradeItemResult.accepted("item-1", "CLOSE_POSITION", "平仓 BTCUSD #1"),
                        BatchTradeItemResult.rejected(
                                "item-2",
                                "CLOSE_POSITION",
                                "平仓 BTCUSD #2",
                                new ExecutionError("TRADE_INVALID_POSITION", "position missing", null)
                        )
                )
        );
        FakeAccountRefreshGateway refreshGateway = new FakeAccountRefreshGateway();
        refreshGateway.cache = new AccountStatsPreloadManager.Cache(
                true,
                null,
                "acc-1",
                "Demo-Server",
                "remote",
                "gateway",
                123L,
                "",
                456L
        );
        TradeAuditStore auditStore = TradeAuditStore.createInMemory(() -> 300L);
        BatchTradeCoordinator coordinator = new BatchTradeCoordinator(gateway, refreshGateway, 2, auditStore);

        coordinator.submit(buildCloseBatchPlan());

        List<TradeAuditEntry> entries = auditStore.lookup("batch-close-001");
        assertEquals(2, entries.size());
        assertEquals("batch_result", entries.get(0).getStage());
        assertEquals("PARTIAL", entries.get(0).getStatus());
        assertEquals("batch_submit", entries.get(1).getStage());
    }

    private static BatchTradePlan buildCloseBatchPlan() throws Exception {
        JSONObject firstExtras = new JSONObject();
        firstExtras.put("positionTicket", 11L);
        JSONObject secondExtras = new JSONObject();
        secondExtras.put("positionTicket", 12L);
        return new BatchTradePlan(
                "batch-close-001",
                "BEST_EFFORT",
                "hedging",
                "批量平仓 BTCUSD",
                Arrays.asList(
                        new BatchTradeItem(
                                "item-1",
                                "平仓 BTCUSD #1",
                                TradeCommandFactory.closePosition("acc-1", "BTCUSD", 11L, 0.10d, 0d),
                                firstExtras
                        ),
                        new BatchTradeItem(
                                "item-2",
                                "平仓 BTCUSD #2",
                                TradeCommandFactory.closePosition("acc-1", "BTCUSD", 12L, 0.20d, 0d),
                                secondExtras
                        )
                )
        );
    }

    private static final class FakeBatchTradeGateway implements BatchTradeCoordinator.BatchTradeGateway {
        private BatchTradeReceipt submitReceipt;

        @Override
        public BatchTradeReceipt submit(BatchTradePlan plan) {
            return submitReceipt;
        }

        @Override
        public BatchTradeReceipt result(String batchId) {
            return submitReceipt;
        }
    }

    private static final class FakeAccountRefreshGateway implements BatchTradeCoordinator.AccountRefreshGateway {
        private int fetchCount;
        private AccountStatsPreloadManager.Cache cache;

        @Override
        public AccountStatsPreloadManager.Cache fetchFullForUi(AccountTimeRange range) {
            fetchCount++;
            return cache;
        }
    }
}
