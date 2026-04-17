/*
 * 账户页和分析图表源码约束测试，确保 Notion Data Desk 主题真正接入账户/分析主路径。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountAnalysisThemeSourceTest {

    @Test
    public void tradeChartsShouldNotUseLegacyFixedResourceColors() throws Exception {
        String weekdayChart = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/TradeWeekdayBarChartView.java",
                "src/main/java/com/binance/monitor/ui/account/TradeWeekdayBarChartView.java"
        );
        String pnlChart = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java",
                "src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java"
        );

        assertTrue("星期盈亏图应提供 refreshPalette 入口", weekdayChart.contains("public void refreshPalette()"));
        assertTrue("品种盈亏图应提供 refreshPalette 入口", pnlChart.contains("public void refreshPalette()"));
        assertTrue("星期盈亏图应从 UiPaletteManager 读取主题", weekdayChart.contains("UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());"));
        assertTrue("品种盈亏图应从 UiPaletteManager 读取主题", pnlChart.contains("UiPaletteManager.Palette palette = UiPaletteManager.resolve(getContext());"));
        assertFalse("星期盈亏图不能再直接使用旧 accent_green",
                weekdayChart.contains("R.color.accent_green"));
        assertFalse("星期盈亏图不能再直接使用旧 accent_red",
                weekdayChart.contains("R.color.accent_red"));
        assertFalse("品种盈亏图不能再直接使用旧 accent_green",
                pnlChart.contains("R.color.accent_green"));
        assertFalse("品种盈亏图不能再直接使用旧 accent_red",
                pnlChart.contains("R.color.accent_red"));
    }

    @Test
    public void accountStatsScreenShouldUsePaletteForCardsSummariesAndLoginBanner() throws Exception {
        String screen = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        );

        assertTrue("账户页应刷新两张分析柱状图 palette",
                screen.contains("binding.tradePnlBarChart.refreshPalette();"));
        assertTrue("账户页应刷新星期统计图 palette",
                screen.contains("binding.tradeWeekdayBarChart.refreshPalette();"));
        assertTrue("账户卡片应走当前主题圆角和边框，而不是强制方角无描边",
                screen.contains("UiPaletteManager.radiusLgPx(this, palette)"));
        assertTrue("账户卡片应恢复主题描边",
                screen.contains("cardView.setStrokeWidth(UiPaletteManager.strokeWidthPx"));
        assertTrue("登录成功提示文字应走主题选中文字色",
                screen.contains("binding.tvLoginSuccessBanner.setTextColor(UiPaletteManager.controlSelectedText(this));"));
        assertTrue("交易统计摘要应走主题主文字色",
                screen.contains("binding.tvTradeSummaryCountValue.setTextColor(palette.textPrimary);"));
        assertTrue("数字正负色应来自当前 palette",
                screen.contains("return palette.rise;"));
        assertTrue("数字负色应来自当前 palette",
                screen.contains("return palette.fall;"));
        assertFalse("账户页不应再把卡片统一压回 0 圆角",
                screen.contains("cardView.setRadius(0f);"));
        assertFalse("账户页不应再把卡片描边清零",
                screen.contains("cardView.setStrokeWidth(0);"));
        assertFalse("登录成功提示不应再固定白字",
                screen.contains("binding.tvLoginSuccessBanner.setTextColor(ContextCompat.getColor(this, R.color.white));"));
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
        throw new IllegalStateException("找不到源码文件");
    }
}
