/*
 * 悬浮窗整体快照，确保连接状态、顶部汇总和各产品行情在同一次刷新里一起更新。
 * MonitorService 负责构建，FloatingWindowManager 只消费这一份不可变结果。
 */
package com.binance.monitor.ui.floating;

import java.util.ArrayList;
import java.util.List;

public class FloatingWindowSnapshot {

    private final String connectionStatus;
    private final long updatedAt;
    private final List<FloatingSymbolCardData> cards;

    public FloatingWindowSnapshot(String connectionStatus,
                                  long updatedAt,
                                  List<FloatingSymbolCardData> cards) {
        this.connectionStatus = connectionStatus == null ? "" : connectionStatus;
        this.updatedAt = updatedAt;
        this.cards = cards == null ? new ArrayList<>() : new ArrayList<>(cards);
    }

    // 返回连接状态文本。
    public String getConnectionStatus() {
        return connectionStatus;
    }

    // 返回本次悬浮窗统一刷新时间。
    public long getUpdatedAt() {
        return updatedAt;
    }

    // 返回本次悬浮窗的全部产品卡片。
    public List<FloatingSymbolCardData> getCards() {
        return new ArrayList<>(cards);
    }
}
