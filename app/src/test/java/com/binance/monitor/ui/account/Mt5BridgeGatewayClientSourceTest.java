package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Mt5BridgeGatewayClientSourceTest {

    @Test
    public void mt5BridgeGatewayClientShouldNotUseStringProbeHeuristics() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse(source.contains("body.contains("));
        assertFalse(source.contains("fallbackToSnapshot"));
    }
}
