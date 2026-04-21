package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MarketChartFragmentRealHostSourceTest {

    @Test
    public void marketChartFragmentShouldUseSharedScreenHostInsteadOfPlaceholderCallbacks() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("MarketChartScreen"));
        assertFalse(source.contains("当前阶段先保留空实现"));
        assertFalse(source.contains("图表数据主链仍由旧 Activity 承接"));
    }

    @Test
    public void marketChartScreenShouldSubscribeRealtimeDisplayKlines() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("dataCoordinator.observeRealtimeDisplayKlines();"));
    }

    @Test
    public void marketChartDataCoordinatorShouldPromoteBrokenSeriesToSeriesRepairReason() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("RequestReason.SERIES_REPAIR"));
        assertTrue(source.contains("effectiveRequestReason = MarketChartRefreshHelper.RequestReason.SERIES_REPAIR;"));
        assertTrue(source.contains("boolean keepViewportOnApply = autoRefresh\n                                && finalRequestReason != MarketChartRefreshHelper.RequestReason.SERIES_REPAIR;"));
    }
}
