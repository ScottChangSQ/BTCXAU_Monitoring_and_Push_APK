package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceCpuPolicySourceTest {

    @Test
    public void serviceShouldDowngradeRuntimeByForegroundAndScreenState() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/service/MonitorService.java");

        assertTrue(source.contains("private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {"));
        assertTrue(source.contains("registerScreenStateReceiver();"));
        assertTrue(source.contains("private MonitorStreamRuntimeModeHelper.RuntimeMode resolveStreamRuntimeMode() {"));
        assertTrue(source.contains("if (MonitorStreamRuntimeModeHelper.shouldApplyFullAccountRuntime(runtimeMode)) {"));
        assertTrue(source.contains("} else if (MonitorStreamRuntimeModeHelper.shouldApplyLiteAccountRuntime(runtimeMode)) {"));
        assertTrue(source.contains("applyAccountSnapshotLiteFromStream(plan.getAccountSnapshot(), message.getPublishedAt());"));
    }

    @Test
    public void serviceShouldSkipHistoryAndFloatingWorkInAlertOnlyMode() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/service/MonitorService.java");

        assertTrue(source.contains("if (plan.shouldPullAccountHistory() && MonitorStreamRuntimeModeHelper.shouldPullAccountHistory(runtimeMode)) {"));
        assertTrue(source.contains("if (plan.shouldRefreshFloating() && MonitorStreamRuntimeModeHelper.shouldRefreshFloating(runtimeMode)) {"));
        assertTrue(source.contains("if (MonitorStreamRuntimeModeHelper.shouldRefreshFloating(resolveStreamRuntimeMode())) {"));
    }

    @Test
    public void preloadManagerShouldProvideLiteAccountRuntimeApplyPath() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java");

        assertTrue(source.contains("public Cache applyPublishedAccountRuntimeLite(JSONObject accountRuntimeSnapshot, long publishedAt) {"));
        assertTrue(source.contains("replaceLatestCache(cache, false);"));
        assertTrue(source.contains("private Cache buildLiteCacheFromPublishedRuntime(JSONObject runtimeSnapshot,"));
    }

    private String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
