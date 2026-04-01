/*
 * 已加载历史K线异常回填测试，确保图表不再只依赖通知记录或服务端最近几根K线。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.SymbolConfig;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HistoricalAbnormalRecordBuilderTest {
    private static final long BASE_TIME = 1_000_000L;

    @Test
    public void shouldDetectHistoricalAbnormalCandlesFromLoadedCandles() {
        SymbolConfig config = new SymbolConfig("BTCUSDT", 100d, 1_000d, 5d, true, true, true);
        List<CandleEntry> candles = Arrays.asList(
                buildCandle(BASE_TIME, 10d, 12d, 150d, 2_000d),
                buildCandle(BASE_TIME + 60_000L, 12d, 12.4d, 10d, 80d),
                buildCandle(BASE_TIME + 120_000L, 12.4d, 20d, 50d, 200d)
        );

        List<AbnormalRecord> records = HistoricalAbnormalRecordBuilder.buildFromCandles(
                "BTCUSDT",
                candles,
                config,
                false
        );

        assertEquals(2, records.size());
        assertEquals(BASE_TIME + 59_999L, records.get(0).getCloseTime());
        assertTrue(records.get(0).getTriggerSummary().contains("成交量"));
        assertEquals(BASE_TIME + 179_999L, records.get(1).getCloseTime());
        assertTrue(records.get(1).getTriggerSummary().contains("价格变化"));
    }

    @Test
    public void shouldMergeStoredAndHistoricalRecordsWithoutDuplicatingSameCandle() {
        AbnormalRecord stored = new AbnormalRecord(
                "stored",
                "BTCUSDT",
                BASE_TIME + 60_000L,
                BASE_TIME + 59_999L,
                10d,
                12d,
                150d,
                2_000d,
                2d,
                20d,
                "成交量 / 成交额"
        );
        AbnormalRecord derivedSameCandle = new AbnormalRecord(
                "derived",
                "BTCUSDT",
                BASE_TIME + 60_100L,
                BASE_TIME + 59_999L,
                10d,
                12d,
                150d,
                2_000d,
                2d,
                20d,
                "成交量 / 成交额"
        );
        AbnormalRecord derivedNewCandle = new AbnormalRecord(
                "derived-new",
                "BTCUSDT",
                BASE_TIME + 120_100L,
                BASE_TIME + 119_999L,
                12d,
                18d,
                80d,
                1_200d,
                6d,
                50d,
                "价格变化 / 成交额"
        );

        List<AbnormalRecord> merged = HistoricalAbnormalRecordBuilder.merge(
                Collections.singletonList(stored),
                Arrays.asList(derivedSameCandle, derivedNewCandle)
        );

        assertEquals(2, merged.size());
        assertEquals(BASE_TIME + 59_999L, merged.get(0).getCloseTime());
        assertEquals(BASE_TIME + 119_999L, merged.get(1).getCloseTime());
    }

    private CandleEntry buildCandle(long openTime,
                                    double open,
                                    double close,
                                    double volume,
                                    double quoteVolume) {
        return new CandleEntry(
                "BTCUSDT",
                openTime,
                openTime + 59_999L,
                open,
                Math.max(open, close),
                Math.min(open, close),
                close,
                volume,
                quoteVolume
        );
    }
}
