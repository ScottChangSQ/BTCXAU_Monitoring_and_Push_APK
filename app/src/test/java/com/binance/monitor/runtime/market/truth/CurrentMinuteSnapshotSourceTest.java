package com.binance.monitor.runtime.market.truth;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.runtime.market.truth.model.CurrentMinuteSnapshot;

import org.junit.Test;

public class CurrentMinuteSnapshotSourceTest {

    @Test
    public void currentMinuteSnapshotShouldShareLatestPriceVolumeAndAmountWithOneMinuteDraft() {
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
        CurrentMinuteSnapshot current = center.getSnapshot().selectCurrentMinute("BTCUSDT");

        assertEquals(68_050.5, current.getLatestPrice(), 0.0001d);
        assertEquals(12.0, current.getVolume(), 0.0001d);
        assertEquals(816_000.0, current.getAmount(), 0.0001d);
        assertEquals(1_713_916_800_000L, current.getOpenTime());
        assertEquals(1_713_916_859_999L, current.getCloseTime());
        assertEquals(1_713_916_840_000L, current.getUpdatedAt());
    }
}
