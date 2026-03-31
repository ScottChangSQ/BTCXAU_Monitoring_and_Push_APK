/*
 * 验证图表历史合并逻辑会保留旧数据，并按 openTime 去重，
 * 避免切周期或刷新后把已经拉过的历史 K 线丢掉。
 */
package com.binance.monitor.data.local.db.repository;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ChartHistoryRepositoryTest {

    // 新旧 K 线合并后，应保留旧历史，并用最新同 openTime 数据覆盖旧值。
    @Test
    public void mergeCandlesKeepsHistoryAndDeduplicatesByOpenTime() {
        List<CandleEntry> existing = Arrays.asList(
                candle(1000L, 1999L, 100d),
                candle(2000L, 2999L, 101d)
        );
        List<CandleEntry> incoming = Arrays.asList(
                candle(2000L, 2999L, 105d),
                candle(3000L, 3999L, 106d)
        );

        List<CandleEntry> merged = ChartHistoryRepository.mergeCandles(existing, incoming);

        assertEquals(3, merged.size());
        assertEquals(1000L, merged.get(0).getOpenTime());
        assertEquals(2000L, merged.get(1).getOpenTime());
        assertEquals(105d, merged.get(1).getClose(), 0.0001d);
        assertEquals(3000L, merged.get(2).getOpenTime());
    }

    private CandleEntry candle(long openTime, long closeTime, double close) {
        return new CandleEntry("BTCUSDT", openTime, closeTime, close, close, close, close, 1d, 1d);
    }
}
