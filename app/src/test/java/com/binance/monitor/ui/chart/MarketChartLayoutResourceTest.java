package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;

public class MarketChartLayoutResourceTest {

    @Test
    public void activityMarketChartShouldKeepOnlyLightweightOverlayViews() throws Exception {
        Path layoutPath = resolveProjectFile(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        );
        Document document = parseXml(layoutPath);
        String xml = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);

        assertTrue(hasViewId(document, "klineChartView"));
        assertTrue(hasViewId(document, "tvChartPositionSummary"));
        assertTrue(xml.contains("android:id=\"@+id/tvChartPositionSummary\""));
        assertTrue(xml.contains("android:text=\"盈亏：-- | 持仓：--\""));
        assertTrue(xml.contains("android:textSize=\"@dimen/chart_price_info_text_size\""));
        assertTrue(xml.contains("android:gravity=\"end\""));
        assertTrue(xml.contains("android:textAlignment=\"viewEnd\""));
        assertTrue(hasViewId(document, "btnChartModeMarket"));
        assertTrue(hasViewId(document, "btnChartModePending"));
        assertTrue(hasViewId(document, "layoutChartQuickTradeBar"));
        assertTrue(hasViewId(document, "btnQuickTradePrimary"));
        assertTrue(hasViewId(document, "etQuickTradeVolume"));
        assertTrue(hasViewId(document, "btnQuickTradeSecondary"));
        assertTrue(hasViewId(document, "btnToggleHistoryTrades"));
        assertTrue(hasViewId(document, "btnTogglePositionOverlays"));
        assertTrue(xml.contains("style=\"@style/Widget.BinanceMonitor.Button.SecondaryContentSquare\""));
        assertTrue(xml.contains("android:id=\"@+id/btnChartModeMarket\""));
        assertTrue(xml.contains("android:id=\"@+id/btnChartModePending\""));
        assertTrue(xml.contains("android:id=\"@+id/btnGlobalStatus\""));
        assertTrue(xml.contains("android:id=\"@+id/btnQuickTradePrimary\""));
        assertTrue(xml.contains("android:id=\"@+id/btnQuickTradeSecondary\""));
        assertTrue(xml.contains("android:id=\"@+id/layoutChartQuickTradeBar\""));
        assertTrue(xml.contains("android:paddingStart=\"3dp\""));
        assertTrue(xml.contains("android:paddingEnd=\"3dp\""));
        assertTrue(xml.contains("android:id=\"@+id/btnQuickTradePrimary\""));
        assertTrue(xml.contains("android:id=\"@+id/btnQuickTradeSecondary\""));
        assertTrue(xml.contains("android:layout_width=\"0dp\""));
        assertTrue(xml.contains("android:layout_weight=\"1\""));

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
        assertFalse(xml.contains("持仓手数: -- | 持仓盈亏: --"));
        assertTrue(xml.indexOf("@+id/btnToggleHistoryTrades") < xml.indexOf("@+id/btnTogglePositionOverlays"));
    }

    @Test
    public void chartPriceInfoTextSizeShouldComeFromSharedDimen() throws Exception {
        Path dimenPath = resolveProjectFile(
                "app/src/main/res/values/dimens.xml",
                "src/main/res/values/dimens.xml"
        );
        String xml = new String(Files.readAllBytes(dimenPath), StandardCharsets.UTF_8);

        assertTrue(xml.contains("<dimen name=\"chart_price_info_text_size\">9dp</dimen>"));
    }

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

    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static boolean hasViewId(Document document, String viewId) {
        org.w3c.dom.NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            org.w3c.dom.Element element = (org.w3c.dom.Element) elements.item(i);
            String rawId = element.getAttribute("android:id");
            if (rawId == null || rawId.isEmpty()) {
                continue;
            }
            String shortId = rawId.substring(rawId.indexOf('/') + 1);
            if (viewId.equals(shortId)) {
                return true;
            }
        }
        return false;
    }
}
