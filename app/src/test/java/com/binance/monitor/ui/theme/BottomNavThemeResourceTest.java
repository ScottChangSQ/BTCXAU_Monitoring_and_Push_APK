/*
 * 底部导航主题资源测试，确保主要页面不再直接绑定固定风格的旧导航背景资源。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BottomNavThemeResourceTest {

    // 校验底部导航不再把旧的固定背景资源写死在布局里，统一交给运行时主题入口处理。
    @Test
    public void bottomNavLayoutsShouldNotReferenceLegacyFixedBackgrounds() throws Exception {
        assertLayoutDoesNotContainLegacyBackground("app/src/main/res/layout/activity_main.xml");
        assertLayoutDoesNotContainLegacyBackground("app/src/main/res/layout/activity_market_chart.xml");
        assertLayoutDoesNotContainLegacyBackground("app/src/main/res/layout/activity_account_stats.xml");
        assertLayoutDoesNotContainLegacyBackground("app/src/main/res/layout/activity_settings.xml");
        assertLayoutDoesNotContainLegacyBackground("app/src/main/res/layout/activity_settings_detail.xml");
    }

    // 读取单个布局并断言旧导航资源不再出现。
    private static void assertLayoutDoesNotContainLegacyBackground(String relativePath) throws Exception {
        String xml = readUtf8(relativePath, relativePath.replace("app/", ""));
        assertFalse(relativePath + " 仍引用 bg_bottom_nav，应改为运行时主题绘制",
                xml.contains("@drawable/bg_bottom_nav"));
        assertFalse(relativePath + " 仍引用 bg_tab_wechat_selected，应改为运行时主题绘制",
                xml.contains("@drawable/bg_tab_wechat_selected"));
        assertFalse(relativePath + " 仍引用 bg_tab_wechat_unselected，应改为运行时主题绘制",
                xml.contains("@drawable/bg_tab_wechat_unselected"));
    }

    // 按 UTF-8 读取项目布局资源，兼容从仓库根目录执行测试。
    private static String readUtf8(String... relativePaths) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String relativePath : relativePaths) {
            Path path = workingDir.resolve(relativePath).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到布局文件");
    }
}
