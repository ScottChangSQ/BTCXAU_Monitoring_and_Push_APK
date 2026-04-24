/*
 * 交易会话手数记忆器，负责在本次 APP 运行期间记住最近一次成功提交的开仓/挂单手数。
 * 与图表页快捷交易、交易弹窗默认手数以及执行协调器协同工作。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.TradeCommand;

import java.util.List;
import java.util.Locale;

public final class TradeSessionVolumeMemory {
    private static final double DEFAULT_VOLUME = 0.05d;
    private static volatile TradeSessionVolumeMemory instance;

    private double sessionVolume = DEFAULT_VOLUME;

    private TradeSessionVolumeMemory() {
    }

    @NonNull
    public static TradeSessionVolumeMemory getInstance() {
        if (instance == null) {
            synchronized (TradeSessionVolumeMemory.class) {
                if (instance == null) {
                    instance = new TradeSessionVolumeMemory();
                }
            }
        }
        return instance;
    }

    // 返回当前会话应使用的默认手数；冷启动前始终回到 0.05。
    public synchronized double getCurrentVolume() {
        return sessionVolume > 0d ? sessionVolume : DEFAULT_VOLUME;
    }

    // 在单笔开仓或新增挂单真正受理后更新会话手数。
    public synchronized void rememberSuccessfulTrade(@Nullable TradeCommand command) {
        double nextVolume = resolveRememberedVolume(command);
        if (nextVolume > 0d) {
            sessionVolume = nextVolume;
        }
    }

    // 批量计划里如果包含新增开仓/挂单，也同步记住最后一笔有效手数。
    public synchronized void rememberSuccessfulBatch(@Nullable BatchTradePlan plan) {
        if (plan == null) {
            return;
        }
        List<BatchTradeItem> items = plan.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        for (BatchTradeItem item : items) {
            double nextVolume = resolveRememberedVolume(item == null ? null : item.getCommand());
            if (nextVolume > 0d) {
                sessionVolume = nextVolume;
            }
        }
    }

    private double resolveRememberedVolume(@Nullable TradeCommand command) {
        if (command == null) {
            return 0d;
        }
        String action = command.getAction() == null ? "" : command.getAction().trim().toUpperCase(Locale.ROOT);
        if (!"OPEN_MARKET".equals(action) && !"PENDING_ADD".equals(action)) {
            return 0d;
        }
        double volume = Math.abs(command.getVolume());
        return Double.isFinite(volume) && volume > 0d ? volume : 0d;
    }
}
