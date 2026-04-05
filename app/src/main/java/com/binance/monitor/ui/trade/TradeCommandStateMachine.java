/*
 * 交易命令状态机，明确从草稿到检查、确认、提交、受理、结算的固定状态流。
 * 保证“检查成功不等于已下单”“已受理不等于已结算”。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.ExecutionError;
import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeReceipt;

public class TradeCommandStateMachine {

    public enum Step {
        DRAFT,
        CHECKING,
        CONFIRMING,
        SUBMITTING,
        ACCEPTED,
        REJECTED,
        TIMEOUT,
        SETTLED
    }

    private final TradeCommand command;
    private Step step = Step.DRAFT;
    @Nullable
    private TradeCheckResult checkResult;
    @Nullable
    private TradeReceipt receipt;
    @Nullable
    private ExecutionError error;

    // 创建状态机并绑定本次交易命令。
    public TradeCommandStateMachine(TradeCommand command) {
        this.command = command;
    }

    // 开始检查阶段，重复点击会被忽略。
    public synchronized boolean beginChecking() {
        if (step != Step.DRAFT) {
            return false;
        }
        step = Step.CHECKING;
        error = null;
        return true;
    }

    // 处理检查结果，成功进入确认，失败进入拒绝。
    public synchronized boolean onCheckCompleted(@Nullable TradeCheckResult result) {
        if (step != Step.CHECKING) {
            return false;
        }
        checkResult = result;
        if (result == null) {
            error = ExecutionError.of("TRADE_CHECK_EMPTY", "检查结果为空");
            step = Step.REJECTED;
            return true;
        }
        error = result.getError();
        if (result.isExecutable()) {
            step = Step.CONFIRMING;
        } else {
            step = Step.REJECTED;
        }
        return true;
    }

    // 开始提交阶段，重复点击会被忽略。
    public synchronized boolean beginSubmitting() {
        if (step != Step.CONFIRMING) {
            return false;
        }
        step = Step.SUBMITTING;
        return true;
    }

    // 处理提交结果，受理进入 ACCEPTED，拒单进入 REJECTED，超时进入 TIMEOUT。
    public synchronized boolean onSubmitCompleted(@Nullable TradeReceipt submitReceipt) {
        if (step != Step.SUBMITTING) {
            return false;
        }
        receipt = submitReceipt;
        if (submitReceipt == null) {
            error = ExecutionError.of("TRADE_SUBMIT_EMPTY", "提交回执为空");
            step = Step.TIMEOUT;
            return true;
        }
        error = submitReceipt.getError();
        if (submitReceipt.isAccepted()) {
            step = Step.ACCEPTED;
            return true;
        }
        if (isIdempotentDuplicate(submitReceipt)) {
            step = Step.ACCEPTED;
            return true;
        }
        if (isTimeoutStatus(submitReceipt.getStatus()) || isTimeoutError(error)) {
            step = Step.TIMEOUT;
            return true;
        }
        step = Step.REJECTED;
        return true;
    }

    // 提交超时时进入 TIMEOUT，避免界面落入灰区。
    public synchronized boolean onSubmitTimeout(String message) {
        if (step != Step.SUBMITTING) {
            return false;
        }
        error = ExecutionError.of("TRADE_SUBMIT_TIMEOUT", message == null ? "提交超时" : message);
        step = Step.TIMEOUT;
        return true;
    }

    // 在回执与持仓同步完成后标记为已结算。
    public synchronized boolean markSettled() {
        if (step != Step.ACCEPTED) {
            return false;
        }
        step = Step.SETTLED;
        return true;
    }

    // 返回当前步骤。
    public synchronized Step getStep() {
        return step;
    }

    // 返回交易命令。
    public TradeCommand getCommand() {
        return command;
    }

    // 返回检查结果。
    @Nullable
    public synchronized TradeCheckResult getCheckResult() {
        return checkResult;
    }

    // 返回提交回执。
    @Nullable
    public synchronized TradeReceipt getReceipt() {
        return receipt;
    }

    // 返回当前错误。
    @Nullable
    public synchronized ExecutionError getError() {
        return error;
    }

    // 判断是否已经受理。
    public synchronized boolean isOrderAccepted() {
        return step == Step.ACCEPTED || step == Step.SETTLED;
    }

    // 判断是否已经结算完成。
    public synchronized boolean isSettled() {
        return step == Step.SETTLED;
    }

    // 判断错误是否属于超时类别。
    private static boolean isTimeoutError(@Nullable ExecutionError executionError) {
        if (executionError == null) {
            return false;
        }
        String code = executionError.getCode();
        return "TRADE_TIMEOUT".equalsIgnoreCase(code)
                || "TRADE_SUBMIT_TIMEOUT".equalsIgnoreCase(code)
                || "TRADE_RESULT_UNKNOWN".equalsIgnoreCase(code);
    }

    // 判断提交状态是否属于待确认超时分支。
    private static boolean isTimeoutStatus(@Nullable String status) {
        if (status == null) {
            return false;
        }
        return "TIMEOUT".equalsIgnoreCase(status)
                || "UNKNOWN".equalsIgnoreCase(status)
                || "PENDING".equalsIgnoreCase(status);
    }

    // 判断是否是“已按幂等接收”的重复提交回执。
    private static boolean isIdempotentDuplicate(@Nullable TradeReceipt receipt) {
        if (receipt == null || !receipt.isIdempotent()) {
            return false;
        }
        if ("DUPLICATE".equalsIgnoreCase(receipt.getStatus())) {
            return true;
        }
        ExecutionError executionError = receipt.getError();
        return executionError != null
                && "TRADE_DUPLICATE_SUBMISSION".equalsIgnoreCase(executionError.getCode());
    }
}
