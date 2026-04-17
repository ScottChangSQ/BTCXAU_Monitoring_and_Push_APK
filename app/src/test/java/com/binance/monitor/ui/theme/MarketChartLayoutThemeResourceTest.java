/*
 * 图表页布局资源测试，确保主题相关颜色不再硬编码在 XML 里。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

public class MarketChartLayoutThemeResourceTest {
    private static final Pattern HARD_CODED_HEX_COLOR = Pattern.compile("#[0-9A-Fa-f]{6,8}");
    private Document document;

    @Before
    public void setUp() throws Exception {
        document = parseXml(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
    }

    @Test
    public void marketChartLayoutShouldNotContainHardCodedHexColors() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        assertFalse("图表页布局仍包含硬编码颜色，应改为统一主题入口控制",
                HARD_CODED_HEX_COLOR.matcher(xml).find());
    }

    @Test
    public void marketChartLayoutShouldRemoveLegacyCountdownView() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        assertFalse(xml.contains("@+id/tvChartRefreshCountdown"));
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

    private static Document parseXml(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                return factory.newDocumentBuilder().parse(path.toFile());
            }
        }
        throw new IllegalStateException("找不到布局文件");
    }

}
