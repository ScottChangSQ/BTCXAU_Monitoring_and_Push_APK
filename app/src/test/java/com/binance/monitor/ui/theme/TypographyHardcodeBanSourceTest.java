/*
 * 禁止布局、Java 和图表绘制继续定义局部字号，所有字号都必须回到 TextAppearance。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class TypographyHardcodeBanSourceTest {

    private static final List<String> XML_FILES = Arrays.asList(
            "src/main/res/layout/activity_account_position.xml",
            "src/main/res/layout/activity_account_stats.xml",
            "src/main/res/layout/activity_main.xml",
            "src/main/res/layout/activity_market_chart.xml",
            "src/main/res/layout/activity_settings_detail.xml",
            "src/main/res/layout/content_account_position.xml",
            "src/main/res/layout/content_account_stats.xml",
            "src/main/res/layout/content_settings.xml",
            "src/main/res/layout/dialog_abnormal_threshold_settings.xml",
            "src/main/res/layout/dialog_indicator_params.xml",
            "src/main/res/layout/dialog_trade_command.xml",
            "src/main/res/layout/layout_floating_window.xml"
    );

    private static final List<String> JAVA_FILES = Arrays.asList(
            "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
            "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
            "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
            "src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java",
            "src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java",
            "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
            "src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java",
            "src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java",
            "src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java",
            "src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java",
            "src/main/java/com/binance/monitor/ui/account/TradeWeekdayBarChartView.java",
            "src/main/java/com/binance/monitor/ui/chart/KlineChartView.java",
            "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
            "src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java",
            "src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java",
            "src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java",
            "src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java",
            "src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java"
    );

    @Test
    public void layoutsShouldNotDefineTextSizeDirectly() throws Exception {
        for (String file : XML_FILES) {
            String text = readUtf8(file);
            assertFalse(file, text.contains("android:textSize=\""));
        }
    }

    @Test
    public void javaShouldNotUseLiteralTextSizeOrDpTextSize() throws Exception {
        Pattern literalSetTextSize = Pattern.compile("setTextSize\\([^\\n]*[0-9]+(?:\\.[0-9]+)?f\\)");
        Pattern dpPaintSize = Pattern.compile("setTextSize\\(dp\\([0-9]+(?:\\.[0-9]+)?f\\)\\)");

        for (String file : JAVA_FILES) {
            String text = readUtf8(file);
            assertFalse(file, literalSetTextSize.matcher(text).find());
            assertFalse(file, dpPaintSize.matcher(text).find());
        }
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
