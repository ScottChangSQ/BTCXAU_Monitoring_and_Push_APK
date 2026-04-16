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

    // 历史分析和收益统计应落在账户统计页底部，避免打断交易记录与交易统计的阅读顺序。
    @Test
    public void activityAccountStatsShouldMoveCurveAndReturnSectionsToBottomAtStartup() throws Exception {
        Path sourceFile = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
        String source = new String(Files.readAllBytes(sourceFile), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        int onCreateStart = source.indexOf("protected void onCreate(@Nullable Bundle savedInstanceState) {");
        int onResumeStart = source.indexOf("@Override\n    protected void onResume()");
        String onCreateMethod = source.substring(onCreateStart, onResumeStart);

        assertTrue("onCreate 应调用底部重排逻辑", onCreateMethod.contains("placeCurveSectionToBottom();"));
        assertTrue("应保留历史分析卡片重排方法", source.contains("private void placeCurveSectionToBottom() {"));
        assertTrue("重排方法应先移除历史分析卡片", source.contains("container.removeView(binding.cardCurveSection);"));
        assertTrue("重排方法应把历史分析卡片重新追加到底部", source.contains("container.addView(binding.cardCurveSection);"));
        assertTrue("重排方法应把收益统计卡片跟随追加到底部", source.contains("container.addView(returnStatsSection);"));
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
