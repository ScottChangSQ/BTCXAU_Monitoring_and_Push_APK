/*
 * 图表持久化窗口辅助，负责在落库前剔除仍未闭合的最新 patch。
 * 与 MarketChartActivity 配合，保证 Room 里只保存闭合历史快照。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.List;

final class ChartPersistenceWindowHelper {

    private ChartPersistenceWindowHelper() {
    }

    // 持久化前剔除最后一根尚未闭合的 patch，避免恢复缓存时把活 K 线当历史真值。
    static List<CandleEntry> retainClosedCandles(@Nullable List<CandleEntry> source, long nowMs) {
        List<CandleEntry> result = source == null ? new ArrayList<>() : new ArrayList<>(source);
        if (result.isEmpty()) {
            return result;
        }
        CandleEntry latest = result.get(result.size() - 1);
        if (latest != null && latest.getCloseTime() >= nowMs) {
            result.remove(result.size() - 1);
        }
        return result;
    }
}
