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
        boolean fullRefreshRequired = safePayload.optJSONObject("fullRefresh") != null
                && safePayload.optJSONObject("fullRefresh").optBoolean("required", false);
        boolean hasMarketDelta = hasItems(safePayload.optJSONArray("marketDelta"));
        boolean hasAccountDelta = hasItems(safePayload.optJSONArray("accountDelta"));

        if (unchanged && !fullRefreshRequired && !hasMarketDelta && !hasAccountDelta) {
            return new RefreshPlan(false, false, false, messageType == null ? "" : messageType);
        }
        boolean refreshMarket = fullRefreshRequired || hasMarketDelta;
        boolean refreshAccount = fullRefreshRequired || hasAccountDelta;
        boolean refreshFloating = refreshMarket || refreshAccount;
        return new RefreshPlan(refreshMarket, refreshAccount, refreshFloating, messageType == null ? "" : messageType);
    }

    private static boolean hasItems(@Nullable JSONArray array) {
        return array != null && array.length() > 0;
    }

    public static final class RefreshPlan {
        private final boolean refreshMarket;
        private final boolean refreshAccount;
        private final boolean refreshFloating;
        private final String messageType;

        RefreshPlan(boolean refreshMarket,
                    boolean refreshAccount,
                    boolean refreshFloating,
                    String messageType) {
            this.refreshMarket = refreshMarket;
            this.refreshAccount = refreshAccount;
            this.refreshFloating = refreshFloating;
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

        public String getMessageType() {
            return messageType;
        }
    }
}
