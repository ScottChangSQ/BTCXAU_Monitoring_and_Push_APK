package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeActivityV2RefreshSourceTest {

    @Test
    public void requestSnapshotShouldFavorPreloadCacheWhenAvailable() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        int applyIndex = source.indexOf("applyPreloadedCacheIfAvailable()");
        int refreshIndex = source.indexOf("preloadManager.fetchForUi(fetchRange)");
        int legacyIndex = source.indexOf("gatewayClient.fetch", refreshIndex);

        assertTrue("账户页进入时应先尝试应用预加载缓存", applyIndex >= 0);
        assertTrue("主动刷新应通过 preloadManager.fetchForUi(fetchRange) 走统一 v2 优先链路", refreshIndex > applyIndex);
        assertTrue("账户页不应继续自己直连旧 gatewayClient.fetch", legacyIndex < 0);
    }

    @Test
    public void foregroundEntryShouldTriggerImmediateUiRefresh() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户页应提供统一的前台进入刷新入口",
                source.contains("private void requestForegroundEntrySnapshot()"));
        assertTrue("页面创建时应立即触发一次账户主动刷新",
                source.contains("if (userLoggedIn) {\n            requestForegroundEntrySnapshot();"));
        assertTrue("页面回到前台时也应立即触发一次账户主动刷新",
                source.contains("protected void onResume()")
                        && source.contains("requestForegroundEntrySnapshot();"));
        assertTrue("前台进入刷新不应再因为缓存够新而只做延后调度",
                !source.contains("if (hasFreshPreloadedCache()) {\n                    scheduleNextSnapshot(dynamicRefreshDelayMs);"));
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
