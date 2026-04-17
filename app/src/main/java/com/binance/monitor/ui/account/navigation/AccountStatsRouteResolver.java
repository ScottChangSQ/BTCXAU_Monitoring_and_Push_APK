/*
 * 账户统计旧入口解析器，把历史 extras 翻译成统一分析跳转协议。
 */
package com.binance.monitor.ui.account.navigation;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.AccountStatsBridgeActivity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AccountStatsRouteResolver {

    private AccountStatsRouteResolver() {
    }

    @NonNull
    public static AnalysisDeepLinkTarget resolve(@Nullable Intent sourceIntent,
                                                 @Nullable String source) {
        return resolve(sourceIntent == null ? null : sourceIntent.getExtras(),
                sourceIntent == null ? false
                        : sourceIntent.getBooleanExtra(AccountStatsBridgeActivity.EXTRA_FORCE_DIRECT_ANALYSIS, false),
                source);
    }

    @NonNull
    public static AnalysisDeepLinkTarget resolve(@Nullable Bundle extras,
                                                 boolean forceDirectAnalysis,
                                                 @Nullable String source) {
        return resolveRaw(buildRawParams(extras), forceDirectAnalysis, source);
    }

    @NonNull
    public static AnalysisDeepLinkTarget resolveRaw(@Nullable Map<String, String> rawParams,
                                                    boolean forceDirectAnalysis,
                                                    @Nullable String source) {
        String symbol = rawParams == null ? null : rawParams.get("symbol");
        String normalizedSection = normalizeFocusSection(rawParams);
        AnalysisDeepLinkTarget.TargetType targetType;
        if (!forceDirectAnalysis) {
            targetType = AnalysisDeepLinkTarget.TargetType.ANALYSIS_HOME;
        } else if (AccountStatsBridgeActivity.ANALYSIS_TARGET_TRADE_HISTORY.equals(normalizedSection)) {
            targetType = AnalysisDeepLinkTarget.TargetType.TRADE_HISTORY_FULL;
        } else {
            targetType = AnalysisDeepLinkTarget.TargetType.ANALYSIS_FULL;
        }
        return new AnalysisDeepLinkTarget(
                targetType,
                null,
                symbol,
                null,
                normalizedSection,
                Collections.emptyMap(),
                source
        );
    }

    @NonNull
    public static String normalizeFocusSection(@Nullable Intent sourceIntent) {
        return normalizeFocusSection(sourceIntent == null ? null : sourceIntent.getExtras());
    }

    @NonNull
    public static String normalizeFocusSection(@Nullable Bundle extras) {
        return normalizeFocusSection(buildRawParams(extras));
    }

    @NonNull
    public static String normalizeFocusSection(@Nullable Map<String, String> rawParams) {
        if (rawParams == null) {
            return "";
        }
        String targetSection = rawParams.get(AccountStatsBridgeActivity.EXTRA_ANALYSIS_TARGET_SECTION);
        if (AccountStatsBridgeActivity.ANALYSIS_TARGET_STRUCTURE.equals(targetSection)) {
            return AccountStatsBridgeActivity.ANALYSIS_TARGET_STRUCTURE;
        }
        if (AccountStatsBridgeActivity.ANALYSIS_TARGET_TRADE_HISTORY.equals(targetSection)) {
            return AccountStatsBridgeActivity.ANALYSIS_TARGET_TRADE_HISTORY;
        }
        return "";
    }

    @Nullable
    private static Map<String, String> buildRawParams(@Nullable Bundle extras) {
        if (extras == null) {
            return null;
        }
        Map<String, String> rawParams = new LinkedHashMap<>();
        rawParams.put("symbol", extras.getString("symbol"));
        rawParams.put(AccountStatsBridgeActivity.EXTRA_ANALYSIS_TARGET_SECTION,
                extras.getString(AccountStatsBridgeActivity.EXTRA_ANALYSIS_TARGET_SECTION));
        return rawParams;
    }
}
