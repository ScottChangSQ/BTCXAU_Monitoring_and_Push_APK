/*
 * 账户统计页源码约束测试，确保页面主动刷新主链优先切到 v2 账户接口。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeActivityV2SourceTest {

    @Test
    public void accountStatsBridgeActivityShouldRequestUiRefreshThroughPreloadManager() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户统计页应通过预加载管理器触发一次 v2 优先的主动刷新",
                source.contains("preloadManager.fetchForUi(fetchRange)"));
    }

    @Test
    public void requestSnapshotShouldStopDirectLegacyGatewayFetch() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户统计页不应继续自己直连旧 MT5 网关抓快照",
                !source.contains("gatewayClient.fetch(fetchRange)"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountStatsBridgeActivity.java");
    }
}
