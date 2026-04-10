/*
 * 交易执行协调器，负责把检查、统一确认、提交和交易后强一致刷新串成同一条闭环。
 * 第一阶段只实现最小规则：默认先确认、提交后立即强刷、超时保留未确认、拒单回安全态。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.ExecutionError;
import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.model.v2.trade.TradeReceipt;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TradeExecutionCoordinator {
    private final TradeGateway tradeGateway;
    private final AccountRefreshGateway accountRefreshGateway;
    private final TradeConfirmDialogController confirmDialogController;
    private final int maxStrongRefreshAttempts;

    // 创建交易执行协调器。
    public TradeExecutionCoordinator(TradeGateway tradeGateway,
                                     AccountRefreshGateway accountRefreshGateway,
                                     TradeConfirmDialogController confirmDialogController,
                                     int maxStrongRefreshAttempts) {
        this.tradeGateway = tradeGateway;
        this.accountRefreshGateway = accountRefreshGateway;
        this.confirmDialogController = confirmDialogController;
        this.maxStrongRefreshAttempts = Math.max(1, maxStrongRefreshAttempts);
    }

    // 先执行检查并进入统一确认态。
    public PreparedTrade prepareExecution(TradeCommand command) {
        TradeCommandStateMachine stateMachine = new TradeCommandStateMachine(command);
        stateMachine.beginChecking();
        TradeCheckResult checkResult;
        try {
            checkResult = tradeGateway.check(command);
        } catch (Exception exception) {
            stateMachine.onCheckTimeout(resolveExceptionMessage(exception, "检查结果未确认"));
            return new PreparedTrade(
                    UiState.RESULT_UNCONFIRMED,
                    stateMachine,
                    false,
                    false,
                    resolveErrorMessage(stateMachine.getError(), "检查结果未确认"),
                    false
            );
        }
        stateMachine.onCheckCompleted(checkResult);
        if (stateMachine.getStep() != TradeCommandStateMachine.Step.CONFIRMING) {
            return new PreparedTrade(
                    mapRejectedUiState(stateMachine),
                    stateMachine,
                    false,
                    false,
                    resolveCheckFailureMessage(stateMachine),
                    false
            );
        }
        TradeConfirmDialogController.Decision decision =
                confirmDialogController.buildDecision(command, checkResult);
        return new PreparedTrade(
                UiState.AWAITING_CONFIRMATION,
                stateMachine,
                decision.isConfirmationRequired(),
                decision.isOneClickTradingEnabled(),
                decision.getMessage(),
                !decision.isConfirmationRequired()
        );
    }

    // 用户确认后提交交易，并在受理后立即触发强一致刷新。
    public ExecutionResult submitAfterConfirmation(PreparedTrade preparedTrade,
                                                   @Nullable AccountStatsPreloadManager.Cache baselineCache) {
        if (preparedTrade == null) {
            throw new IllegalArgumentException("preparedTrade 不能为空");
        }
        TradeCommandStateMachine stateMachine = preparedTrade.getStateMachine();
        if (stateMachine == null) {
            return new ExecutionResult(
                    UiState.RESULT_UNCONFIRMED,
                    stateMachine,
                    null,
                    false,
                    true,
                    "交易尚未确认，请重新确认"
            );
        }
        if (preparedTrade.requiresConfirmation() && !preparedTrade.isConfirmed()) {
            return new ExecutionResult(
                    UiState.RESULT_UNCONFIRMED,
                    stateMachine,
                    null,
                    false,
                    true,
                    "交易尚未确认，请重新确认"
            );
        }
        if (!stateMachine.beginSubmitting()) {
            return buildBlockedExecutionResult(stateMachine);
        }
        TradeReceipt receipt;
        try {
            receipt = tradeGateway.submit(stateMachine.getCommand());
        } catch (Exception exception) {
            receipt = resolveSubmitReceiptByRequestId(stateMachine.getCommand());
            if (receipt == null) {
                stateMachine.onSubmitTimeout(resolveExceptionMessage(exception, "结果未确认"));
                return new ExecutionResult(
                        UiState.RESULT_UNCONFIRMED,
                        stateMachine,
                        null,
                        false,
                        false,
                        resolveErrorMessage(stateMachine.getError(), "结果未确认")
                );
            }
        }
        stateMachine.onSubmitCompleted(receipt);

        if (stateMachine.getStep() == TradeCommandStateMachine.Step.REJECTED) {
            return new ExecutionResult(
                    UiState.REJECTED,
                    stateMachine,
                    null,
                    false,
                    true,
                    resolveErrorMessage(stateMachine.getError(), "交易被拒绝")
            );
        }
        if (stateMachine.getStep() == TradeCommandStateMachine.Step.TIMEOUT) {
            return new ExecutionResult(
                    UiState.RESULT_UNCONFIRMED,
                    stateMachine,
                    null,
                    false,
                    false,
                    resolveErrorMessage(stateMachine.getError(), "结果未确认")
            );
        }

        AccountStatsPreloadManager.Cache latestCache = null;
        long refreshStartedAt = System.currentTimeMillis();
        for (int attempt = 0; attempt < maxStrongRefreshAttempts; attempt++) {
            try {
                latestCache = accountRefreshGateway.fetchForUi(AccountTimeRange.ALL);
            } catch (Exception exception) {
                return new ExecutionResult(
                        UiState.RESULT_UNCONFIRMED,
                        stateMachine,
                        latestCache,
                        false,
                        false,
                        resolveExceptionMessage(exception, "结果未确认，请等待后续刷新")
                );
            }
            if (hasConverged(stateMachine.getCommand(), stateMachine.getReceipt(), baselineCache, latestCache, refreshStartedAt)) {
                stateMachine.markSettled();
                return new ExecutionResult(
                        UiState.SETTLED,
                        stateMachine,
                        latestCache,
                        true,
                        false,
                        "交易已受理并完成账户刷新"
                );
            }
        }

        return new ExecutionResult(
                UiState.RESULT_UNCONFIRMED,
                stateMachine,
                latestCache,
                false,
                false,
                "结果未确认，请等待后续刷新"
        );
    }

    // 提交链路异常时，按 requestId 向服务端追认真实回执，避免把已受理交易误留在未确认态。
    @Nullable
    private TradeReceipt resolveSubmitReceiptByRequestId(@Nullable TradeCommand command) {
        if (command == null) {
            return null;
        }
        String requestId = safe(command.getRequestId());
        if (requestId.isEmpty()) {
            return null;
        }
        try {
            return tradeGateway.result(requestId);
        } catch (Exception ignored) {
            return null;
        }
    }

    // 判断强刷结果是否已满足第一阶段收敛标准。
    private boolean hasConverged(@Nullable TradeCommand command,
                                 @Nullable TradeReceipt receipt,
                                 @Nullable AccountStatsPreloadManager.Cache baselineCache,
                                 @Nullable AccountStatsPreloadManager.Cache latestCache,
                                 long refreshStartedAt) {
        if (latestCache == null || !latestCache.isConnected() || latestCache.getSnapshot() == null) {
            return false;
        }
        if (!hasRequiredRefreshSections(latestCache.getSnapshot())) {
            return false;
        }
        if (latestCache.getFetchedAt() < refreshStartedAt) {
            return false;
        }
        AccountSnapshot latestSnapshot = latestCache.getSnapshot();
        ReceiptReference receiptReference = buildReceiptReference(command, receipt);
        boolean latestReceiptMatched = hasReceiptMatch(receiptReference, null, latestSnapshot);
        if (isAcceptedDuplicateReceipt(receipt)) {
            return latestReceiptMatched;
        }
        if (receipt != null && receipt.isAccepted() && !receiptReference.hasReference()) {
            return false;
        }
        if (baselineCache == null || baselineCache.getSnapshot() == null) {
            return false;
        }
        AccountSnapshot baselineSnapshot = baselineCache.getSnapshot();
        if (latestCache.getUpdatedAt() <= baselineCache.getUpdatedAt()) {
            return false;
        }
        List<PositionItem> baselinePositions = filterPositionsBySymbol(baselineSnapshot.getPositions(), command);
        List<PositionItem> latestPositions = filterPositionsBySymbol(latestSnapshot.getPositions(), command);
        List<PositionItem> baselinePendingOrders = filterPositionsBySymbol(baselineSnapshot.getPendingOrders(), command);
        List<PositionItem> latestPendingOrders = filterPositionsBySymbol(latestSnapshot.getPendingOrders(), command);
        List<TradeRecordItem> baselineTrades = filterTradesBySymbol(baselineSnapshot.getTrades(), command);
        List<TradeRecordItem> latestTrades = filterTradesBySymbol(latestSnapshot.getTrades(), command);
        boolean positionsChanged = !buildPositionsSignature(baselinePositions)
                .equals(buildPositionsSignature(latestPositions));
        boolean pendingOrdersChanged = !buildPositionsSignature(baselinePendingOrders)
                .equals(buildPositionsSignature(latestPendingOrders));
        boolean tradesChanged = !buildTradesSignature(baselineTrades)
                .equals(buildTradesSignature(latestTrades));
        boolean receiptTransitionMatched = hasReceiptMatch(receiptReference, baselineSnapshot, latestSnapshot);
        return hasRelevantChange(command,
                receiptReference,
                baselinePositions,
                latestPositions,
                baselinePendingOrders,
                latestPendingOrders,
                positionsChanged,
                pendingOrdersChanged,
                tradesChanged,
                receiptTransitionMatched);
    }

    // 判断刷新结果是否至少带回了持仓、挂单、成交和保证金/净值四类数据。
    private boolean hasRequiredRefreshSections(AccountSnapshot snapshot) {
        return snapshot.getPositions() != null
                && snapshot.getPendingOrders() != null
                && snapshot.getTrades() != null
                && hasMarginAndEquityMetrics(snapshot.getOverviewMetrics());
    }

    // 判断账户概要里是否同时带回保证金和净值。
    private boolean hasMarginAndEquityMetrics(@Nullable List<AccountMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return false;
        }
        boolean hasEquity = false;
        boolean hasMargin = false;
        for (AccountMetric metric : metrics) {
            if (metric == null || metric.getName() == null) {
                continue;
            }
            String normalized = metric.getName().trim().toLowerCase(Locale.ROOT);
            if ("总资产".equals(metric.getName()) || normalized.contains("equity")) {
                hasEquity = true;
            }
            if ("保证金".equals(metric.getName()) || normalized.contains("margin")) {
                hasMargin = true;
            }
        }
        return hasEquity && hasMargin;
    }

    // 生成持仓或挂单列表的稳定签名。
    private String buildPositionsSignature(@Nullable List<PositionItem> items) {
        if (items == null || items.isEmpty()) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        List<String> signatures = new ArrayList<>();
        for (PositionItem item : items) {
            if (item == null) {
                continue;
            }
            signatures.add(new StringBuilder()
                    .append(item.getCode()).append('|')
                    .append(item.getSide()).append('|')
                    .append(item.getPositionTicket()).append('|')
                    .append(item.getOrderId()).append('|')
                    .append(item.getQuantity()).append('|')
                    .append(item.getPendingCount()).append('|')
                    .append(item.getPendingPrice()).append('|')
                    .append(item.getTakeProfit()).append('|')
                    .append(item.getStopLoss())
                    .toString());
        }
        Collections.sort(signatures);
        for (String signature : signatures) {
            builder.append(signature).append(';');
        }
        return builder.toString();
    }

    // 生成历史成交列表的稳定签名。
    private String buildTradesSignature(@Nullable List<TradeRecordItem> items) {
        if (items == null || items.isEmpty()) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        List<String> signatures = new ArrayList<>();
        for (TradeRecordItem item : items) {
            if (item == null) {
                continue;
            }
            signatures.add(new StringBuilder()
                    .append(item.getDealTicket()).append('|')
                    .append(item.getOrderId()).append('|')
                    .append(item.getPositionId()).append('|')
                    .append(item.getCloseTime())
                    .toString());
        }
        Collections.sort(signatures);
        for (String signature : signatures) {
            builder.append(signature).append(';');
        }
        return builder.toString();
    }

    // 生成账户概要中净值与保证金的稳定签名。
    private String buildOverviewSignature(@Nullable List<AccountMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "empty";
        }
        String equity = "";
        String margin = "";
        for (AccountMetric metric : metrics) {
            if (metric == null || metric.getName() == null) {
                continue;
            }
            String normalized = metric.getName().trim().toLowerCase(Locale.ROOT);
            if ("总资产".equals(metric.getName()) || normalized.contains("equity")) {
                equity = normalizeMetricValue(metric.getValue());
            }
            if ("保证金".equals(metric.getName()) || normalized.contains("margin")) {
                margin = normalizeMetricValue(metric.getValue());
            }
        }
        return equity + "|" + margin;
    }

    // 返回拒绝或超时时应展示的文案。
    private String resolveErrorMessage(@Nullable ExecutionError error, String fallback) {
        if (error == null || safe(error.getMessage()).isEmpty()) {
            return fallback;
        }
        return error.getMessage();
    }

    // 将检查阶段的失败态映射为 UI 态。
    private UiState mapRejectedUiState(TradeCommandStateMachine stateMachine) {
        if (stateMachine.getStep() == TradeCommandStateMachine.Step.TIMEOUT) {
            return UiState.RESULT_UNCONFIRMED;
        }
        return UiState.REJECTED;
    }

    // 根据检查阶段的最终状态返回一致的提示文案。
    private String resolveCheckFailureMessage(TradeCommandStateMachine stateMachine) {
        if (stateMachine != null && stateMachine.getStep() == TradeCommandStateMachine.Step.TIMEOUT) {
            return resolveErrorMessage(stateMachine.getError(), "检查结果未确认");
        }
        return resolveErrorMessage(stateMachine == null ? null : stateMachine.getError(), "交易检查未通过");
    }

    // 将无法进入提交流程的状态按当前真实语义返回给界面。
    private ExecutionResult buildBlockedExecutionResult(@Nullable TradeCommandStateMachine stateMachine) {
        if (stateMachine == null) {
            return new ExecutionResult(
                    UiState.RESULT_UNCONFIRMED,
                    null,
                    null,
                    false,
                    true,
                    "交易尚未确认，请重新确认"
            );
        }
        if (stateMachine.getStep() == TradeCommandStateMachine.Step.SETTLED) {
            return new ExecutionResult(
                    UiState.SETTLED,
                    stateMachine,
                    null,
                    true,
                    false,
                    "交易已受理并完成账户刷新"
            );
        }
        if (stateMachine.getStep() == TradeCommandStateMachine.Step.ACCEPTED
                || stateMachine.getStep() == TradeCommandStateMachine.Step.SUBMITTING) {
            return new ExecutionResult(
                    UiState.RESULT_UNCONFIRMED,
                    stateMachine,
                    null,
                    false,
                    false,
                    "结果未确认，请等待后续刷新"
            );
        }
        if (stateMachine.getStep() == TradeCommandStateMachine.Step.REJECTED) {
            return new ExecutionResult(
                    UiState.REJECTED,
                    stateMachine,
                    null,
                    false,
                    true,
                    resolveErrorMessage(stateMachine.getError(), "交易被拒绝")
            );
        }
        if (stateMachine.getStep() == TradeCommandStateMachine.Step.TIMEOUT) {
            return new ExecutionResult(
                    UiState.RESULT_UNCONFIRMED,
                    stateMachine,
                    null,
                    false,
                    false,
                    resolveErrorMessage(stateMachine.getError(), "结果未确认")
            );
        }
        return new ExecutionResult(
                UiState.RESULT_UNCONFIRMED,
                stateMachine,
                null,
                false,
                true,
                "交易尚未确认，请重新确认"
        );
    }

    // 提取异常提示，避免把空异常文案直接透出给界面。
    private String resolveExceptionMessage(@Nullable Exception exception, String fallback) {
        if (exception == null) {
            return fallback;
        }
        String message = safe(exception.getMessage());
        return message.isEmpty() ? fallback : message;
    }

    // 根据交易动作识别本次收敛允许依赖哪些变化证据。
    private boolean hasRelevantChange(@Nullable TradeCommand command,
                                      ReceiptReference receiptReference,
                                      List<PositionItem> baselinePositions,
                                      List<PositionItem> latestPositions,
                                      List<PositionItem> baselinePendingOrders,
                                      List<PositionItem> latestPendingOrders,
                                      boolean positionsChanged,
                                      boolean pendingOrdersChanged,
                                      boolean tradesChanged,
                                      boolean receiptTransitionMatched) {
        boolean anyChanged = positionsChanged || pendingOrdersChanged || tradesChanged;
        if (!anyChanged) {
            return false;
        }
        String action = command == null ? "" : safe(command.getAction()).toUpperCase(Locale.ROOT);
        if (isModifyPriceAction(action)) {
            return hasTrackedPriceItemChanged(receiptReference,
                    baselinePositions,
                    latestPositions,
                    baselinePendingOrders,
                    latestPendingOrders,
                    positionsChanged,
                    pendingOrdersChanged);
        }
        if (isPendingOrderAction(action)) {
            if (receiptReference.hasReference()) {
                return receiptTransitionMatched;
            }
            return pendingOrdersChanged;
        }
        if (isMarketExecutionAction(action)) {
            if (receiptReference.hasReference()) {
                return receiptTransitionMatched;
            }
            return positionsChanged || tradesChanged;
        }
        if (receiptReference.hasReference()) {
            return receiptTransitionMatched;
        }
        return anyChanged;
    }

    // 判断是否属于改单、改价、改止盈止损这类动作。
    private boolean isModifyPriceAction(String action) {
        return action.contains("MODIFY")
                || action.contains("UPDATE")
                || action.contains("SLTP")
                || action.contains("TAKE_PROFIT")
                || action.contains("STOP_LOSS");
    }

    // 判断是否属于挂单相关动作。
    private boolean isPendingOrderAction(String action) {
        return action.contains("PENDING")
                || action.contains("ORDER")
                || action.contains("LIMIT")
                || action.contains("STOP")
                || action.contains("DELETE");
    }

    // 判断是否属于市价成交或平仓相关动作。
    private boolean isMarketExecutionAction(String action) {
        return action.contains("OPEN")
                || action.contains("CLOSE")
                || action.contains("MARKET")
                || action.contains("PARTIAL");
    }

    // 统一规整金额或百分比展示，避免仅格式变化就误判成状态变化。
    private String normalizeMetricValue(@Nullable String value) {
        String safeValue = safe(value);
        if (safeValue.isEmpty()) {
            return safeValue;
        }
        String normalized = safeValue
                .replace(",", "")
                .replace("$", "")
                .replace("%", "");
        try {
            return new BigDecimal(normalized).stripTrailingZeros().toPlainString();
        } catch (Exception ignored) {
            return safeValue;
        }
    }

    // 规整空字符串。
    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    // 只保留当前命令品种的持仓或挂单，避免别的品种变化误判为本次交易收敛。
    private List<PositionItem> filterPositionsBySymbol(@Nullable List<PositionItem> items,
                                                       @Nullable TradeCommand command) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        String symbol = normalizeSymbol(command == null ? null : command.getSymbol());
        if (symbol.isEmpty()) {
            return items;
        }
        List<PositionItem> filtered = new ArrayList<>();
        for (PositionItem item : items) {
            if (item == null) {
                continue;
            }
            if (matchesSymbol(symbol, item.getCode(), item.getProductName())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    // 只保留当前命令品种的成交，避免别的品种成交串入本次收敛判断。
    private List<TradeRecordItem> filterTradesBySymbol(@Nullable List<TradeRecordItem> items,
                                                       @Nullable TradeCommand command) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        String symbol = normalizeSymbol(command == null ? null : command.getSymbol());
        if (symbol.isEmpty()) {
            return items;
        }
        List<TradeRecordItem> filtered = new ArrayList<>();
        for (TradeRecordItem item : items) {
            if (item == null) {
                continue;
            }
            if (matchesSymbol(symbol, item.getCode(), item.getProductName())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    // 判断快照里是否真的出现或移除了本次回执对应的 order/deal。
    private boolean hasReceiptMatch(ReceiptReference receiptReference,
                                    @Nullable AccountSnapshot baselineSnapshot,
                                    @Nullable AccountSnapshot latestSnapshot) {
        if (!receiptReference.hasReference() || latestSnapshot == null) {
            return false;
        }
        boolean latestMatched = hasReceiptMatchInSnapshot(receiptReference, latestSnapshot);
        if (baselineSnapshot == null) {
            return latestMatched;
        }
        boolean baselineMatched = hasReceiptMatchInSnapshot(receiptReference, baselineSnapshot);
        return latestMatched != baselineMatched;
    }

    // 判断某个快照是否已经包含本次回执对应的 order/deal。
    private boolean hasReceiptMatchInSnapshot(ReceiptReference receiptReference,
                                              AccountSnapshot snapshot) {
        if (!receiptReference.hasReference() || snapshot == null) {
            return false;
        }
        List<PositionItem> positions = filterPositionsBySymbol(snapshot.getPositions(), receiptReference.command);
        List<PositionItem> pendingOrders = filterPositionsBySymbol(snapshot.getPendingOrders(), receiptReference.command);
        List<TradeRecordItem> trades = filterTradesBySymbol(snapshot.getTrades(), receiptReference.command);
        return containsOrderId(positions, receiptReference.orderId)
                || containsOrderId(pendingOrders, receiptReference.orderId)
                || containsTradeOrderId(trades, receiptReference.orderId)
                || containsDealTicket(trades, receiptReference.dealId);
    }

    // 对改单场景，只认命中的那一条持仓/挂单本身是否变化，避免同品种别的单子误触发收敛。
    private boolean hasTrackedPriceItemChanged(ReceiptReference receiptReference,
                                               List<PositionItem> baselinePositions,
                                               List<PositionItem> latestPositions,
                                               List<PositionItem> baselinePendingOrders,
                                               List<PositionItem> latestPendingOrders,
                                               boolean positionsChanged,
                                               boolean pendingOrdersChanged) {
        if (!receiptReference.hasOrderReference()) {
            return positionsChanged || pendingOrdersChanged;
        }
        String baselinePositionSignature = buildTrackedPositionSignature(baselinePositions, receiptReference.orderId);
        String latestPositionSignature = buildTrackedPositionSignature(latestPositions, receiptReference.orderId);
        if (!baselinePositionSignature.isEmpty() || !latestPositionSignature.isEmpty()) {
            return !baselinePositionSignature.equals(latestPositionSignature);
        }
        String baselinePendingSignature = buildTrackedPositionSignature(baselinePendingOrders, receiptReference.orderId);
        String latestPendingSignature = buildTrackedPositionSignature(latestPendingOrders, receiptReference.orderId);
        if (!baselinePendingSignature.isEmpty() || !latestPendingSignature.isEmpty()) {
            return !baselinePendingSignature.equals(latestPendingSignature);
        }
        return positionsChanged || pendingOrdersChanged;
    }

    // 构建命中某个 orderId 的单条持仓/挂单签名。
    private String buildTrackedPositionSignature(@Nullable List<PositionItem> items, long orderId) {
        if (items == null || items.isEmpty() || orderId <= 0L) {
            return "";
        }
        for (PositionItem item : items) {
            if (item == null || item.getOrderId() != orderId) {
                continue;
            }
            return new StringBuilder()
                    .append(item.getCode()).append('|')
                    .append(item.getSide()).append('|')
                    .append(item.getPositionTicket()).append('|')
                    .append(item.getOrderId()).append('|')
                    .append(item.getQuantity()).append('|')
                    .append(item.getPendingCount()).append('|')
                    .append(item.getPendingPrice()).append('|')
                    .append(item.getTakeProfit()).append('|')
                    .append(item.getStopLoss())
                    .toString();
        }
        return "";
    }

    // 从回执里提取当前可用于收敛匹配的 order/deal。
    private ReceiptReference buildReceiptReference(@Nullable TradeCommand command,
                                                  @Nullable TradeReceipt receipt) {
        if (receipt == null) {
            return new ReceiptReference(command, 0L, 0L);
        }
        JSONObject result = receipt.getResult();
        long orderId = result == null ? 0L : result.optLong("order", 0L);
        long dealId = result == null ? 0L : result.optLong("deal", 0L);
        return new ReceiptReference(command, orderId, dealId);
    }

    // 判断回执是否是“幂等重复但已确认执行”的受理结果。
    private boolean isAcceptedDuplicateReceipt(@Nullable TradeReceipt receipt) {
        if (receipt == null || !receipt.isIdempotent()) {
            return false;
        }
        String status = receipt.getStatus();
        if ("DUPLICATE".equalsIgnoreCase(status)) {
            return true;
        }
        if (!"ACCEPTED".equalsIgnoreCase(status)) {
            return false;
        }
        ExecutionError error = receipt.getError();
        return error != null
                && "TRADE_DUPLICATE_SUBMISSION".equalsIgnoreCase(error.getCode());
    }

    // 判断列表里是否包含指定 orderId。
    private boolean containsOrderId(@Nullable List<PositionItem> items, long orderId) {
        if (items == null || items.isEmpty() || orderId <= 0L) {
            return false;
        }
        for (PositionItem item : items) {
            if (item != null && item.getOrderId() == orderId) {
                return true;
            }
        }
        return false;
    }

    // 判断成交列表里是否包含指定 orderId。
    private boolean containsTradeOrderId(@Nullable List<TradeRecordItem> items, long orderId) {
        if (items == null || items.isEmpty() || orderId <= 0L) {
            return false;
        }
        for (TradeRecordItem item : items) {
            if (item != null && item.getOrderId() == orderId) {
                return true;
            }
        }
        return false;
    }

    // 判断成交列表里是否包含指定 deal。
    private boolean containsDealTicket(@Nullable List<TradeRecordItem> items, long dealId) {
        if (items == null || items.isEmpty() || dealId <= 0L) {
            return false;
        }
        for (TradeRecordItem item : items) {
            if (item != null && item.getDealTicket() == dealId) {
                return true;
            }
        }
        return false;
    }

    // 统一规整品种名，避免大小写不同导致匹配失败。
    private String normalizeSymbol(@Nullable String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }

    // 判断当前记录是否属于目标品种。
    private boolean matchesSymbol(String normalizedSymbol, @Nullable String code, @Nullable String productName) {
        return normalizedSymbol.equals(normalizeSymbol(code))
                || normalizedSymbol.equals(normalizeSymbol(productName));
    }

    private static class ReceiptReference {
        private final TradeCommand command;
        private final long orderId;
        private final long dealId;

        private ReceiptReference(@Nullable TradeCommand command, long orderId, long dealId) {
            this.command = command;
            this.orderId = orderId;
            this.dealId = dealId;
        }

        private boolean hasReference() {
            return hasOrderReference() || dealId > 0L;
        }

        private boolean hasOrderReference() {
            return orderId > 0L;
        }
    }

    public interface TradeGateway {
        TradeCheckResult check(TradeCommand command) throws Exception;

        TradeReceipt submit(TradeCommand command) throws Exception;

        TradeReceipt result(String requestId) throws Exception;
    }

    public interface AccountRefreshGateway {
        AccountStatsPreloadManager.Cache fetchForUi(AccountTimeRange range) throws Exception;
    }

    public enum UiState {
        AWAITING_CONFIRMATION,
        SETTLED,
        RESULT_UNCONFIRMED,
        REJECTED
    }

    public static class PreparedTrade {
        private final UiState uiState;
        private final TradeCommandStateMachine stateMachine;
        private final boolean requiresConfirmation;
        private final boolean oneClickTradingEnabled;
        private final String message;
        private boolean confirmed;

        // 保存检查完成后的待确认状态。
        public PreparedTrade(UiState uiState,
                             TradeCommandStateMachine stateMachine,
                             boolean requiresConfirmation,
                             boolean oneClickTradingEnabled,
                             String message,
                             boolean confirmed) {
            this.uiState = uiState;
            this.stateMachine = stateMachine;
            this.requiresConfirmation = requiresConfirmation;
            this.oneClickTradingEnabled = oneClickTradingEnabled;
            this.message = message == null ? "" : message;
            this.confirmed = confirmed;
        }

        // 返回当前界面状态。
        public UiState getUiState() {
            return uiState;
        }

        // 返回当前状态机。
        public TradeCommandStateMachine getStateMachine() {
            return stateMachine;
        }

        // 返回是否需要确认。
        public boolean requiresConfirmation() {
            return requiresConfirmation;
        }

        // 返回是否开启一键交易。
        public boolean isOneClickTradingEnabled() {
            return oneClickTradingEnabled;
        }

        // 返回当前文案。
        public String getMessage() {
            return message;
        }

        // 标记用户已经完成确认，允许后续进入提交流程。
        public PreparedTrade markConfirmed() {
            confirmed = true;
            return this;
        }

        // 返回当前是否已完成确认。
        public boolean isConfirmed() {
            return confirmed;
        }
    }

    public static class ExecutionResult {
        private final UiState uiState;
        private final TradeCommandStateMachine stateMachine;
        private final AccountStatsPreloadManager.Cache latestCache;
        private final boolean settled;
        private final boolean safeState;
        private final String message;

        // 保存提交后的最终可展示结果。
        public ExecutionResult(UiState uiState,
                               TradeCommandStateMachine stateMachine,
                               @Nullable AccountStatsPreloadManager.Cache latestCache,
                               boolean settled,
                               boolean safeState,
                               String message) {
            this.uiState = uiState;
            this.stateMachine = stateMachine;
            this.latestCache = latestCache;
            this.settled = settled;
            this.safeState = safeState;
            this.message = message == null ? "" : message;
        }

        // 返回界面状态。
        public UiState getUiState() {
            return uiState;
        }

        // 返回状态机。
        public TradeCommandStateMachine getStateMachine() {
            return stateMachine;
        }

        // 返回最新账户缓存。
        public AccountStatsPreloadManager.Cache getLatestCache() {
            return latestCache;
        }

        // 返回是否已经收敛。
        public boolean isSettled() {
            return settled;
        }

        // 返回是否已回到安全态。
        public boolean isSafeState() {
            return safeState;
        }

        // 返回提示文案。
        public String getMessage() {
            return message;
        }
    }
}
