/*
 * 收益统计“回到本月”快捷入口源码测试，确保日收益月份入口与快捷按钮始终走同一条状态链。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsReturnCurrentMonthSourceTest {

    @Test
    public void returnStatsShouldExposeCurrentMonthShortcutInSharedLayoutAndBothHosts() throws Exception {
        String layout = readUtf8("src/main/res/layout/content_account_stats.xml");
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
        String bridgeSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String helperSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java");

        assertTrue(layout.contains("@+id/btnReturnCurrentMonth"));
        assertTrue(layout.contains("android:text=\"回到本月\""));

        assertTrue(screenSource.contains("binding.btnReturnCurrentMonth.setOnClickListener(v -> resetReturnStatsToCurrentMonth());"));
        assertTrue(screenSource.contains("private void resetReturnStatsToCurrentMonth() {"));
        assertTrue(screenSource.contains("returnStatsAnchorDateMs = resolveCurrentMonthAnchorDateMs();"));
        assertTrue(screenSource.contains("showReturnPeriodPickerPanel();"));
        assertTrue(screenSource.contains("binding.btnReturnCurrentMonth.setEnabled(returnStatsMode == ReturnStatsMode.DAY);"));
        assertTrue(screenSource.contains("binding.btnReturnCurrentMonth.setAlpha(returnStatsMode == ReturnStatsMode.DAY ? 1f : 0.65f);"));
        assertTrue(screenSource.contains("UiPaletteManager.styleTextTrigger(\n                binding.btnReturnCurrentMonth,"));
        assertTrue(screenSource.contains("UiPaletteManager.styleSelectFieldLabel(\n                binding.tvReturnsPeriod,"));
        assertTrue(screenSource.contains("UiPaletteManager.styleActionButton("));

        assertTrue(bridgeSource.contains("binding.btnReturnCurrentMonth.setOnClickListener(v -> resetReturnStatsToCurrentMonth());"));
        assertTrue(bridgeSource.contains("private void resetReturnStatsToCurrentMonth() {"));
        assertTrue(bridgeSource.contains("returnStatsAnchorDateMs = resolveCurrentMonthAnchorDateMs();"));
        assertTrue(bridgeSource.contains("showReturnPeriodPickerPanel();"));
        assertTrue(bridgeSource.contains("binding.btnReturnCurrentMonth.setEnabled(returnStatsMode == ReturnStatsMode.DAY);"));
        assertTrue(bridgeSource.contains("binding.btnReturnCurrentMonth.setAlpha(returnStatsMode == ReturnStatsMode.DAY ? 1f : 0.65f);"));
        assertTrue(bridgeSource.contains("UiPaletteManager.styleTextTrigger(\n                binding.btnReturnCurrentMonth,"));
        assertTrue(bridgeSource.contains("UiPaletteManager.styleSelectFieldLabel(\n                binding.tvReturnsPeriod,"));
        assertTrue(bridgeSource.contains("UiPaletteManager.styleActionButton("));

        assertTrue(helperSource.contains("private void setReturnPeriodControlsVisible(boolean visible) {"));
        assertTrue(helperSource.contains("binding.btnReturnCurrentMonth.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);"));
        assertTrue(helperSource.contains("binding.btnReturnCurrentMonth.setClickable(visible);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
