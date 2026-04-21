/*
 * 锁定文字体系的全局行高合同，避免页面继续各自定义行距。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public class LineHeightContractResourceTest {

    private static final Map<String, String> EXPECTED_SCALE_LINE_HEIGHTS = buildExpectedScaleLineHeights();

    @Test
    public void textAppearancesShouldExposeGlobalLineHeightContract() throws Exception {
        Map<String, Map<String, String>> styleItems = parseStyleItems(
                "app/src/main/res/values/styles.xml",
                "src/main/res/values/styles.xml"
        );

        assertScaleStyleBoundary(styleItems);
        assertScaleLineHeightBoundary(styleItems);
    }

    // 断言指定样式存在并满足行高合同。
    private static void assertStyleLineHeight(Map<String, Map<String, String>> styleItems,
                                              String styleName,
                                              String expectedLineHeight) {
        assertTrue("未找到样式: " + styleName, styleItems.containsKey(styleName));
        Map<String, String> items = styleItems.get(styleName);
        assertTrue("样式缺少行高合同: " + styleName, items.containsKey("android:lineHeight"));
        assertEquals("样式行高不符: " + styleName, expectedLineHeight, items.get("android:lineHeight"));
    }

    // 锁定 scale 样式集合，避免后续再长出第二套正文尺度命名。
    private static void assertScaleStyleBoundary(Map<String, Map<String, String>> styleItems) {
        Set<String> actualScaleStyles = new LinkedHashSet<>();
        for (String styleName : styleItems.keySet()) {
            if (styleName.startsWith("TextAppearance.BinanceMonitor.Scale.")) {
                actualScaleStyles.add(styleName);
            }
        }
        assertEquals(
                "Scale 样式集合边界不符",
                EXPECTED_SCALE_LINE_HEIGHTS.keySet(),
                actualScaleStyles
        );
    }

    // 锁定参与 lineHeight 合同的 scale 样式集合与取值，避免只补几个 style 名就通过。
    private static void assertScaleLineHeightBoundary(Map<String, Map<String, String>> styleItems) {
        Set<String> actualLineHeightStyles = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, String>> entry : styleItems.entrySet()) {
            if (entry.getKey().startsWith("TextAppearance.BinanceMonitor.Scale.")
                    && entry.getValue().containsKey("android:lineHeight")) {
                actualLineHeightStyles.add(entry.getKey());
            }
        }
        assertEquals(
                "Scale 样式的 lineHeight 合同集合边界不符",
                EXPECTED_SCALE_LINE_HEIGHTS.keySet(),
                actualLineHeightStyles
        );
        for (Map.Entry<String, String> entry : EXPECTED_SCALE_LINE_HEIGHTS.entrySet()) {
            assertStyleLineHeight(styleItems, entry.getKey(), entry.getValue());
        }
    }

    // 构建 scale 样式 lineHeight 合同真值。
    private static Map<String, String> buildExpectedScaleLineHeights() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("TextAppearance.BinanceMonitor.Scale.PageHero", "28sp");
        expected.put("TextAppearance.BinanceMonitor.Scale.ValueHero", "24sp");
        expected.put("TextAppearance.BinanceMonitor.Scale.Section", "22sp");
        expected.put("TextAppearance.BinanceMonitor.Scale.Body", "20sp");
        expected.put("TextAppearance.BinanceMonitor.Scale.Compact", "16sp");
        expected.put("TextAppearance.BinanceMonitor.Scale.Dense", "14sp");
        return expected;
    }

    // 解析 styles.xml 为 style -> item -> value 的合同结构。
    private static Map<String, Map<String, String>> parseStyleItems(String... candidates) throws Exception {
        Path path = resolvePath(candidates);
        Document document = parseXml(path);
        NodeList styleNodes = document.getElementsByTagName("style");
        Map<String, Map<String, String>> styles = new HashMap<>();
        for (int i = 0; i < styleNodes.getLength(); i++) {
            org.w3c.dom.Element styleNode = (org.w3c.dom.Element) styleNodes.item(i);
            String styleName = styleNode.getAttribute("name");
            if (styleName == null || styleName.isEmpty()) {
                continue;
            }

            Map<String, String> items = new HashMap<>();
            NodeList children = styleNode.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (!(child instanceof org.w3c.dom.Element)) {
                    continue;
                }
                org.w3c.dom.Element itemNode = (org.w3c.dom.Element) child;
                if (!"item".equals(itemNode.getTagName())) {
                    continue;
                }
                String itemName = itemNode.getAttribute("name");
                if (itemName == null || itemName.isEmpty()) {
                    continue;
                }
                items.put(itemName, itemNode.getTextContent().trim());
            }
            styles.put(styleName, items);
        }
        return styles;
    }

    // 在不同运行目录下解析资源文件路径。
    private static Path resolvePath(String... candidates) {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new IllegalStateException("未找到资源文件");
    }

    // 读取 XML 文档，避免断言依赖原始文本排版。
    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }
}
