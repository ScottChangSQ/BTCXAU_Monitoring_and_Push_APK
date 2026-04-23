/*
 * 图表交易线模型，承载单条活动交易线的价格、标签和状态。
 * 与 ChartTradeLayerSnapshot、KlineChartView 协同工作。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;

final class ChartTradeLine {
    private final String id;
    private final String groupId;
    private final double price;
    private final String label;
    private final String centerLabel;
    private final ChartTradeLineState state;
    private final ChartTradeLineTone tone;
    private final ChartTradeLineRole role;
    private final boolean editable;
    private final boolean ghost;
    private final String actionText;

    ChartTradeLine(String id, double price, String label, ChartTradeLineState state) {
        this(id, id, price, label, "", state, ChartTradeLineTone.NEUTRAL);
    }

    ChartTradeLine(String id,
                   String groupId,
                   double price,
                   String label,
                   String centerLabel,
                   ChartTradeLineState state,
                   ChartTradeLineTone tone) {
        this(id, groupId, price, label, centerLabel, state, tone, defaultRoleForState(state), false, false, "");
    }

    ChartTradeLine(String id,
                   String groupId,
                   double price,
                   String label,
                   String centerLabel,
                   ChartTradeLineState state,
                   ChartTradeLineTone tone,
                   ChartTradeLineRole role,
                   boolean editable,
                   boolean ghost,
                   String actionText) {
        this.id = id == null ? "" : id;
        this.groupId = groupId == null ? this.id : groupId;
        this.price = price;
        this.label = label == null ? "" : label;
        this.centerLabel = centerLabel == null ? "" : centerLabel;
        this.state = state == null ? ChartTradeLineState.LIVE_PENDING : state;
        this.tone = tone == null ? ChartTradeLineTone.NEUTRAL : tone;
        this.role = role == null ? defaultRoleForState(state) : role;
        this.editable = editable;
        this.ghost = ghost;
        this.actionText = actionText == null ? "" : actionText;
    }

    String getId() {
        return id;
    }

    String getGroupId() {
        return groupId;
    }

    double getPrice() {
        return price;
    }

    String getLabel() {
        return label;
    }

    String getCenterLabel() {
        return centerLabel;
    }

    ChartTradeLineState getState() {
        return state;
    }

    ChartTradeLineTone getTone() {
        return tone;
    }

    ChartTradeLineRole getRole() {
        return role;
    }

    boolean isEditable() {
        return editable;
    }

    boolean isGhost() {
        return ghost;
    }

    String getActionText() {
        return actionText;
    }

    @NonNull
    private static ChartTradeLineRole defaultRoleForState(ChartTradeLineState state) {
        if (state == ChartTradeLineState.LIVE_TP) {
            return ChartTradeLineRole.TP;
        }
        if (state == ChartTradeLineState.LIVE_SL) {
            return ChartTradeLineRole.SL;
        }
        return ChartTradeLineRole.ENTRY;
    }
}
