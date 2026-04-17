/*
 * 设置页源码约束测试，确保清理运行时缓存时不会误删异常记录历史。
 */
package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void gatewaySectionShouldExposeCanonicalEntryAsReadOnly() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java"
        );

        assertTrue("设置页应把固定入口写回展示框",
                source.contains("binding.etMt5GatewayUrl.setText(AppConstants.MT5_GATEWAY_BASE_URL);"));
        assertTrue("设置页不应再允许手工编辑入口",
                source.contains("binding.etMt5GatewayUrl.setEnabled(false);"));
        assertTrue("设置页不应再显示保存入口按钮",
                source.contains("binding.btnSaveMt5GatewayUrl.setVisibility(View.GONE);"));
        assertFalse("设置页不应继续按用户输入保存入口",
                source.contains("viewModel.setMt5GatewayBaseUrl(input);"));
    }

    @Test
    public void clearCacheShouldRunOnBackgroundExecutorInsteadOfBlockingUiThread() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("设置页应提供独立缓存清理执行器",
                source.contains("private java.util.concurrent.ExecutorService cacheExecutor;"));
        assertTrue("页面初始化时应创建缓存清理执行器",
                source.contains("cacheExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();"));
        assertTrue("点击清理后应切到后台执行器，而不是主线程直接删库删文件",
                source.contains(".setPositiveButton(\"清理\", (dialogInterface, which) -> clearCacheDataAsync("));
        assertTrue("后台清理完成后应切回主线程展示结果",
                source.contains("runOnUiThread(() -> Toast.makeText(this,"));
        assertTrue("页面销毁时应关闭缓存清理执行器",
                source.contains("if (cacheExecutor != null) {")
                        && source.contains("cacheExecutor.shutdownNow();"));
    }

    @Test
    public void settingsSectionShouldNotKeepLegacyTabManagement() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertFalse("设置二级页不应再保留旧 Tab 管理区块",
                source.contains("binding.switchTabAccountPosition.setOnCheckedChangeListener"));
        assertFalse("设置二级页不应再回显旧 Tab 可见性开关",
                source.contains("binding.switchTabAccountPosition.setChecked(viewModel.isTabAccountPositionVisible());"));
        assertFalse("设置二级页不应再按旧一级 Tab 做显示控制",
                source.contains("SettingsActivity.SECTION_TAB"));
    }

    @Test
    public void settingsShouldRemoveThemeSectionAndThemeEntry() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java"
        );
        String layout = readUtf8(
                "app/src/main/res/layout/activity_settings_detail.xml",
                "src/main/res/layout/activity_settings_detail.xml"
        );
        String settingsPage = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java"
        );
        String settingsHomeLayout = readUtf8(
                "app/src/main/res/layout/content_settings.xml",
                "src/main/res/layout/content_settings.xml"
        );
        String settingsActivity = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java",
                "src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java"
        );

        assertFalse("设置二级页不应再保留主题卡片切换逻辑",
                source.contains("selectTheme("));
        assertFalse("设置二级页不应再保留主题模块背景或可见性控制",
                source.contains("cardThemeSection"));
        assertFalse("设置目录控制器不应再保留主题入口",
                settingsPage.contains("binding.itemTheme"));
        assertFalse("设置首页布局不应再保留主题设置入口",
                settingsHomeLayout.contains("android:id=\"@+id/itemTheme\""));
        assertFalse("设置详情布局不应再保留主题设置区块",
                layout.contains("android:text=\"主题设置\""));
        assertFalse("设置详情布局不应再保留金融专业风主题卡片",
                layout.contains("android:id=\"@+id/cardThemeFinancial\""));
        assertFalse("设置详情布局不应再保留 Notion Data Desk 主题卡片",
                layout.contains("android:id=\"@+id/cardThemeNotion\""));
        assertFalse("设置 Activity 不应再保留主题 section 常量",
                settingsActivity.contains("SECTION_THEME"));
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
