/*
 * 主壳跳转工厂，统一把目标页收口到 MainHostActivity。
 */
package com.binance.monitor.ui.host;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

public final class HostNavigationIntentFactory {

    private HostNavigationIntentFactory() {
    }

    public static Intent forTab(@NonNull Context context, @NonNull HostTab tab) {
        Intent intent = new Intent(context, MainHostActivity.class);
        intent.putExtra(MainHostActivity.EXTRA_TARGET_TAB, tab.getKey());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }
}
