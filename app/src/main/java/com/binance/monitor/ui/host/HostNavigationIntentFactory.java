/*
 * 主壳跳转工厂，统一把目标页收口到 MainHostActivity。
 */
package com.binance.monitor.ui.host;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.AccountStatsBridgeActivity;
import com.binance.monitor.ui.account.navigation.AnalysisDeepLinkTarget;

public final class HostNavigationIntentFactory {

    private HostNavigationIntentFactory() {
    }

    public static Intent forTab(@NonNull Context context, @NonNull HostTab tab) {
        Intent intent = new Intent(context, MainHostActivity.class);
        intent.putExtra(MainHostActivity.EXTRA_TARGET_TAB, tab.getKey());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @NonNull
    public static Intent forAnalysisTarget(@NonNull Context context,
                                           @NonNull AnalysisDeepLinkTarget target) {
        if (!target.requiresDirectAnalysisPage()) {
            return forTab(context, HostTab.ACCOUNT_STATS);
        }
        Intent intent = new Intent(context, AccountStatsBridgeActivity.class);
        intent.putExtra(AccountStatsBridgeActivity.EXTRA_FORCE_DIRECT_ANALYSIS, true);
        if (!target.getFocusSection().isEmpty()) {
            intent.putExtra(AccountStatsBridgeActivity.EXTRA_ANALYSIS_TARGET_SECTION, target.getFocusSection());
        }
        putOptionalStringExtra(intent, "symbol", target.getSymbol());
        putOptionalStringExtra(intent, "timeRange", target.getTimeRange());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private static void putOptionalStringExtra(@NonNull Intent intent,
                                               @NonNull String key,
                                               @Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        intent.putExtra(key, value);
    }
}
