/*
 * 主壳底部 Tab 枚举，统一维护稳定 key 与菜单项映射。
 */
package com.binance.monitor.ui.host;

import androidx.annotation.IdRes;

import com.binance.monitor.R;

public enum HostTab {
    TRADING("trading", R.id.nav_market_chart),
    ACCOUNT("account", R.id.nav_account_position),
    ANALYSIS("analysis", R.id.nav_account_stats);

    public static final HostTab MARKET_MONITOR = TRADING;
    public static final HostTab MARKET_CHART = TRADING;
    public static final HostTab ACCOUNT_POSITION = ACCOUNT;
    public static final HostTab ACCOUNT_STATS = ANALYSIS;

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
        if (menuItemId == R.id.nav_market_monitor) {
            return TRADING;
        }
        for (HostTab value : values()) {
            if (value.menuItemId == menuItemId) {
                return value;
            }
        }
        return TRADING;
    }

    public static HostTab fromKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return TRADING;
        }
        if ("market_monitor".equals(key) || "market_chart".equals(key)) {
            return TRADING;
        }
        if ("account_position".equals(key)) {
            return ACCOUNT;
        }
        if ("account_stats".equals(key)) {
            return ANALYSIS;
        }
        for (HostTab value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return TRADING;
    }
}
