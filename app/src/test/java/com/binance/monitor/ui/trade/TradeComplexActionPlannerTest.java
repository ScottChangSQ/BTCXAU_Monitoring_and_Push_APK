/*
 * 复杂交易规划器单测，负责锁定部分平仓、加仓、反手和 Close By 的正式展开规则。
 * 与 TradeComplexActionPlanner、BatchTradePlan、TradeCommandFactory 一起保证复杂动作不回退到页面层分支。
 */
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.domain.account.model.PositionItem;

import org.junit.Test;

public class TradeComplexActionPlannerTest {

    @Test
    public void reverseInNettingShouldExpandToCloseThenOpen() {
        BatchTradePlan plan = TradeComplexActionPlanner.planReverse(
                "acc-1",
                "netting",
                buildPosition("BTCUSD", "buy", 0.30d, 1001L),
                0.50d,
                65100d
        );

        assertEquals("BEST_EFFORT", plan.getStrategy());
        assertEquals(2, plan.getItems().size());
        assertEquals("CLOSE_POSITION", plan.getItems().get(0).getCommand().getAction());
        assertEquals("OPEN_MARKET", plan.getItems().get(1).getCommand().getAction());
        assertEquals("sell", plan.getItems().get(1).getCommand().getParams().optString("side", ""));
    }

    @Test
    public void partialCloseShouldKeepExplicitTargetVolume() {
        BatchTradePlan plan = TradeComplexActionPlanner.planPartialClose(
                "acc-1",
                "hedging",
                buildPosition("BTCUSD", "sell", -0.80d, 2001L),
                0.30d
        );

        assertEquals(1, plan.getItems().size());
        assertEquals("CLOSE_POSITION", plan.getItems().get(0).getCommand().getAction());
        assertEquals(0.30d, plan.getItems().get(0).getCommand().getVolume(), 0.0000001d);
    }

    @Test
    public void addPositionShouldKeepSingleOpenMarketCommand() {
        BatchTradePlan plan = TradeComplexActionPlanner.planAddPosition(
                "acc-1",
                "hedging",
                "BTCUSD",
                "buy",
                0.20d,
                65200d,
                64000d,
                68000d
        );

        assertEquals(1, plan.getItems().size());
        assertEquals("OPEN_MARKET", plan.getItems().get(0).getCommand().getAction());
        assertEquals("buy", plan.getItems().get(0).getCommand().getParams().optString("side", ""));
    }

    @Test
    public void closeByInHedgingShouldKeepSinglePairGroup() {
        BatchTradePlan plan = TradeComplexActionPlanner.planCloseBy(
                "acc-1",
                buildPosition("BTCUSD", "buy", 0.20d, 3001L),
                buildPosition("BTCUSD", "sell", -0.20d, 3002L)
        );

        assertEquals("GROUPED", plan.getStrategy());
        assertEquals(2, plan.getItems().size());
        assertEquals("pair-1", plan.getItems().get(0).getExtras().optString("groupKey", ""));
        assertEquals("CLOSE_BY", plan.getItems().get(0).getCommand().getAction());
        assertEquals(3002L, plan.getItems().get(0).getCommand().getParams().optLong("oppositePositionTicket", 0L));
    }

    private static PositionItem buildPosition(String symbol, String side, double quantity, long positionTicket) {
        return new PositionItem(
                symbol,
                symbol,
                side,
                positionTicket,
                0L,
                quantity,
                Math.abs(quantity),
                65000d,
                65100d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0,
                0d,
                0d,
                0d,
                0d
        );
    }
}
