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
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        int applyIndex = source.indexOf("applyPreloadedCacheIfAvailable()");
        int refreshIndex = source.indexOf("preloadManager.fetchForUi(fetchRange)");
        int legacyIndex = source.indexOf("gatewayClient.fetch", refreshIndex);

        assertTrue("账户页进入时应先尝试应用预加载缓存", applyIndex >= 0);
        assertTrue("主动刷新应通过 preloadManager.fetchForUi(fetchRange) 走统一 v2 优先链路", refreshIndex > applyIndex);
        assertTrue("账户页不应继续自己直连旧 gatewayClient.fetch", legacyIndex < 0);
    }

    @Test
    public void foregroundEntryShouldTriggerImmediateUiRefresh() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户页应提供统一的前台进入刷新入口",
                source.contains("private void requestForegroundEntrySnapshot()"));
        assertTrue("账户页创建时应启动监控服务，避免首次直达账户页时服务未就绪",
                source.contains("ensureMonitorServiceStarted();"));
        assertTrue("账户页应提供单独的服务启动入口，避免 onResume 每次都误触发 bootstrap",
                source.contains("private void ensureMonitorServiceStarted()"));
        assertTrue("账户页回到前台时也应先确认服务仍在运行，避免服务被系统回收后页面空转",
                source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();"));
        assertFalse("账户页回到前台时不应无条件重建会话客户端传输层",
                source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();\n        applyPaletteStyles();\n        applyPrivacyMaskState();\n        if (sessionClient != null) {\n            sessionClient.resetTransport();"));
        assertTrue("账户页回到前台时不应再把页面恢复误当成服务 bootstrap",
                !source.contains("protected void onResume() {\n        super.onResume();\n        applyPaletteStyles();\n        applyPrivacyMaskState();\n        sendServiceAction(AppConstants.ACTION_BOOTSTRAP);"));
        assertTrue("页面进入前台应统一走账户页恢复入口，而不是在 onCreate/onResume 各自散落 bootstrap 逻辑",
                source.contains("enterAccountScreen(true);")
                        && source.contains("enterAccountScreen(false);"));
        assertTrue("账户页恢复入口应先消费当前会话缓存，再决定是否需要 bootstrap",
                source.contains("private void enterAccountScreen(boolean coldStart) {")
                        && source.contains("applyPreloadedCacheIfAvailable();"));
        assertFalse("账户页已有当前会话缓存时，不应再因为 fetchedAt 过旧而拒绝先渲染已有快照",
                source.contains("System.currentTimeMillis() - cache.getFetchedAt() > AppConstants.ACCOUNT_REFRESH_INTERVAL_MS * 3L"));
        assertTrue("已有当前会话可渲染状态时，只应恢复刷新节奏，不应立即全量刷新",
                source.contains("if (hasRenderableCurrentSessionState()) {")
                        && source.contains("scheduleNextSnapshot(dynamicRefreshDelayMs);"));
        assertTrue("只有确实没有可渲染状态时，才应触发远程会话状态同步或前景快照刷新",
                source.contains("if (shouldBootstrapRemoteSession()) {")
                        && source.contains("requestForegroundEntrySnapshot();"));
        assertTrue("普通快照刷新不应再把“未连接 -> 已连接”误当成登录成功",
                !source.contains("|| AccountConnectionTransitionHelper.shouldShowLoginSuccess("));
        assertTrue("前台进入刷新不应再因为缓存够新而只做延后调度",
                !source.contains("if (hasFreshPreloadedCache()) {\n                    scheduleNextSnapshot(dynamicRefreshDelayMs);"));
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
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("预加载缓存命中时应先构建签名，再决定是否需要整页重画",
                source.contains("String cacheSignature = buildRefreshSignature("));
        assertTrue("缓存签名未变化时应直接跳过 applySnapshot，避免切 tab 时整页重算",
                source.contains("if (cacheSignature.equals(lastAppliedSnapshotSignature)) {\n            return;\n        }"));
        assertTrue("命中新签名后才应应用快照，并回写最新签名",
                source.contains("applySnapshot(cache.getSnapshot(), cache.isConnected());\n        lastAppliedSnapshotSignature = cacheSignature;"));
    }

    @Test
    public void preloadCacheListenerShouldNotDropTradeRefreshWhileLoading() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("账户页应保留统一的预加载缓存监听器",
                source.contains("private final AccountStatsPreloadManager.CacheListener preloadCacheListener = cache -> {"));
        assertTrue("预加载缓存监听器命中后应继续应用当前缓存，而不是直接丢弃交易后的强刷结果",
                source.contains("if (cache == null || isFinishing() || isDestroyed()) {\n            return;\n        }\n        applyPreloadedCacheIfAvailable();"));
        assertTrue("账户页不应再因为 loading=true 而跳过预加载缓存更新",
                !source.contains("if (cache == null || isFinishing() || isDestroyed() || loading) {"));
    }

    @Test
    public void refreshCadenceShouldUseSnapshotSignatureInsteadOfHardcodedUnchangedFalse() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("刷新节流应基于快照签名判断 unchanged",
                source.contains("finalSignature.equals(lastAppliedSnapshotSignature)"));
        assertTrue("不应再把 finalUnchanged 写死为 false",
                !source.contains("final boolean finalUnchanged = false;"));
        assertTrue("应存在统一快照签名构建方法",
                source.contains("private String buildRefreshSignature("));
        assertTrue("快照签名应做顺序无关排序，避免仅顺序变化导致误判",
                source.contains("Collections.sort(entries);"));
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
