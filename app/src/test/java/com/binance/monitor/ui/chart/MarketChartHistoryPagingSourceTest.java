package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartHistoryPagingSourceTest {

    @Test
    public void activityShouldLetChartViewHandleOlderCandleSorting() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        Path coordinatorFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String coordinatorSource = new String(Files.readAllBytes(coordinatorFile), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(coordinatorSource.contains("List<CandleEntry> older = ChartHistoryPagingHelper.resolveOlderCandles(host.getLoadedCandles(), processed);"));
        assertTrue(activitySource.contains("binding.klineChartView.prependCandles(older);"));
        assertFalse(activitySource.contains("Collections.sort(older, (a, b) -> Long.compare(a.getOpenTime(), b.getOpenTime()));"));
    }
}
