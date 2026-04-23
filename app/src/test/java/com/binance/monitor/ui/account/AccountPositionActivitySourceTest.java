package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountPositionActivitySourceTest {

    @Test
    public void accountPositionActivityShouldBindCacheListenerWithoutSlowingSharedPreloadLoop() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");

        assertTrue(source.contains("public void onPageShown() {"));
        assertTrue(source.contains("ensureMonitorServiceStarted();"));
        assertTrue(source.contains("preloadManager.addCacheListener(cacheListener);"));
        assertTrue(source.contains("public void onPageHidden() {"));
        assertTrue(source.contains("preloadManager.removeCacheListener(cacheListener);"));
        assertFalse(source.contains("preloadManager.setLiveScreenActive(true);"));
        assertFalse(source.contains("preloadManager.setLiveScreenActive(false);"));
        assertFalse(source.contains("preloadManager.addCacheListener(cacheListener);\n        AccountStatsPreloadManager.Cache initialCache"));
    }

    @Test
    public void accountPositionActivityShouldRejectCacheFromOtherSession() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");

        assertTrue(source.contains("private SecureSessionPrefs secureSessionPrefs;"));
        assertTrue(source.contains("secureSessionPrefs = new SecureSessionPrefs(appContext);"));
        assertTrue(source.contains("private AccountStatsPreloadManager.Cache filterCurrentSessionCache(@Nullable AccountStatsPreloadManager.Cache cache) {"));
        assertTrue(source.contains("private AccountStatsPreloadManager.Cache resolveCurrentSessionCache() {"));
        assertTrue(source.contains("private AccountStatsPreloadManager.Cache resolveStoredCurrentSessionCacheOnWorkerThread() {"));
        assertTrue(source.contains("private void restoreStoredCurrentSessionCacheAsync() {"));
        assertTrue(source.contains("if (initialCache == null) {\n            restoreStoredCurrentSessionCacheAsync();\n        }"));
        assertTrue(source.contains("AccountStatsPreloadManager.Cache cache = preloadManager.hydrateLatestCacheFromStorage();"));
        assertFalse(source.contains("initialCache = resolveStoredCurrentSessionCache();"));
        assertTrue(source.contains("if (!matchesActiveSessionIdentity(cache.getAccount(), cache.getServer())) {"));
        assertTrue(source.contains("preloadManager.clearLatestCache();"));
        assertTrue(source.contains("SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();"));
        assertTrue(source.contains("return expectedAccount.equalsIgnoreCase(trimToEmpty(account))"));
        assertTrue(source.contains("&& expectedServer.equalsIgnoreCase(trimToEmpty(server));"));
        assertTrue(source.contains("AccountStatsPreloadManager.Cache effectiveCache = filterCurrentSessionCache(cache);"));
        assertTrue(source.contains("accountSessionDialogController.onCacheUpdated(effectiveCache);"));
        assertTrue(source.contains("scheduleUiModelBuild(effectiveCache);"));
        assertTrue(source.contains("return matchesActiveSessionIdentity(cache.getAccount(), cache.getServer()) ? cache : null;"));
        assertTrue(source.contains("return filterCurrentSessionCache(cache);"));
    }

    @Test
    public void accountOverviewConnectionChipShouldRouteConnectedStateToInfoDialogAndFallbackToLogin() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");
        int methodStart = source.indexOf("private void openAccountLogin() {");
        int methodEnd = source.indexOf("private void ensureMonitorServiceStarted()", methodStart);
        String methodSource = methodStart >= 0 && methodEnd > methodStart
                ? source.substring(methodStart, methodEnd)
                : source;

        assertTrue(source.contains("binding.tvAccountConnectionStatus.setOnClickListener(v -> openAccountLogin());"));
        assertTrue(source.contains("private AccountSessionDialogController accountSessionDialogController;"));
        assertTrue(source.contains("accountSessionDialogController = new AccountSessionDialogController("));
        assertTrue(methodSource.contains("String displayedConnectionStatus = resolveDisplayedConnectionStatusText(currentUiModel);"));
        assertTrue(methodSource.contains("if (displayedConnectionStatus.startsWith(\"已连接\")) {"));
        assertTrue(methodSource.contains("accountSessionDialogController.showAccountConnectionDialog(resolveCurrentSessionCache(), displayedConnectionStatus);"));
        assertTrue(methodSource.contains("accountSessionDialogController.showLoginDialog();"));
        assertFalse(source.contains("binding.tvAccountConnectionStatus.setOnClickListener(v -> host.openAccountStats());"));
    }

    @Test
    public void accountPositionActivityShouldConsumeDirectSessionCallbacksInsteadOfActivityResultBridge() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private String pendingConnectionStatusText = \"\";"));
        assertTrue("账户持仓页应直接持有独立登录组件回调，不再通过 ActivityResult 借道账户统计页",
                source.contains("new AccountSessionDialogController.Callback() {")
                        && source.contains("public void onSessionSubmitting(")
                        && source.contains("public void onSessionVerified(")
                        && source.contains("public void onSessionFailed(")
                        && source.contains("public void onSessionLoggedOut("));
        assertTrue("等待同步时应优先显示受理中的连接状态，而不是立刻回退成未连接账户",
                source.contains("updateConnectionStatusChip(resolveDisplayedConnectionStatusText(nextModel));")
                        && source.contains("private String resolveDisplayedConnectionStatusText(@NonNull AccountPositionUiModel nextModel) {"));
        assertFalse("账户持仓页不应继续保留 ActivityResult 借道路由",
                source.contains("registerForActivityResult("));
    }

    @Test
    public void independentSessionDialogControllerShouldCloseRemoteSessionAwaitingSyncAfterVerifiedSnapshot() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String pageSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue("独立登录组件应改由账户页缓存监听驱动最终收口，不能继续把切号成败绑在本地 8 秒等待器上",
                source.contains("public void onCacheUpdated(@Nullable AccountStatsPreloadManager.Cache cache) {")
                        && source.contains("private AcceptedSession pendingAcceptedSession;")
                        && source.contains("pendingAcceptedSession = new AcceptedSession(result, successMessage);"));
        assertTrue("目标账号快照真正到达时，独立登录组件仍必须回写给远程会话协调器完成 active 收口",
                source.contains("boolean sessionActivated = remoteSessionCoordinator.onSnapshotApplied(")
                        && source.contains("cache == null ? \"\" : cache.getAccount(),")
                        && source.contains("cache == null ? \"\" : cache.getServer()"));
        assertTrue("若快照已返回但会话仍未收口，当前链路必须显式失败，避免留下半同步状态",
                source.contains("notifySessionFailed(\"账户快照已返回，但会话状态未完成收口\", false);"));
        assertTrue("账户页缓存监听必须把新快照继续转交给独立登录组件，避免 accepted 后无人收口",
                pageSource.contains("accountSessionDialogController.onCacheUpdated(cache);"));
        assertFalse("独立登录组件不应继续依赖本地硬超时等待器判定成败",
                source.contains("AccountSessionSyncWaiter"));
    }

    @Test
    public void accountPositionActivityShouldShowDirectLoginSuccessInsteadOfPendingSyncMessage() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue("原页仍应消费登录页返回结果",
                source.contains("handleAcceptedLoginResult("));
        assertTrue("提交后页面允许短暂展示“正在同步账户”，但这应只出现在提交中态，而不是登录成功态",
                source.contains("private void applyPendingSessionState(")
                        && source.contains("? \"正在同步账户\""));
        assertTrue("登录成功后顶部入口应直接显示登录成功，而不是继续显示同步中",
                source.contains("return safeAccount.isEmpty() ? \"登录成功\" : \"已连接账户 \" + safeAccount;"));
        assertTrue("成功回调后应走明确的成功态文案生成，而不是继续停留在提交中态",
                source.contains("pendingConnectionStatusText = safeMessage.isEmpty()\n                ? buildAcceptedConnectionStatusText(account)\n                : safeMessage;"));
    }

    @Test
    public void accountPositionTradeShortcutShouldSendCanonicalMarketSymbolToChartPage() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("symbol = com.binance.monitor.util.ProductSymbolMapper.toMarketSymbol(symbol);"));
        assertTrue(source.contains("intent.putExtra(com.binance.monitor.ui.chart.MarketChartActivity.EXTRA_TARGET_SYMBOL, symbol);"));
    }

    @Test
    public void accountPositionTradeShortcutShouldNotReuseChartActivityThroughClearTop() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        int methodStart = source.indexOf("public void openChartTradeAction(@Nullable com.binance.monitor.domain.account.model.PositionItem item,");
        int methodEnd = source.indexOf("}\n                },", methodStart);
        String methodSource = methodStart >= 0 && methodEnd > methodStart
                ? source.substring(methodStart, methodEnd)
                : source;

        assertTrue(methodSource.contains("Intent intent = HostNavigationIntentFactory.forTab(requireContext(), HostTab.MARKET_CHART);"));
        assertTrue(methodSource.contains("startActivity(intent);"));
        assertTrue(methodSource.contains("intent.putExtra(com.binance.monitor.ui.chart.MarketChartActivity.EXTRA_TARGET_SYMBOL, symbol);"));
        assertTrue(methodSource.contains("intent.putExtra(com.binance.monitor.ui.chart.MarketChartActivity.EXTRA_TRADE_ACTION, tradeAction);"));
    }

    @Test
    public void accountPositionActivityShouldIgnoreStaleUiModelFromOlderCacheRestore() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");
        String modelSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionUiModel.java");

        assertTrue(modelSource.contains("private final long snapshotVersionMs;"));
        assertTrue(modelSource.contains("public long getSnapshotVersionMs() {"));
        assertTrue(activitySource.contains("if (nextModel.getSnapshotVersionMs() < currentUiModel.getSnapshotVersionMs()) {"));
        assertTrue(activitySource.contains("return;"));
    }

    @Test
    public void accountPositionActivityShouldBootstrapMonitorServiceAndRequestForegroundSnapshot() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("ensureMonitorServiceStarted();\n        preloadManager.start();"));
        assertTrue(source.contains("public void onPageShown() {\n        if (destroyed || !bound) {\n            return;\n        }\n        ensureMonitorServiceStarted();"));
        assertTrue("账户持仓页回到前台时应显式触发服务 bootstrap，确保服务端掉线后的远程会话恢复链能立即执行",
                source.contains("MonitorServiceController.dispatch(host.requireActivity(), AppConstants.ACTION_BOOTSTRAP);"));
        assertTrue(source.contains("requestForegroundEntrySnapshot();"));
        assertTrue(source.contains("private void requestForegroundEntrySnapshot() {"));
        assertTrue("账户持仓页回前台不应再把登录收口重新绑回 full 全量链",
                source.contains("uiModelExecutor.execute(() -> preloadManager.fetchSnapshotForUi());"));
        assertFalse(source.contains("fetchFullForUi(AccountTimeRange.ALL)"));
        assertTrue(source.contains("private void ensureMonitorServiceStarted() {"));
        assertTrue(source.contains("MonitorServiceController.ensureStarted(host.requireActivity());"));
    }

    @Test
    public void accountPositionActivityShouldRestoreLastStableModelWhenSessionSubmissionFails() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private AccountPositionUiModel lastStableUiModel = AccountPositionUiModel.empty();"));
        assertTrue(source.contains("private void restoreLastStableUiModel() {"));
        assertTrue(source.contains("restoreLastStableUiModel();"));
        assertTrue(source.contains("pendingConnectionStatusText = buildFailureConnectionStatusText(message);"));
        assertTrue(source.contains("private String buildFailureConnectionStatusText(@Nullable String message) {"));
        assertTrue(source.contains("if (!nextModel.getSignature().isEmpty()) {"));
        assertTrue(source.contains("lastStableUiModel = nextModel;"));
        assertFalse(source.contains("public void onSessionFailed(@NonNull String message) {\n                        pendingConnectionStatusText = \"\";\n                        updateConnectionStatusChip(resolveDisplayedConnectionStatusText(currentUiModel));\n                    }"));
    }

    @Test
    public void accountPositionActivityShouldBridgeToMainHostAccountPositionTab() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java");

        assertTrue(source.contains("import com.binance.monitor.ui.host.HostNavigationIntentFactory;"));
        assertTrue(source.contains("import com.binance.monitor.ui.host.HostTab;"));
        assertTrue(source.contains("startActivity(HostNavigationIntentFactory.forTab(this, HostTab.ACCOUNT_POSITION));"));
        assertTrue(source.contains("finish();"));
        assertFalse(source.contains("AccountPositionPageController"));
    }

    @Test
    public void accountPositionActivityShouldStyleCardViewsByCardSurfaceInsteadOfPlainBackground() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");

        assertTrue(source.contains("applySectionCardStyle(binding.cardOverviewSection, palette);"));
        assertTrue(source.contains("applySectionCardStyle(binding.cardPositionSection, palette);"));
        assertTrue(source.contains("applySectionCardStyle(binding.cardPendingSection, palette);"));
        assertTrue(source.contains("applySectionCardStyle(binding.cardHistorySection, palette);"));
        assertTrue(source.contains("cardView.setCardBackgroundColor(palette.surfaceEnd);"));
        assertTrue(source.contains("cardView.setRadius(UiPaletteManager.radiusLgPx(activity, palette));"));
        assertTrue(source.contains("initializePageContent();\n        applyPaletteStyles();\n        applyPrivacyToggleState(isPrivacyMasked());"));
        assertTrue(source.contains("ViewCompat.setBackgroundTintList(binding.btnOpenAccountHistory, null);"));
        assertFalse(source.contains("binding.cardOverviewSection.setBackground(UiPaletteManager.createSectionBackground(activity, palette.surfaceEnd, palette.stroke));"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
