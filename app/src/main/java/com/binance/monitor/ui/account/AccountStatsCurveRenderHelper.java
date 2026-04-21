/*
 * 账户统计页曲线渲染助手，负责把主曲线、回撤、日收益和元信息一次性绑定到界面。
 * 供 AccountStatsBridgeActivity 复用，避免重曲线 helper 继续堆在旧 Activity 里。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.databinding.ContentAccountStatsBinding;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.ui.account.adapter.AccountMetricAdapter;

import java.util.ArrayList;
import java.util.List;

final class AccountStatsCurveRenderHelper {

    // 记录一次曲线绑定后的显示状态，供页面更新自身字段缓存。
    static final class RenderResult {
        private final List<CurvePoint> displayedCurvePoints;
        private final List<CurveAnalyticsHelper.DrawdownPoint> displayedDrawdownPoints;
        private final List<CurveAnalyticsHelper.DailyReturnPoint> displayedDailyReturnPoints;
        private final String defaultCurveMeta;
        private final double curveBaseBalance;

        private RenderResult(@NonNull List<CurvePoint> displayedCurvePoints,
                             @NonNull List<CurveAnalyticsHelper.DrawdownPoint> displayedDrawdownPoints,
                             @NonNull List<CurveAnalyticsHelper.DailyReturnPoint> displayedDailyReturnPoints,
                             @NonNull String defaultCurveMeta,
                             double curveBaseBalance) {
            this.displayedCurvePoints = displayedCurvePoints;
            this.displayedDrawdownPoints = displayedDrawdownPoints;
            this.displayedDailyReturnPoints = displayedDailyReturnPoints;
            this.defaultCurveMeta = defaultCurveMeta;
            this.curveBaseBalance = curveBaseBalance;
        }

        @NonNull
        List<CurvePoint> getDisplayedCurvePoints() {
            return displayedCurvePoints;
        }

        @NonNull
        List<CurveAnalyticsHelper.DrawdownPoint> getDisplayedDrawdownPoints() {
            return displayedDrawdownPoints;
        }

        @NonNull
        List<CurveAnalyticsHelper.DailyReturnPoint> getDisplayedDailyReturnPoints() {
            return displayedDailyReturnPoints;
        }

        @NonNull
        String getDefaultCurveMeta() {
            return defaultCurveMeta;
        }

        double getCurveBaseBalance() {
            return curveBaseBalance;
        }
    }

    private final ContentAccountStatsBinding binding;
    private final AccountMetricAdapter indicatorAdapter;

    AccountStatsCurveRenderHelper(@NonNull ContentAccountStatsBinding binding,
                                  @NonNull AccountMetricAdapter indicatorAdapter) {
        this.binding = binding;
        this.indicatorAdapter = indicatorAdapter;
    }

    // 绑定当前立即显示的曲线和派生附图。
    @NonNull
    RenderResult renderImmediate(@Nullable List<CurvePoint> points,
                                 @Nullable List<AccountMetric> latestCurveIndicators,
                                 boolean secondarySectionsAttached,
                                 boolean privacyMasked) {
        List<CurvePoint> effectivePoints = points == null
                ? new ArrayList<>()
                : new ArrayList<>(points);
        CurveAnalyticsHelper.DrawdownSegment drawdownSegment =
                CurveAnalyticsHelper.resolveMaxDrawdownSegment(effectivePoints);
        double curveBaseBalance = resolveCurvePercentBase(effectivePoints);
        binding.equityCurveView.setBaseBalance(curveBaseBalance);

        long viewportStartTs = effectivePoints.isEmpty() ? 0L : effectivePoints.get(0).getTimestamp();
        long viewportEndTs = effectivePoints.size() > 1
                ? effectivePoints.get(effectivePoints.size() - 1).getTimestamp()
                : viewportStartTs + 1L;
        applyDrawdownHighlight(drawdownSegment);
        binding.equityCurveView.setPoints(effectivePoints);

        String defaultCurveMeta = buildCurveMeta(effectivePoints, drawdownSegment);
        binding.tvCurveMeta.setText(privacyMasked
                ? AccountStatsPrivacyFormatter.maskValue(defaultCurveMeta, true)
                : defaultCurveMeta);

        List<CurveAnalyticsHelper.DrawdownPoint> drawdownPoints = new ArrayList<>();
        List<CurveAnalyticsHelper.DailyReturnPoint> dailyReturnPoints = new ArrayList<>();
        if (secondarySectionsAttached) {
            binding.positionRatioChartView.setViewport(viewportStartTs, viewportEndTs);
            binding.positionRatioChartView.setPoints(effectivePoints);
            drawdownPoints = CurveAnalyticsHelper.buildDrawdownSeries(effectivePoints);
            dailyReturnPoints = CurveAnalyticsHelper.buildDailyReturnSeries(effectivePoints);
            binding.drawdownChartView.setViewport(viewportStartTs, viewportEndTs);
            binding.drawdownChartView.setPoints(drawdownPoints);
            binding.dailyReturnChartView.setViewport(viewportStartTs, viewportEndTs);
            binding.dailyReturnChartView.setPoints(dailyReturnPoints);
        }
        indicatorAdapter.submitList(buildCurveIndicators(latestCurveIndicators));
        return new RenderResult(
                effectivePoints,
                drawdownPoints,
                dailyReturnPoints,
                defaultCurveMeta,
                curveBaseBalance
        );
    }

    // 绑定后台已准备好的曲线投影结果，避免主线程再次重复推导附图。
    @NonNull
    RenderResult applyPreparedProjection(@Nullable AccountDeferredSnapshotRenderHelper.CurveProjection curveProjection,
                                         @Nullable List<AccountMetric> latestCurveIndicators,
                                         boolean secondarySectionsAttached,
                                         boolean privacyMasked) {
        if (curveProjection == null) {
            return renderImmediate(new ArrayList<>(), latestCurveIndicators, secondarySectionsAttached, privacyMasked);
        }
        List<CurvePoint> effectivePoints = curveProjection.getDisplayedCurvePoints() == null
                ? new ArrayList<>()
                : new ArrayList<>(curveProjection.getDisplayedCurvePoints());
        CurveAnalyticsHelper.DrawdownSegment drawdownSegment = curveProjection.getDrawdownSegment();
        double curveBaseBalance = curveProjection.getCurveBaseBalance();
        binding.equityCurveView.setBaseBalance(curveBaseBalance);
        applyDrawdownHighlight(drawdownSegment);
        binding.equityCurveView.setPoints(effectivePoints);

        String defaultCurveMeta = buildCurveMeta(effectivePoints, drawdownSegment);
        binding.tvCurveMeta.setText(privacyMasked
                ? AccountStatsPrivacyFormatter.maskValue(defaultCurveMeta, true)
                : defaultCurveMeta);

        List<CurveAnalyticsHelper.DrawdownPoint> drawdownPoints = curveProjection.getDrawdownPoints() == null
                ? new ArrayList<>()
                : new ArrayList<>(curveProjection.getDrawdownPoints());
        List<CurveAnalyticsHelper.DailyReturnPoint> dailyReturnPoints = curveProjection.getDailyReturnPoints() == null
                ? new ArrayList<>()
                : new ArrayList<>(curveProjection.getDailyReturnPoints());
        if (secondarySectionsAttached) {
            binding.positionRatioChartView.setViewport(
                    curveProjection.getViewportStartTs(),
                    curveProjection.getViewportEndTs()
            );
            binding.positionRatioChartView.setPoints(effectivePoints);
            binding.drawdownChartView.setViewport(
                    curveProjection.getViewportStartTs(),
                    curveProjection.getViewportEndTs()
            );
            binding.drawdownChartView.setPoints(drawdownPoints);
            binding.dailyReturnChartView.setViewport(
                    curveProjection.getViewportStartTs(),
                    curveProjection.getViewportEndTs()
            );
            binding.dailyReturnChartView.setPoints(dailyReturnPoints);
        }
        indicatorAdapter.submitList(buildCurveIndicators(latestCurveIndicators));
        return new RenderResult(
                effectivePoints,
                drawdownPoints,
                dailyReturnPoints,
                defaultCurveMeta,
                curveBaseBalance
        );
    }

    // 同步主图上的回撤高亮。
    private void applyDrawdownHighlight(@Nullable CurveAnalyticsHelper.DrawdownSegment drawdownSegment) {
        if (drawdownSegment == null) {
            binding.equityCurveView.setDrawdownHighlight(0L, 0L, 0d, 0d);
            return;
        }
        binding.equityCurveView.setDrawdownHighlight(
                drawdownSegment.getPeakTimestamp(),
                drawdownSegment.getValleyTimestamp(),
                drawdownSegment.getPeakEquity(),
                drawdownSegment.getValleyEquity()
        );
    }

    // 曲线百分比以当前显示范围第一点为基准，缺数据时退回 1。
    private static double resolveCurvePercentBase(@Nullable List<CurvePoint> points) {
        if (points != null && !points.isEmpty()) {
            double firstEquity = points.get(0).getEquity();
            if (firstEquity > 0d) {
                return Math.max(1e-9, firstEquity);
            }
        }
        return 1d;
    }

    // 生成曲线元信息，供主图外侧摘要复用。
    @NonNull
    private static String buildCurveMeta(@Nullable List<CurvePoint> points,
                                         @Nullable CurveAnalyticsHelper.DrawdownSegment drawdownSegment) {
        return "";
    }

    // 没有服务端指标时回退到占位指标，保证 UI 结构稳定。
    @NonNull
    private static List<AccountMetric> buildCurveIndicators(@Nullable List<AccountMetric> snapshotIndicators) {
        if (snapshotIndicators != null && !snapshotIndicators.isEmpty()) {
            return new ArrayList<>(snapshotIndicators);
        }
        List<AccountMetric> result = new ArrayList<>();
        result.add(new AccountMetric("近1日收益", "--"));
        result.add(new AccountMetric("近7日收益", "--"));
        result.add(new AccountMetric("近30日收益", "--"));
        result.add(new AccountMetric("累计收益", "--"));
        result.add(new AccountMetric("最大回撤", "--"));
        result.add(new AccountMetric("夏普比率", "--"));
        return result;
    }

}
