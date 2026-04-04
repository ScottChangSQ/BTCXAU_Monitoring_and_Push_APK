package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.PositionItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AccountOverviewMetricsCalculatorTest {

    @Test
    public void calculateShouldUseUserSpecifiedOverviewFormulas() {
        List<AccountMetric> overview = Arrays.asList(
                new AccountMetric("预付款", "$200.00"),
                new AccountMetric("杠杆", "100x")
        );
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTC", "BTCUSDT", "Buy", 1L, 11L,
                        0.5d, 0.5d, 60000d, 70000d, 99999d, 0d,
                        10d, 300d, 0d, 0d, 0, 0d, 0d, 0d, 50d),
                new PositionItem("XAU", "XAUUSDT", "Sell", 2L, 12L,
                        2d, 2d, 2300d, 2400d, 88888d, 0d,
                        5d, -100d, 0d, 0d, 0, 0d, 0d, 0d, -20d)
        );

        AccountOverviewMetricsCalculator.OverviewValues values = AccountOverviewMetricsCalculator.calculate(
                1000d,
                800d,
                overview,
                positions
        );

        assertEquals(200d, values.getPrepayment(), 0.0001d);
        assertEquals(600d, values.getFreePrepayment(), 0.0001d);
        assertEquals(39800d, values.getMarketValue(), 0.0001d);
        assertEquals(230d, values.getPositionPnl(), 0.0001d);
        assertEquals(4975d, values.getPositionRatio(), 0.0001d);
        assertEquals(0.23d, values.getPositionPnlRate(), 0.0001d);
    }
}
