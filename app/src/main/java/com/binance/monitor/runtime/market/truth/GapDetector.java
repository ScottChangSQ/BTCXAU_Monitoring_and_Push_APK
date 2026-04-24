/*
 * 断档检测器，负责按固定时间步长判断分钟历史是否存在缺口。
 */
package com.binance.monitor.runtime.market.truth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GapDetector {

    // 查找分钟序列中的首个缺口。
    @Nullable
    public Gap findMinuteGap(@Nullable List<CandleEntry> candles, long stepMs) {
        if (candles == null || candles.size() < 2 || stepMs <= 0L) {
            return null;
        }
        List<CandleEntry> sorted = new ArrayList<>();
        for (CandleEntry candle : candles) {
            if (candle != null) {
                sorted.add(candle);
            }
        }
        sorted.sort(Comparator.comparingLong(CandleEntry::getOpenTime));
        for (int index = 1; index < sorted.size(); index++) {
            CandleEntry previous = sorted.get(index - 1);
            CandleEntry current = sorted.get(index);
            long expectedOpenTime = previous.getOpenTime() + stepMs;
            if (current.getOpenTime() > expectedOpenTime) {
                return new Gap(expectedOpenTime, current.getOpenTime() - 1L);
            }
        }
        return null;
    }

    public boolean hasMinuteGap(@Nullable List<CandleEntry> candles) {
        return findMinuteGap(candles, 60_000L) != null;
    }

    public static final class Gap {
        private final long missingStartOpenTime;
        private final long missingEndCloseTime;

        public Gap(long missingStartOpenTime, long missingEndCloseTime) {
            this.missingStartOpenTime = missingStartOpenTime;
            this.missingEndCloseTime = missingEndCloseTime;
        }

        public long getMissingStartOpenTime() {
            return missingStartOpenTime;
        }

        public long getMissingEndCloseTime() {
            return missingEndCloseTime;
        }
    }
}
