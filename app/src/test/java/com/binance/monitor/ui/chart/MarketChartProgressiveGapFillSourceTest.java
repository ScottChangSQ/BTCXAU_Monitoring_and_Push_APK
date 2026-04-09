package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartProgressiveGapFillSourceTest {

    @Test
    public void chartActivityShouldUseCompactRestoreWindow() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");

        assertTrue(source.contains("private static final int RESTORE_WINDOW_LIMIT = 300;"));
        assertTrue(source.contains("private static final int HISTORY_PAGE_LIMIT = 300;"));
        assertFalse(source.contains("private static final int FULL_WINDOW_LIMIT = 1500;"));
    }

    @Test
    public void chartActivityShouldBackfillGapProgressivelyAfterInitialDisplay() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private Future<?> progressiveGapFillTask;"));
        assertTrue(source.contains("startProgressiveGapFill("));
        assertTrue(source.contains("cancelProgressiveGapFillTask();"));
        assertTrue(source.contains("private void applyGapFillBatch("));
        assertTrue(source.contains("applyGapFillBatch(reqSymbol, reqInterval, current, older);"));
        assertFalse(source.contains("result = expandFullHistoryWhenGapDetected("));
        assertFalse(source.contains("mergedYear = expandYearHistoryWhenGapDetected("));
    }

    private static String readUtf8(String candidate) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path path = workingDir.resolve(candidate).normalize();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
