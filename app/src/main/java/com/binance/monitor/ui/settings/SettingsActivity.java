/*
 * 设置首页旧 Activity 桥接页，统一把旧入口收口到主壳设置 Tab。
 * 设置首页真实页面由 SettingsFragment 和 SettingsPageController 承担。
 */
package com.binance.monitor.ui.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;

public class SettingsActivity extends AppCompatActivity {

    public static final String SECTION_DISPLAY = "display";
    public static final String SECTION_GATEWAY = "gateway";
    public static final String SECTION_DIAGNOSTICS = "diagnostics";
    public static final String SECTION_THEME = "theme";
    public static final String SECTION_TAB = "tab";
    public static final String SECTION_CACHE = "cache";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(HostNavigationIntentFactory.forTab(this, HostTab.SETTINGS));
        overridePendingTransition(0, 0);
        finish();
    }
}
