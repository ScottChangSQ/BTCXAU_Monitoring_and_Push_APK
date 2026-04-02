/*
 * 账户预加载策略测试，确保前后台切换后会自动使用不同的预加载节奏。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountPreloadPolicyHelperTest {

    @Test
    public void backgroundDelayShouldBeSlowerThanForeground() {
        assertTrue(AccountPreloadPolicyHelper.resolveRefreshDelayMs(false, true)
                > AccountPreloadPolicyHelper.resolveRefreshDelayMs(true, true));
        assertTrue(AccountPreloadPolicyHelper.resolveRefreshDelayMs(false, false)
                > AccountPreloadPolicyHelper.resolveRefreshDelayMs(true, false));
    }

    @Test
    public void foregroundDelayShouldKeepCurrentCadence() {
        assertEquals(5_000L, AccountPreloadPolicyHelper.resolveRefreshDelayMs(true, true));
        assertEquals(10_000L, AccountPreloadPolicyHelper.resolveRefreshDelayMs(true, false));
    }
}
