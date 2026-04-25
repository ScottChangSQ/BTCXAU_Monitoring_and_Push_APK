package com.binance.monitor.service;

import androidx.annotation.NonNull;

final class MonitorStreamRuntimeModeHelper {

    enum RuntimeMode {
        FOREGROUND_FULL,
        BACKGROUND_FLOATING_MINIMAL,
        ALERT_ONLY
    }

    private MonitorStreamRuntimeModeHelper() {
    }

    @NonNull
    static RuntimeMode resolve(boolean foreground,
                               boolean deviceInteractive,
                               boolean floatingEnabled) {
        if (foreground) {
            return RuntimeMode.FOREGROUND_FULL;
        }
        if (deviceInteractive && floatingEnabled) {
            return RuntimeMode.BACKGROUND_FLOATING_MINIMAL;
        }
        return RuntimeMode.ALERT_ONLY;
    }

    static boolean shouldApplyMarketSnapshot(@NonNull RuntimeMode runtimeMode) {
        return runtimeMode != RuntimeMode.ALERT_ONLY;
    }

    static boolean shouldApplyFullAccountRuntime(@NonNull RuntimeMode runtimeMode) {
        return runtimeMode == RuntimeMode.FOREGROUND_FULL;
    }

    static boolean shouldApplyLiteAccountRuntime(@NonNull RuntimeMode runtimeMode) {
        return runtimeMode == RuntimeMode.BACKGROUND_FLOATING_MINIMAL;
    }

    static boolean shouldPullAccountHistory(@NonNull RuntimeMode runtimeMode) {
        return runtimeMode == RuntimeMode.FOREGROUND_FULL;
    }

    static boolean shouldRefreshFloating(@NonNull RuntimeMode runtimeMode) {
        return runtimeMode != RuntimeMode.ALERT_ONLY;
    }
}
