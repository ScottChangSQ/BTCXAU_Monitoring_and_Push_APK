/*
 * 账户统计页收益表渲染助手，负责把收益表主链从旧 Activity 中抽离。
 * 低层表格控件创建仍复用宿主已有能力，避免同一套 UI 细节再复制一遍。
 */
package com.binance.monitor.ui.account;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.databinding.ContentAccountStatsBinding;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class AccountStatsReturnsTableHelper {

    enum ReturnStatsMode {
        MONTH,
        DAY,
        YEAR,
        STAGE
    }

    enum ReturnValueMode {
        RATE,
        AMOUNT
    }

    interface Host {
        @NonNull ContentAccountStatsBinding getBinding();
        boolean isPrivacyMasked();
        long resolveCloseTime(@Nullable TradeRecordItem item);
        @NonNull String formatMonthLabel(long timeMs);
        int resolveReturnDisplayColor(double rate, double amount, int neutralColorRes);
        @NonNull String formatReturnValue(double rate, double amount, boolean dayMode);
        void applyCurveRangeFromTableSelection(long startMs, long endMs);
        long startOfDay(long timeMs);
        long endOfDay(long timeMs);
        long startOfMonth(long timeMs);
        long endOfMonth(long timeMs);
        long startOfYear(long timeMs);
        @NonNull List<CurvePoint> filterCurveByManualRange(@Nullable List<CurvePoint> source, long startInclusive, long endInclusive);
        void applyReturnsCellLayout(@NonNull View cell,
                                    int widthDp,
                                    float weight,
                                    int heightDp,
                                    int marginLeftDp,
                                    int marginTopDp,
                                    int marginRightDp,
                                    int marginBottomDp);
        @NonNull TableRow createSimpleHeaderRow(@NonNull String[] headers, int widthDp);
        @NonNull TableRow createAlignedReturnsRow(@NonNull CharSequence label,
                                                  @NonNull CharSequence value,
                                                  boolean header,
                                                  @Nullable Integer valueColor,
                                                  @Nullable Double heatRate);
        @NonNull TableRow createAlignedReturnsRow(@NonNull CharSequence label,
                                                  @NonNull CharSequence value,
                                                  boolean header,
                                                  @Nullable Integer valueColor,
                                                  @Nullable Double heatRate,
                                                  @Nullable View.OnClickListener clickListener);
        @NonNull View createDailyReturnsCell(@NonNull String label,
                                             @Nullable String value,
                                             int labelColor,
                                             @Nullable Integer valueColor,
                                             @Nullable View.OnClickListener clickListener,
                                             @Nullable Double heatRate);
        @NonNull LinearLayout createMonthlyGroupedBlock(@NonNull YearlyReturnRow rowData);
    }

    static final class RenderRequest {
        private final List<CurvePoint> sourceCurvePoints;
        private final List<TradeRecordItem> baseTrades;
        private final List<CurvePoint> allCurvePoints;
        private final ReturnStatsMode returnStatsMode;
        private final long returnStatsAnchorDateMs;
        private final boolean manualCurveRangeEnabled;
        private final long manualCurveRangeStartMs;
        private final long manualCurveRangeEndMs;

        RenderRequest(@Nullable List<CurvePoint> sourceCurvePoints,
                      @Nullable List<TradeRecordItem> baseTrades,
                      @Nullable List<CurvePoint> allCurvePoints,
                      @NonNull ReturnStatsMode returnStatsMode,
                      long returnStatsAnchorDateMs,
                      boolean manualCurveRangeEnabled,
                      long manualCurveRangeStartMs,
                      long manualCurveRangeEndMs) {
            this.sourceCurvePoints = sourceCurvePoints == null ? new ArrayList<>() : new ArrayList<>(sourceCurvePoints);
            this.baseTrades = baseTrades == null ? new ArrayList<>() : new ArrayList<>(baseTrades);
            this.allCurvePoints = allCurvePoints == null ? new ArrayList<>() : new ArrayList<>(allCurvePoints);
            this.returnStatsMode = returnStatsMode;
            this.returnStatsAnchorDateMs = returnStatsAnchorDateMs;
            this.manualCurveRangeEnabled = manualCurveRangeEnabled;
            this.manualCurveRangeStartMs = manualCurveRangeStartMs;
            this.manualCurveRangeEndMs = manualCurveRangeEndMs;
        }
    }

    static final class RenderResult {
        private final long resolvedAnchorDateMs;

        RenderResult(long resolvedAnchorDateMs) {
            this.resolvedAnchorDateMs = resolvedAnchorDateMs;
        }

        long getResolvedAnchorDateMs() {
            return resolvedAnchorDateMs;
        }
    }

    private final Host host;

    AccountStatsReturnsTableHelper(@NonNull Host host) {
        this.host = host;
    }

    @NonNull
    RenderResult render(@NonNull RenderRequest request) {
        ContentAccountStatsBinding binding = host.getBinding();
        binding.tableMonthlyReturns.removeAllViews();
        binding.tvMonthlyReturnsHint.setVisibility(View.GONE);
        binding.tvMonthlyReturnsHint.setText("");

        long resolvedAnchor = request.returnStatsAnchorDateMs;
        if (!request.baseTrades.isEmpty()) {
            resolvedAnchor = resolveTradeReturnReferenceTime(request.baseTrades, resolvedAnchor);
            String periodText = host.isPrivacyMasked()
                    ? SensitiveDisplayMasker.MASK_TEXT
                    : host.formatMonthLabel(resolvedAnchor);
            binding.tvReturnsPeriod.setText(periodText);
            switch (request.returnStatsMode) {
                case DAY:
                    binding.tvReturnsPeriod.setVisibility(View.VISIBLE);
                    binding.tvReturnsPeriod.setClickable(true);
                    renderDailyReturnsTableFromTrades(request, resolvedAnchor);
                    break;
                case YEAR:
                    binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                    binding.tvReturnsPeriod.setClickable(false);
                    renderYearlyReturnsTableFromTrades(request);
                    break;
                case STAGE:
                    binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                    binding.tvReturnsPeriod.setClickable(false);
                    renderStageReturnsTableFromTrades(request, resolvedAnchor);
                    break;
                case MONTH:
                default:
                    binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                    binding.tvReturnsPeriod.setClickable(false);
                    renderMonthlyReturnsTableFromTrades(request);
                    break;
            }
            return new RenderResult(resolvedAnchor);
        }

        if (request.sourceCurvePoints.size() < 2) {
            binding.tvReturnsPeriod.setText("--");
            return new RenderResult(resolvedAnchor);
        }
        resolvedAnchor = resolveCurveReturnReferenceTime(request.sourceCurvePoints, resolvedAnchor);
        String periodText = host.isPrivacyMasked()
                ? SensitiveDisplayMasker.MASK_TEXT
                : host.formatMonthLabel(resolvedAnchor);
        binding.tvReturnsPeriod.setText(periodText);
        switch (request.returnStatsMode) {
            case DAY:
                binding.tvReturnsPeriod.setVisibility(View.VISIBLE);
                binding.tvReturnsPeriod.setClickable(true);
                renderDailyReturnsTable(request, resolvedAnchor);
                break;
            case YEAR:
                binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                binding.tvReturnsPeriod.setClickable(false);
                renderYearlyReturnsTable(request);
                break;
            case STAGE:
                binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                binding.tvReturnsPeriod.setClickable(false);
                renderStageReturnsTable(request, resolvedAnchor);
                break;
            case MONTH:
            default:
                binding.tvReturnsPeriod.setVisibility(View.INVISIBLE);
                binding.tvReturnsPeriod.setClickable(false);
                renderMonthlyReturnsTable(request);
                break;
        }
        return new RenderResult(resolvedAnchor);
    }

    private long resolveTradeReturnReferenceTime(@NonNull List<TradeRecordItem> trades, long currentAnchor) {
        long latest = 0L;
        for (TradeRecordItem item : trades) {
            latest = Math.max(latest, host.resolveCloseTime(item));
        }
        if (currentAnchor <= 0L || currentAnchor > latest) {
            return latest;
        }
        return Math.min(currentAnchor, latest);
    }

    private long resolveCurveReturnReferenceTime(@NonNull List<CurvePoint> source, long currentAnchor) {
        long latest = source.get(source.size() - 1).getTimestamp();
        if (currentAnchor <= 0L || currentAnchor > latest) {
            return latest;
        }
        return Math.min(currentAnchor, latest);
    }

    private static double safeDivide(double numerator, double denominator) {
        if (Math.abs(denominator) < 1e-9) {
            return 0d;
        }
        return numerator / denominator;
    }

    private void renderDailyReturnsTableFromTrades(@NonNull RenderRequest request, long referenceTime) {
        ContentAccountStatsBinding binding = host.getBinding();
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setShrinkAllColumns(true);
        table.setStretchAllColumns(true);
        table.addView(host.createSimpleHeaderRow(new String[]{"一", "二", "三", "四", "五", "六", "日"}, 48));

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(referenceTime);
        int year = target.get(Calendar.YEAR);
        int month = target.get(Calendar.MONTH);

        Map<Integer, MonthReturnInfo> dayInfoMap = new LinkedHashMap<>();
        for (TradeRecordItem trade : request.baseTrades) {
            if (trade == null) {
                continue;
            }
            long closeTime = host.resolveCloseTime(trade);
            if (closeTime <= 0L) {
                continue;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(closeTime);
            if (calendar.get(Calendar.YEAR) != year || calendar.get(Calendar.MONTH) != month) {
                continue;
            }
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            MonthReturnInfo info = dayInfoMap.get(day);
            if (info == null) {
                info = new MonthReturnInfo();
                info.startMs = host.startOfDay(closeTime);
                info.endMs = host.endOfDay(closeTime);
                info.hasData = true;
                dayInfoMap.put(day, info);
            }
            info.returnAmount += trade.getProfit() + trade.getStorageFee();
        }
        for (MonthReturnInfo info : dayInfoMap.values()) {
            info.returnRate = AccountPeriodReturnHelper.resolvePeriodReturnRate(
                    request.allCurvePoints,
                    info.startMs,
                    info.returnAmount
            );
        }

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(year, month, 1, 0, 0, 0);
        firstDay.set(Calendar.MILLISECOND, 0);
        int firstWeek = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar prevMonth = Calendar.getInstance();
        prevMonth.set(year, month, 1, 0, 0, 0);
        prevMonth.add(Calendar.MONTH, -1);
        int prevDaysInMonth = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        int nextDayValue = 1;

        int totalCells = firstWeek + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7d);
        int dayValue = 1;
        for (int row = 0; row < rows; row++) {
            TableRow tableRow = new TableRow(binding.getRoot().getContext());
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || dayValue > daysInMonth) {
                    String ghostLabel = index < firstWeek
                            ? String.valueOf(prevDaysInMonth - (firstWeek - index) + 1)
                            : String.valueOf(nextDayValue++);
                    View ghostCell = host.createDailyReturnsCell(
                            ghostLabel,
                            null,
                            androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), com.binance.monitor.R.color.text_secondary),
                            null,
                            null,
                            null
                    );
                    host.applyReturnsCellLayout(ghostCell, 0, 1f, 42, 1, 1, 1, 1);
                    tableRow.addView(ghostCell);
                    continue;
                }
                MonthReturnInfo info = dayInfoMap.get(dayValue);
                String valueText = host.formatReturnValue(
                        info == null ? 0d : info.returnRate,
                        info == null ? 0d : info.returnAmount,
                        true
                );
                int color = host.resolveReturnDisplayColor(
                        info == null ? 0d : info.returnRate,
                        info == null ? 0d : info.returnAmount,
                        com.binance.monitor.R.color.text_secondary
                );
                View.OnClickListener click = null;
                Double heatRate = 0d;
                if (info != null && info.hasData && info.endMs > info.startMs) {
                    long startMs = info.startMs;
                    long endMs = info.endMs;
                    click = v -> host.applyCurveRangeFromTableSelection(startMs, endMs);
                    heatRate = info.returnRate;
                }
                View dayCell = host.createDailyReturnsCell(
                        String.valueOf(dayValue),
                        valueText,
                        androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), com.binance.monitor.R.color.text_primary),
                        color,
                        click,
                        heatRate
                );
                host.applyReturnsCellLayout(dayCell, 0, 1f, 42, 1, 1, 1, 1);
                tableRow.addView(dayCell);
                dayValue++;
            }
            table.addView(tableRow);
        }
    }

    private void renderMonthlyReturnsTableFromTrades(@NonNull RenderRequest request) {
        rebuildMonthlyTableThreeRowsV4(buildMonthlyReturnRowsFromTrades(request.baseTrades, request.allCurvePoints));
    }

    private void renderYearlyReturnsTableFromTrades(@NonNull RenderRequest request) {
        TableLayout table = host.getBinding().tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        String valueHeader = host.formatReturnValue(0d, 0d, false).contains("%") ? "收益率" : "收益额";
        table.addView(host.createAlignedReturnsRow("年份", valueHeader, true, null, null));

        List<YearlyReturnRow> rows = buildMonthlyReturnRowsFromTrades(request.baseTrades, request.allCurvePoints);
        for (YearlyReturnRow row : rows) {
            int color = host.resolveReturnDisplayColor(row.yearReturnRate, row.yearReturnAmount, com.binance.monitor.R.color.text_secondary);
            String valueText = host.formatReturnValue(row.yearReturnRate, row.yearReturnAmount, false);
            long startMs = row.startMs;
            long endMs = row.endMs;
            table.addView(host.createAlignedReturnsRow(
                    row.year + "年",
                    valueText,
                    false,
                    color,
                    row.yearReturnRate,
                    v -> host.applyCurveRangeFromTableSelection(startMs, endMs)
            ));
        }
    }

    private void renderStageReturnsTableFromTrades(@NonNull RenderRequest request, long referenceTime) {
        TableLayout table = host.getBinding().tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        String valueHeader = host.formatReturnValue(0d, 0d, false).contains("%") ? "收益率" : "收益额";
        table.addView(host.createAlignedReturnsRow("阶段", valueHeader, true, null, null));

        long endMs = host.endOfDay(referenceTime);
        long allStart = resolveTradeRangeStart(request.baseTrades);
        for (StageRange stage : buildStageRanges(request, allStart, endMs)) {
            MonthReturnInfo info = buildTradeReturnInfo(request.baseTrades, request.allCurvePoints, stage.startMs, stage.endMs);
            if (info == null || !info.hasData) {
                table.addView(host.createAlignedReturnsRow(stage.label, "--", false, null, null));
                continue;
            }
            int color = host.resolveReturnDisplayColor(info.returnRate, info.returnAmount, com.binance.monitor.R.color.text_secondary);
            String valueText = host.formatReturnValue(info.returnRate, info.returnAmount, false);
            table.addView(host.createAlignedReturnsRow(
                    stage.label,
                    valueText,
                    false,
                    color,
                    info.returnRate,
                    v -> host.applyCurveRangeFromTableSelection(stage.startMs, stage.endMs)
            ));
        }
    }

    private void renderDailyReturnsTable(@NonNull RenderRequest request, long referenceTime) {
        List<CurvePoint> sorted = new ArrayList<>(request.sourceCurvePoints);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(referenceTime);
        int year = target.get(Calendar.YEAR);
        int month = target.get(Calendar.MONTH);

        Map<Integer, DayBucket> dayBuckets = new LinkedHashMap<>();
        Map<Integer, Double> closeByDay = new LinkedHashMap<>();
        List<Integer> dayOrder = new ArrayList<>();
        for (CurvePoint point : sorted) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(point.getTimestamp());
            if (calendar.get(Calendar.YEAR) != year || calendar.get(Calendar.MONTH) != month) {
                continue;
            }
            int day = calendar.get(Calendar.DAY_OF_MONTH);
            int key = year * 10_000 + (month + 1) * 100 + day;
            DayBucket bucket = dayBuckets.get(day);
            if (bucket == null) {
                bucket = new DayBucket();
                dayBuckets.put(day, bucket);
                dayOrder.add(key);
            }
            if (bucket.startMs == 0L || point.getTimestamp() < bucket.startMs) {
                bucket.startMs = point.getTimestamp();
            }
            if (point.getTimestamp() >= bucket.endMs) {
                bucket.endMs = point.getTimestamp();
                bucket.closeEquity = point.getBalance();
                closeByDay.put(key, point.getBalance());
            }
        }
        rebuildDailyTableV3(year, month, dayBuckets, closeByDay, dayOrder);
    }

    private void renderMonthlyReturnsTable(@NonNull RenderRequest request) {
        rebuildMonthlyTableThreeRowsV4(buildMonthlyReturnRows(request.sourceCurvePoints));
    }

    private void renderYearlyReturnsTable(@NonNull RenderRequest request) {
        TableLayout table = host.getBinding().tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        String valueHeader = host.formatReturnValue(0d, 0d, false).contains("%") ? "收益率" : "收益额";
        table.addView(host.createAlignedReturnsRow("年份", valueHeader, true, null, null));

        List<CurvePoint> sorted = new ArrayList<>(request.sourceCurvePoints);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));
        Map<Integer, PeriodBucket> yearBuckets = new TreeMap<>();
        for (CurvePoint point : sorted) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(point.getTimestamp());
            int year = calendar.get(Calendar.YEAR);
            PeriodBucket bucket = yearBuckets.get(year);
            if (bucket == null) {
                bucket = new PeriodBucket();
                yearBuckets.put(year, bucket);
            }
            if (bucket.startMs == 0L || point.getTimestamp() < bucket.startMs) {
                bucket.startMs = point.getTimestamp();
            }
            if (point.getTimestamp() >= bucket.endMs) {
                bucket.endMs = point.getTimestamp();
                bucket.closeEquity = point.getBalance();
            }
        }
        if (yearBuckets.isEmpty()) {
            return;
        }
        double previousClose = Math.max(1e-9, sorted.get(0).getBalance());
        for (Map.Entry<Integer, PeriodBucket> entry : yearBuckets.entrySet()) {
            PeriodBucket bucket = entry.getValue();
            double yearAmount = bucket.closeEquity - previousClose;
            double yearReturn = safeDivide(yearAmount, previousClose);
            previousClose = bucket.closeEquity;
            int color = host.resolveReturnDisplayColor(yearReturn, yearAmount, com.binance.monitor.R.color.text_secondary);
            String valueText = host.formatReturnValue(yearReturn, yearAmount, false);
            long startMs = bucket.startMs;
            long endMs = bucket.endMs;
            table.addView(host.createAlignedReturnsRow(
                    entry.getKey() + "年",
                    valueText,
                    false,
                    color,
                    yearReturn,
                    v -> host.applyCurveRangeFromTableSelection(startMs, endMs)
            ));
        }
    }

    private void renderStageReturnsTable(@NonNull RenderRequest request, long referenceTime) {
        TableLayout table = host.getBinding().tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        String valueHeader = host.formatReturnValue(0d, 0d, false).contains("%") ? "收益率" : "收益额";
        table.addView(host.createAlignedReturnsRow("阶段", valueHeader, true, null, null));

        long endMs = Math.min(request.sourceCurvePoints.get(request.sourceCurvePoints.size() - 1).getTimestamp(), host.endOfDay(referenceTime));
        long allStart = request.sourceCurvePoints.get(0).getTimestamp();
        for (StageRange stage : buildStageRanges(request, allStart, endMs)) {
            List<CurvePoint> range = host.filterCurveByManualRange(request.sourceCurvePoints, stage.startMs, stage.endMs);
            if (range.size() < 2) {
                table.addView(host.createAlignedReturnsRow(stage.label, "--", false, null, null));
                continue;
            }
            double startEquity = range.get(0).getBalance();
            double endEquity = range.get(range.size() - 1).getBalance();
            double profit = endEquity - startEquity;
            double rate = safeDivide(profit, startEquity);
            int color = host.resolveReturnDisplayColor(rate, profit, com.binance.monitor.R.color.text_secondary);
            String valueText = host.formatReturnValue(rate, profit, false);
            table.addView(host.createAlignedReturnsRow(
                    stage.label,
                    valueText,
                    false,
                    color,
                    rate,
                    v -> host.applyCurveRangeFromTableSelection(stage.startMs, stage.endMs)
            ));
        }
    }

    private void rebuildDailyTableV3(int year,
                                     int month,
                                     @NonNull Map<Integer, DayBucket> dayBuckets,
                                     @NonNull Map<Integer, Double> closeByDay,
                                     @NonNull List<Integer> dayOrder) {
        ContentAccountStatsBinding binding = host.getBinding();
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setShrinkAllColumns(true);
        table.setStretchAllColumns(true);
        table.addView(host.createSimpleHeaderRow(new String[]{"一", "二", "三", "四", "五", "六", "日"}, 0));

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(year, month, 1, 0, 0, 0);
        firstDay.set(Calendar.MILLISECOND, 0);
        int firstWeek = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar prevMonth = Calendar.getInstance();
        prevMonth.set(year, month, 1, 0, 0, 0);
        prevMonth.add(Calendar.MONTH, -1);
        int prevDaysInMonth = prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
        int nextDayValue = 1;

        int totalCells = firstWeek + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7d);
        int day = 1;
        for (int row = 0; row < rows; row++) {
            TableRow tableRow = new TableRow(binding.getRoot().getContext());
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || day > daysInMonth) {
                    String ghostLabel = index < firstWeek
                            ? String.valueOf(prevDaysInMonth - (firstWeek - index) + 1)
                            : String.valueOf(nextDayValue++);
                    View emptyCell = host.createDailyReturnsCell(
                            ghostLabel,
                            null,
                            androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), com.binance.monitor.R.color.text_secondary),
                            null,
                            null,
                            null
                    );
                    host.applyReturnsCellLayout(emptyCell, 0, 1f, 42, 1, 1, 1, 1);
                    tableRow.addView(emptyCell);
                    continue;
                }
                DayBucket bucket = dayBuckets.get(day);
                if (bucket == null) {
                    View dayCell = host.createDailyReturnsCell(
                            String.valueOf(day),
                            host.formatReturnValue(0d, 0d, true),
                            androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), com.binance.monitor.R.color.text_primary),
                            host.resolveReturnDisplayColor(0d, 0d, com.binance.monitor.R.color.text_secondary),
                            null,
                            null
                    );
                    host.applyReturnsCellLayout(dayCell, 0, 1f, 42, 1, 1, 1, 1);
                    tableRow.addView(dayCell);
                } else {
                    int key = year * 10_000 + (month + 1) * 100 + day;
                    double prevClose = bucket.closeEquity;
                    int indexOfDay = dayOrder.indexOf(key);
                    if (indexOfDay > 0) {
                        Integer prevKey = dayOrder.get(indexOfDay - 1);
                        Double prev = closeByDay.get(prevKey);
                        if (prev != null && prev > 0d) {
                            prevClose = prev;
                        }
                    }
                    double dayAmount = bucket.closeEquity - prevClose;
                    double dayReturn = safeDivide(dayAmount, prevClose);
                    int color = host.resolveReturnDisplayColor(dayReturn, dayAmount, com.binance.monitor.R.color.text_secondary);
                    String valueText = host.formatReturnValue(dayReturn, dayAmount, true);
                    long startMs = bucket.startMs;
                    long endMs = bucket.endMs;
                    View dayCell = host.createDailyReturnsCell(
                            String.valueOf(day),
                            valueText,
                            androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), com.binance.monitor.R.color.text_primary),
                            color,
                            v -> host.applyCurveRangeFromTableSelection(startMs, endMs),
                            dayReturn
                    );
                    host.applyReturnsCellLayout(dayCell, 0, 1f, 42, 1, 1, 1, 1);
                    tableRow.addView(dayCell);
                }
                day++;
            }
            table.addView(tableRow);
        }
    }

    private void rebuildMonthlyTableThreeRowsV4(@NonNull List<YearlyReturnRow> rows) {
        TableLayout table = host.getBinding().tableMonthlyReturns;
        table.removeAllViews();
        table.setShrinkAllColumns(false);
        table.setStretchAllColumns(false);
        for (YearlyReturnRow row : rows) {
            table.addView(host.createMonthlyGroupedBlock(row));
        }
    }

    private List<StageRange> buildStageRanges(@NonNull RenderRequest request, long allStart, long endMs) {
        List<StageRange> stageRanges = new ArrayList<>();
        stageRanges.add(new StageRange("近1日", endMs - 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近7日", endMs - 7L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近30日", endMs - 30L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近3月", endMs - 90L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近1年", endMs - 365L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("今年以来", host.startOfYear(endMs), endMs));
        stageRanges.add(new StageRange("全部", allStart, endMs));
        if (request.manualCurveRangeEnabled) {
            stageRanges.add(new StageRange("自定义区间", request.manualCurveRangeStartMs, request.manualCurveRangeEndMs));
        }
        return stageRanges;
    }

    private long resolveTradeRangeStart(@NonNull List<TradeRecordItem> trades) {
        long start = Long.MAX_VALUE;
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            long closeTime = host.resolveCloseTime(trade);
            if (closeTime > 0L) {
                start = Math.min(start, closeTime);
            }
        }
        return start == Long.MAX_VALUE ? 0L : start;
    }

    @Nullable
    private MonthReturnInfo buildTradeReturnInfo(@NonNull List<TradeRecordItem> trades,
                                                 @NonNull List<CurvePoint> allCurvePoints,
                                                 long startMs,
                                                 long endMs) {
        MonthReturnInfo info = new MonthReturnInfo();
        info.startMs = startMs;
        info.endMs = endMs;
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            long closeTime = host.resolveCloseTime(trade);
            if (closeTime < startMs || closeTime > endMs) {
                continue;
            }
            info.hasData = true;
            info.returnAmount += trade.getProfit() + trade.getStorageFee();
        }
        if (!info.hasData) {
            return null;
        }
        info.returnRate = AccountPeriodReturnHelper.resolvePeriodReturnRate(
                allCurvePoints,
                info.startMs,
                info.returnAmount
        );
        return info;
    }

    @NonNull
    private List<YearlyReturnRow> buildMonthlyReturnRowsFromTrades(@NonNull List<TradeRecordItem> trades,
                                                                   @NonNull List<CurvePoint> allCurvePoints) {
        List<YearlyReturnRow> rows = new ArrayList<>();
        Map<Integer, MonthReturnInfo> monthReturnMap = new TreeMap<>();
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            long closeTime = host.resolveCloseTime(trade);
            if (closeTime <= 0L) {
                continue;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(closeTime);
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            int key = year * 100 + month;
            MonthReturnInfo info = monthReturnMap.get(key);
            if (info == null) {
                info = new MonthReturnInfo();
                info.startMs = host.startOfMonth(closeTime);
                info.endMs = host.endOfMonth(closeTime);
                info.hasData = true;
                monthReturnMap.put(key, info);
            }
            info.returnAmount += trade.getProfit() + trade.getStorageFee();
        }
        if (monthReturnMap.isEmpty()) {
            return rows;
        }
        for (MonthReturnInfo info : monthReturnMap.values()) {
            info.returnRate = AccountPeriodReturnHelper.resolvePeriodReturnRate(
                    allCurvePoints,
                    info.startMs,
                    info.returnAmount
            );
        }
        LinkedHashSet<Integer> years = new LinkedHashSet<>();
        for (Integer key : monthReturnMap.keySet()) {
            years.add(key / 100);
        }
        for (Integer year : years) {
            YearlyReturnRow yearly = new YearlyReturnRow(year);
            for (int month = 1; month <= 12; month++) {
                int key = year * 100 + month;
                MonthReturnInfo info = monthReturnMap.get(key);
                yearly.monthly.put(month, info);
                if (info != null && info.hasData) {
                    yearly.yearReturnAmount += info.returnAmount;
                    if (yearly.startMs == 0L || info.startMs < yearly.startMs) {
                        yearly.startMs = info.startMs;
                    }
                    if (info.endMs > yearly.endMs) {
                        yearly.endMs = info.endMs;
                    }
                }
            }
            yearly.yearReturnRate = AccountPeriodReturnHelper.resolvePeriodReturnRate(
                    allCurvePoints,
                    yearly.startMs,
                    yearly.yearReturnAmount
            );
            rows.add(yearly);
        }
        return rows;
    }

    @NonNull
    private List<YearlyReturnRow> buildMonthlyReturnRows(@NonNull List<CurvePoint> source) {
        List<YearlyReturnRow> rows = new ArrayList<>();
        if (source.size() < 2) {
            return rows;
        }
        List<CurvePoint> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));

        Map<Integer, PeriodBucket> monthBuckets = new TreeMap<>();
        for (CurvePoint point : sorted) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(point.getTimestamp());
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            int key = year * 100 + month;
            PeriodBucket bucket = monthBuckets.get(key);
            if (bucket == null) {
                bucket = new PeriodBucket();
                monthBuckets.put(key, bucket);
            }
            if (bucket.startMs == 0L || point.getTimestamp() < bucket.startMs) {
                bucket.startMs = point.getTimestamp();
            }
            if (point.getTimestamp() >= bucket.endMs) {
                bucket.endMs = point.getTimestamp();
                bucket.closeEquity = point.getBalance();
            }
        }
        if (monthBuckets.isEmpty()) {
            return rows;
        }

        Map<Integer, MonthReturnInfo> monthReturnMap = new LinkedHashMap<>();
        double previousClose = Math.max(1e-9, sorted.get(0).getBalance());
        for (Map.Entry<Integer, PeriodBucket> entry : monthBuckets.entrySet()) {
            PeriodBucket bucket = entry.getValue();
            MonthReturnInfo info = new MonthReturnInfo();
            info.startMs = bucket.startMs;
            info.endMs = bucket.endMs;
            info.hasData = true;
            info.startEquity = previousClose;
            info.endEquity = bucket.closeEquity;
            info.returnAmount = bucket.closeEquity - previousClose;
            info.returnRate = safeDivide(info.returnAmount, previousClose);
            previousClose = bucket.closeEquity;
            monthReturnMap.put(entry.getKey(), info);
        }

        LinkedHashSet<Integer> years = new LinkedHashSet<>();
        for (Integer key : monthReturnMap.keySet()) {
            years.add(key / 100);
        }
        for (Integer year : years) {
            YearlyReturnRow yearly = new YearlyReturnRow(year);
            double yearStart = 0d;
            double yearEnd = 0d;
            for (int month = 1; month <= 12; month++) {
                int key = year * 100 + month;
                MonthReturnInfo info = monthReturnMap.get(key);
                yearly.monthly.put(month, info);
                if (info != null && info.hasData) {
                    yearly.yearReturnAmount += info.returnAmount;
                    if (yearStart <= 0d) {
                        yearStart = info.startEquity;
                    }
                    yearEnd = info.endEquity;
                    if (yearly.startMs == 0L || info.startMs < yearly.startMs) {
                        yearly.startMs = info.startMs;
                    }
                    if (info.endMs > yearly.endMs) {
                        yearly.endMs = info.endMs;
                    }
                }
            }
            yearly.yearReturnRate = yearStart <= 0d ? 0d : safeDivide(yearEnd - yearStart, yearStart);
            rows.add(yearly);
        }
        return rows;
    }

    private static final class DayBucket {
        private long startMs;
        private long endMs;
        private double closeEquity;
    }

    static final class MonthReturnInfo {
        long startMs;
        long endMs;
        double startEquity;
        double endEquity;
        double returnAmount;
        double returnRate;
        boolean hasData;
    }

    static final class YearlyReturnRow {
        final int year;
        final Map<Integer, MonthReturnInfo> monthly = new LinkedHashMap<>();
        long startMs;
        long endMs;
        double yearReturnAmount;
        double yearReturnRate;

        YearlyReturnRow(int year) {
            this.year = year;
        }
    }

    private static final class PeriodBucket {
        private long startMs;
        private long endMs;
        private double closeEquity;
    }

    private static final class StageRange {
        private final String label;
        private final long startMs;
        private final long endMs;

        private StageRange(@NonNull String label, long startMs, long endMs) {
            this.label = label;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }
}
