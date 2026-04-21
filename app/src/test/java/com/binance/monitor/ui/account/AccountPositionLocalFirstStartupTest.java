package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountPositionLocalFirstStartupTest {

    @Test
    public void accountPositionPageShouldUseBootstrapStateBeforeShowingTrueEmpty() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("private PageBootstrapStateMachine accountBootstrapStateMachine = new PageBootstrapStateMachine();"));
        assertTrue(source.contains("private PageBootstrapSnapshot accountBootstrapSnapshot = PageBootstrapSnapshot.initial();"));
        assertTrue(source.contains("applyBootstrapState(accountBootstrapStateMachine.onStorageRestoreStarted());"));
        assertTrue(source.contains("applyBootstrapState(accountBootstrapStateMachine.onStorageMiss());"));
    }

    @Test
    public void accountPositionPageShouldRenderRestoringModelInsteadOfImmediateEmptyModel() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("return AccountPositionUiModel.restoring();"));
        assertTrue(source.contains("private AccountPositionUiModel resolveVisibleUiModel(@Nullable AccountStatsPreloadManager.Cache cache) {"));
        assertTrue(source.contains("AccountPositionUiModel nextModel = resolveVisibleUiModel(cache);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到账户页源码");
    }
}
