package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.BatchTradeItem;
import com.binance.monitor.data.model.v2.trade.BatchTradePlan;
import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

public class TradeRiskGuardTest {

    @Test
    public void marketOrderShouldStayAllowedButRequireConfirmationByDefault() {
        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateTrade(
                TradeCommandFactory.withEntryMode(
                        TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0.05d, 65000d, 0d, 0d),
                        "quick"
                ),
                config()
        );

        assertTrue(decision.isAllowed());
        assertTrue(decision.isConfirmationRequired());
    }

    @Test
    public void quickPendingOrderShouldStayAllowed() {
        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateTrade(
                TradeCommandFactory.withEntryMode(
                        TradeCommandFactory.pendingAdd("acc-1", "BTCUSD", "buy_limit", 0.50d, 64000d, 0d, 0d),
                        "quick"
                ),
                config()
        );

        assertTrue(decision.isAllowed());
        assertTrue(decision.isConfirmationRequired());
    }

    @Test
    public void zeroVolumeOrderShouldBeRejected() {
        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateTrade(
                TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0d, 65000d, 0d, 0d),
                config()
        );

        assertFalse(decision.isAllowed());
        assertTrue(decision.getMessage().contains("手数"));
    }

    @Test
    public void batchShouldRejectWhenItemContainsInvalidCommand() {
        BatchTradePlan plan = new BatchTradePlan(
                "batch-risk-001",
                "BEST_EFFORT",
                "hedging",
                "批量平仓 BTCUSD",
                Arrays.asList(
                        new BatchTradeItem("item-1", "坏命令", null, new JSONObject())
                )
        );

        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateBatch(plan, config());

        assertFalse(decision.isAllowed());
        assertTrue(decision.getMessage().contains("无效命令"));
    }

    @Test
    public void validBatchShouldRemainAllowed() throws Exception {
        BatchTradePlan plan = new BatchTradePlan(
                "batch-risk-002",
                "BEST_EFFORT",
                "hedging",
                "反手 BTCUSD",
                Arrays.asList(buildBatchItem("item-1", 0.20d))
        );

        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateBatch(plan, config());

        assertTrue(decision.isAllowed());
        assertFalse(decision.isConfirmationRequired());
    }

    private static TradeRiskGuard.Config config() {
        return new TradeRiskGuard.Config(
                0.10d,
                999d,
                999,
                999d,
                true,
                true,
                true
        );
    }

    private static BatchTradeItem buildBatchItem(String itemId, double volume) throws Exception {
        JSONObject extras = new JSONObject();
        extras.put("positionTicket", 11L);
        TradeCommand command = TradeCommandFactory.closePosition("acc-1", "BTCUSD", 11L, volume, 0d);
        return new BatchTradeItem(itemId, "平仓 BTCUSD", command, extras);
    }
}
