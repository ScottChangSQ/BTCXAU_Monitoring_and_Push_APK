/*
 * 快速模式策略，负责定义哪些快捷交易允许跳过确认。
 * 与 TradeConfirmDialogController、TradeCommandFactory 协同工作。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.TradeCommand;

public final class TradeQuickModePolicy {
    private TradeQuickModePolicy() {
    }

    // 判断当前命令是否允许走快速模式直提。
    public static boolean shouldAllowQuickMode(@Nullable TradeCommand command, boolean quickModeEnabled) {
        return shouldAllowQuickMode(command, quickModeEnabled, TradeRiskGuard.Config.defaultConfig());
    }

    // 判断当前命令是否允许在给定风控配置下跳过确认。
    public static boolean shouldAllowQuickMode(@Nullable TradeCommand command,
                                               boolean quickModeEnabled,
                                               @NonNull TradeRiskGuard.Config config) {
        if (!quickModeEnabled || command == null || config == null) {
            return false;
        }
        if (!"OPEN_MARKET".equalsIgnoreCase(command.getAction())) {
            return false;
        }
        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateTrade(command, config);
        return decision.isAllowed() && !decision.isConfirmationRequired();
    }
}
