package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.runtime.state.model.AccountRuntimeSnapshot;

import org.junit.Test;

public class AccountRevisionRefreshPolicyTest {

    @Test
    public void shouldRequestWhenRuntimeMissing() {
        assertTrue(AccountRevisionRefreshPolicy.shouldRequestSnapshot(
                null,
                "",
                0L,
                1_000L,
                5_000L
        ));
    }

    @Test
    public void shouldRequestWhenHistoryRevisionFallsBehindRuntime() {
        AccountRuntimeSnapshot runtimeSnapshot = new AccountRuntimeSnapshot(
                3L,
                true,
                "7400048@IC",
                "7400048",
                "IC",
                "V2",
                "gw",
                "history-3",
                10_000L,
                new AccountSnapshot(null, null, null, null, null, null, null)
        );

        assertTrue(AccountRevisionRefreshPolicy.shouldRequestSnapshot(
                runtimeSnapshot,
                "history-2",
                9_000L,
                10_500L,
                5_000L
        ));
    }

    @Test
    public void shouldRequestWhenAppliedSnapshotIsStale() {
        AccountRuntimeSnapshot runtimeSnapshot = new AccountRuntimeSnapshot(
                3L,
                true,
                "7400048@IC",
                "7400048",
                "IC",
                "V2",
                "gw",
                "history-3",
                10_000L,
                new AccountSnapshot(null, null, null, null, null, null, null)
        );

        assertTrue(AccountRevisionRefreshPolicy.shouldRequestSnapshot(
                runtimeSnapshot,
                "history-3",
                10_000L,
                16_500L,
                5_000L
        ));
    }

    @Test
    public void shouldSkipWhenRuntimeAndAppliedStateAreFresh() {
        AccountRuntimeSnapshot runtimeSnapshot = new AccountRuntimeSnapshot(
                3L,
                true,
                "7400048@IC",
                "7400048",
                "IC",
                "V2",
                "gw",
                "history-3",
                10_000L,
                new AccountSnapshot(null, null, null, null, null, null, null)
        );

        assertFalse(AccountRevisionRefreshPolicy.shouldRequestSnapshot(
                runtimeSnapshot,
                "history-3",
                12_000L,
                13_000L,
                5_000L
        ));
    }
}
