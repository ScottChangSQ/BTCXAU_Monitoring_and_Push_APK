/*
 * 账户页次级区块后台计算助手，负责把交易统计、交易筛选和曲线投影整理成可直接绑定的结果。
 * 供 AccountStatsBridgeActivity 在首帧后异步准备数据使用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.ProductSymbolMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AccountDeferredSnapshotRenderHelper {

    public enum TradePnlSideMode {
        ALL,
        BUY,
        SELL
    }

    public enum TradeWeekdayBasis {
        CLOSE_TIME,
        OPEN_TIME
    }

    public enum SortMode {
        CLOSE_TIME,
        OPEN_TIME,
        PROFIT
    }

    private AccountDeferredSnapshotRenderHelper() {
    }

    // 一次性准备账户页次级区块所需的纯计算结果，避免首帧后继续在主线程做重算。
    @NonNull
    public static PreparedSnapshotSections prepare(@NonNull PrepareRequest request) {
        CurveProjection curveProjection = buildCurveProjection(
                request.getAllCurvePoints(),
                request.getSelectedRange(),
                request.isManualCurveRangeEnabled(),
                request.getManualCurveRangeStartMs(),
                request.getManualCurveRangeEndMs()
        );
        TradeAnalytics tradeAnalytics = buildTradeAnalytics(
                request.getLatestStatsMetrics(),
                request.getBaseTrades(),
                request.getTradePnlSideMode(),
                request.getTradeWeekdayBasis(),
                request.getAllCurvePoints()
        );
        List<String> tradeProducts = buildTradeProducts(request.getBaseTrades());
        List<TradeRecordItem> filteredTrades = buildFilteredTrades(request.getBaseTrades(), request.getTradeFilterRequest());
        TradeSummary tradeSummary = buildTradeSummary(filteredTrades);
        return new PreparedSnapshotSections(
                tradeProducts,
                curveProjection,
                tradeAnalytics,
                filteredTrades,
                tradeSummary
        );
    }

    // 计算交易产品选项，保持唯一且按字母排序。
    @NonNull
    public static List<String> buildTradeProducts(List<TradeRecordItem> baseTrades) {
        LinkedHashSet<String> uniqueProducts = new LinkedHashSet<>();
        if (baseTrades != null) {
            for (TradeRecordItem item : baseTrades) {
                if (item == null) {
                    continue;
                }
                String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
                if (!code.isEmpty()) {
                    uniqueProducts.add(code);
                }
            }
        }
        List<String> products = new ArrayList<>(uniqueProducts);
        products.sort(String::compareToIgnoreCase);
        return products;
    }

    // 计算当前曲线范围下的显示点和派生曲线，避免主线程重复遍历大列表。
    @NonNull
    public static CurveProjection buildCurveProjection(List<CurvePoint> allCurvePoints,
                                                       AccountTimeRange selectedRange,
                                                       boolean manualCurveRangeEnabled,
                                                       long manualCurveRangeStartMs,
                                                       long manualCurveRangeEndMs) {
        boolean manualRangeApplied = false;
        List<CurvePoint> displayedCurvePoints;
        if (manualCurveRangeEnabled) {
            List<CurvePoint> manualPoints = filterCurveByManualRange(
                    allCurvePoints,
                    manualCurveRangeStartMs,
                    manualCurveRangeEndMs
            );
            if (manualPoints.size() >= 2) {
                displayedCurvePoints = manualPoints;
                manualRangeApplied = true;
            } else {
                displayedCurvePoints = filterCurveByRange(allCurvePoints, selectedRange);
            }
        } else {
            displayedCurvePoints = filterCurveByRange(allCurvePoints, selectedRange);
        }
        CurveAnalyticsHelper.DrawdownSegment drawdownSegment =
                CurveAnalyticsHelper.resolveMaxDrawdownSegment(displayedCurvePoints);
        long viewportStartTs = displayedCurvePoints.isEmpty()
                ? 0L
                : displayedCurvePoints.get(0).getTimestamp();
        long viewportEndTs = displayedCurvePoints.size() > 1
                ? displayedCurvePoints.get(displayedCurvePoints.size() - 1).getTimestamp()
                : viewportStartTs + 1L;
        return new CurveProjection(
                displayedCurvePoints,
                CurveAnalyticsHelper.buildDrawdownSeries(displayedCurvePoints),
                CurveAnalyticsHelper.buildDailyReturnSeries(displayedCurvePoints),
                drawdownSegment,
                resolveCurvePercentBase(displayedCurvePoints),
                viewportStartTs,
                viewportEndTs,
                manualRangeApplied
        );
    }

    // 汇总交易统计区所需的所有派生结果，供主线程直接绑定。
    @NonNull
    public static TradeAnalytics buildTradeAnalytics(List<AccountMetric> latestStatsMetrics,
                                                     List<TradeRecordItem> baseTrades,
                                                     TradePnlSideMode tradePnlSideMode,
                                                     TradeWeekdayBasis tradeWeekdayBasis,
                                                     List<CurvePoint> displayedCurvePoints) {
        List<AccountMetric> statsMetrics = buildTradeStatsMetrics(latestStatsMetrics, baseTrades, displayedCurvePoints);
        List<TradePnlBarChartView.Entry> tradePnlEntries =
                buildTradePnlChartEntries(baseTrades, tradePnlSideMode);
        List<TradeRecordItem> scopedTrades = filterTradesBySideMode(baseTrades, tradePnlSideMode);
        List<TradeRecordItem> distributionTrades = filterTradeDistributionSymbols(scopedTrades);
        List<TradeWeekdayStatsHelper.Row> weekdayRows = TradeWeekdayStatsHelper.buildRows(
                scopedTrades,
                tradeWeekdayBasis == TradeWeekdayBasis.OPEN_TIME
                        ? TradeWeekdayStatsHelper.TimeBasis.OPEN_TIME
                        : TradeWeekdayStatsHelper.TimeBasis.CLOSE_TIME
        );
        return new TradeAnalytics(
                statsMetrics,
                tradePnlEntries,
                CurveAnalyticsHelper.buildTradeScatterPoints(distributionTrades, displayedCurvePoints),
                CurveAnalyticsHelper.buildHoldingDurationDistribution(scopedTrades),
                TradeWeekdayBarChartHelper.buildEntries(weekdayRows),
                sumTradePnl(tradePnlEntries)
        );
    }

    // 首轮快照尚未携带服务端统计指标时，直接基于当前交易和曲线真值补出同口径基础指标，避免页面先出现空卡片。
    @NonNull
    private static List<AccountMetric> buildTradeStatsMetrics(List<AccountMetric> snapshotStats,
                                                              List<TradeRecordItem> baseTrades,
                                                              List<CurvePoint> curvePoints) {
        List<AccountMetric> snapshotMetricResult = snapshotStats == null
                ? new ArrayList<>()
                : new ArrayList<>(snapshotStats);
        if (!snapshotMetricResult.isEmpty()) {
            upsertMetric(snapshotMetricResult, "夏普比率", buildSharpeRatioValue(curvePoints), "Sharpe", "Sharpe Ratio");
            return snapshotMetricResult;
        }
        List<TradeRecordItem> trades = baseTrades == null ? new ArrayList<>() : new ArrayList<>(baseTrades);
        if (trades.isEmpty()) {
            return new ArrayList<>();
        }

        double totalPnl = 0d;
        int buyCount = 0;
        int sellCount = 0;
        int winCount = 0;
        int lossCount = 0;
        double totalWinPnl = 0d;
        double totalLossPnl = 0d;
        for (TradeRecordItem item : trades) {
            if (item == null) {
                continue;
            }
            double pnl = item.getProfit() + item.getStorageFee() + item.getFee();
            totalPnl += pnl;
            if (matchesSideMode(item, TradePnlSideMode.BUY)) {
                buyCount++;
            } else if (matchesSideMode(item, TradePnlSideMode.SELL)) {
                sellCount++;
            }
            if (pnl > 0d) {
                winCount++;
                totalWinPnl += pnl;
            } else if (pnl < 0d) {
                lossCount++;
                totalLossPnl += pnl;
            }
        }

        AccountOverviewCumulativeMetricsCalculator.OverviewCumulativeValues cumulativeValues =
                AccountOverviewCumulativeMetricsCalculator.calculate(trades, null, curvePoints);
        double cumulativePnl = cumulativeValues.hasCumulativePnlTruth()
                ? cumulativeValues.getCumulativePnl()
                : totalPnl;
        double cumulativeReturnRate = cumulativeValues.hasCumulativeReturnRateTruth()
                ? cumulativeValues.getCumulativeReturnRate()
                : 0d;

        double averageWin = winCount == 0 ? 0d : totalWinPnl / winCount;
        double averageLoss = lossCount == 0 ? 0d : totalLossPnl / lossCount;
        double profitLossRatio = averageLoss == 0d ? 0d : Math.abs(averageWin / averageLoss);
        int settledCount = winCount + lossCount;
        double winRate = settledCount == 0 ? 0d : ((double) winCount) / settledCount;
        CurveAnalyticsHelper.DrawdownSegment drawdownSegment =
                CurveAnalyticsHelper.resolveMaxDrawdownSegment(curvePoints == null ? new ArrayList<>() : curvePoints);
        double maxDrawdown = drawdownSegment == null ? 0d : Math.abs(drawdownSegment.getDrawdownRate());

        List<AccountMetric> result = new ArrayList<>();
        result.add(new AccountMetric("累计收益额", FormatUtils.formatSignedMoney(cumulativePnl)));
        result.add(new AccountMetric("累计收益率", formatRatioPercent(cumulativeReturnRate)));
        result.add(new AccountMetric("总交易次数", String.valueOf(trades.size())));
        result.add(new AccountMetric("买入次数", String.valueOf(buyCount)));
        result.add(new AccountMetric("卖出次数", String.valueOf(sellCount)));
        result.add(new AccountMetric("胜率", formatRatioPercent(winRate)));
        result.add(new AccountMetric("盈利/亏损", winCount + " / " + lossCount));
        result.add(new AccountMetric("平均每笔盈利", FormatUtils.formatSignedMoney(averageWin)));
        result.add(new AccountMetric("平均每笔亏损", FormatUtils.formatSignedMoney(averageLoss)));
        result.add(new AccountMetric("盈亏比", String.format(Locale.getDefault(), "%.2f", profitLossRatio)));
        result.add(new AccountMetric("最大回撤", formatRatioPercent(maxDrawdown)));
        result.add(new AccountMetric("夏普比率", buildSharpeRatioValue(curvePoints)));
        return result;
    }

    // 夏普比率口径与服务端 curveIndicators 保持一致：逐点收益率均值年化后除以年化波动率。
    @NonNull
    static String buildSharpeRatioValue(@Nullable List<CurvePoint> curvePoints) {
        if (curvePoints == null || curvePoints.isEmpty()) {
            return "--";
        }
        List<Double> returns = new ArrayList<>();
        Double previousEquity = null;
        for (CurvePoint point : curvePoints) {
            if (point == null) {
                continue;
            }
            double equity = point.getEquity();
            if (previousEquity != null) {
                returns.add(safeDivide(equity - previousEquity, previousEquity));
            }
            previousEquity = equity;
        }
        if (returns.isEmpty()) {
            return String.format(Locale.getDefault(), "%.2f", 0d);
        }
        double meanReturn = 0d;
        for (double item : returns) {
            meanReturn += item;
        }
        meanReturn /= returns.size();
        double variance = 0d;
        for (double item : returns) {
            double diff = item - meanReturn;
            variance += diff * diff;
        }
        variance /= returns.size();
        double volatility = Math.sqrt(variance) * Math.sqrt(365d);
        double sharpe = volatility > 1e-9 ? (meanReturn * 365d) / volatility : 0d;
        return String.format(Locale.getDefault(), "%.2f", sharpe);
    }

    // 缺失或占位时补入统一指标，避免核心统计固定字段出现空洞。
    private static void upsertMetric(@NonNull List<AccountMetric> metrics,
                                     @NonNull String targetName,
                                     @NonNull String value,
                                     @NonNull String... aliases) {
        if (value.trim().isEmpty()) {
            return;
        }
        for (int i = 0; i < metrics.size(); i++) {
            AccountMetric metric = metrics.get(i);
            if (metric == null || metric.getName() == null) {
                continue;
            }
            String normalizedName = normalizeMetricName(metric.getName());
            if (!matchesMetricName(normalizedName, targetName, aliases)) {
                continue;
            }
            String currentValue = metric.getValue() == null ? "" : metric.getValue().trim();
            if (!currentValue.isEmpty() && !"--".equals(currentValue)) {
                return;
            }
            metrics.set(i, new AccountMetric(targetName, value));
            return;
        }
        metrics.add(new AccountMetric(targetName, value));
    }

    @NonNull
    private static String normalizeMetricName(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace(" ", "").trim();
    }

    private static boolean matchesMetricName(@NonNull String normalizedName,
                                             @NonNull String targetName,
                                             @NonNull String... aliases) {
        if (normalizedName.equals(normalizeMetricName(targetName))) {
            return true;
        }
        for (String alias : aliases) {
            if (normalizedName.equals(normalizeMetricName(alias))) {
                return true;
            }
        }
        return false;
    }

    private static double safeDivide(double numerator, double denominator) {
        if (Math.abs(denominator) < 1e-9) {
            return 0d;
        }
        return numerator / denominator;
    }

    // 计算交易列表当前筛选和排序结果，供页面直接提交给适配器。
    @NonNull
    public static List<TradeRecordItem> buildFilteredTrades(List<TradeRecordItem> baseTrades,
                                                            @NonNull TradeFilterRequest request) {
        List<TradeRecordItem> filtered = new ArrayList<>();
        if (baseTrades == null || baseTrades.isEmpty()) {
            return filtered;
        }
        for (TradeRecordItem item : baseTrades) {
            if (item == null) {
                continue;
            }
            if (!request.isAllProducts()
                    && !trim(item.getCode()).equalsIgnoreCase(trim(request.getProductFilter()))) {
                continue;
            }
            if (!request.isAllSides() && !matchesExactSide(item, request.getSideFilter())) {
                continue;
            }
            filtered.add(item);
        }
        filtered.sort((left, right) -> compareTrades(left, right, request.getSortMode()));
        if (request.isDescending()) {
            java.util.Collections.reverse(filtered);
        }
        return filtered;
    }

    // 预先汇总交易列表右侧摘要数字，避免主线程再次遍历。
    @NonNull
    public static TradeSummary buildTradeSummary(List<TradeRecordItem> trades) {
        double tradeProfitTotal = 0d;
        double tradeStorageTotal = 0d;
        if (trades != null) {
            for (TradeRecordItem item : trades) {
                if (item == null) {
                    continue;
                }
                tradeProfitTotal += item.getProfit();
                tradeStorageTotal += item.getStorageFee();
            }
        }
        return new TradeSummary(
                trades == null ? 0 : trades.size(),
                tradeProfitTotal,
                tradeStorageTotal
        );
    }

    // 仅保留当前盈亏方向下的交易。
    @NonNull
    public static List<TradeRecordItem> filterTradesBySideMode(List<TradeRecordItem> trades,
                                                               TradePnlSideMode sideMode) {
        List<TradeRecordItem> result = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            return result;
        }
        for (TradeRecordItem item : trades) {
            if (item != null && matchesSideMode(item, sideMode)) {
                result.add(item);
            }
        }
        return result;
    }

    // 构建交易盈亏柱状图条目。
    @NonNull
    public static List<TradePnlBarChartView.Entry> buildTradePnlChartEntries(List<TradeRecordItem> trades,
                                                                              TradePnlSideMode sideMode) {
        List<TradePnlBarChartView.Entry> result = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            return result;
        }
        Map<String, Double> byCode = new LinkedHashMap<>();
        for (TradeRecordItem item : trades) {
            if (!matchesSideMode(item, sideMode)) {
                continue;
            }
            String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                code = trim(item.getProductName());
            }
            if (code.isEmpty()) {
                code = "UNKNOWN";
            }
            byCode.put(code, byCode.getOrDefault(code, 0d) + item.getProfit() + item.getStorageFee());
        }
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(byCode.entrySet());
        ordered.sort((left, right) -> Double.compare(Math.abs(right.getValue()), Math.abs(left.getValue())));
        for (Map.Entry<String, Double> entry : ordered) {
            result.add(new TradePnlBarChartView.Entry(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    // 筛出支持分布图的标准品种。
    @NonNull
    public static List<TradeRecordItem> filterTradeDistributionSymbols(List<TradeRecordItem> source) {
        List<TradeRecordItem> filtered = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return filtered;
        }
        for (TradeRecordItem item : source) {
            if (item == null) {
                continue;
            }
            String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
            if (ProductSymbolMapper.isSupportedProduct(code)) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    // 累加盈亏柱状图总盈亏，用于顶部摘要。
    private static double sumTradePnl(List<TradePnlBarChartView.Entry> entries) {
        double total = 0d;
        if (entries == null) {
            return total;
        }
        for (TradePnlBarChartView.Entry entry : entries) {
            if (entry == null) {
                continue;
            }
            total += entry.pnl;
        }
        return total;
    }

    // 计算筛选列表排序结果。
    private static int compareTrades(TradeRecordItem left, TradeRecordItem right, SortMode sortMode) {
        if (sortMode == SortMode.OPEN_TIME) {
            return Long.compare(resolveOpenTime(left), resolveOpenTime(right));
        }
        if (sortMode == SortMode.PROFIT) {
            return Double.compare(left.getProfit(), right.getProfit());
        }
        return Long.compare(resolveCloseTime(left), resolveCloseTime(right));
    }

    // 判断交易是否符合盈亏方向统计模式。
    private static boolean matchesSideMode(TradeRecordItem item, TradePnlSideMode sideMode) {
        if (item == null || sideMode == null || sideMode == TradePnlSideMode.ALL) {
            return true;
        }
        String side = trim(item.getSide()).toLowerCase(Locale.ROOT);
        if (sideMode == TradePnlSideMode.BUY) {
            return side.contains("buy") || side.contains("多") || side.contains("买");
        }
        return side.contains("sell") || side.contains("空") || side.contains("卖");
    }

    // 判断交易是否符合列表方向筛选。
    private static boolean matchesExactSide(TradeRecordItem item, String sideFilter) {
        String normalizedFilter = trim(sideFilter);
        if (normalizedFilter.isEmpty()) {
            return true;
        }
        String normalizedTradeSide = normalizeSide(item == null ? "" : item.getSide()).toLowerCase(Locale.ROOT);
        String normalizedExpectedSide = normalizeSide(normalizedFilter).toLowerCase(Locale.ROOT);
        return normalizedTradeSide.equals(normalizedExpectedSide);
    }

    // 手工区间过滤曲线点。
    @NonNull
    private static List<CurvePoint> filterCurveByManualRange(List<CurvePoint> source,
                                                             long startInclusive,
                                                             long endInclusive) {
        List<CurvePoint> filtered = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return filtered;
        }
        for (CurvePoint point : source) {
            long timestamp = point.getTimestamp();
            if (timestamp >= startInclusive && timestamp <= endInclusive) {
                filtered.add(point);
            }
        }
        return filtered;
    }

    // 按当前时间范围过滤曲线点，并保持至少两点展示语义。
    @NonNull
    private static List<CurvePoint> filterCurveByRange(List<CurvePoint> source, AccountTimeRange range) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        if (range == null || range == AccountTimeRange.ALL) {
            return new ArrayList<>(source);
        }
        long durationMs;
        switch (range) {
            case D1:
                durationMs = 24L * 60L * 60L * 1000L;
                break;
            case D7:
                durationMs = 7L * 24L * 60L * 60L * 1000L;
                break;
            case M1:
                durationMs = 30L * 24L * 60L * 60L * 1000L;
                break;
            case M3:
                durationMs = 90L * 24L * 60L * 60L * 1000L;
                break;
            case Y1:
                durationMs = 365L * 24L * 60L * 60L * 1000L;
                break;
            case ALL:
            default:
                return new ArrayList<>(source);
        }
        long end = source.get(source.size() - 1).getTimestamp();
        long start = end - durationMs;
        List<CurvePoint> filtered = new ArrayList<>();
        for (CurvePoint point : source) {
            if (point.getTimestamp() >= start) {
                filtered.add(point);
            }
        }
        if (filtered.size() >= 2) {
            return filtered;
        }
        if (source.size() >= 2) {
            return new ArrayList<>(source.subList(source.size() - 2, source.size()));
        }
        return new ArrayList<>(source);
    }

    // 曲线百分比以当前显示范围第一点为基准，缺数据时退回 1。
    private static double resolveCurvePercentBase(List<CurvePoint> points) {
        if (points != null && !points.isEmpty()) {
            double firstEquity = points.get(0).getEquity();
            if (firstEquity > 0d) {
                return Math.max(1e-9, firstEquity);
            }
        }
        return 1d;
    }

    private static long resolveOpenTime(TradeRecordItem item) {
        return item == null ? 0L : item.getOpenTime();
    }

    private static long resolveCloseTime(TradeRecordItem item) {
        return item == null ? 0L : item.getCloseTime();
    }

    private static String normalizeSide(String raw) {
        String normalized = trim(raw).toLowerCase(Locale.ROOT);
        if ("buy".equals(normalized) || "买入".equals(normalized) || "多".equals(normalized)) {
            return "Buy";
        }
        if ("sell".equals(normalized) || "卖出".equals(normalized) || "空".equals(normalized)) {
            return "Sell";
        }
        return normalized;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    @NonNull
    private static String formatRatioPercent(double ratio) {
        return String.format(Locale.getDefault(), "%+.2f%%", ratio * 100d);
    }

    public static final class PrepareRequest {
        private final List<AccountMetric> latestStatsMetrics;
        private final List<TradeRecordItem> baseTrades;
        private final List<CurvePoint> allCurvePoints;
        private final AccountTimeRange selectedRange;
        private final boolean manualCurveRangeEnabled;
        private final long manualCurveRangeStartMs;
        private final long manualCurveRangeEndMs;
        private final TradePnlSideMode tradePnlSideMode;
        private final TradeWeekdayBasis tradeWeekdayBasis;
        private final TradeFilterRequest tradeFilterRequest;

        public PrepareRequest(List<AccountMetric> latestStatsMetrics,
                              List<TradeRecordItem> baseTrades,
                              List<CurvePoint> allCurvePoints,
                              AccountTimeRange selectedRange,
                              boolean manualCurveRangeEnabled,
                              long manualCurveRangeStartMs,
                              long manualCurveRangeEndMs,
                              TradePnlSideMode tradePnlSideMode,
                              TradeWeekdayBasis tradeWeekdayBasis,
                              TradeFilterRequest tradeFilterRequest) {
            this.latestStatsMetrics = latestStatsMetrics == null ? new ArrayList<>() : new ArrayList<>(latestStatsMetrics);
            this.baseTrades = baseTrades == null ? new ArrayList<>() : new ArrayList<>(baseTrades);
            this.allCurvePoints = allCurvePoints == null ? new ArrayList<>() : new ArrayList<>(allCurvePoints);
            this.selectedRange = selectedRange;
            this.manualCurveRangeEnabled = manualCurveRangeEnabled;
            this.manualCurveRangeStartMs = manualCurveRangeStartMs;
            this.manualCurveRangeEndMs = manualCurveRangeEndMs;
            this.tradePnlSideMode = tradePnlSideMode == null ? TradePnlSideMode.ALL : tradePnlSideMode;
            this.tradeWeekdayBasis = tradeWeekdayBasis == null ? TradeWeekdayBasis.CLOSE_TIME : tradeWeekdayBasis;
            this.tradeFilterRequest = tradeFilterRequest == null
                    ? new TradeFilterRequest("", true, "", true, SortMode.CLOSE_TIME, true)
                    : tradeFilterRequest;
        }

        public List<AccountMetric> getLatestStatsMetrics() {
            return latestStatsMetrics;
        }

        public List<TradeRecordItem> getBaseTrades() {
            return baseTrades;
        }

        public List<CurvePoint> getAllCurvePoints() {
            return allCurvePoints;
        }

        public AccountTimeRange getSelectedRange() {
            return selectedRange;
        }

        public boolean isManualCurveRangeEnabled() {
            return manualCurveRangeEnabled;
        }

        public long getManualCurveRangeStartMs() {
            return manualCurveRangeStartMs;
        }

        public long getManualCurveRangeEndMs() {
            return manualCurveRangeEndMs;
        }

        public TradePnlSideMode getTradePnlSideMode() {
            return tradePnlSideMode;
        }

        public TradeWeekdayBasis getTradeWeekdayBasis() {
            return tradeWeekdayBasis;
        }

        public TradeFilterRequest getTradeFilterRequest() {
            return tradeFilterRequest;
        }
    }

    public static final class TradeFilterRequest {
        private final String productFilter;
        private final boolean allProducts;
        private final String sideFilter;
        private final boolean allSides;
        private final SortMode sortMode;
        private final boolean descending;

        public TradeFilterRequest(String productFilter,
                                  boolean allProducts,
                                  String sideFilter,
                                  boolean allSides,
                                  SortMode sortMode,
                                  boolean descending) {
            this.productFilter = trim(productFilter);
            this.allProducts = allProducts;
            this.sideFilter = trim(sideFilter);
            this.allSides = allSides;
            this.sortMode = sortMode == null ? SortMode.CLOSE_TIME : sortMode;
            this.descending = descending;
        }

        public String getProductFilter() {
            return productFilter;
        }

        public boolean isAllProducts() {
            return allProducts;
        }

        public String getSideFilter() {
            return sideFilter;
        }

        public boolean isAllSides() {
            return allSides;
        }

        public SortMode getSortMode() {
            return sortMode;
        }

        public boolean isDescending() {
            return descending;
        }
    }

    public static final class PreparedSnapshotSections {
        private final List<String> tradeProducts;
        private final CurveProjection curveProjection;
        private final TradeAnalytics tradeAnalytics;
        private final List<TradeRecordItem> filteredTrades;
        private final TradeSummary tradeSummary;

        public PreparedSnapshotSections(List<String> tradeProducts,
                                        CurveProjection curveProjection,
                                        TradeAnalytics tradeAnalytics,
                                        List<TradeRecordItem> filteredTrades,
                                        TradeSummary tradeSummary) {
            this.tradeProducts = tradeProducts == null ? new ArrayList<>() : new ArrayList<>(tradeProducts);
            this.curveProjection = curveProjection == null
                    ? new CurveProjection(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null, 1d, 0L, 1L, false)
                    : curveProjection;
            this.tradeAnalytics = tradeAnalytics == null
                    ? new TradeAnalytics(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0d)
                    : tradeAnalytics;
            this.filteredTrades = filteredTrades == null ? new ArrayList<>() : new ArrayList<>(filteredTrades);
            this.tradeSummary = tradeSummary == null ? new TradeSummary(0, 0d, 0d) : tradeSummary;
        }

        public List<String> getTradeProducts() {
            return tradeProducts;
        }

        public CurveProjection getCurveProjection() {
            return curveProjection;
        }

        public List<AccountMetric> getTradeStatsMetrics() {
            return tradeAnalytics.getTradeStatsMetrics();
        }

        public List<TradePnlBarChartView.Entry> getTradePnlEntries() {
            return tradeAnalytics.getTradePnlEntries();
        }

        public List<CurveAnalyticsHelper.TradeScatterPoint> getTradeScatterPoints() {
            return tradeAnalytics.getTradeScatterPoints();
        }

        public List<CurveAnalyticsHelper.DurationBucket> getHoldingDurationBuckets() {
            return tradeAnalytics.getHoldingDurationBuckets();
        }

        public List<TradeWeekdayBarChartHelper.Entry> getTradeWeekdayEntries() {
            return tradeAnalytics.getTradeWeekdayEntries();
        }

        public double getTradePnlTotal() {
            return tradeAnalytics.getTradePnlTotal();
        }

        public List<TradeRecordItem> getFilteredTrades() {
            return filteredTrades;
        }

        public TradeSummary getTradeSummary() {
            return tradeSummary;
        }
    }

    public static final class TradeAnalytics {
        private final List<AccountMetric> tradeStatsMetrics;
        private final List<TradePnlBarChartView.Entry> tradePnlEntries;
        private final List<CurveAnalyticsHelper.TradeScatterPoint> tradeScatterPoints;
        private final List<CurveAnalyticsHelper.DurationBucket> holdingDurationBuckets;
        private final List<TradeWeekdayBarChartHelper.Entry> tradeWeekdayEntries;
        private final double tradePnlTotal;

        public TradeAnalytics(List<AccountMetric> tradeStatsMetrics,
                              List<TradePnlBarChartView.Entry> tradePnlEntries,
                              List<CurveAnalyticsHelper.TradeScatterPoint> tradeScatterPoints,
                              List<CurveAnalyticsHelper.DurationBucket> holdingDurationBuckets,
                              List<TradeWeekdayBarChartHelper.Entry> tradeWeekdayEntries,
                              double tradePnlTotal) {
            this.tradeStatsMetrics = tradeStatsMetrics == null ? new ArrayList<>() : new ArrayList<>(tradeStatsMetrics);
            this.tradePnlEntries = tradePnlEntries == null ? new ArrayList<>() : new ArrayList<>(tradePnlEntries);
            this.tradeScatterPoints = tradeScatterPoints == null ? new ArrayList<>() : new ArrayList<>(tradeScatterPoints);
            this.holdingDurationBuckets = holdingDurationBuckets == null ? new ArrayList<>() : new ArrayList<>(holdingDurationBuckets);
            this.tradeWeekdayEntries = tradeWeekdayEntries == null ? new ArrayList<>() : new ArrayList<>(tradeWeekdayEntries);
            this.tradePnlTotal = tradePnlTotal;
        }

        public List<AccountMetric> getTradeStatsMetrics() {
            return tradeStatsMetrics;
        }

        public List<TradePnlBarChartView.Entry> getTradePnlEntries() {
            return tradePnlEntries;
        }

        public List<CurveAnalyticsHelper.TradeScatterPoint> getTradeScatterPoints() {
            return tradeScatterPoints;
        }

        public List<CurveAnalyticsHelper.DurationBucket> getHoldingDurationBuckets() {
            return holdingDurationBuckets;
        }

        public List<TradeWeekdayBarChartHelper.Entry> getTradeWeekdayEntries() {
            return tradeWeekdayEntries;
        }

        public double getTradePnlTotal() {
            return tradePnlTotal;
        }
    }

    public static final class CurveProjection {
        private final List<CurvePoint> displayedCurvePoints;
        private final List<CurveAnalyticsHelper.DrawdownPoint> drawdownPoints;
        private final List<CurveAnalyticsHelper.DailyReturnPoint> dailyReturnPoints;
        private final CurveAnalyticsHelper.DrawdownSegment drawdownSegment;
        private final double curveBaseBalance;
        private final long viewportStartTs;
        private final long viewportEndTs;
        private final boolean manualRangeApplied;

        public CurveProjection(List<CurvePoint> displayedCurvePoints,
                               List<CurveAnalyticsHelper.DrawdownPoint> drawdownPoints,
                               List<CurveAnalyticsHelper.DailyReturnPoint> dailyReturnPoints,
                               CurveAnalyticsHelper.DrawdownSegment drawdownSegment,
                               double curveBaseBalance,
                               long viewportStartTs,
                               long viewportEndTs,
                               boolean manualRangeApplied) {
            this.displayedCurvePoints = displayedCurvePoints == null ? new ArrayList<>() : new ArrayList<>(displayedCurvePoints);
            this.drawdownPoints = drawdownPoints == null ? new ArrayList<>() : new ArrayList<>(drawdownPoints);
            this.dailyReturnPoints = dailyReturnPoints == null ? new ArrayList<>() : new ArrayList<>(dailyReturnPoints);
            this.drawdownSegment = drawdownSegment;
            this.curveBaseBalance = curveBaseBalance;
            this.viewportStartTs = viewportStartTs;
            this.viewportEndTs = viewportEndTs;
            this.manualRangeApplied = manualRangeApplied;
        }

        public List<CurvePoint> getDisplayedCurvePoints() {
            return displayedCurvePoints;
        }

        public List<CurveAnalyticsHelper.DrawdownPoint> getDrawdownPoints() {
            return drawdownPoints;
        }

        public List<CurveAnalyticsHelper.DailyReturnPoint> getDailyReturnPoints() {
            return dailyReturnPoints;
        }

        public CurveAnalyticsHelper.DrawdownSegment getDrawdownSegment() {
            return drawdownSegment;
        }

        public double getCurveBaseBalance() {
            return curveBaseBalance;
        }

        public long getViewportStartTs() {
            return viewportStartTs;
        }

        public long getViewportEndTs() {
            return viewportEndTs;
        }

        public boolean isManualRangeApplied() {
            return manualRangeApplied;
        }
    }

    public static final class TradeSummary {
        private final int tradeCount;
        private final double tradeProfitTotal;
        private final double tradeStorageTotal;

        public TradeSummary(int tradeCount, double tradeProfitTotal, double tradeStorageTotal) {
            this.tradeCount = tradeCount;
            this.tradeProfitTotal = tradeProfitTotal;
            this.tradeStorageTotal = tradeStorageTotal;
        }

        public int getTradeCount() {
            return tradeCount;
        }

        public double getTradeProfitTotal() {
            return tradeProfitTotal;
        }

        public double getTradeStorageTotal() {
            return tradeStorageTotal;
        }
    }
}
