/*
 * 行情图页显示辅助，负责处理本地预显示与网络回填的合并，以及是否需要阻塞式 loading。
 * 与 MarketChartActivity 配合，减少切周期时的卡顿和短窗口覆盖问题。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MarketChartDisplayHelper {

    private MarketChartDisplayHelper() {
    }

    // 先保留本地预显示，再让网络结果覆盖同一时间桶，避免更短网络窗口把当前图表“盖短”。
    static List<CandleEntry> mergeDisplaySeries(@Nullable List<CandleEntry> preview,
                                                @Nullable List<CandleEntry> fetched,
                                                int limit) {
        Map<Long, CandleEntry> merged = new LinkedHashMap<>();
        appendSeries(merged, preview);
        appendSeries(merged, fetched);
        List<CandleEntry> result = new ArrayList<>(merged.values());
        result.sort((left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        int safeLimit = Math.max(1, limit);
        if (result.size() <= safeLimit) {
            return result;
        }
        return new ArrayList<>(result.subList(result.size() - safeLimit, result.size()));
    }

    // 只有完全没有可见 K 线时，才显示阻塞式 loading；否则静默后台回填即可。
    static boolean shouldShowBlockingLoading(boolean autoRefresh,
                                            @Nullable List<CandleEntry> visibleCandles) {
        return !autoRefresh && (visibleCandles == null || visibleCandles.isEmpty());
    }

    // 把一组 K 线按 openTime 合并进结果，同时间桶以后到的数据覆盖先到的数据。
    private static void appendSeries(Map<Long, CandleEntry> target,
                                     @Nullable List<CandleEntry> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (CandleEntry item : source) {
            if (item == null) {
                continue;
            }
            target.put(item.getOpenTime(), item);
        }
    }
}
