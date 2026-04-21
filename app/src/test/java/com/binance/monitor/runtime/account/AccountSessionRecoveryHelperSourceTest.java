/*
 * 账户会话恢复助手源码约束测试，确保远程会话恢复细节正式下沉到账户运行时域。
 */
package com.binance.monitor.runtime.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountSessionRecoveryHelperSourceTest {

    @Test
    public void helperShouldOwnRemoteSessionRecoveryFlow() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelper.java",
                "src/main/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelper.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户域 helper 应提供正式的远程会话恢复入口",
                source.contains("public RecoveryResult reconcileRemoteSession() {"));
        assertTrue("helper 应直接读取本地安全会话摘要",
                source.contains("SessionSummarySnapshot localSummary = secureSessionPrefs.loadSessionSummary();"));
        assertTrue("helper 应直接读取服务端 session status 真值",
                source.contains("SessionStatusPayload status = sessionClient.fetchStatus();"));
        assertTrue("helper 应把恢复后的 active/saved accounts 真值写回本地安全会话摘要",
                source.contains("secureSessionPrefs.saveSession("));
        assertTrue("helper 应在 logged_out 本地收口时保存草稿身份",
                source.contains("secureSessionPrefs.saveDraftIdentity("));
        assertTrue("helper 应在 logged_out 本地收口时切换本地登录态开关",
                source.contains("configManager.setAccountSessionActive(false);"));
        assertTrue("helper 应在 logged_out 本地收口时清理正式账户运行态",
                source.contains("accountStatsPreloadManager.clearAccountRuntimeState("));
        assertTrue("helper 应保留稳定身份匹配逻辑，优先使用 profileId，其次 login+server",
                source.contains("private boolean matchesSessionIdentity("));
        assertFalse("APP 重启恢复链不应再自动切回已保存账号",
                source.contains("sessionClient.switchAccount("));
        assertTrue("恢复结果应显式区分账号失配",
                source.contains("ACCOUNT_MISMATCH(true)"));
        assertFalse("helper 不应反向依赖 service 包实现",
                source.contains("com.binance.monitor.service."));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountSessionRecoveryHelper.java");
    }
}
