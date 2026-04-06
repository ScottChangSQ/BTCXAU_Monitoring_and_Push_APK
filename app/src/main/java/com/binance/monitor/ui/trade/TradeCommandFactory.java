/*
 * 交易命令工厂，负责把图表页输入整理成第一阶段支持的单笔交易命令。
 * 统一生成 requestId、扩展 params 和人类可读摘要，避免页面层自己拼命令。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.util.FormatUtils;

import org.json.JSONObject;

import java.util.Locale;
import java.util.UUID;

public final class TradeCommandFactory {

    private TradeCommandFactory() {
    }

    // 创建市价开仓命令。
    public static TradeCommand openMarket(String accountId,
                                          String symbol,
                                          String side,
                                          double volume,
                                          double price,
                                          double sl,
                                          double tp) {
        JSONObject params = createBaseParams(symbol, volume, price, sl, tp);
        putQuietly(params, "side", normalizeSide(side));
        return new TradeCommand(
                nextRequestId(),
                accountId,
                symbol,
                "OPEN_MARKET",
                volume,
                price,
                sl,
                tp,
                params
        );
    }

    // 创建平仓命令。
    public static TradeCommand closePosition(String accountId,
                                             String symbol,
                                             long positionTicket,
                                             double volume,
                                             double price) {
        JSONObject params = createBaseParams(symbol, volume, price, 0d, 0d);
        if (positionTicket > 0L) {
            putQuietly(params, "positionTicket", positionTicket);
        }
        return new TradeCommand(
                nextRequestId(),
                accountId,
                symbol,
                "CLOSE_POSITION",
                volume,
                price,
                0d,
                0d,
                params
        );
    }

    // 创建挂单新增命令。
    public static TradeCommand pendingAdd(String accountId,
                                          String symbol,
                                          String orderType,
                                          double volume,
                                          double price,
                                          double sl,
                                          double tp) {
        JSONObject params = createBaseParams(symbol, volume, price, sl, tp);
        putQuietly(params, "orderType", normalizeOrderType(orderType));
        return new TradeCommand(
                nextRequestId(),
                accountId,
                symbol,
                "PENDING_ADD",
                volume,
                price,
                sl,
                tp,
                params
        );
    }

    // 创建挂单撤销命令。
    public static TradeCommand pendingCancel(String accountId,
                                             String symbol,
                                             long orderTicket) {
        JSONObject params = createBaseParams(symbol, 0d, 0d, 0d, 0d);
        if (orderTicket > 0L) {
            putQuietly(params, "orderTicket", orderTicket);
            putQuietly(params, "orderId", orderTicket);
        }
        return new TradeCommand(
                nextRequestId(),
                accountId,
                symbol,
                "PENDING_CANCEL",
                0d,
                0d,
                0d,
                0d,
                params
        );
    }

    // 创建 TP/SL 修改命令。
    public static TradeCommand modifyTpSl(String accountId,
                                          String symbol,
                                          long positionTicket,
                                          double referencePrice,
                                          double sl,
                                          double tp) {
        JSONObject params = createBaseParams(symbol, 0d, referencePrice, sl, tp);
        if (positionTicket > 0L) {
            putQuietly(params, "positionTicket", positionTicket);
        }
        return new TradeCommand(
                nextRequestId(),
                accountId,
                symbol,
                "MODIFY_TPSL",
                0d,
                referencePrice,
                sl,
                tp,
                params
        );
    }

    // 生成当前命令的简短确认文案。
    public static String describe(@Nullable TradeCommand command) {
        if (command == null) {
            return "交易命令无效";
        }
        String action = safe(command.getAction()).toUpperCase(Locale.ROOT);
        JSONObject params = command.getParams();
        String symbol = safe(command.getSymbol());
        if ("OPEN_MARKET".equals(action)) {
            String side = normalizeSide(params.optString("side", ""));
            return ("buy".equals(side) ? "买入 " : "卖出 ")
                    + symbol
                    + " "
                    + formatVolume(command.getVolume())
                    + " "
                    + formatExecutionPrice(command.getPrice());
        }
        if ("CLOSE_POSITION".equals(action)) {
            return "平仓 "
                    + symbol
                    + " "
                    + formatVolume(command.getVolume())
                    + " "
                    + formatExecutionPrice(command.getPrice());
        }
        if ("PENDING_ADD".equals(action)) {
            return "新增挂单 "
                    + normalizeOrderType(params.optString("orderType", ""))
                    + " "
                    + symbol
                    + " "
                    + formatVolume(command.getVolume())
                    + " @ $"
                    + FormatUtils.formatPrice(command.getPrice());
        }
        if ("PENDING_CANCEL".equals(action)) {
            return "撤销挂单 " + symbol + " #" + params.optLong("orderTicket", 0L);
        }
        if ("MODIFY_TPSL".equals(action)) {
            return "修改止盈止损 "
                    + symbol
                    + " TP="
                    + formatOptionalPrice(command.getTp())
                    + " SL="
                    + formatOptionalPrice(command.getSl());
        }
        return action + " " + symbol;
    }

    // 生成基础 params。
    private static JSONObject createBaseParams(String symbol,
                                               double volume,
                                               double price,
                                               double sl,
                                               double tp) {
        JSONObject params = new JSONObject();
        putQuietly(params, "symbol", safe(symbol).toUpperCase(Locale.ROOT));
        if (volume > 0d) {
            putQuietly(params, "volume", volume);
        }
        if (price > 0d) {
            putQuietly(params, "price", price);
        }
        if (sl > 0d) {
            putQuietly(params, "sl", sl);
        }
        if (tp > 0d) {
            putQuietly(params, "tp", tp);
        }
        return params;
    }

    // 安全写入 JSON，避免把格式异常暴露给页面层。
    private static void putQuietly(JSONObject target, String key, Object value) {
        if (target == null || key == null || key.trim().isEmpty()) {
            return;
        }
        try {
            target.put(key, value);
        } catch (Exception ignored) {
        }
    }

    // 统一规范方向。
    private static String normalizeSide(String side) {
        String normalized = safe(side).toLowerCase(Locale.ROOT);
        if ("buy".equals(normalized) || "long".equals(normalized)) {
            return "buy";
        }
        if ("sell".equals(normalized) || "short".equals(normalized)) {
            return "sell";
        }
        return normalized;
    }

    // 统一规范挂单类型。
    private static String normalizeOrderType(String orderType) {
        return safe(orderType).toLowerCase(Locale.ROOT);
    }

    // 生成 requestId。
    private static String nextRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String formatVolume(double volume) {
        return String.format(Locale.getDefault(), "%.2f 手", Math.max(0d, volume));
    }

    private static String formatOptionalPrice(double price) {
        if (price <= 0d) {
            return "--";
        }
        return "$" + FormatUtils.formatPrice(price);
    }

    private static String formatExecutionPrice(double price) {
        if (price <= 0d) {
            return "按市价";
        }
        return "@ $" + FormatUtils.formatPrice(price);
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
