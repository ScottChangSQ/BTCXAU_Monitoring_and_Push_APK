/*
 * 异常同步运行时辅助逻辑，负责判断服务端 alert 的重复通知抑制。
 * MonitorService 通过这里的纯函数收口提醒冷却规则，避免规则散落在服务内部。
 */
package com.binance.monitor.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class AbnormalSyncRuntimeHelper {

    private AbnormalSyncRuntimeHelper() {
    }

    // 只有提醒里的全部品种都已脱离冷却期，才继续发服务端 alert，避免重复通知。
    static boolean shouldDispatchServerAlert(String alertId,
                                             List<String> symbols,
                                             Set<String> dispatchedAlertIds,
                                             Map<String, Long> lastNotifyAt,
                                             long now,
                                             long cooldownMs) {
        String safeAlertId = safeTrim(alertId);
        if (safeAlertId.isEmpty()) {
            return false;
        }
        if (containsNormalizedAlertId(dispatchedAlertIds, safeAlertId)) {
            return false;
        }
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

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsNormalizedAlertId(Set<String> dispatchedAlertIds, String alertId) {
        if (dispatchedAlertIds == null || dispatchedAlertIds.isEmpty()) {
            return false;
        }
        String safeAlertId = safeTrim(alertId);
        if (safeAlertId.isEmpty()) {
            return false;
        }
        for (String dispatchedAlertId : dispatchedAlertIds) {
            if (safeAlertId.equals(safeTrim(dispatchedAlertId))) {
                return true;
            }
        }
        return false;
    }
}
