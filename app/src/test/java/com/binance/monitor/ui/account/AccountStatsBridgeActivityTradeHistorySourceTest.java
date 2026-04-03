/*
 * 账户统计页面源码约束测试，确保本地恢复/预加载快照里的历史交易不会被旧内存历史挡住。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeActivityTradeHistorySourceTest {

    @Test
    public void applySnapshotShouldMergeTradeHistoryBeforeRemoteConnectedBranch() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        int mergeIndex = source.indexOf("mergeTradeHistory(snapshotTrades);");
        int remoteIndex = source.indexOf("if (remoteConnected) {", mergeIndex);

        assertTrue("applySnapshot 应先合并快照里的历史交易，再按连接态更新其他缓存", mergeIndex >= 0);
        assertTrue("mergeTradeHistory(snapshotTrades) 不应继续放在 remoteConnected 分支内部", remoteIndex > mergeIndex);
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
