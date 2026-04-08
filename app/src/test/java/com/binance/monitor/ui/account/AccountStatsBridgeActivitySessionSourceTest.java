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

    @Test
    public void applyRemoteSessionStatusShouldRequireOkAndCompleteIdentity() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("普通会话状态刷新也应先校验 status.ok，避免脏成功把本地会话重新写回",
                source.contains("if (status == null || !status.isOk()) {"));
        assertTrue("普通会话状态刷新应只接受完整 activeAccount，不能把缺字段账号恢复成激活态",
                source.contains("RemoteAccountProfile activeAccount = sanitizeRemoteSessionProfile(status.getActiveAccount());"));
        assertTrue("普通会话状态刷新应明确校验 profileId/login/server 完整性",
                source.contains("return profile != null")
                        && source.contains("&& !trim(profile.getProfileId()).isEmpty()")
                        && source.contains("&& !trim(profile.getLogin()).isEmpty()")
                        && source.contains("&& !trim(profile.getServer()).isEmpty();"));
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
