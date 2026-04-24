/*
 * 统一市场真值中心，负责收口 stream 与 REST 输入，并产出唯一市场快照。
 */
package com.binance.monitor.runtime.market.truth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;
import com.binance.monitor.runtime.market.truth.model.MarketTruthSnapshot;
import com.binance.monitor.runtime.market.truth.model.MarketTruthSymbolState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MarketTruthCenter {
    private final MinuteBaseStore minuteBaseStore;
    private final IntervalProjectionStore intervalProjectionStore;
    private final GapDetector gapDetector;
    private MarketTruthSnapshot snapshot = MarketTruthSnapshot.empty();

    public MarketTruthCenter(@NonNull MinuteBaseStore minuteBaseStore,
                             @NonNull IntervalProjectionStore intervalProjectionStore,
                             @NonNull GapDetector gapDetector) {
        this.minuteBaseStore = minuteBaseStore;
        this.intervalProjectionStore = intervalProjectionStore;
        this.gapDetector = gapDetector;
    }

    // 应用 stream 下发的实时分钟草稿。
    public synchronized void applyStreamDraft(@NonNull String symbol,
                                              @NonNull CandleEntry draftMinute,
                                              double latestPrice,
                                              long updatedAt) {
        minuteBaseStore.applyDraft(symbol, draftMinute, latestPrice, updatedAt);
        rebuildSymbolState(symbol, updatedAt);
    }

    // 应用一个完整的 stream 市场窗口。
    public synchronized void applyStreamWindow(@Nullable SymbolMarketWindow window,
                                               long updatedAt) {
        if (window == null || window.getMarketSymbol().trim().isEmpty()) {
            return;
        }
        applyStreamWindow(
                window.getMarketSymbol(),
                window.getLatestClosedMinute(),
                window.getLatestPatch(),
                window.getLatestPrice(),
                updatedAt
        );
    }

    // 应用 stream 的闭合分钟和 patch。
    public synchronized void applyStreamWindow(@NonNull String symbol,
                                               @Nullable KlineData latestClosedMinute,
                                               @Nullable KlineData latestPatch,
                                               double latestPrice,
                                               long updatedAt) {
        if (latestClosedMinute != null) {
            CandleEntry closedMinute = toCandleEntry(latestClosedMinute);
            minuteBaseStore.applyClosedMinute(symbol, closedMinute, latestPrice, updatedAt);
            intervalProjectionStore.onMinuteClosed(symbol, closedMinute);
        }
        if (latestPatch != null) {
            CandleEntry patch = toCandleEntry(latestPatch);
            if (latestPatch.isClosed()) {
                minuteBaseStore.applyClosedMinute(symbol, patch, latestPrice, updatedAt);
                intervalProjectionStore.onMinuteClosed(symbol, patch);
            } else {
                minuteBaseStore.applyDraft(symbol, patch, latestPrice, updatedAt);
            }
        }
        rebuildSymbolState(symbol, updatedAt);
    }

    // 应用 REST 周期历史。
    public synchronized void applyRestSeries(@NonNull String symbol,
                                             @NonNull String intervalKey,
                                             @Nullable List<CandleEntry> closedCandles,
                                             @Nullable CandleEntry latestPatch,
                                             long updatedAt) {
        String normalizedInterval = MarketTruthAggregationHelper.normalizeIntervalKey(intervalKey);
        if ("1m".equals(normalizedInterval)) {
            double latestPrice = resolveLatestPrice(symbol, closedCandles, latestPatch);
            minuteBaseStore.applyClosedMinutes(symbol, closedCandles, latestPrice, updatedAt);
            if (closedCandles != null) {
                for (CandleEntry candle : closedCandles) {
                    if (candle != null) {
                        intervalProjectionStore.onMinuteClosed(symbol, candle);
                    }
                }
            }
            if (latestPatch != null) {
                minuteBaseStore.applyDraft(symbol, latestPatch, latestPrice, updatedAt);
            }
        }
        intervalProjectionStore.applyCanonicalSeries(symbol, normalizedInterval, closedCandles, latestPatch);
        rebuildSymbolState(symbol, updatedAt);
    }

    // 应用补档得到的分钟窗口；无 patch 时只补闭合历史，不允许把当前分钟价打回旧闭合价。
    public synchronized void applyRepairedMinuteWindow(@NonNull String symbol,
                                                       @Nullable List<CandleEntry> minuteCandles,
                                                       @Nullable CandleEntry latestPatch,
                                                       long updatedAt) {
        double latestPrice = latestPatch != null
                ? resolveLatestPrice(symbol, minuteCandles, latestPatch)
                : resolveRepairLatestPrice(symbol, minuteCandles);
        minuteBaseStore.applyClosedMinutes(symbol, minuteCandles, latestPrice, updatedAt);
        if (minuteCandles != null) {
            for (CandleEntry candle : minuteCandles) {
                if (candle != null) {
                    intervalProjectionStore.onMinuteClosed(symbol, candle);
                }
            }
        }
        if (latestPatch != null) {
            minuteBaseStore.applyDraft(symbol, latestPatch, latestPrice, updatedAt);
        }
        rebuildSymbolState(symbol, updatedAt);
    }

    // 兼容旧调用口；无 patch 时仍按“只补历史，不回滚当前价”处理。
    public synchronized void applyRepairedMinuteHistory(@NonNull String symbol,
                                                        @Nullable List<CandleEntry> minuteCandles,
                                                        long updatedAt) {
        applyRepairedMinuteWindow(symbol, minuteCandles, null, updatedAt);
    }

    @NonNull
    public synchronized MarketTruthSnapshot getSnapshot() {
        return snapshot;
    }

    private void rebuildSymbolState(@NonNull String symbol, long updatedAt) {
        MinuteBaseStore.ApplyResult minuteResult = minuteBaseStore.selectState(symbol);
        Map<String, List<CandleEntry>> closedSeries = new LinkedHashMap<>(intervalProjectionStore.snapshotClosedSeries(symbol));
        Map<String, CandleEntry> intervalDrafts = new LinkedHashMap<>(intervalProjectionStore.snapshotDrafts(symbol));
        closedSeries.put("1m", minuteResult.getClosedMinutes());
        boolean minuteGap = gapDetector.hasMinuteGap(minuteResult.getClosedMinutes());
        long truthUpdatedAt = Math.max(0L, minuteResult.getUpdatedAt());
        MarketTruthSymbolState state = new MarketTruthSymbolState(
                minuteResult.getSymbol(),
                minuteResult.getLatestPrice(),
                truthUpdatedAt,
                minuteResult.getLatestClosedMinute(),
                minuteResult.getDraftMinute(),
                minuteGap,
                minuteResult.getClosedMinutes(),
                closedSeries,
                intervalDrafts
        );
        snapshot = snapshot.withSymbolState(symbol, state, truthUpdatedAt);
    }

    private double resolveLatestPrice(@NonNull String symbol,
                                      @Nullable List<CandleEntry> closedCandles,
                                      @Nullable CandleEntry latestPatch) {
        if (latestPatch != null) {
            return latestPatch.getClose();
        }
        if (closedCandles != null && !closedCandles.isEmpty()) {
            CandleEntry latest = closedCandles.get(closedCandles.size() - 1);
            if (latest != null) {
                return latest.getClose();
            }
        }
        return snapshot.selectLatestPrice(symbol);
    }

    private double resolveRepairLatestPrice(@NonNull String symbol,
                                            @Nullable List<CandleEntry> minuteCandles) {
        double currentLatestPrice = snapshot.selectLatestPrice(symbol);
        if (currentLatestPrice > 0d) {
            return currentLatestPrice;
        }
        return resolveLatestPrice(symbol, minuteCandles, null);
    }

    @NonNull
    public static CandleEntry toCandleEntry(@NonNull KlineData klineData) {
        return new CandleEntry(
                klineData.getSymbol(),
                klineData.getOpenTime(),
                klineData.getCloseTime(),
                klineData.getOpenPrice(),
                klineData.getHighPrice(),
                klineData.getLowPrice(),
                klineData.getClosePrice(),
                klineData.getVolume(),
                klineData.getQuoteAssetVolume()
        );
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }
}
