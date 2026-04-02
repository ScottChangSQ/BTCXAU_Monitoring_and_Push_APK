/*
 * 日志页主题接线测试，确保页面入口已经接入统一主题应用逻辑。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogActivityThemeWiringTest {

    @Test
    public void logActivityShouldApplySharedPageTheme() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/log/LogActivity.java",
                "src/main/java/com/binance/monitor/ui/log/LogActivity.java"
        );
        assertTrue("日志页仍未调用 applyPaletteStyles，应统一接入主题入口",
                source.contains("applyPaletteStyles();"));
        assertTrue("日志页仍未调用 UiPaletteManager.applyPageTheme，应统一切换页面主题",
                source.contains("UiPaletteManager.applyPageTheme("));
        assertTrue("日志页仍未调用 UiPaletteManager.applySystemBars，应同步系统栏主题",
                source.contains("UiPaletteManager.applySystemBars("));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到日志页源码文件");
    }
}
