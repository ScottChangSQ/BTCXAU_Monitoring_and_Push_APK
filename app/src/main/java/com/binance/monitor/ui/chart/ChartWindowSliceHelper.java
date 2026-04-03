/*
 * 图表窗口裁剪工具，负责把一整段历史裁成最近的可刷新窗口。
 * 供增量刷新使用，避免把左滑翻出的更早历史也一起带进最新窗口补尾与写盘。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.List;

final class ChartWindowSliceHelper {

    private ChartWindowSliceHelper() {
    }

    // 只保留最近 limit 根 K 线，供最新窗口补尾与写盘使用。
    static List<CandleEntry> takeLatest(@Nullable List<CandleEntry> source, int limit) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        int safeLimit = Math.max(1, limit);
        if (source.size() <= safeLimit) {
            return new ArrayList<>(source);
        }
        return new ArrayList<>(source.subList(source.size() - safeLimit, source.size()));
    }
}
