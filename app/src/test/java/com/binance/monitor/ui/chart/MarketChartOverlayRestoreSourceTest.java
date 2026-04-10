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
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("if (!sessionActive) {\n            clearAccountAnnotationsOverlay();\n            return;\n        }"));
        assertTrue(source.contains("// 会话仍有效但缓存尚未回填时，保留当前页面状态，避免首帧先闪成空白。"));
        assertFalse(source.contains("if (snapshot == null) {\n            updateChartPositionPanel(new ArrayList<>(), new ArrayList<>(), 0d);\n            return;\n        }"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
