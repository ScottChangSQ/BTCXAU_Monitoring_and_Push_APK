package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MarketChartStartupGateTest {

    @Test
    public void commitPrimaryDisplayShouldWaitUntilDrawThenFlushRealtimeAndOverlay() {
        MarketChartStartupGate gate = new MarketChartStartupGate();
        gate.resetForDataKey("BTCUSDT|1m");

        List<String> steps = new ArrayList<>();
        gate.replacePendingRealtime("BTCUSDT|1m", () -> steps.add("realtime"));
        gate.replacePendingOverlay("BTCUSDT|1m", () -> steps.add("overlay"));

        List<Runnable> pendingAfterCommit = gate.onPrimaryDisplayCommitted("BTCUSDT|1m");

        assertNotNull(pendingAfterCommit);
        assertTrue(pendingAfterCommit.isEmpty());
        assertTrue(gate.shouldDeferUntilPrimaryDisplay("BTCUSDT|1m"));

        List<Runnable> pendingAfterDraw = gate.onPrimaryDisplayDrawn("BTCUSDT|1m");

        assertNotNull(pendingAfterDraw);
        assertEquals(2, pendingAfterDraw.size());
        assertFalse(gate.shouldDeferUntilPrimaryDisplay("BTCUSDT|1m"));

        for (Runnable item : pendingAfterDraw) {
            item.run();
        }
        assertEquals(Arrays.asList("realtime", "overlay"), steps);
    }

    @Test
    public void drawBeforeCommitShouldAlsoWaitForPrimaryDisplayCommit() {
        MarketChartStartupGate gate = new MarketChartStartupGate();
        gate.resetForDataKey("BTCUSDT|1m");

        List<String> steps = new ArrayList<>();
        gate.replacePendingRealtime("BTCUSDT|1m", () -> steps.add("realtime"));

        List<Runnable> pendingAfterDraw = gate.onPrimaryDisplayDrawn("BTCUSDT|1m");

        assertTrue(pendingAfterDraw.isEmpty());
        assertTrue(gate.shouldDeferUntilPrimaryDisplay("BTCUSDT|1m"));

        List<Runnable> pendingAfterCommit = gate.onPrimaryDisplayCommitted("BTCUSDT|1m");

        assertEquals(1, pendingAfterCommit.size());
        assertFalse(gate.shouldDeferUntilPrimaryDisplay("BTCUSDT|1m"));
        pendingAfterCommit.get(0).run();
        assertEquals(Arrays.asList("realtime"), steps);
    }

    @Test
    public void resetForNewDataKeyShouldDropPendingWorkFromPreviousKey() {
        MarketChartStartupGate gate = new MarketChartStartupGate();
        gate.resetForDataKey("BTCUSDT|1m");
        gate.replacePendingRealtime("BTCUSDT|1m", () -> {
        });
        gate.replacePendingOverlay("BTCUSDT|1m", () -> {
        });

        gate.resetForDataKey("XAUUSDT|1m");

        assertTrue(gate.shouldDeferUntilPrimaryDisplay("XAUUSDT|1m"));
        assertTrue(gate.onPrimaryDisplayCommitted("BTCUSDT|1m").isEmpty());
        assertTrue(gate.onPrimaryDisplayDrawn("BTCUSDT|1m").isEmpty());
        assertTrue(gate.onPrimaryDisplayCommitted("XAUUSDT|1m").isEmpty());
        assertTrue(gate.onPrimaryDisplayDrawn("XAUUSDT|1m").isEmpty());
    }

    @Test
    public void replacingPendingWorkShouldKeepLatestTaskOnly() {
        MarketChartStartupGate gate = new MarketChartStartupGate();
        gate.resetForDataKey("BTCUSDT|1m");

        List<String> steps = new ArrayList<>();
        gate.replacePendingRealtime("BTCUSDT|1m", () -> steps.add("realtime-old"));
        gate.replacePendingRealtime("BTCUSDT|1m", () -> steps.add("realtime-new"));
        gate.replacePendingOverlay("BTCUSDT|1m", () -> steps.add("overlay-old"));
        gate.replacePendingOverlay("BTCUSDT|1m", () -> steps.add("overlay-new"));

        List<Runnable> pending = gate.onPrimaryDisplayCommitted("BTCUSDT|1m");
        assertTrue(pending.isEmpty());
        pending = gate.onPrimaryDisplayDrawn("BTCUSDT|1m");
        for (Runnable item : pending) {
            item.run();
        }

        assertEquals(Arrays.asList("realtime-new", "overlay-new"), steps);
    }
}
