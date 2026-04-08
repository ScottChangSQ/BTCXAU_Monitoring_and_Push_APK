package com.binance.monitor.ui.account.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TradeRecordItemTest {

    @Test
    public void explicitLifecyclePricesShouldNotBackfillFromTradePrice() {
        TradeRecordItem item = new TradeRecordItem(
                2_000L,
                "BTCUSD",
                "BTCUSD",
                "Buy",
                120d,
                0.1d,
                12d,
                0d,
                "",
                8d,
                1_000L,
                2_000L,
                0d,
                0d,
                0d,
                11L,
                12L,
                13L,
                1
        );

        assertEquals(0d, item.getOpenPrice(), 1e-9);
        assertEquals(0d, item.getClosePrice(), 1e-9);
    }
}
