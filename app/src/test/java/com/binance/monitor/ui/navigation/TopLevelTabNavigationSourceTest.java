package com.binance.monitor.ui.navigation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TopLevelTabNavigationSourceTest {

    @Test
    public void topLevelTabsShouldClearCurrentTaskAboveTargetInsteadOfReorderingHiddenPages() throws Exception {
        assertNavigationUsesClearTopOrHostBridge(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "MainActivity",
                "HostTab.MARKET_MONITOR"
        );
        assertNavigationUsesClearTopOrHostBridge(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "MarketChartActivity",
                "HostTab.MARKET_MONITOR"
        );
        assertNavigationUsesClearTopOrHostBridge(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "AccountStatsBridgeActivity",
                "HostTab.MARKET_MONITOR"
        );
        assertNavigationUsesClearTopOrHostBridge(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java",
                "SettingsActivity",
                "HostTab.SETTINGS"
        );
        assertNavigationUsesClearTopOrHostBridge(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "SettingsSectionActivity",
                "HostTab.MARKET_MONITOR"
        );
    }

    private static void assertNavigationUsesClearTop(String candidateA,
                                                     String candidateB,
                                                     String label) throws Exception {
        String source = readUtf8(candidateA, candidateB)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(label + " 的顶层 Tab 跳转应使用 CLEAR_TOP + SINGLE_TOP，避免把旧任务里的隐藏页面重新拉到前台",
                source.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP")
                        || source.contains("android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP"));
        assertFalse(label + " 的顶层 Tab 跳转不应继续使用 REORDER_TO_FRONT，否则会把旧页面重排到前台造成像重启一样的卡顿",
                source.contains("Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP")
                        || source.contains("android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP"));
    }

    private static void assertNavigationUsesClearTopOrHostBridge(String candidateA,
                                                                 String candidateB,
                                                                 String label,
                                                                 String expectedHostTab) throws Exception {
        String source = readUtf8(candidateA, candidateB)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        boolean usesLegacyClearTop = source.contains("Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP")
                || source.contains("android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP");
        boolean usesHostBridge = source.contains("HostNavigationIntentFactory.forTab(this, " + expectedHostTab + ")");

        assertTrue(label + " 的顶层导航要么继续显式使用 CLEAR_TOP + SINGLE_TOP，要么已经收口到主壳对应 Tab",
                usesLegacyClearTop || usesHostBridge);
        assertFalse(label + " 的顶层 Tab 跳转不应继续使用 REORDER_TO_FRONT，否则会把旧页面重排到前台造成像重启一样的卡顿",
                source.contains("Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP")
                        || source.contains("android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到导航源码");
    }
}
