/*
 * 锁定颜色系统的 canonical token 合同，防止资源层继续扩张或回到旧命名体系。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ColorTokenContractResourceTest {

    @Test
    public void colorsXmlShouldExposeFourteenCanonicalTokens() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/values/colors.xml",
                "src/main/res/values/colors.xml"
        );

        assertContainsColor(xml, "bg_app_base");
        assertContainsColor(xml, "bg_panel_base");
        assertContainsColor(xml, "bg_card_base");
        assertContainsColor(xml, "bg_field_base");
        assertContainsColor(xml, "border_subtle");
        assertContainsColor(xml, "text_primary");
        assertContainsColor(xml, "text_secondary");
        assertContainsColor(xml, "text_inverse");
        assertContainsColor(xml, "accent_primary");
        assertContainsColor(xml, "state_warning");
        assertContainsColor(xml, "trade_buy");
        assertContainsColor(xml, "trade_sell");
        assertContainsColor(xml, "pnl_profit");
        assertContainsColor(xml, "pnl_loss");
        assertEquals(14, countCanonicalTokens(xml));
    }

    @Test
    public void colorsXmlShouldNotKeepLegacyColorDefinitions() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/values/colors.xml",
                "src/main/res/values/colors.xml"
        );

        assertLegacyColorMissing(xml, "accent_cyan");
        assertLegacyColorMissing(xml, "accent_blue");
        assertLegacyColorMissing(xml, "accent_gold");
        assertLegacyColorMissing(xml, "accent_green");
        assertLegacyColorMissing(xml, "accent_red");
        assertLegacyColorMissing(xml, "divider");
        assertLegacyColorMissing(xml, "stroke_card");
        assertLegacyColorMissing(xml, "text_control_selected");
        assertLegacyColorMissing(xml, "text_control_unselected");
        assertLegacyColorMissing(xml, "vintage_orange");
        assertLegacyColorMissing(xml, "vintage_mustard");
        assertLegacyColorMissing(xml, "vintage_paper_dark");
        assertLegacyColorMissing(xml, "vintage_ink");
        assertLegacyColorMissing(xml, "grain_overlay");
        assertLegacyColorMissing(xml, "log_level_warn_bg");
        assertLegacyColorMissing(xml, "log_level_info_bg");
        assertLegacyColorMissing(xml, "log_level_error_bg");
        assertLegacyColorMissing(xml, "white");
        assertLegacyColorMissing(xml, "transparent");
    }

    private static void assertContainsColor(String xml, String colorName) {
        assertTrue("missing color token: " + colorName, xml.contains("<color name=\"" + colorName + "\">"));
    }

    private static void assertLegacyColorMissing(String xml, String colorName) {
        assertFalse("legacy color should be removed: " + colorName,
                xml.contains("<color name=\"" + colorName + "\">"));
    }

    private static int countCanonicalTokens(String xml) {
        int count = 0;
        String[] canonicalTokens = new String[] {
                "bg_app_base",
                "bg_panel_base",
                "bg_card_base",
                "bg_field_base",
                "border_subtle",
                "text_primary",
                "text_secondary",
                "text_inverse",
                "accent_primary",
                "state_warning",
                "trade_buy",
                "trade_sell",
                "pnl_profit",
                "pnl_loss"
        };
        for (String token : canonicalTokens) {
            if (xml.contains("<color name=\"" + token + "\">")) {
                count++;
            }
        }
        return count;
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
