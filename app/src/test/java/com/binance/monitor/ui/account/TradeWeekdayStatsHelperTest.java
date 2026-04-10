package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.domain.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TradeWeekdayStatsHelperTest {

    @Test
    public void buildRowsShouldAggregateByCloseTimeFromMondayToSunday() {
        List<TradeRecordItem> trades = Arrays.asList(
                buildTrade(1704067200000L, 1704153600000L, 100d, -5d),
                buildTrade(1704067200000L, 1704153600000L, -50d, 0d),
                buildTrade(1704240000000L, 1704326400000L, 20d, 0d)
        );

        List<TradeWeekdayStatsHelper.Row> rows = TradeWeekdayStatsHelper.buildRows(
                trades,
                TradeWeekdayStatsHelper.TimeBasis.CLOSE_TIME
        );

        assertEquals("周一", rows.get(0).label);
        assertEquals(2, rows.get(1).tradeCount);
        assertEquals(45d, rows.get(1).totalPnl, 1e-6);
        assertEquals(1, rows.get(1).winCount);
        assertEquals(1, rows.get(1).lossCount);
        assertEquals(1, rows.get(3).tradeCount);
        assertEquals(20d, rows.get(3).totalPnl, 1e-6);
        assertEquals("周日", rows.get(6).label);
    }

    @Test
    public void buildRowsShouldUseOpenTimeWhenRequested() {
        List<TradeRecordItem> trades = Arrays.asList(
                buildTrade(1704067200000L, 1704326400000L, 80d, 0d),
                buildTrade(1704672000000L, 1704758400000L, -10d, 0d)
        );

        List<TradeWeekdayStatsHelper.Row> rows = TradeWeekdayStatsHelper.buildRows(
                trades,
                TradeWeekdayStatsHelper.TimeBasis.OPEN_TIME
        );

        assertEquals(2, rows.get(0).tradeCount);
        assertEquals(70d, rows.get(0).totalPnl, 1e-6);
        assertEquals(1, rows.get(0).winCount);
        assertEquals(1, rows.get(0).lossCount);
    }

    @Test
    public void buildRowsShouldUseCanonicalMillisecondTimestampsForWeekdayResolution() {
        TradeRecordItem trade = buildTrade(1704067200000L, 1704153600000L, 18d, 0d);

        List<TradeWeekdayStatsHelper.Row> openRows = TradeWeekdayStatsHelper.buildRows(
                Arrays.asList(trade),
                TradeWeekdayStatsHelper.TimeBasis.OPEN_TIME
        );
        List<TradeWeekdayStatsHelper.Row> closeRows = TradeWeekdayStatsHelper.buildRows(
                Arrays.asList(trade),
                TradeWeekdayStatsHelper.TimeBasis.CLOSE_TIME
        );

        assertEquals(1, openRows.get(0).tradeCount);
        assertEquals(18d, openRows.get(0).totalPnl, 1e-6);
        assertEquals(1, closeRows.get(1).tradeCount);
        assertEquals(18d, closeRows.get(1).totalPnl, 1e-6);
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
