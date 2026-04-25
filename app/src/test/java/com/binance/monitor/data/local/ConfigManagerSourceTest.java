/*
 * 配置中心源码约束测试，确保配置入口与持久化真值一致。
 */
package com.binance.monitor.data.local;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManagerSourceTest {

    @Test
    public void gatewayConfigShouldReadAndWriteGatewayFromPreferences() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/ConfigManager.java",
                "src/main/java/com/binance/monitor/data/local/ConfigManager.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("读取配置时应直接从 SharedPreferences 读取网关地址",
                source.contains("return preferences.getString(KEY_MT5_GATEWAY_URL, AppConstants.MT5_GATEWAY_BASE_URL);"));
        assertTrue("写入配置时应先做空白裁剪，再决定回退默认值",
                source.contains("String normalized = baseUrl == null ? \"\" : baseUrl.trim();")
                        && source.contains("String target = normalized.isEmpty() ? AppConstants.MT5_GATEWAY_BASE_URL : normalized;"));
        assertTrue("写入配置时应把最终网关地址写入 SharedPreferences",
                source.contains("preferences.edit().putString(KEY_MT5_GATEWAY_URL, target).apply();"));
        assertTrue("配置中心应新增网关鉴权 token 键",
                source.contains("private static final String KEY_MT5_GATEWAY_AUTH_TOKEN = \"mt5_gateway_auth_token\";"));
        assertTrue("配置中心应提供网关鉴权 token 读取方法",
                source.contains("public String getMt5GatewayAuthToken() {"));
        assertTrue("配置中心应提供网关鉴权 token 写入方法",
                source.contains("public void setMt5GatewayAuthToken(String authToken) {"));
    }

    @Test
    public void tabVisibilityConfigShouldIncludeAccountPosition() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/ConfigManager.java",
                "src/main/java/com/binance/monitor/data/local/ConfigManager.java"
        );

        assertTrue("配置中心应新增账户持仓 tab 的持久化键",
                source.contains("private static final String KEY_TAB_ACCOUNT_POSITION_VISIBLE = \"tab_account_position_visible\";"));
        assertTrue("配置中心应提供账户持仓 tab 可见性读取方法",
                source.contains("public boolean isTabAccountPositionVisible() {"));
        assertTrue("配置中心应提供账户持仓 tab 可见性写入方法",
                source.contains("public void setTabAccountPositionVisible(boolean visible) {"));
    }

    @Test
    public void tradeTemplateConfigShouldExposeCanonicalTradeDefaults() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/ConfigManager.java",
                "src/main/java/com/binance/monitor/data/local/ConfigManager.java"
        );

        assertTrue("配置中心应新增交易默认手数键",
                source.contains("private static final String KEY_TRADE_DEFAULT_VOLUME = \"trade_default_volume\";"));
        assertTrue("配置中心应新增交易默认止损键",
                source.contains("private static final String KEY_TRADE_DEFAULT_SL = \"trade_default_sl\";"));
        assertTrue("配置中心应新增交易默认止盈键",
                source.contains("private static final String KEY_TRADE_DEFAULT_TP = \"trade_default_tp\";"));
        assertTrue("配置中心应新增默认模板键",
                source.contains("private static final String KEY_TRADE_DEFAULT_TEMPLATE_ID = \"trade_default_template_id\";"));
        assertTrue("配置中心应新增快捷模板键",
                source.contains("private static final String KEY_TRADE_QUICK_TEMPLATE_ID = \"trade_quick_template_id\";"));
        assertTrue("配置中心应新增模板列表键",
                source.contains("private static final String KEY_TRADE_TEMPLATES_JSON = \"trade_templates_json\";"));
        assertTrue("配置中心应提供默认手数读取方法",
                source.contains("public double getTradeDefaultVolume() {"));
        assertTrue("配置中心应提供默认手数写入方法",
                source.contains("public void setTradeDefaultVolume(double volume) {"));
        assertTrue("配置中心应提供默认止损读取方法",
                source.contains("public double getTradeDefaultSl() {"));
        assertTrue("配置中心应提供默认止盈读取方法",
                source.contains("public double getTradeDefaultTp() {"));
        assertTrue("配置中心应提供默认模板读取方法",
                source.contains("public String getTradeDefaultTemplateId() {"));
        assertTrue("配置中心应提供快捷模板读取方法",
                source.contains("public String getTradeQuickTemplateId() {"));
        assertTrue("配置中心应提供模板 JSON 读取方法",
                source.contains("public String getTradeTemplatesJson() {"));
    }

    @Test
    public void tradeRiskConfigShouldExposeCanonicalRiskThresholds() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/ConfigManager.java",
                "src/main/java/com/binance/monitor/data/local/ConfigManager.java"
        );

        assertTrue(source.contains("private static final String KEY_TRADE_MAX_QUICK_MARKET_VOLUME = \"trade_max_quick_market_volume\";"));
        assertTrue(source.contains("private static final String KEY_TRADE_MAX_SINGLE_MARKET_VOLUME = \"trade_max_single_market_volume\";"));
        assertTrue(source.contains("private static final String KEY_TRADE_MAX_BATCH_ITEMS = \"trade_max_batch_items\";"));
        assertTrue(source.contains("private static final String KEY_TRADE_MAX_BATCH_TOTAL_VOLUME = \"trade_max_batch_total_volume\";"));
        assertTrue(source.contains("private static final String KEY_TRADE_FORCE_CONFIRM_ADD_POSITION = \"trade_force_confirm_add_position\";"));
        assertTrue(source.contains("private static final String KEY_TRADE_FORCE_CONFIRM_REVERSE = \"trade_force_confirm_reverse\";"));
        assertTrue(source.contains("public double getTradeMaxQuickMarketVolume() {"));
        assertTrue(source.contains("public double getTradeMaxSingleMarketVolume() {"));
        assertTrue(source.contains("public int getTradeMaxBatchItems() {"));
        assertTrue(source.contains("public double getTradeMaxBatchTotalVolume() {"));
        assertTrue(source.contains("public boolean isTradeForceConfirmAddPosition() {"));
        assertTrue(source.contains("public boolean isTradeForceConfirmReverse() {"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 ConfigManager.java");
    }
}
