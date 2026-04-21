/*
 * 批量交易回执模型，对应 /v2/trade/batch/submit 与 /v2/trade/batch/result 的响应。
 * 用于表示整批状态，以及每一项的执行结果清单。
 */
package com.binance.monitor.data.model.v2.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BatchTradeReceipt {
    private final String batchId;
    private final String strategy;
    private final String accountMode;
    private final String status;
    private final ExecutionError error;
    private final List<BatchTradeItemResult> items;
    private final long serverTime;

    // 构造批量交易回执。
    public BatchTradeReceipt(String batchId,
                             String strategy,
                             String accountMode,
                             String status,
                             ExecutionError error,
                             List<BatchTradeItemResult> items,
                             long serverTime) {
        this.batchId = batchId == null ? "" : batchId;
        this.strategy = strategy == null ? "" : strategy;
        this.accountMode = accountMode == null ? "" : accountMode;
        this.status = status == null ? "" : status;
        this.error = error;
        this.items = Collections.unmodifiableList(new ArrayList<>(items == null ? Collections.emptyList() : items));
        this.serverTime = serverTime;
    }

    // 构建整批成功回执。
    public static BatchTradeReceipt accepted(String batchId,
                                             String strategy,
                                             String accountMode,
                                             List<BatchTradeItemResult> items) {
        return new BatchTradeReceipt(batchId, strategy, accountMode, "ACCEPTED", null, items, 0L);
    }

    // 构建部分成功回执。
    public static BatchTradeReceipt partial(String batchId,
                                            String strategy,
                                            String accountMode,
                                            List<BatchTradeItemResult> items) {
        return new BatchTradeReceipt(
                batchId,
                strategy,
                accountMode,
                "PARTIAL",
                new ExecutionError("TRADE_EXECUTION_FAILED", "部分成功，部分失败", null),
                items,
                0L
        );
    }

    // 构建失败回执。
    public static BatchTradeReceipt failed(String batchId,
                                           String strategy,
                                           String accountMode,
                                           ExecutionError error,
                                           List<BatchTradeItemResult> items) {
        return new BatchTradeReceipt(batchId, strategy, accountMode, "FAILED", error, items, 0L);
    }

    // 返回替换结果明细后的新回执。
    public BatchTradeReceipt withItems(List<BatchTradeItemResult> items) {
        return new BatchTradeReceipt(batchId, strategy, accountMode, status, error, items, serverTime);
    }

    // 判断整批是否已受理成功。
    public boolean isAccepted() {
        return "ACCEPTED".equalsIgnoreCase(status);
    }

    // 判断整批是否部分成功。
    public boolean isPartial() {
        return "PARTIAL".equalsIgnoreCase(status);
    }

    // 判断整批是否失败。
    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status);
    }

    // 返回 batchId。
    public String getBatchId() {
        return batchId;
    }

    // 返回策略。
    public String getStrategy() {
        return strategy;
    }

    // 返回账户模式。
    public String getAccountMode() {
        return accountMode;
    }

    // 返回整体状态。
    public String getStatus() {
        return status;
    }

    // 返回整体错误。
    public ExecutionError getError() {
        return error;
    }

    // 返回单项列表。
    public List<BatchTradeItemResult> getItems() {
        return items;
    }

    // 返回服务端时间。
    public long getServerTime() {
        return serverTime;
    }
}
