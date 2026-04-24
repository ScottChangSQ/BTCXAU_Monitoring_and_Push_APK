package com.binance.monitor.runtime.market.truth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class HistoryRepairCoordinatorTest {

    @Test
    public void resolveMinuteRepairLimit_shouldCoverOneDayTailInsideGatewayLimit() {
        assertEquals(1445, HistoryRepairCoordinator.resolveMinuteRepairLimit("1d"));
    }

    @Test
    public void resolveMinuteRepairLimit_shouldClampSmallIntervalsToReasonableMinimum() {
        assertEquals(60, HistoryRepairCoordinator.resolveMinuteRepairLimit("5m"));
    }

    @Test
    public void repairRecentMinuteTail_shouldForwardLatestPatchIntoRepositoryRepairWindow() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java",
                "src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("repository.applyRepairedMinuteWindow("));
        assertTrue(source.contains("minutePayload == null ? null : minutePayload.getLatestPatch()"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 HistoryRepairCoordinator.java");
    }
}
