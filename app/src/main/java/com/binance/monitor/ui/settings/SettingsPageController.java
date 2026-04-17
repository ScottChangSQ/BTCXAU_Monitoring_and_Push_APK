/*
 * 设置页共享控制器，统一承接设置首页目录、主题和底部导航。
 * 旧 Activity 与主壳 Fragment 共用这一套页面实现。
 */
package com.binance.monitor.ui.settings;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ContentSettingsBinding;
import com.binance.monitor.ui.theme.UiPaletteManager;

public final class SettingsPageController {

    private final Host host;
    private final ContentSettingsBinding binding;

    public SettingsPageController(@NonNull Host host,
                                  @NonNull ContentSettingsBinding binding) {
        this.host = host;
        this.binding = binding;
    }

    // 绑定设置首页入口。
    public void bind() {
        setupEntries();
    }

    // 页面进入可见态时刷新主题。
    public void onPageShown() {
        applyPaletteStyles();
    }

    public void onPageHidden() {
    }

    public void onDestroy() {
    }

    // 绑定首页分类入口。
    private void setupEntries() {
        binding.itemDisplay.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_DISPLAY, "悬浮窗与显示"));
        binding.itemGateway.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_GATEWAY, "正式入口"));
        binding.itemTheme.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_THEME, "主题设置"));
        binding.itemCache.setOnClickListener(v -> host.openSettingsSection(SettingsActivity.SECTION_CACHE, "缓存管理"));
        binding.itemLogs.setOnClickListener(v -> host.openLogPage());
    }

    // 应用当前主题色到首页目录。
    private void applyPaletteStyles() {
        AppCompatActivity activity = host.requireActivity();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(activity, palette);
        styleEntry(binding.itemDisplay, palette);
        styleEntry(binding.itemGateway, palette);
        styleEntry(binding.itemTheme, palette);
        styleEntry(binding.itemCache, palette);
        styleEntry(binding.itemLogs, palette);
    }

    // 统一绘制设置入口样式。
    private void styleEntry(@Nullable View view, @NonNull UiPaletteManager.Palette palette) {
        if (view == null) {
            return;
        }
        view.setBackground(UiPaletteManager.createListRowBackground(host.requireActivity(), palette.surfaceEnd, palette.stroke));
    }

    public interface Host {
        @NonNull
        AppCompatActivity requireActivity();

        void openSettingsSection(@NonNull String section, @NonNull String title);

        void openLogPage();
    }
}
