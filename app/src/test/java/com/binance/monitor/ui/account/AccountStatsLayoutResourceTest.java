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
}
