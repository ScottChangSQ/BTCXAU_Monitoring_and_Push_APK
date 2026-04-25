package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AccountPageRefreshCadenceHelperTest {

    @Test
    public void disconnectedDelayShouldUseTenSecondBaseline() {
        assertEquals(10_000L, AccountPageRefreshCadenceHelper.resolveDelayMs(false, 0));
        assertEquals(10_000L, AccountPageRefreshCadenceHelper.resolveDelayMs(false, 5));
    }

    @Test
    public void connectedDelayShouldBackoffFasterForUnchangedSnapshots() {
        assertEquals(10_000L, AccountPageRefreshCadenceHelper.resolveDelayMs(true, 0));
        assertEquals(14_000L, AccountPageRefreshCadenceHelper.resolveDelayMs(true, 1));
        assertEquals(30_000L, AccountPageRefreshCadenceHelper.resolveDelayMs(true, 8));
    }
}
