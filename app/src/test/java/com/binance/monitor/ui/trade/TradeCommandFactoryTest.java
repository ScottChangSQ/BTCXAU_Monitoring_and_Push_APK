/*
 * 交易命令工厂单测，负责锁定第一阶段五类单笔交易命令的组装结果。
 * 避免图表页接线后把 side、orderTicket 或 positionTicket 拼错。
 */
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.junit.Test;

public class TradeCommandFactoryTest {

    @Test
    public void openMarketShouldBuildBuyCommandWithSide() {
        TradeCommand command = TradeCommandFactory.openMarket(
                "acc-1",
                "BTCUSD",
                "buy",
                0.1d,
                65000d,
                64000d,
                68000d
        );

        assertEquals("OPEN_MARKET", command.getAction());
        assertEquals("buy", command.getParams().optString("side", ""));
        assertEquals("BTCUSD", command.getParams().optString("symbol", ""));
    }

    @Test
    public void closePositionShouldKeepPositionTicket() {
        TradeCommand command = TradeCommandFactory.closePosition(
                "acc-1",
                "BTCUSD",
                9001L,
                0.2d,
                0d
        );

        assertEquals("CLOSE_POSITION", command.getAction());
        assertEquals(9001L, command.getParams().optLong("positionTicket", 0L));
        assertEquals(0.2d, command.getParams().optDouble("volume"), 0.0000001d);
        assertTrue(!command.getParams().has("price"));
    }

    @Test
    public void pendingCancelShouldKeepOrderTicket() {
        TradeCommand command = TradeCommandFactory.pendingCancel("acc-1", "BTCUSD", 8002L);

        assertEquals("PENDING_CANCEL", command.getAction());
        assertEquals(8002L, command.getParams().optLong("orderTicket", 0L));
    }

    @Test
    public void describeShouldProduceReadableText() {
        TradeCommand command = TradeCommandFactory.modifyTpSl(
                "acc-1",
                "BTCUSD",
                9001L,
                65000d,
                64000d,
                68000d
        );

        String summary = TradeCommandFactory.describe(command);

        assertTrue(summary.contains("修改止盈止损"));
        assertTrue(summary.contains("BTCUSD"));
        assertTrue(summary.contains("TP="));
    }

    @Test
    public void describeShouldUseMarketExecutionTextWhenPriceMissing() {
        TradeCommand command = TradeCommandFactory.openMarket(
                "acc-1",
                "BTCUSD",
                "buy",
                0.1d,
                0d,
                0d,
                0d
        );

        String summary = TradeCommandFactory.describe(command);

        assertTrue(summary.contains("按市价"));
    }
}
