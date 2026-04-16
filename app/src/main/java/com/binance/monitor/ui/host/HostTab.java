/*
 * 主壳底部 Tab 枚举，统一维护稳定 key 与菜单项映射。
 */
package com.binance.monitor.ui.host;

import androidx.annotation.IdRes;

import com.binance.monitor.R;

public enum HostTab {
    MARKET_MONITOR("market_monitor", R.id.nav_market_monitor),
    MARKET_CHART("market_chart", R.id.nav_market_chart),
    ACCOUNT_STATS("account_stats", R.id.nav_account_stats),
    ACCOUNT_POSITION("account_position", R.id.nav_account_position),
    SETTINGS("settings", R.id.nav_settings);

    private final String key;
    private final int menuItemId;

    HostTab(String key, @IdRes int menuItemId) {
        this.key = key;
        this.menuItemId = menuItemId;
    }

    public String getKey() {
        return key;
    }

    public int getMenuItemId() {
        return menuItemId;
    }

    public static HostTab fromMenuItemId(@IdRes int menuItemId) {
        for (HostTab value : values()) {
            if (value.menuItemId == menuItemId) {
                return value;
            }
        }
        return MARKET_MONITOR;
    }

    public static HostTab fromKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return MARKET_MONITOR;
        }
        for (HostTab value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return MARKET_MONITOR;
    }
}
