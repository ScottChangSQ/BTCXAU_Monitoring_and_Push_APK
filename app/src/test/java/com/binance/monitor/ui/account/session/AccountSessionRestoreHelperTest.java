/*
 * 账户页会话恢复助手测试，确保空缓存和读取失败不会再被当成同一种情况。
 */
package com.binance.monitor.ui.account.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.security.SessionSummarySnapshot;

import org.junit.Test;

import java.util.Collections;

public class AccountSessionRestoreHelperTest {

    @Test
    public void restoreShouldUseSecureSummaryWhenSnapshotIsReadable() {
        RemoteAccountProfile profile = new RemoteAccountProfile(
                "acc-1", "12345678", "****5678", "IC", "IC 5678", true, "active"
        );
        AccountSessionRestoreHelper.RestoreResult result = AccountSessionRestoreHelper.restore(
                new AccountSessionRestoreHelper.RestoreRequest(
                        SessionSummarySnapshot.ready(true, profile, Collections.singletonList(profile), "87654321", "IC-2"),
                        false,
                        true,
                        "11111111",
                        "PREF-SERVER",
                        "DEFAULT-ACCOUNT",
                        "DEFAULT-SERVER"
                )
        );

        assertTrue(result.isUserLoggedIn());
        assertNotNull(result.getActiveSessionAccount());
        assertEquals("87654321", result.getLoginAccountInput());
        assertEquals("IC-2", result.getLoginServerInput());
        assertEquals(1, result.getSavedSessionAccounts().size());
        assertEquals("", result.getStorageError());
    }

    @Test
    public void restoreShouldDistinguishStorageFailureFromEmptySnapshot() {
        AccountSessionRestoreHelper.RestoreResult failed = AccountSessionRestoreHelper.restore(
                new AccountSessionRestoreHelper.RestoreRequest(
                        SessionSummarySnapshot.storageFailure("读取安全会话缓存失败: AEADBadTagException"),
                        true,
                        true,
                        "11111111",
                        "IC-PREF",
                        "DEFAULT-ACCOUNT",
                        "DEFAULT-SERVER"
                )
        );
        AccountSessionRestoreHelper.RestoreResult empty = AccountSessionRestoreHelper.restore(
                new AccountSessionRestoreHelper.RestoreRequest(
                        SessionSummarySnapshot.empty(),
                        true,
                        true,
                        "11111111",
                        "IC-PREF",
                        "DEFAULT-ACCOUNT",
                        "DEFAULT-SERVER"
                )
        );

        assertTrue(failed.isUserLoggedIn());
        assertEquals("读取安全会话缓存失败: AEADBadTagException", failed.getStorageError());
        assertTrue(failed.getSavedSessionAccounts().isEmpty());
        assertEquals("11111111", failed.getLoginAccountInput());
        assertEquals("IC-PREF", failed.getLoginServerInput());

        assertTrue(empty.isUserLoggedIn());
        assertEquals("", empty.getStorageError());
        assertTrue(empty.getSavedSessionAccounts().isEmpty());
        assertEquals(failed.getLoginAccountInput(), empty.getLoginAccountInput());
        assertEquals(failed.getLoginServerInput(), empty.getLoginServerInput());
    }

    @Test
    public void restoreShouldIgnoreIncompleteProfilesFromSummary() {
        RemoteAccountProfile brokenProfile = new RemoteAccountProfile(
                "acc-2", "", "****0000", "", "IC 0000", true, "active"
        );
        AccountSessionRestoreHelper.RestoreResult result = AccountSessionRestoreHelper.restore(
                new AccountSessionRestoreHelper.RestoreRequest(
                        SessionSummarySnapshot.ready(true, brokenProfile, Collections.singletonList(brokenProfile), "", ""),
                        false,
                        true,
                        "",
                        "",
                        "DEFAULT-ACCOUNT",
                        "DEFAULT-SERVER"
                )
        );

        assertFalse(result.isUserLoggedIn());
        assertEquals("DEFAULT-ACCOUNT", result.getLoginAccountInput());
        assertEquals("DEFAULT-SERVER", result.getLoginServerInput());
        assertTrue(result.getSavedSessionAccounts().isEmpty());
        assertEquals(null, result.getActiveSessionAccount());
    }
}
