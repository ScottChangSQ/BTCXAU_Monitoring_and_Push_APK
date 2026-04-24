/*
 * 锁定全局语义尺寸 token 合同，防止资源层继续分叉命名。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public class SpacingTokenContractResourceTest {

    private static final Map<String, String> EXPECTED_CANONICAL_SEMANTIC_SPACING_TOKENS =
            buildExpectedCanonicalSemanticSpacingTokens();
    @Test
    public void dimensShouldExposeGlobalBaseSpacingLadder() throws Exception {
        Map<String, String> dimens = parseDimens(
                "app/src/main/res/values/dimens.xml",
                "src/main/res/values/dimens.xml"
        );

        assertDimenValue(dimens, "space_2", "2dp");
        assertDimenValue(dimens, "space_4", "4dp");
        assertDimenValue(dimens, "space_8", "8dp");
        assertDimenValue(dimens, "space_12", "12dp");
        assertDimenValue(dimens, "space_16", "16dp");
        assertDimenValue(dimens, "space_24", "24dp");
    }

    @Test
    public void dimensShouldExposeCanonicalSemanticSpacingTokens() throws Exception {
        Map<String, String> dimens = parseDimens(
                "app/src/main/res/values/dimens.xml",
                "src/main/res/values/dimens.xml"
        );

        assertCanonicalSemanticSpacingTokenBoundary(dimens);
    }

    @Test
    public void canonicalSpacingBoundaryShouldIgnoreFutureGeometryTokens() {
        Map<String, String> dimens = new LinkedHashMap<>(EXPECTED_CANONICAL_SEMANTIC_SPACING_TOKENS);
        dimens.put("floating_window_padding_x", "4dp");
        dimens.put("curve_chart_left_inset", "34dp");
        dimens.put("kline_indicator_plot_top_inset", "12dp");

        assertCanonicalSemanticSpacingTokenBoundary(dimens);
    }

    @Test
    public void dimensShouldNotKeepLegacySpacingAliases() throws Exception {
        Map<String, String> dimens = parseDimens(
                "app/src/main/res/values/dimens.xml",
                "src/main/res/values/dimens.xml"
        );

        List<String> legacyNames = Arrays.asList(
                "page_horizontal_padding",
                "page_section_gap",
                "card_content_padding",
                "control_group_gap",
                "global_status_sheet_row_gap",
                "chart_indicator_option_gap",
                "chart_indicator_option_padding_x",
                "chart_top_mode_button_padding_x",
                "chart_symbol_select_field_trailing_reserve",
                "subject_padding_x_md",
                "subject_padding_x_compact",
                "subject_select_field_trailing_reserve",
                "subject_group_gap_md",
                "position_row_header_padding_horizontal",
                "position_row_header_padding_vertical"
        );
        for (String name : legacyNames) {
            assertTrue("legacy spacing token 应已删除: " + name, !dimens.containsKey(name));
        }
    }

    // 断言指定尺寸 token 存在且值完全一致。
    private static void assertDimenValue(Map<String, String> dimens, String name, String expectedValue) {
        assertTrue("缺少尺寸 token: " + name, dimens.containsKey(name));
        assertEquals("尺寸 token 值不符: " + name, expectedValue, dimens.get(name));
    }

    // 锁定 canonical 语义 spacing token 的完整集合边界，避免只补部分名称也误判通过。
    private static void assertCanonicalSemanticSpacingTokenBoundary(Map<String, String> dimens) {
        Set<String> actualNames = new LinkedHashSet<>();
        for (String name : dimens.keySet()) {
            if (isCanonicalSemanticSpacingTokenCandidate(name)) {
                actualNames.add(name);
            }
        }
        assertEquals(
                "canonical 语义 spacing token 集合边界不符",
                EXPECTED_CANONICAL_SEMANTIC_SPACING_TOKENS.keySet(),
                actualNames
        );
        for (Map.Entry<String, String> entry : EXPECTED_CANONICAL_SEMANTIC_SPACING_TOKENS.entrySet()) {
            assertDimenValue(dimens, entry.getKey(), entry.getValue());
        }
    }

    // 只把 canonical 名本身或其非法扩展名纳入集合边界判断，避免把非主链旧名噪音卷进来。
    private static boolean isCanonicalSemanticSpacingTokenCandidate(String name) {
        for (String canonicalName : EXPECTED_CANONICAL_SEMANTIC_SPACING_TOKENS.keySet()) {
            if (canonicalName.equals(name) || name.startsWith(canonicalName + "_")) {
                return true;
            }
        }
        return false;
    }

    // 构建 canonical 语义 spacing token 合同真值。
    private static Map<String, String> buildExpectedCanonicalSemanticSpacingTokens() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("screen_edge_padding", "8dp");
        expected.put("section_gap", "8dp");
        expected.put("container_padding", "8dp");
        expected.put("container_padding_compact", "4dp");
        expected.put("sheet_content_padding", "8dp");
        expected.put("dialog_content_padding", "12dp");
        expected.put("row_gap", "8dp");
        expected.put("row_gap_compact", "4dp");
        expected.put("inline_gap", "4dp");
        expected.put("inline_gap_compact", "2dp");
        expected.put("field_padding_x", "12dp");
        expected.put("field_padding_x_compact", "8dp");
        expected.put("field_trailing_reserve", "16dp");
        expected.put("field_trailing_reserve_compact", "8dp");
        expected.put("icon_text_gap", "4dp");
        expected.put("list_item_padding_x", "12dp");
        expected.put("list_item_padding_y", "8dp");
        return expected;
    }


    // 解析 dimens.xml 为 name -> value 的合同字典。
    private static Map<String, String> parseDimens(String... candidates) throws Exception {
        Path path = resolvePath(candidates);
        Document document = parseXml(path);
        NodeList nodes = document.getElementsByTagName("dimen");
        Map<String, String> dimens = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Element node = (org.w3c.dom.Element) nodes.item(i);
            String name = node.getAttribute("name");
            if (name == null || name.isEmpty()) {
                continue;
            }
            dimens.put(name, node.getTextContent().trim());
        }
        return dimens;
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
