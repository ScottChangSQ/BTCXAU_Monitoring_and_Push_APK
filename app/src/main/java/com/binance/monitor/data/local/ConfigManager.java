package com.binance.monitor.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.util.GatewayUrlResolver;

public class ConfigManager {

    private static final String PREF_NAME = "binance_monitor_prefs";
    private static final String KEY_LOGIC_AND = "logic_and";
    private static final String KEY_FLOATING_ENABLED = "floating_enabled";
    private static final String KEY_FLOATING_ALPHA = "floating_alpha";
    private static final String KEY_SHOW_BTC = "show_btc";
    private static final String KEY_SHOW_XAU = "show_xau";
    private static final String KEY_MT5_GATEWAY_URL = "mt5_gateway_url";
    private static final String KEY_COLOR_PALETTE = "color_palette";
    private static final String KEY_TAB_MARKET_MONITOR_VISIBLE = "tab_market_monitor_visible";
    private static final String KEY_TAB_MARKET_CHART_VISIBLE = "tab_market_chart_visible";
    private static final String KEY_TAB_ACCOUNT_STATS_VISIBLE = "tab_account_stats_visible";
    private static volatile ConfigManager instance;

    private final SharedPreferences preferences;

    private ConfigManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static ConfigManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager(context);
                }
            }
        }
        return instance;
    }

    public SymbolConfig getSymbolConfig(String symbol) {
        SymbolConfig defaults = SymbolConfig.createDefault(symbol);
        String prefix = getPrefix(symbol);
        return new SymbolConfig(
                symbol,
                preferences.getFloat(prefix + "_volume_threshold", (float) defaults.getVolumeThreshold()),
                preferences.getFloat(prefix + "_amount_threshold", (float) defaults.getAmountThreshold()),
                preferences.getFloat(prefix + "_price_threshold", (float) defaults.getPriceChangeThreshold()),
                preferences.getBoolean(prefix + "_volume_enabled", defaults.isVolumeEnabled()),
                preferences.getBoolean(prefix + "_amount_enabled", defaults.isAmountEnabled()),
                preferences.getBoolean(prefix + "_price_enabled", defaults.isPriceChangeEnabled())
        );
    }

    public void saveSymbolConfig(SymbolConfig config) {
        String prefix = getPrefix(config.getSymbol());
        preferences.edit()
                .putFloat(prefix + "_volume_threshold", (float) config.getVolumeThreshold())
                .putFloat(prefix + "_amount_threshold", (float) config.getAmountThreshold())
                .putFloat(prefix + "_price_threshold", (float) config.getPriceChangeThreshold())
                .putBoolean(prefix + "_volume_enabled", config.isVolumeEnabled())
                .putBoolean(prefix + "_amount_enabled", config.isAmountEnabled())
                .putBoolean(prefix + "_price_enabled", config.isPriceChangeEnabled())
                .apply();
    }

    public SymbolConfig resetSymbolConfig(String symbol) {
        SymbolConfig defaults = SymbolConfig.createDefault(symbol);
        saveSymbolConfig(defaults);
        return defaults;
    }

    public boolean isUseAndMode() {
        return preferences.getBoolean(KEY_LOGIC_AND, false);
    }

    public void setUseAndMode(boolean useAndMode) {
        preferences.edit().putBoolean(KEY_LOGIC_AND, useAndMode).apply();
    }

    public boolean isFloatingEnabled() {
        return preferences.getBoolean(KEY_FLOATING_ENABLED, false);
    }

    public void setFloatingEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_FLOATING_ENABLED, enabled).apply();
    }

    public int getFloatingAlpha() {
        return preferences.getInt(KEY_FLOATING_ALPHA, 88);
    }

    public void setFloatingAlpha(int alpha) {
        preferences.edit().putInt(KEY_FLOATING_ALPHA, alpha).apply();
    }

    public boolean isShowBtc() {
        return preferences.getBoolean(KEY_SHOW_BTC, true);
    }

    public void setShowBtc(boolean show) {
        preferences.edit().putBoolean(KEY_SHOW_BTC, show).apply();
    }

    public boolean isShowXau() {
        return preferences.getBoolean(KEY_SHOW_XAU, true);
    }

    public void setShowXau(boolean show) {
        preferences.edit().putBoolean(KEY_SHOW_XAU, show).apply();
    }

    public String getMt5GatewayBaseUrl() {
        String stored = preferences.getString(KEY_MT5_GATEWAY_URL, AppConstants.MT5_GATEWAY_BASE_URL);
        return GatewayUrlResolver.resolveBaseUrl(stored, AppConstants.MT5_GATEWAY_BASE_URL);
    }

    public void setMt5GatewayBaseUrl(String baseUrl) {
        preferences.edit()
                .putString(KEY_MT5_GATEWAY_URL, GatewayUrlResolver.resolveBaseUrl(baseUrl, AppConstants.MT5_GATEWAY_BASE_URL))
                .apply();
    }

    public String getBinanceRestBaseUrl() {
        return GatewayUrlResolver.buildBinanceRestBaseUrl(
                getMt5GatewayBaseUrl(),
                AppConstants.MT5_GATEWAY_BASE_URL
        );
    }

    public String getBinanceWebSocketBaseUrl() {
        return GatewayUrlResolver.buildBinanceWebSocketBaseUrl(
                getMt5GatewayBaseUrl(),
                AppConstants.MT5_GATEWAY_BASE_URL
        );
    }

    public int getColorPalette() {
        return preferences.getInt(KEY_COLOR_PALETTE, 0);
    }

    public void setColorPalette(int paletteId) {
        preferences.edit().putInt(KEY_COLOR_PALETTE, Math.max(0, paletteId)).apply();
    }

    public boolean isTabMarketMonitorVisible() {
        return preferences.getBoolean(KEY_TAB_MARKET_MONITOR_VISIBLE, true);
    }

    public void setTabMarketMonitorVisible(boolean visible) {
        preferences.edit().putBoolean(KEY_TAB_MARKET_MONITOR_VISIBLE, visible).apply();
    }

    public boolean isTabMarketChartVisible() {
        return preferences.getBoolean(KEY_TAB_MARKET_CHART_VISIBLE, true);
    }

    public void setTabMarketChartVisible(boolean visible) {
        preferences.edit().putBoolean(KEY_TAB_MARKET_CHART_VISIBLE, visible).apply();
    }

    public boolean isTabAccountStatsVisible() {
        return preferences.getBoolean(KEY_TAB_ACCOUNT_STATS_VISIBLE, true);
    }

    public void setTabAccountStatsVisible(boolean visible) {
        preferences.edit().putBoolean(KEY_TAB_ACCOUNT_STATS_VISIBLE, visible).apply();
    }

    private String getPrefix(String symbol) {
        if (AppConstants.SYMBOL_XAU.equals(symbol)) {
            return "xau";
        }
        return "btc";
    }
}
