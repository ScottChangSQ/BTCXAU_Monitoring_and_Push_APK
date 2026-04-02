/*
 * 悬浮窗文案格式工具，负责把产品名与盈亏金额拼成统一的一行文本。
 */
package com.binance.monitor.ui.floating;

import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;

final class FloatingWindowTextFormatter {

    private static final double ZERO_EPSILON = 1e-9;

    private FloatingWindowTextFormatter() {
    }

    // 生成“产品（盈亏）”格式的标题，隐私开启时仅隐藏金额部分。
    static String formatCardTitle(String label, double totalPnl, boolean masked) {
        String safeLabel = label == null ? "" : label.trim();
        String pnlText = formatPnlAmount(totalPnl, masked);
        return safeLabel + "（" + pnlText + "）";
    }

    // 统一格式化悬浮窗盈亏金额，顶部汇总和产品标题都复用这套规则。
    static String formatPnlAmount(double totalPnl, boolean masked) {
        return masked ? SensitiveDisplayMasker.MASK_TEXT : formatVisiblePnl(totalPnl);
    }

    // 判断盈亏是否应按中性色展示，避免 0 值仍被误显示成涨色或跌色。
    static boolean shouldUseNeutralPnlStyle(double totalPnl) {
        return Math.abs(totalPnl) < ZERO_EPSILON;
    }

    // 统一处理悬浮窗盈亏显示，零值时按产品要求显示为 $-。
    private static String formatVisiblePnl(double totalPnl) {
        if (shouldUseNeutralPnlStyle(totalPnl)) {
            return "$-";
        }
        return FormatUtils.formatSignedMoneyNoDecimal(totalPnl);
    }
}
