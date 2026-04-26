/*
 * 异常同步运行时辅助逻辑测试，确保服务端 alert 冷却判定规则稳定。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertEquals;
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
    public void shouldOnlyDispatchSymbolsThatAreOutOfCooldown() {
        Map<String, Long> lastNotifyAt = new HashMap<>();
        long now = 1_000_000L;
        lastNotifyAt.put(AppConstants.SYMBOL_BTC, now - 60_000L);
        lastNotifyAt.put(AppConstants.SYMBOL_XAU, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);

        assertEquals(Collections.singletonList(AppConstants.SYMBOL_XAU),
                AbnormalSyncRuntimeHelper.collectDispatchableServerAlertSymbols(
                Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        ));
    }

    @Test
    public void shouldDispatchAllSymbolsWhenAllSymbolsAreOutOfCooldown() {
        Map<String, Long> lastNotifyAt = new HashMap<>();
        long now = 1_000_000L;
        lastNotifyAt.put(AppConstants.SYMBOL_BTC, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);
        lastNotifyAt.put(AppConstants.SYMBOL_XAU, now - AppConstants.NOTIFICATION_COOLDOWN_MS - 1L);

        assertEquals(Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                AbnormalSyncRuntimeHelper.collectDispatchableServerAlertSymbols(
                Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                lastNotifyAt,
                now,
                AppConstants.NOTIFICATION_COOLDOWN_MS
        ));
    }

    @Test
    public void shouldSkipServerAlertWhenAlertIdAlreadyDispatched() {
        Set<String> dispatchedAlertIds = new HashSet<>(Collections.singleton("alert-3"));

        assertTrue(AbnormalSyncRuntimeHelper.isServerAlertAlreadyDispatched(
                "alert-3",
                dispatchedAlertIds
        ));
    }

    @Test
    public void shouldNormalizeDispatchedAlertIdsBeforeComparing() {
        Set<String> dispatchedAlertIds = new HashSet<>(Collections.singleton(" alert-4 "));

        assertTrue(AbnormalSyncRuntimeHelper.isServerAlertAlreadyDispatched(
                "alert-4",
                dispatchedAlertIds
        ));
    }

    @Test
    public void shouldExtractOnlyMatchedLineForSymbolScopedContent() {
        String content = "BTC 的 成交量 出现异常！\nXAU 的 价格变化 出现异常！";

        assertEquals("XAU 的 价格变化 出现异常！",
                AbnormalSyncRuntimeHelper.buildSymbolScopedAlertContent(content, AppConstants.SYMBOL_XAU));
    }

    @Test
    public void shouldFallbackToOriginalContentWhenNoMatchedLineExists() {
        String content = "组合异常提醒";

        assertEquals(content,
                AbnormalSyncRuntimeHelper.buildSymbolScopedAlertContent(content, AppConstants.SYMBOL_BTC));
    }
}
