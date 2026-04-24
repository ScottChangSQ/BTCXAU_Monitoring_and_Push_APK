package com.binance.monitor.service;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MonitorServiceMarketRepairPolicyTest {

    @Test
    public void marketRepairShouldUseGapStateInsteadOfRetryingSameGapBlindly() throws Exception {
        String serviceSource = readUtf8(
                "app/src/main/java/com/binance/monitor/service/MonitorService.java",
                "src/main/java/com/binance/monitor/service/MonitorService.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String repositorySource = readUtf8(
                "app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java",
                "src/main/java/com/binance/monitor/data/repository/MonitorRepository.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("服务层补修策略应先读当前分钟缺口",
                serviceSource.contains("GapDetector.Gap gap = repository.selectMinuteGap(symbol);"));
        assertTrue("同一缺口能否重试必须经过统一状态机判断",
                serviceSource.contains("repository.shouldRetryMinuteGapRepair(symbol, gap, evidenceToken, now);"));
        assertTrue("服务层应显式记录缺口补修进入请求中",
                serviceSource.contains("repository.markMinuteGapRepairAttempted(symbol, gapBefore, evidenceToken, requestedAt);"));
        assertTrue("补修后同一缺口仍存在时，应标记为 still missing，而不是继续无条件重试",
                serviceSource.contains("repository.markMinuteGapRepairStillMissing(symbol, gapBefore, evidenceToken, System.currentTimeMillis());"));
        assertTrue("仓库层必须提供统一的缺口状态存储",
                repositorySource.contains("private final GapRepairStateStore gapRepairStateStore = new GapRepairStateStore();"));
        assertTrue("仓库层必须暴露当前缺口的稳定证据口径",
                repositorySource.contains("public synchronized String buildMinuteGapEvidenceToken(@Nullable String symbol) {"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到市场补修策略源码");
    }
}
