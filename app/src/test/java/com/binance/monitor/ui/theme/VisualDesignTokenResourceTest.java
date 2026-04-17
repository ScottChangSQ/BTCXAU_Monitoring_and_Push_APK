/*
 * 统一视觉 token 资源测试，锁定这轮极简终端风格：方角、5dp 模块外边距、黑色 tab/弹窗、深灰模块、无边框。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VisualDesignTokenResourceTest {

    @Test
    public void dimensShouldExposeTerminalSpacingCornerAndControlScale() throws Exception {
        String dimens = readUtf8(
                "app/src/main/res/values/dimens.xml",
                "src/main/res/values/dimens.xml"
        );

        assertTrue(dimens.contains("<dimen name=\"page_horizontal_padding\">5dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"page_section_gap\">5dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"card_content_padding\">2dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"control_group_gap\">2dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"radius_sm\">0dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"radius_md\">0dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"radius_lg\">0dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"control_height_sm\">40dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"control_height_md\">44dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"control_height_lg\">48dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"position_row_header_padding_horizontal\">12dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"position_row_header_padding_vertical\">8dp</dimen>"));
        assertTrue(dimens.contains("<dimen name=\"position_row_action_height\">32dp</dimen>"));
    }

    @Test
    public void stylesAndThemesShouldExposeSquareBorderlessTerminalContract() throws Exception {
        String styles = readUtf8(
                "app/src/main/res/values/styles.xml",
                "src/main/res/values/styles.xml"
        );
        String themes = readUtf8(
                "app/src/main/res/values/themes.xml",
                "src/main/res/values/themes.xml"
        );
        String colors = readUtf8(
                "app/src/main/res/values/colors.xml",
                "src/main/res/values/colors.xml"
        );
        String paletteManager = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java",
                "src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java"
        );

        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Control\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.MetaStrongSingleLine\""));
        assertTrue(styles.contains("<style name=\"Widget.BinanceMonitor.TextAction.RowCompact\""));
        assertTrue(styles.contains("<item name=\"android:textSize\">15sp</item>"));
        assertTrue(styles.contains("<item name=\"android:textSize\">13sp</item>"));
        assertTrue(styles.contains("<item name=\"android:textSize\">12sp</item>"));
        assertTrue(styles.contains("<item name=\"android:lineHeight\">14sp</item>"));
        assertTrue(styles.contains("<item name=\"cornerRadius\">0dp</item>"));
        assertTrue(styles.contains("<item name=\"strokeWidth\">0dp</item>"));
        assertTrue(styles.contains("<item name=\"backgroundTint\">@color/bg_input</item>"));
        assertTrue(themes.contains("<item name=\"cardCornerRadius\">@dimen/radius_lg</item>"));
        assertTrue(themes.contains("<item name=\"strokeWidth\">1dp</item>"));
        assertTrue(themes.contains("<item name=\"strokeColor\">@color/stroke_card</item>"));
        assertTrue(themes.contains("<item name=\"boxStrokeColor\">@android:color/transparent</item>"));
        assertTrue(themes.contains("<item name=\"backgroundTint\">@color/bg_card</item>"));
        assertTrue(themes.contains("<item name=\"colorSurface\">@color/bg_card</item>"));
        assertTrue(colors.contains("<color name=\"bg_primary\">#06080B</color>"));
        assertTrue(colors.contains("<color name=\"bg_surface\">#0A0E12</color>"));
        assertTrue(colors.contains("<color name=\"bg_card\">#12181F</color>"));
        assertTrue(colors.contains("<color name=\"bg_input\">#161D25</color>"));
        assertTrue(colors.contains("<color name=\"stroke_card\">#27313A</color>"));
        assertTrue(colors.contains("<color name=\"text_control_selected\">#FFFFFF</color>"));
        assertTrue(colors.contains("<color name=\"text_control_unselected\">#93A0AA</color>"));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.BodyCompact\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Micro\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.MicroStrong\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Tiny\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.TinyStrong\""));
        assertTrue(paletteManager.contains("public static GradientDrawable createSurfaceDrawable("));
        assertTrue(paletteManager.contains("R.dimen.control_group_gap"));
        assertTrue(paletteManager.contains("R.color.text_control_selected"));
        assertTrue(paletteManager.contains("R.color.text_control_unselected"));
        assertTrue(paletteManager.contains("window.setBackgroundDrawable(createSurfaceDrawable("));
        assertTrue(paletteManager.contains("bottomSheet.setBackground(createSurfaceDrawable("));
        assertTrue(paletteManager.contains("card.setStrokeWidth(dp(context, 1));"));
        assertFalse(styles.contains("@dimen/space_14"));
    }

    @Test
    public void marketChartToolbarShouldUseCompactSquareTerminalContract() throws Exception {
        String layout = readUtf8(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );

        assertTrue(layout.contains("android:id=\"@+id/cardSymbolPanel\""));
        assertTrue(layout.contains("android:paddingStart=\"@dimen/page_horizontal_padding\""));
        assertTrue(layout.contains("android:paddingEnd=\"@dimen/page_horizontal_padding\""));
        assertTrue(layout.contains("android:paddingTop=\"@dimen/space_2\""));
        assertTrue(layout.contains("android:paddingBottom=\"@dimen/space_2\""));
        assertTrue(layout.contains("android:id=\"@+id/tvChartSymbolPickerLabel\""));
        assertTrue(layout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.Control\""));
        assertTrue(layout.contains("android:paddingStart=\"@dimen/space_2\""));
        assertTrue(layout.contains("android:layout_height=\"@dimen/control_height_lg\""));
        assertTrue(layout.contains("android:id=\"@+id/btnChartModeMarket\""));
        assertTrue(layout.contains("android:id=\"@+id/btnChartModePending\""));
        assertTrue(layout.contains("android:id=\"@+id/btnGlobalStatus\""));
        assertTrue(layout.contains("android:layout_marginEnd=\"@dimen/control_group_gap\""));
        assertTrue(layout.contains("android:layout_marginTop=\"@dimen/page_section_gap\""));
        assertTrue(layout.contains("android:id=\"@+id/btnQuickTradePrimary\""));
        assertTrue(layout.contains("android:id=\"@+id/btnQuickTradeSecondary\""));
        assertTrue(layout.contains("style=\"@style/Widget.BinanceMonitor.Button.SecondaryContentSquare\""));
        assertTrue(layout.contains("android:hint=\"@string/chart_quick_trade_default_volume\""));
        assertFalse(layout.contains("<Button\n                        android:id=\"@+id/btnQuickTradePrimary\""));
        assertFalse(layout.contains("<Button\n                        android:id=\"@+id/btnQuickTradeSecondary\""));
        assertFalse(layout.contains("@dimen/space_14"));
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
