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

        assertTrue(source.contains("requireObject(json, \"accountMeta\", \"v2 account snapshot\")"));
        assertTrue(source.contains("requireObject(json, \"account\", \"v2 account snapshot\")"));
        assertTrue(source.contains("requireArray(json, \"positions\", \"v2 account snapshot\")"));
        assertTrue(source.contains("requireArray(json, \"orders\", \"v2 account snapshot\")"));
        assertTrue(source.contains("requireArray(json, \"trades\", \"v2 account history\")"));
        assertTrue(source.contains("requireArray(json, \"curvePoints\", \"v2 account history\")"));
        assertTrue(source.contains("throw new IllegalStateException(context + \" missing \" + key + \" object\")"));
        assertTrue(source.contains("throw new IllegalStateException(context + \" missing \" + key + \" array\")"));
    }

    @Test
    public void gatewayV2ClientShouldSupportTransportResetAfterForegroundResume() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("public synchronized void resetTransport()"));
        assertTrue(source.contains("client = buildClient();"));
        assertTrue(source.contains("private static OkHttpClient buildClient()"));
        assertTrue(source.contains("OkHttpClient previous = client;"));
        assertTrue(source.contains("closeClient(previous);"));
        assertTrue(source.contains("previous.connectionPool().evictAll();"));
    }

    @Test
    public void gatewayV2ClientShouldPreserveServerErrorBodyInHttpException() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("String responseBody = response.body() == null ? \"\" : response.body().string();"));
        assertTrue(source.contains("throw new IOException(\"HTTP \" + response.code() + \" for \" + path + \" \" + responseBody);"));
    }
}
