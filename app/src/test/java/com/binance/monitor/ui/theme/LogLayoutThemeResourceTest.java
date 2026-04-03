/*
 * 日志页布局资源测试，确保日志页关键按钮不再直接绑旧的固定背景资源。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LogLayoutThemeResourceTest {

    @Test
    public void logLayoutShouldNotReferenceLegacyInlineButtonBackground() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_log.xml",
                "src/main/res/layout/activity_log.xml"
        );
        assertFalse("日志页仍引用 bg_inline_button，应改为运行时主题绘制",
                xml.contains("@drawable/bg_inline_button"));
    }

    @Test
    public void logLayoutButtonsShouldNotUseTransparentBackgrounds() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_log.xml",
                "src/main/res/layout/activity_log.xml"
        );
        assertFalse("日志页返回按钮仍是透明背景",
                xml.contains("android:id=\"@+id/btnBack\"")
                        && xml.contains("android:background=\"@android:color/transparent\""));
        assertFalse("日志页全选按钮仍是透明背景",
                xml.contains("android:id=\"@+id/btnSelectAll\"")
                        && xml.contains("android:background=\"@android:color/transparent\""));
        assertFalse("日志页删除按钮仍是透明背景",
                xml.contains("android:id=\"@+id/btnDeleteSelected\"")
                        && xml.contains("android:background=\"@android:color/transparent\""));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到日志页布局文件");
    }
}
