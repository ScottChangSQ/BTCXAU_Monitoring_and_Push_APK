/*
 * 锁定预加载清理链只清理已解析到的身份分区，避免 identity 缺失时退化成全量清缓存。
 */
package com.binance.monitor.runtime.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsPreloadManagerSourceTest {

    @Test
    public void preloadManagerShouldNotFallbackToBlankIdentityClearWhenResolvedIdentityMissing() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java");

        assertTrue(source.contains("private void clearStoredSnapshotForResolvedIdentity() {"));
        assertFalse(source.contains("clearStoredSnapshotForIdentity(\"\", \"\")"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
