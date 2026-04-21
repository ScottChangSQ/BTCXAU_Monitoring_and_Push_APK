package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountPositionHistorySectionSourceTest {

    @Test
    public void accountPositionPageShouldTreatHistoryAsIndependentSection() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("AccountPositionSectionDiff.diff(currentUiModel, nextModel, currentTradeHistory, tradeHistory);"));
        assertTrue(source.contains("if (diff.isHistoryChanged()) {"));
        assertTrue(source.contains("bindHistorySection(tradeHistory);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到账户持仓页源码");
    }
}
