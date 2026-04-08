package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GatewayV2ClientSourceTest {

    @Test
    public void gatewayV2ClientShouldExposeMarketSeriesPagingMethods() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("fetchMarketSeriesBefore("));
        assertTrue(source.contains("fetchMarketSeriesAfter("));
        assertTrue(source.contains("endTime="));
        assertTrue(source.contains("startTime="));
    }

    @Test
    public void gatewayV2ClientShouldRequireCanonicalAccountObjectFromSnapshot() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("v2 account snapshot missing account object"));
        assertTrue(source.contains("JSONObject account = json.optJSONObject(\"account\");"));
        assertTrue(source.contains("if (account == null) {"));
    }
}
