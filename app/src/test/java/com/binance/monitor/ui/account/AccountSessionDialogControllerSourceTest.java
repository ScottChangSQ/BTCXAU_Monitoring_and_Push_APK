/*
 * 独立登录弹窗源码约束测试，锁定已保存账号入口必须和本地会话缓存共用同一套去重逻辑。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountSessionDialogControllerSourceTest {

    @Test
    public void sessionDialogControllerShouldDeduplicateSavedAccountsOnRestoreAndPersist() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java"
        );

        assertTrue(source.contains("import com.binance.monitor.data.model.v2.session.RemoteAccountProfileDeduplicationHelper;"));
        assertTrue(source.contains("savedSessionAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(\n                summary.getSavedAccountsSnapshot()\n        );"));
        assertTrue(source.contains("savedSessionAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(savedAccounts);"));
    }

    @Test
    public void sessionDialogControllerShouldExposeConnectedAccountDialogAndLogoutCallback() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java"
        );
        String layout = readUtf8(
                "app/src/main/res/layout/dialog_account_connection_sheet.xml",
                "src/main/res/layout/dialog_account_connection_sheet.xml"
        );

        assertTrue(source.contains("void onSessionLoggedOut(@NonNull String message);"));
        assertTrue(source.contains("public void showAccountConnectionDialog("));
        assertTrue(source.contains("BottomSheetDialog"));
        assertTrue(source.contains("DialogAccountConnectionSheetBinding"));
        assertTrue(source.contains("R.layout.dialog_account_connection_sheet"));
        assertTrue(source.contains("binding.btnAccountConnectionSwitch.setOnClickListener"));
        assertTrue(source.contains("binding.btnAccountConnectionLogout.setOnClickListener"));
        assertTrue(source.contains("binding.btnAccountConnectionClose.setOnClickListener"));
        assertTrue(source.contains("bindConnectionDetail(binding.tvAccountConnectionStatusValue"));
        assertTrue(source.contains("bindConnectionDetail(binding.tvAccountConnectionLatencyValue"));
        assertTrue(source.contains("ConnectionDetailNetworkHelper.load("));
        assertTrue(source.contains("callback.onSessionLoggedOut(\"已注销当前账户\");"));
        assertTrue(source.contains("remoteSessionCoordinator.logoutCurrent();"));
        assertTrue(layout.contains("@+id/tvAccountConnectionStatusValue"));
        assertTrue(layout.contains("@+id/tvAccountConnectionLatencyValue"));
        assertTrue(layout.contains("@+id/btnAccountConnectionSwitch"));
        assertTrue(layout.contains("@+id/btnAccountConnectionLogout"));
        assertTrue(layout.contains("@+id/btnAccountConnectionClose"));
        assertTrue(layout.contains("android:gravity=\"center_vertical\""));
        assertTrue(layout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.Caption\""));
        assertTrue(layout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.ValueCompact\""));
        assertFalse(layout.contains("android:layout_marginHorizontal=\"3dp\""));
        assertFalse(layout.contains("createOutlinedDrawable"));
    }

    @Test
    public void sessionDialogControllerShouldResolveRuntimeSpacingFromTokenResolver() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java"
        );

        assertTrue(source.contains("import com.binance.monitor.ui.theme.SpacingTokenResolver;"));
        assertTrue(source.contains("SpacingTokenResolver.px(activity, R.dimen.dialog_content_padding)"));
        assertTrue(source.contains("SpacingTokenResolver.rowGapPx(activity)"));
        assertTrue(source.contains("SpacingTokenResolver.rowGapCompactPx(activity)"));
        assertTrue(source.contains("SpacingTokenResolver.inlineGapPx(activity)"));
        assertTrue(source.contains("SpacingTokenResolver.px(activity, R.dimen.list_item_padding_x)"));
        assertTrue(source.contains("SpacingTokenResolver.px(activity, R.dimen.list_item_padding_y)"));
        assertFalse(source.contains("int horizontal = dpToPx(16);"));
        assertFalse(source.contains("rowParams.topMargin = dpToPx(10);"));
        assertFalse(source.contains("continueParams.leftMargin = dpToPx(10);"));
        assertFalse(source.contains("row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));"));
    }

    @Test
    public void sessionDialogControllerShouldWaitForVerifiedCacheThroughPageListenerInsteadOfLocalTimeout() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java"
        );

        assertTrue(source.contains("public void onCacheUpdated(@Nullable AccountStatsPreloadManager.Cache cache) {"));
        assertTrue(source.contains("private final Object acceptedSessionLock = new Object();"));
        assertTrue(source.contains("private AcceptedSession pendingAcceptedSession;"));
        assertTrue(source.contains("pendingAcceptedSession = new AcceptedSession(result, successMessage);"));
        assertTrue(source.contains("requestForegroundEntrySnapshot();"));
        assertFalse(source.contains("AccountSessionSyncWaiter"));
        assertFalse(source.contains("awaitVerifiedCache("));
        assertFalse(source.contains("账户同步超时，目标账号数据尚未就绪"));
    }

    @Test
    public void sessionDialogControllerShouldUseLightSnapshotProbeBeforeBackgroundFullRefresh() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java"
        );

        assertTrue(source.contains("preloadManager.fetchSnapshotForUi()"));
        assertFalse(source.contains("preloadManager.fetchFullForUi(AccountTimeRange.ALL)"));
    }

    @Test
    public void sessionDialogControllerShouldLoadServerDiagnosticAndShowPersistentFailureDialog() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java"
        );

        assertTrue(source.contains("sessionClient.fetchSessionDiagnosticTimeline("));
        assertTrue(source.contains("private String buildFailureMessageWithDiagnostic("));
        assertTrue(source.contains("private String buildStructuredFailureSummary("));
        assertTrue(source.contains("stage=\" + trim(receipt.getStage())"));
        assertTrue(source.contains("loginError=\" + trim(receipt.getLoginError())"));
        assertTrue(source.contains("lastObserved=\" + trim(lastObserved.getLogin()) + \" / \" + trim(lastObserved.getServer())"));
        assertTrue(source.contains("private void showSessionFailureDialog("));
        assertTrue(source.contains("new MaterialAlertDialogBuilder(activity)"));
        assertTrue(source.contains(".setTitle(\"账户登录失败\")"));
        assertTrue(source.contains("TextView messageView = new TextView(activity);"));
        assertTrue(source.contains("messageView.setText(message);"));
        assertTrue(source.contains("messageView.setTextIsSelectable(true);"));
        assertTrue(source.contains("messageView.setLongClickable(true);"));
        assertTrue(source.contains(".setView(messageView)"));
        assertFalse(source.contains(".setMessage(message)"));
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
        throw new IllegalStateException("找不到 AccountSessionDialogController.java");
    }
}
