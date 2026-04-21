/*
 * 账户统计布局资源测试，确保关键分段按钮从 XML 初始形状就是方角，避免运行时再被圆角模型覆盖。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

public class AccountStatsLayoutResourceTest {

    private static final Set<String> EXPECTED_BUTTON_IDS = new LinkedHashSet<>(Arrays.asList(
            "btnRange1d",
            "btnRange7d",
            "btnRange1m",
            "btnRange3m",
            "btnRange1y",
            "btnRangeAll",
            "btnReturnDay",
            "btnReturnMonth",
            "btnReturnYear",
            "btnReturnStage",
            "btnReturnsRate",
            "btnReturnsAmount",
            "btnTradePnlAll",
            "btnTradePnlBuy",
            "btnTradePnlSell",
            "btnTradeWeekdayCloseTime",
            "btnTradeWeekdayOpenTime"
    ));

    // 读取账户统计布局，并确认关键分段按钮的初始方角配置没有回退。
    @Test
    public void activityAccountStatsShouldKeepSegmentButtonsSquareFromXml() throws Exception {
        Document document = parseXml(resolveProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        ));
        NodeList buttons = document.getElementsByTagName("com.google.android.material.button.MaterialButton");
        Set<String> remaining = new LinkedHashSet<>(EXPECTED_BUTTON_IDS);
        for (int i = 0; i < buttons.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) buttons.item(i);
            String viewId = element.getAttribute("android:id");
            if (viewId == null || viewId.isEmpty()) {
                continue;
            }
            String shortId = viewId.substring(viewId.indexOf('/') + 1);
            if (!remaining.contains(shortId)) {
                continue;
            }
            assertEquals(
                    shortId + " 缺少方角形状覆盖",
                    "@style/ShapeAppearanceBinanceMonitorSquare",
                    element.getAttribute("app:shapeAppearanceOverlay")
            );
            assertEquals(
                    shortId + " 的初始圆角必须为 0dp",
                    "0dp",
                    element.getAttribute("app:cornerRadius")
            );
            remaining.remove(shortId);
        }
        assertTrue("还有分段按钮没有被校验到: " + remaining, remaining.isEmpty());
    }

    // 日期选择面板不应再把标题和按钮颜色写死，否则主题切换后容易与背景贴色。
    @Test
    public void activityAccountStatsShouldNotHardcodePickerPanelTextColors() throws Exception {
        Document document = parseXml(resolveProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        ));
        assertPickerTextColorCleared(document, "tvManualDatePickerTitle");
        assertPickerTextColorCleared(document, "btnManualDateCancel");
        assertPickerTextColorCleared(document, "btnManualDateConfirm");
        assertPickerTextColorCleared(document, "tvReturnPeriodPickerTitle");
        assertPickerTextColorCleared(document, "btnReturnPeriodCancel");
        assertPickerTextColorCleared(document, "btnReturnPeriodConfirm");
    }

    @Test
    public void accountStatsLayoutsShouldRemoveManualApplyButtonAndKeepDateInputsFullWidth() throws Exception {
        Document activityDocument = parseXml(resolveProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        ));
        Document contentDocument = parseXml(resolveProjectFile(
                "app/src/main/res/layout/content_account_stats.xml",
                "src/main/res/layout/content_account_stats.xml"
        ));

        assertElementMissing(activityDocument, "btnApplyManualRange");
        assertElementMissing(contentDocument, "btnApplyManualRange");
        assertEquals("1", findElementById(activityDocument, "etRangeStart").getAttribute("android:layout_weight"));
        assertEquals("1", findElementById(activityDocument, "etRangeEnd").getAttribute("android:layout_weight"));
        assertEquals("1", findElementById(contentDocument, "etRangeStart").getAttribute("android:layout_weight"));
        assertEquals("1", findElementById(contentDocument, "etRangeEnd").getAttribute("android:layout_weight"));
    }

    // 日期滚轮必须保留原生 NumberPicker，避免自定义控件破坏系统滚轮外观。
    @Test
    public void activityAccountStatsShouldUsePlatformNumberPicker() throws Exception {
        Document document = parseXml(resolveProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        ));
        NodeList platformPickers = document.getElementsByTagName("NumberPicker");
        NodeList themedPickers = document.getElementsByTagName("com.binance.monitor.ui.widget.ThemedNumberPicker");
        assertEquals("5", String.valueOf(platformPickers.getLength()));
        assertEquals("0", String.valueOf(themedPickers.getLength()));
    }

    // 分析页主模块顺序应由共享布局直接定义，避免运行时二次重排导致主壳与桥接页不一致。
    @Test
    public void activityAccountStatsShouldKeepAnalysisSectionsInSharedLayoutOrder() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/content_account_stats.xml",
                "src/main/res/layout/content_account_stats.xml"
        );
        String xml = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        int statsSummaryIndex = xml.indexOf("android:id=\"@+id/cardStatsSummarySection\"");
        int returnStatsIndex = xml.indexOf("android:id=\"@+id/cardReturnStatsSection\"");
        int curveIndex = xml.indexOf("android:id=\"@+id/cardCurveSection\"");
        int tradeStatsIndex = xml.indexOf("android:id=\"@+id/cardTradeStatsSection\"");
        int tradeRecordsIndex = xml.indexOf("android:id=\"@+id/cardTradeRecordsSection\"");

        assertTrue("共享布局里必须保留核心统计卡片", statsSummaryIndex >= 0);
        assertTrue("共享布局里必须保留收益统计卡片", returnStatsIndex >= 0);
        assertTrue("共享布局里必须保留净值/结余曲线卡片", curveIndex >= 0);
        assertTrue("共享布局里必须保留交易统计卡片", tradeStatsIndex >= 0);
        assertTrue("共享布局里必须保留交易记录卡片", tradeRecordsIndex >= 0);
        assertTrue("分析页应先展示核心统计，再展示收益统计", statsSummaryIndex < returnStatsIndex);
        assertTrue("分析页应在收益统计后展示净值/结余曲线", returnStatsIndex < curveIndex);
        assertTrue("分析页应在净值/结余曲线后展示交易统计", curveIndex < tradeStatsIndex);
        assertTrue("交易记录应排在交易统计之后，避免打断分析主链", tradeStatsIndex < tradeRecordsIndex);
    }

    // 分析页顺序已交给共享布局真值管理，宿主代码不应继续保留旧的运行时底部重排。
    @Test
    public void accountStatsSourcesShouldNotKeepLegacyAnalysisSectionReorderLogic() throws Exception {
        String bridgeSource = readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        String screenSource = readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        );
        String fragmentSource = readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java"
        );
        String runtimeSource = readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java"
        );
        String controllerSource = readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageController.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsPageController.java"
        );
        String hostDelegateSource = readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageHostDelegate.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsPageHostDelegate.java"
        );

        assertFalse("桥接页不应继续保留旧的曲线卡片底部重排方法", bridgeSource.contains("placeCurveSectionToBottom("));
        assertFalse("正式页不应继续保留旧的曲线卡片底部重排方法", screenSource.contains("placeCurveSectionToBottom("));
        assertFalse("主壳 Fragment 不应继续转发旧的曲线卡片底部重排逻辑", fragmentSource.contains("placeCurveSectionToBottom("));
        assertFalse("运行时主链不应在冷启动时触发旧的底部重排", runtimeSource.contains("placeCurveSectionToBottom("));
        assertFalse("页面控制器接口不应继续暴露旧的底部重排能力", controllerSource.contains("placeCurveSectionToBottom("));
        assertFalse("宿主委托不应继续透传旧的底部重排能力", hostDelegateSource.contains("placeCurveSectionToBottom("));
    }

    // 账户统计页顶部不再保留隐私切换和已连接账户入口，避免和账户持仓页重复。
    @Test
    public void activityAccountStatsShouldNotKeepTopPrivacyAndConnectionShortcuts() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        );
        String xml = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        assertFalse("账户统计页不应继续保留顶部隐藏按钮", xml.contains("@+id/ivAccountPrivacyToggle"));
        assertFalse("账户统计页不应继续保留顶部已连接账户入口", xml.contains("@+id/tvAccountConnectionStatus"));
    }

    @Test
    public void activityAccountStatsShouldReuseSharedContentLayout() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        );
        Path contentPath = resolveProjectFile(
                "app/src/main/res/layout/content_account_stats.xml",
                "src/main/res/layout/content_account_stats.xml"
        );
        String activityXml = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        String contentXml = new String(Files.readAllBytes(contentPath), StandardCharsets.UTF_8);

        assertTrue("账户统计 Activity 应通过 include 复用共享内容布局", activityXml.contains("@layout/content_account_stats"));
        assertTrue("共享内容布局必须保留交易记录滚动容器", contentXml.contains("@+id/scrollAccountStats"));
    }

    @Test
    public void accountStatsSourcesShouldUseSharedContentAwareSegmentSizing() throws Exception {
        String screenSource = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String bridgeSource = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("正式页应复用共享分段按钮样式", screenSource.contains("UiPaletteManager.styleSegmentedOption("));
        assertTrue("正式页应按文案宽度分配分段按钮宽度", screenSource.contains("UiPaletteManager.applyContentAwareButtonGroupLayout("));
        assertTrue("桥接页应复用共享分段按钮样式", bridgeSource.contains("UiPaletteManager.styleSegmentedOption("));
        assertTrue("桥接页应按文案宽度分配分段按钮宽度", bridgeSource.contains("UiPaletteManager.applyContentAwareButtonGroupLayout("));
    }

    @Test
    public void accountStatsSourcesShouldAutoApplyManualDateRangeAfterPickerConfirm() throws Exception {
        String screenSource = readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        );
        String bridgeSource = readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertFalse("正式页不应再保留手动应用按钮点击链路",
                screenSource.contains("binding.btnApplyManualRange.setOnClickListener"));
        assertFalse("桥接页不应再保留手动应用按钮点击链路",
                bridgeSource.contains("binding.btnApplyManualRange.setOnClickListener"));
        assertTrue("正式页日期确认后应直接应用手动区间",
                screenSource.contains("applyManualCurveRangeIfReady();"));
        assertTrue("桥接页日期确认后应直接应用手动区间",
                bridgeSource.contains("applyManualCurveRangeIfReady();"));
    }

    @Test
    public void accountStatsLayoutsShouldUseStandardSubjectsForDateAndTradeFilters() throws Exception {
        Document activityDocument = parseXml(resolveProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        ));
        Document contentDocument = parseXml(resolveProjectFile(
                "app/src/main/res/layout/content_account_stats.xml",
                "src/main/res/layout/content_account_stats.xml"
        ));
        String activityXml = readProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        );
        String contentXml = readProjectFile(
                "app/src/main/res/layout/content_account_stats.xml",
                "src/main/res/layout/content_account_stats.xml"
        );

        assertElementAttribute(activityDocument, "tvReturnsPeriod", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(contentDocument, "tvReturnsPeriod", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(activityDocument, "btnReturnCurrentMonth", "style",
                "@style/Widget.BinanceMonitor.Subject.TextTrigger");
        assertElementAttribute(contentDocument, "btnReturnCurrentMonth", "style",
                "@style/Widget.BinanceMonitor.Subject.TextTrigger");

        assertElementAttribute(activityDocument, "tvTradeProductLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(activityDocument, "tvTradeSideLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(activityDocument, "tvTradeSortLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(contentDocument, "tvTradeProductLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(contentDocument, "tvTradeSideLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertElementAttribute(contentDocument, "tvTradeSortLabel", "style",
                "@style/Widget.BinanceMonitor.Subject.SelectField.Label");

        assertFalse(activityXml.contains("@drawable/bg_spinner_filter"));
        assertFalse(contentXml.contains("@drawable/bg_spinner_filter"));
        assertFalse(activityXml.contains("@drawable/bg_inline_button"));
        assertFalse(contentXml.contains("@drawable/bg_inline_button"));
        assertFalse(activityXml.contains("Widget.BinanceMonitor.TextAction.Medium"));
        assertFalse(contentXml.contains("Widget.BinanceMonitor.TextAction.Medium"));
        assertTrue(activityXml.contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Secondary"));
        assertTrue(activityXml.contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Primary"));
        assertTrue(contentXml.contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Secondary"));
        assertTrue(contentXml.contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Primary"));
    }

    @Test
    public void accountStatsSharedLayoutShouldUseCanonicalSpacingTokens() throws Exception {
        String contentXml = readProjectFile(
                "app/src/main/res/layout/content_account_stats.xml",
                "src/main/res/layout/content_account_stats.xml"
        );

        assertTrue(contentXml.contains("@dimen/screen_edge_padding"));
        assertTrue(contentXml.contains("@dimen/section_gap"));
        assertTrue(contentXml.contains("@dimen/container_padding"));
        assertTrue(contentXml.contains("@dimen/inline_gap"));
        assertFalse(contentXml.contains("@dimen/page_horizontal_padding"));
        assertFalse(contentXml.contains("@dimen/page_section_gap"));
        assertFalse(contentXml.contains("@dimen/card_content_padding"));
        assertFalse(contentXml.contains("@dimen/control_group_gap"));
    }

    @Test
    public void accountStatsBridgeActivityShouldUseCanonicalSpacingTokens() throws Exception {
        String activityXml = readProjectFile(
                "app/src/main/res/layout/activity_account_stats.xml",
                "src/main/res/layout/activity_account_stats.xml"
        );

        assertTrue(activityXml.contains("@dimen/screen_edge_padding"));
        assertTrue(activityXml.contains("@dimen/section_gap"));
        assertTrue(activityXml.contains("@dimen/container_padding"));
        assertTrue(activityXml.contains("@dimen/inline_gap"));
        assertTrue(activityXml.contains("@dimen/row_gap"));
        assertTrue(activityXml.contains("@dimen/row_gap_compact"));
        assertFalse(activityXml.contains("@dimen/page_horizontal_padding"));
        assertFalse(activityXml.contains("@dimen/page_section_gap"));
        assertFalse(activityXml.contains("@dimen/card_content_padding"));
        assertFalse(activityXml.contains("@dimen/control_group_gap"));
        assertFalse(activityXml.contains("android:paddingStart=\"4dp\""));
        assertFalse(activityXml.contains("android:paddingEnd=\"4dp\""));
        assertFalse(activityXml.contains("android:layout_marginTop=\"8dp\""));
        assertFalse(activityXml.contains("android:layout_marginTop=\"6dp\""));
        assertFalse(activityXml.contains("android:padding=\"8dp\""));
    }

    // 按当前工作目录自动解析项目资源文件，兼容根目录和 app 模块目录两种执行入口。
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

    // 读取项目源码文本，兼容根目录和 app 模块目录两种执行入口。
    private static String readProjectFile(String... candidates) throws Exception {
        Path path = resolveProjectFile(candidates);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    // 解析 XML 文档，供资源契约断言复用。
    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    // 断言指定控件已移除 XML 级别的固定文字颜色，交给运行时主题统一控制。
    private static void assertPickerTextColorCleared(Document document, String viewId) {
        org.w3c.dom.Element element = findElementById(document, viewId);
        assertEquals(viewId + " 不应继续写死文字颜色", "", element.getAttribute("android:textColor"));
    }

    // 在布局里按 android:id 精确找到对应元素。
    private static org.w3c.dom.Element findElementById(Document document, String viewId) {
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
        throw new IllegalStateException("找不到控件: " + viewId);
    }

    private static void assertElementMissing(Document document, String viewId) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) elements.item(i);
            String rawId = element.getAttribute("android:id");
            if (rawId == null || rawId.isEmpty()) {
                continue;
            }
            String shortId = rawId.substring(rawId.indexOf('/') + 1);
            if (viewId.equals(shortId)) {
                throw new AssertionError("不应再保留控件: " + viewId);
            }
        }
    }

    private static void assertElementAttribute(Document document,
                                               String viewId,
                                               String attributeName,
                                               String expectedValue) {
        org.w3c.dom.Element element = findElementById(document, viewId);
        String actualValue = element.getAttribute(attributeName);
        assertEquals(viewId + " 的 " + attributeName + " 应为 " + expectedValue,
                expectedValue,
                actualValue);
    }
}
