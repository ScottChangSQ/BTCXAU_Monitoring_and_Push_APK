package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartOverlayRestoreSourceTest {

    @Test
    public void restoreShouldNotClearPositionPanelWhileSessionIsStillActive() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String coordinatorSource = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(coordinatorSource.contains("if (!sessionActive) {\n            host.clearAccountAnnotationsOverlay();\n            return;\n        }"));
        assertTrue(coordinatorSource.contains("if (snapshot == null) {\n            if (!host.isCurrentOverlayBoundToActiveSession()) {\n                host.clearAccountAnnotationsOverlay();\n            }\n            return;\n        }"));
        assertFalse(activitySource.contains("if (snapshot == null) {\n            updateChartPositionPanel(new ArrayList<>(), new ArrayList<>(), 0d);\n            return;\n        }"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
