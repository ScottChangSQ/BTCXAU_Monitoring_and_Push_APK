package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeSnapshotSourceTest {

    @Test
    public void applySnapshotShouldMergeTradeLifecycleBeforeBuildingStats() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("baseTrades = TradeLifecycleMergeHelper.merge(effectiveTrades, TRADE_PNL_ZERO_THRESHOLD);"));
    }

    @Test
    public void applySnapshotShouldReplaceInMemoryHistoryWhenRemoteSnapshotIsConnected() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("replaceTradeHistory(snapshotTrades);"));
        assertTrue(source.contains("replaceCurveHistory(snapshotCurves);"));
    }
}
