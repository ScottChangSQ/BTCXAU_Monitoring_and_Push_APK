package com.binance.monitor.runtime.market.truth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GapRepairStateStoreTest {

    @Test
    public void shouldNotRetrySameGapWithoutNewEvidence() {
        GapRepairStateStore store = new GapRepairStateStore();
        GapRepairStateStore.GapKey key = new GapRepairStateStore.GapKey(
                "BTCUSDT",
                1_713_913_200_000L,
                1_713_913_379_999L
        );

        assertTrue(store.shouldRetry(key, "rev-1", 1_713_917_000_000L));
        store.markRepairAttempted(key, "rev-1", 1_713_917_000_000L);
        store.markStillMissing(key, "rev-1", 1_713_917_005_000L);

        assertFalse(store.shouldRetry(key, "rev-1", 1_713_917_010_000L));
        assertTrue(store.shouldRetry(key, "rev-2", 1_713_917_010_000L));

        GapRepairStateStore.GapRecord record = store.selectRecord(key);
        assertNotNull(record);
        assertEquals(GapRepairStateStore.GapStatus.RETRY_READY, record.getStatus());
        assertEquals("rev-2", record.getLastEvidenceToken());
    }

    @Test
    public void shouldBlockRetryWhileSameGapIsStillRepairing() {
        GapRepairStateStore store = new GapRepairStateStore();
        GapRepairStateStore.GapKey key = new GapRepairStateStore.GapKey(
                "XAUUSD",
                1_713_913_200_000L,
                1_713_913_259_999L
        );

        store.markRepairAttempted(key, "frontier-1", 1_713_917_000_000L);

        assertFalse(store.shouldRetry(key, "frontier-1", 1_713_917_010_000L));

        GapRepairStateStore.GapRecord record = store.selectRecord(key);
        assertNotNull(record);
        assertEquals(GapRepairStateStore.GapStatus.REPAIRING, record.getStatus());
    }
}
