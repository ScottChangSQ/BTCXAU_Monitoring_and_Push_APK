/*
 * 账户预加载源码约束测试，确保后台轻量同步也会拉取交易增量。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsPreloadManagerSourceTest {

    @Test
    public void preloadManagerShouldUseCompositeFetchAndPersistIncrementalTrades() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java"
        );
        assertTrue("后台预加载应走复合快照，确保交易记录也会同步刷新",
                source.contains("gatewayClient.fetch(AccountTimeRange.ALL)"));
        assertTrue("轻量预加载结果应写入交易历史，而不是只更新持仓",
                source.contains("accountStorageRepository.persistIncrementalSnapshot(storedSnapshot)"));
        assertFalse("后台预加载不应继续只走 live 接口，否则交易记录不会更新",
                source.contains("gatewayClient.fetchLive(AccountTimeRange.ALL)"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountStatsPreloadManager.java");
    }
}
