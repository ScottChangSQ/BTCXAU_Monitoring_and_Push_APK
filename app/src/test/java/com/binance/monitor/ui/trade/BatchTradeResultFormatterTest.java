/*
 * 批量交易结果格式化单测，负责锁定总览文案与单项清单的可读性。
 * 与 BatchTradeResultFormatter、BatchTradeReceipt 一起保证第三阶段不会只展示“整批成功”。
 */
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.BatchTradeItemResult;
import com.binance.monitor.data.model.v2.trade.BatchTradeReceipt;
import com.binance.monitor.data.model.v2.trade.ExecutionError;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class BatchTradeResultFormatterTest {

    @Test
    public void buildSummaryShouldDescribePartialSuccess() {
        BatchTradeReceipt receipt = BatchTradeReceipt.partial(
                "batch-close-001",
                "BEST_EFFORT",
                "hedging",
                Arrays.asList(
                        BatchTradeItemResult.accepted("item-1", "CLOSE_POSITION", "平仓 BTCUSD #1"),
                        BatchTradeItemResult.rejected(
                                "item-2",
                                "CLOSE_POSITION",
                                "平仓 BTCUSD #2",
                                new ExecutionError("TRADE_INVALID_POSITION", "position missing", null)
                        )
                )
        );

        String summary = BatchTradeResultFormatter.buildSummary(receipt);

        assertTrue(summary.contains("部分成功"));
        assertTrue(summary.contains("1/2"));
    }

    @Test
    public void buildItemLinesShouldKeepCloseByReadable() {
        BatchTradeReceipt receipt = BatchTradeReceipt.accepted(
                "batch-closeby-001",
                "GROUPED",
                "hedging",
                Arrays.asList(
                        BatchTradeItemResult.accepted("pair-1a", "CLOSE_BY", "对锁平仓 BTCUSD #3001 <-> #3002")
                )
        );

        List<String> lines = BatchTradeResultFormatter.buildItemLines(receipt);

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("成功"));
        assertTrue(lines.get(0).contains("对锁平仓"));
        assertTrue(lines.get(0).contains("3001"));
        assertTrue(lines.get(0).contains("3002"));
    }
}
