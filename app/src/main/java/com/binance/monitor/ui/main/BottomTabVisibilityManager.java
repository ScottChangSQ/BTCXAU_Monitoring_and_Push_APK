package com.binance.monitor.ui.main;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.binance.monitor.data.local.ConfigManager;

public final class BottomTabVisibilityManager {

    private BottomTabVisibilityManager() {
    }

    public static void apply(Context context,
                             @Nullable TextView tabTrading,
                             @Nullable TextView tabAccount,
                             @Nullable TextView tabAnalysis,
                             @Nullable TextView tabSettings) {
        if (context == null) {
            return;
        }
        ConfigManager config = ConfigManager.getInstance(context.getApplicationContext());
        boolean showChart = config.isTabMarketChartVisible();
        boolean showAccountStats = config.isTabAccountStatsVisible();
        boolean showAccountPosition = config.isTabAccountPositionVisible();
        boolean showTrading = showChart || config.isTabMarketMonitorVisible();
        if (!showTrading && !showAccountStats && !showAccountPosition) {
            showTrading = true;
        }
        setVisibility(tabTrading, showTrading);
        setVisibility(tabAccount, showAccountPosition);
        setVisibility(tabAnalysis, showAccountStats);
        setVisibility(tabSettings, true);
    }

    public static void apply(Context context,
                             @Nullable TextView tabMarketMonitor,
                             @Nullable TextView tabMarketChart,
                             @Nullable TextView tabAccountStats,
                             @Nullable TextView tabAccountPosition,
                             @Nullable TextView tabSettings) {
        apply(context, tabMarketChart != null ? tabMarketChart : tabMarketMonitor,
                tabAccountPosition,
                tabAccountStats,
                tabSettings);
        setVisibility(tabMarketMonitor, false);
    }

    private static void setVisibility(@Nullable View view, boolean visible) {
        if (view == null) {
            return;
        }
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
