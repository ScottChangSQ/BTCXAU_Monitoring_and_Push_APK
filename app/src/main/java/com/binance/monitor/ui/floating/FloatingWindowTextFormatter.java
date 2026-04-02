/*
 * 悬浮窗文案格式工具，负责把产品名与盈亏金额拼成统一的一行文本。
 */
package com.binance.monitor.ui.floating;

import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;

final class FloatingWindowTextFormatter {

    private FloatingWindowTextFormatter() {
    }

    // 生成“产品（盈亏）”格式的标题，隐私开启时仅隐藏金额部分。
    static String formatCardTitle(String label, double totalPnl, boolean masked) {
        String safeLabel = label == null ? "" : label.trim();
        String pnlText = masked
                ? SensitiveDisplayMasker.MASK_TEXT
                : FormatUtils.formatSignedMoneyNoDecimal(totalPnl);
        return safeLabel + "（" + pnlText + "）";
    }
}
