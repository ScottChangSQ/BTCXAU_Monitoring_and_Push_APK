package com.binance.monitor.data.remote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BinanceApiClientChartSourceTest {

    @Test
    public void chartChainShouldUseStrictRestOnly() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java",
                "src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java"
        )
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("return requestChartRestKlines(symbol, normalizedInterval, safeLimit, null);"));
        assertTrue(source.contains("return requestChartRestKlines(symbol, normalizedInterval, safeLimit, endTimeInclusive);"));
        assertFalse(source.contains("ChartKlineFallbackLoader"));
        assertFalse(source.contains("ChartLongIntervalFetchPolicyHelper"));
        assertFalse(source.contains("loadChartLongIntervalWindow("));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 BinanceApiClient.java");
    }
}
