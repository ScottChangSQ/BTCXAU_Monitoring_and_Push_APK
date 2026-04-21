package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 交易图表页布局资源测试，只校验关键控件的结构和属性合同，避免受 XML 排版影响。
 * 关联文件：activity_market_chart.xml。
 */
public class MarketChartLayoutResourceTest {

    // 校验交易图表页只保留当前规格需要的轻量覆盖控件，并把断言绑定到具体控件。
    @Test
    public void activityMarketChartShouldKeepOnlyLightweightOverlayViews() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        Document document = parseXml(layoutPath);

        Element root = document.getDocumentElement();
        Element symbolPanel = requireElementById(document, "cardSymbolPanel");
        Element symbolContainer = requireElementById(document, "layoutChartSymbolPickerContainer");
        Element symbolLabel = requireElementById(document, "tvChartSymbolPickerLabel");
        Element modeGroup = requireElementById(document, "layoutChartModeToggleGroup");
        Element marketMode = requireElementById(document, "btnChartModeMarket");
        Element pendingMode = requireElementById(document, "btnChartModePending");
        Element globalStatus = requireElementById(document, "btnGlobalStatus");
        Element quickTradeBar = requireElementById(document, "layoutChartQuickTradeBar");
        Element quickTradePrimary = requireElementById(document, "btnQuickTradePrimary");
        Element quickTradeInput = requireElementById(document, "etQuickTradeVolume");
        Element quickTradeSecondary = requireElementById(document, "btnQuickTradeSecondary");
        Element positionSummary = requireElementById(document, "tvChartPositionSummary");
        Element klineChartView = requireElementById(document, "klineChartView");
        Element overlayGroup = requireElementById(document, "layoutChartOverlayToggleGroup");

        assertEquals("LinearLayout", root.getTagName());
        assertAttributeEquals(root, "android:paddingStart", "@dimen/screen_edge_padding");
        assertAttributeEquals(root, "android:paddingEnd", "@dimen/screen_edge_padding");
        assertAttributeEquals(root, "android:paddingTop", "@dimen/screen_edge_padding");
        assertAttributeEquals(root, "android:paddingBottom", "@dimen/screen_edge_padding");

        assertEquals("androidx.constraintlayout.widget.ConstraintLayout", symbolPanel.getTagName());
        assertAttributeEquals(symbolPanel, "android:layout_marginTop", "@dimen/section_gap");
        assertAttributeEquals(symbolPanel, "android:paddingStart", "@dimen/container_padding");
        assertAttributeEquals(symbolPanel, "android:paddingEnd", "@dimen/container_padding");

        assertAttributeEquals(symbolContainer, "android:layout_height", "@dimen/subject_height_compact");
        assertAttributeEquals(symbolContainer, "android:layout_marginEnd", "@dimen/inline_gap");
        assertAttributeEquals(
                symbolContainer,
                "app:layout_constraintEnd_toStartOf",
                "@+id/layoutChartModeToggleGroup"
        );
        assertAttributeEquals(symbolLabel, "style", "@style/Widget.BinanceMonitor.Subject.SelectField.Label");
        assertAttributeMissing(symbolLabel, "android:paddingStart");
        assertAttributeMissing(symbolLabel, "android:paddingEnd");
        assertAttributeMissing(symbolLabel, "android:textAppearance");

        assertAttributeEquals(modeGroup, "android:layout_marginEnd", "@dimen/inline_gap");
        assertAttributeEquals(
                modeGroup,
                "app:layout_constraintStart_toEndOf",
                "@+id/layoutChartSymbolPickerContainer"
        );
        assertAttributeEquals(
                modeGroup,
                "app:layout_constraintEnd_toStartOf",
                "@+id/btnGlobalStatus"
        );

        assertAttributeEquals(marketMode, "style", "@style/Widget.BinanceMonitor.Subject.SegmentedOption");
        assertAttributeEquals(marketMode, "android:layout_height", "@dimen/subject_height_compact");
        assertAttributeEquals(marketMode, "android:layout_marginEnd", "@dimen/inline_gap");
        assertAttributeEquals(pendingMode, "style", "@style/Widget.BinanceMonitor.Subject.SegmentedOption");
        assertAttributeEquals(pendingMode, "android:layout_height", "@dimen/subject_height_compact");

        assertAttributeEquals(globalStatus, "style", "@style/Widget.BinanceMonitor.Subject.ActionButton.Secondary");
        assertAttributeEquals(globalStatus, "android:layout_height", "@dimen/subject_height_compact");
        assertAttributeEquals(
                globalStatus,
                "app:layout_constraintStart_toEndOf",
                "@+id/layoutChartModeToggleGroup"
        );
        assertAttributeMissing(globalStatus, "android:layout_marginStart");

