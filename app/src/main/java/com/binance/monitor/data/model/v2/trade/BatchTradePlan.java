/*
 * 批量交易计划模型，负责保存 batchId、策略、账户模式和待执行单项列表。
 * 供 TradeComplexActionPlanner、BatchTradeCoordinator 与网关客户端复用。
 */
package com.binance.monitor.data.model.v2.trade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BatchTradePlan {
    private final String batchId;
    private final String strategy;
    private final String accountMode;
    private final String summary;
    private final List<BatchTradeItem> items;

    // 构造一次完整批量交易计划。
    public BatchTradePlan(String batchId,
                          String strategy,
                          String accountMode,
                          String summary,
                          List<BatchTradeItem> items) {
        this.batchId = batchId == null ? "" : batchId;
        this.strategy = strategy == null ? "" : strategy;
        this.accountMode = accountMode == null ? "" : accountMode;
        this.summary = summary == null ? "" : summary;
        this.items = Collections.unmodifiableList(new ArrayList<>(items == null ? Collections.emptyList() : items));
    }

    // 返回批量 ID。
    public String getBatchId() {
        return batchId;
    }

    // 返回执行策略。
    public String getStrategy() {
        return strategy;
    }

    // 返回账户模式。
    public String getAccountMode() {
        return accountMode;
    }

    // 返回计划摘要。
    public String getSummary() {
        return summary;
    }

    // 返回单项列表。
    public List<BatchTradeItem> getItems() {
        return items;
    }
}
