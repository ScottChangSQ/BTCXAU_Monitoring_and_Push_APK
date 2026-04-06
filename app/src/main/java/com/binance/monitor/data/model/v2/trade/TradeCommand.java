/*
 * 交易命令模型，明确保存一次下单意图所需的固定字段。
 * 该模型只表示交易命令，不承载任何展示层字段。
 */
package com.binance.monitor.data.model.v2.trade;

import org.json.JSONObject;

public class TradeCommand {
    private final String requestId;
    private final String accountId;
    private final String symbol;
    private final String action;
    private final double volume;
    private final double price;
    private final double sl;
    private final double tp;
    private final JSONObject params;

    // 构造交易命令对象。
    public TradeCommand(String requestId,
                        String accountId,
                        String symbol,
                        String action,
                        double volume,
                        double price,
                        double sl,
                        double tp) {
        this(requestId, accountId, symbol, action, volume, price, sl, tp, null);
    }

    // 构造带扩展参数的交易命令对象。
    public TradeCommand(String requestId,
                        String accountId,
                        String symbol,
                        String action,
                        double volume,
                        double price,
                        double sl,
                        double tp,
                        JSONObject params) {
        this.requestId = requestId == null ? "" : requestId;
        this.accountId = accountId == null ? "" : accountId;
        this.symbol = symbol == null ? "" : symbol;
        this.action = action == null ? "" : action;
        this.volume = volume;
        this.price = price;
        this.sl = sl;
        this.tp = tp;
        this.params = cloneJson(params);
    }

    // 返回请求 ID。
    public String getRequestId() {
        return requestId;
    }

    // 返回账户 ID。
    public String getAccountId() {
        return accountId;
    }

    // 返回交易品种。
    public String getSymbol() {
        return symbol;
    }

    // 返回动作类型。
    public String getAction() {
        return action;
    }

    // 返回交易手数。
    public double getVolume() {
        return volume;
    }

    // 返回委托价格。
    public double getPrice() {
        return price;
    }

    // 返回止损价。
    public double getSl() {
        return sl;
    }

    // 返回止盈价。
    public double getTp() {
        return tp;
    }

    // 返回扩展参数。
    public JSONObject getParams() {
        return cloneJson(params);
    }

    // 安全复制扩展参数，避免上层意外改到内部状态。
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
