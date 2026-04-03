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
