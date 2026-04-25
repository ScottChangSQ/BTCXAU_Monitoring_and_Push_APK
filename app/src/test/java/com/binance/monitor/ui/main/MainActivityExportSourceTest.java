package com.binance.monitor.ui.main;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainActivityExportSourceTest {

    @Test
    public void mainActivityShouldStayAsThinBridgeOnlyUntilManifestIsConverged() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/main/MainActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("startActivity("));
        assertTrue(source.contains("finish();"));
    }
}
