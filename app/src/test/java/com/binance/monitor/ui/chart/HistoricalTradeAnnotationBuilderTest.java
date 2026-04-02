/*
 * 历史成交图表标记构建器测试，确保只把已平仓历史成交映射到当前 K 线窗口。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HistoricalTradeAnnotationBuilderTest {
    private static final long BASE_TIME = 1_000_000L;

    @Test
    public void shouldOnlyKeepClosedTradesForSelectedSymbol() {
        List<CandleEntry> candles = Arrays.asList(
                buildCandle(BASE_TIME, BASE_TIME + 60_000L),
                buildCandle(BASE_TIME + 60_000L, BASE_TIME + 120_000L)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                buildTrade("BTCUSDT", "buy", BASE_TIME, BASE_TIME + 20_000L, 100d, 101d, 12d, 0d, 1L),
                buildTrade("XAUUSD", "sell", BASE_TIME, BASE_TIME + 30_000L, 200d, 199d, -5d, 0d, 2L),
                buildTrade("BTCUSDT", "sell", BASE_TIME + 60_000L, 0L, 103d, 0d, 8d, 0d, 3L)
        );

        List<HistoricalTradeAnnotationBuilder.TradeAnnotation> annotations =
                HistoricalTradeAnnotationBuilder.build("BTCUSDT", trades, candles);

        assertEquals(1, annotations.size());
        assertEquals(BASE_TIME, annotations.get(0).anchorTimeMs);
        assertEquals(101d, annotations.get(0).price, 1e-9);
    }

    @Test
    public void shouldMapCloseTimeIntoVisibleCandleBucket() {
        List<CandleEntry> candles = Arrays.asList(
                buildCandle(BASE_TIME, BASE_TIME + 60_000L),
                buildCandle(BASE_TIME + 60_000L, BASE_TIME + 120_000L),
                buildCandle(BASE_TIME + 120_000L, BASE_TIME + 180_000L)
        );
        TradeRecordItem trade = buildTrade(
                "BTCUSD",
                "sell",
                BASE_TIME,
                BASE_TIME + 95_000L,
                101d,
                99d,
                -18d,
                -2d,
                88L
        );

        List<HistoricalTradeAnnotationBuilder.TradeAnnotation> annotations =
                HistoricalTradeAnnotationBuilder.build("BTCUSDT", Collections.singletonList(trade), candles);

        assertEquals(1, annotations.size());
        assertEquals(BASE_TIME + 60_000L, annotations.get(0).anchorTimeMs);
        assertEquals(99d, annotations.get(0).price, 1e-9);
    }

    @Test
    public void shouldBuildReadableLabelAndStableGroupId() {
        List<CandleEntry> candles = Collections.singletonList(buildCandle(BASE_TIME, BASE_TIME + 60_000L));
        TradeRecordItem trade = buildTrade(
                "BTCUSDT",
                "buy",
                BASE_TIME,
                BASE_TIME + 30_000L,
                100d,
                104d,
                25.5d,
                -1.5d,
                777L
        );

        List<HistoricalTradeAnnotationBuilder.TradeAnnotation> annotations =
                HistoricalTradeAnnotationBuilder.build("BTCUSDT", Collections.singletonList(trade), candles);

        assertEquals(1, annotations.size());
        assertEquals("BUY +$24", annotations.get(0).label);
        assertTrue(annotations.get(0).groupId.startsWith("tradehist|"));
        assertTrue(annotations.get(0).color != 0);
    }

    @Test
    public void shouldIgnoreTradesOutsideVisibleCandles() {
        List<CandleEntry> candles = Collections.singletonList(buildCandle(BASE_TIME, BASE_TIME + 60_000L));
        TradeRecordItem trade = buildTrade(
                "BTCUSDT",
                "buy",
                BASE_TIME,
                BASE_TIME + 180_000L,
                100d,
                104d,
                10d,
                0d,
                123L
        );

        List<HistoricalTradeAnnotationBuilder.TradeAnnotation> annotations =
                HistoricalTradeAnnotationBuilder.build("BTCUSDT", Collections.singletonList(trade), candles);

        assertTrue(annotations.isEmpty());
    }

    @Test
    public void shouldUseSellColorForSellTrades() {
        List<CandleEntry> candles = Collections.singletonList(buildCandle(BASE_TIME, BASE_TIME + 60_000L));
        TradeRecordItem buyTrade = buildTrade(
                "BTCUSDT",
                "buy",
                BASE_TIME,
                BASE_TIME + 20_000L,
                100d,
                101d,
                5d,
                0d,
                1L
        );
        TradeRecordItem sellTrade = buildTrade(
                "BTCUSDT",
                "sell",
                BASE_TIME,
                BASE_TIME + 30_000L,
                100d,
                99d,
                5d,
                0d,
                2L
        );

        List<HistoricalTradeAnnotationBuilder.TradeAnnotation> annotations =
                HistoricalTradeAnnotationBuilder.build("BTCUSDT", Arrays.asList(buyTrade, sellTrade), candles);

        assertEquals(2, annotations.size());
        assertFalse(annotations.get(0).color == annotations.get(1).color);
    }

    private CandleEntry buildCandle(long openTime, long closeTime) {
        return new CandleEntry("BTCUSDT", openTime, closeTime, 100d, 101d, 99d, 100d, 1d, 1d);
    }

    private TradeRecordItem buildTrade(String code,
                                       String side,
                                       long openTime,
                                       long closeTime,
                                       double openPrice,
                                       double closePrice,
                                       double profit,
                                       double storageFee,
                                       long dealTicket) {
        return new TradeRecordItem(
                closeTime > 0L ? closeTime : openTime,
                code,
                code,
                side,
                closePrice > 0d ? closePrice : openPrice,
                1d,
                100d,
                0d,
                "",
                profit,
                openTime,
                closeTime,
                storageFee,
                openPrice,
                closePrice,
                dealTicket,
                dealTicket,
                dealTicket,
                1
        );
    }
}
