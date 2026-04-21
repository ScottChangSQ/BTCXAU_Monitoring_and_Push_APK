package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.data.model.v2.AccountHistoryPayload;
import com.binance.monitor.data.model.v2.AccountSnapshotPayload;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AccountStatsPreloadManagerTest {

    @Test
    public void resolveRuntimeConnectedShouldReturnFalseForRemoteLoggedOutSnapshot() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "resolveRuntimeConnected",
                JSONObject.class
        );
        method.setAccessible(true);

        JSONObject runtimeMeta = new JSONObject()
                .put("login", "")
                .put("server", "")
                .put("source", "remote_logged_out")
                .put("historyRevision", "empty-history-revision");

        boolean connected = (boolean) method.invoke(null, runtimeMeta);

        assertFalse(connected);
    }

    @Test
    public void resolveRuntimeConnectedShouldReturnTrueForActivatedRuntimeSnapshot() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "resolveRuntimeConnected",
                JSONObject.class
        );
        method.setAccessible(true);

        JSONObject runtimeMeta = new JSONObject()
                .put("login", "7400048")
                .put("server", "ICMarketsSC-MT5-6")
                .put("source", "MT5 Python Pull")
                .put("historyRevision", "history-1");

        boolean connected = (boolean) method.invoke(null, runtimeMeta);

        assertTrue(connected);
    }

    @Test
    public void resolveAccountModeShouldKeepCanonicalModeFromAccountMeta() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "resolveAccountMode",
                JSONObject.class
        );
        method.setAccessible(true);

        JSONObject runtimeMeta = new JSONObject()
                .put("accountMode", "hedging");

        String accountMode = (String) method.invoke(null, runtimeMeta);

        assertEquals("hedging", accountMode);
    }

    @Test
    public void mergeHistoryPagesShouldKeepFirstPageMetaAndAppendAllRows() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "mergeHistoryPages",
                java.util.List.class
        );
        method.setAccessible(true);

        AccountHistoryPayload firstPage = new AccountHistoryPayload(
                1000L,
                "sync-1",
                new JSONObject().put("login", "7400048").put("server", "ICMarketsSC-MT5-6"),
                new JSONArray().put(new JSONObject().put("name", "总资产").put("value", "$1000")),
                new JSONArray().put(new JSONObject().put("name", "最大回撤").put("value", "-1.00%")),
                new JSONArray().put(new JSONObject().put("name", "累计盈亏").put("value", "+$5.00")),
                new JSONArray().put(new JSONObject().put("dealTicket", 101L)),
                new JSONArray().put(new JSONObject().put("orderId", 201L)),
                new JSONArray().put(new JSONObject().put("timestamp", 301L)),
                "cursor-2",
                "{\"page\":1}"
        );
        AccountHistoryPayload secondPage = new AccountHistoryPayload(
                2000L,
                "sync-2",
                new JSONObject().put("login", "7400048").put("server", "ICMarketsSC-MT5-6"),
                new JSONArray().put(new JSONObject().put("name", "总资产").put("value", "$9999")),
                new JSONArray().put(new JSONObject().put("name", "最大回撤").put("value", "-9.99%")),
                new JSONArray().put(new JSONObject().put("name", "累计盈亏").put("value", "+$99.00")),
                new JSONArray().put(new JSONObject().put("dealTicket", 102L)),
                new JSONArray().put(new JSONObject().put("orderId", 202L)),
                new JSONArray().put(new JSONObject().put("timestamp", 302L)),
                "",
                "{\"page\":2}"
        );

        AccountHistoryPayload merged = (AccountHistoryPayload) method.invoke(
                null,
                Arrays.asList(firstPage, secondPage)
        );

        assertEquals(1000L, merged.getServerTime());
        assertEquals("sync-1", merged.getSyncToken());
        assertEquals("$1000", merged.getOverviewMetrics().optJSONObject(0).optString("value"));
        assertEquals("-1.00%", merged.getCurveIndicators().optJSONObject(0).optString("value"));
        assertEquals("+$5.00", merged.getStatsMetrics().optJSONObject(0).optString("value"));
        assertEquals(2, merged.getTrades().length());
        assertEquals(2, merged.getOrders().length());
        assertEquals(2, merged.getCurvePoints().length());
        assertEquals("", merged.getNextCursor());
    }

    @Test
    public void requireExpectedIdentityCacheShouldRejectDisconnectedCache() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "requireExpectedIdentityCache",
                AccountStatsPreloadManager.Cache.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        AccountStatsPreloadManager.Cache disconnectedCache = new AccountStatsPreloadManager.Cache(
                false,
                null,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "http://127.0.0.1",
                1000L,
                "",
                2000L,
                "history-1"
        );

        try {
            method.invoke(null, disconnectedCache, "7400048", "ICMarketsSC-MT5-6");
            fail("expected disconnected cache to be rejected");
        } catch (InvocationTargetException invocationTargetException) {
            assertEquals(
                    "v2 account full recovered cache missing connected account",
                    invocationTargetException.getCause().getMessage()
            );
        }
    }

    @Test
    public void requireExpectedIdentityCacheShouldRejectMismatchedIdentity() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "requireExpectedIdentityCache",
                AccountStatsPreloadManager.Cache.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                null,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "http://127.0.0.1",
                1000L,
                "",
                2000L,
                "history-1"
        );

        try {
            method.invoke(null, cache, "7400049", "ICMarketsSC-MT5-6");
            fail("expected mismatched cache identity to be rejected");
        } catch (InvocationTargetException invocationTargetException) {
            assertEquals(
                    "v2 account full recovered cache identity mismatch",
                    invocationTargetException.getCause().getMessage()
            );
        }
    }

    @Test
    public void requireExpectedIdentityCacheShouldKeepMatchingConnectedCache() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "requireExpectedIdentityCache",
                AccountStatsPreloadManager.Cache.class,
                String.class,
                String.class
        );
        method.setAccessible(true);

        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                null,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "http://127.0.0.1",
                1000L,
                "",
                2000L,
                "history-1"
        );

        Object validated = method.invoke(null, cache, "7400048", "ICMarketsSC-MT5-6");

        assertSame(cache, validated);
    }

    @Test
    public void mergePublishedRuntimeWithStoredHistoryShouldKeepHistoricalTradesAndCurves() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "mergePublishedRuntimeWithStoredHistory",
                AccountStorageRepository.StoredSnapshot.class,
                AccountStorageRepository.StoredSnapshot.class
        );
        method.setAccessible(true);

        AccountStatsPreloadManager manager = instantiatePreloadManager();
        AccountStorageRepository.StoredSnapshot runtimeSnapshot = new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "https://tradeapp.ltd",
                2000L,
                "",
                3000L,
                "history-2",
                Arrays.asList(new AccountMetric("净资产", "$1,020.00")),
                Arrays.asList(),
                Arrays.asList(),
                Arrays.asList(createPosition(2d)),
                Arrays.asList(),
                Arrays.asList(),
                Arrays.asList()
        );
        AccountStorageRepository.StoredSnapshot storedSnapshot = new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "https://tradeapp.ltd",
                1000L,
                "",
                1500L,
                "history-2",
                Arrays.asList(new AccountMetric("净资产", "$1,000.00")),
                Arrays.asList(new CurvePoint(111L, 1000d, 1000d, 0.2d)),
                Arrays.asList(new AccountMetric("最大回撤", "-1.00%")),
                Arrays.asList(createPosition(1d)),
                Arrays.asList(),
                Arrays.asList(createTrade(888L, 111L, 222L)),
                Arrays.asList(new AccountMetric("累计收益额", "+$12.00"))
        );

        AccountStorageRepository.StoredSnapshot merged =
                (AccountStorageRepository.StoredSnapshot) method.invoke(manager, runtimeSnapshot, storedSnapshot);

        assertEquals(1, merged.getTrades().size());
        assertEquals(1, merged.getCurvePoints().size());
        assertEquals(1, merged.getStatsMetrics().size());
        assertEquals(1, merged.getCurveIndicators().size());
        assertEquals(1, merged.getPositions().size());
        assertEquals(2d, merged.getPositions().get(0).getQuantity(), 1e-9);
        assertEquals("$1,020.00", merged.getOverviewMetrics().get(0).getValue());
    }

    @Test
    public void buildStoredSnapshotFromSnapshotPayloadShouldKeepCanonicalIdentityAndHistoryRevision() throws Exception {
        Method method = AccountStatsPreloadManager.class.getDeclaredMethod(
                "buildStoredSnapshotFromSnapshotPayload",
                AccountSnapshotPayload.class
        );
        method.setAccessible(true);

        AccountStatsPreloadManager manager = instantiatePreloadManager();
        AccountSnapshotPayload payload = new AccountSnapshotPayload(
                1234L,
                "sync-token",
                new JSONObject()
                        .put("login", "7400048")
                        .put("server", "ICMarketsSC-MT5-6")
                        .put("source", "MT5 Python Pull")
                        .put("historyRevision", "history-9"),
                new JSONObject()
                        .put("balance", 1000)
                        .put("equity", 1005),
                new JSONArray().put(new JSONObject().put("name", "总资产").put("value", "$1005.00")),
                new JSONArray().put(new JSONObject().put("name", "最大回撤").put("value", "-1.00%")),
                new JSONArray().put(new JSONObject().put("name", "累计收益额").put("value", "+$5.00")),
                new JSONArray().put(new JSONObject()
                        .put("symbol", "BTCUSDT")
                        .put("tradeSymbol", "BTCUSDT")
                        .put("productName", "BTCUSDT")
                        .put("type", "Buy")
                        .put("ticket", 11)
                        .put("positionId", 22)
                        .put("time", 1000)
                        .put("volume", 0.1)
                        .put("volumeCurrent", 0.1)
                        .put("openPrice", 100)
                        .put("currentPrice", 101)
                        .put("marketValue", 10.1)
                        .put("profit", 0.2)
                        .put("commission", 0.1)
                        .put("swap", 0.0)
                        .put("profitPercent", 0.2)),
                new JSONArray(),
                "{}"
        );

        AccountStorageRepository.StoredSnapshot storedSnapshot =
                (AccountStorageRepository.StoredSnapshot) method.invoke(manager, payload);

        assertTrue(storedSnapshot.isConnected());
        assertEquals("7400048", storedSnapshot.getAccount());
        assertEquals("ICMarketsSC-MT5-6", storedSnapshot.getServer());
        assertEquals("history-9", storedSnapshot.getHistoryRevision());
        assertEquals(1, storedSnapshot.getPositions().size());
    }

    private static AccountStatsPreloadManager instantiatePreloadManager() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        java.lang.reflect.Method allocateInstance =
                unsafeClass.getMethod("allocateInstance", Class.class);
        return (AccountStatsPreloadManager) allocateInstance.invoke(unsafe, AccountStatsPreloadManager.class);
    }

    private static PositionItem createPosition(double quantity) {
        return new PositionItem(
                "BTCUSDT",
                "BTCUSDT",
                "Buy",
                1L,
                2L,
                1000L,
                quantity,
                quantity,
                100d,
                101d,
                quantity * 101d,
                0.2d,
                5d,
                6d,
                0.03d,
                0d,
                0,
                0d,
                0d,
                0d,
                0d
        );
    }

    private static TradeRecordItem createTrade(long dealTicket, long openTime, long closeTime) {
        return new TradeRecordItem(
                closeTime,
                "BTCUSDT",
                "BTCUSDT",
                "Buy",
                101d,
                1d,
                101d,
                0d,
                "",
                8d,
                openTime,
                closeTime,
                0d,
                100d,
                101d,
                dealTicket,
                dealTicket,
                dealTicket,
                1
        );
    }
}
