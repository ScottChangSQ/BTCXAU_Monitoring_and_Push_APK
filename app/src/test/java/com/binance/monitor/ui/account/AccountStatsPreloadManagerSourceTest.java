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
    public void preloadManagerShouldPreferV2AndFallbackToMt5Gateway() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java"
        );
        assertTrue("后台预加载应先请求 v2 账户快照",
                source.contains("gatewayV2Client.fetchAccountSnapshot()"));
        assertTrue("后台预加载应继续请求 v2 历史成交与曲线",
                source.contains("gatewayV2Client.fetchAccountHistory(AccountTimeRange.ALL"));
        assertTrue("v2 成功后应走原子替换入口写库，避免继续增量拼装",
                source.contains("accountStorageRepository.persistV2Snapshot"));
        assertTrue("只有 v2 失败时才回退旧 MT5 网关",
                source.contains("gatewayClient.fetch(AccountTimeRange.ALL)"));
        assertFalse("后台预加载不应继续只走 live 接口，否则交易记录不会更新",
                source.contains("gatewayClient.fetchLive(AccountTimeRange.ALL)"));
    }

    @Test
    public void preloadManagerShouldAcceptSnakeCaseTradeLifecycleFields() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java"
        );

        assertTrue("v2 历史成交解析应兼容 open_time 字段",
                source.contains("\"openTime\", \"open_time\""));
        assertTrue("v2 历史成交解析应兼容 close_time 字段",
                source.contains("\"closeTime\", \"close_time\""));
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
