/*
 * 账户页次级区块后台计算测试，确保交易筛选和曲线投影可在纯计算阶段完成。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AccountDeferredSnapshotRenderHelperTest {

    @Test
    public void buildTradeListShouldRespectFilterAndSortConfiguration() {
        TradeRecordItem btcEarlyBuy = buildTrade(
                "BTCUSD", "Buy", 1_000L, 2_000L, 10d, 0d, 100d, 110d
        );
        TradeRecordItem btcLateSell = buildTrade(
                "BTCUSD", "Sell", 3_000L, 4_000L, 30d, 0d, 120d, 100d
        );
        TradeRecordItem xauBuy = buildTrade(
                "XAUUSD", "Buy", 5_000L, 6_000L, 20d, 0d, 200d, 220d
        );

        List<TradeRecordItem> filtered = AccountDeferredSnapshotRenderHelper.buildFilteredTrades(
                Arrays.asList(btcEarlyBuy, btcLateSell, xauBuy),
                new AccountDeferredSnapshotRenderHelper.TradeFilterRequest(
                        "BTCUSD",
                        false,
                        "全部方向",
                        true,
                        AccountDeferredSnapshotRenderHelper.SortMode.PROFIT,
                        true
                )
        );

        assertEquals(2, filtered.size());
        assertEquals(30d, filtered.get(0).getProfit(), 1e-9);
        assertEquals(10d, filtered.get(1).getProfit(), 1e-9);
    }

    @Test
    public void prepareShouldBuildCurveProjectionAndTradeAnalyticsTogether() {
        List<CurvePoint> curvePoints = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(2_000L, 105d, 104d, 0.20d),
                new CurvePoint(3_000L, 110d, 108d, 0.15d),
                new CurvePoint(4_000L, 120d, 118d, 0.05d)
        );
        TradeRecordItem buyTrade = buildTrade(
                "BTCUSD", "Buy", 1_000L, 3_000L, 12d, -1.5d, 100d, 112d
        );
        TradeRecordItem sellTrade = buildTrade(
                "BTCUSD", "Sell", 2_000L, 4_000L, -4d, -0.5d, 110d, 100d
        );

        AccountDeferredSnapshotRenderHelper.PreparedSnapshotSections prepared =
                AccountDeferredSnapshotRenderHelper.prepare(
                        new AccountDeferredSnapshotRenderHelper.PrepareRequest(
                                Arrays.asList(new AccountMetric("胜率", "66%")),
                                Arrays.asList(buyTrade, sellTrade),
                                curvePoints,
                                AccountTimeRange.ALL,
                                false,
                                0L,
                                0L,
                                AccountDeferredSnapshotRenderHelper.TradePnlSideMode.ALL,
                                AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis.CLOSE_TIME,
                                new AccountDeferredSnapshotRenderHelper.TradeFilterRequest(
                                        "全部产品",
                                        true,
                                        "全部方向",
                                        true,
                                        AccountDeferredSnapshotRenderHelper.SortMode.CLOSE_TIME,
                                        true
                                )
                        )
                );

        assertEquals(1, prepared.getTradeProducts().size());
        assertEquals("BTCUSD", prepared.getTradeProducts().get(0));
        assertEquals(2, prepared.getFilteredTrades().size());
        assertEquals(2, prepared.getTradeSummary().getTradeCount());
        assertEquals(8d, prepared.getTradeSummary().getTradeProfitTotal(), 1e-9);
        assertEquals(-2d, prepared.getTradeSummary().getTradeStorageTotal(), 1e-9);
        assertEquals(4, prepared.getCurveProjection().getDisplayedCurvePoints().size());
        assertEquals(1, prepared.getTradePnlEntries().size());
        assertEquals(7, prepared.getHoldingDurationBuckets().size());
        assertTrue(prepared.getTradeWeekdayEntries().size() > 0);
    }

    private static TradeRecordItem buildTrade(String code,
                                              String side,
                                              long openTime,
                                              long closeTime,
                                              double profit,
                                              double storageFee,
                                              double openPrice,
                                              double closePrice) {
        return new TradeRecordItem(
                closeTime,
                code,
                code,
                side,
                closePrice,
                1d,
                100d,
                0d,
                "",
                profit,
                openTime,
                closeTime,
                storageFee,
                openPrice,
                closePrice,
                closeTime,
                closeTime + 1L,
                closeTime + 2L,
                1
        );
    }
}
