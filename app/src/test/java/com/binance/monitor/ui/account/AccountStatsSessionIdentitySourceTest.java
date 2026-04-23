/*
 * 账户统计页会话身份源码约束测试，锁定“分析页只复用当前账户已渲染数据”的正式规则。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsSessionIdentitySourceTest {

    @Test
    public void analysisPagesShouldTrackAppliedSnapshotIdentitySeparately() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("共享分析页应单独记录最后一次真正上屏的账号身份",
                screenSource.contains("private String lastAppliedSnapshotAccount = \"\";")
                        && screenSource.contains("private String lastAppliedSnapshotServer = \"\";"));
        assertTrue("桥接页也应单独记录最后一次真正上屏的账号身份",
                bridgeSource.contains("private String lastAppliedSnapshotAccount = \"\";")
                        && bridgeSource.contains("private String lastAppliedSnapshotServer = \"\";"));
    }

    @Test
    public void analysisPagesShouldInvalidateRenderedStateWhenSessionIdentityChanges() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("共享分析页只应在最后一次上屏身份仍匹配当前会话时复用旧渲染态",
                screenSource.contains("private boolean isLastAppliedSnapshotForCurrentSession()")
                        && screenSource.contains("if (isLastAppliedSnapshotForCurrentSession()) {\n                return true;\n            }\n            clearRuntimeAccountState();\n            applyLoggedOutEmptyState();"));
        assertTrue("桥接页也应在会话身份切换后立刻作废旧渲染态",
                bridgeSource.contains("private boolean isLastAppliedSnapshotForCurrentSession()")
                        && bridgeSource.contains("if (isLastAppliedSnapshotForCurrentSession()) {\n                return true;\n            }\n            clearRuntimeAccountState();\n            applyLoggedOutEmptyState();"));
    }

    @Test
    public void refreshSignatureShouldIncludeSessionIdentityTokens() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("共享分析页快照签名应包含连接态和账号身份，不能只看历史内容",
                screenSource.contains("appendStringToken(builder, connected ? \"connected\" : \"disconnected\");")
                        && screenSource.contains("appendStringToken(builder, trim(account));")
                        && screenSource.contains("appendStringToken(builder, trim(server));"));
        assertTrue("桥接页快照签名也应包含连接态和账号身份，避免切账号误判成同一份快照",
                bridgeSource.contains("appendStringToken(builder, connected ? \"connected\" : \"disconnected\");")
                        && bridgeSource.contains("appendStringToken(builder, trim(account));")
                        && bridgeSource.contains("appendStringToken(builder, trim(server));"));
    }

    @Test
    public void analysisPagesShouldFilterPreloadedCacheBySecureSessionActiveIdentity() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("共享分析页的预加载 cache 判定应只认 SecureSessionPrefs 当前活动账号",
                screenSource.contains("SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();")
                        && screenSource.contains("if (sessionSummary.hasStorageFailure() || sessionSummary.getActiveAccount() == null) {")
                        && screenSource.contains("String expectedAccount = trim(sessionSummary.getActiveAccount().getLogin());")
                        && screenSource.contains("String expectedServer = trim(sessionSummary.getActiveAccount().getServer());"));
        assertTrue("桥接分析页也应只认 SecureSessionPrefs 当前活动账号",
                bridgeSource.contains("SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();")
                        && bridgeSource.contains("if (sessionSummary.hasStorageFailure() || sessionSummary.getActiveAccount() == null) {")
                        && bridgeSource.contains("String expectedAccount = trim(sessionSummary.getActiveAccount().getLogin());")
                        && bridgeSource.contains("String expectedServer = trim(sessionSummary.getActiveAccount().getServer());"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到账户统计页源码");
    }
}
