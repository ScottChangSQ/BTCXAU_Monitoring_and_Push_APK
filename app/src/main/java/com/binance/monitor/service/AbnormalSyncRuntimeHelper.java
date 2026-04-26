/*
 * 异常同步运行时辅助逻辑，负责判断服务端 alert 的重复通知抑制。
 * MonitorService 通过这里的纯函数收口提醒冷却规则，避免规则散落在服务内部。
 */
package com.binance.monitor.service;

import com.binance.monitor.constants.AppConstants;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AbnormalSyncRuntimeHelper {

    private AbnormalSyncRuntimeHelper() {
    }

    // 服务端 alert 去重仍按原始 alert id，但冷却期改为逐品种独立判断。
    static boolean isServerAlertAlreadyDispatched(String alertId,
                                                  Set<String> dispatchedAlertIds) {
        String safeAlertId = safeTrim(alertId);
        if (safeAlertId.isEmpty()) {
            return true;
        }
        return containsNormalizedAlertId(dispatchedAlertIds, safeAlertId);
    }

    // 统一整理 alert 里的品种列表，去掉空值并保持原有顺序。
    static List<String> normalizeServerAlertSymbols(List<String> symbols) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (symbols == null || symbols.isEmpty()) {
            return new ArrayList<>();
        }
        for (String symbol : symbols) {
            String safeSymbol = safeTrim(symbol);
            if (!safeSymbol.isEmpty()) {
                normalized.add(safeSymbol);
            }
        }
        return new ArrayList<>(normalized);
    }

    // 返回本轮真正允许补发通知的品种，只过滤仍在冷却期内的 symbol。
    static List<String> collectDispatchableServerAlertSymbols(List<String> symbols,
                                                              Map<String, Long> lastNotifyAt,
                                                              long now,
                                                              long cooldownMs) {
        List<String> normalizedSymbols = normalizeServerAlertSymbols(symbols);
        if (normalizedSymbols.isEmpty()) {
            return normalizedSymbols;
        }
        List<String> dispatchable = new ArrayList<>();
        long safeCooldownMs = Math.max(0L, cooldownMs);
        for (String symbol : normalizedSymbols) {
            long last = lastNotifyAt == null ? 0L : lastNotifyAt.getOrDefault(symbol, 0L);
            if (now - last >= safeCooldownMs) {
                dispatchable.add(symbol);
            }
        }
        return dispatchable;
    }

    // 合并 alert 会按换行拼接各品种文案；客户端拆分通知时优先提取对应品种那一行。
    static String buildSymbolScopedAlertContent(String content, String symbol) {
        String safeContent = safeTrim(content);
        String safeSymbol = safeTrim(symbol);
        if (safeContent.isEmpty() || safeSymbol.isEmpty()) {
            return safeContent;
        }
        String assetLabel = resolveAlertAssetLabel(safeSymbol);
        if (assetLabel.isEmpty()) {
            return safeContent;
        }
        String[] lines = safeContent.split("\\r?\\n");
        List<String> matchedLines = new ArrayList<>();
        for (String line : lines) {
            String safeLine = safeTrim(line);
            if (!safeLine.isEmpty() && safeLine.contains(assetLabel)) {
                matchedLines.add(safeLine);
            }
        }
        if (matchedLines.isEmpty()) {
            return safeContent;
        }
        return String.join("\n", matchedLines);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String resolveAlertAssetLabel(String symbol) {
        if (AppConstants.SYMBOL_BTC.equalsIgnoreCase(symbol)) {
            return "BTC";
        }
        if (AppConstants.SYMBOL_XAU.equalsIgnoreCase(symbol)) {
            return "XAU";
        }
        return safeTrim(symbol);
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
