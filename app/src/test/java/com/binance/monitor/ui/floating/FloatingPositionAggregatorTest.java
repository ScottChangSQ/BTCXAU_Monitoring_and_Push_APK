/*
 * 验证悬浮窗会把同一产品的多笔持仓盈亏聚合到一起，
 * 避免一个产品出现多行重复且盈亏总额不准确。
 */
package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.ui.account.model.PositionItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FloatingPositionAggregatorTest {

    // 同一产品的多笔持仓应合并成一条，并汇总总盈亏。
    @Test
    public void aggregateGroupsByCodeAndSumsTotalPnl() {
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTCUSD", "BTCUSD", "Sell", 1L, 11L,
                        0.05d, 0.05d, 66000d, 67000d, 3350d, 0.1d,
                        -10d, -20d, -0.03d, 0d, 0, 0d, 0d, 0d, 0d),
                new PositionItem("BTCUSD", "BTCUSD", "Sell", 2L, 12L,
                        0.05d, 0.05d, 66500d, 67000d, 3350d, 0.1d,
                        -5d, -8d, -0.01d, 0d, 0, 0d, 0d, 0d, 0d),
                new PositionItem("XAUUSD", "XAUUSD", "Buy", 3L, 13L,
                        0.01d, 0.01d, 4500d, 4510d, 45.1d, 0.01d,
                        2d, 4d, 0.001d, 0d, 0, 0d, 0d, 0d, 0d)
        );

        List<FloatingPositionPnlItem> items = FloatingPositionAggregator.aggregate(positions);

        assertEquals(2, items.size());
        assertEquals("BTCUSD", items.get(0).getCode());
        assertEquals(-28d, items.get(0).getTotalPnl(), 0.0001d);
        assertEquals("XAUUSD", items.get(1).getCode());
        assertEquals(4d, items.get(1).getTotalPnl(), 0.0001d);
    }

    // 行情价格应按 BTC/XAU 开关过滤，并回填到对应产品行。
    @Test
    public void aggregateCarriesLatestPricesAndAppliesSymbolSwitches() {
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTCUSD", "BTCUSD", "Sell", 1L, 11L,
                        0.05d, 0.05d, 66000d, 67000d, 3350d, 0.1d,
                        -10d, -20d, -0.03d, 0d, 0, 0d, 0d, 0d, 0d),
                new PositionItem("XAUUSD", "XAUUSD", "Buy", 2L, 12L,
                        0.01d, 0.01d, 4500d, 4510d, 45.1d, 0.01d,
                        2d, 4d, 0.001d, 0d, 0, 0d, 0d, 0d, 0d),
                new PositionItem("EURUSD", "EURUSD", "Buy", 3L, 13L,
                        0.10d, 0.10d, 1.08d, 1.09d, 0.109d, 0.01d,
                        1d, 3d, 0.001d, 0d, 0, 0d, 0d, 0d, 0d)
        );
        Map<String, Double> latestPrices = new HashMap<>();
        latestPrices.put(AppConstants.SYMBOL_BTC, 67123.4d);
        latestPrices.put(AppConstants.SYMBOL_XAU, 2329.5d);

        List<FloatingPositionPnlItem> items = FloatingPositionAggregator.aggregate(
                positions,
                latestPrices,
                false,
                true
        );

        assertEquals(2, items.size());
        assertEquals("EURUSD", items.get(0).getCode());
        assertFalse(items.get(0).hasMarketPrice());
        assertEquals("XAUUSD", items.get(1).getCode());
        assertTrue(items.get(1).hasMarketPrice());
        assertEquals(2329.5d, items.get(1).getMarketPrice(), 0.0001d);
    }
}
