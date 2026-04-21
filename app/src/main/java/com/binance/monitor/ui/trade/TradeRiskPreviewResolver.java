/*
 * 交易风险预演解析器，负责把交易命令和检查结果转换成确认阶段可读的风险摘要。
 * 与 TradeRiskPreview、TradeCommandFactory 和网关 check 结果协同工作。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.TradeCheckResult;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.util.FormatUtils;

import org.json.JSONObject;

public final class TradeRiskPreviewResolver {

    private TradeRiskPreviewResolver() {
    }

    // 把命令与检查结果解析成确认阶段展示的风险预演。
    @NonNull
    public static TradeRiskPreview resolve(@Nullable TradeCommand command, @Nullable TradeCheckResult checkResult) {
        JSONObject check = checkResult == null ? new JSONObject() : checkResult.getCheck();
        return new TradeRiskPreview(
                TradeCommandFactory.describe(command),
                command == null ? "" : command.getParams().optString("templateName", ""),
                formatMoney(check.optDouble("margin", 0d)),
                formatMoney(check.optDouble("freeMarginAfter", check.optDouble("freeMargin", 0d))),
                formatMoney(check.optDouble("stopLossAmount", 0d)),
                formatMoney(check.optDouble("takeProfitAmount", 0d))
        );
    }

    // 把金额格式化成确认弹窗里的美元文本。
    @NonNull
    private static String formatMoney(double value) {
        if (!Double.isFinite(value) || value <= 0d) {
            return "";
        }
        return "$" + FormatUtils.formatPrice(value);
    }
}
