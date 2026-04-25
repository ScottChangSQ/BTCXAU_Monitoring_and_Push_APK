/*
 * 锁定分析页销毁链会显式解绑前台刷新监听，避免页面销毁后继续挂着 preload listener。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsPageRuntimeSourceTest {

    @Test
    public void pageRuntimeShouldDetachForegroundRefreshWhenPageDestroyed() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java");
        int onPageDestroyedStart = source.indexOf("public void onPageDestroyed() {");
        int attachForegroundRefreshStart = source.indexOf("public void attachForegroundRefresh() {");

        assertTrue(source.contains("public void onPageDestroyed() {"));
        assertTrue(onPageDestroyedStart >= 0);
        assertTrue(attachForegroundRefreshStart > onPageDestroyedStart);
        String onPageDestroyedBody = source.substring(onPageDestroyedStart, attachForegroundRefreshStart);
        assertTrue(onPageDestroyedBody.contains("host.detachForegroundRefresh();"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
