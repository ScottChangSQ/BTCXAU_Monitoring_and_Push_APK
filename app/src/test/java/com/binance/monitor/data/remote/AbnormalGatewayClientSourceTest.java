package com.binance.monitor.data.remote;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AbnormalGatewayClientSourceTest {

    @Test
    public void abnormalGatewayClientShouldReleasePreviousTransportWhenResetting() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/remote/AbnormalGatewayClient.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("public synchronized void resetTransport()"));
        assertTrue(source.contains("OkHttpClient previous = client;"));
        assertTrue(source.contains("closeClient(previous);"));
        assertTrue(source.contains("previous.connectionPool().evictAll();"));
    }
}
