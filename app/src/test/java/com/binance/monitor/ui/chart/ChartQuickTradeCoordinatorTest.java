package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.v2.trade.TradeCommand;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

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
        assertEquals("quick", command.getParams().optString("entryMode", ""));
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
    public void pendingLineBelowCurrentShouldResolveBuyDirection() {
        assertEquals(
                ChartQuickTradeCoordinator.PendingDirection.BUY,
                ChartQuickTradeCoordinator.resolvePendingDirection(64900d, 65000d)
        );
    }

    @Test
    public void pendingLineAboveCurrentShouldResolveSellDirection() {
        assertEquals(
                ChartQuickTradeCoordinator.PendingDirection.SELL,
                ChartQuickTradeCoordinator.resolvePendingDirection(65100d, 65000d)
        );
    }

    @Test
    public void pendingLineAtCurrentShouldResolveNoneDirection() {
        assertEquals(
                ChartQuickTradeCoordinator.PendingDirection.NONE,
                ChartQuickTradeCoordinator.resolvePendingDirection(65000d, 65000d)
        );
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

    @Test
    public void quickTradeCoordinatorShouldRemainSingleTradeBoundary() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("TradeCommandFactory.openMarket("));
        assertTrue(source.contains("TradeCommandFactory.pendingAdd("));
        assertTrue(source.contains("executor.execute("));
        assertTrue(source.contains("enum PendingDirection"));
        assertTrue(source.contains("static PendingDirection resolvePendingDirection(double linePrice, double currentPrice)"));
        assertTrue(!source.contains("BatchTradeCoordinator"));
    }

    private static final class FakeExecutor implements ChartQuickTradeCoordinator.Executor {
        private TradeCommand lastCommand;

        @Override
        public void execute(TradeCommand command) {
            lastCommand = command;
        }
    }
}
