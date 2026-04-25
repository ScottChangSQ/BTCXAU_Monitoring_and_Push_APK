/*
 * 网关配置源码约束测试，确保 ConfigManager 以 SharedPreferences 作为网关地址真值来源。
 * 关联模块：ConfigManager、AppConstants。
 */
package com.binance.monitor.data.local;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManagerGatewaySourceTest {

    @Test
    public void gatewayGetterShouldReadFromPreferencesInsteadOfReturningConstant() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/ConfigManager.java",
                "src/main/java/com/binance/monitor/data/local/ConfigManager.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("网关 getter 应从 SharedPreferences 读取持久化值",
                source.contains("preferences.getString(KEY_MT5_GATEWAY_URL, AppConstants.MT5_GATEWAY_BASE_URL)"));
        assertFalse("网关 getter 不应直接返回固定常量",
                source.contains("return AppConstants.MT5_GATEWAY_BASE_URL;"));
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
