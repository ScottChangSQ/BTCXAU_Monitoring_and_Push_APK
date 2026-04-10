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
        );

        assertTrue("前台切回时应走统一前台状态处理入口",
                source.contains("foreground -> mainHandler.post(() -> handleForegroundStateChanged(foreground))"));
        assertTrue("前台切回时应先判断当前 stream 是否仍健康",
                source.contains("boolean streamHealthy = isV2StreamHealthy(System.currentTimeMillis());"));
        assertTrue("stream 健康时应直接返回，不再继续重建主链连接",
                source.contains("if (streamHealthy) {\n            floatingCoordinator.requestRefresh(false);"));
        assertTrue("只有 stream 已失活时才应继续走后面的重建逻辑",
                source.contains("if (streamHealthy) {\n            floatingCoordinator.requestRefresh(false);\n            updateConnectionStatus();\n            return;\n        }\n"));
        assertFalse("前台切回时不应无条件重建 v2 stream",
                source.contains("restartV2Stream(\"foreground_resume\");\n        requestForegroundEntryRefresh();"));
        assertFalse("前台切回时不应再主动补拉 /v2/account/snapshot",
                source.contains("requestAccountRefreshFromV2();"));
        assertTrue("stream 健康时只应切换消费层刷新节奏，不应做全量刷新",
                source.contains("floatingCoordinator.requestRefresh(false);"));
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
    public void monitorServiceShouldStayForegroundInsteadOfSuppressingNotification() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertFalse("监控服务不应在启动后立刻撤销前台服务身份，否则息屏后会被系统限制",
                source.contains("suppressForegroundNotification();"));
        assertFalse("监控服务不应主动调用 stopForeground(true) 撤掉持续运行资格",
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
    public void startupShouldLogResolvedGatewayAddresses() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("服务启动时应打印构建默认 MT5 网关地址",
                source.contains("APP诊断 BuildConfig MT5="));
        assertTrue("服务启动时应打印运行时解析后的 MT5 网关地址",
                source.contains("APP诊断 Runtime MT5="));
        assertTrue("服务启动时应打印运行时解析后的 Binance WS 地址",
                source.contains("APP诊断 Runtime BinanceWS="));
        assertTrue("服务启动时应统一走地址诊断入口",
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
    public void accountRefreshShouldClearStreamSnapshotWhenSessionBecomesInactive() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("历史补拉返回空缓存时应清理 stream 持仓快照，避免悬浮窗残留旧仓位",
                source.contains("if (cache == null) {\n                    clearStreamAccountSnapshot();"));
    }

    @Test
    public void explicitAccountRuntimeClearActionShouldResetStreamSnapshotAndRefreshFloating() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("监控服务应支持显式账户运行态清理动作，避免登出和切号后继续显示旧仓位",
                source.contains("case AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME:"));
        assertTrue("显式账户运行态清理动作应清空 stream 持仓快照",
                source.contains("case AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME:\n                clearStreamAccountSnapshot();"));
        assertTrue("显式账户运行态清理动作后应立即刷新悬浮窗",
                source.contains("case AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME:\n                clearStreamAccountSnapshot();\n                floatingCoordinator.requestRefresh(true);"));
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
    public void accountHistoryRefreshShouldQueueLatestRevisionWhilePreviousRefreshIsInFlight() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("历史补拉并发期间应暂存后到的新 revision，不能直接丢弃",
                source.contains("private String pendingAccountHistoryRevision = \"\";"));
        assertTrue("已有历史补拉进行中时，应把最新 revision 记下来等待续跑",
                source.contains("pendingAccountHistoryRevision = safeHistoryRevision;"));
        assertTrue("当前补拉结束后应读取暂存 revision 决定是否续跑",
                source.contains("String nextHistoryRevision;"));
        assertTrue("如果期间来了不同的 revision，应在当前补拉结束后继续补最新一版",
                source.contains("if (!nextHistoryRevision.isEmpty() && !nextHistoryRevision.equals(safeHistoryRevision)) {"));
    }

    @Test
    public void floatingSnapshotShouldKeepAllPositionsInsteadOfOverwritingByCode() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        );

        assertTrue("stream 持仓快照应保留完整列表，避免同产品多笔仓位被覆盖",
                source.contains("private final List<PositionItem> streamPositionSnapshot = new ArrayList<>();"));
        assertTrue("stream 账户快照应用时应追加所有仓位，而不是按 code 覆盖",
                source.contains("streamPositionSnapshot.add(item);"));
        assertFalse("不应按 code 覆盖 stream 持仓快照",
                source.contains("streamPositionSnapshot.put(item.getCode().trim().toUpperCase(), item);"));
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
        );

        assertTrue("悬浮窗快照应带上统一连接阶段，避免 UI 再按字符串猜状态",
                source.contains("return new FloatingWindowSnapshot(\n                dataSource.getCurrentConnectionStage(),"));
    }

    @Test
    public void v2StreamPayloadShouldBeParsedOffMainThread() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("stream 消息应先进入后台串行执行器，不能直接把 payload 解析压到主线程",
                source.contains("executorService.execute(() -> {\n                    lastV2StreamMessageAt = System.currentTimeMillis();"));
        assertTrue("后台消费完 stream 后，主线程只负责刷新连接状态",
                source.contains("mainHandler.post(MonitorService.this::updateConnectionStatus);")
                        || source.contains("mainHandler.post(() -> updateConnectionStatus());"));
        assertFalse("主线程不应直接执行 handleV2StreamMessage(message)",
                source.contains("mainHandler.post(() -> {\n                    lastV2StreamMessageAt = System.currentTimeMillis();\n                    try {\n                        handleV2StreamMessage(message);"));
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

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MonitorService.java");
    }
}
