/*
 * 批量交易单项结果模型，负责保存服务端返回的状态、错误和单项执行详情。
 * 供 BatchTradeReceipt、BatchTradeCoordinator 与结果展示层复用。
 */
package com.binance.monitor.data.model.v2.trade;

import org.json.JSONObject;

public class BatchTradeItemResult {
    private final String itemId;
    private final String action;
    private final String displayLabel;
    private final String status;
    private final ExecutionError error;
    private final JSONObject check;
    private final JSONObject result;
    private final String groupKey;

    // 构造单项批量结果。
    public BatchTradeItemResult(String itemId,
                                String action,
                                String displayLabel,
                                String status,
                                ExecutionError error,
                                JSONObject check,
                                JSONObject result,
                                String groupKey) {
        this.itemId = itemId == null ? "" : itemId;
        this.action = action == null ? "" : action;
        this.displayLabel = displayLabel == null ? "" : displayLabel;
        this.status = status == null ? "" : status;
        this.error = error;
        this.check = cloneJson(check);
        this.result = cloneJson(result);
        this.groupKey = groupKey == null ? "" : groupKey;
    }

    // 构建成功单项结果。
    public static BatchTradeItemResult accepted(String itemId, String action, String displayLabel) {
        return new BatchTradeItemResult(itemId, action, displayLabel, "ACCEPTED", null, null, null, "");
    }

    // 构建失败单项结果。
    public static BatchTradeItemResult rejected(String itemId,
                                                String action,
                                                String displayLabel,
                                                ExecutionError error) {
        return new BatchTradeItemResult(itemId, action, displayLabel, "REJECTED", error, null, null, "");
    }

    // 返回带本地显示文案的新结果，避免页面层再做 Map 侧表。
    public BatchTradeItemResult withDisplayLabel(String displayLabel) {
        return new BatchTradeItemResult(itemId, action, displayLabel, status, error, check, result, groupKey);
    }

    // 返回 itemId。
    public String getItemId() {
        return itemId;
    }

    // 返回动作类型。
    public String getAction() {
        return action;
    }

    // 返回显示文案。
    public String getDisplayLabel() {
        return displayLabel;
    }

    // 返回状态。
    public String getStatus() {
        return status;
    }

    // 返回错误对象。
    public ExecutionError getError() {
        return error;
    }

    // 返回检查结果。
    public JSONObject getCheck() {
        return cloneJson(check);
    }

    // 返回执行结果。
    public JSONObject getResult() {
        return cloneJson(result);
    }

    // 返回分组键。
    public String getGroupKey() {
        return groupKey;
    }

    // 判断当前单项是否成功。
    public boolean isAccepted() {
        return "ACCEPTED".equalsIgnoreCase(status);
    }

    // 判断当前单项是否拒绝。
    public boolean isRejected() {
        return "FAILED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status);
    }

    // 安全复制 JSON，避免上层直接改写内部状态。
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
