package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FloatingWindowManagerSourceTest {

    @Test
    public void expandedStateShouldUseSmallerSummaryAndCardTextSizes() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("binding.tvOverlayStatus.setTextSize(hasCard ? 12f : 11f);"));
        assertTrue(source.contains("titleView.setTextSize(9f);"));
        assertTrue(source.contains("priceView.setTextSize(11f);"));
    }
}
