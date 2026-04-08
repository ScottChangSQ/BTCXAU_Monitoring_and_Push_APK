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
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("gatewayV2Client.fetchAccountSnapshot()"));
        assertTrue(source.contains("gatewayV2Client.fetchAccountHistory("));
        assertFalse(source.contains("Mt5BridgeGatewayClient"));
        assertFalse(Files.exists(Paths.get("src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java")));
    }

    @Test
    public void preloadManagerShouldConsumeServerMetricsInsteadOfBuildingLocally() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("snapshotPayload.getOverviewMetrics()"));
        assertTrue(source.contains("historyPayload.getCurveIndicators()"));
        assertTrue(source.contains("historyPayload.getStatsMetrics()"));
        assertFalse(source.contains("buildOverviewMetrics("));
        assertFalse(source.contains("buildStatsMetrics("));
    }

    @Test
    public void preloadManagerShouldNotBackfillTradeLifecycleFieldsLocally() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        );

        assertFalse(source.contains("optLongAny(item, timestamp,"));
        assertFalse(source.contains("optDoubleAny(item, price,"));
        assertFalse(source.contains("normalizeEpochMs("));
    }

    @Test
    public void preloadManagerShouldConsumeCanonicalSnapshotAndTradeFieldsOnly() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java")),
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
    public void snapshotOnlyPathShouldNotReloadMergedLocalSnapshotIntoLatestCache() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("accountStorageRepository.loadStoredSnapshot();"));
        assertTrue(source.contains("Cache cache = buildCache(storedSnapshot, remoteTradeCount);"));
    }

    @Test
    public void overlayRefreshShouldUseStoredTradeCountWhenMemoryCacheIsMissing() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("int storedTradeCount = accountStorageRepository.loadTrades().size();"));
        assertTrue(source.contains("int cachedTradeCount = previous == null ? storedTradeCount : previous.getHistoryTradeCount();"));
    }

    @Test
    public void preloadManagerFailureCacheShouldNotReusePreviousSnapshotOrLegacyTradeCountAlias() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("previous.snapshot"));
        assertFalse(source.contains("\"trade_count\""));
        assertTrue(source.contains("new AccountSnapshot("));
        assertTrue(source.contains("optLongAny(accountMeta, 0L, \"tradeCount\")"));
    }
}
