/*
 * 主题图标切换器，负责根据当前主题启用对应的桌面入口别名。
 * 设置页选中主题后会通过这里同步更新桌面图标，无需重启应用。
 */
package com.binance.monitor.ui.theme;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

public final class ThemeLauncherIconManager {

    private static final String[] ALIAS_SUFFIXES = new String[]{
            ".launcher.IconFinancialAlias",
            ".launcher.IconVintageAlias",
            ".launcher.IconBinanceAlias",
            ".launcher.IconTradingViewAlias",
            ".launcher.IconLightAlias"
    };

    private ThemeLauncherIconManager() {
    }

    // 应用主题图标，只保留当前主题对应的桌面入口启用。
    public static void apply(Context context, int paletteId) {
        if (context == null) {
            return;
        }
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName();
        int enabledIndex = resolveAliasIndex(paletteId);
        for (int i = 0; i < ALIAS_SUFFIXES.length; i++) {
            ComponentName componentName = new ComponentName(packageName, packageName + ALIAS_SUFFIXES[i]);
            int targetState = i == enabledIndex
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            packageManager.setComponentEnabledSetting(
                    componentName,
                    targetState,
                    PackageManager.DONT_KILL_APP
            );
        }
    }

    // 主题图标与 paletteId 按固定索引映射，避免别名启用策略散落到调用侧。
    private static int resolveAliasIndex(int paletteId) {
        switch (paletteId) {
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            default:
                return 0;
        }
    }
}
