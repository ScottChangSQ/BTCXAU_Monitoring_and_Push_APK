package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GatewayV2TradeClientSourceTest {

    @Test
    public void tradeClientShouldOnlyTargetTradeEndpoints() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("/v2/trade/check"));
        assertTrue(source.contains("/v2/trade/submit"));
        assertTrue(source.contains("/v2/trade/result"));
        assertFalse(source.contains("/v2/market/"));
        assertFalse(source.contains("/v2/account/"));
        assertFalse(source.contains("/v2/sync/"));
    }
}
