/*
 * 交易风控中心，负责统一评估单笔与批量交易的量级边界和强制确认条件。
 * 与 TradeConfirmDialogController、TradeExecutionCoordinator、BatchTradeCoordinator 协同工作。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

public final class TradeRiskGuard {

    private TradeRiskGuard() {
    }

    @NonNull
    public static Decision evaluateTrade(@Nullable TradeCommand command, @NonNull Config config) {
        if (command == null) {
            return Decision.reject("交易命令无效");
        }
        String action = safe(command.getAction()).toUpperCase(Locale.ROOT);
        if (!"OPEN_MARKET".equals(action)) {
            return Decision.allowWithConfirmation("当前交易需要确认后才能提交");
        }
        double volume = Math.abs(command.getVolume());
        if (volume <= 0d) {
            return Decision.reject("交易手数必须大于 0");
        }
        if (volume > config.getMaxSingleMarketVolume()) {
            return Decision.reject("单笔市价手数超过上限，请拆分后再提交");
        }
        if (shouldForceConfirmByContext(command, config)) {
            return Decision.allowWithConfirmation("当前交易动作必须二次确认");
        }
        if (volume > config.getMaxQuickMarketVolume()) {
            return Decision.allowWithConfirmation("当前手数超过一键交易上限，需确认后提交");
        }
        return Decision.allowWithoutConfirmation("当前手数符合一键交易边界");
    }

    @NonNull
    public static Decision evaluateBatch(@Nullable BatchTradePlan plan, @NonNull Config config) {
        if (plan == null) {
            return Decision.reject("批量计划为空");
        }
        List<BatchTradeItem> items = plan.getItems();
        if (items == null || items.isEmpty()) {
            return Decision.reject("批量计划不能为空");
        }
        if (items.size() > config.getMaxBatchItems()) {
            return Decision.reject("批量项数超过上限，请减少后再提交");
        }
        double totalVolume = 0d;
        for (BatchTradeItem item : items) {
            if (item == null || item.getCommand() == null) {
                continue;
            }
            totalVolume += Math.abs(item.getCommand().getVolume());
        }
        if (totalVolume > config.getMaxBatchTotalVolume()) {
            return Decision.reject("批量总手数超过上限，请拆分后再提交");
        }
        String summary = safe(plan.getSummary());
        if (config.isForceConfirmForReverse() && summary.contains("反手")) {
            return Decision.allowWithConfirmation("反手操作必须二次确认");
        }
        if (config.isForceConfirmForAddPosition() && summary.contains("加仓")) {
            return Decision.allowWithConfirmation("加仓操作必须二次确认");
        }
        return Decision.allowWithoutConfirmation("批量交易未命中额外风控限制");
    }

    private static boolean shouldForceConfirmByContext(@NonNull TradeCommand command, @NonNull Config config) {
        JSONObject params = command.getParams();
        String tradeContext = safe(params.optString("tradeContext", "")).toLowerCase(Locale.ROOT);
        return ("add_position".equals(tradeContext) && config.isForceConfirmForAddPosition())
                || ("reverse".equals(tradeContext) && config.isForceConfirmForReverse());
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public interface ConfigProvider {
        @NonNull
        Config getConfig();
    }

    public static final class Config {
        private final double maxQuickMarketVolume;
        private final double maxSingleMarketVolume;
        private final int maxBatchItems;
        private final double maxBatchTotalVolume;
        private final boolean forceConfirmForAddPosition;
        private final boolean forceConfirmForReverse;

        public Config(double maxQuickMarketVolume,
                      double maxSingleMarketVolume,
                      int maxBatchItems,
                      double maxBatchTotalVolume,
                      boolean forceConfirmForAddPosition,
                      boolean forceConfirmForReverse) {
            this.maxQuickMarketVolume = Math.max(0d, maxQuickMarketVolume);
            this.maxSingleMarketVolume = Math.max(this.maxQuickMarketVolume, maxSingleMarketVolume);
            this.maxBatchItems = Math.max(1, maxBatchItems);
            this.maxBatchTotalVolume = Math.max(0d, maxBatchTotalVolume);
            this.forceConfirmForAddPosition = forceConfirmForAddPosition;
            this.forceConfirmForReverse = forceConfirmForReverse;
        }

        @NonNull
        public static Config defaultConfig() {
            return new Config(0.10d, 1.00d, 4, 2.00d, true, true);
        }

        public double getMaxQuickMarketVolume() {
            return maxQuickMarketVolume;
        }

        public double getMaxSingleMarketVolume() {
            return maxSingleMarketVolume;
        }

        public int getMaxBatchItems() {
            return maxBatchItems;
        }

        public double getMaxBatchTotalVolume() {
            return maxBatchTotalVolume;
        }

        public boolean isForceConfirmForAddPosition() {
            return forceConfirmForAddPosition;
        }

        public boolean isForceConfirmForReverse() {
            return forceConfirmForReverse;
        }
    }

    public static final class Decision {
        private final boolean allowed;
        private final boolean confirmationRequired;
        private final String message;

        private Decision(boolean allowed, boolean confirmationRequired, @Nullable String message) {
            this.allowed = allowed;
            this.confirmationRequired = confirmationRequired;
            this.message = message == null ? "" : message;
        }

        @NonNull
        public static Decision reject(@NonNull String message) {
            return new Decision(false, true, message);
        }

        @NonNull
        public static Decision allowWithConfirmation(@NonNull String message) {
            return new Decision(true, true, message);
        }

        @NonNull
        public static Decision allowWithoutConfirmation(@NonNull String message) {
            return new Decision(true, false, message);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public boolean isConfirmationRequired() {
            return confirmationRequired;
        }

        @NonNull
        public String getMessage() {
            return message;
        }
    }
}
