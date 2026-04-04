/*
 * 账户曲线重建测试，确保结余只在平仓时跳变，净值会在持仓期间与结余分离。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AccountCurveRebuildHelperTest {

    @Test
    public void rebuildShouldKeepBalanceFlatUntilTradeCloses() {
        List<CurvePoint> source = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(2_000L, 100d, 100d, 0.20d),
                new CurvePoint(3_000L, 100d, 100d, 0.00d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        3_000L,
                        "BTC",
                        "BTC",
                        "买入",
                        100d,
                        1d,
                        100d,
                        0d,
                        "",
                        20d,
                        1_000L,
                        3_000L,
                        0d,
                        100d,
                        120d,
                        1L,
                        1L,
                        1L,
                        1
                )
        );

        List<CurvePoint> rebuilt = AccountCurveRebuildHelper.rebuild(source, trades, 100d);

        assertEquals(100d, rebuilt.get(0).getBalance(), 1e-9);
        assertEquals(100d, rebuilt.get(1).getBalance(), 1e-9);
        assertEquals(120d, rebuilt.get(2).getBalance(), 1e-9);
        assertEquals(100d, rebuilt.get(0).getEquity(), 1e-9);
        assertEquals(110d, rebuilt.get(1).getEquity(), 1e-9);
        assertEquals(120d, rebuilt.get(2).getEquity(), 1e-9);
    }

    @Test
    public void rebuildShouldPreferSourceFloatingSpreadWhenItExists() {
        List<CurvePoint> source = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(2_000L, 118d, 100d, 0.20d),
                new CurvePoint(3_000L, 120d, 100d, 0.00d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        3_000L,
                        "BTC",
                        "BTC",
                        "买入",
                        100d,
                        1d,
                        100d,
                        0d,
                        "",
                        20d,
                        1_000L,
                        3_000L,
                        0d,
                        100d,
                        120d,
                        1L,
                        1L,
                        1L,
                        1
                )
        );

        List<CurvePoint> rebuilt = AccountCurveRebuildHelper.rebuild(source, trades, 100d);

        assertEquals(100d, rebuilt.get(0).getBalance(), 1e-9);
        assertEquals(100d, rebuilt.get(1).getBalance(), 1e-9);
        assertEquals(120d, rebuilt.get(2).getBalance(), 1e-9);
        assertEquals(100d, rebuilt.get(0).getEquity(), 1e-9);
        assertEquals(118d, rebuilt.get(1).getEquity(), 1e-9);
        assertEquals(120d, rebuilt.get(2).getEquity(), 1e-9);
    }

    @Test
    public void rebuildShouldKeepAlreadySmoothServerCurveUntouched() {
        List<CurvePoint> source = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(2_000L, 118d, 100d, 0.20d),
                new CurvePoint(2_500L, 119d, 100d, 0.18d),
                new CurvePoint(3_000L, 120d, 120d, 0.00d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        3_000L,
                        "BTC",
                        "BTC",
                        "买入",
                        100d,
                        1d,
                        100d,
                        0d,
                        "",
                        20d,
                        1_000L,
                        3_000L,
                        0d,
                        100d,
                        120d,
                        1L,
                        1L,
                        1L,
                        1
                )
        );

        List<CurvePoint> rebuilt = AccountCurveRebuildHelper.rebuild(source, trades, 100d);

        assertEquals(100d, rebuilt.get(1).getBalance(), 1e-9);
        assertEquals(100d, rebuilt.get(2).getBalance(), 1e-9);
        assertEquals(118d, rebuilt.get(1).getEquity(), 1e-9);
        assertEquals(119d, rebuilt.get(2).getEquity(), 1e-9);
        assertEquals(120d, rebuilt.get(3).getBalance(), 1e-9);
    }

    @Test
    public void rebuildShouldIgnoreOutlierSourceFloatingSpread() {
        List<CurvePoint> source = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(2_000L, 5_100d, 100d, 0.20d),
                new CurvePoint(3_000L, 120d, 100d, 0.00d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        3_000L,
                        "BTC",
                        "BTC",
                        "买入",
                        100d,
                        1d,
                        100d,
                        0d,
                        "",
                        20d,
                        1_000L,
                        3_000L,
                        0d,
                        100d,
                        120d,
                        1L,
                        1L,
                        1L,
                        1
                )
        );

        List<CurvePoint> rebuilt = AccountCurveRebuildHelper.rebuild(source, trades, 100d);

        assertEquals(110d, rebuilt.get(1).getEquity(), 1e-9);
    }

    @Test
    public void rebuildShouldRejectSourceFloatingWhenItDeviatesTooMuchFromSimulatedValue() {
        List<CurvePoint> source = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(2_000L, 450d, 100d, 0.20d),
                new CurvePoint(3_000L, 120d, 100d, 0.00d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        3_000L,
                        "BTC",
                        "BTC",
                        "买入",
                        100d,
                        1d,
                        100d,
                        0d,
                        "",
                        20d,
                        1_000L,
                        3_000L,
                        0d,
                        100d,
                        120d,
                        1L,
                        1L,
                        1L,
                        1
                )
        );

        List<CurvePoint> rebuilt = AccountCurveRebuildHelper.rebuild(source, trades, 100d);

        assertEquals(110d, rebuilt.get(1).getEquity(), 1e-9);
        assertEquals(100d, rebuilt.get(1).getBalance(), 1e-9);
    }

    @Test
    public void rebuildShouldKeepSmoothCurveUntouchedWhenTradesOverlap() {
        List<CurvePoint> source = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(1_500L, 106d, 100d, 0.18d),
                new CurvePoint(2_000L, 110d, 110d, 0.20d),
                new CurvePoint(2_500L, 123d, 110d, 0.16d),
                new CurvePoint(3_000L, 130d, 130d, 0.00d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        2_000L,
                        "BTC",
                        "BTC",
                        "买入",
                        100d,
                        1d,
                        100d,
                        0d,
                        "",
                        10d,
                        1_000L,
                        2_000L,
                        0d,
                        100d,
                        110d,
                        1L,
                        1L,
                        1L,
                        1
                ),
                new TradeRecordItem(
                        3_000L,
                        "BTC",
                        "BTC",
                        "买入",
                        100d,
                        1d,
                        100d,
                        0d,
                        "",
                        20d,
                        1_500L,
                        3_000L,
                        0d,
                        100d,
                        120d,
                        2L,
                        2L,
                        2L,
                        1
                )
        );

        List<CurvePoint> rebuilt = AccountCurveRebuildHelper.rebuild(source, trades, 100d);

        assertEquals(106d, rebuilt.get(1).getEquity(), 1e-9);
        assertEquals(100d, rebuilt.get(1).getBalance(), 1e-9);
        assertEquals(110d, rebuilt.get(2).getEquity(), 1e-9);
        assertEquals(110d, rebuilt.get(2).getBalance(), 1e-9);
        assertEquals(123d, rebuilt.get(3).getEquity(), 1e-9);
        assertEquals(110d, rebuilt.get(3).getBalance(), 1e-9);
        assertEquals(130d, rebuilt.get(4).getEquity(), 1e-9);
        assertEquals(130d, rebuilt.get(4).getBalance(), 1e-9);
    }
}
