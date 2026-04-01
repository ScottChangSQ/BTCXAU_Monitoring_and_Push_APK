/*
 * 账户统计分析辅助工具，负责把曲线和交易记录转换为回撤、日收益、交易散点与持仓时长分布。
 * 供 AccountStatsBridgeActivity 和账户统计附图复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class CurveAnalyticsHelper {

    private static final long LEGACY_DURATION_SENTINEL_MS = 946684800000L;
    private static final long HALF_HOUR_MS = 30L * 60L * 1000L;
    private static final long FOUR_HOURS_MS = 4L * 60L * 60L * 1000L;
    private static final long TWELVE_HOURS_MS = 12L * 60L * 60L * 1000L;
    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long THREE_DAYS_MS = 3L * ONE_DAY_MS;
    private static final long SEVEN_DAYS_MS = 7L * ONE_DAY_MS;

    private CurveAnalyticsHelper() {
    }

    public static final class DrawdownSegment {
        private final long peakTimestamp;
        private final long valleyTimestamp;
        private final double peakEquity;
        private final double valleyEquity;
        private final double drawdownRate;

        public DrawdownSegment(long peakTimestamp,
                               long valleyTimestamp,
                               double peakEquity,
                               double valleyEquity,
                               double drawdownRate) {
            this.peakTimestamp = peakTimestamp;
            this.valleyTimestamp = valleyTimestamp;
            this.peakEquity = peakEquity;
            this.valleyEquity = valleyEquity;
            this.drawdownRate = drawdownRate;
        }

        public long getPeakTimestamp() {
            return peakTimestamp;
        }

        public long getValleyTimestamp() {
            return valleyTimestamp;
        }

        public double getPeakEquity() {
            return peakEquity;
        }

        public double getValleyEquity() {
            return valleyEquity;
        }

        public double getDrawdownRate() {
            return drawdownRate;
        }
    }

    public static final class DrawdownPoint {
        private final long timestamp;
        private final double drawdownRate;

        public DrawdownPoint(long timestamp, double drawdownRate) {
            this.timestamp = timestamp;
            this.drawdownRate = drawdownRate;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public double getDrawdownRate() {
            return drawdownRate;
        }
    }

    public static final class DailyReturnPoint {
        private final long timestamp;
        private final double returnRate;

        public DailyReturnPoint(long timestamp, double returnRate) {
            this.timestamp = timestamp;
            this.returnRate = returnRate;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public double getReturnRate() {
            return returnRate;
        }
    }

    public static final class TradeScatterPoint {
        private final String label;
        private final double maxDrawdownRate;
        private final double returnRate;
        private final double profitAmount;
        private final long openTime;
        private final long closeTime;
        private final double openPrice;
        private final double closePrice;
        private final long holdingDurationMs;
        private final boolean positive;
        private final boolean highlight;

        public TradeScatterPoint(String label,
                                 double maxDrawdownRate,
                                 double returnRate,
                                 double profitAmount,
                                 long openTime,
                                 long closeTime,
                                 double openPrice,
                                 double closePrice,
                                 long holdingDurationMs,
                                 boolean positive,
                                 boolean highlight) {
            this.label = label;
            this.maxDrawdownRate = maxDrawdownRate;
            this.returnRate = returnRate;
            this.profitAmount = profitAmount;
            this.openTime = openTime;
            this.closeTime = closeTime;
            this.openPrice = openPrice;
            this.closePrice = closePrice;
            this.holdingDurationMs = holdingDurationMs;
            this.positive = positive;
            this.highlight = highlight;
        }

        public String getLabel() {
            return label;
        }

        public double getMaxDrawdownRate() {
            return maxDrawdownRate;
        }

        public double getReturnRate() {
            return returnRate;
        }

        public double getProfitAmount() {
            return profitAmount;
        }

        public long getOpenTime() {
            return openTime;
        }

        public long getCloseTime() {
            return closeTime;
        }

        public double getOpenPrice() {
            return openPrice;
        }

        public double getClosePrice() {
            return closePrice;
        }

        public long getHoldingDurationMs() {
            return holdingDurationMs;
        }

        public boolean isPositive() {
            return positive;
        }

        public boolean isHighlight() {
            return highlight;
        }
    }

    public static final class DurationBucket {
        private final String label;
        private final long minDurationMs;
        private final long maxDurationMs;
        private int count;
        private int winCount;
        private int lossCount;

        public DurationBucket(String label, long minDurationMs, long maxDurationMs) {
            this.label = label;
            this.minDurationMs = minDurationMs;
            this.maxDurationMs = maxDurationMs;
        }

        public String getLabel() {
            return label;
        }

        public int getCount() {
            return count;
        }

        public int getWinCount() {
            return winCount;
        }

        public int getLossCount() {
            return lossCount;
        }

        private boolean matches(long durationMs) {
            return durationMs > minDurationMs && durationMs <= maxDurationMs;
        }

        private void increment(double profitWithStorage) {
            count++;
            if (profitWithStorage >= 0d) {
                winCount++;
            } else {
                lossCount++;
            }
        }
    }

    @Nullable
    public static DrawdownSegment resolveMaxDrawdownSegment(@Nullable List<CurvePoint> source) {
        List<CurvePoint> points = normalizePoints(source);
        if (points.size() < 2) {
            return null;
        }
        CurvePoint peakPoint = points.get(0);
        CurvePoint maxPeak = null;
        CurvePoint maxValley = null;
        double maxDrawdownRate = 0d;
        for (CurvePoint point : points) {
            if (point.getEquity() >= peakPoint.getEquity()) {
                peakPoint = point;
            }
            double drawdownRate = safeDivide(point.getEquity() - peakPoint.getEquity(), peakPoint.getEquity());
            if (drawdownRate < maxDrawdownRate) {
                maxDrawdownRate = drawdownRate;
                maxPeak = peakPoint;
                maxValley = point;
            }
        }
        if (maxPeak == null || maxValley == null) {
            return null;
        }
        return new DrawdownSegment(
                maxPeak.getTimestamp(),
                maxValley.getTimestamp(),
                maxPeak.getEquity(),
                maxValley.getEquity(),
                maxDrawdownRate
        );
    }

    public static List<DrawdownPoint> buildDrawdownSeries(@Nullable List<CurvePoint> source) {
        List<CurvePoint> points = normalizePoints(source);
        List<DrawdownPoint> result = new ArrayList<>();
        if (points.isEmpty()) {
            return result;
        }
        double peak = Math.max(1e-9, points.get(0).getEquity());
        for (CurvePoint point : points) {
            peak = Math.max(peak, point.getEquity());
            double drawdownRate = Math.min(0d, safeDivide(point.getEquity() - peak, peak));
            result.add(new DrawdownPoint(point.getTimestamp(), drawdownRate));
        }
        return result;
    }

    public static List<DailyReturnPoint> buildDailyReturnSeries(@Nullable List<CurvePoint> source) {
        List<CurvePoint> points = normalizePoints(source);
        List<DailyReturnPoint> result = new ArrayList<>();
        if (points.size() < 2) {
            return result;
        }
        Map<Integer, CurvePoint> dailyClose = new TreeMap<>();
        Calendar calendar = Calendar.getInstance();
        for (CurvePoint point : points) {
            calendar.setTimeInMillis(point.getTimestamp());
            int key = calendar.get(Calendar.YEAR) * 10_000
                    + (calendar.get(Calendar.MONTH) + 1) * 100
                    + calendar.get(Calendar.DAY_OF_MONTH);
            dailyClose.put(key, point);
        }
        CurvePoint previousClose = null;
        for (CurvePoint closePoint : dailyClose.values()) {
            if (previousClose != null) {
                double returnRate = safeDivide(
                        closePoint.getEquity() - previousClose.getEquity(),
                        previousClose.getEquity());
                result.add(new DailyReturnPoint(closePoint.getTimestamp(), returnRate));
            }
            previousClose = closePoint;
        }
        return result;
    }

    public static List<TradeScatterPoint> buildTradeScatterPoints(@Nullable List<TradeRecordItem> trades,
                                                                  @Nullable List<CurvePoint> curvePoints) {
        List<TradeScatterPoint> result = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            return result;
        }
        List<CurvePoint> normalizedCurve = normalizePoints(curvePoints);
        for (TradeRecordItem item : trades) {
            if (item == null) {
                continue;
            }
            double returnRate = resolveTradeReturnRate(item);
            double maxDrawdownRate = resolveTradeWindowDrawdown(item, normalizedCurve, returnRate);
            double profitAmount = item.getProfit() + item.getStorageFee();
            long openTime = resolveOpenTime(item);
            long closeTime = resolveCloseTime(item);
            long holdingDurationMs = resolveHoldingDuration(item);
            boolean positive = returnRate >= 0d;
            boolean highlight = positive && returnRate >= 0.6d;
            String label = safeLabel(item);
            result.add(new TradeScatterPoint(
                    label,
                    maxDrawdownRate,
                    returnRate,
                    profitAmount,
                    openTime,
                    closeTime,
                    item.getOpenPrice(),
                    item.getClosePrice(),
                    holdingDurationMs,
                    positive,
                    highlight
            ));
        }
        return result;
    }

    public static List<DurationBucket> buildHoldingDurationDistribution(@Nullable List<TradeRecordItem> trades) {
        List<DurationBucket> buckets = new ArrayList<>();
        buckets.add(new DurationBucket("0-30分", 0L, HALF_HOUR_MS));
        buckets.add(new DurationBucket("30分-4时", HALF_HOUR_MS, FOUR_HOURS_MS));
        buckets.add(new DurationBucket("4-12时", FOUR_HOURS_MS, TWELVE_HOURS_MS));
        buckets.add(new DurationBucket("12-24时", TWELVE_HOURS_MS, ONE_DAY_MS));
        buckets.add(new DurationBucket("1-3天", ONE_DAY_MS, THREE_DAYS_MS));
        buckets.add(new DurationBucket("3-7天", THREE_DAYS_MS, SEVEN_DAYS_MS));
        buckets.add(new DurationBucket("7天+", SEVEN_DAYS_MS, Long.MAX_VALUE));
        if (trades == null || trades.isEmpty()) {
            return buckets;
        }
        for (TradeRecordItem item : trades) {
            if (item == null) {
                continue;
            }
            long durationMs = resolveHoldingDuration(item);
            double profitWithStorage = item.getProfit() + item.getStorageFee();
            for (DurationBucket bucket : buckets) {
                if (bucket.matches(durationMs)) {
                    bucket.increment(profitWithStorage);
                    break;
                }
            }
        }
        return buckets;
    }

    private static double resolveTradeReturnRate(TradeRecordItem item) {
        double realized = item.getProfit() + item.getStorageFee();
        if (Math.abs(item.getAmount()) > 1e-9) {
            return safeDivide(realized, Math.abs(item.getAmount()));
        }
        double openPrice = item.getOpenPrice();
        double closePrice = item.getClosePrice();
        if (openPrice > 1e-9 && closePrice > 1e-9) {
            boolean sell = "SELL".equalsIgnoreCase(item.getSide()) || "卖出".equals(item.getSide());
            double grossRate = sell
                    ? safeDivide(openPrice - closePrice, openPrice)
                    : safeDivide(closePrice - openPrice, openPrice);
            double feeRate = safeDivide(item.getStorageFee(), Math.max(1d, Math.abs(openPrice * item.getQuantity())));
            return grossRate + feeRate;
        }
        return 0d;
    }

    private static double resolveTradeWindowDrawdown(TradeRecordItem item,
                                                     List<CurvePoint> curvePoints,
                                                     double fallbackReturnRate) {
        if (curvePoints == null || curvePoints.size() < 2) {
            return Math.min(0d, fallbackReturnRate);
        }
        long openTime = resolveOpenTime(item);
        long closeTime = resolveCloseTime(item);
        List<CurvePoint> range = new ArrayList<>();
        for (CurvePoint point : curvePoints) {
            long ts = point.getTimestamp();
            if (ts >= openTime && ts <= closeTime) {
                range.add(point);
            }
        }
        if (range.size() < 2) {
            return Math.min(0d, fallbackReturnRate);
        }
        double peak = Math.max(1e-9, range.get(0).getEquity());
        double drawdownRate = 0d;
        for (CurvePoint point : range) {
            peak = Math.max(peak, point.getEquity());
            drawdownRate = Math.min(drawdownRate, safeDivide(point.getEquity() - peak, peak));
        }
        return drawdownRate;
    }

    private static List<CurvePoint> normalizePoints(@Nullable List<CurvePoint> source) {
        Map<Long, CurvePoint> ordered = new LinkedHashMap<>();
        if (source == null) {
            return new ArrayList<>();
        }
        List<CurvePoint> sorted = new ArrayList<>(source);
        sorted.sort((left, right) -> Long.compare(left.getTimestamp(), right.getTimestamp()));
        for (CurvePoint point : sorted) {
            if (point == null || point.getTimestamp() <= 0L) {
                continue;
            }
            ordered.put(point.getTimestamp(), point);
        }
        return new ArrayList<>(ordered.values());
    }

    private static long resolveOpenTime(TradeRecordItem item) {
        long openTime = item.getOpenTime();
        if (openTime > 0L) {
            return openTime;
        }
        return item.getTimestamp();
    }

    private static long resolveCloseTime(TradeRecordItem item) {
        long closeTime = item.getCloseTime();
        if (closeTime > 0L) {
            return closeTime;
        }
        return item.getTimestamp();
    }

    private static long resolveHoldingDuration(TradeRecordItem item) {
        long openTime = item.getOpenTime();
        long closeTime = item.getCloseTime();
        long timestamp = item.getTimestamp();
        if (openTime > 0L && closeTime > 0L && closeTime >= openTime) {
            return closeTime - openTime;
        }
        if (openTime > 0L && closeTime <= 0L && timestamp >= openTime) {
            return timestamp - openTime;
        }
        // 兼容旧测试数据与本地样例：当时间值明显不是 epoch 毫秒时，按“已持有时长”处理。
        if (timestamp > 0L && timestamp < LEGACY_DURATION_SENTINEL_MS) {
            if (closeTime > 0L && closeTime < LEGACY_DURATION_SENTINEL_MS && closeTime >= openTime) {
                return Math.max(timestamp, closeTime - Math.max(0L, openTime));
            }
            return timestamp;
        }
        if (closeTime > 0L && closeTime >= Math.max(0L, openTime)) {
            return closeTime - Math.max(0L, openTime);
        }
        return 0L;
    }

    private static String safeLabel(TradeRecordItem item) {
        String code = item.getCode() == null ? "" : item.getCode().trim();
        if (!code.isEmpty()) {
            return code.toUpperCase(Locale.ROOT);
        }
        String product = item.getProductName() == null ? "" : item.getProductName().trim();
        return product.isEmpty() ? "UNKNOWN" : product.toUpperCase(Locale.ROOT);
    }

    private static double safeDivide(double a, double b) {
        if (Math.abs(b) < 1e-9) {
            return 0d;
        }
        return a / b;
    }
}
