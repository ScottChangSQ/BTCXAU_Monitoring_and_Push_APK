/*
 * 锁定标准主体在运行时和重点页面中的使用合同，防止图表页继续保留独立旧主体。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class StandardSubjectUsageSourceTest {

    @Test
    public void uiPaletteManagerShouldExposeStandardSubjectEntryPoints() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java");

        assertMatches(source, "\\bstyleActionButton\\s*\\(");
        assertMatches(source, "\\bstyleTextTrigger\\s*\\(");
        assertMatches(source, "\\bstyleSegmentedOption\\s*\\(");
        assertMatches(source, "\\bstyleSelectFieldLabel\\s*\\(");
        assertMatches(source, "\\bstyleInputField\\s*\\(");
        assertMatches(source, "\\bstyleInputField\\s*\\(\\s*@Nullable\\s+EditText");
        assertMatches(source, "\\bstyleToggleChoice\\s*\\(");
    }

    @Test
    public void chartMainlineShouldStopKeepingIndependentInlineButtonSubject() throws Exception {
        String chart = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String tradeDialog = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");

        assertNotMatches(chart, "\\bstyleInlineTextButton\\s*\\(");
        assertNotMatches(tradeDialog, "\\bstyleInlineTextButton\\s*\\(");
    }

    @Test
    public void activePagesShouldUseCanonicalSubjectEntryPoints() throws Exception {
        String statsScreen = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
        String statsBridge = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String marketMonitor = readUtf8("src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java");

        assertMatches(statsScreen, "\\bstyleSegmentedOption\\s*\\(");
        assertMatches(statsBridge, "\\bstyleSegmentedOption\\s*\\(");
        assertNotMatches(statsScreen, "\\bstyleSegmentedButton\\s*\\(");
        assertNotMatches(statsBridge, "\\bstyleSegmentedButton\\s*\\(");

        assertMatches(marketMonitor, "\\bstyleSelectFieldLabel\\s*\\(");
        assertMatches(marketMonitor, "\\bstyleTextTrigger\\s*\\(");
        assertNotMatches(marketMonitor, "\\bstyleSpinnerItemText\\s*\\(");
        assertNotMatches(marketMonitor, "\\bstyleInlineTextButton\\s*\\(");
    }

    @Test
    public void uiPaletteManagerShouldDropLegacySubjectWrapperMethodsAfterMigration() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java");

        assertNotMatches(source, "\\bstyleInlineTextButton\\s*\\(");
        assertNotMatches(source, "\\bstyleSquareTextAction\\s*\\(");
        assertNotMatches(source, "\\bstyleTopControlButton\\s*\\(");
        assertNotMatches(source, "\\bstyleTopControlLabel\\s*\\(");
        assertNotMatches(source, "\\bstyleSegmentedButton\\s*\\(");
        assertNotMatches(source, "\\bstyleSpinnerItemText\\s*\\(");
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static void assertMatches(String text, String regex) {
        assertTrue(Pattern.compile(regex).matcher(text).find());
    }

    private static void assertNotMatches(String text, String regex) {
        assertFalse(Pattern.compile(regex).matcher(text).find());
    }
}
