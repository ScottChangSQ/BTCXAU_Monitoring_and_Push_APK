/*
 * 悬浮窗文案格式工具，负责把产品名、手数和盈亏金额拼成统一的两行文本。
 */
package com.binance.monitor.ui.floating;

import com.binance.monitor.ui.rules.IndicatorFormatterCenter;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;

final class FloatingWindowTextFormatter {

    private static final double ZERO_EPSILON = 1e-9;

    private FloatingWindowTextFormatter() {
    }

    // 生成悬浮窗产品卡片标题，仅保留“产品名 | 手数”主信息。
    static String formatCardTitle(String label,
                                  double totalLots,
                                  double totalPnl,
                                  boolean hasPosition,
                                  boolean masked) {
        String safeLabel = label == null ? "" : label.trim();
        return hasPosition ? safeLabel + " | " + formatLotsText(totalLots) : safeLabel;
    }

    // 统一格式化悬浮窗盈亏金额，顶部汇总和产品标题都复用这套规则。
    static String formatPnlAmount(double totalPnl, boolean masked) {
        return masked ? SensitiveDisplayMasker.MASK_TEXT : formatVisiblePnl(totalPnl);
    }

    // 统一格式化悬浮窗产品价格，改为一位小数并保留隐私遮罩行为。
    static String formatPriceText(double price, boolean masked) {
        String visibleText = IndicatorFormatterCenter.formatPrice(price, 1, masked).replace("$", "$ ");
        return SensitiveDisplayMasker.maskPrice(visibleText, masked);
    }

    // 最小化悬浮窗优先显示离线，其次区分“无持仓”和真实盈亏。
    static String formatMiniStatusText(boolean offline,
                                       boolean hasActivePosition,
                                       double totalPnl,
                                       boolean masked) {
        if (offline) {
            return "离线";
        }
        if (!hasActivePosition) {
            return "无持仓";
        }
        return formatPnlAmount(totalPnl, masked);
    }

    // 判断盈亏是否应按中性色展示，避免 0 值仍被误显示成涨色或跌色。
    static boolean shouldUseNeutralPnlStyle(double totalPnl) {
        return Math.abs(totalPnl) < ZERO_EPSILON;
    }

    // 统一格式化悬浮窗 1 分钟成交量行。
    static String formatVolumeLine(double volume, String unit, boolean masked) {
        String safeUnit = unit == null ? "" : unit.trim();
        String volumeText = masked
                ? SensitiveDisplayMasker.MASK_TEXT
                : FormatUtils.formatVolumeWithUnit(volume, safeUnit).trim();
        return volumeText;
    }

    // 统一格式化悬浮窗 1 分钟成交额行。
    static String formatAmountLine(double amount, boolean masked) {
        String amountText = IndicatorFormatterCenter.formatCompactAmount(amount, masked);
        return amountText;
    }

    // 统一格式化悬浮窗第一行里的方向手数。
    static String formatLotsText(double totalLots) {
        return IndicatorFormatterCenter.formatSignedQuantity(totalLots, 2, "手");
    }

    // 统一处理悬浮窗盈亏显示，零值时按产品要求显示为 $-。
    private static String formatVisiblePnl(double totalPnl) {
        if (shouldUseNeutralPnlStyle(totalPnl)) {
            return "$-";
        }
        return IndicatorFormatterCenter.formatMoney(totalPnl, 1, false);
    }

}
