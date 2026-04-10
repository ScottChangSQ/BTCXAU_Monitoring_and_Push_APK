package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.AccountHistoryPayload;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
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
}
