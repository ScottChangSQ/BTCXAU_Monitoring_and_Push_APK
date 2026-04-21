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
import com.binance.monitor.runtime.state.model.ChartProductRuntimeModel;
import com.binance.monitor.runtime.state.model.ProductRuntimeSnapshot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ChartOverlaySnapshotFactoryTest {
    private static final ChartOverlaySnapshotFactory.ColorScheme COLOR_SCHEME =
            new ChartOverlaySnapshotFactory.ColorScheme(1, 2, 3, 4, 5);

    @Test
    public void buildShouldReturnEmptySnapshotWhenAccountSnapshotMissing() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory(COLOR_SCHEME);

        ChartOverlaySnapshot snapshot = factory.build("BTC", Collections.emptyList(), null, null);

        assertEquals("盈亏：-- | 持仓：--", snapshot.getPositionSummaryText());
        assertEquals("更新时间 --", snapshot.getOverlayMetaText());
        assertTrue(snapshot.getPositionAnnotations().isEmpty());
        assertTrue(snapshot.getPendingAnnotations().isEmpty());
        assertTrue(snapshot.getHistoryTradeAnnotations().isEmpty());
        assertNotEquals("", snapshot.getSignature());
    }

    @Test
    public void buildShouldCreateLightweightOverlayForSelectedSymbol() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory(COLOR_SCHEME);
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
        assertEquals("盈亏：+$20.00 | 持仓：2手", snapshot.getPositionSummaryText());
        assertTrue(snapshot.getOverlayMetaText().startsWith("更新时间 "));
        assertNotEquals("", snapshot.getSignature());
        assertEquals(100d, snapshot.getAggregateCostAnnotation().price, 0.0001d);
    }

    @Test
    public void buildShouldCreateTradeLayerSnapshotForLivePositionPendingAndTpSl() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory(COLOR_SCHEME);
        List<CandleEntry> candles = Arrays.asList(
                new CandleEntry("BTCUSDT", 1710000000000L, 1710000059999L, 100d, 105d, 99d, 102d, 12d, 1200d),
                new CandleEntry("BTCUSDT", 1710000060000L, 1710000119999L, 102d, 106d, 101d, 103d, 10d, 1030d)
        );
        AccountSnapshot snapshotData = new AccountSnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(createPosition("BTCUSD", "Buy", 11L, 21L, 1710000000000L, 2d, 100d, 102d, 20d, 0d, 110d, 95d)),
                Collections.singletonList(createPending("BTCUSD", "Sell", 0L, 31L, 1710000060000L, 1d, 104d, 108d, 96d)),
                Collections.emptyList(),
                Collections.emptyList()
        );

        ChartOverlaySnapshot snapshot = factory.build("BTC", candles, snapshotData, null);

        assertEquals(6, snapshot.getTradeLayerSnapshot().getLiveLines().size());
        assertEquals(ChartTradeLineState.LIVE_POSITION, snapshot.getTradeLayerSnapshot().getLiveLines().get(0).getState());
        assertEquals(ChartTradeLineState.LIVE_PENDING, snapshot.getTradeLayerSnapshot().getLiveLines().get(3).getState());
        assertEquals(ChartTradeLineState.LIVE_SL, snapshot.getTradeLayerSnapshot().getLiveLines().get(5).getState());
    }

    @Test
    public void buildShouldUseOverviewPnlSummaryInsteadOfAnnotationCounts() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory(COLOR_SCHEME);
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

        assertEquals("盈亏：+$22.50 | 持仓：2手", snapshot.getPositionSummaryText());
    }

    @Test
    public void buildShouldStillExposePositionSummaryWithoutTotalAssetMetrics() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory(COLOR_SCHEME);
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

        assertEquals("盈亏：+$22.50 | 持仓：2手", snapshot.getPositionSummaryText());
    }

    @Test
    public void buildShouldPreferUnifiedRuntimeSummaryWhenProvided() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory(COLOR_SCHEME);
        List<CandleEntry> candles = Arrays.asList(
                new CandleEntry("BTCUSDT", 1710000000000L, 1710000059999L, 100d, 105d, 99d, 102d, 12d, 1200d),
                new CandleEntry("BTCUSDT", 1710000060000L, 1710000119999L, 102d, 106d, 101d, 103d, 10d, 1030d)
        );
        AccountSnapshot snapshotData = new AccountSnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(createPosition("BTCUSD", "Buy", 11L, 21L, 1710000000000L, 2d, 100d, 102d, 20d)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        ChartOverlaySnapshot snapshot = factory.build(
                "BTC",
                candles,
                snapshotData,
                null,
                new ChartProductRuntimeModel(
                        new ProductRuntimeSnapshot("BTCUSD", 5L, 1, 0, 3d, 88d,
                                "盈亏：+$88.00 | 持仓：3手", "挂单：--")
                )
        );

        assertEquals("盈亏：+$88.00 | 持仓：3手", snapshot.getPositionSummaryText());
    }

    @Test
    public void buildShouldRespectRuntimeSignedLotsForShortSummary() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory(COLOR_SCHEME);
        List<CandleEntry> candles = Arrays.asList(
                new CandleEntry("BTCUSDT", 1710000000000L, 1710000059999L, 100d, 105d, 99d, 102d, 12d, 1200d),
                new CandleEntry("BTCUSDT", 1710000060000L, 1710000119999L, 102d, 106d, 101d, 103d, 10d, 1030d)
        );
        AccountSnapshot snapshotData = new AccountSnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(createPosition("BTCUSD", "Sell", 11L, 21L, 1710000000000L, 2d, 100d, 102d, -20d)),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList()
        );

        ChartOverlaySnapshot snapshot = factory.build(
                "BTC",
                candles,
                snapshotData,
                null,
                new ChartProductRuntimeModel(
                        new ProductRuntimeSnapshot("BTCUSD", 5L, 1, 0, 0.10d, -0.10d, -28d,
                                "BTCUSD", "BTC", "盈亏：-$28.00 | 持仓：-0.10手", "挂单：--")
                )
        );

        assertEquals("盈亏：-$28.00 | 持仓：-0.10手", snapshot.getPositionSummaryText());
    }

    @Test
    public void buildShouldRenderHistoryTradePnlInUsdInsteadOfMillionUnit() {
        ChartOverlaySnapshotFactory factory = new ChartOverlaySnapshotFactory(COLOR_SCHEME);
        List<CandleEntry> candles = Arrays.asList(
                new CandleEntry("BTCUSDT", 1710000000000L, 1710000059999L, 100d, 105d, 99d, 102d, 12d, 1200d),
                new CandleEntry("BTCUSDT", 1710000060000L, 1710000119999L, 102d, 106d, 101d, 103d, 10d, 1030d)
        );
        AccountSnapshot snapshotData = new AccountSnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(createTrade(
                        "BTCUSDT",
                        "buy",
                        1710000000000L,
                        1710000060000L,
                        100d,
                        103d,
                        12.34d,
                        0d,
                        9001L
                )),
                Collections.emptyList()
        );

        ChartOverlaySnapshot snapshot = factory.build("BTCUSDT", candles, snapshotData, null);

        assertEquals(3, snapshot.getHistoryTradeAnnotations().size());
        assertEquals("+$12.34", snapshot.getHistoryTradeAnnotations().get(1).label);
        assertEquals("盈亏 +$12.34", snapshot.getHistoryTradeAnnotations().get(1).detailLines[5]);
        assertFalse(snapshot.getHistoryTradeAnnotations().get(1).label.contains("M$"));
        assertFalse(snapshot.getHistoryTradeAnnotations().get(1).detailLines[5].contains("M$"));
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
        return createPosition(code, side, positionTicket, orderId, openTime, quantity, costPrice, latestPrice, totalPnl, 0d, 0d, 0d);
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
        return createPosition(code, side, positionTicket, orderId, openTime, quantity, costPrice, latestPrice, totalPnl, storageFee, 0d, 0d);
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
                                               double storageFee,
                                               double takeProfit,
                                               double stopLoss) {
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
                takeProfit,
                stopLoss,
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
        return createPending(code, side, positionTicket, orderId, openTime, pendingLots, pendingPrice, 0d, 0d);
    }

    private static PositionItem createPending(String code,
                                              String side,
                                              long positionTicket,
                                              long orderId,
                                              long openTime,
                                              double pendingLots,
                                              double pendingPrice,
                                              double takeProfit,
                                              double stopLoss) {
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
                takeProfit,
                stopLoss,
                0d
        );
    }

    private static com.binance.monitor.domain.account.model.TradeRecordItem createTrade(String code,
                                                                                         String side,
                                                                                         long openTime,
                                                                                         long closeTime,
                                                                                         double openPrice,
                                                                                         double closePrice,
                                                                                         double profit,
                                                                                         double storageFee,
                                                                                         long dealTicket) {
        return new com.binance.monitor.domain.account.model.TradeRecordItem(
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
                dealTicket,
                dealTicket,
                dealTicket,
                1
        );
    }
}
