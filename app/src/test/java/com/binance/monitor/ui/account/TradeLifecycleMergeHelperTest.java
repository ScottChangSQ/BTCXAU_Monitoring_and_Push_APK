/*
 * 历史交易生命周期归一化测试，确保开仓/平仓拆分记录会被补齐成统计和图表可用的闭合成交。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TradeLifecycleMergeHelperTest {

    @Test
    public void mergeShouldCarryEarliestOpenLifecycleIntoMultipleCloseRecords() {
        long openTime = 1_000_000L;
        long firstClose = 1_060_000L;
        long secondClose = 1_120_000L;
        List<TradeRecordItem> merged = TradeLifecycleMergeHelper.merge(Arrays.asList(
                new TradeRecordItem(
                        openTime,
                        "BTCUSDT",
                        "BTCUSDT",
                        "buy",
                        100d,
                        1d,
                        100d,
                        0d,
                        "open",
                        0d,
                        openTime,
                        openTime,
                        0d,
                        100d,
                        100d,
                        0L,
                        7001L,
                        8001L,
                        0
                ),
                new TradeRecordItem(
                        firstClose,
                        "BTCUSDT",
                        "BTCUSDT",
                        "buy",
                        110d,
                        0.4d,
                        44d,
                        0d,
                        "close-1",
                        8d,
                        firstClose,
                        firstClose,
                        -0.5d,
                        110d,
                        110d,
                        0L,
                        7001L,
                        8001L,
                        1
                ),
                new TradeRecordItem(
                        secondClose,
                        "BTCUSDT",
                        "BTCUSDT",
                        "buy",
                        108d,
                        0.6d,
                        64.8d,
                        0d,
                        "close-2",
                        5d,
                        secondClose,
                        secondClose,
                        -0.7d,
                        108d,
                        108d,
                        0L,
                        7001L,
                        8001L,
                        1
                )
        ));

        assertEquals(2, merged.size());
        assertEquals(openTime, merged.get(0).getOpenTime());
        assertEquals(secondClose, merged.get(0).getCloseTime());
        assertEquals(100d, merged.get(0).getOpenPrice(), 0.0001d);
        assertEquals(openTime, merged.get(1).getOpenTime());
        assertEquals(firstClose, merged.get(1).getCloseTime());
        assertEquals(100d, merged.get(1).getOpenPrice(), 0.0001d);
    }

    @Test
    public void mergeShouldAggregateSingleCloseLifecycleFeesAndStorage() {
        long openTime = 2_000_000L;
        long closeTime = 2_120_000L;
        List<TradeRecordItem> merged = TradeLifecycleMergeHelper.merge(Arrays.asList(
                new TradeRecordItem(
                        openTime,
                        "XAUUSDT",
                        "XAUUSDT",
                        "sell",
                        2_000d,
                        1d,
                        2_000d,
                        1d,
                        "open",
                        0d,
                        openTime,
                        openTime,
                        -0.2d,
                        2_000d,
                        2_000d,
                        0L,
                        9001L,
                        9101L,
                        0
                ),
                new TradeRecordItem(
                        closeTime,
                        "XAUUSDT",
                        "XAUUSDT",
                        "sell",
                        1_980d,
                        1d,
                        1_980d,
                        2d,
                        "close",
                        15d,
                        closeTime,
                        closeTime,
                        -0.8d,
                        1_980d,
                        1_980d,
                        0L,
                        9001L,
                        9101L,
                        1
                )
        ));

        assertEquals(1, merged.size());
        assertEquals(openTime, merged.get(0).getOpenTime());
        assertEquals(closeTime, merged.get(0).getCloseTime());
        assertEquals(3d, merged.get(0).getFee(), 0.0001d);
        assertEquals(-1d, merged.get(0).getStorageFee(), 0.0001d);
    }
}
