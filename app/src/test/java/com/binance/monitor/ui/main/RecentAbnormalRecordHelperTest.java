package com.binance.monitor.ui.main;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.AbnormalRecord;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RecentAbnormalRecordHelperTest {

    @Test
    public void buildRecentDisplayShouldMergeSameCloseTimeRecords() {
        long now = 2_000_000L;
        List<AbnormalRecord> source = new ArrayList<>();
        source.add(buildRecord("btc", AppConstants.SYMBOL_BTC, now - 1_000L, "成交量"));
        source.add(buildRecord("xau", AppConstants.SYMBOL_XAU, now - 1_000L, "价格变化"));

        List<AbnormalRecord> display = RecentAbnormalRecordHelper.buildRecentDisplay(source, now, 10);

        assertEquals(1, display.size());
        assertEquals("BOTH", display.get(0).getSymbol());
    }

    @Test
    public void buildRecentDisplayShouldApplyLimitAndIgnoreOldRecords() {
        long now = 5_000_000L;
        List<AbnormalRecord> source = new ArrayList<>();
        source.add(buildRecord("old", AppConstants.SYMBOL_BTC, now - 4_000_000L, "过旧"));
        for (int i = 0; i < 12; i++) {
            source.add(buildRecord("n" + i, AppConstants.SYMBOL_BTC, now - i * 1_000L, "记录" + i));
        }

        List<AbnormalRecord> display = RecentAbnormalRecordHelper.buildRecentDisplay(source, now, 10);

        assertEquals(10, display.size());
    }

    private static AbnormalRecord buildRecord(String id, String symbol, long closeTime, String summary) {
        return new AbnormalRecord(id, symbol, closeTime, closeTime, 1d, 2d, 3d, 4d, 5d, 6d, summary);
    }
}
