/*
 * 实时 1 分钟 K 线组装器，负责把成交流本地汇总成未收盘/已收盘 K 线。
 * WebSocketManager 通过这里把 aggTrade 流转换成图表和异常判断可复用的 KlineData。
 */
package com.binance.monitor.data.remote;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.TradeTickData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class RealtimeMinuteKlineAssembler {
    private static final long ONE_MINUTE_MS = 60_000L;

    private final Map<String, KlineData> currentMinuteCandles = new ConcurrentHashMap<>();

    // 把一笔实时成交并入当前 1 分钟 K 线；若跨分钟则先吐出上一根已收盘，再吐出新的未收盘。
    List<KlineData> applyTick(@Nullable TradeTickData tick) {
        List<KlineData> out = new ArrayList<>();
        if (tick == null || tick.getSymbol() == null || tick.getSymbol().trim().isEmpty()) {
            return out;
        }
        String symbol = tick.getSymbol().trim().toUpperCase(Locale.ROOT);
        double price = tick.getPrice();
        double quantity = Math.max(0d, tick.getQuantity());
        long tradeTime = resolveTradeTime(tick);
        if (price <= 0d || tradeTime <= 0L) {
            return out;
        }
        long minuteStart = tradeTime - Math.floorMod(tradeTime, ONE_MINUTE_MS);
        long minuteEnd = minuteStart + ONE_MINUTE_MS - 1L;
        KlineData current = currentMinuteCandles.get(symbol);
        if (current == null) {
            KlineData opened = createCandle(symbol, minuteStart, minuteEnd, price, quantity, false);
            currentMinuteCandles.put(symbol, opened);
            out.add(opened);
            return out;
        }
        if (minuteStart < current.getOpenTime()) {
            return out;
        }
        if (minuteStart == current.getOpenTime()) {
            KlineData merged = mergeTradeIntoCurrent(current, price, quantity, false);
            currentMinuteCandles.put(symbol, merged);
            out.add(merged);
            return out;
        }
        KlineData closed = new KlineData(
                current.getSymbol(),
                current.getOpenPrice(),
                current.getHighPrice(),
                current.getLowPrice(),
                current.getClosePrice(),
                current.getVolume(),
                current.getQuoteAssetVolume(),
                current.getOpenTime(),
                current.getCloseTime(),
                true
        );
        KlineData opened = createCandle(symbol, minuteStart, minuteEnd, price, quantity, false);
        currentMinuteCandles.put(symbol, opened);
        out.add(closed);
        out.add(opened);
        return out;
    }

    // 服务重连或停止时清理组装中的分钟状态，避免旧分钟残留到下一次连接。
    void clear() {
        currentMinuteCandles.clear();
    }

    // 当前分钟第一笔成交直接开一根新 K 线。
    private KlineData createCandle(String symbol,
                                   long minuteStart,
                                   long minuteEnd,
                                   double price,
                                   double quantity,
                                   boolean closed) {
        double quoteVolume = quantity * price;
        return new KlineData(
                symbol,
                price,
                price,
                price,
                price,
                quantity,
                quoteVolume,
                minuteStart,
                minuteEnd,
                closed
        );
    }

    // 把成交量、成交额和高低收都并到当前未收盘分钟上。
    private KlineData mergeTradeIntoCurrent(KlineData current,
                                            double price,
                                            double quantity,
                                            boolean closed) {
        return new KlineData(
                current.getSymbol(),
                current.getOpenPrice(),
                Math.max(current.getHighPrice(), price),
                Math.min(current.getLowPrice(), price),
                price,
                current.getVolume() + quantity,
                current.getQuoteAssetVolume() + quantity * price,
                current.getOpenTime(),
                current.getCloseTime(),
                closed
        );
    }

    private long resolveTradeTime(TradeTickData tick) {
        if (tick == null) {
            return 0L;
        }
        return tick.getTradeTime() > 0L ? tick.getTradeTime() : tick.getEventTime();
    }
}
