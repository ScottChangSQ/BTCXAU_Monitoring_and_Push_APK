package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class ChartSeriesSignatureHelperTest {

    @Test
    public void signatureShouldChangeWhenMiddleCandleChanges() {
        List<CandleEntry> baseline = Arrays.asList(
                candle(1_000L, 1d, 2d, 0.5d),
                candle(2_000L, 2d, 3d, 0.8d),
                candle(3_000L, 3d, 4d, 1.1d)
        );
        List<CandleEntry> corrected = Arrays.asList(
                candle(1_000L, 1d, 2d, 0.5d),
                new CandleEntry("BTCUSDT", 2_000L, 2_999L, 2d, 2.6d, 1.7d, 2.4d, 1.6d, 2.8d),
                candle(3_000L, 3d, 4d, 1.1d)
        );

        assertNotEquals(
                ChartSeriesSignatureHelper.build(baseline),
                ChartSeriesSignatureHelper.build(corrected)
        );
    }

    @Test
    public void signatureShouldStaySameForSameSeries() {
        List<CandleEntry> first = Arrays.asList(
                candle(1_000L, 1d, 2d, 0.5d),
                candle(2_000L, 2d, 3d, 0.8d)
        );
        List<CandleEntry> second = Arrays.asList(
                candle(1_000L, 1d, 2d, 0.5d),
                candle(2_000L, 2d, 3d, 0.8d)
        );

        assertEquals(
                ChartSeriesSignatureHelper.build(first),
                ChartSeriesSignatureHelper.build(second)
        );
    }

    private CandleEntry candle(long openTime, double open, double close, double volume) {
        return new CandleEntry(
                "BTCUSDT",
                openTime,
                openTime + 999L,
                open,
                Math.max(open, close) + 0.2d,
                Math.min(open, close) - 0.2d,
                close,
                volume,
                volume * 2d
        );
    }
}
