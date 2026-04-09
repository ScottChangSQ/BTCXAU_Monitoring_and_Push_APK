package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsTabNavigationSourceTest {

    @Test
    public void settingsHomeTabNavigationShouldNotFinishCurrentActivity() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("设置首页切到其他 tab 时不应主动 finish，避免下次切回被重建",
                source.contains("private void openMarketMonitor() {\n        Intent intent = new Intent(this, MainActivity.class);\n        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        startActivity(intent);\n        overridePendingTransition(0, 0);\n        finish();"));
        assertFalse("设置首页切到账户页时不应主动 finish，避免 tab 切换卡顿",
                source.contains("private void openAccountStats() {\n        Intent intent = new Intent(this, AccountStatsBridgeActivity.class);\n        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        startActivity(intent);\n        overridePendingTransition(0, 0);\n        finish();"));
        assertFalse("设置首页切到图表页时不应主动 finish，避免 tab 切换卡顿",
                source.contains("private void openMarketChart() {\n        Intent intent = new Intent(this, MarketChartActivity.class);\n        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        startActivity(intent);\n        overridePendingTransition(0, 0);\n        finish();"));
    }

    @Test
    public void settingsSectionTabNavigationShouldNotFinishCurrentActivity() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("设置子页切到行情监控时不应主动 finish，避免 tab 切换像重开",
                source.contains("private void openMarketMonitor() {\n        Intent intent = new Intent(this, MainActivity.class);\n        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        startActivity(intent);\n        overridePendingTransition(0, 0);\n        finish();"));
        assertFalse("设置子页切到账户页时不应主动 finish，避免 tab 切换像重开",
                source.contains("private void openAccountStats() {\n        Intent intent = new Intent(this, AccountStatsBridgeActivity.class);\n        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        startActivity(intent);\n        overridePendingTransition(0, 0);\n        finish();"));
        assertFalse("设置子页切到图表页时不应主动 finish，避免 tab 切换像重开",
                source.contains("private void openMarketChart() {\n        Intent intent = new Intent(this, MarketChartActivity.class);\n        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        startActivity(intent);\n        overridePendingTransition(0, 0);\n        finish();"));
        assertFalse("设置子页回设置首页时不应依赖 finish 做 tab 语义切换",
                source.contains("private void openSettingsHome() {\n        Intent intent = new Intent(this, SettingsActivity.class);\n        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);\n        startActivity(intent);\n        overridePendingTransition(0, 0);\n        finish();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 SettingsActivity 源码");
    }
}
