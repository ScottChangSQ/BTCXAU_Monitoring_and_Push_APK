package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.junit.Test;

public class ChartQuickTradeCoordinatorTest {

    @Test
    public void marketBuyShouldBuildOpenMarketBuyCommand() {
        FakeExecutor executor = new FakeExecutor();
        ChartQuickTradeCoordinator coordinator = new ChartQuickTradeCoordinator(
                () -> "acc-1",
                () -> "BTCUSD",
                () -> 65000d,
                executor
        );

        coordinator.executeMarketBuy("0.05");

        TradeCommand command = executor.lastCommand;
        assertEquals("OPEN_MARKET", command.getAction());
        assertEquals("buy", command.getParams().optString("side", ""));
        assertEquals(0.05d, command.getVolume(), 0.0000001d);
    }

    @Test
    public void pendingSellAboveCurrentShouldResolveSellLimit() {
        FakeExecutor executor = new FakeExecutor();
        ChartQuickTradeCoordinator coordinator = new ChartQuickTradeCoordinator(
                () -> "acc-1",
                () -> "BTCUSD",
                () -> 65000d,
                executor
        );

        coordinator.executePendingSell("0.05", 65100d);

        assertEquals("sell_limit", executor.lastCommand.getParams().optString("orderType", ""));
    }

    @Test
    public void pendingLineTooCloseShouldReject() {
        FakeExecutor executor = new FakeExecutor();
        ChartQuickTradeCoordinator coordinator = new ChartQuickTradeCoordinator(
                () -> "acc-1",
                () -> "BTCUSD",
                () -> 65000d,
                executor
        );

        try {
            coordinator.executePendingBuy("0.05", 65000d);
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains("挂单线"));
        }
    }

    private static final class FakeExecutor implements ChartQuickTradeCoordinator.Executor {
        private TradeCommand lastCommand;

        @Override
        public void execute(TradeCommand command) {
            lastCommand = command;
        }
    }
}
