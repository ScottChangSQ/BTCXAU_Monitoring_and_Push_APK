/*
 * 监控服务源码约束测试，确保进入应用和回到前台时走统一主链刷新。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceSourceTest {

    @Test
    public void foregroundEntryShouldRefreshAccountAndFloating() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("前台切回时应走统一前台状态处理入口",
                source.contains("foreground -> mainHandler.post(() -> handleForegroundStateChanged(foreground));"));
        assertTrue("前台切回时应先判断当前 stream 是否仍健康",
                source.contains("boolean streamHealthy = isV2StreamHealthy(System.currentTimeMillis());"));
        assertTrue("stream 健康时应先做远程会话恢复检查，再直接返回，不再继续重建主链连接",
                source.contains("if (streamHealthy) {")
                        && source.contains("requestMarketTruthRepair(false);")
                        && source.contains("reconcileRemoteSessionIfNeeded();")
                        && source.contains("floatingCoordinator.requestRefresh(false);"));
        assertTrue("只有 stream 已失活时才应继续走后面的重建逻辑",
                source.contains("if (streamHealthy) {\n            requestMarketTruthRepair(false);\n            reconcileRemoteSessionIfNeeded();\n            floatingCoordinator.requestRefresh(false);\n            updateConnectionStatus();\n            return;\n        }\n"));
        assertFalse("前台切回时不应无条件重建 v2 stream",
                source.contains("restartV2Stream(\"foreground_resume\");\n        requestForegroundEntryRefresh();"));
        assertFalse("前台切回时不应再主动补拉 /v2/account/snapshot",
                source.contains("requestAccountRefreshFromV2();"));
        assertTrue("stream 健康时只应切换消费层刷新节奏，并补一次远程会话一致性检查，不应做全量主链重建",
                source.contains("requestMarketTruthRepair(false);")
                        && source.contains("reconcileRemoteSessionIfNeeded();")
                        && source.contains("floatingCoordinator.requestRefresh(false);"));
        assertTrue("服务层应维护结构化连接阶段，避免把重连过程误渲染成离线",
                source.contains("private volatile ConnectionStage v2StreamStage = ConnectionStage.CONNECTING;"));
    }

    @Test
    public void bootstrapShouldRefreshAccountOnce() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("新进入 APP 时应主动触发一次全局刷新",
                source.contains("requestForegroundEntryRefresh();"));
    }

    @Test
    public void foregroundBootstrapShouldRecoverRemoteSessionWhenLocalSessionStillClaimsActive() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("服务层应维护独立的远程会话恢复并发门闩，避免前后台切换时重复发起恢复",
                source.contains("private final java.util.concurrent.atomic.AtomicBoolean remoteSessionRecoveryInFlight = new java.util.concurrent.atomic.AtomicBoolean(false);")
                        || source.contains("private final AtomicBoolean remoteSessionRecoveryInFlight = new AtomicBoolean(false);"));
        assertTrue("统一前台刷新入口应同时触发远程会话恢复检查，避免本地误以为已登录时一直拿不到账户真值",
                source.contains("private void requestForegroundEntryRefresh() {\n        floatingCoordinator.requestRefresh(true);\n        reconcileRemoteSessionIfNeeded();\n    }"));
        assertTrue("服务层应提供正式的远程会话恢复方法",
                source.contains("private void reconcileRemoteSessionIfNeeded() {"));
        assertTrue("服务层应持有账户域会话恢复 helper，而不是自己堆会话恢复细节",
                source.contains("private AccountSessionRecoveryHelper accountSessionRecoveryHelper;"));
        assertTrue("远程会话恢复应统一委托到账户域 helper",
                source.contains("AccountSessionRecoveryHelper.RecoveryResult recoveryResult =")
                        && source.contains("accountSessionRecoveryHelper.reconcileRemoteSession();"));
        assertTrue("账号失配时服务层应发出明确通知",
                source.contains("notificationHelper.notifyAccountMismatch(\"\", \"\");"));
        assertFalse("服务层不应再直接调用 clearLatestCache() 预热强一致刷新",
                source.contains("accountStatsPreloadManager.clearLatestCache();"));
        assertFalse("服务层不应再直接调用 setFullSnapshotActive(true) 操纵预加载节奏",
                source.contains("accountStatsPreloadManager.setFullSnapshotActive(true);"));
        assertFalse("服务层不应再自己保存恢复后的安全会话摘要",
                source.contains("secureSessionPrefs.saveSession("));
        assertFalse("服务层不应再自己保存草稿身份",
                source.contains("secureSessionPrefs.saveDraftIdentity("));
        assertFalse("服务层不应再自己切登录态开关",
                source.contains("configManager.setAccountSessionActive("));
        assertFalse("服务层不应再自己拉恢复后的强一致全量账户快照",
                source.contains("accountStatsPreloadManager.fetchFullForUiForIdentity("));
        assertFalse("服务层不应再自己清理 logged_out 后的账户运行态",
                source.contains("accountStatsPreloadManager.clearAccountRuntimeState(localActiveAccount.getLogin(), localActiveAccount.getServer());"));
        assertFalse("服务层不应再自己判断恢复后的 cache 是否连通",
                source.contains("recoveredCache == null || !recoveredCache.isConnected()"));
        assertFalse("服务层不应再自己比对恢复后的 cache 身份",
                source.contains("matchesSessionIdentity(remoteActiveAccount, recoveredCache.getAccount(), recoveredCache.getServer())"));
        assertFalse("服务层不应再保留远程会话恢复实现细节方法",
                source.contains("private void performRemoteSessionRecoveryIfNeeded() {"));
        assertFalse("服务层不应再保留 logged_out 本地收口 helper",
                source.contains("private void settleRemoteLoggedOutLocally("));
        assertFalse("服务层不应再保留已保存账号匹配 helper",
                source.contains("private RemoteAccountProfile findRecoverableSavedAccount("));
    }

    @Test
    public void monitorServiceShouldKeepForegroundNotificationUntilServiceDestroy() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("MonitorService 不应再保留启动后收起常驻通知的旧链 runnable",
                source.contains("private final Runnable suppressForegroundNotificationRunnable"));
        assertFalse("MonitorService 不应再保留 suppressForegroundNotification 旧入口",
                source.contains("private void suppressForegroundNotification() {"));
        assertFalse("MonitorService 不应再保留 requestSuppressForegroundNotification 旧入口",
                source.contains("private void requestSuppressForegroundNotification() {"));
        assertTrue("前台服务入口应真正调用 startForeground",
                source.contains("startForeground(notificationId, notification);"));
        assertTrue("服务退出时应调用 stopForeground(true) 收口前台状态",
                source.contains("stopForeground(true);"));
    }

    @Test
    public void staleWatchdogShouldRestartV2StreamInsteadOfOnlyChangingStatusText() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("watchdog 发现 v2 stream 失活时，应直接重建主链连接",
                source.contains("restartV2Stream(\"stale_watchdog\");"));
    }

    @Test
    public void floatingSnapshotShouldUseDisplayResolverSnapshotPositions() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("悬浮窗不应再回读本地恢复快照解析器",
                source.contains("AccountSnapshotDisplayResolver.resolve("));
        assertFalse("悬浮窗不应再直接读取数据库持仓列表",
                source.contains("accountStorageRepository == null ? new ArrayList<>() : accountStorageRepository.loadPositions()"));
    }

    @Test
    public void startupShouldNotKeepGatewayAddressDiagnosticPlaceholder() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("地址诊断明细日志默认应关闭，避免继续污染主链日志",
                source.contains("APP诊断 BuildConfig MT5="));
        assertFalse("服务不应继续保留空转的地址诊断占位调用",
                source.contains("logResolvedGatewayAddresses();"));
    }

    @Test
    public void abnormalHandlingShouldConsumeServerProducedAlerts() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("MonitorService 不应再保留 /v1/abnormal 轮询 runnable",
                source.contains("private final Runnable abnormalSyncRunnable"));
        assertFalse("MonitorService 不应再主动请求异常同步接口",
                source.contains("requestAbnormalSync();"));
        assertFalse("MonitorService 不应再保留本地异常兜底通知分支",
                source.contains("dispatchLocalAbnormalNotification(record);"));
        assertTrue("服务端 alerts 也应进入补发提醒链路",
                source.contains("dispatchServerAlertIfNeeded(alert);"));
        assertTrue("服务端 alerts 去重必须基于 alert id，而不是只按产品冷却",
                source.contains("alert.getId()"));
    }

    @Test
    public void abnormalBootstrapShouldComeFromV2StreamInsteadOfLegacyPolling() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("MonitorService 不应再保留 legacy abnormal bootstrap 标记位",
                source.contains("abnormalBootstrapSynced"));
        assertTrue("异常 bootstrap 应由 v2 stream 统一入口接收",
                source.contains("applyAbnormalSnapshotFromStream("));
    }

    @Test
    public void closedMarketSeriesShouldNotTriggerLocalAbnormalEvaluation() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("闭合 1m K 线写入主链后，不应再在 APP 本地产生异常记录",
                source.contains("handleClosedKline(closedData);"));
    }

    @Test
    public void accountRefreshShouldNotMaintainPrivateStreamSnapshotWhenSessionBecomesInactive() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("历史补拉返回空缓存时不应再清理服务层私有 stream 副本，因为该副本已经删除",
                source.contains("if (cache == null) {\n                    clearStreamAccountSnapshot();"));
        assertTrue("历史补拉结束后仍应刷新悬浮窗，让正式 cache/runtime 生效",
                source.contains("floatingCoordinator.requestRefresh(false);"));
    }

    @Test
    public void explicitAccountRuntimeClearActionShouldClearPreloadCacheAndRefreshFloating() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("监控服务应支持显式账户运行态清理动作，避免登出和切号后继续显示旧仓位",
                source.contains("case AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME:"));
        assertTrue("显式账户运行态清理动作应统一委托预加载管理器清掉内存和持久化运行态",
                source.contains("case AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME:\n                if (accountStatsPreloadManager != null) {\n                    accountStatsPreloadManager.clearAccountRuntimeState(null, null);\n                }"));
        assertTrue("显式账户运行态清理动作后应立即刷新悬浮窗",
                source.contains("floatingCoordinator.requestRefresh(true);"));
    }

    @Test
    public void accountRuntimeShouldApplyPublishedSnapshotBeforeHistoryPull() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("MonitorService 应直接消费 stream 下发的账户运行态",
                source.contains("accountStatsPreloadManager.applyPublishedAccountRuntime("));
        assertTrue("只有 historyRevision 前进时才应补拉 account history",
                source.contains("requestAccountHistoryRefreshFromV2("));
    }

    @Test
    public void accountRuntimeStreamShouldUseDedicatedExecutorInsteadOfGenericBackgroundQueue() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("服务层应为高频账户运行态维护独立执行器，避免被补修和恢复任务挤压",
                source.contains("private ExecutorService accountRuntimeExecutorService;"));
        assertTrue("初始化时应创建账户运行态专用串行执行器",
                source.contains("accountRuntimeExecutorService = Executors.newSingleThreadExecutor();"));
        assertTrue("服务层应提供账户运行态专用投递入口",
                source.contains("private boolean executeAccountRuntime(Runnable task) {"));
        assertTrue("stream 账户运行态应用应走专用执行器，而不是通用后台队列",
                source.contains("executeAccountRuntime(() -> {\n            try {\n                JSONObject snapshotCopy = new JSONObject(snapshotBody);"));
        assertFalse("stream 账户运行态应用不应继续走 executeBackgroundWork，避免 0.5s 更新被堵住",
                source.contains("executeBackgroundWork(() -> {\n            try {\n                JSONObject snapshotCopy = new JSONObject(snapshotBody);\n                AccountStatsPreloadManager.Cache cache =\n                        accountStatsPreloadManager.applyPublishedAccountRuntime(snapshotCopy, publishedAt);"));
    }

    @Test
    public void monitorServiceShouldNotBypassFloatingCoordinatorWithDirectPriceRendering() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("服务层不应直接驱动悬浮窗 UI 更新，必须通过协调器统一刷新",
                source.contains("floatingWindowManager.update("));
        assertTrue("服务层刷新悬浮窗时只应通过 floatingCoordinator.requestRefresh(...)",
                source.contains("floatingCoordinator.requestRefresh(false);")
                        && source.contains("floatingCoordinator.requestRefresh(true);"));
    }

    @Test
    public void monitorServiceShouldRepairMarketTruthAndRefreshFloatingFromUnifiedTruthUpdates() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("repository.getMarketTruthSnapshotLiveData().observeForever(marketTruthObserver);"));
        assertTrue(source.contains("repository.getMarketTruthSnapshotLiveData().removeObserver(marketTruthObserver);"));
        assertTrue(source.contains("private void requestMarketTruthRepair(boolean force) {"));
        assertTrue(source.contains("gatewayV2Client.fetchMarketSeries(symbol, \"1m\", MARKET_TRUTH_REPAIR_LIMIT)"));
        assertTrue(source.contains("repository.applyMarketSeriesPayload("));
        assertTrue(source.contains("requestMarketTruthRepair(true);"));
        assertTrue(source.contains("requestMarketTruthRepair(false);"));
        assertTrue(source.contains("private ExecutorService realtimeMarketExecutorService;"));
        assertTrue(source.contains("private ExecutorService backgroundExecutorService;"));
    }

    @Test
    public void marketTruthRepairGateShouldUseMinuteProgressInsteadOfSnapshotArrivalTime() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private long resolveMarketTruthProgressAt(@Nullable MarketTruthSnapshot snapshot) {"));
        assertTrue(source.contains("MarketTruthSymbolState state = snapshot.getSymbolState(symbol);"));
        assertTrue(source.contains("state.getLastTruthUpdateAt()"));
        assertFalse(source.contains("long updatedAt = snapshot == null ? 0L : Math.max(0L, snapshot.getUpdatedAt());"));
    }

    @Test
    public void malformedAccountRuntimePayloadShouldNotReferenceRemovedStreamSnapshotState() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("stream 账户运行态解析失败时不应再引用已删除的私有 stream 副本",
                source.contains("clearStreamAccountSnapshot();"));
        assertTrue("stream 账户运行态解析失败时仍应记录明确日志",
                source.contains("logManager.warn(\"v2 stream 账户运行态应用失败: \" + exception.getMessage());"));
    }

    @Test
    public void accountHistoryRefreshShouldQueueLatestRevisionWhilePreviousRefreshIsInFlight() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("历史补拉并发 gate 不应继续滞留在服务层",
                source.contains("private final AccountHistoryRefreshGate accountHistoryRefreshGate = new AccountHistoryRefreshGate();"));
        assertTrue("服务层只应把 revision 交给预加载管理器排队处理",
                source.contains("accountStatsPreloadManager.queueHistoryRefreshForRevision("));
        assertFalse("服务层不应再自己调用 gate.tryStart()",
                source.contains("accountHistoryRefreshGate.tryStart("));
        assertFalse("服务层不应再自己调用 gate.finish()",
                source.contains("accountHistoryRefreshGate.finish("));
        assertFalse("服务层不应再自己递归续跑历史补拉",
                source.contains("requestAccountHistoryRefreshFromV2(finishDecision.getNextRevision())"));
    }

    @Test
    public void streamMessageHandlingShouldUseMonotonicBusSeqGuard() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("服务层应维护独立的 stream 顺序守卫，避免旧消息回写主链状态",
                source.contains("private final V2StreamSequenceGuard v2StreamSequenceGuard = new V2StreamSequenceGuard();"));
        assertTrue("新连接建立后应重置顺序守卫，允许接收新的 stream 序列",
                source.contains("if (v2StreamConnected) {\n                        v2StreamSequenceGuard.reset();"));
        assertTrue("消费 stream 消息前应先按 busSeq 判断是否仍是当前有效序列",
                source.contains("if (!v2StreamSequenceGuard.shouldApplyBusSeq(busSeq)) {\n            return;\n        }"));
        assertTrue("marketTick 也应使用独立的 marketSeq 顺序守卫，避免旧 tick 覆盖当前分钟",
                source.contains("if (!v2StreamSequenceGuard.shouldApplyMarketSeq(marketSeq)) {\n            return;\n        }"));
    }

    @Test
    public void monitorServiceShouldNotKeepSecondAccountRuntimeSnapshotForFloatingWindow() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("服务层不应继续维护第二份 stream 持仓副本",
                source.contains("private final List<PositionItem> streamPositionSnapshot = new ArrayList<>();"));
        assertFalse("服务层不应继续维护 stream 账户是否已收到的私有标志",
                source.contains("private volatile boolean streamAccountSnapshotReceived;"));
        assertFalse("服务层不应继续维护 stream 持仓更新时间私有副本",
                source.contains("private volatile long streamPositionsUpdatedAt;"));
        assertTrue("收到 stream 账户运行态后应直接交给账户预加载管理器",
                source.contains("accountStatsPreloadManager.applyPublishedAccountRuntime("));
        assertTrue("stream 账户运行态应用结束后应先刷新连接状态再触发悬浮窗刷新",
                source.contains("mainHandler.post(() -> {\n                    updateConnectionStatus();\n                    floatingCoordinator.requestRefresh(false);\n                });"));
    }

    @Test
    public void monitorServiceShouldDelegatePersistedAccountResetToPreloadManager() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("服务层不应继续自己持有 AccountStorageRepository，避免再长出账户第二副本职责",
                source.contains("private AccountStorageRepository accountStorageRepository;"));
        assertFalse("服务层初始化时不应再自己创建账户存储仓库",
                source.contains("accountStorageRepository = new AccountStorageRepository(this);"));
        assertFalse("服务层不应再自己维护 clearPersistedAccountSnapshot 辅助方法",
                source.contains("private void clearPersistedAccountSnapshot("));
        assertFalse("远端确认 logged_out 后，服务层不应再自己清理当前账号运行态",
                source.contains("accountStatsPreloadManager.clearAccountRuntimeState(localActiveAccount.getLogin(), localActiveAccount.getServer());"));
        assertTrue("服务层应通过账户域 helper 统一触发 logged_out 本地收口",
                source.contains("private AccountSessionRecoveryHelper accountSessionRecoveryHelper;")
                        && source.contains("accountSessionRecoveryHelper.reconcileRemoteSession();"));
    }

    @Test
    public void connectionStatusRefreshShouldSkipForegroundNotificationWhenStatusIsUnchanged() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("连接状态刷新应先读取当前状态，避免每条 stream 消息都重复刷新通知",
                source.contains("String currentStatus = getCurrentConnectionStatus();"));
        assertTrue("连接状态未变化时不应重复 setConnectionStatus 和通知刷新",
                source.contains("if (!status.equals(currentStatus)) {"));
        assertTrue("只有状态变化时才应刷新前台通知",
                source.contains("foregroundNotificationCoordinator.refreshNotification(status);"));
        assertTrue("状态变化时应通过统一发布入口写回仓库和本地真值",
                source.contains("publishConnectionStatus(status);"));
        assertTrue("连接状态字符串前应先由统一连接阶段解析器得出当前阶段",
                source.contains("ConnectionStage resolvedStage = getCurrentConnectionStage();"));
    }

    @Test
    public void floatingSnapshotShouldCarryConnectionStageInsteadOfOnlyPassingStatusText() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("悬浮窗快照应带上统一连接阶段，避免 UI 再按字符串猜状态",
                source.contains("return new FloatingWindowSnapshot(\n                dataSource.getCurrentConnectionStage(),"));
        assertTrue("悬浮窗快照应带上全部产品总持仓数量，避免顶栏被当前可见产品过滤",
                source.contains("resolveTotalFloatingPositionCount(cache),"));
        assertTrue("悬浮窗快照应带上全部产品总持仓盈亏，避免顶栏只跟随当前可见卡片",
                source.contains("resolveTotalFloatingPositionPnl(cache),"));
        assertTrue("悬浮窗总持仓盈亏应优先复用统一运行态产品快照",
                source.contains("runtimeSnapshotStore.selectAllProducts("));
    }

    @Test
    public void monitorServiceShouldFilterFloatingCacheByCurrentSessionIdentity() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("MonitorService 应持有 SecureSessionPrefs 复用当前活动会话身份",
                source.contains("private SecureSessionPrefs secureSessionPrefs;"));
        assertTrue("悬浮窗数据源不应直接暴露 preload 原始 latestCache",
                source.contains("return resolveCurrentSessionFloatingCache();"));
        assertTrue("MonitorService 应提供当前会话过滤后的悬浮窗 cache 入口",
                source.contains("private AccountStatsPreloadManager.Cache resolveCurrentSessionFloatingCache() {"));
        assertTrue("悬浮窗 cache 过滤应校验当前会话开关和活动账号",
                source.contains("!configManager.isAccountSessionActive()")
                        && source.contains("SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();")
                        && source.contains("RemoteAccountProfile activeAccount = sessionSummary.getActiveAccount();"));
        assertTrue("悬浮窗 cache 过滤应严格比较账号和服务器身份",
                source.contains("if (!expectedAccount.equalsIgnoreCase(trimToEmpty(cache.getAccount()))")
                        && source.contains("|| !expectedServer.equalsIgnoreCase(trimToEmpty(cache.getServer()))) {"));
    }

    @Test
    public void v2StreamPayloadShouldBeParsedOffMainThread() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("stream 消息应先进入实时市场执行器，不能直接把 payload 解析压到主线程",
                source.contains("executeRealtimeMarket(() -> {\n                    try {\n                        handleV2StreamMessage(message);"));
        assertFalse("收到 stream 文本时不能先刷新成功应用时间，必须等业务应用成功",
                source.contains("executeRealtimeMarket(() -> {\n                    lastV2StreamMessageAt = System.currentTimeMillis();"));
        assertTrue("后台消费完 stream 后，主线程只负责刷新连接状态",
                source.contains("mainHandler.post(MonitorService.this::updateConnectionStatus);")
                        || source.contains("mainHandler.post(() -> updateConnectionStatus());"));
        assertFalse("主线程不应直接执行 handleV2StreamMessage(message)",
                source.contains("mainHandler.post(() -> {\n                    lastV2StreamMessageAt = System.currentTimeMillis();\n                    try {\n                        handleV2StreamMessage(message);"));
    }

    @Test
    public void lateCallbacksShouldNotSubmitWorkIntoDestroyedExecutors() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("MonitorService 应通过实时/后台两条安全入口向执行器派发任务",
                source.contains("executeRealtimeMarket(() -> {")
                        && source.contains("executeBackgroundWork(() -> {"));
        assertTrue("安全投递入口应先判断执行器是否已关闭",
                source.contains("if (currentExecutor == null || currentExecutor.isShutdown() || currentExecutor.isTerminated()) {"));
        assertTrue("执行器销毁后应回收引用，避免晚到回调继续命中旧实例",
                source.contains("realtimeMarketExecutorService = null;")
                        && source.contains("backgroundExecutorService = null;"));
        assertTrue("晚到回调命中已关闭线程池时应显式吞掉拒绝异常",
                source.contains("} catch (RejectedExecutionException exception) {"));
    }

    @Test
    public void workerDispatchShouldSkipLateCallbacksAfterServiceDestroy() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("服务层应同时暴露实时市场、账户运行态与后台任务三个安全投递入口",
                source.contains("private boolean executeRealtimeMarket(Runnable task) {")
                        && source.contains("private boolean executeAccountRuntime(Runnable task) {")
                        && source.contains("private boolean executeBackgroundWork(Runnable task) {"));
        assertTrue("服务销毁时应先关闭三条执行器再清空引用，避免晚到回调继续提交",
                source.contains("realtimeMarketExecutorService.shutdownNow();\n            realtimeMarketExecutorService = null;")
                        && source.contains("accountRuntimeExecutorService.shutdownNow();\n            accountRuntimeExecutorService = null;")
                        && source.contains("backgroundExecutorService.shutdownNow();\n            backgroundExecutorService = null;"));
        assertTrue("stream 消息应通过安全投递入口执行，避免销毁后 RejectedExecutionException",
                source.contains("executeRealtimeMarket(() -> {\n                    try {\n                        handleV2StreamMessage(message);"));
        assertTrue("历史补拉应直接委托预加载管理器自己的排队入口，避免服务层继续维护第二套线程与 gate",
                source.contains("accountStatsPreloadManager.queueHistoryRefreshForRevision("));
        assertTrue("账户运行态应用应通过安全投递入口执行，避免销毁后继续提交",
                source.contains("executeAccountRuntime(() -> {\n            try {\n                JSONObject snapshotCopy = new JSONObject(snapshotBody);"));
        assertTrue("异常配置同步也应通过安全投递入口执行，避免销毁后继续提交",
                source.contains("executeBackgroundWork(() -> {\n            AbnormalGatewayClient.PushResult result = abnormalGatewayClient.pushConfig"));
    }

    @Test
    public void foregroundNotificationShouldUseStableSignatureToSkipDuplicateNotify() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java"
        );

        assertTrue("前台通知应缓存上次签名，避免高频重复 notify",
                source.contains("private String lastForegroundNotificationSignature = \"\";"));
        assertTrue("前台通知刷新前应比较签名",
                source.contains("if (signature.equals(lastForegroundNotificationSignature)) {"));
    }

    @Test
    public void connectionStatusDedupShouldUseServiceLocalTruthInsteadOfAsyncLiveDataReadBack() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("连接状态去重应维护服务内本地真值，不能只回读 LiveData.getValue()",
                source.contains("private String lastPublishedConnectionStatus = \"\";"));
        assertTrue("连接状态写入仓库前应先同步更新服务内本地真值",
                source.contains("private void publishConnectionStatus(String status) {"));
        assertTrue("连接状态去重应读取服务内当前真值，而不是直接依赖 postValue 回读结果",
                source.contains("String currentStatus = getCurrentConnectionStatus();"));
    }

    @Test
    public void onDestroyShouldReleaseForegroundAndFloatingLifecycleChains() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("服务销毁时应清掉主线程挂起回调",
                source.contains("mainHandler.removeCallbacksAndMessages(null);"));
        assertTrue("服务销毁时应移除应用前后台监听",
                source.contains("AppForegroundTracker.getInstance().removeListener(appForegroundListener);"));
        assertTrue("服务销毁时应关闭悬浮窗协调器",
                source.contains("if (floatingCoordinator != null) {\n            floatingCoordinator.onDestroy();"));
        assertTrue("服务销毁时应关闭前台通知协调器",
                source.contains("if (foregroundNotificationCoordinator != null) {\n            foregroundNotificationCoordinator.onDestroy();"));
    }

    @Test
    public void serviceShouldListenScreenOnOffToSuspendFloatingRefreshWhenScreenIsOff() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {"));
        assertTrue(source.contains("if (Intent.ACTION_SCREEN_OFF.equals(action)) {"));
        assertTrue(source.contains("if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {"));
        assertTrue(source.contains("registerScreenStateReceiver();"));
        assertTrue(source.contains("unregisterReceiver(screenStateReceiver);"));
        assertTrue(source.contains("private void handleScreenInteractiveChanged(boolean interactive) {"));
        assertTrue(source.contains("floatingCoordinator.setScreenInteractive(interactive);"));
    }

    @Test
    public void abnormalBlinkShouldRouteThroughCoordinatorSoScreenOffCanSuspendIt() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("if (recordManager.addRecordIfAbsent(record) && floatingCoordinator != null) {"));
        assertTrue(source.contains("floatingCoordinator.notifyAbnormalEvent(record.getSymbol());"));
        assertFalse(source.contains("floatingWindowManager.notifyAbnormalEvent(record.getSymbol());"));
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
        throw new IllegalStateException("找不到 MonitorService.java");
    }
}
