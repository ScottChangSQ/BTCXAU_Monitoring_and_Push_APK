/*
 * 图表序列签名工具，负责为一整段 K 线生成稳定签名。
 * 供图表页判断本地窗口是否真的发生变化，避免中间桶修正时误跳过持久化。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.List;

final class ChartSeriesSignatureHelper {

    private ChartSeriesSignatureHelper() {
    }

    // 为整段 K 线生成签名，保证中间任意一根变化都会触发新签名。
    static String build(@Nullable List<CandleEntry> candles) {
        if (candles == null || candles.isEmpty()) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(candles.size());
        for (CandleEntry candle : candles) {
            if (candle == null) {
                builder.append("|null");
                continue;
            }
            builder.append('|').append(candle.getOpenTime())
                    .append(':').append(candle.getCloseTime())
                    .append(':').append(Double.doubleToLongBits(candle.getOpen()))
                    .append(':').append(Double.doubleToLongBits(candle.getHigh()))
                    .append(':').append(Double.doubleToLongBits(candle.getLow()))
                    .append(':').append(Double.doubleToLongBits(candle.getClose()))
                    .append(':').append(Double.doubleToLongBits(candle.getVolume()))
                    .append(':').append(Double.doubleToLongBits(candle.getQuoteVolume()));
        }
        return builder.toString();
    }
}
