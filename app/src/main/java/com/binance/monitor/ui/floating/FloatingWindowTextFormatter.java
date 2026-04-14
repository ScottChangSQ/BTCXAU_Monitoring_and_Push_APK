/*
 * 悬浮窗文案格式工具，负责把产品名与盈亏金额拼成统一的两行文本。
 */
package com.binance.monitor.ui.floating;

import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.text.DecimalFormat;

final class FloatingWindowTextFormatter {

    private static final double ZERO_EPSILON = 1e-9;

    private FloatingWindowTextFormatter() {
    }

    // 生成“产品名\n盈亏金额”格式的标题，隐私开启时仅隐藏金额部分。
    static String formatCardTitle(String label,
                                  double totalLots,
                                  double totalPnl,
                                  boolean hasPosition,
                                  boolean masked) {
        String safeLabel = label == null ? "" : label.trim();
        String pnlText = formatCardPnlLine(totalLots, totalPnl, hasPosition, masked);
        return safeLabel + "\n" + pnlText;
    }

    // 统一格式化悬浮窗盈亏金额，顶部汇总和产品标题都复用这套规则。
    static String formatPnlAmount(double totalPnl, boolean masked) {
        return masked ? SensitiveDisplayMasker.MASK_TEXT : formatVisiblePnl(totalPnl);
    }

    // 统一格式化悬浮窗产品价格，改为一位小数并保留隐私遮罩行为。
    static String formatPriceText(double price, boolean masked) {
        String visibleText = FormatUtils.formatPriceOneDecimalWithUnit(price);
        if (visibleText.startsWith("$")) {
            visibleText = "$ " + visibleText.substring(1);
        }
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
        return "1M量 " + volumeText;
    }

    // 统一格式化悬浮窗 1 分钟成交额行。
    static String formatAmountLine(double amount, boolean masked) {
        String amountText = masked
                ? SensitiveDisplayMasker.MASK_TEXT
                : FormatUtils.formatAmountWithChineseUnit(amount);
        return "1M额 " + amountText;
    }

    // 统一格式化产品卡片第二行：有持仓时显示“总手数 + 盈亏”，无持仓时维持原有盈亏占位。
    static String formatCardPnlLine(double totalLots,
                                    double totalPnl,
                                    boolean hasPosition,
                                    boolean masked) {
        if (masked) {
            return SensitiveDisplayMasker.MASK_TEXT;
        }
        if (!hasPosition) {
            return formatVisiblePnl(totalPnl);
        }
        return formatSignedLots(totalLots) + formatVisiblePnl(totalPnl);
    }

    // 统一处理悬浮窗盈亏显示，零值时按产品要求显示为 $-。
    private static String formatVisiblePnl(double totalPnl) {
        if (shouldUseNeutralPnlStyle(totalPnl)) {
            return "$-";
        }
        return FormatUtils.formatSignedMoneyOneDecimal(totalPnl);
    }

    // 统一格式化悬浮窗卡片里的总手数，方向按净方向、大小按绝对手数汇总。
    private static String formatSignedLots(double totalLots) {
        String sign = totalLots < 0d ? "-" : "+";
        String amount = new DecimalFormat("0.00").format(Math.abs(totalLots));
        return "（" + sign + amount + "手）";
    }
}
