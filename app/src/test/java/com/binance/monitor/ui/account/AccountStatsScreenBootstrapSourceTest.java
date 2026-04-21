package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsScreenBootstrapSourceTest {

    @Test
    public void accountStatsScreenShouldTrackBootstrapStateForAnalysisFirstFrame() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private PageBootstrapStateMachine accountBootstrapStateMachine = new PageBootstrapStateMachine();"));
        assertTrue(source.contains("private PageBootstrapSnapshot accountBootstrapSnapshot = PageBootstrapSnapshot.initial();"));
        assertTrue(source.contains("applyBootstrapState(accountBootstrapStateMachine.onMemoryDataReady("));
        assertTrue(source.contains("applyBootstrapState(accountBootstrapStateMachine.onStorageRestoreStarted());"));
    }

    @Test
    public void analysisPlaceholderShouldBeDrivenByBootstrapState() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("getString(R.string.analysis_restore_loading_text)"));
        assertTrue(source.contains("getString(R.string.analysis_restore_syncing_text)"));
        assertTrue(source.contains("PageBootstrapState state = accountBootstrapSnapshot == null"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到分析页源码");
    }
}
