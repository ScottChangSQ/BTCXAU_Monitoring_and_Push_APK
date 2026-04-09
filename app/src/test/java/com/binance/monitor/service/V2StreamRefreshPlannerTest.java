package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class V2StreamRefreshPlannerTest {

    @Test
    public void planShouldRefreshMarketAndAccountRuntimeFromChanges() throws Exception {
        JSONObject event = new JSONObject()
                .put("revisions", new JSONObject()
                        .put("marketRevision", "market-1")
                        .put("accountRuntimeRevision", "account-1"))
                .put("changes", new JSONObject()
                        .put("market", new JSONObject()
                                .put("snapshot", new JSONObject().put("symbolStates", new JSONArray())))
                        .put("accountRuntime", new JSONObject()
                                .put("snapshot", new JSONObject().put("positions", new JSONArray()))));

        V2StreamRefreshPlanner.RefreshPlan plan =
                V2StreamRefreshPlanner.plan("runtimeChanged", event);

        assertTrue(plan.shouldRefreshMarket());
        assertTrue(plan.shouldRefreshAccount());
        assertTrue(plan.shouldRefreshFloating());
        assertFalse(plan.shouldPullAccountHistory());
        assertFalse(plan.hasAbnormalChange());
    }

    @Test
    public void planShouldRequestAccountPullWhenHistoryRevisionAdvances() throws Exception {
        JSONObject event = new JSONObject()
                .put("revisions", new JSONObject()
                        .put("accountHistoryRevision", "history-2"))
                .put("changes", new JSONObject()
                        .put("accountHistory", new JSONObject()
                                .put("historyRevision", "history-2")
                                .put("tradeCount", 9)
                                .put("curvePointCount", 3)));

        V2StreamRefreshPlanner.RefreshPlan plan =
                V2StreamRefreshPlanner.plan("historyChanged", event);

        assertFalse(plan.shouldRefreshMarket());
        assertFalse(plan.shouldRefreshAccount());
        assertFalse(plan.shouldRefreshFloating());
        assertTrue(plan.shouldPullAccountHistory());
        assertTrue(plan.getAccountHistoryRevision().equals("history-2"));
        assertFalse(plan.hasAbnormalChange());
    }

    @Test
    public void planShouldExposeAbnormalChangesWithoutRuntimeRefresh() throws Exception {
        JSONObject event = new JSONObject()
                .put("revisions", new JSONObject().put("abnormalRevision", "abnormal-3"))
                .put("changes", new JSONObject()
                        .put("abnormal", new JSONObject()
                                .put("meta", new JSONObject().put("syncSeq", 3))
                                .put("delta", new JSONObject()
                                        .put("records", new JSONArray())
                                        .put("alerts", new JSONArray()))));

        V2StreamRefreshPlanner.RefreshPlan plan =
                V2StreamRefreshPlanner.plan("abnormalChanged", event);

        assertFalse(plan.shouldRefreshMarket());
        assertFalse(plan.shouldRefreshAccount());
        assertFalse(plan.shouldRefreshFloating());
        assertFalse(plan.shouldPullAccountHistory());
        assertTrue(plan.hasAbnormalChange());
    }

    @Test
    public void planShouldStayIdleWhenEventHasNoChanges() throws Exception {
        JSONObject event = new JSONObject()
                .put("revisions", new JSONObject()
                        .put("marketRevision", "market-5")
                        .put("accountRuntimeRevision", "account-8"))
                .put("changes", new JSONObject());

        V2StreamRefreshPlanner.RefreshPlan plan =
                V2StreamRefreshPlanner.plan("heartbeat", event);

        assertFalse(plan.shouldRefreshMarket());
        assertFalse(plan.shouldRefreshAccount());
        assertFalse(plan.shouldRefreshFloating());
        assertFalse(plan.shouldPullAccountHistory());
        assertFalse(plan.hasAbnormalChange());
    }
}
