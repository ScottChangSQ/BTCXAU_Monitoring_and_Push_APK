/*
 * 批量交易计划中的单项命令模型，负责保存 itemId、显示文案和正式交易命令。
 * 供 BatchTradePlan、BatchTradeCoordinator 和图表页复杂交易入口共享。
 */
package com.binance.monitor.data.model.v2.trade;

import org.json.JSONObject;

public class BatchTradeItem {
    private final String itemId;
    private final String displayLabel;
    private final TradeCommand command;
    private final JSONObject extras;

    // 构造批量交易单项。
    public BatchTradeItem(String itemId,
                          String displayLabel,
                          TradeCommand command,
                          JSONObject extras) {
        this.itemId = itemId == null ? "" : itemId;
        this.displayLabel = displayLabel == null ? "" : displayLabel;
        this.command = command;
        this.extras = cloneJson(extras);
    }

    // 返回单项 ID。
    public String getItemId() {
        return itemId;
    }

    // 返回显示文案。
    public String getDisplayLabel() {
        return displayLabel;
    }

    // 返回正式交易命令。
    public TradeCommand getCommand() {
        return command;
    }

    // 返回扩展字段。
    public JSONObject getExtras() {
        return cloneJson(extras);
    }

    // 返回当前单项的动作类型。
    public String getAction() {
        return command == null ? "" : command.getAction();
    }

    // 返回当前单项的分组键。
    public String getGroupKey() {
        return extras.optString("groupKey", "").trim();
    }

    // 安全复制扩展字段，避免外层直接改写内部状态。
    private static JSONObject cloneJson(JSONObject source) {
        if (source == null) {
            return new JSONObject();
        }
        try {
            return new JSONObject(source.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
