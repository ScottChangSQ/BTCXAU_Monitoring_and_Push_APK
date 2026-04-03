/*
 * 设置页源码约束测试，确保清理运行时缓存时不会误删异常记录历史。
 */
package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SettingsSectionActivitySourceTest {

    @Test
    public void clearCacheShouldNotDeleteAbnormalRecordsHistory() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java"
        );
        assertFalse("清理缓存时不应再直接清空异常记录历史",
                source.contains("AbnormalRecordManager.getInstance(getApplicationContext()).clearAll();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 SettingsSectionActivity.java");
    }
}
