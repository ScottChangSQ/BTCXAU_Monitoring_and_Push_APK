/*
 * 验证交易可见性诊断摘要，确保运行日志能稳定暴露总数、小手数记录分布和示例。
 * 供账户统计页排查“网关已返回但页面未显示”的历史交易问题使用。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.domain.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class TradeVisibilityDiagnosticsHelperTest {

    @Test
    public void buildTradeSignatureShouldHandleEmptyTrades() {
        String signature = TradeVisibilityDiagnosticsHelper.buildTradeSignature(Collections.emptyList());

        assertEquals("count=0, smallLots=none, samples=none", signature);
    }

    @Test
    public void buildTradeSignatureShouldSummarizeSmallLotTradesBySymbol() {
        TradeRecordItem btcSmall = trade("BTCUSD", "Sell", 0.01d, 1774821668485L, 1779633102L);
        TradeRecordItem xauSmall = trade("XAUUSD", "Buy", 0.01d, 1774920152451L, 1779880436L);
        TradeRecordItem btcNormal = trade("BTCUSD", "Sell", 0.05d, 1775210719441L, 1780789731L);

        String signature = TradeVisibilityDiagnosticsHelper.buildTradeSignature(
                Arrays.asList(btcNormal, btcSmall, xauSmall)
        );

        assertTrue(signature.contains("count=3"));
        assertTrue(signature.contains("smallLots=BTCUSD:1,XAUUSD:1"));
        assertTrue(signature.contains("BTCUSD/Sell/0.01@1774821668485#1779633102"));
        assertTrue(signature.contains("XAUUSD/Buy/0.01@1774920152451#1779880436"));
    }

    private TradeRecordItem trade(String code,
                                  String side,
                                  double quantity,
                                  long closeTime,
                                  long dealTicket) {
        return new TradeRecordItem(
                closeTime,
                code,
                code,
                side,
                1d,
                quantity,
                quantity,
                0d,
                "",
                1d,
                closeTime - 1000L,
                closeTime,
                0d,
                1d,
                1d,
                dealTicket,
                dealTicket + 1L,
                dealTicket + 2L,
                1
        );
    }
}
