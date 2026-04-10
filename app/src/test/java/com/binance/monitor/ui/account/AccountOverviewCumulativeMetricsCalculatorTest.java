package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AccountOverviewCumulativeMetricsCalculatorTest {

    @Test
    public void calculateShouldUseClosedTradesAndOpenPositionsAsSingleCumulativeTruth() {
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(1000L, "BTCUSD", "BTCUSD", "Buy",
                        100d, 1d, 100d, -3d, "",
                        40d, 900L, 1000L, -2d, 100d, 110d, 1L, 1L, 1L, 1),
                new TradeRecordItem(2000L, "XAUUSD", "XAUUSD", "Sell",
                        200d, 1d, 200d, -1d, "",
                        20d, 1900L, 2000L, 0d, 200d, 190d, 2L, 2L, 2L, 1)
        );
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTCUSD", "BTCUSD", "Buy", 11L, 21L,
                        1d, 1d, 100d, 110d, 110d, 0d,
                        3d, 50d, 0d, 0d, 0, 0d, 0d, 0d, -1d)
        );
        List<CurvePoint> curvePoints = Arrays.asList(
                new CurvePoint(500L, 1000d, 1000d),
                new CurvePoint(2500L, 1103d, 1080d)
        );

        AccountOverviewCumulativeMetricsCalculator.OverviewCumulativeValues values =
                AccountOverviewCumulativeMetricsCalculator.calculate(trades, positions, curvePoints);

        assertTrue(values.hasLocalTruth());
        assertTrue(values.hasCumulativePnlTruth());
        assertTrue(values.hasCumulativeReturnRateTruth());
        assertEquals(103d, values.getCumulativePnl(), 0.0001d);
        assertEquals(0.103d, values.getCumulativeReturnRate(), 0.0001d);
    }

    @Test
    public void calculateShouldUseCurveTruthWhenTradeHistoryIsTemporarilyUnavailable() {
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTCUSD", "BTCUSD", "Buy", 11L, 21L,
                        1d, 1d, 100d, 110d, 110d, 0d,
                        3d, 50d, 0d, 0d, 0, 0d, 0d, 0d, -1d)
        );
        List<CurvePoint> curvePoints = Arrays.asList(
                new CurvePoint(500L, 1000d, 1000d),
                new CurvePoint(2500L, 1050d, 1020d)
        );

        AccountOverviewCumulativeMetricsCalculator.OverviewCumulativeValues values =
                AccountOverviewCumulativeMetricsCalculator.calculate(null, positions, curvePoints);

        assertTrue(values.hasLocalTruth());
        assertTrue(values.hasCumulativePnlTruth());
        assertTrue(values.hasCumulativeReturnRateTruth());
        assertEquals(50d, values.getCumulativePnl(), 0.0001d);
        assertEquals(0.05d, values.getCumulativeReturnRate(), 0.0001d);
    }

    @Test
    public void calculateShouldNotOverrideCumulativeMetricsWithOnlyOpenPositionRuntime() {
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTCUSD", "BTCUSD", "Buy", 11L, 21L,
                        1d, 1d, 100d, 110d, 110d, 0d,
                        3d, 50d, 0d, 0d, 0, 0d, 0d, 0d, -1d)
        );

        AccountOverviewCumulativeMetricsCalculator.OverviewCumulativeValues values =
                AccountOverviewCumulativeMetricsCalculator.calculate(null, positions, null);

        assertFalse(values.hasLocalTruth());
        assertFalse(values.hasCumulativePnlTruth());
        assertFalse(values.hasCumulativeReturnRateTruth());
        assertEquals(0d, values.getCumulativePnl(), 0.0001d);
        assertEquals(0d, values.getCumulativeReturnRate(), 0.0001d);
    }

    @Test
    public void calculateShouldKeepCumulativePnlTruthWhenTradesExistButCurveMissing() {
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(1000L, "BTCUSD", "BTCUSD", "Buy",
                        100d, 1d, 100d, -3d, "",
                        40d, 900L, 1000L, -2d, 100d, 110d, 1L, 1L, 1L, 1)
        );
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTCUSD", "BTCUSD", "Buy", 11L, 21L,
                        1d, 1d, 100d, 110d, 110d, 0d,
                        3d, 50d, 0d, 0d, 0, 0d, 0d, 0d, -1d)
        );

        AccountOverviewCumulativeMetricsCalculator.OverviewCumulativeValues values =
                AccountOverviewCumulativeMetricsCalculator.calculate(trades, positions, null);

        assertTrue(values.hasLocalTruth());
        assertTrue(values.hasCumulativePnlTruth());
        assertFalse(values.hasCumulativeReturnRateTruth());
        assertEquals(84d, values.getCumulativePnl(), 0.0001d);
    }
}
