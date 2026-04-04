package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsBridgeOverviewSourceTest {

    @Test
    public void buildOverviewMetricsShouldExposePrepaymentLabelInsteadOfMargin() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("new AccountMetric(\"预付款\""));
    }

    @Test
    public void currentPositionSummaryShouldNotAddStorageFeeToTotalPnl() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(!source.contains("item.getTotalPnL() + item.getStorageFee()"));
    }
}
