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
            ".launcher.IconFinancialAlias"
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
        ComponentName componentName = new ComponentName(packageName, packageName + ALIAS_SUFFIXES[0]);
        packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );
    }
}
