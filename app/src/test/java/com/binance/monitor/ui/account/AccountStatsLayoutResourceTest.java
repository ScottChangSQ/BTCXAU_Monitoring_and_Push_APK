/*
 * 账户统计布局资源测试，确保关键分段按钮从 XML 初始形状就是方角，避免运行时再被圆角模型覆盖。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

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
            "btnTradePnlSell"
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
}
