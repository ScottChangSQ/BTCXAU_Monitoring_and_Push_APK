/*
 * 图表历史分页辅助，负责从服务端返回窗口里提取“真正需要追加到左侧”的更早 K 线。
 * 供图表页和绘图控件共用，避免同一套 older 过滤逻辑分散在多处。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ChartHistoryPagingHelper {

    private ChartHistoryPagingHelper() {
    }

    // 从服务端返回窗口中提取“当前窗口最左侧之前、且本地还没有”的历史 K 线。
    static List<CandleEntry> resolveOlderCandles(@Nullable List<CandleEntry> existing,
                                                 @Nullable List<CandleEntry> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return new ArrayList<>();
        }
        if (existing == null || existing.isEmpty()) {
            List<CandleEntry> all = new ArrayList<>(incoming);
            Collections.sort(all, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
            return all;
        }
        long oldest = existing.get(0).getOpenTime();
        Set<Long> existingOpenTimes = new HashSet<>();
        for (CandleEntry item : existing) {
            if (item != null) {
                existingOpenTimes.add(item.getOpenTime());
            }
        }
        List<CandleEntry> older = new ArrayList<>();
        for (CandleEntry item : incoming) {
            if (item == null) {
                continue;
            }
            long openTime = item.getOpenTime();
            if (openTime < oldest && !existingOpenTimes.contains(openTime)) {
                older.add(item);
                existingOpenTimes.add(openTime);
            }
        }
        Collections.sort(older, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        return older;
    }
}
