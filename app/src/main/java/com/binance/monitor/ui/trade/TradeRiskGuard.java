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
        if ("OPEN_MARKET".equals(action) || "PENDING_ADD".equals(action) || "CLOSE_POSITION".equals(action)) {
            double volume = Math.abs(command.getVolume());
            if (volume <= 0d) {
                return Decision.reject("交易手数必须大于 0");
            }
        }
        if ("PENDING_MODIFY".equals(action) && command.getPrice() <= 0d && command.getSl() <= 0d && command.getTp() <= 0d) {
            return Decision.reject("修改挂单至少要传一个有效价格");
        }
        if ("MODIFY_TPSL".equals(action) && command.getSl() <= 0d && command.getTp() <= 0d) {
            return Decision.reject("止盈止损至少要修改一项");
        }
        if ("PENDING_CANCEL".equals(action) || "CLOSE_BY".equals(action) || "PENDING_MODIFY".equals(action) || "MODIFY_TPSL".equals(action)) {
            return Decision.allowWithConfirmation("当前交易需要确认后才能提交");
        }
        if (shouldForceConfirmByContext(command, config)) {
            return Decision.allowWithConfirmation("当前交易动作必须二次确认");
        }
        return Decision.allowWithConfirmation("当前交易需要确认后才能提交");
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
        for (BatchTradeItem item : items) {
            if (item == null || item.getCommand() == null) {
                return Decision.reject("批量项里存在无效命令");
            }
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
        private final boolean oneClickTradingEnabled;

        public Config(double maxQuickMarketVolume,
                      double maxSingleMarketVolume,
                      int maxBatchItems,
                      double maxBatchTotalVolume,
                      boolean forceConfirmForAddPosition,
                      boolean forceConfirmForReverse) {
            this(maxQuickMarketVolume,
                    maxSingleMarketVolume,
                    maxBatchItems,
                    maxBatchTotalVolume,
                    forceConfirmForAddPosition,
                    forceConfirmForReverse,
                    false);
        }

        public Config(double maxQuickMarketVolume,
                      double maxSingleMarketVolume,
                      int maxBatchItems,
                      double maxBatchTotalVolume,
                      boolean forceConfirmForAddPosition,
                      boolean forceConfirmForReverse,
                      boolean oneClickTradingEnabled) {
            this.maxQuickMarketVolume = Math.max(0d, maxQuickMarketVolume);
            this.maxSingleMarketVolume = Math.max(this.maxQuickMarketVolume, maxSingleMarketVolume);
            this.maxBatchItems = Math.max(1, maxBatchItems);
            this.maxBatchTotalVolume = Math.max(0d, maxBatchTotalVolume);
            this.forceConfirmForAddPosition = forceConfirmForAddPosition;
            this.forceConfirmForReverse = forceConfirmForReverse;
            this.oneClickTradingEnabled = oneClickTradingEnabled;
        }

        @NonNull
        public static Config defaultConfig() {
            return new Config(0.10d, 1.00d, 4, 2.00d, true, true, false);
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

        public boolean isOneClickTradingEnabled() {
            return oneClickTradingEnabled;
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
