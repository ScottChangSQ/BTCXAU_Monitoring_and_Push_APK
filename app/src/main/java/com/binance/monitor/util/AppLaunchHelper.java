package com.binance.monitor.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import com.binance.monitor.constants.AppConstants;

public final class AppLaunchHelper {

    private AppLaunchHelper() {
    }

    public static boolean openBinance(Context context) {
        PackageManager packageManager = context.getPackageManager();
        for (String packageName : AppConstants.BINANCE_PACKAGES) {
            try {
                packageManager.getPackageInfo(packageName, 0);
                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                    return true;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return false;
    }

    public static boolean openMt5(Context context) {
        PackageManager packageManager = context.getPackageManager();
        for (String packageName : AppConstants.MT5_PACKAGES) {
            try {
                packageManager.getPackageInfo(packageName, 0);
                Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launchIntent);
                    return true;
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return false;
    }
}
