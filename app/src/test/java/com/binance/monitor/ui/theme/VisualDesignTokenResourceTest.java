/*
 * 统一视觉 token 资源测试，锁标准主体尺寸 token 和 14 个 canonical 颜色口径。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VisualDesignTokenResourceTest {

    @Test
    public void dimensShouldExposeStandardSubjectTokens() throws Exception {
        String dimens = readUtf8(
                "app/src/main/res/values/dimens.xml",
                "src/main/res/values/dimens.xml"
        );

        assertTrue(dimens.contains("subject_height_md"));
        assertTrue(dimens.contains("subject_height_compact"));
        assertTrue(dimens.contains("subject_height_trigger"));
        assertTrue(dimens.contains("field_padding_x"));
        assertTrue(dimens.contains("field_padding_x_compact"));
        assertTrue(dimens.contains("inline_gap"));
    }

    @Test
    public void colorsShouldMoveTowardCanonicalColorTokenVocabulary() throws Exception {
        String colors = readUtf8(
                "app/src/main/res/values/colors.xml",
                "src/main/res/values/colors.xml"
        );

        assertTrue(colors.contains("bg_app_base"));
        assertTrue(colors.contains("bg_panel_base"));
        assertTrue(colors.contains("bg_card_base"));
        assertTrue(colors.contains("bg_field_base"));
        assertTrue(colors.contains("border_subtle"));
        assertTrue(colors.contains("text_inverse"));
        assertTrue(colors.contains("accent_primary"));
        assertTrue(colors.contains("state_warning"));
        assertTrue(colors.contains("trade_buy"));
        assertTrue(colors.contains("trade_sell"));
        assertTrue(colors.contains("pnl_profit"));
        assertTrue(colors.contains("pnl_loss"));
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
