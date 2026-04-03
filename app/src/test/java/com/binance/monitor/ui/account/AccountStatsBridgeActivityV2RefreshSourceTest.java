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
