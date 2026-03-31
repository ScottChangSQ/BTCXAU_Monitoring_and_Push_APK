/*
 * 验证图表 K 线在官方 REST 不可用时仍能回退加载历史数据，
 * 避免图表页面一直停留在“正在加载数据”。
 */
package com.binance.monitor.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ChartKlineFallbackLoaderTest {

    // 当图表 REST 全部失败时，应回退到通用历史数据，避免图表空白。
    @Test
    public void loadFallsBackToHistoryWhenRestCandidatesAllFail() throws Exception {
        ChartKlineFallbackLoader loader = new ChartKlineFallbackLoader();
        AtomicInteger restAttempts = new AtomicInteger();
        AtomicInteger fallbackCalls = new AtomicInteger();
        List<CandleEntry> fallbackCandles = Arrays.asList(
                candle(2000L, 2999L, 102d),
                candle(1000L, 1999L, 101d)
        );

        List<CandleEntry> result = loader.load(
                Arrays.asList("https://fapi.binance.com/a", "https://fapi4.binance.com/b"),
                "BTCUSDT",
                null,
                url -> {
                    restAttempts.incrementAndGet();
                    throw new IOException("空响应体");
                },
                () -> {
                    fallbackCalls.incrementAndGet();
                    return fallbackCandles;
                }
        );

        assertEquals(2, restAttempts.get());
        assertEquals(1, fallbackCalls.get());
        assertEquals(2, result.size());
        assertEquals(1000L, result.get(0).getOpenTime());
        assertEquals(2000L, result.get(1).getOpenTime());
    }

    // 当 REST 和回退都失败时，应保留最后一条真实错误，方便页面展示原因。
    @Test
    public void loadThrowsRestErrorWhenFallbackAlsoEmpty() throws Exception {
        ChartKlineFallbackLoader loader = new ChartKlineFallbackLoader();
        try {
            loader.load(
                    Arrays.asList("https://fapi.binance.com/a", "https://fapi4.binance.com/b"),
                    "BTCUSDT",
                    null,
                    url -> {
                        throw new IOException("空响应体");
                    },
                    java.util.Collections::emptyList
            );
            fail("预期应抛出异常");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("图表K线REST请求失败"));
            assertTrue(e.getMessage().contains("fapi4.binance.com"));
        }
    }

    // 构造简单 K 线数据，供回退测试复用。
    private CandleEntry candle(long openTime, long closeTime, double close) {
        return new CandleEntry("BTCUSDT", openTime, closeTime, close, close, close, close, 1d, 1d);
    }

}
