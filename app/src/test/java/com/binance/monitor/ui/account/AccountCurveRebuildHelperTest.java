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
}
