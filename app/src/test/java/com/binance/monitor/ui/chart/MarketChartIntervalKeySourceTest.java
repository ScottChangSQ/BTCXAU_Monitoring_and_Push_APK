package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartIntervalKeySourceTest {

    @Test
    public void intervalLookupShouldKeepMonthlyAndMinuteKeysCaseSensitive() throws Exception {
        String activitySource = readUtf8("app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String screenSource = readUtf8("app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String runtimeHelperSource = readUtf8("app/src/main/java/com/binance/monitor/ui/chart/MarketChartRuntimeHelper.java");

        assertTrue(activitySource.contains("key.equals(option.key)"));
        assertTrue(screenSource.contains("key.equals(option.key)"));
        assertTrue(runtimeHelperSource.contains("candidate.equals(supportedKey.trim())"));
        assertFalse(activitySource.contains("key.equalsIgnoreCase(option.key)"));
        assertFalse(screenSource.contains("key.equalsIgnoreCase(option.key)"));
        assertFalse(runtimeHelperSource.contains("candidate.equalsIgnoreCase(supportedKey.trim())"));
    }

    private static String readUtf8(String candidate) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path direct = workingDir.resolve(candidate).normalize();
        if (Files.exists(direct)) {
            return new String(Files.readAllBytes(direct), StandardCharsets.UTF_8);
        }
        String trimmedCandidate = candidate.startsWith("app/") ? candidate.substring(4) : candidate;
        Path sibling = workingDir.resolve(trimmedCandidate).normalize();
        if (Files.exists(sibling)) {
            return new String(Files.readAllBytes(sibling), StandardCharsets.UTF_8);
        }
        Path nested = workingDir.resolve("app").resolve(trimmedCandidate).normalize();
        return new String(Files.readAllBytes(nested), StandardCharsets.UTF_8);
    }
}
