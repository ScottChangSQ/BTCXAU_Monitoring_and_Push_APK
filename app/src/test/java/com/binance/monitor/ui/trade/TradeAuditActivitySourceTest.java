package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TradeAuditActivitySourceTest {

    @Test
    public void tradeAuditPageShouldExposeReplayPageAndEntryPoints() throws Exception {
        String activity = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/trade/TradeAuditActivity.java",
                "src/main/java/com/binance/monitor/ui/trade/TradeAuditActivity.java"
        );
        String layout = readUtf8(
                "app/src/main/res/layout/activity_trade_audit.xml",
                "src/main/res/layout/activity_trade_audit.xml"
        );
        String manifest = readUtf8(
                "app/src/main/AndroidManifest.xml",
                "src/main/AndroidManifest.xml"
        );
        String chartCoordinator = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java"
        );
        String globalStatus = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java",
                "src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java"
        );
        String settingsPage = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java"
        );
        String settingsSection = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java"
        );
        String strings = readUtf8(
                "app/src/main/res/values/strings.xml",
                "src/main/res/values/strings.xml"
        );

        assertTrue(activity.contains("TradeAuditViewModel"));
        assertTrue(activity.contains("ClipboardManager"));
        assertTrue(activity.contains("btnTradeAuditLookup"));
        assertTrue(activity.contains("btnTradeAuditCopy"));
        assertTrue(activity.contains("public static void open("));
        assertTrue(layout.contains("@+id/layoutTradeAuditRecentList"));
        assertTrue(layout.contains("@+id/etTradeAuditLookup"));
        assertTrue(layout.contains("@+id/btnTradeAuditLookup"));
        assertTrue(layout.contains("@+id/btnTradeAuditCopy"));
        assertTrue(layout.contains("@+id/tvTradeAuditDetail"));
        assertTrue(manifest.contains("android:name=\".ui.trade.TradeAuditActivity\""));
        assertTrue(chartCoordinator.contains("TradeAuditActivity.open("));
        assertTrue(chartCoordinator.contains("查看追踪"));
        assertTrue(globalStatus.contains("TradeAuditActivity.class"));
        assertTrue(settingsPage.contains("openTradeAuditPage()"));
        assertTrue(settingsSection.contains("TradeAuditActivity.open("));
        assertTrue(strings.contains("trade_audit_title"));
        assertTrue(strings.contains("trade_audit_action_open"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到交易追踪页相关文件");
    }
}
