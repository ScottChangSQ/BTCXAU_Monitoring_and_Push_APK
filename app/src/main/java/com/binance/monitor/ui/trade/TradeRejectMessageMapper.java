/*
 * 交易拒单文案映射器，负责把底层错误码翻成统一中文提示。
 * 与 TradeExecutionCoordinator、ExecutionError 协同工作。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.ExecutionError;

public final class TradeRejectMessageMapper {

    private TradeRejectMessageMapper() {
    }

    // 把统一错误对象翻译成用户可读文案。
    @NonNull
    public static String toUserMessage(@Nullable ExecutionError error) {
        if (error == null) {
            return "交易被拒绝，请稍后重试";
        }
        String code = error.getCode() == null ? "" : error.getCode().trim();
        if ("TRADE_INSUFFICIENT_MARGIN".equals(code)) {
            return "保证金不足，请降低手数或释放仓位后重试";
        }
        if ("TRADE_MARKET_CLOSED".equals(code)) {
            return "当前市场暂不可交易，请稍后重试";
        }
        if ("TRADE_REQUOTE".equals(code)) {
            return "服务器报价已变化，请重新确认价格后再提交";
        }
        if ("TRADE_TIMEOUT".equals(code) || "TRADE_RESULT_UNKNOWN".equals(code)) {
            return "结果暂未确认，请等待账户同步后再判断";
        }
        String message = error.getMessage() == null ? "" : error.getMessage().trim();
        return message.isEmpty() ? "交易被拒绝，请稍后重试" : message;
    }
}
