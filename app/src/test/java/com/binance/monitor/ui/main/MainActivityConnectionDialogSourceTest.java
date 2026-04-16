/*
 * 主界面桥接源码约束测试，确保旧入口已收口到主壳。
 */
package com.binance.monitor.ui.main;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainActivityConnectionDialogSourceTest {

    @Test
    public void mainActivityShouldBridgeLegacyEntryToHostShell() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("旧入口应通过主壳跳转工厂转到统一主壳",
                source.contains("import com.binance.monitor.ui.host.HostNavigationIntentFactory;"));
        assertTrue("旧入口应桥接到交易主入口",
                source.contains("startActivity(HostNavigationIntentFactory.forTab(this, HostTab.MARKET_MONITOR));"));
        assertTrue("桥接页应关闭切换动画，避免旧入口闪屏",
                source.contains("overridePendingTransition(0, 0);"));
        assertTrue("桥接完成后应立即结束旧 Activity",
                source.contains("finish();"));
    }

    @Test
    public void mainActivityShouldNotRetainLegacyConnectionDialogImplementation() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java"
        );

        assertFalse("桥接页不应继续保留旧的连接详情弹窗拼装逻辑",
                source.contains("createConnectionDetailRow("));
        assertFalse("桥接页不应继续保留旧的运行时地址展示逻辑",
                source.contains("getBinanceRestBaseUrl()"));
        assertFalse("桥接页不应继续保留旧的运行时地址展示逻辑",
                source.contains("getBinanceWebSocketBaseUrl()"));
    }

    @Test
    public void mainActivityShouldNotRetainLegacyServiceBootstrapLifecycle() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java"
        );

        assertFalse("桥接页不应继续持有旧的服务启动入口",
                source.contains("ensureMonitorServiceStarted("));
        assertFalse("桥接页不应继续在 onResume 内做服务拉起",
                source.contains("protected void onResume()"));
        assertFalse("桥接页不应继续直接发送旧的 bootstrap action",
                source.contains("ACTION_BOOTSTRAP"));
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
