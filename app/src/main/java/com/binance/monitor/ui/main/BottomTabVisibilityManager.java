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
                             @Nullable TextView tabMarketMonitor,
                             @Nullable TextView tabMarketChart,
                             @Nullable TextView tabAccountStats,
                             @Nullable TextView tabAccountPosition,
                             @Nullable TextView tabSettings) {
        if (context == null) {
            return;
        }
        ConfigManager config = ConfigManager.getInstance(context.getApplicationContext());
        boolean showMarket = config.isTabMarketMonitorVisible();
        boolean showChart = config.isTabMarketChartVisible();
        boolean showAccountStats = config.isTabAccountStatsVisible();
        boolean showAccountPosition = config.isTabAccountPositionVisible();
        if (!showMarket && !showChart && !showAccountStats && !showAccountPosition) {
            showMarket = true;
        }
        setVisibility(tabMarketMonitor, showMarket);
        setVisibility(tabMarketChart, showChart);
        setVisibility(tabAccountStats, showAccountStats);
        setVisibility(tabAccountPosition, showAccountPosition);
        setVisibility(tabSettings, true);
    }

    private static void setVisibility(@Nullable View view, boolean visible) {
        if (view == null) {
            return;
        }
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
