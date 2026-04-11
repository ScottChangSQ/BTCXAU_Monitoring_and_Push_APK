package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeActivityV2RefreshSourceTest {

    @Test
    public void requestSnapshotShouldFavorPreloadCacheWhenAvailable() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"
        );
        int applyIndex = source.indexOf("applyPreloadedCacheIfAvailable()");
        int refreshIndex = source.indexOf("host.fetchForUi(AccountTimeRange.ALL)");
        int legacyIndex = source.indexOf("gatewayClient.fetch", refreshIndex);

        assertTrue("账户页进入时应先尝试应用预加载缓存", applyIndex >= 0);
        assertTrue("主动刷新应通过协调器里的 host.fetchForUi(AccountTimeRange.ALL) 走统一 v2 优先链路", refreshIndex > applyIndex);
        assertTrue("账户页不应继续自己直连旧 gatewayClient.fetch", legacyIndex < 0);
    }

    @Test
    public void foregroundEntryShouldTriggerImmediateUiRefresh() throws Exception {
        String activitySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        String coordinatorSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"
        );

        assertTrue("账户页应提供统一的前台进入刷新入口",
                activitySource.contains("private void requestForegroundEntrySnapshot()"));
        assertTrue("账户页创建时应启动监控服务，避免首次直达账户页时服务未就绪",
                activitySource.contains("ensureMonitorServiceStarted();"));
        assertTrue("账户页应提供单独的服务启动入口，避免 onResume 每次都误触发 bootstrap",
                activitySource.contains("private void ensureMonitorServiceStarted()"));
        assertTrue("账户页回到前台时也应先确认服务仍在运行，避免服务被系统回收后页面空转",
                activitySource.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();"));
        assertTrue("页面进入前台应统一走账户页恢复入口，而不是在 onCreate/onResume 各自散落 bootstrap 逻辑",
                activitySource.contains("enterAccountScreen(true);")
                        && activitySource.contains("enterAccountScreen(false);"));
        assertTrue("账户页恢复入口应先消费当前会话缓存，再决定是否需要 bootstrap",
                coordinatorSource.contains("void enterAccountScreen(boolean coldStart) {")
                        && coordinatorSource.contains("applyPreloadedCacheIfAvailable();"));
        assertFalse("账户页已有当前会话缓存时，不应再因为 fetchedAt 过旧而拒绝先渲染已有快照",
                coordinatorSource.contains("System.currentTimeMillis() - cache.getFetchedAt() > AppConstants.ACCOUNT_REFRESH_INTERVAL_MS * 3L"));
        assertTrue("已有当前会话可渲染状态时，只应恢复刷新节奏，不应立即全量刷新",
                coordinatorSource.contains("if (host.hasRenderableCurrentSessionState()) {")
                        && coordinatorSource.contains("host.scheduleNextSnapshot(host.getDynamicRefreshDelayMs());"));
        assertTrue("只有确实没有可渲染状态时，才应触发远程会话状态同步或前景快照刷新",
                coordinatorSource.contains("if (host.shouldBootstrapRemoteSession()) {")
                        && coordinatorSource.contains("requestForegroundEntrySnapshot();"));
        assertTrue("普通快照刷新不应再把“未连接 -> 已连接”误当成登录成功",
                !coordinatorSource.contains("|| AccountConnectionTransitionHelper.shouldShowLoginSuccess("));
        assertTrue("前台进入刷新不应再因为缓存够新而只做延后调度",
                !coordinatorSource.contains("if (hasFreshPreloadedCache()) {\n                    scheduleNextSnapshot(dynamicRefreshDelayMs);"));
    }

    @Test
    public void bindLocalMetaShouldUseCurrentSessionCacheBeforeRenderingDisconnectedState() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户页应提供当前会话缓存解析入口，避免新建页面首帧先显示未连接",
                source.contains("private AccountStatsPreloadManager.Cache resolveCurrentSessionCache()"));
        assertTrue("bindLocalMeta 应先尝试消费当前会话缓存",
                source.contains("AccountStatsPreloadManager.Cache cache = resolveCurrentSessionCache();"));
        assertTrue("命中当前会话缓存时应直接应用缓存元数据并返回，避免继续写入未连接状态",
                source.contains("applyCacheMeta(cache);\n            return;"));
    }

    @Test
    public void preloadedCacheApplyShouldSkipHeavyRenderWhenSignatureUnchanged() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("预加载缓存命中时应先构建签名，再决定是否需要整页重画",
                source.contains("String cacheSignature = host.buildRefreshSignature("));
        assertTrue("缓存签名未变化时应直接跳过 applySnapshot，避免切 tab 时整页重算",
                source.contains("if (cacheSignature.equals(host.getLastAppliedSnapshotSignature())) {\n            return;\n        }"));
        assertTrue("命中新签名后才应应用快照，并回写最新签名",
                source.contains("host.applySnapshot(cache.getSnapshot(), cache.isConnected());\n        host.setLastAppliedSnapshotSignature(cacheSignature);"));
    }

    @Test
    public void preloadCacheListenerShouldSkipCacheReplayWhileExplicitSnapshotIsLoading() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户页应保留统一的预加载缓存监听器",
                source.contains("private final AccountStatsPreloadManager.CacheListener preloadCacheListener = cache -> {"));
        assertTrue("显式快照请求进行中时，预加载监听器不应再回灌缓存覆盖当前请求链路",
                source.contains("if (cache == null || isFinishing() || isDestroyed() || loading) {\n            return;\n        }\n        if (snapshotRefreshCoordinator != null) {\n            snapshotRefreshCoordinator.applyPreloadedCacheIfAvailable();\n        }"));
    }

    @Test
    public void refreshCadenceShouldUseSnapshotSignatureInsteadOfHardcodedUnchangedFalse() throws Exception {
        String coordinatorSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"
        );
        String activitySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("刷新节流应基于快照签名判断 unchanged",
                coordinatorSource.contains("finalSignature.equals(host.getLastAppliedSnapshotSignature())"));
        assertTrue("不应再把 finalUnchanged 写死为 false",
                !coordinatorSource.contains("final boolean finalUnchanged = false;"));
        assertTrue("应存在统一快照签名构建方法",
                activitySource.contains("private String buildRefreshSignature("));
        assertTrue("快照签名应做顺序无关排序，避免仅顺序变化导致误判",
                activitySource.contains("Collections.sort(entries);"));
    }

    @Test
    public void transientDisconnectedSnapshotShouldNotClearRenderablePositions() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户页应显式区分“真实快照”和“页面自己合成的断线空快照”",
                source.contains("private boolean shouldApplyFetchedSnapshot("));
        assertTrue("当前页已经有可渲染快照时，临时断线不应再用合成空快照清空持仓",
                source.contains("if (syntheticDisconnectedSnapshot && hasRenderableCurrentSessionState()) {\n            return false;\n        }"));
        assertTrue("快照回写前应统一通过 shouldApplyFetchedSnapshot 决定是否重画",
                source.contains("private boolean shouldApplyFetchedSnapshot("));
    }

    @Test
    public void staleHistoryResponseShouldNotOverwriteNewerTradeHistoryAlreadyAppliedFromCache() throws Exception {
        String coordinatorSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String activitySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("请求发起时应记录当时页面所见的历史修订号，供回包时判断是否过期",
                coordinatorSource.contains("final String requestStartHistoryRevision = requestStartCache == null\n                ? \"\"\n                : trim(requestStartCache.getHistoryRevision());"));
        assertTrue("账户页应提供统一的旧历史回包拦截方法",
                activitySource.contains("private boolean shouldRejectStaleHistorySnapshot("));
        assertTrue("如果请求发出后页面已经收到新的 historyRevision，旧回包不应再覆盖当前交易记录",
                activitySource.contains("if (currentRevision.equals(requestRevision)) {\n            return false;\n        }\n        return !currentRevision.equals(incomingRevision);"));
    }

    @Test
    public void refreshSignatureShouldIncludeTradeAndCurveSections() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户页签名必须覆盖交易记录，避免平仓后交易列表被误判为未变化",
                source.contains("appendTradeSignature(builder, snapshot.getTrades());"));
        assertTrue("账户页签名应覆盖净值曲线，避免曲线更新时被误判成旧快照",
                source.contains("appendCurveSignature(builder, snapshot.getCurvePoints());"));
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
