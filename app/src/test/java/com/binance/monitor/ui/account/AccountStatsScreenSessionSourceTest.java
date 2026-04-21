/*
 * 账户统计共享 screen 的远程会话源码约束测试，锁定“先受理、后同步”的正式切号链。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsScreenSessionSourceTest {

    @Test
    public void statsScreenShouldAcceptSessionFirstAndWaitForForegroundRefresh() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("共享 screen 登录和切换都应先落到“已受理、正在同步账户”状态",
                source.contains("applyRemoteSessionAccepted(result, \"登录已受理，正在同步账户\")")
                        && source.contains("applyRemoteSessionAccepted(result, \"切换已受理，正在同步账户\")"));
        assertTrue("共享 screen 受理后应把会话先写成未完成态，等待正式快照命中目标账号再转 active",
                source.contains("updateSessionProfiles(result.getActiveAccount(), result.getSavedAccounts(), false);"));
        assertTrue("共享 screen 受理后必须立刻请求一次前台快照，避免页面停在旧账号或空白态",
                source.contains("snapshotRefreshCoordinator.requestForegroundEntrySnapshot();"));
        assertFalse("共享 screen 不应继续把提交链写成“一次 fetchFullForUi 命中新账号才算成功”",
                source.contains("verifyRemoteSessionAndApply(result, \"登录成功\")"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountStatsScreen.java");
    }
}
