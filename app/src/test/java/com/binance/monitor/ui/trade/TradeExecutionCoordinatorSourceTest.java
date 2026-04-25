package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TradeExecutionCoordinatorSourceTest {

    @Test
    public void tradeExecutionCoordinatorShouldNotClassifyActionsByContains() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("action.contains("));
    }
}
