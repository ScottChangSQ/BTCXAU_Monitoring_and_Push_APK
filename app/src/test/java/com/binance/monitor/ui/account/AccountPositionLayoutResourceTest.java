/*
 * 账户持仓页布局资源测试，确保页面骨架分段顺序和关键控件契约稳定。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    // 所有底部导航页都必须固定为同一套 5 Tab 结构和统一文案引用。
    @Test
    public void allBottomNavLayoutsShouldKeepUnifiedFiveTabs() throws Exception {
        Map<String, String> expectedTabs = new LinkedHashMap<>();
        expectedTabs.put("tabMarketMonitor", "@string/nav_market_monitor");
        expectedTabs.put("tabMarketChart", "@string/nav_market_chart");
        expectedTabs.put("tabAccountStats", "@string/nav_account_stats");
        expectedTabs.put("tabAccountPosition", "@string/nav_account_position");
        expectedTabs.put("tabSettings", "@string/nav_settings");

        String[] layoutCandidates = new String[]{
                "app/src/main/res/layout/activity_main.xml",
                "app/src/main/res/layout/activity_market_chart.xml",
                "app/src/main/res/layout/activity_account_stats.xml",
                "app/src/main/res/layout/activity_account_position.xml",
                "app/src/main/res/layout/activity_settings.xml",
                "app/src/main/res/layout/activity_settings_detail.xml"
        };
        for (String candidate : layoutCandidates) {
            Document document = parseXml(resolveProjectFile(candidate, candidate.replace("app/", "")));
            for (Map.Entry<String, String> entry : expectedTabs.entrySet()) {
                org.w3c.dom.Element element = findElement(document, entry.getKey());
                assertTrue(candidate + " 缺少 " + entry.getKey(), element != null);
                assertTrue(candidate + " 的 " + entry.getKey() + " 文案不一致",
                        entry.getValue().equals(element.getAttribute("android:text")));
            }
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
}
