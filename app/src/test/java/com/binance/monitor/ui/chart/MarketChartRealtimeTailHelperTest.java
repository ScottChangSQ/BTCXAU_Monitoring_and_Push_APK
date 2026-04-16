package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MarketChartRealtimeTailHelperTest {

    @Test
    public void buildRealtimeDisplayCandlesShouldNotTreatMonthlyIntervalAsMinute() {
        MarketChartRealtimeTailHelper helper = new MarketChartRealtimeTailHelper();
        MarketChartDataCoordinator.IntervalSelection monthly = new MarketChartDataCoordinator.IntervalSelection(
                "1M",
                "1M",
                1500,
                false
        );
        CandleEntry realtimeBase = new CandleEntry(
                "BTCUSDT",
                1_706_745_600_000L,
                1_706_745_600_000L + 60_000L - 1L,
                100d,
                101d,
                99d,
                100.5d,
                1d,
                10d
        );
        List<CandleEntry> loadedCandles = Arrays.asList(
                realtimeBase
        );

        List<CandleEntry> display = helper.buildRealtimeDisplayCandles(
                "BTCUSDT",
                monthly,
                loadedCandles,
                realtimeBase,
                null
        );

        assertFalse(display.equals(loadedCandles));
    }
}
