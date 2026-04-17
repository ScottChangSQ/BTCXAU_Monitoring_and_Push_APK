package com.binance.monitor.runtime.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.state.model.AccountRuntimeSnapshot;
import com.binance.monitor.runtime.state.model.ChartProductRuntimeModel;
import com.binance.monitor.runtime.state.model.FloatingCardRuntimeModel;
import com.binance.monitor.runtime.state.model.ProductRuntimeSnapshot;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class UnifiedRuntimeSnapshotStoreTest {

    private UnifiedRuntimeSnapshotStore store;

    @Before
    public void setUp() {
        store = UnifiedRuntimeSnapshotStore.getInstance();
        store.clearAccountRuntime();
    }

    @Test
    public void productRevisionShouldNotAdvanceWhenOtherSymbolChanges() {
        store.applyAccountCache(buildCache("BTCUSD", 0.10d, 12.30d));
        long btcRevision = store.selectProduct("BTCUSD").getProductRevision();

        store.applyAccountCache(new AccountStatsPreloadManager.Cache(
                true,
                new AccountSnapshot(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Arrays.asList(
                                buildPosition("BTCUSD", 0.10d, 12.30d),
                                buildPosition("XAUUSD", 0.20d, 5.20d)
                        ),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                "7400048",
                "ICMarketsSC-MT5-6",
                "stream",
                "gateway",
                1000L,
                "",
                1001L,
                "history-1"
        ));

        assertEquals(btcRevision, store.selectProduct("BTCUSD").getProductRevision());
    }

    @Test
    public void accountRuntimeShouldTrackLatestIdentityAndRevision() {
        store.applyAccountCache(buildCache("BTCUSD", 0.10d, 12.30d));

        AccountRuntimeSnapshot snapshot = store.getAccountRuntimeSnapshot();
        assertNotNull(snapshot);
        assertEquals("7400048@ICMarketsSC-MT5-6", snapshot.getAccountKey());
        assertEquals(1L, snapshot.getAccountRevision());
    }

    @Test
    public void productSnapshotShouldExposeSummaryTexts() {
        store.applyAccountCache(buildCache("BTCUSD", 0.10d, 12.30d));

        ProductRuntimeSnapshot snapshot = store.selectProduct("BTCUSD");
        assertEquals("BTCUSD", snapshot.getSymbol());
        assertEquals(1, snapshot.getPositionCount());
        assertEquals("挂单：--", snapshot.getPendingSummaryText());
    }

    @Test
    public void productSnapshotShouldExposeSignedLotsAndCompactDisplayLabel() {
        store.applyAccountCache(buildSellCache("BTCUSD", 0.10d, -12.30d));

        ProductRuntimeSnapshot snapshot = store.selectProduct("BTCUSD");
        assertEquals(0.10d, snapshot.getTotalLots(), 0.0001d);
        assertEquals(-0.10d, snapshot.getSignedLots(), 0.0001d);
        assertEquals("BTCUSD", snapshot.getDisplayLabel());
        assertEquals("BTC", snapshot.getCompactDisplayLabel());
    }

    @Test
    public void productSnapshotShouldPreferSnapshotProductNameAsDisplayLabel() {
        store.applyAccountCache(new AccountStatsPreloadManager.Cache(
                true,
                new AccountSnapshot(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.singletonList(new PositionItem(
                                "比特币",
                                "BTCUSD",
                                "Buy",
                                1L,
                                10L,
                                0L,
                                0.10d,
                                0.10d,
                                100d,
                                110d,
                                0d,
                                0.1d,
                                0d,
                                12.3d,
                                0.1d,
                                0d,
                                0,
                                0d,
                                0d,
                                0d,
                                0d
                        )),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                "7400048",
                "ICMarketsSC-MT5-6",
                "stream",
                "gateway",
                1000L,
                "",
                1001L,
                "history-1"
        ));

        ProductRuntimeSnapshot snapshot = store.selectProduct("BTCUSD");
        assertEquals("比特币", snapshot.getDisplayLabel());
        assertEquals("BTC", snapshot.getCompactDisplayLabel());
    }

    @Test
    public void uiSelectorsShouldMatchMarketSymbolToTradeRuntime() {
        store.applyAccountCache(buildCache("BTCUSD", 0.10d, 12.30d));

        ChartProductRuntimeModel chartRuntime = store.selectChartProductRuntime("BTC");
        FloatingCardRuntimeModel floatingRuntime = store.selectFloatingCard("BTCUSDT");

        assertEquals("BTCUSD", chartRuntime.getProductRuntimeSnapshot().getSymbol());
        assertEquals("BTCUSD", floatingRuntime.getProductRuntimeSnapshot().getSymbol());
    }

    private static AccountStatsPreloadManager.Cache buildCache(String symbol, double quantity, double pnl) {
        return new AccountStatsPreloadManager.Cache(
                true,
                new AccountSnapshot(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.singletonList(buildPosition(symbol, quantity, pnl)),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                "7400048",
                "ICMarketsSC-MT5-6",
                "stream",
                "gateway",
                1000L,
                "",
                1001L,
                "history-1"
        );
    }

    private static AccountStatsPreloadManager.Cache buildSellCache(String symbol, double quantity, double pnl) {
        return new AccountStatsPreloadManager.Cache(
                true,
                new AccountSnapshot(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.singletonList(buildSellPosition(symbol, quantity, pnl)),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                "7400048",
                "ICMarketsSC-MT5-6",
                "stream",
                "gateway",
                1000L,
                "",
                1001L,
                "history-1"
        );
    }

    private static PositionItem buildPosition(String symbol, double quantity, double pnl) {
        return new PositionItem(
                symbol,
                symbol,
                "Buy",
                1L,
                10L,
                0L,
                quantity,
                quantity,
                100d,
                110d,
                0d,
                0d,
                0d,
                pnl,
                0.1d,
                0d,
                0,
                0d,
                0d,
                0d,
                0d
        );
    }

    private static PositionItem buildSellPosition(String symbol, double quantity, double pnl) {
        return new PositionItem(
                symbol,
                symbol,
                "Sell",
                2L,
                20L,
                0L,
                quantity,
                quantity,
                100d,
                110d,
                0d,
                0.1d,
                0d,
                pnl,
                0.1d,
                0d,
                0,
                0d,
                0d,
                0d,
                0d
        );
    }
}
