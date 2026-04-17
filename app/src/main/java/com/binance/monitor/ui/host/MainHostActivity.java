/*
 * 主壳页面，统一承载底部 Tab 容器。
 */
package com.binance.monitor.ui.host;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.R;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MainHostActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET_TAB = "com.binance.monitor.ui.host.extra.TARGET_TAB";
    private static final String STATE_SELECTED_TAB = "state_selected_tab";
    private static final int REQUEST_CODE_NOTIFICATION = 100;

    private final HostTabNavigator navigator = new HostTabNavigator();
    private HostTab selectedTab = HostTab.TRADING;
    @Nullable
    private View bottomNavigationView;
    @Nullable
    private TextView tabTrading;
    @Nullable
    private TextView tabAccount;
    @Nullable
    private TextView tabAnalysis;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_host);
        if (savedInstanceState != null) {
            selectedTab = HostTab.fromKey(savedInstanceState.getString(STATE_SELECTED_TAB));
        } else {
            selectedTab = HostTab.fromKey(getIntent().getStringExtra(EXTRA_TARGET_TAB));
        }
        bottomNavigationView = findViewById(R.id.hostBottomNavigation);
        tabTrading = findViewById(R.id.tabTrading);
        tabAccount = findViewById(R.id.tabAccount);
        tabAnalysis = findViewById(R.id.tabAnalysis);
        setupBottomTabs();
        showSelectedTab();
        promptNotificationPermissionIfNeeded();
    }

    @Override
    protected void onNewIntent(@NonNull android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        selectedTab = HostTab.fromKey(intent.getStringExtra(EXTRA_TARGET_TAB));
        showSelectedTab();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_TAB, selectedTab.getKey());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBottomTabs();
    }

    private void setupBottomTabs() {
        if (tabTrading != null) {
            tabTrading.setOnClickListener(v -> switchTo(HostTab.TRADING));
        }
        if (tabAccount != null) {
            tabAccount.setOnClickListener(v -> switchTo(HostTab.ACCOUNT));
        }
        if (tabAnalysis != null) {
            tabAnalysis.setOnClickListener(v -> switchTo(HostTab.ANALYSIS));
        }
        updateBottomTabs();
    }

    private void switchTo(@NonNull HostTab targetTab) {
        selectedTab = targetTab;
        showSelectedTab();
    }

    private void showSelectedTab() {
        updateBottomTabs();
        navigator.show(getSupportFragmentManager(), R.id.hostFragmentContainer, selectedTab);
    }

    private void updateBottomTabs() {
        if (bottomNavigationView == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applySystemBars(this, palette);
        BottomTabVisibilityManager.apply(this,
                tabTrading,
                tabAccount,
                tabAnalysis,
                null);
        bottomNavigationView.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.surfaceEnd, palette.stroke));
        UiPaletteManager.styleBottomNavTab(tabTrading, selectedTab == HostTab.TRADING, palette);
        UiPaletteManager.styleBottomNavTab(tabAccount, selectedTab == HostTab.ACCOUNT, palette);
        UiPaletteManager.styleBottomNavTab(tabAnalysis, selectedTab == HostTab.ANALYSIS, palette);
    }

    private void promptNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !PermissionHelper.hasNotificationPermission(this)) {
            UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                    .setMessage(R.string.notification_permission_required)
                    .setPositiveButton(R.string.permission_settings, (dialogInterface, which) ->
                            PermissionHelper.requestNotificationPermission(this, REQUEST_CODE_NOTIFICATION))
                    .setNegativeButton(R.string.dismiss, null)
                    .create();
            dialog.show();
            UiPaletteManager.applyAlertDialogSurface(dialog, palette);
        }
    }
}
