/*
 * 已加载历史K线异常记录构建器，负责按当前阈值配置对图表已加载K线重算异常。
 * 这样图表标注不再依赖通知是否发出，也不依赖服务端最近窗口是否覆盖到当前历史区间。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.SymbolConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class HistoricalAbnormalRecordBuilder {

    private HistoricalAbnormalRecordBuilder() {
    }

    // 根据当前已加载的K线和异常配置，重算所有应显示的异常记录。
    static List<AbnormalRecord> buildFromCandles(@Nullable String symbol,
                                                 @Nullable List<CandleEntry> candles,
                                                 @Nullable SymbolConfig config,
                                                 boolean useAndMode) {
        List<AbnormalRecord> result = new ArrayList<>();
        if (symbol == null || symbol.trim().isEmpty() || candles == null || candles.isEmpty() || config == null) {
            return result;
        }
        String normalizedSymbol = symbol.trim().toUpperCase(Locale.ROOT);
        for (CandleEntry candle : candles) {
            if (candle == null) {
                continue;
            }
            EvaluationResult evaluation = evaluate(candle, config, useAndMode);
            if (!evaluation.participating || !evaluation.abnormal) {
                continue;
            }
            long closeTime = resolveCloseTime(candle);
            double openPrice = candle.getOpen();
            double closePrice = candle.getClose();
            double priceChange = Math.abs(closePrice - openPrice);
            double percentChange = openPrice <= 0d
                    ? 0d
                    : Math.abs((closePrice - openPrice) / openPrice * 100d);
            result.add(new AbnormalRecord(
                    buildStableId(normalizedSymbol, closeTime),
                    normalizedSymbol,
                    closeTime,
                    closeTime,
                    openPrice,
                    closePrice,
                    candle.getVolume(),
                    candle.getQuoteVolume(),
                    priceChange,
                    percentChange,
                    evaluation.summary
            ));
        }
        result.sort(Comparator.comparingLong(HistoricalAbnormalRecordBuilder::resolveSortTime));
        return result;
    }

    // 合并本地已同步记录与当前图表重算记录，同一根K线优先保留已有记录，避免重复计数。
    static List<AbnormalRecord> merge(@Nullable List<AbnormalRecord> storedRecords,
                                      @Nullable List<AbnormalRecord> derivedRecords) {
        Map<String, AbnormalRecord> merged = new LinkedHashMap<>();
        appendRecords(merged, storedRecords);
        appendRecords(merged, derivedRecords);
        List<AbnormalRecord> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparingLong(HistoricalAbnormalRecordBuilder::resolveSortTime));
        return result;
    }

    private static void appendRecords(Map<String, AbnormalRecord> target,
                                      @Nullable List<AbnormalRecord> source) {
        if (target == null || source == null || source.isEmpty()) {
            return;
        }
        for (AbnormalRecord record : source) {
            if (record == null) {
                continue;
            }
            String key = buildMergeKey(record);
            if (target.containsKey(key)) {
                continue;
            }
            target.put(key, record);
        }
    }

    // 复用服务端与前台监控同一套阈值口径，保证图表历史回填和实时判断一致。
    private static EvaluationResult evaluate(CandleEntry candle, SymbolConfig config, boolean useAndMode) {
        int enabledCount = 0;
        List<String> triggered = new ArrayList<>();
        if (config.isVolumeEnabled()) {
            enabledCount++;
            if (candle.getVolume() >= config.getVolumeThreshold()) {
                triggered.add("成交量");
            }
        }
        if (config.isAmountEnabled()) {
            enabledCount++;
            if (candle.getQuoteVolume() >= config.getAmountThreshold()) {
                triggered.add("成交额");
            }
        }
        if (config.isPriceChangeEnabled()) {
            enabledCount++;
            if (Math.abs(candle.getClose() - candle.getOpen()) >= config.getPriceChangeThreshold()) {
                triggered.add("价格变化");
            }
        }
        if (enabledCount == 0) {
            return new EvaluationResult(false, false, "");
        }
        boolean abnormal = useAndMode ? triggered.size() == enabledCount : !triggered.isEmpty();
        return new EvaluationResult(true, abnormal, String.join(" / ", triggered));
    }

    private static long resolveCloseTime(CandleEntry candle) {
        if (candle == null) {
            return 0L;
        }
        if (candle.getCloseTime() > 0L) {
            return candle.getCloseTime();
        }
        return Math.max(0L, candle.getOpenTime());
    }

    private static long resolveSortTime(AbnormalRecord record) {
        if (record == null) {
            return 0L;
        }
        if (record.getCloseTime() > 0L) {
            return record.getCloseTime();
        }
        return Math.max(0L, record.getTimestamp());
    }

    private static String buildStableId(String symbol, long closeTime) {
        return "hist|" + symbol + "|" + closeTime;
    }

    private static String buildMergeKey(AbnormalRecord record) {
        String symbol = record.getSymbol() == null ? "" : record.getSymbol().trim().toUpperCase(Locale.ROOT);
        long closeTime = record.getCloseTime() > 0L ? record.getCloseTime() : Math.max(0L, record.getTimestamp());
        return symbol + "|" + closeTime;
    }

    private static final class EvaluationResult {
        private final boolean participating;
        private final boolean abnormal;
        private final String summary;

        private EvaluationResult(boolean participating, boolean abnormal, String summary) {
            this.participating = participating;
            this.abnormal = abnormal;
            this.summary = summary == null ? "" : summary;
        }
    }
}
