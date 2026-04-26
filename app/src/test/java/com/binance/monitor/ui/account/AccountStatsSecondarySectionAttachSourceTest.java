package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 锁定分析页次屏模块挂载口径：保留交易统计，移除分析页交易记录模块。
 */
public class AccountStatsSecondarySectionAttachSourceTest {

    @Test
    public void analysisSecondarySectionsShouldExposeTradeStatsOnlyInScreenAndBridge() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        );
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue(screenSource.contains("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(screenSource.contains("binding.cardTradeRecordsSection.setVisibility(View.GONE);"));
        assertTrue(screenSource.contains("maybeAttachTradeStatsSectionForScroll()"));
        assertTrue(screenSource.contains("tradeStatsSectionAttached"));
        assertTrue(screenSource.contains("renderCoordinator.refreshTradeStats();"));

        assertTrue(bridgeSource.contains("binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(bridgeSource.contains("binding.cardTradeRecordsSection.setVisibility(View.GONE);"));
        assertTrue(bridgeSource.contains("maybeAttachTradeStatsSectionForScroll()"));
        assertTrue(bridgeSource.contains("tradeStatsSectionAttached"));
        assertTrue(bridgeSource.contains("renderCoordinator.refreshTradeStats();"));
    }

    @Test
    public void curveSecondaryChartsShouldRefreshImmediatelyAfterAttachWithoutFullDeferredRender() throws Exception {
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        );
        String bridgeSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        String coordinatorSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java"
        );

        assertTrue(screenSource.contains("renderCoordinator.refreshCurveProjectionAsync();"));
        assertTrue(bridgeSource.contains("renderCoordinator.refreshCurveProjectionAsync();"));
        assertTrue(coordinatorSource.contains("void refreshCurveProjectionAsync()"));
        assertTrue(coordinatorSource.contains("executeDeferredSecondaryRender(() ->"));
        assertTrue(coordinatorSource.contains("host.applyPreparedCurveProjection(curveProjection);"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到分析页源码");
    }
}
