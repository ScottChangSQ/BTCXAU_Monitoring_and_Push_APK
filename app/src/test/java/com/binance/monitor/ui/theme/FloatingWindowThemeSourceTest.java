/*
 * 悬浮窗主题源码测试，锁定展开态背景使用更可见但仍克制的近色细描边。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FloatingWindowThemeSourceTest {

    @Test
    public void createFloatingBackgroundShouldUseVisibleOutlineStillCloseToSurfaceColor() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(source.contains("public static GradientDrawable createFloatingBackground(Context context, Palette palette) {"));
        assertTrue("悬浮窗展开态背景应保持近背景色，但描边可见度要比当前更高",
                source.contains("drawable.setStroke(floatingStrokeWidthPx(context), ColorUtils.blendARGB(palette.surfaceEnd, palette.stroke, 0.36f));"));
        assertTrue("悬浮窗展开态描边应继续保持细边框，不改成粗边框",
                source.contains("private static int floatingStrokeWidthPx(Context context) {\n        return dp(context, 1);\n    }"));
    }
}
