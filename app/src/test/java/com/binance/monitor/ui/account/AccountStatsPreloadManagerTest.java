package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

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
}
