/*
 * 账户统计页远程会话源码约束测试，锁定弹窗刷新 saved accounts 时的本地激活态收口。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
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

    @Test
    public void interactiveSessionActionsShouldUseDedicatedExecutor() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("远程会话交互应有独立执行器，避免被快照或弹窗辅助刷新阻塞",
                source.contains("private ExecutorService sessionExecutor;"));
        assertTrue("页面初始化时应创建远程会话独立执行器",
                source.contains("sessionExecutor = Executors.newSingleThreadExecutor();"));
        assertTrue("页面销毁时应显式关闭远程会话独立执行器",
                source.contains("if (sessionExecutor != null) {")
                        && source.contains("sessionExecutor.shutdownNow();"));
        assertTrue("登录提交应走独立会话执行器，不能继续和通用 ioExecutor 共队列",
                source.contains("if (sessionExecutor == null || remoteSessionCoordinator == null) {")
                        && source.contains("sessionExecutor.execute(() -> {"));
        assertTrue("退出登录应走独立会话执行器，避免被其他后台任务拖住",
                source.contains("if (sessionExecutor == null || remoteSessionCoordinator == null) {")
                        && source.contains("sessionExecutor.execute(() -> {\n            try {\n                remoteSessionCoordinator.logoutCurrent();"));
        assertTrue("已保存账号切换应走独立会话执行器，避免点击切换后继续排队等待",
                source.contains("if (sessionExecutor == null || remoteSessionCoordinator == null || profile == null) {")
                        && source.contains("sessionExecutor.execute(() -> {\n            try {\n                AccountRemoteSessionCoordinator.SessionActionResult result = remoteSessionCoordinator.switchSavedAccount(profile.getProfileId());"));
    }

    @Test
    public void loginDialogShouldDisableSystemAutofillForRemoteCredentials() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("远程账户弹窗容器应禁用系统自动填充，避免系统浮窗劫持点击链",
                source.contains("container.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);"));
        assertTrue("远程账户弹窗容器应标记为敏感辅助功能数据，避免系统凭据浮窗读取内容",
                source.contains("container.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);"));
        assertTrue("远程账户弹窗窗口应启用 FLAG_SECURE，避免敏感凭据被系统辅助浮窗接管",
                source.contains("dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);"));
        assertFalse("远程账户弹窗不应调用需要 HIDE_OVERLAY_WINDOWS 特权权限的接口，避免真机直接崩溃",
                source.contains("dialog.getWindow().setHideOverlayWindows(true);"));
        assertTrue("登录输入框应显式禁用自动填充，避免 ColorOS 自动填充打断继续按钮",
                source.contains("input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);"));
        assertTrue("登录输入框应清空 autofill hints，避免系统把远程交易凭据当作普通账号密码处理",
                source.contains("input.setAutofillHints((String[]) null);"));
        assertTrue("登录输入框应标记为敏感辅助功能数据，避免自动填充服务读取远程交易凭据",
                source.contains("input.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);"));
    }

    @Test
    public void loginFlowShouldNotKeepPlainPasswordInActivityStateOrRefillInput() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertFalse("登录提交后不应再把明文密码缓存到 Activity 字段",
                source.contains("loginPasswordInput = password;"));
        assertFalse("再次打开登录弹窗时不应把旧密码回填到输入框",
                source.contains("passwordInput.setText(loginPasswordInput);"));
    }

    @Test
    public void accountSnapshotResetShouldMoveUiStateChangesToMainThreadAndClearServiceRuntime() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("切账号清理应在主线程收口页面内存态和 loading 状态",
                source.contains("runOnUiThread(() -> {\n                            snapshotRequestGuard.invalidateSession();\n                            loading = false;\n                            clearRuntimeAccountState();"));
        assertTrue("切账号和登出后应显式通知服务清理流式账户运行态",
                source.contains("requestMonitorServiceAccountRuntimeClear();"));
        assertTrue("账户页应提供统一的服务清理入口",
                source.contains("private void requestMonitorServiceAccountRuntimeClear() {")
                        && source.contains("sendServiceAction(AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME);"));
    }

    @Test
    public void syncingStateShouldExposePreciseMessageAndOfflineSnapshotShouldNotForceFailed() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("syncing 状态应优先展示状态机里的精确文案，而不是永远只显示“正在同步”",
                source.contains("case SYNCING:\n                return trim(snapshot.getMessage()).isEmpty() ? \"正在同步\" : trim(snapshot.getMessage());"));
        assertTrue("网关离线但会话仍待同步时，应切到等待网关同步文案，而不是直接判失败",
                source.contains("remoteSessionCoordinator.markAwaitingGatewaySync(\"会话已受理，等待网关上线\")"));
        assertFalse("离线历史快照不应继续直接把等待同步态打成失败",
                source.contains("&& \"登录校验失败\".equals(finalSource)) {\n                    remoteSessionCoordinator.markSyncFailed(finalError);"));
    }

    @Test
    public void dispatchTouchEventShouldNotStealLoginDialogTouches() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户页应记录当前登录弹窗实例，避免页面级触摸逻辑继续拦截弹窗点击",
                source.contains("private AlertDialog activeLoginDialog;"));
        assertTrue("登录弹窗展示期间，页面级 dispatchTouchEvent 应直接放行给弹窗自己处理",
                source.contains("if (activeLoginDialog != null && activeLoginDialog.isShowing()) {\n                return super.dispatchTouchEvent(event);\n            }"));
        assertTrue("展示登录弹窗时应登记当前弹窗实例",
                source.contains("activeLoginDialog = dialog;"));
        assertTrue("登录弹窗关闭后应清掉活动引用，避免后续页面触摸永久失效",
                source.contains("dialog.setOnDismissListener(ignored -> {")
                        && source.contains("if (activeLoginDialog == dialog) {")
                        && source.contains("activeLoginDialog = null;"));
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
