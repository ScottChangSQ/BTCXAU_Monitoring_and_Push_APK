package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.junit.Test;

public class TradeQuickModePolicyTest {

    @Test
    public void onlySmallQuickMarketOrdersShouldSkipConfirmation() {
        TradeCommand quickMarket = TradeCommandFactory.withEntryMode(
                TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0.05d, 65000d, 0d, 0d),
                "quick"
        );
        TradeCommand largeQuickMarket = TradeCommandFactory.withEntryMode(
                TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 1.50d, 65000d, 0d, 0d),
                "quick"
        );

        TradeRiskGuard.Config config = new TradeRiskGuard.Config(0.10d, 1.00d, 4, 2.00d, true, true);

        assertTrue(TradeQuickModePolicy.shouldAllowQuickMode(quickMarket, true, config));
        assertFalse(TradeQuickModePolicy.shouldAllowQuickMode(largeQuickMarket, true, config));
    }

    @Test
    public void configuredQuickThresholdShouldOverrideLegacyFixedVolume() {
        TradeCommand quickMarket = TradeCommandFactory.withEntryMode(
                TradeCommandFactory.openMarket("acc-1", "BTCUSD", "buy", 0.05d, 65000d, 0d, 0d),
                "quick"
        );

        assertFalse(TradeQuickModePolicy.shouldAllowQuickMode(
                quickMarket,
                true,
                new TradeRiskGuard.Config(0.02d, 1.00d, 4, 2.00d, true, true)
        ));
    }
}
