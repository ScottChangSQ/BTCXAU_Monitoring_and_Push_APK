/*
 * 交易命令状态机单测，负责锁定检查、确认、提交、超时和结算的状态边界。
 * 与 TradeCommandStateMachine 实现一起保证页面不会把未确认状态误判成已下单。
 */
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.ExecutionError;
import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeReceipt;

import org.json.JSONObject;
import org.junit.Test;

public class TradeCommandStateMachineTest {

    @Test
    public void beginCheckingShouldIgnoreRepeatedClick() {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-repeat-check"));

        assertTrue(machine.beginChecking());
        assertFalse(machine.beginChecking());
        assertEquals(TradeCommandStateMachine.Step.CHECKING, machine.getStep());
    }

    @Test
    public void checkExecutableShouldMoveToConfirmingNotSubmitting() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-check-pass"));
        machine.beginChecking();

        boolean handled = machine.onCheckCompleted(new TradeCheckResult(
                "req-check-pass",
                "OPEN_MARKET",
                "netting",
                "EXECUTABLE",
                null,
                new JSONObject().put("retcode", 0),
                1L
        ));

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.CONFIRMING, machine.getStep());
        assertNull(machine.getReceipt());
        assertFalse(machine.isOrderAccepted());
    }

    @Test
    public void emptyCheckResultShouldMoveToTimeoutState() {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-empty-check"));
        machine.beginChecking();

        boolean handled = machine.onCheckCompleted(null);

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, machine.getStep());
        assertEquals("TRADE_CHECK_EMPTY", machine.getError().getCode());
    }

    @Test
    public void unknownCheckStatusShouldMoveToTimeoutState() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-unknown-check"));
        machine.beginChecking();

        boolean handled = machine.onCheckCompleted(new TradeCheckResult(
                "req-unknown-check",
                "OPEN_MARKET",
                "netting",
                "UNKNOWN",
                null,
                new JSONObject().put("retcode", 0),
                1L
        ));

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, machine.getStep());
    }

    @Test
    public void submitShouldIgnoreRepeatedClickWhileSubmitting() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-repeat-submit"));
        moveToConfirming(machine, "req-repeat-submit");

        assertTrue(machine.beginSubmitting());
        assertFalse(machine.beginSubmitting());
        assertEquals(TradeCommandStateMachine.Step.SUBMITTING, machine.getStep());
    }

    @Test
    public void submitTimeoutShouldMoveToTimeoutState() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-timeout"));
        moveToConfirming(machine, "req-timeout");
        machine.beginSubmitting();

        boolean handled = machine.onSubmitTimeout("submit timeout");

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, machine.getStep());
        assertEquals("TRADE_SUBMIT_TIMEOUT", machine.getError().getCode());
    }

    @Test
    public void submitRejectedShouldMoveToRejectedState() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-rejected"));
        moveToConfirming(machine, "req-rejected");
        machine.beginSubmitting();

        boolean handled = machine.onSubmitCompleted(new TradeReceipt(
                "req-rejected",
                "OPEN_MARKET",
                "netting",
                "FAILED",
                ExecutionError.of("TRADE_INSUFFICIENT_MARGIN", "no money"),
                new JSONObject().put("retcode", 10019),
                null,
                false,
                2L
        ));

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.REJECTED, machine.getStep());
        assertEquals("TRADE_INSUFFICIENT_MARGIN", machine.getError().getCode());
    }

    @Test
    public void failedDuplicateReceiptShouldStayRejected() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-failed-duplicate"));
        moveToConfirming(machine, "req-failed-duplicate");
        machine.beginSubmitting();

        boolean handled = machine.onSubmitCompleted(new TradeReceipt(
                "req-failed-duplicate",
                "OPEN_MARKET",
                "netting",
                "FAILED",
                ExecutionError.of("TRADE_DUPLICATE_SUBMISSION", "duplicate"),
                new JSONObject().put("retcode", 10030),
                new JSONObject().put("order", 7001).put("deal", 7002),
                true,
                2L
        ));

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.REJECTED, machine.getStep());
        assertFalse(machine.isOrderAccepted());
    }

    @Test
    public void rejectedShouldNotBePromotedToSettledOrAccepted() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-rejected-settle"));
        moveToConfirming(machine, "req-rejected-settle");
        machine.beginSubmitting();
        machine.onSubmitCompleted(new TradeReceipt(
                "req-rejected-settle",
                "OPEN_MARKET",
                "netting",
                "FAILED",
                ExecutionError.of("TRADE_INSUFFICIENT_MARGIN", "no money"),
                new JSONObject().put("retcode", 10019),
                null,
                false,
                2L
        ));

        boolean settledHandled = machine.markSettled();

        assertFalse(settledHandled);
        assertEquals(TradeCommandStateMachine.Step.REJECTED, machine.getStep());
        assertFalse(machine.isOrderAccepted());
    }

    @Test
    public void timeoutShouldNotBePromotedToSettledOrAccepted() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-timeout-settle"));
        moveToConfirming(machine, "req-timeout-settle");
        machine.beginSubmitting();
        machine.onSubmitTimeout("timeout");

        boolean settledHandled = machine.markSettled();

        assertFalse(settledHandled);
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, machine.getStep());
        assertFalse(machine.isOrderAccepted());
    }

    @Test
    public void submitStatusTimeoutShouldMoveToTimeoutWithoutErrorCode() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-status-timeout"));
        moveToConfirming(machine, "req-status-timeout");
        machine.beginSubmitting();

        boolean handled = machine.onSubmitCompleted(new TradeReceipt(
                "req-status-timeout",
                "OPEN_MARKET",
                "netting",
                "TIMEOUT",
                null,
                new JSONObject().put("retcode", 0),
                null,
                false,
                3L
        ));

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, machine.getStep());
    }

    @Test
    public void submitStatusUnknownShouldMoveToTimeoutWithoutErrorCode() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-status-unknown"));
        moveToConfirming(machine, "req-status-unknown");
        machine.beginSubmitting();

        boolean handled = machine.onSubmitCompleted(new TradeReceipt(
                "req-status-unknown",
                "OPEN_MARKET",
                "netting",
                "UNKNOWN",
                null,
                new JSONObject().put("retcode", 0),
                null,
                false,
                3L
        ));

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.TIMEOUT, machine.getStep());
    }

    @Test
    public void idempotentDuplicateShouldBeAcceptedNotRejected() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-dup-idempotent"));
        moveToConfirming(machine, "req-dup-idempotent");
        machine.beginSubmitting();

        boolean handled = machine.onSubmitCompleted(new TradeReceipt(
                "req-dup-idempotent",
                "OPEN_MARKET",
                "netting",
                "DUPLICATE",
                ExecutionError.of("TRADE_DUPLICATE_SUBMISSION", "duplicate"),
                new JSONObject().put("retcode", 0),
                new JSONObject().put("order", 7001).put("deal", 7002),
                true,
                3L
        ));

        assertTrue(handled);
        assertEquals(TradeCommandStateMachine.Step.ACCEPTED, machine.getStep());
        assertTrue(machine.isOrderAccepted());
        assertFalse(machine.isSettled());
    }

    @Test
    public void acceptedShouldNotBeSettledUntilSettlementEventArrives() throws Exception {
        TradeCommandStateMachine machine = new TradeCommandStateMachine(buildCommand("req-accepted"));
        moveToConfirming(machine, "req-accepted");
        machine.beginSubmitting();

        boolean acceptedHandled = machine.onSubmitCompleted(new TradeReceipt(
                "req-accepted",
                "OPEN_MARKET",
                "netting",
                "ACCEPTED",
                null,
                new JSONObject().put("retcode", 0),
                new JSONObject().put("order", 7788).put("deal", 8899),
                false,
                3L
        ));

        assertTrue(acceptedHandled);
        assertEquals(TradeCommandStateMachine.Step.ACCEPTED, machine.getStep());
        assertFalse(machine.isSettled());

        boolean settledHandled = machine.markSettled();

        assertTrue(settledHandled);
        assertEquals(TradeCommandStateMachine.Step.SETTLED, machine.getStep());
        assertTrue(machine.isSettled());
    }

    private static TradeCommand buildCommand(String requestId) {
        return new TradeCommand(
                requestId,
                "acc-001",
                "BTCUSD",
                "OPEN_MARKET",
                0.1,
                65000.0,
                64000.0,
                68000.0
        );
    }

    private static void moveToConfirming(TradeCommandStateMachine machine, String requestId) throws Exception {
        assertTrue(machine.beginChecking());
        assertTrue(machine.onCheckCompleted(new TradeCheckResult(
                requestId,
                "OPEN_MARKET",
                "netting",
                "EXECUTABLE",
                null,
                new JSONObject().put("retcode", 0),
                1L
        )));
        assertEquals(TradeCommandStateMachine.Step.CONFIRMING, machine.getStep());
    }
}
