/*
 * 账户持仓页布局资源测试，确保页面骨架分段顺序和关键控件契约稳定。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

public class AccountPositionLayoutResourceTest {

    // 布局必须存在账户持仓 Tab，并保留固定分段顺序。
    @Test
    public void activityAccountPositionShouldKeepSectionOrderAndTabId() throws Exception {
        Document document = parseXml(resolveProjectFile(
                "app/src/main/res/layout/activity_account_position.xml",
                "src/main/res/layout/activity_account_position.xml"
        ));
        assertElementExists(document, "tabAccountPosition");
        assertElementExists(document, "ivAccountPrivacyToggle");
        assertElementExists(document, "tvAccountConnectionStatus");
        assertElementExists(document, "recyclerOverviewMetrics");
        assertElementExists(document, "tvPositionAggregateTitle");
        assertElementExists(document, "recyclerPositionAggregates");
        assertElementExists(document, "recyclerPositions");
        assertElementExists(document, "recyclerPendingOrders");

        int overviewIndex = findElementOrder(document, "cardOverviewSection");
        int positionIndex = findElementOrder(document, "cardPositionSection");
        int pendingIndex = findElementOrder(document, "cardPendingSection");
        int tabBarIndex = findElementOrder(document, "tabBar");

        assertTrue("账户概览应在当前持仓之前", overviewIndex < positionIndex);
        assertTrue("当前持仓应在挂单之前", positionIndex < pendingIndex);
        assertTrue("挂单应在底部 Tab 之前", pendingIndex < tabBarIndex);
    }

    // 所有底部导航页都必须固定为同一套 4 Tab 结构和统一文案引用。
    @Test
    public void allBottomNavLayoutsShouldKeepUnifiedFourTabs() throws Exception {
        Map<String, String> expectedTabs = new LinkedHashMap<>();
        expectedTabs.put("tabMarketMonitor", "@string/nav_market_monitor");
        expectedTabs.put("tabMarketChart", "@string/nav_market_chart");
        expectedTabs.put("tabAccountStats", "@string/nav_account_stats");
        expectedTabs.put("tabAccountPosition", "@string/nav_account_position");

        String[] layoutCandidates = new String[]{
                "app/src/main/res/layout/activity_main.xml",
                "app/src/main/res/layout/activity_market_chart.xml",
                "app/src/main/res/layout/activity_account_stats.xml",
                "app/src/main/res/layout/activity_account_position.xml"
        };
        for (String candidate : layoutCandidates) {
            Document document = parseXml(resolveProjectFile(candidate, candidate.replace("app/", "")));
            for (Map.Entry<String, String> entry : expectedTabs.entrySet()) {
                org.w3c.dom.Element element = findElement(document, entry.getKey());
                assertTrue(candidate + " 缺少 " + entry.getKey(), element != null);
                assertTrue(candidate + " 的 " + entry.getKey() + " 文案不一致",
                        entry.getValue().equals(element.getAttribute("android:text")));
            }
            assertTrue(candidate + " 不应再保留设置 tab",
                    findElement(document, "tabSettings") == null);
            int accountPositionIndex = findElementOrder(document, "tabAccountPosition");
            int accountStatsIndex = findElementOrder(document, "tabAccountStats");
            assertTrue(candidate + " 应让账户持仓排在账户统计之前",
                    accountPositionIndex >= 0 && accountStatsIndex >= 0 && accountPositionIndex < accountStatsIndex);
        }
    }

    // 隐藏的 BottomNavigationView 菜单顺序也要和可见 Tab 一致，避免后续恢复原生导航时顺序回退。
    @Test
    public void bottomNavMenuShouldPlaceAccountPositionBeforeAccountStats() throws Exception {
        Path menuPath = resolveProjectFile(
                "app/src/main/res/menu/menu_bottom_nav.xml",
                "src/main/res/menu/menu_bottom_nav.xml"
        );
        Document document = parseXml(menuPath);
        int accountPositionIndex = findElementOrder(document, "nav_account_position");
        int accountStatsIndex = findElementOrder(document, "nav_account_stats");
        assertTrue("底部导航菜单应让账户持仓排在账户统计之前",
                accountPositionIndex >= 0 && accountStatsIndex >= 0 && accountPositionIndex < accountStatsIndex);
    }

    @Test
    public void activityAccountPositionShouldReuseSharedContentLayout() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_account_position.xml",
                "src/main/res/layout/activity_account_position.xml"
        );
        Path contentPath = resolveProjectFile(
                "app/src/main/res/layout/content_account_position.xml",
                "src/main/res/layout/content_account_position.xml"
        );
        String activityXml = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        String contentXml = new String(Files.readAllBytes(contentPath), StandardCharsets.UTF_8);

        assertTrue("账户持仓 Activity 应通过 include 复用共享内容布局", activityXml.contains("@layout/content_account_position"));
        assertTrue("共享内容布局必须保留持仓滚动容器", contentXml.contains("@+id/scrollAccountPosition"));
    }

    @Test
    public void accountPositionHeaderShouldPlacePrivacyEyeBeforeConnectedAccountButton() throws Exception {
        String activityXml = new String(Files.readAllBytes(resolveProjectFile(
                "app/src/main/res/layout/activity_account_position.xml",
                "src/main/res/layout/activity_account_position.xml"
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String contentXml = new String(Files.readAllBytes(resolveProjectFile(
                "app/src/main/res/layout/content_account_position.xml",
                "src/main/res/layout/content_account_position.xml"
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertPrivacyBeforeConnection(activityXml, "activity_account_position.xml");
        assertPrivacyBeforeConnection(contentXml, "content_account_position.xml");
    }

    @Test
    public void accountPositionTriggersAndTradeHistoryFiltersShouldUseStandardSubjectStyles() throws Exception {
        Document contentDocument = parseXml(resolveProjectFile(
                "app/src/main/res/layout/content_account_position.xml",
                "src/main/res/layout/content_account_position.xml"
        ));
        Document historyDocument = parseXml(resolveProjectFile(
                "app/src/main/res/layout/dialog_account_trade_history_sheet.xml",
                "src/main/res/layout/dialog_account_trade_history_sheet.xml"
        ));
        String historyXml = new String(Files.readAllBytes(resolveProjectFile(
                "app/src/main/res/layout/dialog_account_trade_history_sheet.xml",
                "src/main/res/layout/dialog_account_trade_history_sheet.xml"
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertElementAttribute(contentDocument, "tvAccountConnectionStatus", "style",
                "@style/Widget.BinanceMonitor.Subject.TextTrigger.Compact");
        assertElementAttribute(contentDocument, "btnOpenAccountHistory", "style",
                "@style/Widget.BinanceMonitor.Subject.TextTrigger");
        assertElementAttribute(historyDocument, "tvTradeHistoryProductLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(historyDocument, "tvTradeHistorySideLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(historyDocument, "tvTradeHistorySortLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");

        assertFalse("历史筛选不应继续使用旧 spinner 背景", historyXml.contains("@drawable/bg_spinner_filter"));
        assertFalse("历史筛选不应继续引用旧 Spinner.Label 包装样式", historyXml.contains("Widget.BinanceMonitor.Spinner.Label"));
        assertFalse("历史筛选不应继续沿用旧 control_height_md 高度", historyXml.contains("@dimen/control_height_md"));
        assertTrue("历史筛选应切到 subject_height_md", historyXml.contains("@dimen/subject_height_md"));
    }

    @Test
    public void accountPositionLayoutsShouldUseCanonicalSpacingTokens() throws Exception {
        String contentXml = new String(Files.readAllBytes(resolveProjectFile(
                "app/src/main/res/layout/content_account_position.xml",
                "src/main/res/layout/content_account_position.xml"
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String historyXml = new String(Files.readAllBytes(resolveProjectFile(
                "app/src/main/res/layout/dialog_account_trade_history_sheet.xml",
                "src/main/res/layout/dialog_account_trade_history_sheet.xml"
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(contentXml.contains("@dimen/screen_edge_padding"));
        assertTrue(contentXml.contains("@dimen/section_gap"));
        assertTrue(contentXml.contains("@dimen/container_padding"));
        assertTrue(contentXml.contains("@dimen/inline_gap"));
        assertFalse(contentXml.contains("@dimen/page_horizontal_padding"));
        assertFalse(contentXml.contains("@dimen/page_section_gap"));
        assertFalse(contentXml.contains("@dimen/card_content_padding"));
        assertFalse(contentXml.contains("@dimen/control_group_gap"));

        assertTrue(historyXml.contains("@dimen/sheet_content_padding"));
        assertTrue(historyXml.contains("@dimen/inline_gap"));
        assertFalse(historyXml.contains("@dimen/card_content_padding"));
        assertFalse(historyXml.contains("@dimen/control_group_gap"));
    }

    @Test
    public void accountPositionBridgeActivityShouldUseCanonicalSpacingTokens() throws Exception {
        String activityXml = new String(Files.readAllBytes(resolveProjectFile(
                "app/src/main/res/layout/activity_account_position.xml",
                "src/main/res/layout/activity_account_position.xml"
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(activityXml.contains("@dimen/screen_edge_padding"));
        assertTrue(activityXml.contains("@dimen/section_gap"));
        assertTrue(activityXml.contains("@dimen/container_padding"));
        assertTrue(activityXml.contains("@dimen/inline_gap_compact"));
        assertTrue(activityXml.contains("@dimen/row_gap"));
        assertTrue(activityXml.contains("@dimen/row_gap_compact"));
        assertFalse(activityXml.contains("@dimen/page_horizontal_padding"));
        assertFalse(activityXml.contains("@dimen/page_section_gap"));
        assertFalse(activityXml.contains("@dimen/card_content_padding"));
        assertFalse(activityXml.contains("@dimen/control_group_gap"));
        assertFalse(activityXml.contains("android:drawablePadding=\"2dp\""));
        assertFalse(activityXml.contains("android:paddingTop=\"6dp\""));
        assertFalse(activityXml.contains("android:paddingBottom=\"4dp\""));
    }

    // 按当前工作目录自动解析项目文件，兼容根目录和 app 模块目录两种入口。
    private static Path resolveProjectFile(String... candidates) {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new IllegalStateException("找不到资源文件: " + Arrays.toString(candidates));
    }

    // 解析 XML 资源文件。
    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    // 断言控件 id 存在。
    private static void assertElementExists(Document document, String viewId) {
        if (findElementOrder(document, viewId) >= 0) {
            return;
        }
        throw new AssertionError("找不到控件: " + viewId);
    }

    private static void assertElementAttribute(Document document,
                                               String viewId,
                                               String attributeName,
                                               String expectedValue) {
        org.w3c.dom.Element element = findElement(document, viewId);
        if (element == null) {
            throw new AssertionError("找不到控件: " + viewId);
        }
        String actualValue = element.getAttribute(attributeName);
        assertTrue(viewId + " 的 " + attributeName + " 应为 " + expectedValue + "，实际为 " + actualValue,
                expectedValue.equals(actualValue));
    }

    private static org.w3c.dom.Element findElement(Document document, String viewId) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) elements.item(i);
            String rawId = element.getAttribute("android:id");
            if (rawId == null || rawId.isEmpty()) {
                continue;
            }
            String shortId = rawId.substring(rawId.indexOf('/') + 1);
            if (viewId.equals(shortId)) {
                return element;
            }
        }
        return null;
    }

    // 按文档顺序返回控件首次出现位置。
    private static int findElementOrder(Document document, String viewId) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) elements.item(i);
            String rawId = element.getAttribute("android:id");
            if (rawId == null || rawId.isEmpty()) {
                continue;
            }
            String shortId = rawId.substring(rawId.indexOf('/') + 1);
            if (viewId.equals(shortId)) {
                return i;
            }
        }
        return -1;
    }

    private static void assertPrivacyBeforeConnection(String xml, String fileLabel) {
        int privacyIndex = xml.indexOf("android:id=\"@+id/ivAccountPrivacyToggle\"");
        int connectionIndex = xml.indexOf("android:id=\"@+id/tvAccountConnectionStatus\"");
        assertTrue(fileLabel + " 必须保留隐私按钮", privacyIndex >= 0);
        assertTrue(fileLabel + " 必须保留已连接账户按钮", connectionIndex >= 0);
        assertTrue(fileLabel + " 应把小眼睛放在已连接账户按钮左侧", privacyIndex < connectionIndex);
    }
}
