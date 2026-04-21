package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsPreloadManagerSourceTest {

    @Test
    public void preloadManagerShouldUseGatewayV2AsOnlyAccountSource() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("applyPublishedAccountRuntime("));
        assertTrue(source.contains("refreshHistoryForRevision("));
        assertTrue(source.contains("fetchFullForUi("));
        assertTrue(source.contains("gatewayV2Client.fetchAccountFull()"));
        assertTrue(source.contains("gatewayV2Client.fetchAccountHistory("));
        assertFalse(source.contains("Mt5BridgeGatewayClient"));
        assertFalse(Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java")));
    }

    @Test
    public void preloadManagerShouldConsumeServerMetricsInsteadOfBuildingLocally() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("runtimeSnapshot.optJSONArray(\"overviewMetrics\")"));
        assertTrue(source.contains("historyPayload.getCurveIndicators()"));
        assertTrue(source.contains("historyPayload.getStatsMetrics()"));
        assertFalse(source.contains("buildOverviewMetrics("));
        assertFalse(source.contains("buildStatsMetrics("));
    }

    @Test
    public void preloadManagerShouldNotBackfillTradeLifecycleFieldsLocally() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        );

        assertFalse(source.contains("optLongAny(item, timestamp,"));
        assertFalse(source.contains("optDoubleAny(item, price,"));
        assertFalse(source.contains("normalizeEpochMs("));
    }

    @Test
    public void preloadManagerShouldConsumeCanonicalSnapshotAndTradeFieldsOnly() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        );

        assertFalse(source.contains("\"open_time\""));
        assertFalse(source.contains("\"timeOpen\""));
        assertFalse(source.contains("\"time_close\""));
        assertFalse(source.contains("\"open_price\""));
        assertFalse(source.contains("\"entryPrice\""));
        assertFalse(source.contains("\"exitPrice\""));
        assertFalse(source.contains("\"deal_ticket\""));
        assertFalse(source.contains("\"entry_type\""));
        assertFalse(source.contains("optString(item, \"productName\", optString(item, \"code\","));
        assertFalse(source.contains("optString(item, \"code\", optString(item, \"productName\","));
        assertFalse(source.contains("optDoubleAny(item, 0d, \"quantity\", \"qty\", \"volume\")"));
    }

    @Test
    public void runtimeApplyShouldReuseStoredHistoryWhenSkippingHistoryReload() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("AccountStorageRepository.StoredSnapshot runtimeSnapshot =\n                    buildStoredSnapshotFromPublishedRuntime(accountRuntimeSnapshot, publishedAt);"));
        assertTrue(source.contains("AccountStorageRepository.StoredSnapshot previousSnapshot = loadStoredSnapshotForWorkerThread();"));
        assertTrue(source.contains("AccountStorageRepository.StoredSnapshot mergedSnapshot =\n                    mergePublishedRuntimeWithStoredHistory(runtimeSnapshot, previousSnapshot);"));
        assertTrue(source.contains("Cache cache = buildCache(mergedSnapshot, resolvedRevision);"));
    }

    @Test
    public void historyRefreshShouldFallBackToStoredHistoryRevisionWhenMemoryCacheMissing() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("AccountStorageRepository.StoredSnapshot storedSnapshot =\n                    loadStoredSnapshotForWorkerThread();"));
        assertTrue(source.contains("int storedTradeCount = loadStoredTradeCountForWorkerThread();"));
        assertTrue(source.contains("String cachedHistoryRevision = previous == null\n                    ? storedSnapshot.getHistoryRevision()\n                    : previous.getHistoryRevision();"));
    }

    @Test
    public void historyRefreshShouldOnlyFetchHistoryAfterRevisionChange() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("boolean shouldRefreshAllHistory = AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("));
        assertTrue(source.contains("AccountHistoryPayload historyPayload = fetchCompleteHistoryPayload(AccountTimeRange.ALL);"));
        assertTrue(source.contains("if (!shouldRefreshAllHistory) {"));
    }

    @Test
    public void historyRefreshShouldHydrateMemoryCacheFromStoredSnapshotWhenSkippingReload() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("if (!shouldRefreshAllHistory) {\n                if (previous != null) {\n                    return previous;\n                }\n                Cache cache = buildCache(storedSnapshot, cachedHistoryRevision);\n                updateLatestCache(cache);\n                return cache;\n            }"));
    }

    @Test
    public void preloadManagerFailureCacheShouldNotReusePreviousSnapshotOrLegacyTradeCountAlias() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("previous.snapshot"));
        assertFalse(source.contains("\"trade_count\""));
        assertTrue(source.contains("new AccountSnapshot("));
        assertTrue(source.contains("optString(runtimeMeta, \"historyRevision\", \"\")"));
    }

    @Test
    public void preloadManagerShouldRequireHistoryRevisionFromSnapshotMetaOnly() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("String runtimeRevision = optString(runtimeMeta, \"historyRevision\", \"\")"));
        assertTrue(source.contains("throw new IllegalStateException(\"published account runtime missing historyRevision\")"));
        assertTrue(source.contains("throw new IllegalStateException(\"v2 account history refresh missing historyRevision\")"));
    }

    @Test
    public void preloadManagerShouldResetGatewayTransportWhenAppReturnsForeground() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("if (foreground) {\n            gatewayV2Client.resetTransport();\n        }"));
    }

    @Test
    public void preloadManagerShouldOnlyRescheduleCadenceWhenAppReturnsForeground() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("scheduleFetch(foreground ? 0L : nextDelayMs);"));
        assertTrue(source.contains("scheduleFetch(nextDelayMs);"));
    }

    @Test
    public void preloadManagerShouldKeepGetLatestCacheAsMemoryOnlyAccessor() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public Cache getLatestCache() {\n        Cache cache = latestCache;\n        if (cache != null || !isAccountSessionActive()) {\n            return cache;\n        }\n        return null;\n    }"));
        assertFalse(source.contains("public Cache getLatestCache() {\n        Cache cache = latestCache;\n        if (cache != null || !isAccountSessionActive()) {\n            return cache;\n        }\n        AccountStorageRepository.StoredSnapshot storedSnapshot = accountStorageRepository.loadStoredSnapshot();"));
    }

    @Test
    public void preloadManagerShouldExposeBackgroundHydrationFromStoredSnapshot() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public Cache hydrateLatestCacheFromStorage() {"));
        assertTrue(source.contains("AccountStorageRepository.StoredSnapshot storedSnapshot = loadStoredSnapshotForWorkerThread();"));
        assertTrue(source.contains("Cache hydratedCache = buildCache(storedSnapshot, storedSnapshot.getHistoryRevision());"));
        assertTrue(source.contains("latestCache = hydratedCache;"));
    }

    @Test
    public void startShouldKickOffAsyncStoragePreheatBeforeRegularSchedule() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("registerForegroundListenerIfNeeded();\n        preheatLatestCacheFromStorageIfNeeded();\n        scheduleFetch(0L);"));
        assertTrue(source.contains("private void preheatLatestCacheFromStorageIfNeeded() {"));
        assertTrue(source.contains("if (!storageHydrationInFlight.compareAndSet(false, true)) {\n            return;\n        }"));
    }

    @Test
    public void storagePreheatShouldPromoteHydratedCacheThroughUnifiedUpdatePath() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private Cache hydrateLatestCacheFromStorageInternal(boolean notifyConsumers) {"));
        assertTrue(source.contains("if (notifyConsumers) {\n            updateLatestCache(hydratedCache);\n        } else {\n            latestCache = hydratedCache;\n        }"));
        assertTrue(source.contains("hydrateLatestCacheFromStorageInternal(true);"));
    }

    @Test
    public void preloadManagerShouldExposeOnlyThreeFormalAccountEntryPoints() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public Cache applyPublishedAccountRuntime(JSONObject accountRuntimeSnapshot, long publishedAt) {"));
        assertTrue(source.contains("public Cache refreshHistoryForRevision(String remoteHistoryRevision) {"));
        assertTrue(source.contains("public Cache fetchFullForUi(AccountTimeRange range) {"));
        assertFalse(source.contains("public Cache fetchForUi(AccountTimeRange range) {"));
        assertFalse(source.contains("public Cache fetchForOverlay() {"));
    }

    @Test
    public void preloadManagerShouldOwnPersistedAccountRuntimeResetInsteadOfMonitorService() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public void clearAccountRuntimeState(String account, String server) {"));
        assertTrue(source.contains("fullSnapshotActive = false;"));
        assertTrue(source.contains("clearStoredSnapshotForIdentity(account, server);"));
        assertTrue(source.contains("private void clearStoredSnapshotForIdentity(String account, String server) {"));
        assertTrue(source.contains("accountStorageRepository.clearRuntimeSnapshot(account.trim(), server.trim());"));
        assertTrue(source.contains("accountStorageRepository.clearTradeHistory(account.trim(), server.trim());"));
    }

    @Test
    public void preloadManagerShouldOwnStrongRefreshPreparationInsteadOfMonitorService() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public void prepareFullSnapshotRefresh() {"));
        assertTrue(source.contains("clearLatestCache();"));
        assertTrue(source.contains("setFullSnapshotActive(true);"));
    }

    @Test
    public void preloadManagerShouldOwnRecoveredCacheIdentityValidationInsteadOfMonitorService() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public Cache fetchFullForUiForIdentity(AccountTimeRange range, String expectedAccount, String expectedServer) {"));
        assertTrue(source.contains("return requireExpectedIdentityCache(fetchFullForUi(range), expectedAccount, expectedServer);"));
        assertTrue(source.contains("private static Cache requireExpectedIdentityCache(Cache cache, String expectedAccount, String expectedServer) {"));
        assertTrue(source.contains("throw new IllegalStateException(\"v2 account full recovered cache missing connected account\")"));
        assertTrue(source.contains("throw new IllegalStateException(\"v2 account full recovered cache identity mismatch\")"));
    }

    @Test
    public void preloadManagerShouldOwnHistoryRefreshQueueGateInsteadOfMonitorService() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private final AccountHistoryRefreshGate accountHistoryRefreshGate = new AccountHistoryRefreshGate();"));
        assertTrue(source.contains("public void queueHistoryRefreshForRevision(String remoteHistoryRevision, Runnable afterRefresh) {"));
        assertTrue(source.contains("AccountHistoryRefreshGate.StartDecision startDecision = accountHistoryRefreshGate.tryStart(safeHistoryRevision);"));
        assertTrue(source.contains("executor.execute(() -> runQueuedHistoryRefresh(startDecision.getRevision(), afterRefresh));"));
        assertTrue(source.contains("AccountHistoryRefreshGate.FinishDecision finishDecision = accountHistoryRefreshGate.finish(historyRevision);"));
        assertTrue(source.contains("if (finishDecision.shouldContinue()) {\n                runQueuedHistoryRefresh(finishDecision.getNextRevision(), afterRefresh);\n            }"));
        assertTrue(source.contains("private void dispatchAfterHistoryRefresh(Runnable afterRefresh) {"));
        assertTrue(source.contains("mainHandler.post(afterRefresh);"));
    }

    @Test
    public void fetchForUiShouldAlwaysHydrateStoredHistoryFromAllRange() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        int fetchFullForUiStart = source.indexOf("public Cache fetchFullForUi(AccountTimeRange range) {");
        int updateLatestCacheIndex = source.indexOf("updateLatestCache(cache);", fetchFullForUiStart);
        String fetchFullForUiBody = source.substring(fetchFullForUiStart, updateLatestCacheIndex);

        assertTrue(fetchFullForUiBody.contains("AccountFullPayload fullPayload = gatewayV2Client.fetchAccountFull();"));
        assertFalse(fetchFullForUiBody.contains("AccountHistoryPayload historyPayload = fetchCompleteHistoryPayload(AccountTimeRange.ALL);"));
    }

    @Test
    public void fetchForUiShouldNotSkipCanonicalHistoryReloadWhenRevisionLooksUnchanged() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        int fetchForUiStart = source.indexOf("public Cache fetchFullForUi(AccountTimeRange range) {");
        int updateLatestCacheIndex = source.indexOf("updateLatestCache(cache);", fetchForUiStart);
        String fetchForUiBody = source.substring(fetchForUiStart, updateLatestCacheIndex);

        assertTrue(fetchForUiBody.contains("AccountFullPayload fullPayload = gatewayV2Client.fetchAccountFull();"));
        assertTrue(fetchForUiBody.contains("AccountStorageRepository.StoredSnapshot mergedSnapshot ="));
        assertTrue(fetchForUiBody.contains("buildStoredSnapshotFromFullPayload(fullPayload);"));
        assertFalse(fetchForUiBody.contains("AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("));
        assertFalse(fetchForUiBody.contains("buildStoredSnapshotFromSnapshotOnly(snapshotPayload)"));
        assertFalse(fetchForUiBody.contains("persistIncrementalSnapshot(incrementalSnapshot)"));
    }

    @Test
    public void preloadManagerShouldDeleteLegacySnapshotOnlyHelpersAfterFullPayloadUnification() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("buildStoredSnapshotFromV2(AccountSnapshotPayload snapshotPayload,"));
        assertFalse(source.contains("buildStoredSnapshotFromSnapshotOnly(AccountSnapshotPayload snapshotPayload)"));
        assertFalse(source.contains("resolveRemoteHistoryRevision(AccountSnapshotPayload snapshotPayload)"));
        assertFalse(source.contains("resolveHistoryRevisionFromPayload(AccountSnapshotPayload snapshotPayload,"));
    }

    @Test
    public void buildCacheShouldReuseStoredConnectedTruthInsteadOfHardcodingTrue() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("return new Cache(\n                storedSnapshot.isConnected(),"));
        assertFalse(source.contains("return new Cache(\n                true,"));
    }

    @Test
    public void historyMergeShouldKeepCurrentPendingOrdersInsteadOfOverwritingWithHistoryOrders() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("baseSnapshot == null ? new ArrayList<>() : baseSnapshot.getPendingOrders()"));
        assertFalse(source.contains("parsePositionItems(historyPayload == null ? null : historyPayload.getOrders(), true)"));
    }

    @Test
    public void preloadManagerShouldGuardSynchronousStorageReadsBehindWorkerThreadAssertion() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private void assertWorkerThreadForStorageAccess(String operation) {"));
        assertTrue(source.contains("if (Looper.myLooper() == Looper.getMainLooper()) {"));
        assertTrue(source.contains("throw new IllegalStateException(\"AccountStatsPreloadManager synchronous storage access must stay off main thread: \" + operation);"));
        assertTrue(source.contains("private AccountStorageRepository.StoredSnapshot loadStoredSnapshotForWorkerThread() {"));
        assertTrue(source.contains("assertWorkerThreadForStorageAccess(\"loadStoredSnapshot\");"));
        assertTrue(source.contains("private int loadStoredTradeCountForWorkerThread() {"));
        assertTrue(source.contains("assertWorkerThreadForStorageAccess(\"loadTrades\");"));
        assertFalse(source.contains("AccountStorageRepository.StoredSnapshot storedSnapshot = accountStorageRepository.loadStoredSnapshot();"));
        assertFalse(source.contains("AccountStorageRepository.StoredSnapshot cachedSnapshot =\n                    accountStorageRepository.loadStoredSnapshot();"));
        assertFalse(source.contains("int storedTradeCount = accountStorageRepository.loadTrades().size();"));
    }

    @Test
    public void preloadManagerShouldUseAtomicLoadingGateForScheduledFetch() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private final AtomicBoolean loading = new AtomicBoolean(false);"));
        assertTrue(source.contains("if (!loading.compareAndSet(false, true)) {\n            return;\n        }"));
        assertTrue(source.contains("loading.set(false);"));
    }
}
