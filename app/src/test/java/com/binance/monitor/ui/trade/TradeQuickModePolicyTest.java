package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.junit.Test;

public class TradeQuickModePolicyTest {

    @Test
    public void oneClickModeShouldAllowQuickOpenAndQuickPending() {
        TradeCommand quickMarket = TradeCommandFactory.withEntryMode(
                TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0.05d, 65000d, 0d, 0d),
                "quick"
        );
        TradeCommand quickPending = TradeCommandFactory.withEntryMode(
                TradeCommandFactory.pendingAdd("acc-1", "BTCUSD", "buy_limit", 0.05d, 64000d, 0d, 0d),
                "quick"
        );

        TradeRiskGuard.Config config = new TradeRiskGuard.Config(0.10d, 999d, 999, 999d, true, true, true);

        assertTrue(TradeQuickModePolicy.shouldAllowQuickMode(quickMarket, true, config));
        assertTrue(TradeQuickModePolicy.shouldAllowQuickMode(quickPending, true, config));
    }

    @Test
    public void oneClickModeShouldControlCloseAndCancelFastPath() {
        TradeCommand closeCommand = TradeCommandFactory.closePosition("acc-1", "BTCUSD", 1001L, 0.05d, 0d);
        TradeCommand cancelCommand = TradeCommandFactory.pendingCancel("acc-1", "BTCUSD", 2001L);

        TradeRiskGuard.Config enabledConfig = new TradeRiskGuard.Config(0.10d, 999d, 999, 999d, true, true, true);
        TradeRiskGuard.Config disabledConfig = new TradeRiskGuard.Config(0.10d, 999d, 999, 999d, true, true, false);

        assertTrue(TradeQuickModePolicy.shouldAllowQuickMode(closeCommand, true, enabledConfig));
        assertTrue(TradeQuickModePolicy.shouldAllowQuickMode(cancelCommand, true, enabledConfig));
        assertFalse(TradeQuickModePolicy.shouldAllowQuickMode(closeCommand, false, enabledConfig));
        assertFalse(TradeQuickModePolicy.shouldAllowQuickMode(cancelCommand, false, disabledConfig));
    }

    @Test
    public void nonQuickOpenShouldStillRequireNormalConfirmationPath() {
        TradeCommand normalOpen = TradeCommandFactory.openMarket(
                "acc-1",
                "BTCUSD",
                "buy",
                0.05d,
                65000d,
                0d,
                0d
        );

        assertFalse(TradeQuickModePolicy.shouldAllowQuickMode(
                normalOpen,
                true,
                new TradeRiskGuard.Config(0.10d, 999d, 999, 999d, true, true, true)
        ));
    }
}
