/*
 * 主界面连接详情源码约束测试，确保弹窗字段与当前交互要求一致。
 */
package com.binance.monitor.ui.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainActivityConnectionDialogSourceTest {

    @Test
    public void connectionDialogShouldHideLegacyMt5GatewayRowAndKeepLatencyRow() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java"
        );
        assertFalse("连接状态弹窗不应继续显示 MT5 网关 字段",
                source.contains("createConnectionDetailRow(\"MT5 网关\""));
        assertTrue("连接状态弹窗应保留服务器延迟字段",
                source.contains("createConnectionDetailRowHolder(\"服务器延迟\""));
    }

    @Test
    public void connectionDialogShouldUseRuntimeBinanceAddressesFromConfigManager() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java"
        );

        assertTrue("连接状态弹窗应读取运行时 Binance REST 地址",
                source.contains("String binanceRest = viewModel.getBinanceRestBaseUrl();"));
        assertTrue("连接状态弹窗应读取运行时 Binance WS 地址",
                source.contains("String binanceWs = viewModel.getBinanceWebSocketBaseUrl();"));
        assertFalse("连接状态弹窗不应再本地重建 Binance REST 地址",
                source.contains("GatewayUrlResolver.buildBinanceRestBaseUrl("));
        assertFalse("连接状态弹窗不应再本地重建 Binance WS 地址",
                source.contains("GatewayUrlResolver.buildBinanceWebSocketBaseUrl("));
    }

    @Test
    public void mainActivityShouldStartServiceOnCreateInsteadOfOnEveryResume() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("ensureMonitorServiceStarted();"));
        assertTrue(source.contains("private void ensureMonitorServiceStarted()"));
        assertTrue(source.contains("protected void onResume()"));
        assertTrue(source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();"));
        assertFalse(source.contains("protected void onResume() {\n        super.onResume();\n        sendServiceAction(AppConstants.ACTION_BOOTSTRAP);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MainActivity.java");
    }
}
