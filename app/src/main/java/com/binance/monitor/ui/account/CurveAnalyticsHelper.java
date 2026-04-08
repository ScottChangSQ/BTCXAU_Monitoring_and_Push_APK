/*
 * 账户统计分析辅助工具，负责把曲线和交易记录转换为回撤、日收益、交易散点与持仓时长分布。
 * 供 AccountStatsBridgeActivity 和账户统计附图复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.util.ProductSymbolMapper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class CurveAnalyticsHelper {

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
            return durationMs >= minDurationMs && durationMs <= maxDurationMs;
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
        if (points.isEmpty()) {
            return null;
        }
        CurvePoint currentPeakPoint = points.get(0);
        CurvePoint worstPeakPoint = null;
        CurvePoint worstValleyPoint = null;
        double maxDrawdownRate = 0d;
        for (CurvePoint point : points) {
            if (point.getEquity() > currentPeakPoint.getEquity()) {
                currentPeakPoint = point;
            }
            // 回撤统一按“历史净值峰值 -> 当前净值谷值”的 running peak 口径计算。
            double drawdownRate = Math.min(0d,
                    safeDivide(point.getEquity() - currentPeakPoint.getEquity(), currentPeakPoint.getEquity()));
            if (drawdownRate < maxDrawdownRate) {
                maxDrawdownRate = drawdownRate;
                worstPeakPoint = currentPeakPoint;
                worstValleyPoint = point;
            }
        }
        if (worstPeakPoint == null || worstValleyPoint == null) {
            return null;
        }
        return new DrawdownSegment(
                worstPeakPoint.getTimestamp(),
                worstValleyPoint.getTimestamp(),
                worstPeakPoint.getEquity(),
                worstValleyPoint.getEquity(),
                maxDrawdownRate
        );
    }

    public static List<DrawdownPoint> buildDrawdownSeries(@Nullable List<CurvePoint> source) {
        List<CurvePoint> points = normalizePoints(source);
        List<DrawdownPoint> result = new ArrayList<>();
        if (points.isEmpty()) {
            return result;
        }
        double runningPeakEquity = Math.max(0d, points.get(0).getEquity());
        for (CurvePoint point : points) {
            runningPeakEquity = Math.max(runningPeakEquity, point.getEquity());
            // 每个时间点都按历史峰值净值计算水下比例，盈利区间固定钳到 0。
            double drawdownRate = Math.min(0d, safeDivide(point.getEquity() - runningPeakEquity, runningPeakEquity));
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
        double openPrice = item.getOpenPrice();
        double quantity = Math.abs(item.getQuantity());
        double contractMultiplier = resolveTradeContractMultiplier(item);
        double notional = Math.abs(quantity * openPrice * contractMultiplier);
        if (notional > 1e-9) {
            return safeDivide(realized, notional);
        }
        if (Math.abs(item.getAmount()) > 1e-9) {
            return safeDivide(realized, Math.abs(item.getAmount()));
        }
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

    private static double resolveTradeContractMultiplier(TradeRecordItem item) {
        String tradeSymbol = ProductSymbolMapper.toTradeSymbol(safeLabel(item));
        if (ProductSymbolMapper.TRADE_SYMBOL_XAU.equals(tradeSymbol)) {
            return 100d;
        }
        return 1d;
    }

    private static double resolveTradeWindowDrawdown(TradeRecordItem item,
                                                     List<CurvePoint> curvePoints,
                                                     double fallbackReturnRate) {
        if (curvePoints == null || curvePoints.size() < 2) {
            return 0d;
        }
        long openTime = resolveOpenTime(item);
        long closeTime = resolveCloseTime(item);
        List<CurvePoint> range = new ArrayList<>();
        CurvePoint previousPoint = null;
        CurvePoint nextPoint = null;
        for (CurvePoint point : curvePoints) {
            long ts = point.getTimestamp();
            if (ts >= openTime && ts <= closeTime) {
                range.add(point);
                continue;
            }
            if (ts < openTime) {
                previousPoint = point;
                continue;
            }
            if (ts > closeTime && nextPoint == null) {
                nextPoint = point;
            }
        }
        if (range.isEmpty() && previousPoint != null) {
            range.add(previousPoint);
        }
        if (previousPoint != null && (range.isEmpty() || range.get(0).getTimestamp() != previousPoint.getTimestamp())) {
            range.add(0, previousPoint);
        }
        if (nextPoint != null) {
            range.add(nextPoint);
        }
        if (range.size() < 2) {
            return 0d;
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
        return item.getOpenTime();
    }

    private static long resolveCloseTime(TradeRecordItem item) {
        return item.getCloseTime();
    }

    private static long resolveHoldingDuration(TradeRecordItem item) {
        long openTime = item.getOpenTime();
        long closeTime = item.getCloseTime();
        if (openTime > 0L && closeTime > 0L && closeTime >= openTime) {
            return closeTime - openTime;
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
