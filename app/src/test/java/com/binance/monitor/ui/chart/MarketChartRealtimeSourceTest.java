package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartRealtimeSourceTest {

    @Test
    public void activityShouldObserveRealtimeDisplayKlinesForChartTail() throws Exception {
        Path activityFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        Path coordinatorFile = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java");
        String activitySource = new String(Files.readAllBytes(activityFile), StandardCharsets.UTF_8);
        String coordinatorSource = new String(Files.readAllBytes(coordinatorFile), StandardCharsets.UTF_8);

        assertTrue(activitySource.contains("dataCoordinator.observeRealtimeDisplayKlines();"));
        assertTrue(coordinatorSource.contains("repository.getDisplayKlines().observe(host.getLifecycleOwner(), snapshot -> {"));
        assertFalse(activitySource.contains("private boolean hasRealtimeTailSourceForChart() {\r\n        return false;\r\n    }"));
    }

    @Test
    public void activeCrosshairShouldPauseFollowLatestDuringRefresh() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("private boolean shouldFollowLatestViewportOnRefresh()"));
        assertTrue(source.contains("binding.klineChartView.isFollowingLatestViewport()"));
        assertTrue(source.contains("!binding.klineChartView.hasActiveCrosshair()"));
        assertTrue(source.contains("shouldFollowLatestViewportOnRefresh();"));
    }
}