        assertEquals("com.binance.monitor.ui.chart.KlineChartView", klineChartView.getTagName());
        assertAttributeEquals(positionSummary, "android:text", "盈亏：-- | 持仓：--");
        assertAttributeEquals(
                positionSummary,
                "android:textAppearance",
                "@style/TextAppearance.BinanceMonitor.ChartCompact"
        );
        assertAttributeEquals(positionSummary, "android:gravity", "start");
        assertAttributeEquals(positionSummary, "android:textAlignment", "viewStart");

        assertAttributeStartsWith(quickTradeBar, "android:paddingStart", "@dimen/");
        assertAttributeStartsWith(quickTradeBar, "android:paddingEnd", "@dimen/");
        assertAttributeEquals(
                quickTradePrimary,
                "style",
                "@style/Widget.BinanceMonitor.Subject.ActionButton.Secondary"
        );
        assertAttributeEquals(quickTradePrimary, "android:layout_width", "0dp");
        assertAttributeEquals(quickTradePrimary, "android:layout_weight", "1");
        assertAttributeEquals(quickTradeSecondary, "android:layout_width", "0dp");
        assertAttributeEquals(quickTradeSecondary, "android:layout_weight", "1");
        assertAttributeEquals(quickTradeInput, "android:layout_height", "@dimen/subject_height_md");
        assertAttributeEquals(quickTradeInput, "android:layout_marginStart", "@dimen/inline_gap");
        assertAttributeEquals(quickTradeInput, "android:layout_marginEnd", "@dimen/inline_gap");
        assertAttributeMissing(quickTradeInput, "android:background");
        assertAttributeMissing(quickTradeInput, "android:paddingHorizontal");
        assertAttributeMissing(quickTradeInput, "android:textColor");
        assertAttributeMissing(quickTradeInput, "android:textAppearance");

        assertTrue(indexOfChildWithId(overlayGroup, "btnToggleHistoryTrades") <
                indexOfChildWithId(overlayGroup, "btnTogglePositionOverlays"));

