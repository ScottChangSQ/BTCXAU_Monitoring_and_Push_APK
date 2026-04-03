package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TradeWeekdayBarChartHelperTest {

    @Test
    public void buildEntriesShouldKeepWeekdayOrderAndPnlValues() {
        List<TradeRecordItem> trades = Arrays.asList(
                buildTrade(1704067200000L, 1704067200000L, 60d, 0d),
                buildTrade(1704067200000L, 1704067200000L, 80d, 0d),
                buildTrade(1704067200000L, 1704067200000L, -20d, 0d),
                buildTrade(1704153600000L, 1704153600000L, -15d, 0d),
                buildTrade(1704153600000L, 1704153600000L, -20d, 0d)
        );
        List<TradeWeekdayStatsHelper.Row> rows = TradeWeekdayStatsHelper.buildRows(
                trades,
                TradeWeekdayStatsHelper.TimeBasis.CLOSE_TIME
        );

        List<TradeWeekdayBarChartHelper.Entry> entries = TradeWeekdayBarChartHelper.buildEntries(
                rows.subList(0, 2)
        );

        assertEquals(2, entries.size());
        assertEquals("周一", entries.get(0).label);
        assertEquals(120d, entries.get(0).pnl, 1e-6);
        assertEquals("3次  盈2/亏1", entries.get(0).summary);
        assertEquals("周二", entries.get(1).label);
        assertEquals(-35d, entries.get(1).pnl, 1e-6);
        assertEquals("2次  盈0/亏2", entries.get(1).summary);
    }

    private static TradeRecordItem buildTrade(long openTime, long closeTime, double profit, double storage) {
        return new TradeRecordItem(
                closeTime,
                "BTC",
                "BTCUSDT",
                "BUY",
                100d,
                1d,
                100d,
                0d,
                "",
                profit,
                openTime,
                closeTime,
                storage,
                100d,
                110d
        );
    }
}
