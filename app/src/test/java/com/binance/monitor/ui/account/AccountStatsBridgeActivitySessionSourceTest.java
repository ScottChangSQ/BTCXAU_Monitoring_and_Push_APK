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
    public void accountStatsActivityShouldDeduplicateSavedAccountsBeforePersistingToPrefs() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户统计页更新 active/saved account 摘要时，应先按稳定身份去重，避免本地再次写入重复账号",
                source.contains("import com.binance.monitor.data.model.v2.session.RemoteAccountProfileDeduplicationHelper;")
                        && source.contains("savedSessionAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(savedAccounts);"));
    }

    @Test
    public void accountStatsActivityShouldConsumeLoginDialogIntentForNewAndExistingInstances() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户统计页应定义显式登录弹窗入口 extra，避免顶部账户按钮误落到普通统计页",
                source.contains("public static final String EXTRA_OPEN_LOGIN_DIALOG = \"com.binance.monitor.ui.account.extra.OPEN_LOGIN_DIALOG\";"));
        assertTrue("账户统计页应支持“只弹登录后返回”模式，避免账户持仓页被 CLEAR_TOP 清栈跳走",
                source.contains("public static final String EXTRA_FINISH_AFTER_LOGIN_DIALOG = \"com.binance.monitor.ui.account.extra.FINISH_AFTER_LOGIN_DIALOG\";"));
        assertTrue("新建页面时应消费登录弹窗意图",
                source.contains("consumeLoginDialogIntent(getIntent());"));
        assertTrue("复用已存在页面实例时也应消费新的登录弹窗意图",
                source.contains("protected void onNewIntent(Intent intent) {")
                        && source.contains("consumeLoginDialogIntent(intent);"));
        assertTrue("消费到登录弹窗意图后，应在页面可交互时真正弹出登录框",
                source.contains("private void openLoginDialogIfRequested() {")
                        && source.contains("binding.getRoot().post(() -> {")
                        && source.contains("dismissActiveLoginDialog();")
                        && source.contains("showLoginDialog();"));
        assertTrue("登录弹窗专用模式应在取消时直接返回原页面，而不是停留在账户统计页",
                source.contains("if (finishAfterLoginDialog && !loginDialogSubmissionInFlight && !isFinishing() && !isDestroyed()) {")
                        && source.contains("finish();")
                        && source.contains("overridePendingTransition(0, 0);"));
        assertTrue("登录弹窗专用模式在受理成功后也应直接返回原页面，由原页面继续消费同步结果",
                source.contains("if (finishAfterLoginDialog) {")
                        && source.contains("persistUiState();")
                        && source.contains("finish();")
                        && source.contains("snapshotRefreshCoordinator.requestForegroundEntrySnapshot();"));
    }

    @Test
    public void legacyAccountStatsEntryShouldBridgeToHostShellBeforeRenderingLegacyPage() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("旧账户统计入口应在 onCreate 开头先桥接到主壳，避免继续渲染旧页面",
                source.contains("if (bridgeLegacyEntryToMainHost(getIntent())) {\n            return;\n        }"));
        assertTrue("桥接时应把原始 extras 透传给主壳，保留登录弹窗等后续意图",
                source.contains("Bundle sourceExtras = sourceIntent == null ? null : sourceIntent.getExtras();")
                        && source.contains("bridgeIntent.putExtras(sourceExtras);"));
        assertTrue("桥接时应关闭切换动画并结束旧页面，避免闪到旧统计页",
                source.contains("startActivity(bridgeIntent);")
                        && source.contains("overridePendingTransition(0, 0);")
                        && source.contains("finish();"));
    }

    @Test
    public void loginDialogOnlyModeShouldNotFinishBeforeSubmissionResultReturns() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("继续登录前应先标记提交中，避免弹窗 dismiss 时把页面过早 finish 掉",
                source.contains("logRemoteSessionDebug(\"登录继续通过校验，准备提交\");")
                        && source.contains("loginDialogSubmissionInFlight = true;")
                        && source.contains("passwordInput.setText(\"\");")
                        && source.contains("dialog.dismiss();")
                        && source.contains("submitRemoteLogin(account, password, server, rememberCheckBox.isChecked());"));
        assertTrue("切换已保存账号前也应先标记提交中，避免 dismiss 直接结束页面",
                source.contains("actionButton.setOnClickListener(v -> {")
                        && source.contains("loginDialogSubmissionInFlight = true;")
                        && source.contains("if (dialog != null) {")
                        && source.contains("dialog.dismiss();")
                        && source.contains("submitSavedAccountSwitch(profile);"));
        assertTrue("登录或切换失败后，应清掉提交中标记并优先把重试弹窗交还给共享 screen，避免桥接页继续自持真实弹窗链",
                source.contains("loginDialogSubmissionInFlight = false;")
                        && source.contains("if (finishAfterLoginDialog) {")
                        && source.contains("if (screen != null) {\n                screen.retryLoginDialogFromBridge();\n            } else {\n                pendingOpenLoginDialogFromIntent = true;\n                openLoginDialogIfRequested();\n            }"));
    }

    @Test
    public void loginDialogOnlyModeShouldReturnAcceptedResultAndKeepSessionRefreshEnabled() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("登录弹窗专用模式应定义回传原页的受理结果字段，避免原页无法展示同步中状态",
                source.contains("public static final String EXTRA_LOGIN_DIALOG_RESULT_MESSAGE = \"com.binance.monitor.ui.account.extra.LOGIN_DIALOG_RESULT_MESSAGE\";")
                        && source.contains("public static final String EXTRA_LOGIN_DIALOG_RESULT_ACCOUNT = \"com.binance.monitor.ui.account.extra.LOGIN_DIALOG_RESULT_ACCOUNT\";")
                        && source.contains("public static final String EXTRA_LOGIN_DIALOG_RESULT_SERVER = \"com.binance.monitor.ui.account.extra.LOGIN_DIALOG_RESULT_SERVER\";"));
        assertTrue("受理成功后应立即把账户会话标记为可刷新，不能在主动 snapshot 前就把 fetchFullForUi 自己拦掉",
                source.contains("ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(true);"));
        assertTrue("登录弹窗专用模式受理成功后应把结果回传给原页，而不是静默 finish",
                source.contains("setResult(RESULT_OK, buildLoginDialogResultIntent(result.getActiveAccount(), sourceText));"));
        assertTrue("账户统计页应提供统一的登录结果回传 Intent 构造方法",
                source.contains("private Intent buildLoginDialogResultIntent(@Nullable RemoteAccountProfile profile,")
                        && source.contains("result.putExtra(EXTRA_LOGIN_DIALOG_RESULT_MESSAGE, statusMessage);"));
    }

    @Test
    public void loginDialogShouldAcceptSessionFirstAndLetForegroundRefreshCloseSync() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("登录和已保存账号切换都应先进入“已受理、等待同步”的正式主链，不能要求第一次快照就切到新账号",
                source.contains("applyRemoteSessionAccepted(result, \"登录已受理，正在同步账户\")")
                        && source.contains("applyRemoteSessionAccepted(result, \"切换已受理，正在同步账户\")"));
        assertTrue("受理后应把会话摘要写成 active=false，等待前台正式快照再收口成功",
                source.contains("updateSessionProfiles(result.getActiveAccount(), result.getSavedAccounts(), false);"));
        assertTrue("受理后应主动请求一次前台正式快照，不能只切空页面不拉真值",
                source.contains("snapshotRefreshCoordinator.requestForegroundEntrySnapshot();"));
        assertFalse("桥接页提交链不应继续直接调用一次性快照校验主链",
                source.contains("verifyRemoteSessionAndApply(result, \"登录成功\")"));
    }

    @Test
    public void loginDialogShouldUseOwnedActionButtonsInsteadOfSystemPositiveButton() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("登录弹窗不应继续依赖系统正按钮，否则部分机型会直接 dismiss 而不进入提交监听",
                source.contains(".setPositiveButton(\"继续\", null)"));
        assertTrue("登录弹窗应改为自管的内容区操作按钮，避免系统按钮链吞掉点击",
                source.contains("LinearLayout actionRow = createLoginActionRow(dialog, palette, accountInput, passwordInput, serverInput, rememberCheckBox);"));
        assertTrue("自管继续按钮仍应走完整的字段校验与提交日志链",
                source.contains("continueButton.setOnClickListener(v -> {")
                        && source.contains("logRemoteSessionDebug(\"点击登录继续: accountEmpty=\" + account.isEmpty()"));
        assertTrue("自管取消按钮应显式 dismiss 当前弹窗，继续复用现有 finishAfterLoginDialog 收口",
                source.contains("cancelButton.setOnClickListener(v -> dialog.dismiss());"));
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
                source.contains("sessionExecutor.execute(() -> {\n            try {\n                remoteSessionCoordinator.logoutCurrent();"));
        assertTrue("退出登录链在执行器或协调器缺失时应直接报失败，不能绕过远端 logout 直接本地登出",
                source.contains("sessionStateMachine.markFailed(\"退出登录服务未就绪\");")
                        && source.contains("Toast.makeText(this, \"退出登录服务未就绪\", Toast.LENGTH_SHORT).show();"));
        assertFalse("退出登录链不应在执行器缺失时直接本地收口，避免服务端残留活跃会话",
                source.contains("if (sessionExecutor == null || remoteSessionCoordinator == null) {\n            applyLoggedOutSessionState();"));
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
                source.contains("runOnUiThread(() -> {")
                        && source.contains("snapshotRequestGuard.invalidateSession();")
                        && source.contains("loading = false;")
                        && source.contains("clearRuntimeAccountState();"));
        assertTrue("切账号清理也应同步失效快照刷新协调器内部请求，避免旧回包串回页面",
                source.contains("snapshotRefreshCoordinator.invalidateSession();"));
        assertTrue("切账号和登出后应显式通知服务清理流式账户运行态",
                source.contains("requestMonitorServiceAccountRuntimeClear();"));
        assertTrue("账户页应提供统一的服务清理入口",
                source.contains("private void requestMonitorServiceAccountRuntimeClear() {")
                        && source.contains("MonitorServiceController.dispatch(this, AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME);"));
    }

    @Test
    public void syncingStateShouldExposePreciseMessageAndOfflineSnapshotShouldNotForceFailed() throws Exception {
        String activitySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        String coordinatorSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"
        );

        assertTrue("syncing 状态应优先展示状态机里的精确文案，而不是永远只显示“正在同步”",
                activitySource.contains("case SYNCING:\n                return trim(snapshot.getMessage()).isEmpty() ? \"正在同步\" : trim(snapshot.getMessage());"));
        assertTrue("如果网关已返回明确错误，等待同步态应直接收口为失败并展示真实原因",
                coordinatorSource.contains("if (!trim(finalError).isEmpty() && !\"历史数据（网关离线）\".equals(finalSource)) {\n                            host.markSyncFailed(finalError);"));
        assertTrue("网关离线但会话仍待同步时，应切到等待网关同步文案，而不是直接判失败",
                coordinatorSource.contains("host.markAwaitingGatewaySync(\"会话已受理，等待网关上线\");"));
        assertTrue("登录校验失败仍应直接收口为失败，避免用户继续停在同步中",
                coordinatorSource.contains("if (\"登录校验失败\".equals(finalSource)) {\n                            host.markSyncFailed(finalError);"));
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

    @Test
    public void restoreUiStateShouldUseSessionRestoreHelperAndPersistStorageFailureMessage() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户页恢复链应统一先读取会话摘要快照",
                source.contains("SessionSummarySnapshot sessionSummary = secureSessionPrefs == null")
                        && source.contains("secureSessionPrefs.loadSessionSummary();"));
        assertTrue("账户页恢复链应委托给独立 helper，而不是在 Activity 内继续拼装细节",
                source.contains("AccountSessionRestoreHelper.RestoreResult restoredSession = AccountSessionRestoreHelper.restore("));
        assertTrue("helper 结果应回填页面字段，包括本地会话摘要错误信息",
                source.contains("sessionStorageError = restoredSession.getStorageError();"));
    }

    @Test
    public void connectionDialogShouldExposeSessionStorageFailureSeparately() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户页应显式保存本地会话摘要读取错误，供弹窗展示",
                source.contains("private String sessionStorageError = \"\";"));
        assertTrue("连接详情弹窗应把本地会话摘要错误单独展示，避免继续误判为空缓存",
                source.contains("if (!sessionStorageError.isEmpty()) {")
                        && source.contains("createConnectionDetailRow(\"本地会话摘要\""));
    }

    @Test
    public void sessionDialogsShouldResolveSpacingFromTokenResolver() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("桥接页弹窗也应显式接入 SpacingTokenResolver，避免继续在兼容入口写死 dp",
                source.contains("import com.binance.monitor.ui.theme.SpacingTokenResolver;"));
        assertTrue("连接详情弹窗容器应走 dialog_content_padding",
                source.contains("SpacingTokenResolver.px(this, R.dimen.dialog_content_padding)"));
        assertTrue("桥接页弹窗行距应走 row_gap",
                source.contains("SpacingTokenResolver.rowGapPx(this)"));
        assertTrue("桥接页弹窗按钮间距应走 inline_gap",
                source.contains("SpacingTokenResolver.inlineGapPx(this)"));
        assertTrue("桥接页列表行内边距应走 list_item_padding token",
                source.contains("SpacingTokenResolver.px(this, R.dimen.list_item_padding_x)")
                        && source.contains("SpacingTokenResolver.px(this, R.dimen.list_item_padding_y)"));
        assertFalse("旧 18/14/6dp 容器 padding 不应继续保留",
                source.contains("content.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(6));"));
        assertFalse("旧 12/10dp 列表行 padding 不应继续保留",
                source.contains("row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));"));
    }

    @Test
    public void sessionSummaryWritesShouldRefreshDisplayedStorageErrorState() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户页应提供统一入口，同步本地会话摘要最近一次读写错误",
                source.contains("private void refreshSessionStorageErrorFromPrefs() {")
                        && source.contains("sessionStorageError = trim(secureSessionPrefs.getLastStorageError());"));
        assertTrue("登录输入草稿写回后也应刷新错误状态，避免旧失败文案残留",
                source.contains("secureSessionPrefs.saveDraftIdentity(account, server);\n            refreshSessionStorageErrorFromPrefs();"));
        assertTrue("登出后保存草稿时也应刷新错误状态，避免连接详情继续显示旧错误",
                source.contains("secureSessionPrefs.saveDraftIdentity(loginAccountInput, loginServerInput);\n            refreshSessionStorageErrorFromPrefs();"));
        assertTrue("页面持久化输入草稿后也应刷新错误状态，避免设置保存后仍显示旧失败",
                source.contains("editor.apply();\n        if (secureSessionPrefs != null) {\n            secureSessionPrefs.saveDraftIdentity(loginAccountInput, loginServerInput);\n            refreshSessionStorageErrorFromPrefs();\n        }\n    }"));
        assertTrue("active/saved accounts 摘要写回后也应刷新错误状态，避免成功恢复后仍展示旧失败",
                source.contains("secureSessionPrefs.saveSession(activeAccount, savedSessionAccounts, active);\n            refreshSessionStorageErrorFromPrefs();"));
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
