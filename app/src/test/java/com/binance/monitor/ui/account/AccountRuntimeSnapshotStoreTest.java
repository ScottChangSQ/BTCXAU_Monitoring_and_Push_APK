package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.account.runtime.AccountRuntimePayload;
import com.binance.monitor.ui.account.runtime.AccountRuntimeSnapshotStore;

import org.junit.Test;

import java.util.Collections;

public class AccountRuntimeSnapshotStoreTest {

    @Test
    public void runtimeStoreShouldOnlyExposeOverviewPositionsAndPendingOrders() {
        AccountSnapshot snapshot = new AccountSnapshot(
                Collections.singletonList(new AccountMetric("balance", "100")),
                Collections.singletonList(new CurvePoint(1L, 100d, 100d, 0.1d)),
                Collections.singletonList(new AccountMetric("curve", "x")),
                Collections.singletonList(new PositionItem("Gold", "XAUUSD", 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d)),
                Collections.singletonList(new PositionItem("Gold", "XAUUSD", "Buy", 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1d, 1)),
                Collections.singletonList(new TradeRecordItem(1L, "Gold", "XAUUSD", "Buy", 1d, 1d, 1d, 0d, "")),
                Collections.singletonList(new AccountMetric("profit", "10"))
        );
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                snapshot,
                "123",
                "server",
                "stream",
                "gateway",
                1L,
                "",
                2L,
                "rev-1"
        );

        AccountRuntimePayload payload = new AccountRuntimeSnapshotStore().build(cache);

        assertNotNull(payload.getOverviewMetrics());
        assertNotNull(payload.getPositions());
        assertNotNull(payload.getPendingOrders());
        assertEquals(1, payload.getPositions().size());
        assertEquals(1, payload.getPendingOrders().size());
        assertTrue(payload.getTrades().isEmpty());
        assertTrue(payload.getCurvePoints().isEmpty());
    }
}
