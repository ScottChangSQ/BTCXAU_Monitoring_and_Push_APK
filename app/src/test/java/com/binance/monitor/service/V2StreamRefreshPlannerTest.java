package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class V2StreamRefreshPlannerTest {

    @Test
    public void planShouldRefreshBothSidesWhenFullRefreshRequired() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("unchanged", false);
        payload.put("fullRefresh", new JSONObject().put("required", true));
        payload.put("marketDelta", new JSONArray());
        payload.put("accountDelta", new JSONArray());

        V2StreamRefreshPlanner.RefreshPlan plan =
                V2StreamRefreshPlanner.plan("syncBootstrap", payload);

        assertTrue(plan.shouldRefreshMarket());
        assertTrue(plan.shouldRefreshAccount());
        assertTrue(plan.shouldRefreshFloating());
    }

    @Test
    public void planShouldRefreshOnlyAccountWhenAccountDeltaPresent() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("unchanged", false);
        payload.put("fullRefresh", new JSONObject().put("required", false));
        payload.put("marketDelta", new JSONArray());
        payload.put("accountDelta", new JSONArray().put(new JSONObject().put("type", "accountSnapshotChanged")));

        V2StreamRefreshPlanner.RefreshPlan plan =
                V2StreamRefreshPlanner.plan("syncDelta", payload);

        assertFalse(plan.shouldRefreshMarket());
        assertTrue(plan.shouldRefreshAccount());
        assertTrue(plan.shouldRefreshFloating());
    }

    @Test
    public void planShouldStayIdleWhenPayloadUnchanged() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("unchanged", true);
        payload.put("fullRefresh", new JSONObject().put("required", false));
        payload.put("marketDelta", new JSONArray());
        payload.put("accountDelta", new JSONArray());

        V2StreamRefreshPlanner.RefreshPlan plan =
                V2StreamRefreshPlanner.plan("syncSummary", payload);

        assertFalse(plan.shouldRefreshMarket());
        assertFalse(plan.shouldRefreshAccount());
        assertFalse(plan.shouldRefreshFloating());
    }
}
