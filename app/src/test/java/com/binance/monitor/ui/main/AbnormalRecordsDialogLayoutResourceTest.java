package com.binance.monitor.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.parsers.DocumentBuilderFactory;

public class AbnormalRecordsDialogLayoutResourceTest {

    private Document document;
    private Document itemDocument;

    @Before
    public void setUp() throws Exception {
        document = parseXml(
                "app/src/main/res/layout/dialog_abnormal_records.xml",
                "src/main/res/layout/dialog_abnormal_records.xml"
        );
        itemDocument = parseXml(
                "app/src/main/res/layout/item_abnormal_record.xml",
                "src/main/res/layout/item_abnormal_record.xml"
        );
    }

    @Test
    public void dialogLayoutShouldUseMaterialCardRoot() {
        assertEquals("com.google.android.material.card.MaterialCardView",
                document.getDocumentElement().getTagName());
    }

    @Test
    public void dialogLayoutShouldContainInDialogTitleAndSubtitle() {
        assertEquals("异常详细记录", findElementById("tvAbnormalDialogTitle").getAttribute("android:text"));
        assertEquals("最多显示 500 条，可按产品、时间、触发条件筛选",
                findElementById("tvAbnormalDialogSubtitle").getAttribute("android:text"));
    }

    @Test
    public void dialogAndItemCardsShouldExplicitlyUseSquareBorderlessContract() {
        org.w3c.dom.Element dialogRoot = document.getDocumentElement();
        org.w3c.dom.Element itemRoot = itemDocument.getDocumentElement();

        assertEquals("", dialogRoot.getAttribute("app:cardCornerRadius"));
        assertEquals("", dialogRoot.getAttribute("app:strokeWidth"));
        assertEquals("", dialogRoot.getAttribute("app:strokeColor"));
        assertEquals("", dialogRoot.getAttribute("app:cardBackgroundColor"));

        assertEquals("", itemRoot.getAttribute("app:cardCornerRadius"));
        assertEquals("", itemRoot.getAttribute("app:strokeWidth"));
        assertEquals("", itemRoot.getAttribute("app:strokeColor"));
        assertEquals("", itemRoot.getAttribute("app:cardBackgroundColor"));
    }

    @Test
    public void dialogShouldKeepOuterSpacingDrivenBySharedModuleTokens() {
        org.w3c.dom.Element dialogRoot = document.getDocumentElement();
        assertEquals("@dimen/screen_edge_padding", dialogRoot.getAttribute("android:layout_marginHorizontal"));
        assertTrue(findElementById("inputLayoutAbnormalSearch")
                .getAttribute("android:layout_marginTop")
                .contains("@dimen/section_gap"));
    }

    private static Document parseXml(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                return factory.newDocumentBuilder().parse(path.toFile());
            }
        }
        throw new IllegalStateException("找不到异常记录弹窗布局");
    }

    private org.w3c.dom.Element findElementById(String viewId) {
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
