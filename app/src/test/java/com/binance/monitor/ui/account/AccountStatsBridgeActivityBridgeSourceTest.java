package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsBridgeActivityBridgeSourceTest {

    @Test
    public void accountStatsBridgeActivityShouldBridgeLegacyEntryToMainHostAccountStatsTab() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private boolean bridgeLegacyEntryToMainHost(@Nullable Intent sourceIntent) {"));
        assertTrue(source.contains("AnalysisDeepLinkTarget analysisTarget = AccountStatsRouteResolver.resolve(sourceIntent, \"bridge\");"));
        assertTrue(source.contains("if (analysisTarget.requiresDirectAnalysisPage()) {"));
        assertTrue(source.contains("private boolean shouldOpenDirectAnalysisPage(@Nullable Intent sourceIntent) {"));
        assertTrue(source.contains("return AccountStatsRouteResolver.resolve(sourceIntent, \"bridge\").requiresDirectAnalysisPage();"));
        assertTrue(source.contains("HostNavigationIntentFactory.forAnalysisTarget(this, analysisTarget)"));
        assertTrue(source.contains("bridgeIntent.putExtras(sourceExtras);"));
        assertTrue(source.contains("startActivity(bridgeIntent);"));
        assertTrue(source.contains("finish();"));
    }

    @Test
    public void accountStatsBridgeActivityShouldDelegateDirectAnalysisRuntimeToSharedScreen() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private AccountStatsScreen screen;"));
        assertTrue(source.contains("screen = new AccountStatsScreen("));
        assertTrue(source.contains("screen.initialize();"));
        assertTrue(source.contains("screen.onNewIntent(getIntent());"));
        assertTrue(source.contains("screen.attachPageRuntime(pageRuntime);"));
        assertTrue(source.contains("if (screen != null) {") && source.contains("screen.onNewIntent(intent);"));
        assertTrue(source.contains("if (screen != null) {") && source.contains("screen.bindPageContent();"));
        assertTrue(source.contains("if (screen != null) {") && source.contains("screen.bindLocalMeta();"));
    }

    @Test
    public void accountStatsBridgeActivityShouldNotKeepScreenNullFallbackForRealPageHostLogic() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public void attachForegroundRefresh() {\n                if (screen != null) {\n                    screen.attachForegroundRefresh();\n                }\n            }"));
        assertTrue(source.contains("public void enterAccountScreen(boolean coldStart) {\n                if (screen != null) {\n                    screen.enterAccountScreen(coldStart);\n                }\n            }"));
        assertTrue(source.contains("public void detachForegroundRefresh() {\n                if (screen != null) {\n                    screen.detachForegroundRefresh();\n                }\n            }"));
        assertTrue(source.contains("public void requestScheduledSnapshot() {\n                if (screen != null) {\n                    screen.requestScheduledSnapshot();\n                }\n            }"));
        assertTrue(!source.contains("} else if (preloadManager != null) {"));
        assertTrue(!source.contains("} else if (snapshotRefreshCoordinator != null) {"));
        assertTrue(!source.contains("} else {\n            openLoginDialogIfRequested();\n        }"));
    }

    @Test
    public void accountStatsBridgeActivityShouldMarkLegacyDialogHelpersAsCompatibilityFallbacks() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("真实登录弹窗链优先回到共享 screen，桥接页自己的弹窗字段只保留兼容壳。"));
        assertTrue(source.contains("兼容残留：真实深页销毁链优先交给共享 screen"));
        assertTrue(source.contains("兼容残留：真实深页释放链优先交给共享 screen"));
        assertTrue(source.contains("兼容残留：真实登录弹窗已迁给共享 screen"));
        assertTrue(source.contains("if (screen != null) {\n            screen.dismissActiveLoginDialog();\n            return;\n        }"));
        assertTrue(source.contains("if (screen != null) {\n            screen.shutdownExecutors();\n            return;\n        }"));
    }
}
