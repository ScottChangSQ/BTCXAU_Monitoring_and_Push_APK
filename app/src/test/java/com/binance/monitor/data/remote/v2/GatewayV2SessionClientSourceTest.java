package com.binance.monitor.data.remote.v2;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GatewayV2SessionClientSourceTest {

    @Test
    public void sessionClientShouldReserveLongerReadTimeoutForSessionActions() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue("远程会话登录链需要覆盖服务端 120s MT5 登录预算，read timeout 不能继续停留在 60 秒",
                source.contains("private static final long READ_TIMEOUT_SECONDS = 135L;"));
        assertTrue("会话客户端应继续只命中 v2 session 主链接口",
                source.contains("/v2/session/public-key")
                        && source.contains("/v2/session/status")
                        && source.contains("/v2/session/login")
                        && source.contains("/v2/session/switch")
                        && source.contains("/v2/session/logout"));
    }

    @Test
    public void sessionClientShouldSupportTransportResetAfterForegroundResume() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("public synchronized void resetTransport()"));
        assertTrue(source.contains("client = buildClient();"));
        assertTrue(source.contains("private static OkHttpClient buildClient()"));
        assertTrue(source.contains("OkHttpClient previous = client;"));
        assertTrue(source.contains("OkHttpTransportResetHelper.closeClientAsync(previous);"));
        assertTrue(source.contains("json.optString(\"stage\", \"\")"));
        assertTrue(source.contains("json.optLong(\"elapsedMs\", 0L)"));
        assertTrue(source.contains("parseProfile(json.optJSONObject(\"lastObservedAccount\"))"));
    }
}
