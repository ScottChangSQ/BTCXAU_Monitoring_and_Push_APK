/*
 * 异常同步运行时辅助逻辑，负责判断本地兜底通知、服务端重复通知抑制和错误日志节流。
 * MonitorService 通过这里的纯函数收口同步状态判断，避免规则散落在服务内部。
 */
package com.binance.monitor.service;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

final class AbnormalSyncRuntimeHelper {

    private AbnormalSyncRuntimeHelper() {
    }

    // 只有服务端同步已经尝试过且当前不健康时，才启用本地兜底通知。
    static boolean shouldUseLocalFallbackNotification(boolean syncAttempted, boolean syncHealthy) {
        return syncAttempted && !syncHealthy;
    }

    // 同一条错误在冷却时间内不重复写日志，避免每轮轮询都刷屏。
    static boolean shouldLogSyncError(@Nullable String previousError,
                                      long previousErrorAt,
                                      @Nullable String currentError,
                                      long now,
                                      long cooldownMs) {
        String safeCurrent = safeTrim(currentError);
        if (safeCurrent.isEmpty()) {
            return false;
        }
        String safePrevious = safeTrim(previousError);
        if (!safeCurrent.equals(safePrevious)) {
            return true;
        }
        return now - previousErrorAt >= Math.max(0L, cooldownMs);
    }

    // 只有提醒里的全部品种都已脱离冷却期，才继续发服务端回补通知，避免和本地兜底重复。
    static boolean shouldDispatchServerAlert(@Nullable List<String> symbols,
                                             @Nullable Map<String, Long> lastNotifyAt,
                                             long now,
                                             long cooldownMs) {
        if (symbols == null || symbols.isEmpty()) {
            return false;
        }
        boolean hasUsableSymbol = false;
        for (String symbol : symbols) {
            String safeSymbol = safeTrim(symbol);
            if (safeSymbol.isEmpty()) {
                continue;
            }
            hasUsableSymbol = true;
            long last = lastNotifyAt == null ? 0L : lastNotifyAt.getOrDefault(safeSymbol, 0L);
            if (now - last < Math.max(0L, cooldownMs)) {
                return false;
            }
        }
        return hasUsableSymbol;
    }

    private static String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
