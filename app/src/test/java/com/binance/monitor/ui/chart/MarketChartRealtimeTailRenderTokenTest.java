package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartRealtimeTailRenderTokenTest {

    @Test
    public void realtimeTailRenderTokenShouldIncludeIntraminuteFields() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("latestKline.getHighPrice()"));
        assertTrue(source.contains("latestKline.getLowPrice()"));
        assertTrue(source.contains("latestKline.getVolume()"));
        assertTrue(source.contains("latestKline.getQuoteAssetVolume()"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MarketChartScreen.java");
    }
}
