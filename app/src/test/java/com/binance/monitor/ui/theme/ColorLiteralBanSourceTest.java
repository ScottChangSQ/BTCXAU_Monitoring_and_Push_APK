/*
 * 禁止运行时主题层继续保留十六进制颜色字面量，确保颜色真值只来自资源 token。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ColorLiteralBanSourceTest {

    @Test
    public void uiPaletteManagerShouldNotKeepRawHexColors() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java",
                "src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java"
        );

        assertFalse(source.contains("Color.parseColor("));
        assertFalse(source.matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
        assertTrue(source.contains("R.color.bg_app_base"));
        assertTrue(source.contains("R.color.accent_primary"));
        assertTrue(source.contains("R.color.trade_buy"));
        assertTrue(source.contains("R.color.pnl_profit"));
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
        throw new IllegalStateException("未找到源码文件");
    }
}