        assertFalse(hasViewId(document, "recyclerChartOverview"));
        assertFalse(hasViewId(document, "recyclerChartPositionByProduct"));
        assertFalse(hasViewId(document, "recyclerChartPositions"));
        assertFalse(hasViewId(document, "recyclerChartPendingOrders"));
        assertFalse(hasViewId(document, "spinnerChartPositionSort"));
        assertFalse(hasViewId(document, "tvChartPositionSortLabel"));
        assertFalse(hasViewId(document, "layoutChartLegacyCompat"));
        assertFalse(hasViewId(document, "tvChartInfo"));
        assertFalse(hasViewId(document, "tvChartRefreshCountdown"));
        assertFalse(hasViewId(document, "cardChartPositions"));
        assertFalse(hasViewId(document, "cardChartTradeActions"));
        assertFalse(hasViewId(document, "cardChartRiskBanner"));
        assertFalse(hasViewId(document, "btnChartRiskAction"));
        assertFalse(hasViewId(document, "tvChartRiskBanner"));
        assertFalse(hasViewId(document, "tvChartRiskMeta"));
        assertFalse(hasViewId(document, "tvChartPositionTitle"));
        assertFalse(hasViewId(document, "tvChartOverlayMeta"));
        assertFalse(hasViewId(document, "tvChartAbnormalSummary"));
    }

    // 校验图表信息文本全部走共享文本样式，而不是各自写死字号。
    @Test
    public void chartPriceInfoTextShouldUseSharedTextAppearanceInsteadOfDimenTextSize() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        Document document = parseXml(layoutPath);

        assertTextAppearanceWithoutTextSize(
                requireElementById(document, "tvChartPositionSummary"),
                "@style/TextAppearance.BinanceMonitor.ChartCompact"
        );
        assertTextAppearanceWithoutTextSize(
                requireElementById(document, "tvChartLoading"),
                "@style/TextAppearance.BinanceMonitor.Meta"
        );
        assertTextAppearanceWithoutTextSize(
                requireElementById(document, "tvChartState"),
                "@style/TextAppearance.BinanceMonitor.Caption"
        );
        assertTextAppearanceWithoutTextSize(
                requireElementById(document, "tvError"),
                "@style/TextAppearance.BinanceMonitor.Caption"
        );
    }

    // 校验图表区通过权重占满剩余高度，而不是靠固定高度或滚动容器兜底。
    @Test
    public void activityMarketChartShouldUseRemainingHeightForChartAreaWithoutBottomGap() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        Document document = parseXml(layoutPath);

        Element chartPanel = requireElementById(document, "cardChartPanel");
        Element klineChartView = requireElementById(document, "klineChartView");
        Element chartViewport = requireParentElement(klineChartView);

        assertAttributeEquals(chartPanel, "android:layout_width", "match_parent");
        assertAttributeEquals(chartPanel, "android:layout_height", "0dp");
        assertAttributeEquals(chartPanel, "android:layout_weight", "1");
        assertEquals("FrameLayout", chartViewport.getTagName());
        assertAttributeEquals(chartViewport, "android:layout_width", "match_parent");
        assertAttributeEquals(chartViewport, "android:layout_height", "0dp");
        assertAttributeEquals(chartViewport, "android:layout_weight", "1");
        assertAttributeStartsWith(chartViewport, "android:layout_marginTop", "@dimen/");
        assertAttributeEquals(klineChartView, "android:layout_width", "match_parent");
        assertAttributeEquals(klineChartView, "android:layout_height", "match_parent");
        assertFalse(hasTag(document, "NestedScrollView"));
        assertFalse(hasTag(document, "androidx.core.widget.NestedScrollView"));
    }

    // 校验周期条、指标条和图层开关都绑定到各自的具体控件，而不是依赖整段 XML 文本。
    @Test
    public void chartStripsShouldShareFullWidthSegmentedModuleStyle() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        Document document = parseXml(layoutPath);

        Element chartPanel = requireElementById(document, "cardChartPanel");
        Element intervalStrip = requireElementById(document, "layoutIntervalStrip");
        Element intervalTabs = requireElementById(document, "layoutIntervalTabs");
        Element intervalFirst = requireElementById(document, "btnInterval1m");
        Element intervalLast = requireElementById(document, "btnInterval1y");
        Element interval1h = requireElementById(document, "btnInterval1h");
        Element interval4h = requireElementById(document, "btnInterval4h");
        Element indicatorStrip = requireElementById(document, "layoutIndicatorStrip");
        Element indicatorTabs = requireElementById(document, "layoutIndicatorTabs");
        Element indicatorButton = requireElementById(document, "btnIndicatorVolume");
        Element lastIndicatorButton = requireElementById(document, "btnIndicatorKdj");
        Element chartState = requireElementById(document, "tvChartState");
        Element overlayGroup = requireElementById(document, "layoutChartOverlayToggleGroup");
        Element historyToggle = requireElementById(document, "btnToggleHistoryTrades");
        Element positionToggle = requireElementById(document, "btnTogglePositionOverlays");

        assertAttributeMissing(chartPanel, "android:paddingStart");
        assertAttributeMissing(chartPanel, "android:paddingEnd");
        assertEquals("LinearLayout", intervalStrip.getTagName());
        assertAttributeMissing(intervalTabs, "android:paddingStart");
        assertAttributeMissing(intervalTabs, "android:paddingEnd");
        assertAttributeEquals(intervalFirst, "android:layout_width", "0dp");
        assertAttributeEquals(intervalFirst, "android:layout_weight", "1");
        assertAttributeEquals(intervalLast, "android:layout_width", "0dp");
        assertAttributeEquals(intervalLast, "android:layout_weight", "1");
        assertAttributeEquals(interval1h, "android:text", "1时");
        assertAttributeEquals(interval4h, "android:text", "4时");

        assertEquals("HorizontalScrollView", indicatorStrip.getTagName());
        assertAttributeEquals(indicatorStrip, "android:background", "@android:color/transparent");
        assertAttributeMissing(indicatorTabs, "android:paddingStart");
        assertAttributeMissing(indicatorTabs, "android:paddingEnd");
        assertAttributeEquals(indicatorButton, "style", "@style/Widget.BinanceMonitor.Subject.SegmentedOption");
        assertAttributeStartsWith(indicatorButton, "android:layout_marginEnd", "@dimen/");
        assertAttributeStartsWith(indicatorButton, "android:paddingStart", "@dimen/");
        assertAttributeStartsWith(indicatorButton, "android:paddingEnd", "@dimen/");
        assertAttributeMissing(lastIndicatorButton, "android:layout_marginEnd");

        assertAttributeEquals(overlayGroup, "android:background", "@android:color/transparent");
        assertAttributeStartsWith(historyToggle, "android:layout_marginEnd", "@dimen/");
        assertAttributeEquals(historyToggle, "android:background", "@android:color/transparent");
        assertAttributeEquals(positionToggle, "android:background", "@android:color/transparent");
        assertAttributeEquals(chartState, "android:layout_width", "0dp");
        assertAttributeEquals(chartState, "android:layout_weight", "1");
        assertAttributeEquals(chartState, "android:maxLines", "1");
        assertAttributeEquals(chartState, "android:ellipsize", "end");
    }

    // 校验细调间距都来自 dimen 引用，而不是直接写死在目标控件上。
    @Test
    public void chartFineTuneSpacingShouldUseDimenReferencesInsteadOfHardcodedDp() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        Document document = parseXml(layoutPath);

        Element indicatorButton = requireElementById(document, "btnIndicatorVolume");
        Element historyToggle = requireElementById(document, "btnToggleHistoryTrades");
        Element quickTradeInput = requireElementById(document, "etQuickTradeVolume");

        assertAttributeStartsWith(indicatorButton, "android:layout_marginEnd", "@dimen/");
        assertAttributeStartsWith(indicatorButton, "android:paddingStart", "@dimen/");
        assertAttributeStartsWith(indicatorButton, "android:paddingEnd", "@dimen/");
        assertAttributeStartsWith(historyToggle, "android:layout_marginEnd", "@dimen/");
        assertAttributeStartsWith(quickTradeInput, "android:layout_marginStart", "@dimen/");
        assertAttributeStartsWith(quickTradeInput, "android:layout_marginEnd", "@dimen/");
    }

    // 解析布局文件路径，兼容仓库根目录和模块目录两种运行位置。
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

    // 解析 XML 为 DOM，便于按控件和属性做精确断言。
    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    // 按控件 id 获取元素，不存在时直接报出缺失控件。
    private static Element requireElementById(Document document, String viewId) {
        Element element = findElementById(document, viewId);
        if (element == null) {
            throw new IllegalStateException("未找到控件: " + viewId);
        }
        return element;
    }

    // 判断当前布局里是否还存在指定控件 id。
    private static boolean hasViewId(Document document, String viewId) {
        return findElementById(document, viewId) != null;
    }

    // 在 DOM 里查找指定 id 的元素。
    private static Element findElementById(Document document, String viewId) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            String rawId = element.getAttribute("android:id");
            if (rawId == null || rawId.isEmpty()) {
                continue;
            }
            int separatorIndex = rawId.indexOf('/');
            String shortId = separatorIndex >= 0 ? rawId.substring(separatorIndex + 1) : rawId;
            if (viewId.equals(shortId)) {
                return element;
            }
        }
        return null;
    }

    // 校验某个属性必须等于指定值。
    private static void assertAttributeEquals(Element element, String attributeName, String expectedValue) {
        assertEquals(describeElement(element) + " 的 " + attributeName + " 不符合预期",
                expectedValue,
                element.getAttribute(attributeName));
    }

    // 校验某个属性必须引用指定前缀，避免把断言绑死到 XML 排版上。
    private static void assertAttributeStartsWith(
            Element element,
            String attributeName,
            String expectedPrefix
    ) {
        String actualValue = element.getAttribute(attributeName);
        assertTrue(
                describeElement(element) + " 的 " + attributeName + " 应以 " + expectedPrefix + " 开头，实际为 "
                        + actualValue,
                actualValue.startsWith(expectedPrefix)
        );
    }

    // 校验目标控件没有再次写回本应由样式承接的属性。
    private static void assertAttributeMissing(Element element, String attributeName) {
        assertFalse(
                describeElement(element) + " 不应直接声明 " + attributeName,
                element.hasAttribute(attributeName)
        );
    }

    // 校验文本控件走共享样式，同时没有再额外写死字号。
    private static void assertTextAppearanceWithoutTextSize(Element element, String expectedTextAppearance) {
        assertAttributeEquals(element, "android:textAppearance", expectedTextAppearance);
        assertAttributeMissing(element, "android:textSize");
    }

    // 获取父容器元素，便于校验图表视口容器的尺寸策略。
    private static Element requireParentElement(Element element) {
        Node parentNode = element.getParentNode();
        if (!(parentNode instanceof Element)) {
            throw new IllegalStateException("未找到父容器: " + describeElement(element));
        }
        return (Element) parentNode;
    }

    // 判断布局里是否存在某种标签，避免误引入滚动容器。
    private static boolean hasTag(Document document, String tagName) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (tagName.equals(element.getTagName())) {
                return true;
            }
        }
        return false;
    }

    // 返回子控件在父容器中的顺序位置，用于校验覆盖开关的排列关系。
    private static int indexOfChildWithId(Element parent, String childViewId) {
        int elementIndex = 0;
        NodeList childNodes = parent.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);
            if (!(childNode instanceof Element)) {
                continue;
            }
            Element childElement = (Element) childNode;
            String rawId = childElement.getAttribute("android:id");
            if (!rawId.isEmpty() && rawId.endsWith("/" + childViewId)) {
                return elementIndex;
            }
            elementIndex++;
        }
        throw new IllegalStateException("父容器中未找到子控件: " + childViewId);
    }

    // 生成控件描述，便于测试失败时直接定位到具体元素。
    private static String describeElement(Element element) {
        String rawId = element.getAttribute("android:id");
        if (rawId == null || rawId.isEmpty()) {
            return "<" + element.getTagName() + ">";
        }
        return rawId;
    }
}
