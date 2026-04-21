package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class TradeReplayFormatterTest {

    @Test
    public void buildReplayShouldDescribeSettledSingleTrade() {
        TradeReplayFormatter.ReplayContent content = TradeReplayFormatter.buildReplay(
                "req-settled",
                Arrays.asList(
                        entry("req-settled", "single", "OPEN_MARKET", "BTCUSD", "check", "EXECUTABLE", "", "检查通过", 101L),
                        entry("req-settled", "single", "OPEN_MARKET", "BTCUSD", "submit", "ACCEPTED", "", "交易已受理", 102L)
                ),
                Collections.singletonList(
                        entry("req-settled", "single", "OPEN_MARKET", "BTCUSD", "result", "SETTLED", "", "交易已收敛", 103L)
                )
        );

        assertTrue(content.getSummaryText().contains("已受理并收敛"));
        assertTrue(content.getTimelineText().contains("check"));
        assertTrue(content.getTimelineText().contains("result"));
    }

    @Test
    public void buildReplayShouldDescribePartialBatchTrade() {
        TradeReplayFormatter.ReplayContent content = TradeReplayFormatter.buildReplay(
                "batch-partial",
                Collections.singletonList(
                        entry("batch-partial", "batch", "BATCH", "BTCUSD", "batch_submit", "PARTIAL", "TRADE_BATCH_PARTIAL", "批量部分成功", 201L)
                ),
                Collections.emptyList()
        );

        assertTrue(content.getSummaryText().contains("批量部分成功"));
        assertTrue(content.getCopyText().contains("batch_submit"));
    }

    @Test
    public void buildReplayShouldDescribeRejectedTrade() {
        TradeReplayFormatter.ReplayContent content = TradeReplayFormatter.buildReplay(
                "req-rejected",
                Collections.singletonList(
                        entry("req-rejected", "single", "OPEN_MARKET", "BTCUSD", "submit", "FAILED", "TRADE_INVALID_VOLUME", "手数不合法", 301L)
                ),
                Collections.emptyList()
        );

        assertTrue(content.getSummaryText().contains("交易被拒绝"));
        assertTrue(content.getCopyText().contains("TRADE_INVALID_VOLUME"));
    }

    @Test
    public void buildReplayShouldDescribeUnknownResult() {
        TradeReplayFormatter.ReplayContent content = TradeReplayFormatter.buildReplay(
                "req-unknown",
                Collections.singletonList(
                        entry("req-unknown", "single", "OPEN_MARKET", "BTCUSD", "result", "ACCEPTED_AWAITING_SYNC", "", "等待账户同步", 401L)
                ),
                Collections.emptyList()
        );

        assertTrue(content.getSummaryText().contains("结果仍未确认"));
        assertTrue(content.getTimelineText().contains("等待账户同步"));
    }

    private static TradeAuditEntry entry(String traceId,
                                         String traceType,
                                         String action,
                                         String symbol,
                                         String stage,
                                         String status,
                                         String errorCode,
                                         String message,
                                         long serverTime) {
        return new TradeAuditEntry(
                traceId,
                traceType,
                action,
                symbol,
                "hedging",
                stage,
                status,
                errorCode,
                message,
                "测试摘要",
                serverTime,
                serverTime
        );
    }
}
