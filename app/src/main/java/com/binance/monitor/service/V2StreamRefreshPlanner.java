/*
 * v2 同步流刷新决策器，负责根据 stream 消息决定要补拉市场、账户还是只刷新悬浮窗。
 * MonitorService 通过它把消息判断和真正的网络补拉拆开，降低后续调整成本。
 */
package com.binance.monitor.service;

import androidx.annotation.Nullable;

import com.binance.monitor.data.remote.v2.GatewayV2StreamClient;

import org.json.JSONArray;
import org.json.JSONObject;

public final class V2StreamRefreshPlanner {

    private V2StreamRefreshPlanner() {
    }

    // 根据 v2 stream 新协议事件封套，生成本轮最小刷新计划。
    public static RefreshPlan plan(@Nullable String messageType, @Nullable JSONObject payload) {
        if (GatewayV2StreamClient.MESSAGE_TYPE_MARKET_TICK.equals(messageType)) {
            return new RefreshPlan(
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    false,
                    "",
                    messageType == null ? "" : messageType
            );
        }
        JSONObject safePayload = payload == null ? new JSONObject() : payload;
        JSONObject revisions = safePayload.optJSONObject("revisions");
        if (revisions == null) {
            revisions = new JSONObject();
        }
        JSONObject changes = safePayload.optJSONObject("changes");
        if (changes == null) {
            changes = new JSONObject();
        }

        JSONObject marketSnapshot = extractSnapshot(changes, "market");
        JSONObject accountSnapshot = extractSnapshot(changes, "accountRuntime");
        boolean historyRevisionAdvanced = hasHistoryRevisionAdvanced(revisions, changes);
        boolean abnormalChanged = hasAbnormalChange(revisions, changes);

        boolean refreshMarket = marketSnapshot != null;
        boolean refreshAccount = accountSnapshot != null;
        boolean refreshFloating = refreshMarket || refreshAccount;
        String accountHistoryRevision = revisions.optString("accountHistoryRevision", "").trim();
        boolean pullAccountHistory = historyRevisionAdvanced;
        return new RefreshPlan(
                refreshMarket,
                refreshAccount,
                refreshFloating,
                pullAccountHistory,
                marketSnapshot,
                accountSnapshot,
                abnormalChanged,
                accountHistoryRevision,
                messageType == null ? "" : messageType
        );
    }

    @Nullable
    private static JSONObject extractSnapshot(@Nullable JSONObject changes, String changeKey) {
        if (changes == null) {
            return null;
        }
        JSONObject change = changes.optJSONObject(changeKey);
        if (change == null) {
            return null;
        }
        return change.optJSONObject("snapshot");
    }

    // 只有 accountHistory change 与 revisions 里的新修订号同时成立，才认定历史修订前进。
    private static boolean hasHistoryRevisionAdvanced(@Nullable JSONObject revisions, @Nullable JSONObject changes) {
        if (revisions == null || changes == null) {
            return false;
        }
        JSONObject accountHistory = changes.optJSONObject("accountHistory");
        if (accountHistory == null) {
            return false;
        }
        String changedRevision = accountHistory.optString("historyRevision", "").trim();
        String currentRevision = revisions.optString("accountHistoryRevision", "").trim();
        return !changedRevision.isEmpty() && changedRevision.equals(currentRevision);
    }

    // abnormal 变化必须同时具备 change 记录和当前 revision，避免把缺字段事件误判成有效变化。
    private static boolean hasAbnormalChange(@Nullable JSONObject revisions, @Nullable JSONObject changes) {
        if (revisions == null || changes == null) {
            return false;
        }
        JSONObject abnormal = changes.optJSONObject("abnormal");
        if (abnormal == null) {
            return false;
        }
        return !revisions.optString("abnormalRevision", "").trim().isEmpty();
    }

    public static final class RefreshPlan {
        private final boolean refreshMarket;
        private final boolean refreshAccount;
        private final boolean refreshFloating;
        private final boolean pullAccountHistory;
        @Nullable
        private final JSONObject marketSnapshot;
        @Nullable
        private final JSONObject accountSnapshot;
        private final boolean abnormalChange;
        private final String accountHistoryRevision;
        private final String messageType;

        RefreshPlan(boolean refreshMarket,
                    boolean refreshAccount,
                    boolean refreshFloating,
                    boolean pullAccountHistory,
                    @Nullable JSONObject marketSnapshot,
                    @Nullable JSONObject accountSnapshot,
                    boolean abnormalChange,
                    String accountHistoryRevision,
                    String messageType) {
            this.refreshMarket = refreshMarket;
            this.refreshAccount = refreshAccount;
            this.refreshFloating = refreshFloating;
            this.pullAccountHistory = pullAccountHistory;
            this.marketSnapshot = marketSnapshot;
            this.accountSnapshot = accountSnapshot;
            this.abnormalChange = abnormalChange;
            this.accountHistoryRevision = accountHistoryRevision == null ? "" : accountHistoryRevision;
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

        public boolean shouldPullAccountHistory() {
            return pullAccountHistory;
        }

        public boolean hasAbnormalChange() {
            return abnormalChange;
        }

        public String getAccountHistoryRevision() {
            return accountHistoryRevision;
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
