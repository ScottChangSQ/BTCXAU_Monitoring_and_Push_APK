package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FloatingRevisionRefreshPolicySourceTest {

    @Test
    public void floatingCoordinatorShouldGateNonImmediateRefreshByVisibleProductRevisions() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
                "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private final FloatingRevisionRefreshPolicy revisionRefreshPolicy = new FloatingRevisionRefreshPolicy();"));
        assertTrue(source.contains("List<Long> productRevisions = resolveVisibleProductRevisions();"));
        assertTrue(source.contains("List<String> marketSignatures = resolveVisibleMarketSignatures();"));
        assertTrue(source.contains("if (!immediate && !revisionRefreshPolicy.shouldRefresh(productRevisions, marketSignatures)) {"));
        assertTrue(source.contains("revisionRefreshPolicy.markApplied(work.productRevisions, work.marketSignatures);"));
        assertTrue(source.contains("private List<Long> resolveVisibleProductRevisions() {"));
        assertTrue(source.contains("private List<String> resolveVisibleMarketSignatures() {"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到悬浮窗协调器源码");
    }
}
