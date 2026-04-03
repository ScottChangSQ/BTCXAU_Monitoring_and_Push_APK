package com.binance.monitor.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.binance.monitor.data.model.AbnormalRecord;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RecentAbnormalRecordHelperTest {

    @Test
    public void buildRecentDisplayShouldKeepLatestRawRecordsWithoutMerging() {
        long now = 2_000_000L;
        List<AbnormalRecord> source = new ArrayList<>();
        AbnormalRecord first = buildRecord("btc", "BTCUSDT", now - 1_000L, "成交量");
        AbnormalRecord second = buildRecord("xau", "XAUUSD", now - 1_000L, "价格变化");
        source.add(first);
        source.add(second);

        List<AbnormalRecord> display = RecentAbnormalRecordHelper.buildRecentDisplay(source, now, 10);

        assertEquals(2, display.size());
        assertSame(first, display.get(0));
        assertSame(second, display.get(1));
    }

    @Test
    public void buildRecentDisplayShouldApplyLimitWithoutTimeFiltering() {
        long now = 5_000_000L;
        List<AbnormalRecord> source = new ArrayList<>();
        AbnormalRecord old = buildRecord("old", "BTCUSDT", now - 4_000_000L, "过旧");
        source.add(old);
        for (int i = 0; i < 12; i++) {
            source.add(buildRecord("n" + i, "BTCUSDT", now - i * 1_000L, "记录" + i));
        }

        List<AbnormalRecord> display = RecentAbnormalRecordHelper.buildRecentDisplay(source, now, 10);

        assertEquals(10, display.size());
        assertSame(old, display.get(0));
    }

    private static AbnormalRecord buildRecord(String id, String symbol, long closeTime, String summary) {
        return new AbnormalRecord(id, symbol, closeTime, closeTime, 1d, 2d, 3d, 4d, 5d, 6d, summary);
    }
}
