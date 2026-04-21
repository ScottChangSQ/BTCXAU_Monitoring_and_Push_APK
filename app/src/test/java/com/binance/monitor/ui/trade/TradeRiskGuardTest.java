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
    public void smallMarketOrderShouldAllowQuickMode() {
        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateTrade(
                TradeCommandFactory.withEntryMode(
                        TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0.05d, 65000d, 0d, 0d),
                        "quick"
                ),
                config(0.10d, 1.00d, 4, 2.00d)
        );

        assertTrue(decision.isAllowed());
        assertFalse(decision.isConfirmationRequired());
    }

    @Test
    public void largeMarketOrderShouldRequireConfirmation() {
        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateTrade(
                TradeCommandFactory.withEntryMode(
                        TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0.50d, 65000d, 0d, 0d),
                        "quick"
                ),
                config(0.10d, 1.00d, 4, 2.00d)
        );

        assertTrue(decision.isAllowed());
        assertTrue(decision.isConfirmationRequired());
    }

    @Test
    public void overSizedMarketOrderShouldBeRejected() {
        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateTrade(
                TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 1.50d, 65000d, 0d, 0d),
                config(0.10d, 1.00d, 4, 2.00d)
        );

        assertFalse(decision.isAllowed());
        assertTrue(decision.getMessage().contains("单笔市价"));
    }

    @Test
    public void batchShouldRejectWhenItemCountExceedsLimit() throws Exception {
        BatchTradePlan plan = new BatchTradePlan(
                "batch-risk-001",
                "BEST_EFFORT",
                "hedging",
                "批量平仓 BTCUSD",
                Arrays.asList(
                        buildBatchItem("item-1", 0.20d),
                        buildBatchItem("item-2", 0.20d),
                        buildBatchItem("item-3", 0.20d)
                )
        );

        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateBatch(plan, config(0.10d, 1.00d, 2, 2.00d));

        assertFalse(decision.isAllowed());
        assertTrue(decision.getMessage().contains("批量项数"));
    }

    @Test
    public void reverseBatchShouldForceConfirmation() throws Exception {
        BatchTradePlan plan = new BatchTradePlan(
                "batch-risk-002",
                "BEST_EFFORT",
                "hedging",
                "反手 BTCUSD",
                Arrays.asList(buildBatchItem("item-1", 0.20d))
        );

        TradeRiskGuard.Decision decision = TradeRiskGuard.evaluateBatch(plan, config(0.10d, 1.00d, 4, 2.00d));

        assertTrue(decision.isAllowed());
        assertTrue(decision.isConfirmationRequired());
    }

    private static TradeRiskGuard.Config config(double maxQuickVolume,
                                                double maxSingleVolume,
                                                int maxBatchItems,
                                                double maxBatchVolume) {
        return new TradeRiskGuard.Config(
                maxQuickVolume,
                maxSingleVolume,
                maxBatchItems,
                maxBatchVolume,
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
