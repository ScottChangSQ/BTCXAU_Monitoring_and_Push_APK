/*
 * K线十字光标弹窗数据测试，确保主图已开启的指标会进入长按弹窗。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.List;

public class KlinePopupDataHelperTest {

    @Test
    public void shouldAppendEnabledMainPaneIndicatorsToPopupRows() {
        CandleEntry candle = new CandleEntry("BTCUSDT", 1L, 2L, 100d, 110d, 90d, 105d, 12d, 34d);

        List<KlinePopupDataHelper.Row> rows = KlinePopupDataHelper.buildRows(
                candle,
                true,
                20,
                2,
                120.12d,
                110.11d,
                100.10d,
                true,
                7,
                99.95d,
                true,
                12,
                101.01d,
                true,
                14,
                102.02d
        );

        assertEquals("时间", rows.get(0).label);
        assertEquals("BOLL UP", rows.get(8).label);
        assertEquals("120.12", rows.get(8).value);
        assertEquals("BOLL MB", rows.get(9).label);
        assertEquals("MA(7)", rows.get(11).label);
        assertEquals("99.95", rows.get(11).value);
        assertEquals("EMA(12)", rows.get(12).label);
        assertEquals("SRA(14)", rows.get(13).label);
    }

    @Test
    public void shouldSkipDisabledOrInvalidIndicatorRows() {
        CandleEntry candle = new CandleEntry("BTCUSDT", 1L, 2L, 100d, 110d, 90d, 105d, 12d, 34d);

        List<KlinePopupDataHelper.Row> rows = KlinePopupDataHelper.buildRows(
                candle,
                false,
                20,
                2,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                true,
                7,
                Double.NaN,
                false,
                12,
                101.01d,
                false,
                14,
                102.02d
        );

        assertEquals(8, rows.size());
    }
}
