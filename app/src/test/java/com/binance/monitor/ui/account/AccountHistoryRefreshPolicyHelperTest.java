package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.runtime.account.AccountHistoryRefreshPolicyHelper;

import org.junit.Test;

public class AccountHistoryRefreshPolicyHelperTest {

    @Test
    public void shouldRefreshAllHistoryWhenRemoteRevisionChanges() {
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("rev-11", "rev-10", true));
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("rev-9", "rev-10", true));
    }

    @Test
    public void shouldRefreshAllHistoryWhenHistoryMissingButRemoteRevisionExists() {
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("rev-3", "", false));
    }

    @Test
    public void shouldRefreshAllHistoryWhenTradeHistoryMissingEvenIfRemoteRevisionMatchesCache() {
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("rev-5", "rev-5", false));
    }

    @Test
    public void shouldSkipAllHistoryWhenRemoteRevisionUnchanged() {
        assertFalse(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("rev-5", "rev-5", true));
    }

    @Test
    public void shouldRefreshAllHistoryWhenBothRevisionsAreEmptyAndHistoryMissing() {
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("", "", false));
    }

    @Test
    public void shouldRefreshAllHistoryWhenRemoteRevisionUnavailableAndNoCachedRevisionExists() {
        assertTrue(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("", "", true));
    }

    @Test
    public void shouldSkipAllHistoryWhenRemoteRevisionUnavailableButCachedRevisionExists() {
        assertFalse(AccountHistoryRefreshPolicyHelper.shouldRefreshAllHistory("", "rev-existing", true));
    }
}
