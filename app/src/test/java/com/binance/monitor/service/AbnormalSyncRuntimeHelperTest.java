/*
 * 异常同步运行时辅助逻辑测试，确保服务端 alert 冷却判定规则稳定。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.constants.AppConstants;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AbnormalSyncRuntimeHelperTest {

    @Test
    public void shouldSkipServerAlertWhenAnySymbolIsStillInCooldown() {
        Map<String, Long> lastNotifyAt = new HashMap<>();
        Set<String> dispatchedAlertIds = new HashSet<>();
        long now = 1_000_000L;
        lastNotifyAt.put(AppConstants.SYMBOL_BTC, now - 60_000L);
        lastNotifyAt.put(AppConstants.SYMBOL_XAU, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);

        boolean eligible = AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                "alert-1",
                Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                dispatchedAlertIds,
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        );

        assertFalse(eligible);
    }

    @Test
    public void shouldDispatchServerAlertWhenAllSymbolsAreOutOfCooldown() {
        Map<String, Long> lastNotifyAt = new HashMap<>();
        Set<String> dispatchedAlertIds = new HashSet<>();
        long now = 1_000_000L;
        lastNotifyAt.put(AppConstants.SYMBOL_BTC, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);
        lastNotifyAt.put(AppConstants.SYMBOL_XAU, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);

        boolean eligible = AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                "alert-2",
                Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                dispatchedAlertIds,
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        );

        assertTrue(eligible);
    }

    @Test
    public void shouldSkipServerAlertWhenAlertIdAlreadyDispatched() {
        Map<String, Long> lastNotifyAt = new HashMap<>();
        Set<String> dispatchedAlertIds = new HashSet<>(Collections.singleton("alert-3"));
        long now = 1_000_000L;
        lastNotifyAt.put(AppConstants.SYMBOL_BTC, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);

        boolean eligible = AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                "alert-3",
                Collections.singletonList(AppConstants.SYMBOL_BTC),
                dispatchedAlertIds,
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        );

        assertFalse(eligible);
    }

    @Test
    public void shouldNormalizeDispatchedAlertIdsBeforeComparing() {
        Map<String, Long> lastNotifyAt = new HashMap<>();
        Set<String> dispatchedAlertIds = new HashSet<>(Collections.singleton(" alert-4 "));
        long now = 1_000_000L;
        lastNotifyAt.put(AppConstants.SYMBOL_BTC, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);

        boolean eligible = AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                "alert-4",
                Collections.singletonList(AppConstants.SYMBOL_BTC),
                dispatchedAlertIds,
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        );

        assertFalse(eligible);
    }
}
