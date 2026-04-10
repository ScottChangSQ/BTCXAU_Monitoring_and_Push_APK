package com.binance.monitor.ui.launch;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OverlayLaunchBridgeActivitySourceTest {

    @Test
    public void bridgeActivityShouldForwardTargetSymbolToChartAndFinishImmediately() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/launch/OverlayLaunchBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/launch/OverlayLaunchBridgeActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("public static final String EXTRA_TARGET_SYMBOL"));
        assertTrue(source.contains("routeToTargetAndFinish();"));
        assertTrue(source.contains("private void routeToTargetAndFinish()"));
        assertTrue(source.contains("new Intent(this, MarketChartActivity.class)"));
        assertTrue(source.contains("putExtra(MarketChartActivity.EXTRA_TARGET_SYMBOL"));
        assertTrue(source.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP"));
        assertTrue(source.contains("finish();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 OverlayLaunchBridgeActivity.java");
    }
}
