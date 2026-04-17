/*
 * 统一按钮样式资源测试，确保全局控件回到方角、无边框、黑底弹窗的终端风格。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SquareButtonStyleResourceTest {

    @Test
    public void stylesShouldDefineSquareButtonStylesWithCenteredTextContract() throws Exception {
        String styles = readUtf8(
                "app/src/main/res/values/styles.xml",
                "src/main/res/values/styles.xml"
        );
        String themes = readUtf8(
                "app/src/main/res/values/themes.xml",
                "src/main/res/values/themes.xml"
        );

        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.Button.PrimarySquare\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.Button.SecondarySquare\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.Button.SecondaryCompactSquare\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.Button.SecondaryContentSquare\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.Button.SecondaryCompactContentSquare\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.TextAction.Compact\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.Spinner.Label\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.Spinner.DropdownItem\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.Spinner.AnchorItem\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Control\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.ControlCompact\""));
        assertTrue(styles.contains("<item name=\"cornerRadius\">0dp</item>"));
        assertTrue(styles.contains("<item name=\"android:includeFontPadding\">false</item>"));
        assertTrue(styles.contains("<item name=\"android:gravity\">center</item>"));
        assertTrue(styles.contains("<item name=\"android:minWidth\">0dp</item>"));
        assertTrue(styles.contains("<item name=\"android:paddingStart\">@dimen/space_2</item>"));
        assertTrue(styles.contains("<item name=\"android:paddingEnd\">@dimen/space_2</item>"));
        assertTrue(styles.contains("<item name=\"android:paddingTop\">0dp</item>"));
        assertTrue(styles.contains("<item name=\"android:paddingBottom\">0dp</item>"));
        assertTrue(styles.contains("<item name=\"strokeWidth\">0dp</item>"));
        assertTrue(styles.contains("<item name=\"backgroundTint\">@color/bg_card</item>"));
        assertTrue(themes.contains("<item name=\"materialButtonStyle\">@style/Widget.BinanceMonitor.Button.PrimarySquare</item>"));
        assertTrue(themes.contains("<item name=\"materialButtonOutlinedStyle\">@style/Widget.BinanceMonitor.Button.SecondarySquare</item>"));
        assertTrue(themes.contains("<item name=\"cardCornerRadius\">@dimen/radius_lg</item>"));
    }

    @Test
    public void analysisAndStatusLayoutsShouldUseSquareActionButtons() throws Exception {
        String contentAccountStats = readUtf8(
                "app/src/main/res/layout/content_account_stats.xml",
                "src/main/res/layout/content_account_stats.xml"
        );
        String activityAccountStats = readUtf8(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        );
        String dialogStatus = readUtf8(
                "app/src/main/res/layout/dialog_global_status_sheet.xml",
                "src/main/res/layout/dialog_global_status_sheet.xml"
        );
        String chartLayout = readUtf8(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );

        assertTrue(contentAccountStats.contains("@+id/cardStructureAnalysisSection"));
        assertTrue(contentAccountStats.contains("@+id/cardTradeAnalysisEntrySection"));
        assertTrue(activityAccountStats.contains("@+id/cardStructureAnalysisSection"));
        assertTrue(activityAccountStats.contains("@+id/cardTradeAnalysisEntrySection"));

        assertTrue(dialogStatus.contains("<com.google.android.material.button.MaterialButton"));
        assertTrue(dialogStatus.contains("android:id=\"@+id/btnGlobalStatusSettings\""));
        assertTrue(dialogStatus.contains("style=\"@style/Widget.BinanceMonitor.Button.SecondaryCompactContentSquare\""));
        assertTrue(dialogStatus.contains("android:gravity=\"center_vertical\""));
        assertTrue(dialogStatus.contains("android:layout_height=\"@dimen/control_height_sm\""));
        assertTrue(dialogStatus.contains("android:layout_width=\"0dp\""));
        assertTrue(dialogStatus.contains("android:layout_weight=\"1\""));
        assertTrue(dialogStatus.contains("android:id=\"@+id/btnGlobalStatusLogs\""));
        assertTrue(dialogStatus.contains("android:id=\"@+id/btnGlobalStatusAbnormal\""));

        assertTrue(chartLayout.contains("<com.google.android.material.button.MaterialButton"));
        assertTrue(chartLayout.contains("android:id=\"@+id/btnGlobalStatus\""));
        assertTrue(chartLayout.contains("style=\"@style/Widget.BinanceMonitor.Button.SecondaryContentSquare\""));
        assertTrue(chartLayout.contains("android:id=\"@+id/tvChartSymbolPickerLabel\""));
        assertTrue(chartLayout.contains("style=\"@style/Widget.BinanceMonitor.Spinner.Label\""));
        assertTrue(chartLayout.contains("android:id=\"@+id/btnChartModeMarket\""));
        assertTrue(chartLayout.contains("android:id=\"@+id/btnChartModePending\""));
        assertTrue(chartLayout.contains("android:id=\"@+id/btnQuickTradePrimary\""));
        assertTrue(chartLayout.contains("android:id=\"@+id/btnQuickTradeSecondary\""));
        assertTrue(chartLayout.contains("android:layout_width=\"0dp\""));
        assertTrue(chartLayout.contains("android:layout_weight=\"1\""));
        assertTrue(chartLayout.contains("android:layout_height=\"@dimen/control_height_lg\""));
    }

    @Test
    public void listActionsAndSpinnerOptionsShouldUseSharedControlStyles() throws Exception {
        String positionLayout = readUtf8(
                "app/src/main/res/layout/item_position.xml",
                "src/main/res/layout/item_position.xml"
        );
        String spinnerItem = readUtf8(
                "app/src/main/res/layout/item_spinner_filter.xml",
                "src/main/res/layout/item_spinner_filter.xml"
        );
        String spinnerAnchor = readUtf8(
                "app/src/main/res/layout/item_spinner_filter_anchor.xml",
                "src/main/res/layout/item_spinner_filter_anchor.xml"
        );
        String spinnerDropdown = readUtf8(
                "app/src/main/res/layout/item_spinner_filter_dropdown.xml",
                "src/main/res/layout/item_spinner_filter_dropdown.xml"
        );
        String tradeHistorySheet = readUtf8(
                "app/src/main/res/layout/dialog_account_trade_history_sheet.xml",
                "src/main/res/layout/dialog_account_trade_history_sheet.xml"
        );

        assertTrue(positionLayout.contains("style=\"@style/Widget.BinanceMonitor.TextAction.RowCompact\""));
        assertTrue(positionLayout.contains("android:layout_marginStart=\"@dimen/control_group_gap\""));
        assertTrue(positionLayout.contains("android:layout_height=\"@dimen/position_row_action_height\""));
        assertTrue(positionLayout.contains("android:id=\"@+id/layoutSummaryColumn\""));
        assertTrue(positionLayout.contains("android:id=\"@+id/tvSummary\""));
        assertTrue(positionLayout.contains("android:maxLines=\"1\""));
        assertTrue(positionLayout.contains("android:ellipsize=\"end\""));
        assertTrue(positionLayout.contains("android:singleLine=\"true\""));
        assertTrue(positionLayout.contains("android:lines=\"1\""));
        assertTrue(positionLayout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.MetaStrongSingleLine\""));
        assertTrue(positionLayout.contains("android:paddingTop=\"@dimen/position_row_header_padding_vertical\""));
        assertTrue(positionLayout.contains("android:paddingBottom=\"@dimen/position_row_header_padding_vertical\""));
        assertTrue(positionLayout.contains("android:paddingStart=\"@dimen/position_row_header_padding_horizontal\""));
        assertTrue(positionLayout.contains("android:paddingEnd=\"@dimen/position_row_header_padding_horizontal\""));
        assertTrue(positionLayout.contains("android:layout_width=\"wrap_content\""));
        assertTrue(spinnerItem.contains("style=\"@style/Widget.BinanceMonitor.Spinner.DropdownItem\""));
        assertTrue(spinnerAnchor.contains("style=\"@style/Widget.BinanceMonitor.Spinner.AnchorItem\""));
        assertTrue(spinnerAnchor.contains("android:paddingEnd=\"28dp\""));
        assertTrue(spinnerItem.contains("android:layout_height=\"@dimen/control_height_md\""));
        assertTrue(spinnerDropdown.contains("style=\"@style/Widget.BinanceMonitor.Spinner.DropdownItem\""));
        assertTrue(tradeHistorySheet.contains("style=\"@style/Widget.BinanceMonitor.Spinner.Label\""));
        assertTrue(tradeHistorySheet.contains("android:layout_marginStart=\"@dimen/control_group_gap\""));
    }

    @Test
    public void sharedDrawablesShouldKeepSquareBorderlessContract() throws Exception {
        String inlineButton = readUtf8(
                "app/src/main/res/drawable/bg_inline_button.xml",
                "src/main/res/drawable/bg_inline_button.xml"
        );
        String positionRowCollapsed = readUtf8(
                "app/src/main/res/drawable/bg_position_row_collapsed.xml",
                "src/main/res/drawable/bg_position_row_collapsed.xml"
        );
        String positionRowExpanded = readUtf8(
                "app/src/main/res/drawable/bg_position_row_expanded.xml",
                "src/main/res/drawable/bg_position_row_expanded.xml"
        );
        String chartCard = readUtf8(
                "app/src/main/res/drawable/bg_chart_position_card.xml",
                "src/main/res/drawable/bg_chart_position_card.xml"
        );
        String chartRow = readUtf8(
                "app/src/main/res/drawable/bg_chart_position_row.xml",
                "src/main/res/drawable/bg_chart_position_row.xml"
        );
        String positionButton = readUtf8(
                "app/src/main/res/drawable/bg_position_action_button.xml",
                "src/main/res/drawable/bg_position_action_button.xml"
        );
        String positionDangerButton = readUtf8(
                "app/src/main/res/drawable/bg_position_action_button_danger.xml",
                "src/main/res/drawable/bg_position_action_button_danger.xml"
        );
        String symbolSelected = readUtf8(
                "app/src/main/res/drawable/bg_symbol_selected.xml",
                "src/main/res/drawable/bg_symbol_selected.xml"
        );
        String symbolUnselected = readUtf8(
                "app/src/main/res/drawable/bg_symbol_unselected.xml",
                "src/main/res/drawable/bg_symbol_unselected.xml"
        );

        assertTrue(inlineButton.contains("<corners android:radius=\"0dp\" />"));
        assertTrue(positionRowCollapsed.contains("<corners android:radius=\"0dp\" />"));
        assertTrue(positionRowExpanded.contains("<corners android:radius=\"0dp\" />"));
        assertTrue(chartCard.contains("<corners android:radius=\"0dp\" />"));
        assertTrue(chartRow.contains("<corners android:radius=\"0dp\" />"));
        assertTrue(positionButton.contains("<corners android:radius=\"0dp\" />"));
        assertTrue(positionDangerButton.contains("<corners android:radius=\"0dp\" />"));
        assertTrue(symbolSelected.contains("<corners android:radius=\"0dp\" />"));
        assertTrue(symbolUnselected.contains("<corners android:radius=\"0dp\" />"));

        assertTrue(inlineButton.contains("android:width=\"0dp\""));
        assertTrue(positionRowCollapsed.contains("android:width=\"0dp\""));
        assertTrue(positionRowExpanded.contains("android:width=\"0dp\""));
        assertTrue(chartCard.contains("android:width=\"0dp\""));
        assertTrue(chartRow.contains("android:width=\"0dp\""));
        assertTrue(positionButton.contains("android:width=\"0dp\""));
        assertTrue(positionDangerButton.contains("android:width=\"0dp\""));
        assertTrue(symbolSelected.contains("android:width=\"0dp\""));
        assertTrue(symbolUnselected.contains("android:width=\"0dp\""));
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
        throw new IllegalStateException("未找到资源文件");
    }
}
