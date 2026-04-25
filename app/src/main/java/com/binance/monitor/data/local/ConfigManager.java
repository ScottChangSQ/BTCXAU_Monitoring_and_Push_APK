package com.binance.monitor.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.SymbolConfig;
public class ConfigManager {

    private static final String PREF_NAME = "binance_monitor_prefs";
    private static final String KEY_LOGIC_AND = "logic_and";
    private static final String KEY_FLOATING_ENABLED = "floating_enabled";
    private static final String KEY_FLOATING_ALPHA = "floating_alpha";
    private static final String KEY_SHOW_BTC = "show_btc";
    private static final String KEY_SHOW_XAU = "show_xau";
    private static final String KEY_MT5_GATEWAY_URL = "mt5_gateway_url";
    private static final String KEY_MT5_GATEWAY_AUTH_TOKEN = "mt5_gateway_auth_token";
    private static final String KEY_COLOR_PALETTE = "color_palette";
    private static final String KEY_TAB_MARKET_MONITOR_VISIBLE = "tab_market_monitor_visible";
    private static final String KEY_TAB_MARKET_CHART_VISIBLE = "tab_market_chart_visible";
    private static final String KEY_TAB_ACCOUNT_STATS_VISIBLE = "tab_account_stats_visible";
    private static final String KEY_TAB_ACCOUNT_POSITION_VISIBLE = "tab_account_position_visible";
    private static final String KEY_MONITORING_ENABLED = "monitoring_enabled";
    private static final String KEY_ACCOUNT_SESSION_ACTIVE = "account_session_active";
    private static final String KEY_DATA_MASKED = "data_masked";
    private static final String KEY_TRADE_DEFAULT_VOLUME = "trade_default_volume";
    private static final String KEY_TRADE_DEFAULT_SL = "trade_default_sl";
    private static final String KEY_TRADE_DEFAULT_TP = "trade_default_tp";
    private static final String KEY_TRADE_DEFAULT_TEMPLATE_ID = "trade_default_template_id";
    private static final String KEY_TRADE_QUICK_TEMPLATE_ID = "trade_quick_template_id";
    private static final String KEY_TRADE_TEMPLATES_JSON = "trade_templates_json";
    private static final String KEY_TRADE_MAX_QUICK_MARKET_VOLUME = "trade_max_quick_market_volume";
    private static final String KEY_TRADE_MAX_SINGLE_MARKET_VOLUME = "trade_max_single_market_volume";
    private static final String KEY_TRADE_MAX_BATCH_ITEMS = "trade_max_batch_items";
    private static final String KEY_TRADE_MAX_BATCH_TOTAL_VOLUME = "trade_max_batch_total_volume";
    private static final String KEY_TRADE_FORCE_CONFIRM_ADD_POSITION = "trade_force_confirm_add_position";
    private static final String KEY_TRADE_FORCE_CONFIRM_REVERSE = "trade_force_confirm_reverse";
    private static final String KEY_TRADE_ONE_CLICK_MODE_ENABLED = "trade_one_click_mode_enabled";
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
        return preferences.getString(KEY_MT5_GATEWAY_URL, AppConstants.MT5_GATEWAY_BASE_URL);
    }

    public void setMt5GatewayBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        String target = normalized.isEmpty() ? AppConstants.MT5_GATEWAY_BASE_URL : normalized;
        preferences.edit().putString(KEY_MT5_GATEWAY_URL, target).apply();
    }

    public String getMt5GatewayAuthToken() {
        String authToken = preferences.getString(KEY_MT5_GATEWAY_AUTH_TOKEN, "");
        return authToken == null ? "" : authToken.trim();
    }

    public void setMt5GatewayAuthToken(String authToken) {
        String normalized = authToken == null ? "" : authToken.trim();
        preferences.edit().putString(KEY_MT5_GATEWAY_AUTH_TOKEN, normalized).apply();
    }

    public String getBinanceRestBaseUrl() {
        return AppConstants.BASE_REST_URL;
    }

    public String getBinanceWebSocketBaseUrl() {
        return AppConstants.BASE_WS_URL;
    }

    public int getColorPalette() {
        return 0;
    }

    public void setColorPalette(int paletteId) {
        preferences.edit().putInt(KEY_COLOR_PALETTE, 0).apply();
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

    public boolean isTabAccountPositionVisible() {
        return preferences.getBoolean(KEY_TAB_ACCOUNT_POSITION_VISIBLE, true);
    }

    public void setTabAccountPositionVisible(boolean visible) {
        preferences.edit().putBoolean(KEY_TAB_ACCOUNT_POSITION_VISIBLE, visible).apply();
    }

    public boolean isMonitoringEnabled() {
        return preferences.getBoolean(KEY_MONITORING_ENABLED, true);
    }

    public void setMonitoringEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_MONITORING_ENABLED, enabled).apply();
    }

    public boolean isAccountSessionActive() {
        return preferences.getBoolean(KEY_ACCOUNT_SESSION_ACTIVE, false);
    }

    public void setAccountSessionActive(boolean active) {
        preferences.edit().putBoolean(KEY_ACCOUNT_SESSION_ACTIVE, active).apply();
    }

    public boolean isDataMasked() {
        return preferences.getBoolean(KEY_DATA_MASKED, false);
    }

    public void setDataMasked(boolean masked) {
        preferences.edit().putBoolean(KEY_DATA_MASKED, masked).apply();
    }

    public double getTradeDefaultVolume() {
        return preferences.getFloat(KEY_TRADE_DEFAULT_VOLUME, 0f);
    }

    public void setTradeDefaultVolume(double volume) {
        preferences.edit().putFloat(KEY_TRADE_DEFAULT_VOLUME, (float) Math.max(0d, volume)).apply();
    }

    public double getTradeDefaultSl() {
        return preferences.getFloat(KEY_TRADE_DEFAULT_SL, 0f);
    }

    public void setTradeDefaultSl(double sl) {
        preferences.edit().putFloat(KEY_TRADE_DEFAULT_SL, (float) Math.max(0d, sl)).apply();
    }

    public double getTradeDefaultTp() {
        return preferences.getFloat(KEY_TRADE_DEFAULT_TP, 0f);
    }

    public void setTradeDefaultTp(double tp) {
        preferences.edit().putFloat(KEY_TRADE_DEFAULT_TP, (float) Math.max(0d, tp)).apply();
    }

    public String getTradeDefaultTemplateId() {
        return preferences.getString(KEY_TRADE_DEFAULT_TEMPLATE_ID, "");
    }

    public void setTradeDefaultTemplateId(String templateId) {
        preferences.edit().putString(KEY_TRADE_DEFAULT_TEMPLATE_ID, templateId == null ? "" : templateId.trim()).apply();
    }

    public String getTradeQuickTemplateId() {
        return preferences.getString(KEY_TRADE_QUICK_TEMPLATE_ID, "");
    }

    public void setTradeQuickTemplateId(String templateId) {
        preferences.edit().putString(KEY_TRADE_QUICK_TEMPLATE_ID, templateId == null ? "" : templateId.trim()).apply();
    }

    public String getTradeTemplatesJson() {
        return preferences.getString(KEY_TRADE_TEMPLATES_JSON, "");
    }

    public void setTradeTemplatesJson(String templatesJson) {
        preferences.edit().putString(KEY_TRADE_TEMPLATES_JSON, templatesJson == null ? "" : templatesJson).apply();
    }

    public double getTradeMaxQuickMarketVolume() {
        return preferences.getFloat(KEY_TRADE_MAX_QUICK_MARKET_VOLUME, 0.10f);
    }

    public void setTradeMaxQuickMarketVolume(double value) {
        preferences.edit().putFloat(KEY_TRADE_MAX_QUICK_MARKET_VOLUME, (float) Math.max(0d, value)).apply();
    }

    public double getTradeMaxSingleMarketVolume() {
        return preferences.getFloat(KEY_TRADE_MAX_SINGLE_MARKET_VOLUME, 1.00f);
    }

    public void setTradeMaxSingleMarketVolume(double value) {
        preferences.edit().putFloat(KEY_TRADE_MAX_SINGLE_MARKET_VOLUME, (float) Math.max(0d, value)).apply();
    }

    public int getTradeMaxBatchItems() {
        return preferences.getInt(KEY_TRADE_MAX_BATCH_ITEMS, 4);
    }

    public void setTradeMaxBatchItems(int value) {
        preferences.edit().putInt(KEY_TRADE_MAX_BATCH_ITEMS, Math.max(1, value)).apply();
    }

    public double getTradeMaxBatchTotalVolume() {
        return preferences.getFloat(KEY_TRADE_MAX_BATCH_TOTAL_VOLUME, 2.00f);
    }

    public void setTradeMaxBatchTotalVolume(double value) {
        preferences.edit().putFloat(KEY_TRADE_MAX_BATCH_TOTAL_VOLUME, (float) Math.max(0d, value)).apply();
    }

    public boolean isTradeForceConfirmAddPosition() {
        return preferences.getBoolean(KEY_TRADE_FORCE_CONFIRM_ADD_POSITION, true);
    }

    public void setTradeForceConfirmAddPosition(boolean enabled) {
        preferences.edit().putBoolean(KEY_TRADE_FORCE_CONFIRM_ADD_POSITION, enabled).apply();
    }

    public boolean isTradeForceConfirmReverse() {
        return preferences.getBoolean(KEY_TRADE_FORCE_CONFIRM_REVERSE, true);
    }

    public void setTradeForceConfirmReverse(boolean enabled) {
        preferences.edit().putBoolean(KEY_TRADE_FORCE_CONFIRM_REVERSE, enabled).apply();
    }

    public boolean isTradeOneClickModeEnabled() {
        return preferences.getBoolean(KEY_TRADE_ONE_CLICK_MODE_ENABLED, false);
    }

    public void setTradeOneClickModeEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_TRADE_ONE_CLICK_MODE_ENABLED, enabled).apply();
    }

    private String getPrefix(String symbol) {
        if (AppConstants.SYMBOL_XAU.equals(symbol)) {
            return "xau";
        }
        return "btc";
    }
}
