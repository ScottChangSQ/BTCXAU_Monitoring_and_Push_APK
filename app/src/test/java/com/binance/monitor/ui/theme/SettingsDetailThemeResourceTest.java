/*
 * 设置详情页主题资源测试，确保主题预览和操作按钮不再直接绑旧固定背景资源。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsDetailThemeResourceTest {

    @Test
    public void settingsDetailLayoutShouldNotReferenceLegacyThemeBackgrounds() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_settings_detail.xml",
                "src/main/res/layout/activity_settings_detail.xml"
        );
        assertFalse("设置详情页仍引用 bg_inline_button，应改为运行时主题绘制",
                xml.contains("@drawable/bg_inline_button"));
        assertFalse("设置详情页仍引用 bg_overlay_mini，应改为运行时主题绘制",
                xml.contains("@drawable/bg_overlay_mini"));
    }

    @Test
    public void settingsDetailLayoutShouldNotKeepDuplicatedLogEntryButton() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_settings_detail.xml",
                "src/main/res/layout/activity_settings_detail.xml"
        );
        assertFalse("设置详情页不应再保留重复的查看日志按钮",
                xml.contains("android:id=\"@+id/btnViewLogs\""));
    }

    @Test
    public void settingsDetailLayoutShouldNotKeepLegacyTabManagementSwitches() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_settings_detail.xml",
                "src/main/res/layout/activity_settings_detail.xml"
        );
        assertFalse("设置详情页不应再保留旧账户持仓开关",
                xml.contains("android:id=\"@+id/switchTabAccountPosition\""));
        assertFalse("设置详情页不应再保留旧 Tab 管理标题",
                xml.contains("android:text=\"Tab 页管理\""));
    }

    @Test
    public void settingsDetailLayoutShouldUseStandardSubjectsForLowFrequencyControls() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_settings_detail.xml",
                "src/main/res/layout/activity_settings_detail.xml"
        );
        assertTrue("设置详情页的布尔项应使用 ToggleChoice 样式",
                xml.contains("@style/Widget.BinanceMonitor.Subject.ToggleChoice"));
        assertTrue("设置详情页的输入项应使用 InputField 样式",
                xml.contains("@style/Widget.BinanceMonitor.Subject.InputField"));
        assertTrue("设置详情页的清缓存按钮应使用 Secondary ActionButton",
                xml.contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Secondary"));
        assertTrue("设置详情页的保存按钮应使用 Primary ActionButton",
                xml.contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Primary"));
        assertFalse("设置详情页不应继续使用透明背景伪装按钮",
                xml.contains("@android:color/transparent"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到设置详情页布局文件");
    }
}
