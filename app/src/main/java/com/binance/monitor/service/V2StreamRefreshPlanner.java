/*
 * v2 同步流刷新决策器，负责根据 stream 消息决定要补拉市场、账户还是只刷新悬浮窗。
 * MonitorService 通过它把消息判断和真正的网络补拉拆开，降低后续调整成本。
 */
package com.binance.monitor.service;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

public final class V2StreamRefreshPlanner {

    private V2StreamRefreshPlanner() {
    }

    // 根据 v2 stream 消息类型和 payload，生成本轮最小刷新计划。
    public static RefreshPlan plan(@Nullable String messageType, @Nullable JSONObject payload) {
        JSONObject safePayload = payload == null ? new JSONObject() : payload;
        boolean unchanged = safePayload.optBoolean("unchanged", false);
        JSONObject fullRefresh = safePayload.optJSONObject("fullRefresh");
        boolean fullRefreshRequired = fullRefresh != null && fullRefresh.optBoolean("required", false);
        JSONObject fullRefreshSnapshot = fullRefresh == null ? null : fullRefresh.optJSONObject("snapshot");
        JSONObject fullMarketSnapshot = fullRefreshSnapshot == null ? null : fullRefreshSnapshot.optJSONObject("market");
        JSONObject fullAccountSnapshot = fullRefreshSnapshot == null ? null : fullRefreshSnapshot.optJSONObject("account");

        JSONArray marketDelta = safePayload.optJSONArray("marketDelta");
        JSONArray accountDelta = safePayload.optJSONArray("accountDelta");
        JSONObject marketSnapshot = extractSnapshot(marketDelta, "market.snapshot");
        JSONObject accountSnapshot = extractSnapshot(accountDelta, "account.snapshot");
        boolean historyRevisionChanged = hasHistoryRevisionChanged(accountDelta);

        if (unchanged && !fullRefreshRequired && marketSnapshot == null && accountSnapshot == null) {
            return new RefreshPlan(
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    messageType == null ? "" : messageType
            );
        }
        boolean refreshMarket = fullRefreshRequired || marketSnapshot != null || hasItems(marketDelta);
        boolean refreshAccount = fullRefreshRequired || accountSnapshot != null || hasItems(accountDelta);
        boolean refreshFloating = refreshMarket || refreshAccount;
        boolean pullAccountSnapshot = fullRefreshRequired || historyRevisionChanged;
        return new RefreshPlan(
                refreshMarket,
                refreshAccount,
                refreshFloating,
                pullAccountSnapshot,
                fullRefreshRequired ? fullMarketSnapshot : marketSnapshot,
                fullRefreshRequired ? fullAccountSnapshot : accountSnapshot,
                messageType == null ? "" : messageType
        );
    }

    @Nullable
    private static JSONObject extractSnapshot(@Nullable JSONArray events, String expectedAction) {
        if (events == null || events.length() == 0) {
            return null;
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) {
                continue;
            }
            String action = event.optString("action", "");
            if (!expectedAction.equals(action)) {
                continue;
            }
            JSONObject snapshot = event.optJSONObject("snapshot");
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }

    private static boolean hasHistoryRevisionChanged(@Nullable JSONArray events) {
        if (events == null || events.length() == 0) {
            return false;
        }
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) {
                continue;
            }
            if (event.optBoolean("historyRevisionChanged", false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasItems(@Nullable JSONArray array) {
        return array != null && array.length() > 0;
    }

    public static final class RefreshPlan {
        private final boolean refreshMarket;
        private final boolean refreshAccount;
        private final boolean refreshFloating;
        private final boolean pullAccountSnapshot;
        @Nullable
        private final JSONObject marketSnapshot;
        @Nullable
        private final JSONObject accountSnapshot;
        private final String messageType;

        RefreshPlan(boolean refreshMarket,
                    boolean refreshAccount,
                    boolean refreshFloating,
                    boolean pullAccountSnapshot,
                    @Nullable JSONObject marketSnapshot,
                    @Nullable JSONObject accountSnapshot,
                    String messageType) {
            this.refreshMarket = refreshMarket;
            this.refreshAccount = refreshAccount;
            this.refreshFloating = refreshFloating;
            this.pullAccountSnapshot = pullAccountSnapshot;
            this.marketSnapshot = marketSnapshot;
            this.accountSnapshot = accountSnapshot;
            this.messageType = messageType == null ? "" : messageType;
        }

        public boolean shouldRefreshMarket() {
            return refreshMarket;
        }

        public boolean shouldRefreshAccount() {
            return refreshAccount;
        }

        public boolean shouldRefreshFloating() {
            return refreshFloating;
        }

        public boolean shouldPullAccountSnapshot() {
            return pullAccountSnapshot;
        }

        @Nullable
        public JSONObject getMarketSnapshot() {
            return marketSnapshot;
        }

        @Nullable
        public JSONObject getAccountSnapshot() {
            return accountSnapshot;
        }

        public String getMessageType() {
            return messageType;
        }
    }
}
