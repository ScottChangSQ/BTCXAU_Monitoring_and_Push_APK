/*
 * 校验主路径页面开始复用统一文字层级样式，避免字号和通用文字颜色继续散落在布局里。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TypographyResourceUsageTest {

    @Test
    public void keyLayoutsShouldUseSharedTypographyStyles() throws Exception {
        String hostLayout = readUtf8("src/main/res/layout/activity_main_host.xml");
        String positionLayout = readUtf8("src/main/res/layout/activity_account_position.xml");
        String logLayout = readUtf8("src/main/res/layout/activity_log.xml");
        String settingsLayout = readUtf8("src/main/res/layout/activity_settings_detail.xml");

        assertTrue(hostLayout.contains("textAppearance=\"@style/TextAppearance.BinanceMonitor.Tiny\""));
        assertTrue(positionLayout.contains("textAppearance=\"@style/TextAppearance.BinanceMonitor.Micro\""));
        assertTrue(logLayout.contains("textAppearance=\"@style/TextAppearance.BinanceMonitor.BodyCompact\""));
        assertTrue(settingsLayout.contains("textAppearance=\"@style/TextAppearance.BinanceMonitor.BodyCompact\""));
        assertTrue(settingsLayout.contains("textAppearance=\"@style/TextAppearance.BinanceMonitor.Micro\""));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
