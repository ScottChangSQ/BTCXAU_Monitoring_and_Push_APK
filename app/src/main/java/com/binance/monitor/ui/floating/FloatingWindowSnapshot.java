/*
 * 悬浮窗整体快照，确保连接状态、顶部汇总和各产品行情在同一次刷新里一起更新。
 * MonitorService 负责构建，FloatingWindowManager 只消费这一份不可变结果。
 */
package com.binance.monitor.ui.floating;

import com.binance.monitor.runtime.ConnectionStage;

import java.util.ArrayList;
import java.util.List;

public class FloatingWindowSnapshot {

    private final ConnectionStage connectionStage;
    private final String connectionStatus;
    private final long updatedAt;
    private final List<FloatingSymbolCardData> cards;
    private final String visualSignature;

    public FloatingWindowSnapshot(ConnectionStage connectionStage,
                                  String connectionStatus,
                                  long updatedAt,
                                  List<FloatingSymbolCardData> cards) {
        this.connectionStage = connectionStage == null ? ConnectionStage.CONNECTING : connectionStage;
        this.connectionStatus = connectionStatus == null ? "" : connectionStatus;
        this.updatedAt = updatedAt;
        this.cards = cards == null ? new ArrayList<>() : new ArrayList<>(cards);
        this.visualSignature = buildVisualSignature(this.connectionStage, this.connectionStatus, this.cards);
    }

    // 返回当前连接阶段真值。
    public ConnectionStage getConnectionStage() {
        return connectionStage;
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

    // 返回当前快照的可视内容签名，用于跳过无变化重绘。
    public String getVisualSignature() {
        return visualSignature;
    }

    // 判断两份快照的可视内容是否完全一致。
    public boolean hasSameVisualContent(FloatingWindowSnapshot other) {
        if (other == null) {
            return false;
        }
        return visualSignature.equals(other.getVisualSignature());
    }

    // 只把真正影响展示的字段纳入签名，不让纯时间戳变化触发重绘。
    private String buildVisualSignature(ConnectionStage connectionStage,
                                        String connectionStatus,
                                        List<FloatingSymbolCardData> cards) {
        StringBuilder builder = new StringBuilder();
        builder.append(connectionStage == null ? "" : connectionStage.name());
        builder.append('|');
        builder.append(connectionStatus == null ? "" : connectionStatus.trim());
        builder.append("|cards=");
        if (cards == null || cards.isEmpty()) {
            return builder.toString();
        }
        for (FloatingSymbolCardData card : cards) {
            if (card == null) {
                continue;
            }
            builder.append('[').append(card.buildVisualSignature()).append(']');
        }
        return builder.toString();
    }
}
