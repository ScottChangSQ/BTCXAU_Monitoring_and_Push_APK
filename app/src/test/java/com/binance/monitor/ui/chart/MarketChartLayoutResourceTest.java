package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;

public class MarketChartLayoutResourceTest {

    @Test
    public void activityMarketChartShouldKeepOnlyLightweightOverlayViews() throws Exception {
        Document document = parseXml(resolveProjectFile(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        ));

        assertTrue(hasViewId(document, "klineChartView"));
        assertTrue(hasViewId(document, "tvChartPositionTitle"));
        assertTrue(hasViewId(document, "tvChartPositionSummary"));
        assertTrue(hasViewId(document, "tvChartOverlayMeta"));
        assertTrue(hasViewId(document, "btnChartTradeBuy"));
        assertTrue(hasViewId(document, "btnChartTradeSell"));
        assertTrue(hasViewId(document, "btnChartTradePending"));

        assertFalse(hasViewId(document, "recyclerChartOverview"));
        assertFalse(hasViewId(document, "recyclerChartPositionByProduct"));
        assertFalse(hasViewId(document, "recyclerChartPositions"));
        assertFalse(hasViewId(document, "recyclerChartPendingOrders"));
        assertFalse(hasViewId(document, "spinnerChartPositionSort"));
        assertFalse(hasViewId(document, "tvChartPositionSortLabel"));
        assertFalse(hasViewId(document, "layoutChartLegacyCompat"));
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
