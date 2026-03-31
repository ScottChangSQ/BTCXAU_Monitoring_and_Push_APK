/*
 * 验证悬浮窗聚合结果既能汇总总盈亏，也能按 BTC/XAU 行情开关筛选展示。
 */
package com.binance.monitor.ui.floating;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class FloatingPositionAggregatorVisibilityTest {

    @Test
    public void sumTotalPnlAggregatesAllVisibleItems() {
        List<FloatingPositionPnlItem> items = Arrays.asList(
                new FloatingPositionPnlItem("BTCUSD", "BTCUSD", -28d),
                new FloatingPositionPnlItem("XAUUSD", "XAUUSD", 4d),
                new FloatingPositionPnlItem("NAS100", "NAS100", 12.5d)
        );

        assertEquals(-11.5d, FloatingPositionAggregator.sumTotalPnl(items), 0.0001d);
    }

    @Test
    public void filterMarketSymbolsHonorsBtcAndXauSwitches() {
        List<String> codes = FloatingPositionAggregator.filterMarketSymbols(
                Arrays.asList("BTCUSDT", "XAUUSDT", "ETHUSDT"),
                true,
                false
        );

        assertEquals(2, codes.size());
        assertEquals("BTCUSDT", codes.get(0));
        assertEquals("ETHUSDT", codes.get(1));
    }
}
