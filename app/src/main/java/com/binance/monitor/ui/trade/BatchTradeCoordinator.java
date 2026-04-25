/*
 * 批量交易协调器，负责把批量提交、结果追认和交易后强刷收在一条正式边界里。
 * 第三阶段只允许图表页和复杂动作规划层通过这里消费 batch 契约，不允许页面层自行循环单笔提交。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradeItemResult;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.BatchTradeReceipt;
import com.binance.monitor.data.model.v2.trade.ExecutionError;
import com.binance.monitor.data.model.v2.trade.TradeAuditEntry;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

import java.util.ArrayList;
import java.util.List;

public class BatchTradeCoordinator {
    private final BatchTradeGateway batchTradeGateway;
    private final AccountRefreshGateway accountRefreshGateway;
    private final int maxStrongRefreshAttempts;
    @Nullable
    private final TradeAuditStore auditStore;
    @NonNull
    private final TradeRiskGuard.ConfigProvider riskConfigProvider;

    // 创建批量交易协调器。
    public BatchTradeCoordinator(BatchTradeGateway batchTradeGateway,
                                 AccountRefreshGateway accountRefreshGateway,
                                 int maxStrongRefreshAttempts) {
        this(batchTradeGateway, accountRefreshGateway, maxStrongRefreshAttempts, TradeRiskGuard.Config::defaultConfig, null);
    }

    // 创建带本地审计存储的批量交易协调器。
    public BatchTradeCoordinator(BatchTradeGateway batchTradeGateway,
                                 AccountRefreshGateway accountRefreshGateway,
                                 int maxStrongRefreshAttempts,
                                 @Nullable TradeAuditStore auditStore) {
        this(batchTradeGateway, accountRefreshGateway, maxStrongRefreshAttempts, TradeRiskGuard.Config::defaultConfig, auditStore);
    }

    public BatchTradeCoordinator(BatchTradeGateway batchTradeGateway,
                                 AccountRefreshGateway accountRefreshGateway,
                                 int maxStrongRefreshAttempts,
                                 @NonNull TradeRiskGuard.ConfigProvider riskConfigProvider,
                                 @Nullable TradeAuditStore auditStore) {
        this.batchTradeGateway = batchTradeGateway;
        this.accountRefreshGateway = accountRefreshGateway;
        this.maxStrongRefreshAttempts = Math.max(1, maxStrongRefreshAttempts);
        this.riskConfigProvider = riskConfigProvider;
        this.auditStore = auditStore;
    }

    // 提交一批正式交易计划，并在受理后拉取最新账户缓存。
    public ExecutionResult submit(BatchTradePlan plan) {
        validatePlan(plan);
        TradeRiskGuard.Decision riskDecision = TradeRiskGuard.evaluateBatch(plan, riskConfigProvider.getConfig());
        if (!riskDecision.isAllowed()) {
            BatchTradeReceipt receipt = failedReceipt(plan, "TRADE_BATCH_RISK_LIMIT", riskDecision.getMessage());
            recordBatchAudit(plan, "batch_result", receipt.getStatus(), receipt.getError(), riskDecision.getMessage(), receipt.getServerTime());
            return new ExecutionResult(UiState.FAILED, receipt, null, riskDecision.getMessage());
        }
        BatchTradeReceipt receipt;
        try {
            receipt = enrichReceipt(batchTradeGateway.submit(plan), plan);
        } catch (Exception submitException) {
            receipt = resolveReceiptByBatchId(plan);
            if (receipt == null) {
                recordBatchAudit(plan, "batch_submit", "RESULT_UNCONFIRMED", new ExecutionError("TRADE_BATCH_RESULT_UNKNOWN", resolveExceptionMessage(submitException, "批量结果未确认"), null), resolveExceptionMessage(submitException, "批量结果未确认"), 0L);
                return new ExecutionResult(
                        UiState.RESULT_UNCONFIRMED,
                        failedReceipt(plan, "TRADE_BATCH_RESULT_UNKNOWN", resolveExceptionMessage(submitException, "批量结果未确认")),
                        null,
                        resolveExceptionMessage(submitException, "批量结果未确认")
                );
            }
        }
        recordBatchAudit(plan, "batch_submit", receipt.getStatus(), receipt.getError(), resolveReceiptMessage(receipt), receipt.getServerTime());
        UiState uiState = resolveUiState(receipt);
        if (uiState != UiState.ACCEPTED && uiState != UiState.PARTIAL) {
            recordBatchAudit(plan, "batch_result", receipt.getStatus(), receipt.getError(), resolveReceiptMessage(receipt), receipt.getServerTime());
            return new ExecutionResult(uiState, receipt, null, resolveReceiptMessage(receipt));
        }
        TradeSessionVolumeMemory.getInstance().rememberSuccessfulBatch(plan);

        AccountStatsPreloadManager.Cache latestCache = null;
        Exception lastException = null;
        for (int attempt = 0; attempt < maxStrongRefreshAttempts; attempt++) {
            try {
                latestCache = accountRefreshGateway.fetchFullForUi(AccountTimeRange.ALL);
                if (latestCache != null) {
                    break;
                }
            } catch (Exception exception) {
                lastException = exception;
            }
        }
        String message = lastException == null
                ? resolveReceiptMessage(receipt)
                : resolveExceptionMessage(lastException, resolveReceiptMessage(receipt));
        recordBatchAudit(plan, "batch_result", receipt.getStatus(), receipt.getError(), message, receipt.getServerTime());
        return new ExecutionResult(uiState, receipt, latestCache, message);
    }

    // 根据 batchId 追认真实回执，避免网络异常时误把整批结果留成未知态。
    @Nullable
    private BatchTradeReceipt resolveReceiptByBatchId(@Nullable BatchTradePlan plan) {
        if (plan == null || isBlank(plan.getBatchId())) {
            return null;
        }
        try {
            return enrichReceipt(batchTradeGateway.result(plan.getBatchId()), plan);
        } catch (Exception ignored) {
            return null;
        }
    }

    // 用本地计划回填服务端结果的显示文案，避免页面层再做手写映射。
    private BatchTradeReceipt enrichReceipt(@Nullable BatchTradeReceipt receipt, @Nullable BatchTradePlan plan) {
        if (receipt == null) {
            return failedReceipt(plan, "TRADE_BATCH_RESULT_UNKNOWN", "批量结果缺失");
        }
        if (plan == null || plan.getItems().isEmpty() || receipt.getItems().isEmpty()) {
            return receipt;
        }
        List<BatchTradeItemResult> enrichedItems = new ArrayList<>();
        for (BatchTradeItemResult itemResult : receipt.getItems()) {
            String displayLabel = resolveItemDisplayLabel(plan, itemResult == null ? "" : itemResult.getItemId());
            if (itemResult == null || isBlank(displayLabel)) {
                enrichedItems.add(itemResult);
                continue;
            }
            enrichedItems.add(itemResult.withDisplayLabel(displayLabel));
        }
        return receipt.withItems(enrichedItems);
    }

    // 按 itemId 从计划里找本地显示文案。
    private String resolveItemDisplayLabel(@NonNull BatchTradePlan plan, @Nullable String itemId) {
        for (BatchTradeItem item : plan.getItems()) {
            if (item == null) {
                continue;
            }
            if ((itemId == null ? "" : itemId).equals(item.getItemId())) {
                return item.getDisplayLabel();
            }
        }
        return "";
    }

    // 将整体状态映射成界面状态。
    private UiState resolveUiState(@Nullable BatchTradeReceipt receipt) {
        if (receipt == null) {
            return UiState.RESULT_UNCONFIRMED;
        }
        if (receipt.isAccepted()) {
            return UiState.ACCEPTED;
        }
        if (receipt.isPartial()) {
            return UiState.PARTIAL;
        }
        if (receipt.isFailed()) {
            return UiState.FAILED;
        }
        return UiState.RESULT_UNCONFIRMED;
    }

    // 统一回执提示文案。
    private String resolveReceiptMessage(@Nullable BatchTradeReceipt receipt) {
        if (receipt == null) {
            return "批量结果未确认";
        }
        if (receipt.isAccepted()) {
            return "批量交易已受理";
        }
        if (receipt.isPartial()) {
            return "批量交易部分成功";
        }
        if (receipt.getError() != null && !isBlank(receipt.getError().getMessage())) {
            return receipt.getError().getMessage();
        }
        return "批量交易失败";
    }

    // 构建统一失败回执。
    private BatchTradeReceipt failedReceipt(@Nullable BatchTradePlan plan,
                                           @NonNull String errorCode,
                                           @NonNull String message) {
        return BatchTradeReceipt.failed(
                plan == null ? "" : plan.getBatchId(),
                plan == null ? "" : plan.getStrategy(),
                plan == null ? "" : plan.getAccountMode(),
                new ExecutionError(errorCode, message, null),
                null
        );
    }

    // 最小校验批量计划边界。
    private void validatePlan(@Nullable BatchTradePlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan 不能为空");
        }
        if (isBlank(plan.getBatchId())) {
            throw new IllegalArgumentException("batchId 不能为空");
        }
        if (plan.getItems().isEmpty()) {
            throw new IllegalArgumentException("items 不能为空");
        }
    }

    // 统一解析异常提示。
    private String resolveExceptionMessage(@Nullable Exception exception, @NonNull String fallback) {
        if (exception == null || isBlank(exception.getMessage())) {
            return fallback;
        }
        return exception.getMessage().trim();
    }

    // 判断字符串是否为空。
    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private void recordBatchAudit(@Nullable BatchTradePlan plan,
                                  @NonNull String stage,
                                  @NonNull String status,
                                  @Nullable ExecutionError error,
                                  @Nullable String message,
                                  long serverTime) {
        if (auditStore == null || plan == null) {
            return;
        }
        String action = plan.getItems().isEmpty() || plan.getItems().get(0) == null
                ? "BATCH"
                : plan.getItems().get(0).getAction();
        String symbol = plan.getItems().isEmpty() || plan.getItems().get(0) == null || plan.getItems().get(0).getCommand() == null
                ? ""
                : plan.getItems().get(0).getCommand().getSymbol();
        auditStore.record(new TradeAuditEntry(
                plan.getBatchId(),
                "batch",
                action,
                symbol,
                plan.getAccountMode(),
                stage,
                status,
                error == null ? "" : error.getCode(),
                message == null ? "" : message,
                plan.getSummary(),
                serverTime,
                0L
        ));
    }

    public interface BatchTradeGateway {
        BatchTradeReceipt submit(BatchTradePlan plan) throws Exception;

        BatchTradeReceipt result(String batchId) throws Exception;
    }

    public interface AccountRefreshGateway {
        AccountStatsPreloadManager.Cache fetchFullForUi(AccountTimeRange range) throws Exception;
    }

    public enum UiState {
        ACCEPTED,
        PARTIAL,
        FAILED,
        RESULT_UNCONFIRMED
    }

    public static class ExecutionResult {
        private final UiState uiState;
        private final BatchTradeReceipt receipt;
        private final AccountStatsPreloadManager.Cache latestCache;
        private final String message;

        // 构造批量执行结果。
        public ExecutionResult(UiState uiState,
                               BatchTradeReceipt receipt,
                               @Nullable AccountStatsPreloadManager.Cache latestCache,
                               String message) {
            this.uiState = uiState;
            this.receipt = receipt;
            this.latestCache = latestCache;
            this.message = message == null ? "" : message;
        }

        // 返回界面状态。
        public UiState getUiState() {
            return uiState;
        }

        // 返回回执。
        public BatchTradeReceipt getReceipt() {
            return receipt;
        }

        // 返回最新缓存。
        public AccountStatsPreloadManager.Cache getLatestCache() {
            return latestCache;
        }

        // 返回结果文案。
        public String getMessage() {
            return message;
        }
    }
}
