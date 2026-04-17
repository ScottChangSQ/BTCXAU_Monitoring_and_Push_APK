/*
 * 设置首页 Activity，负责承接状态弹层进入后的设置目录页。
 * 通过 SettingsPageController 复用设置首页的目录和跳转逻辑。
 */
package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ContentSettingsBinding;
import com.binance.monitor.ui.log.LogActivity;

public class SettingsActivity extends AppCompatActivity {

    public static final String SECTION_DISPLAY = "display";
    public static final String SECTION_GATEWAY = "gateway";
    public static final String SECTION_THEME = "theme";
    public static final String SECTION_CACHE = "cache";
    private ContentSettingsBinding binding;
    private SettingsPageController pageController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ContentSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        pageController = new SettingsPageController(new SettingsPageController.Host() {
            @Override
            public AppCompatActivity requireActivity() {
                return SettingsActivity.this;
            }

            @Override
            public void openSettingsSection(@androidx.annotation.NonNull String section, @androidx.annotation.NonNull String title) {
                Intent intent = new Intent(SettingsActivity.this, SettingsSectionActivity.class);
                intent.putExtra(SettingsSectionActivity.EXTRA_SECTION, section);
                intent.putExtra(SettingsSectionActivity.EXTRA_TITLE, title);
                startActivity(intent);
            }

            @Override
            public void openLogPage() {
                startActivity(new Intent(SettingsActivity.this, LogActivity.class));
            }
        }, binding);
        pageController.bind();
        pageController.onPageShown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pageController != null) {
            pageController.onPageShown();
        }
    }

    @Override
    protected void onDestroy() {
        if (pageController != null) {
            pageController.onDestroy();
            pageController = null;
        }
        super.onDestroy();
    }
}
