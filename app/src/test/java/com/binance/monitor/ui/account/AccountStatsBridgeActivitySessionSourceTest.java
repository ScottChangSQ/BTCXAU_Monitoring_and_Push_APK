/*
 * 账户统计页远程会话源码约束测试，锁定弹窗刷新 saved accounts 时的本地激活态收口。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeActivitySessionSourceTest {

    @Test
    public void refreshSavedAccountsShouldPersistServerActiveFlag() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("弹窗刷新已保存账号时，应按服务端 activeAccount 真值更新本地激活标记",
                source.contains("updateSessionProfiles(payload.getActiveAccount(), payload.getSavedAccounts(), payload.getActiveAccount() != null);"));
    }

    @Test
    public void syncingCredentialMatchShouldUseCoordinatorPendingIdentity() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("syncing 阶段应按协调器 pending 目标账号比对，避免沿用旧输入值",
                source.contains("remoteSessionCoordinator != null && remoteSessionCoordinator.isAwaitingSync()"));
        assertTrue("syncing 阶段应优先使用 pending login 做匹配",
                source.contains("expectedAccount = trim(remoteSessionCoordinator.getPendingLogin());"));
        assertTrue("syncing 阶段应优先使用 pending server 做匹配",
                source.contains("expectedServer = trim(remoteSessionCoordinator.getPendingServer());"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到 AccountStatsBridgeActivity.java");
    }
}
