package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountHistoryRefreshPolicyHelperTest {

    @Test
    public void shouldRefreshAllHistoryWhenRemoteTradeCountChanges() {
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(11, 10, true));
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(9, 10, true));
    }

    @Test
    public void shouldRefreshAllHistoryWhenHistoryMissingButRemoteAlreadyHasTrades() {
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(3, -1, false));
    }

    @Test
    public void shouldSkipAllHistoryWhenRemoteTradeCountUnchanged() {
        assertFalse(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(5, 5, true));
    }

    @Test
    public void shouldSkipAllHistoryWhenRemoteTradeCountIsZeroAndLocalHistoryAlreadyMatchesZero() {
        assertFalse(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(0, 0, true));
        assertFalse(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(0, -1, false));
    }

    @Test
    public void shouldRefreshAllHistoryWhenRemoteTradeCountUnavailableButLocalHistoryMissing() {
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(-1, -1, false));
    }

    @Test
    public void shouldSkipAllHistoryWhenRemoteTradeCountUnavailableButLocalHistoryExists() {
        assertFalse(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory(-1, 12, true));
    }
}
