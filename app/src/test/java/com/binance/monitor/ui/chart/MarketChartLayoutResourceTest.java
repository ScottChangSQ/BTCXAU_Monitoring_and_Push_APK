package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Document;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilderFactory;

public class MarketChartLayoutResourceTest {

    @Test
    public void activityMarketChartShouldPlacePositionSortControlBesideDetailTitle() throws Exception {
        Document document = parseXml(resolveProjectFile(
                "app/src/main/res/layout/activity_market_chart.xml",
                "src/main/res/layout/activity_market_chart.xml"
        ));

        org.w3c.dom.Element sortLabel = findElementById(document, "tvChartPositionSortLabel");
        org.w3c.dom.Element sortSpinner = findElementById(document, "spinnerChartPositionSort");

        assertEquals("center_vertical|end", sortLabel.getAttribute("android:gravity"));
        assertEquals("@style/TextAppearance.BinanceMonitor.SectionLabel", sortLabel.getAttribute("android:textAppearance"));
        assertEquals("0dp", sortLabel.getAttribute("android:padding"));
        assertEquals("wrap_content", sortSpinner.getAttribute("android:layout_width"));
        assertEquals("@android:color/transparent", sortSpinner.getAttribute("android:background"));
        assertEquals("dropdown", sortSpinner.getAttribute("android:spinnerMode"));
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

    private static org.w3c.dom.Element findElementById(Document document, String viewId) {
        org.w3c.dom.NodeList elements = document.getElementsByTagName("*");
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
}
