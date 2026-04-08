/*
 * 图表页交易辅助类，负责处理图表品种到 MT5 品种的映射、参考价回退和表单数值解析。
 * 与 MarketChartActivity、第一阶段交易命令工厂配合，避免页面层散落重复判断。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.util.ProductSymbolMapper;

import java.util.List;

public final class MarketChartTradeSupport {

    private MarketChartTradeSupport() {
    }

    // 把图表品种转换成 MT5 交易品种。
    public static String toTradeSymbol(@Nullable String chartSymbol) {
        return ProductSymbolMapper.toTradeSymbol(chartSymbol);
    }

    // 统一解析当前交易参考价，优先取最新 K 线收盘价。
    public static double resolveReferencePrice(@Nullable List<CandleEntry> loadedCandles,
                                               @Nullable PositionItem positionItem,
                                               @Nullable PositionItem pendingOrderItem) {
        if (loadedCandles != null && !loadedCandles.isEmpty()) {
            CandleEntry latest = loadedCandles.get(loadedCandles.size() - 1);
            if (latest != null && latest.getClose() > 0d) {
                return latest.getClose();
            }
        }
        if (positionItem != null) {
            if (positionItem.getLatestPrice() > 0d) {
                return positionItem.getLatestPrice();
            }
            if (positionItem.getCostPrice() > 0d) {
                return positionItem.getCostPrice();
            }
        }
        if (pendingOrderItem != null) {
            if (pendingOrderItem.getPendingPrice() > 0d) {
                return pendingOrderItem.getPendingPrice();
            }
            if (pendingOrderItem.getLatestPrice() > 0d) {
                return pendingOrderItem.getLatestPrice();
            }
        }
        return 0d;
    }

    // 解析可选输入，空值或非法值时回退默认值。
    public static double parseOptionalDouble(@Nullable String text, double fallbackValue) {
        String normalized = safe(text);
        if (normalized.isEmpty()) {
            return fallbackValue;
        }
        try {
            double value = Double.parseDouble(normalized);
            return Double.isFinite(value) ? value : fallbackValue;
        } catch (Exception ignored) {
            return fallbackValue;
        }
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
