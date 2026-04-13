package com.binance.monitor.data.model.v2.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class RemoteAccountProfileDeduplicationHelperTest {

    @Test
    public void deduplicateShouldMergeProfilesByProfileId() {
        RemoteAccountProfile first = new RemoteAccountProfile(
                "acct_100_demo",
                "100",
                "1**",
                "Demo",
                "",
                false,
                ""
        );
        RemoteAccountProfile second = new RemoteAccountProfile(
                "acct_100_demo",
                "100",
                "100",
                "Demo",
                "主账户",
                true,
                "active"
        );

        List<RemoteAccountProfile> deduplicated = RemoteAccountProfileDeduplicationHelper.deduplicate(
                Arrays.asList(first, second)
        );

        assertEquals(1, deduplicated.size());
        RemoteAccountProfile merged = deduplicated.get(0);
        assertEquals("acct_100_demo", merged.getProfileId());
        assertEquals("100", merged.getLogin());
        assertEquals("100", merged.getLoginMasked());
        assertEquals("Demo", merged.getServer());
        assertEquals("主账户", merged.getDisplayName());
        assertTrue(merged.isActive());
        assertEquals("active", merged.getState());
    }

    @Test
    public void deduplicateShouldFallbackToLoginAndServerWhenProfileIdMissing() {
        RemoteAccountProfile first = new RemoteAccountProfile(
                "",
                "200",
                "2**",
                "TradeServer",
                "Alpha",
                false,
                "saved"
        );
        RemoteAccountProfile second = new RemoteAccountProfile(
                "",
                "200",
                "200",
                "tradeserver",
                "",
                false,
                ""
        );

        List<RemoteAccountProfile> deduplicated = RemoteAccountProfileDeduplicationHelper.deduplicate(
                Arrays.asList(first, second)
        );

        assertEquals(1, deduplicated.size());
        RemoteAccountProfile merged = deduplicated.get(0);
        assertEquals("200", merged.getLogin());
        assertEquals("200", merged.getLoginMasked());
        assertEquals("tradeserver", merged.getServer());
        assertEquals("Alpha", merged.getDisplayName());
        assertEquals("saved", merged.getState());
    }
}
