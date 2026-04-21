/*
 * 锁定主题层和样式层对 canonical color token 的消费边界，防止继续回到旧 accent 命名。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class ColorTokenUsageSourceTest {

    @Test
    public void themesShouldReferenceCanonicalColorTokens() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/values/themes.xml",
                "src/main/res/values/themes.xml"
        );

        assertTrue(xml.contains("@color/accent_primary"));
        assertTrue(xml.contains("@color/bg_card_base"));
        assertTrue(xml.contains("@color/border_subtle"));
        assertTrue(xml.contains("@color/trade_sell"));
        assertTrue(xml.contains("@color/text_inverse"));
        assertFalse(xml.contains("@color/accent_blue"));
        assertFalse(xml.contains("@color/accent_green"));
        assertFalse(xml.contains("@color/accent_red"));
        assertFalse(xml.contains("@color/accent_gold"));
        assertFalse(xml.contains("@color/bg_card\""));
        assertFalse(xml.contains("@color/stroke_card"));
        assertFalse(xml.contains("@color/white"));
    }

    @Test
    public void selectorsShouldUseCanonicalTokens() throws Exception {
        String navItemTint = readUtf8(
                "app/src/main/res/color/nav_item_tint.xml",
                "src/main/res/color/nav_item_tint.xml"
        );
        String inlineButton = readUtf8(
                "app/src/main/res/color/button_text_inline.xml",
                "src/main/res/color/button_text_inline.xml"
        );

        assertTrue(navItemTint.contains("@color/accent_primary"));
        assertFalse(navItemTint.contains("@color/accent_blue"));
        assertTrue(inlineButton.contains("@color/bg_card_base"));
        assertFalse(inlineButton.contains("@color/bg_card\""));
    }

    @Test
    public void appResourcesShouldNotReferenceRemovedLegacyColorTokens() throws Exception {
        String allResources = readAllUtf8Under("app/src/main/res", "src/main/res");

        assertFalse(allResources.contains("@color/accent_cyan"));
        assertFalse(allResources.contains("@color/accent_blue"));
        assertFalse(allResources.contains("@color/accent_gold"));
        assertFalse(allResources.contains("@color/accent_green"));
        assertFalse(allResources.contains("@color/accent_red"));
        assertFalse(allResources.contains("@color/divider"));
        assertFalse(allResources.contains("@color/stroke_card"));
        assertFalse(allResources.contains("@color/text_control_selected"));
        assertFalse(allResources.contains("@color/text_control_unselected"));
        assertFalse(allResources.contains("@color/vintage_orange"));
        assertFalse(allResources.contains("@color/vintage_mustard"));
        assertFalse(allResources.contains("@color/vintage_paper_dark"));
        assertFalse(allResources.contains("@color/vintage_ink"));
        assertFalse(allResources.contains("@color/grain_overlay"));
        assertFalse(allResources.contains("@color/log_level_warn_bg"));
        assertFalse(allResources.contains("@color/log_level_info_bg"));
        assertFalse(allResources.contains("@color/log_level_error_bg"));
        assertFalse(allResources.contains("@color/white"));
        assertFalse(allResources.contains("@color/transparent"));
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

    private static String readAllUtf8Under(String... candidates) throws IOException {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path root = workingDir.resolve(candidate).normalize();
            if (!Files.exists(root)) {
                continue;
            }
            StringBuilder builder = new StringBuilder();
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile).forEach(path -> {
                    try {
                        builder.append(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                                .replace("\r\n", "\n")
                                .replace('\r', '\n'));
                        builder.append('\n');
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            return builder.toString();
        }
        throw new IllegalStateException("未找到资源目录");
    }
}
