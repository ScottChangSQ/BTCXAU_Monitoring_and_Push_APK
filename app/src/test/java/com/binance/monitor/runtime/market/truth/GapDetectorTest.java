package com.binance.monitor.runtime.market.truth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class GapDetectorTest {

    @Test
    public void detectMinuteGap_shouldMarkMissingWindowByTimeContinuity() {
        GapDetector detector = new GapDetector();
        List<CandleEntry> minutes = Arrays.asList(
                minute(1_713_916_800_000L, 68_000.0, 68_010.0),
                minute(1_713_916_920_000L, 68_010.0, 68_030.0)
        );

        GapDetector.Gap gap = detector.findMinuteGap(minutes, 60_000L);

        assertEquals(1_713_916_860_000L, gap.getMissingStartOpenTime());
        assertEquals(1_713_916_919_999L, gap.getMissingEndCloseTime());
    }

    @Test
    public void hasMinuteGap_shouldReturnTrueWhenInternalMinuteMissing() {
        GapDetector detector = new GapDetector();

        boolean hasGap = detector.hasMinuteGap(Arrays.asList(
                minute(1_713_916_800_000L, 68_000.0, 68_010.0),
                minute(1_713_916_920_000L, 68_010.0, 68_030.0)
        ));

        assertTrue(hasGap);
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
