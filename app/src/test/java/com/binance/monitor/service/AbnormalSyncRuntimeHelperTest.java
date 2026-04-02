/*
 * 异常同步运行时辅助逻辑测试，确保本地兜底通知、重复通知抑制和错误日志节流规则稳定。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.constants.AppConstants;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AbnormalSyncRuntimeHelperTest {

    @Test
    public void shouldOnlyUseLocalFallbackNotificationAfterSyncFailed() {
        assertFalse(AbnormalSyncRuntimeHelper.shouldUseLocalFallbackNotification(false, false));
        assertFalse(AbnormalSyncRuntimeHelper.shouldUseLocalFallbackNotification(false, true));
        assertFalse(AbnormalSyncRuntimeHelper.shouldUseLocalFallbackNotification(true, true));
        assertTrue(AbnormalSyncRuntimeHelper.shouldUseLocalFallbackNotification(true, false));
    }

    @Test
    public void shouldThrottleSameSyncErrorWithinCooldown() {
        assertTrue(AbnormalSyncRuntimeHelper.shouldLogSyncError("", 0L, "HTTP 404", 10_000L, 60_000L));
        assertFalse(AbnormalSyncRuntimeHelper.shouldLogSyncError("HTTP 404", 10_000L, "HTTP 404", 20_000L, 60_000L));
        assertTrue(AbnormalSyncRuntimeHelper.shouldLogSyncError("HTTP 404", 10_000L, "HTTP 404", 80_001L, 60_000L));
        assertTrue(AbnormalSyncRuntimeHelper.shouldLogSyncError("HTTP 404", 10_000L, "connect failed", 20_000L, 60_000L));
    }

    @Test
    public void shouldSkipServerAlertWhenAnySymbolIsStillInCooldown() {
        Map<String, Long> lastNotifyAt = new HashMap<>();
        long now = 1_000_000L;
        lastNotifyAt.put(AppConstants.SYMBOL_BTC, now - 60_000L);
        lastNotifyAt.put(AppConstants.SYMBOL_XAU, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);

        boolean eligible = AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        );

        assertFalse(eligible);
    }

    @Test
    public void shouldDispatchServerAlertWhenAllSymbolsAreOutOfCooldown() {
        Map<String, Long> lastNotifyAt = new HashMap<>();
        long now = 1_000_000L;
        lastNotifyAt.put(AppConstants.SYMBOL_BTC, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);
        lastNotifyAt.put(AppConstants.SYMBOL_XAU, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);

        boolean eligible = AbnormalSyncRuntimeHelper.shouldDispatchServerAlert(
                Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        );

        assertTrue(eligible);
    }
}
