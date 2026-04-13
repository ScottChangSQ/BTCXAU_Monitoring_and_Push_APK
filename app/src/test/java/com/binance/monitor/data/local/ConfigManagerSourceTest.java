/*
 * 配置中心源码约束测试，确保旧的 HTTP 网关地址不会继续作为正式入口保留下来。
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
    public void gatewayConfigShouldPinGatewayToCanonicalEntry() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/ConfigManager.java",
                "src/main/java/com/binance/monitor/data/local/ConfigManager.java"
        );

        assertTrue("读取配置时应把持久化值强制收口到唯一入口",
                source.contains("preferences.edit().putString(KEY_MT5_GATEWAY_URL, AppConstants.MT5_GATEWAY_BASE_URL).apply();"));
        assertTrue("读取配置时应直接返回唯一入口常量",
                source.contains("return AppConstants.MT5_GATEWAY_BASE_URL;"));
        assertTrue("写入配置时不应再接受自定义入口",
                source.contains("public void setMt5GatewayBaseUrl(String baseUrl) {\n        preferences.edit()\n                .putString(KEY_MT5_GATEWAY_URL, AppConstants.MT5_GATEWAY_BASE_URL)"));
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
