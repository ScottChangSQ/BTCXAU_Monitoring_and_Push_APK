package com.binance.monitor.runtime.market.truth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.runtime.market.truth.model.CurrentMinuteSnapshot;
import com.binance.monitor.runtime.market.truth.model.MarketDisplaySeries;
import com.binance.monitor.runtime.market.truth.model.MarketTruthSnapshot;

import org.junit.Test;

import java.util.Arrays;

public class MarketTruthCenterTest {

    @Test
    public void applyStreamDraft_shouldDriveLatestPriceAndOneMinuteTailFromSameSource() {
        MarketTruthCenter center = new MarketTruthCenter(
                new MinuteBaseStore(),
                new IntervalProjectionStore(),
                new GapDetector()
        );

        CandleEntry draft = new CandleEntry(
                "BTCUSDT",
                1_713_916_800_000L,
                1_713_916_859_999L,
                68_000.0,
                68_080.0,
                67_980.0,
                68_050.5,
                12.0,
                816_000.0
        );

        center.applyStreamDraft("BTCUSDT", draft, 68_050.5, 1_713_916_840_000L);
        MarketTruthSnapshot snapshot = center.getSnapshot();

        assertEquals(68_050.5, snapshot.selectLatestPrice("BTCUSDT"), 0.0001d);
        CurrentMinuteSnapshot currentMinute = snapshot.selectCurrentMinute("BTCUSDT");
        assertEquals(68_050.5, currentMinute.getLatestPrice(), 0.0001d);
        assertEquals(12.0, currentMinute.getVolume(), 0.0001d);
        assertEquals(816_000.0, currentMinute.getAmount(), 0.0001d);
        MarketDisplaySeries display = snapshot.selectDisplaySeries("BTCUSDT", "1m", 120);
        assertEquals(1, display.getCandles().size());
        assertEquals(68_050.5, display.getCandles().get(0).getClose(), 0.0001d);
    }

    @Test
    public void applyRestSeries_shouldUseMinuteProjectionToRefreshFiveMinuteTail() {
        MarketTruthCenter center = new MarketTruthCenter(
                new MinuteBaseStore(),
                new IntervalProjectionStore(),
                new GapDetector()
        );
        center.applyRestSeries(
                "BTCUSDT",
                "5m",
                Arrays.asList(
                        candle(1_713_916_500_000L, 67_900.0, 67_980.0),
                        candle(1_713_916_800_000L, 68_000.0, 68_010.0)
                ),
                null,
                1_713_916_830_000L
        );
        center.applyRepairedMinuteHistory(
                "BTCUSDT",
                Arrays.asList(
                        minute(1_713_916_800_000L, 68_000.0, 68_010.0),
                        minute(1_713_916_860_000L, 68_010.0, 68_015.0),
                        minute(1_713_916_920_000L, 68_015.0, 68_025.0)
                ),
                1_713_916_950_000L
        );
        center.applyStreamDraft(
                "BTCUSDT",
                minute(1_713_916_980_000L, 68_025.0, 68_030.0),
                68_030.0,
                1_713_916_985_000L
        );

        MarketDisplaySeries display = center.getSnapshot().selectDisplaySeries("BTCUSDT", "5m", 120);

        assertFalse(display.getCandles().isEmpty());
        assertEquals(68_030.0, display.getCandles().get(display.getCandles().size() - 1).getClose(), 0.0001d);
    }

    @Test
    public void sameMinuteStreamDraft_shouldAdvanceTruthProgressTimestampWhenServerPushesNewTick() {
        MarketTruthCenter center = new MarketTruthCenter(
                new MinuteBaseStore(),
                new IntervalProjectionStore(),
                new GapDetector()
        );
        CandleEntry firstDraft = minute(1_713_916_800_000L, 68_000.0, 68_020.0);
        CandleEntry secondDraft = minute(1_713_916_800_000L, 68_000.0, 68_050.0);

        center.applyStreamDraft("BTCUSDT", firstDraft, 68_020.0, 1_713_916_840_000L);
        long firstUpdatedAt = center.getSnapshot()
                .getSymbolState("BTCUSDT")
                .getLastTruthUpdateAt();

        center.applyStreamDraft("BTCUSDT", secondDraft, 68_050.0, 1_713_917_200_000L);
        long secondUpdatedAt = center.getSnapshot()
                .getSymbolState("BTCUSDT")
                .getLastTruthUpdateAt();

        assertEquals(68_050.0, center.getSnapshot().selectLatestPrice("BTCUSDT"), 0.0001d);
        assertEquals(68_050.0, center.getSnapshot().selectCurrentMinute("BTCUSDT").getLatestPrice(), 0.0001d);
        assertEquals(1_713_917_200_000L, secondUpdatedAt);
        assertFalse(firstUpdatedAt == secondUpdatedAt);
    }

    @Test
    public void repairedMinuteWindow_withoutLatestPatch_shouldNotRollbackCurrentMinutePrice() {
        MarketTruthCenter center = new MarketTruthCenter(
                new MinuteBaseStore(),
                new IntervalProjectionStore(),
                new GapDetector()
        );
        CandleEntry draft = minute(1_713_916_980_000L, 68_025.0, 68_050.0);

        center.applyStreamDraft("BTCUSDT", draft, 68_050.0, 1_713_917_000_000L);
        center.applyRepairedMinuteWindow(
                "BTCUSDT",
                Arrays.asList(
                        minute(1_713_916_800_000L, 68_000.0, 68_010.0),
                        minute(1_713_916_860_000L, 68_010.0, 68_020.0)
                ),
                null,
                1_713_917_010_000L
        );

        assertEquals(68_050.0, center.getSnapshot().selectLatestPrice("BTCUSDT"), 0.0001d);
        assertEquals(68_050.0, center.getSnapshot().selectCurrentMinute("BTCUSDT").getLatestPrice(), 0.0001d);
    }

    private static CandleEntry candle(long openTime, double open, double close) {
        return new CandleEntry(
                "BTCUSDT",
                openTime,
                openTime + 5L * 60_000L - 1L,
                open,
                Math.max(open, close),
                Math.min(open, close),
                close,
                10d,
                100d
        );
    }

    private static CandleEntry minute(long openTime, double open, double close) {
        return new CandleEntry(
                "BTCUSDT",
                openTime,
                openTime + 60_000L - 1L,
                open,
                Math.max(open, close),
                Math.min(open, close),
                close,
                10d,
                100d
        );
    }
}
