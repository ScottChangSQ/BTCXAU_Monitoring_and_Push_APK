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

    // MT5 持仓代码与 Binance 行情代码不一致时，悬浮窗仍应显示对应产品盈亏数字。
    @Test
    public void buildSymbolCardsMatchesMt5SymbolsToBinanceCards() {
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTCUSD", "BTCUSD", "Sell", 1L, 11L,
                        0.05d, 0.05d, 66000d, 67000d, 3350d, 0.1d,
                        -10d, -20d, -0.03d, 0d, 0, 0d, 0d, 0d, 0d),
                new PositionItem("XAUUSD", "XAUUSD", "Buy", 2L, 12L,
                        0.01d, 0.01d, 4500d, 4510d, 45.1d, 0.01d,
                        2d, 4d, 0.001d, 0d, 0, 0d, 0d, 0d, 0d)
        );
        Map<String, com.binance.monitor.data.model.KlineData> latestKlines = new HashMap<>();
        latestKlines.put(AppConstants.SYMBOL_BTC, new com.binance.monitor.data.model.KlineData(
                AppConstants.SYMBOL_BTC,
                66_000d,
                67_123.4d,
                123d,
                456_000d,
                1_000L,
                2_000L,
                true
        ));
        latestKlines.put(AppConstants.SYMBOL_XAU, new com.binance.monitor.data.model.KlineData(
                AppConstants.SYMBOL_XAU,
                2_300d,
                2_329.5d,
                456d,
                789_000d,
                1_000L,
                2_000L,
                true
        ));

        List<FloatingSymbolCardData> cards = FloatingPositionAggregator.buildSymbolCards(
                positions,
                latestKlines,
                new HashMap<>(),
                true,
                true
        );

        assertEquals(2, cards.size());
        assertEquals(AppConstants.SYMBOL_BTC, cards.get(0).getCode());
        assertEquals("BTC", cards.get(0).getLabel());
        assertEquals(-20d, cards.get(0).getTotalPnl(), 0.0001d);
        assertEquals(AppConstants.SYMBOL_XAU, cards.get(1).getCode());
        assertEquals("XAU", cards.get(1).getLabel());
        assertEquals(4d, cards.get(1).getTotalPnl(), 0.0001d);
    }

    // 悬浮窗价格应优先使用实时价格缓存，不能继续落回旧的已收盘 K 线价格。
    @Test
    public void buildSymbolCardsPrefersRealtimePrices() {
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTCUSD", "BTCUSD", "Sell", 1L, 11L,
                        0.05d, 0.05d, 66000d, 67000d, 3350d, 0.1d,
                        -10d, -20d, -0.03d, 0d, 0, 0d, 0d, 0d, 0d)
        );
        Map<String, com.binance.monitor.data.model.KlineData> latestKlines = new HashMap<>();
        latestKlines.put(AppConstants.SYMBOL_BTC, new com.binance.monitor.data.model.KlineData(
                AppConstants.SYMBOL_BTC,
                66_000d,
                67_000d,
                123d,
                456_000d,
                1_000L,
                2_000L,
                true
        ));
        Map<String, Double> latestPrices = new HashMap<>();
        latestPrices.put(AppConstants.SYMBOL_BTC, 67_123.4d);

        List<FloatingSymbolCardData> cards = FloatingPositionAggregator.buildSymbolCards(
                positions,
                latestKlines,
                latestPrices,
                true,
                true
        );

        assertEquals(1, cards.size());
        assertEquals(67_123.4d, cards.get(0).getLatestPrice(), 0.0001d);
    }
}
