/*
 * 图表页布局资源测试，确保主题相关颜色不再硬编码在 XML 里。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class MarketChartLayoutThemeResourceTest {
    private static final Pattern HARD_CODED_HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6,8}");

    @Test
    public void marketChartLayoutShouldNotContainHardCodedHexColors() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        assertFalse("图表页布局仍包含硬编码颜色，应改为统一主题入口控制",
                HARD_CODED_HEX_COLOR.matcher(xml).find());
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到布局文件");
    }
}
