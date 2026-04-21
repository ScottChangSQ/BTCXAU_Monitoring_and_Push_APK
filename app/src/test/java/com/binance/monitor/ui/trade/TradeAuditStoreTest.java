package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class TradeAuditStoreTest {

    @Test
    public void recordShouldKeepLatestEntriesFirstAndFillCreatedAtFromClock() {
        TradeAuditStore store = TradeAuditStore.createInMemory(() -> 100L);

        store.record(new TradeAuditEntry(
                "req-1",
                "single",
                "OPEN_MARKET",
                "BTCUSD",
                "hedging",
                "check",
                "EXECUTABLE",
                "",
                "检查通过",
                "买入 BTCUSD 0.05 手",
                0L,
                0L
        ));
        store.record(new TradeAuditEntry(
                "req-2",
                "single",
                "OPEN_MARKET",
                "BTCUSD",
                "hedging",
                "submit",
                "ACCEPTED",
                "",
                "交易已受理",
                "买入 BTCUSD 0.10 手",
                0L,
                0L
        ));

        List<TradeAuditEntry> recent = store.getRecent(10);

        assertEquals(2, recent.size());
        assertEquals("req-2", recent.get(0).getTraceId());
        assertEquals(100L, recent.get(0).getCreatedAt());
        assertEquals("submit", recent.get(0).getStage());
    }

    @Test
    public void lookupShouldReturnAllEntriesForSameTraceId() {
        TradeAuditStore store = TradeAuditStore.createInMemory(() -> 200L);

        store.record(new TradeAuditEntry(
                "req-lookup",
                "single",
                "OPEN_MARKET",
                "BTCUSD",
                "hedging",
                "check",
                "EXECUTABLE",
                "",
                "检查通过",
                "买入 BTCUSD 0.05 手",
                0L,
                0L
        ));
        store.record(new TradeAuditEntry(
                "req-lookup",
                "single",
                "OPEN_MARKET",
                "BTCUSD",
                "hedging",
                "result",
                "SETTLED",
                "",
                "交易已收敛",
                "买入 BTCUSD 0.05 手",
                0L,
                0L
        ));
        store.record(new TradeAuditEntry(
                "req-other",
                "single",
                "OPEN_MARKET",
                "BTCUSD",
                "hedging",
                "check",
                "EXECUTABLE",
                "",
                "检查通过",
                "买入 BTCUSD 0.02 手",
                0L,
                0L
        ));

        List<TradeAuditEntry> entries = store.lookup("req-lookup");

        assertEquals(2, entries.size());
        assertTrue(entries.get(0).getTraceId().equals("req-lookup"));
        assertTrue(entries.get(1).getTraceId().equals("req-lookup"));
    }
}
