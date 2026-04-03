/*
 * 实时 1 分钟 K 线组装器测试，确保 aggTrade 流能稳定转换成当前分钟和已收盘分钟。
 */
package com.binance.monitor.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.TradeTickData;

import org.junit.Test;

import java.util.List;

public class RealtimeMinuteKlineAssemblerTest {
    private static final long MINUTE = 60_000L;
    private static final long BASE_TIME = 1_700_000_040_000L;

    @Test
    public void applyTickShouldMergeTradesWithinSameMinute() {
        RealtimeMinuteKlineAssembler assembler = new RealtimeMinuteKlineAssembler();

        List<KlineData> first = assembler.applyTick(tick("BTCUSDT", 100d, 1d, BASE_TIME));
        List<KlineData> second = assembler.applyTick(tick("BTCUSDT", 102d, 2d, BASE_TIME + 15_000L));

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        KlineData merged = second.get(0);
        assertTrue(!merged.isClosed());
        assertEquals(BASE_TIME - Math.floorMod(BASE_TIME, MINUTE), merged.getOpenTime());
        assertEquals(100d, merged.getOpenPrice(), 1e-9);
        assertEquals(102d, merged.getHighPrice(), 1e-9);
        assertEquals(100d, merged.getLowPrice(), 1e-9);
        assertEquals(102d, merged.getClosePrice(), 1e-9);
        assertEquals(3d, merged.getVolume(), 1e-9);
        assertEquals(304d, merged.getQuoteAssetVolume(), 1e-9);
    }

    @Test
    public void applyTickShouldEmitClosedPreviousMinuteAndOpenNextMinuteWhenMinuteRolls() {
        RealtimeMinuteKlineAssembler assembler = new RealtimeMinuteKlineAssembler();

        assembler.applyTick(tick("BTCUSDT", 100d, 1d, BASE_TIME));
        List<KlineData> rolled = assembler.applyTick(tick("BTCUSDT", 105d, 2d, BASE_TIME + MINUTE));

        assertEquals(2, rolled.size());

        KlineData closed = rolled.get(0);
        assertTrue(closed.isClosed());
        assertEquals(BASE_TIME - Math.floorMod(BASE_TIME, MINUTE), closed.getOpenTime());
        assertEquals(100d, closed.getOpenPrice(), 1e-9);
        assertEquals(100d, closed.getClosePrice(), 1e-9);

        KlineData opened = rolled.get(1);
        assertTrue(!opened.isClosed());
        assertEquals(closed.getOpenTime() + MINUTE, opened.getOpenTime());
        assertEquals(105d, opened.getOpenPrice(), 1e-9);
        assertEquals(105d, opened.getClosePrice(), 1e-9);
        assertEquals(2d, opened.getVolume(), 1e-9);
    }

    @Test
    public void applyTickShouldIgnoreOutOfOrderOlderMinuteTrades() {
        RealtimeMinuteKlineAssembler assembler = new RealtimeMinuteKlineAssembler();

        assembler.applyTick(tick("BTCUSDT", 100d, 1d, BASE_TIME + MINUTE));
        List<KlineData> ignored = assembler.applyTick(tick("BTCUSDT", 99d, 1d, BASE_TIME));

        assertTrue(ignored.isEmpty());
    }

    private TradeTickData tick(String symbol, double price, double quantity, long tradeTime) {
        return new TradeTickData(symbol, price, quantity, tradeTime, tradeTime);
    }
}
