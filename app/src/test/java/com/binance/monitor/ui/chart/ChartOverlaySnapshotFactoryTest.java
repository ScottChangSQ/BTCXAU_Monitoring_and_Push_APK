package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChartOverlaySnapshotFactoryTest {

    @Test
    public void buildShouldReturnEmptySnapshotWhenAccountSnapshotMissing() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory();

        ChartOverlaySnapshot snapshot = factory.build("BTC", Collections.emptyList(), null, null);

        assertEquals("持仓盈亏: -- | 持仓收益率: --", snapshot.getPositionSummaryText());
        assertEquals("更新时间 --", snapshot.getOverlayMetaText());
        assertTrue(snapshot.getPositionAnnotations().isEmpty());
        assertTrue(snapshot.getPendingAnnotations().isEmpty());
        assertTrue(snapshot.getHistoryTradeAnnotations().isEmpty());
        assertNotEquals("", snapshot.getSignature());
    }

    @Test
    public void buildShouldCreateLightweightOverlayForSelectedSymbol() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory();
        List<CandleEntry> candles = Arrays.asList(
                new CandleEntry("BTCUSDT", 1710000000000L, 1710000059999L, 100d, 105d, 99d, 102d, 12d, 1200d),
                new CandleEntry("BTCUSDT", 1710000060000L, 1710000119999L, 102d, 106d, 101d, 103d, 10d, 1030d)
        );
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                new AccountSnapshot(
                        Arrays.asList(
                                new AccountMetric("总资产", "$1,000.00"),
                                new AccountMetric("净资产", "$980.00")
                        ),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.singletonList(createPosition("BTCUSD", "Buy", 11L, 21L, 1710000000000L, 2d, 100d, 102d, 20d)),
                        Collections.singletonList(createPending("BTCUSD", "Sell", 0L, 31L, 1710000060000L, 1d, 104d)),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "https://example.test",
                1710000123456L,
                "",
                1710000123456L,
                "rev-btc"
        );

        ChartOverlaySnapshot snapshot = factory.build("BTC", candles, cache.getSnapshot(), cache);

        assertEquals(1, snapshot.getPositionAnnotations().size());
        assertEquals(1, snapshot.getPendingAnnotations().size());
        assertEquals("持仓盈亏: +$20.00 | 持仓收益率: +2.00%", snapshot.getPositionSummaryText());
        assertTrue(snapshot.getOverlayMetaText().startsWith("更新时间 "));
        assertNotEquals("", snapshot.getSignature());
        assertEquals(100d, snapshot.getAggregateCostAnnotation().price, 0.0001d);
    }

    @Test
    public void buildShouldUseOverviewPnlSummaryInsteadOfAnnotationCounts() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory();
        List<CandleEntry> candles = Arrays.asList(
                new CandleEntry("BTCUSDT", 1710000000000L, 1710000059999L, 100d, 105d, 99d, 102d, 12d, 1200d),
                new CandleEntry("BTCUSDT", 1710000060000L, 1710000119999L, 102d, 106d, 101d, 103d, 10d, 1030d)
        );
        AccountSnapshot snapshotData = new AccountSnapshot(
                Arrays.asList(
                        new AccountMetric("总资产", "$1,000.00"),
                        new AccountMetric("净资产", "$980.00")
                ),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(createPosition("BTCUSD", "Buy", 11L, 21L, 1710000000000L, 2d, 100d, 102d, 20d, 2.5d)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                snapshotData,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "https://example.test",
                1710000123456L,
                "",
                1710000123456L,
                "rev-btc"
        );

        ChartOverlaySnapshot snapshot = factory.build("BTC", candles, snapshotData, cache);

        assertEquals("持仓盈亏: +$22.50 | 持仓收益率: +2.25%", snapshot.getPositionSummaryText());
    }

    @Test
    public void buildShouldStillExposePositionSummaryWithoutTotalAssetMetrics() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory();
        List<CandleEntry> candles = Arrays.asList(
                new CandleEntry("BTCUSDT", 1710000000000L, 1710000059999L, 100d, 105d, 99d, 102d, 12d, 1200d),
                new CandleEntry("BTCUSDT", 1710000060000L, 1710000119999L, 102d, 106d, 101d, 103d, 10d, 1030d)
        );
        AccountSnapshot snapshotData = new AccountSnapshot(
                Collections.singletonList(new AccountMetric("保证金", "$300.00")),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(createPosition("BTCUSD", "Buy", 11L, 21L, 1710000000000L, 2d, 100d, 102d, 20d, 2.5d)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        ChartOverlaySnapshot snapshot = factory.build("BTC", candles, snapshotData, null);

        assertEquals("持仓盈亏: +$22.50 | 持仓收益率: +11.25%", snapshot.getPositionSummaryText());
    }

    private static PositionItem createPosition(String code,
                                               String side,
                                               long positionTicket,
                                               long orderId,
                                               long openTime,
                                               double quantity,
                                               double costPrice,
                                               double latestPrice,
                                               double totalPnl) {
        return createPosition(code, side, positionTicket, orderId, openTime, quantity, costPrice, latestPrice, totalPnl, 0d);
    }

    private static PositionItem createPosition(String code,
                                               String side,
                                               long positionTicket,
                                               long orderId,
                                               long openTime,
                                               double quantity,
                                               double costPrice,
                                               double latestPrice,
                                               double totalPnl,
                                               double storageFee) {
        return new PositionItem(
                code,
                code,
                side,
                positionTicket,
                orderId,
                openTime,
                quantity,
                quantity,
                costPrice,
                latestPrice,
                latestPrice * quantity,
                0.2d,
                10d,
                totalPnl,
                0.02d,
                0d,
                0,
                0d,
                0d,
                0d,
                storageFee
        );
    }

    private static PositionItem createPending(String code,
                                              String side,
                                              long positionTicket,
                                              long orderId,
                                              long openTime,
                                              double pendingLots,
                                              double pendingPrice) {
        return new PositionItem(
                code,
                code,
                side,
                positionTicket,
                orderId,
                openTime,
                0d,
                0d,
                pendingPrice,
                pendingPrice,
                0d,
                0d,
                0d,
                0d,
                0d,
                pendingLots,
                1,
                pendingPrice,
                0d,
                0d,
                0d
        );
    }
}
