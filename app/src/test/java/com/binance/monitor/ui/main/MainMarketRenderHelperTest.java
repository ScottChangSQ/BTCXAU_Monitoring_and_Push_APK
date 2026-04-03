/*
 * 验证行情持仓页只会在行情真正变化时重绘，避免切页恢复时重复刷新同一份数据。
 */
package com.binance.monitor.ui.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.KlineData;

import org.junit.Test;

public class MainMarketRenderHelperTest {

    @Test
    public void shouldRenderReturnsFalseWhenSignatureDoesNotChange() {
        String signature = MainMarketRenderHelper.buildRenderSignature(
                "BTCUSDT",
                65000d,
                kline(1_000L, 1_999L, 64900d, 65100d, 64800d, 65000d, 12d, 120d)
        );

        assertFalse(MainMarketRenderHelper.shouldRender(signature, signature));
    }

    @Test
    public void shouldRenderReturnsTrueWhenPriceChanges() {
        String previous = MainMarketRenderHelper.buildRenderSignature(
                "BTCUSDT",
                65000d,
                kline(1_000L, 1_999L, 64900d, 65100d, 64800d, 65000d, 12d, 120d)
        );
        String next = MainMarketRenderHelper.buildRenderSignature(
                "BTCUSDT",
                65010d,
                kline(1_000L, 1_999L, 64900d, 65100d, 64800d, 65000d, 12d, 120d)
        );

        assertTrue(MainMarketRenderHelper.shouldRender(previous, next));
    }

    @Test
    public void shouldRenderReturnsTrueWhenKlineChanges() {
        String previous = MainMarketRenderHelper.buildRenderSignature(
                "XAUUSDT",
                2320d,
                kline(2_000L, 2_999L, 2318d, 2321d, 2317d, 2320d, 5d, 50d)
        );
        String next = MainMarketRenderHelper.buildRenderSignature(
                "XAUUSDT",
                2320d,
                kline(3_000L, 3_999L, 2320d, 2325d, 2319d, 2324d, 8d, 80d)
        );

        assertTrue(MainMarketRenderHelper.shouldRender(previous, next));
    }

    private KlineData kline(long openTime,
                            long closeTime,
                            double open,
                            double high,
                            double low,
                            double close,
                            double volume,
                            double amount) {
        return new KlineData("BTCUSDT", open, high, low, close, volume, amount, openTime, closeTime, true);
    }
}
