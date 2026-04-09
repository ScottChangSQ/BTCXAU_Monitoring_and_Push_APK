package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FloatingWindowManagerTitleSourceTest {

    @Test
    public void titleViewShouldAllowTwoLinePnlLayout() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("titleView.setSingleLine(false);"));
        assertTrue(source.contains("titleView.setMaxLines(2);"));
    }
}
