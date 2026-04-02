/*
 * 启动图标资源测试，确保安装包默认图标和主题别名图标链路统一指向新的图标资源。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

public class LauncherIconResourceTest {

    // 校验 AndroidManifest 中的应用图标与主题别名图标都挂到正确的资源入口。
    @Test
    public void manifestShouldBindLauncherIconsToExpectedResources() throws Exception {
        Document document = parseXml(resolveProjectFile(
                "app/src/main/AndroidManifest.xml",
                "src/main/AndroidManifest.xml"
        ));
        org.w3c.dom.Element application = (org.w3c.dom.Element) document.getElementsByTagName("application").item(0);
        assertEquals("@mipmap/ic_launcher", application.getAttribute("android:icon"));
        assertEquals("@mipmap/ic_launcher_round", application.getAttribute("android:roundIcon"));

        Map<String, String> expectedIcons = new LinkedHashMap<>();
        expectedIcons.put(".launcher.IconFinancialAlias", "@drawable/ic_launcher_theme_financial");
        expectedIcons.put(".launcher.IconVintageAlias", "@drawable/ic_launcher_theme_vintage");
        expectedIcons.put(".launcher.IconBinanceAlias", "@drawable/ic_launcher_theme_binance");
        expectedIcons.put(".launcher.IconTradingViewAlias", "@drawable/ic_launcher_theme_tradingview");
        expectedIcons.put(".launcher.IconLightAlias", "@drawable/ic_launcher_theme_light");

        NodeList aliases = document.getElementsByTagName("activity-alias");
        for (int i = 0; i < aliases.getLength(); i++) {
            org.w3c.dom.Element alias = (org.w3c.dom.Element) aliases.item(i);
            String name = alias.getAttribute("android:name");
            if (!expectedIcons.containsKey(name)) {
                continue;
            }
            String expectedIcon = expectedIcons.get(name);
            assertEquals(name + " 的桌面图标资源不正确", expectedIcon, alias.getAttribute("android:icon"));
            assertEquals(name + " 的圆形桌面图标资源不正确", expectedIcon, alias.getAttribute("android:roundIcon"));
            expectedIcons.remove(name);
        }
        assertEquals("所有主题别名都应该被校验到", 0, expectedIcons.size());
    }

    // 校验默认 launcher 资源是否已经切到默认主题的新图标，避免安装包仍打进旧图标。
    @Test
    public void launcherMipmapsShouldMatchDefaultFinancialIcon() throws Exception {
        String financial = readUtf8(resolveProjectFile(
                "app/src/main/res/drawable/ic_launcher_theme_financial.xml",
                "src/main/res/drawable/ic_launcher_theme_financial.xml"
        ));
        assertEquals(
                financial,
                readUtf8(resolveProjectFile(
                        "app/src/main/res/mipmap/ic_launcher.xml",
                        "src/main/res/mipmap/ic_launcher.xml"
                ))
        );
        assertEquals(
                financial,
                readUtf8(resolveProjectFile(
                        "app/src/main/res/mipmap/ic_launcher_round.xml",
                        "src/main/res/mipmap/ic_launcher_round.xml"
                ))
        );
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

    // 解析 XML 文档，供清单断言复用。
    private static Document parseXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    // 读取 UTF-8 文本并去掉首尾空白，避免换行差异影响断言。
    private static String readUtf8(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
    }
}
