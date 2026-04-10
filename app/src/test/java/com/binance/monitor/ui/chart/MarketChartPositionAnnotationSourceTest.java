package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartPositionAnnotationSourceTest {

    @Test
    public void positionOverlayShouldPreferPositionOpenTimeFromSnapshotTruth() throws Exception {
        String chartSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String preloadSource = readUtf8("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java");
        String modelSource = readUtf8("src/main/java/com/binance/monitor/ui/account/model/PositionItem.java");

        assertTrue(modelSource.contains("private final long openTime;"));
        assertTrue(modelSource.contains("public long getOpenTime() {"));
        assertTrue(preloadSource.contains("optLong(item, \"openTime\", 0L),"));
        assertTrue(chartSource.contains("long directOpenTime = position.getOpenTime();"));
        assertTrue(chartSource.contains("if (directOpenTime > 0L) {\n            return directOpenTime;\n        }"));
    }

    @Test
    public void annotationDetailLinesShouldReuseResolvedAnchorTime() throws Exception {
        String chartSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(chartSource.contains("\"开仓 \" + formatPositionOpenTime(anchorTime) + \" $\" + FormatUtils.formatPrice(price)"));
        assertTrue(chartSource.contains("\"挂单 \" + formatPositionOpenTime(anchorTime) + \" $\" + FormatUtils.formatPrice(price)"));
        assertTrue(!chartSource.contains("\"开仓 \" + formatPositionOpenTime(item.getOpenTime()) + \" $\" + FormatUtils.formatPrice(price)"));
        assertTrue(!chartSource.contains("\"挂单 \" + formatPositionOpenTime(item.getOpenTime()) + \" $\" + FormatUtils.formatPrice(price)"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
