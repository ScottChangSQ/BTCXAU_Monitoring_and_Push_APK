package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStatsPreloadManagerSourceTest {

    @Test
    public void buildOverviewMetricsShouldIncludeLeverageMetric() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("new AccountMetric(\"杠杆\""));
    }
}
