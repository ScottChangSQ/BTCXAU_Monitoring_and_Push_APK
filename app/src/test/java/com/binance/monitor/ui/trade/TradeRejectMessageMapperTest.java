package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.v2.trade.ExecutionError;

import org.junit.Test;

public class TradeRejectMessageMapperTest {

    @Test
    public void shouldTranslateKnownTradeCodes() {
        assertEquals("保证金不足，请降低手数或释放仓位后重试",
                TradeRejectMessageMapper.toUserMessage(
                        ExecutionError.of("TRADE_INSUFFICIENT_MARGIN", "margin not enough")));
        assertEquals("当前市场暂不可交易，请稍后重试",
                TradeRejectMessageMapper.toUserMessage(
                        ExecutionError.of("TRADE_MARKET_CLOSED", "market closed")));
        assertEquals("服务器报价已变化，请重新确认价格后再提交",
                TradeRejectMessageMapper.toUserMessage(
                        ExecutionError.of("TRADE_REQUOTE", "requote")));
    }
}
