package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GlobalStatusBottomSheetSourceTest {

    @Test
    public void marketChartShouldExposeStatusButtonAndBottomSheetController() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_market_chart.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String controller = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/btnGlobalStatus"));
        assertTrue(layout.contains("@string/global_status_button_offline"));
        assertTrue(controller.contains("BottomSheetDialog"));
        assertTrue(controller.contains("dialog_global_status_sheet"));
    }
}
