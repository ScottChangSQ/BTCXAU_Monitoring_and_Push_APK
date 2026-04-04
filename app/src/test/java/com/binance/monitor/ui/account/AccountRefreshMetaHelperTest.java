/*
 * 账户概览刷新信息辅助逻辑测试，确保顶部显示的剩余秒数与实际调度一致。
 */
package com.binance.monitor.ui.account;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccountRefreshMetaHelperTest {

    @Test
    public void scheduledIntervalShouldOverrideDynamicInterval() {
        long intervalSeconds = AccountRefreshMetaHelper.resolveIntervalSeconds(7_000L, 5_000L);

        assertEquals(7L, intervalSeconds);
    }

    @Test
    public void remainingSecondsShouldTrackScheduledNextRefresh() {
        long nowMs = 1_000L;
        long remainSeconds = AccountRefreshMetaHelper.resolveRemainingSeconds(4_200L, nowMs, 7L);

        assertEquals(4L, remainSeconds);
    }

    @Test
    public void remainingSecondsShouldFallbackToIntervalWhenUnschedule() {
        long remainSeconds = AccountRefreshMetaHelper.resolveRemainingSeconds(0L, 1_000L, 5L);

        assertEquals(5L, remainSeconds);
    }

    @Test
    public void remainingSecondsShouldClampToOneWhenScheduleAlreadyExpired() {
        long remainSeconds = AccountRefreshMetaHelper.resolveRemainingSeconds(1_000L, 1_500L, 5L);

        assertEquals(1L, remainSeconds);
    }

    @Test
    public void remainingSecondsShouldStayAtOneWhileRequestIsLoadingWithoutScheduledRefresh() {
        long remainSeconds = AccountRefreshMetaHelper.resolveRemainingSeconds(0L, 1_000L, 5L, true);

        assertEquals(1L, remainSeconds);
    }
}
