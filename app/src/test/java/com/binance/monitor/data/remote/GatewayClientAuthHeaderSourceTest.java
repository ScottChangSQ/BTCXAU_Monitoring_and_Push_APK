package com.binance.monitor.data.remote;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GatewayClientAuthHeaderSourceTest {

    @Test
    public void gatewayClientsShouldApplyConfiguredAuthHeaders() throws Exception {
        String helperSource = readUtf8(
                "app/src/main/java/com/binance/monitor/util/GatewayAuthRequestHelper.java",
                "src/main/java/com/binance/monitor/util/GatewayAuthRequestHelper.java"
        );
        String v2ClientSource = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java"
        );
        String sessionSource = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java"
        );
        String tradeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java"
        );
        String streamSource = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java",
                "src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java"
        );
        String abnormalSource = readUtf8(
                "app/src/main/java/com/binance/monitor/data/remote/AbnormalGatewayClient.java",
                "src/main/java/com/binance/monitor/data/remote/AbnormalGatewayClient.java"
        );
        String legacySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java",
                "src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java"
        );

        assertTrue(helperSource.contains(".header(HEADER_AUTHORIZATION, \"Bearer \" + authToken)"));
        assertTrue(helperSource.contains(".header(HEADER_GATEWAY_AUTH_TOKEN, authToken)"));
        assertTrue(v2ClientSource.contains("GatewayAuthRequestHelper"));
        assertTrue(sessionSource.contains("GatewayAuthRequestHelper"));
        assertTrue(tradeSource.contains("GatewayAuthRequestHelper"));
        assertTrue(streamSource.contains("GatewayAuthRequestHelper"));
        assertTrue(abnormalSource.contains("GatewayAuthRequestHelper"));
        assertTrue(legacySource.contains("GatewayAuthRequestHelper"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到网关鉴权源码文件");
    }
}
