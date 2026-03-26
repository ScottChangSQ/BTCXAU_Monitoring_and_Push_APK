package com.binance.monitor.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.databinding.ActivityAccountStatsBinding;
import com.binance.monitor.ui.account.adapter.AccountMetricAdapter;
import com.binance.monitor.ui.account.adapter.PendingOrderAdapter;
import com.binance.monitor.ui.account.adapter.PositionAggregateAdapter;
import com.binance.monitor.ui.account.adapter.PositionAdapterV2;
import com.binance.monitor.ui.account.adapter.StatsMetricAdapter;
import com.binance.monitor.ui.account.adapter.TradeRecordAdapterV2;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.util.FormatUtils;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountStatsBridgeActivity extends AppCompatActivity {
    private static final float SWIPE_THRESHOLD = 120f;
    private static final float SWIPE_VELOCITY_THRESHOLD = 120f;

    private static final String ACCOUNT = "7400048";
    private static final String PASSWORD = "_fWsAeW1";
    private static final String SERVER = "ICMarketsSC-MT5-6";

    private static final String FILTER_PRODUCT = "产品";
    private static final String FILTER_SIDE = "方向";
    private static final String FILTER_DATE = "日期";
    private static final String FILTER_LAST_1D = "近1日";
    private static final String FILTER_LAST_7D = "近7日";
    private static final String FILTER_LAST_30D = "近30日";

    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private ActivityAccountStatsBinding binding;
    private AccountStatsFallbackDataSource fallbackDataSource;
    private Mt5BridgeGatewayClient gatewayClient;
    private AccountMetricAdapter overviewAdapter;
    private StatsMetricAdapter indicatorAdapter;
    private PositionAggregateAdapter positionAggregateAdapter;
    private PositionAdapterV2 positionAdapter;
    private PendingOrderAdapter pendingOrderAdapter;
    private TradeRecordAdapterV2 tradeAdapter;
    private StatsMetricAdapter statsAdapter;
    private GestureDetector gestureDetector;
    private ExecutorService ioExecutor;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private volatile boolean loading;
    private AccountTimeRange selectedRange = AccountTimeRange.D7;

    private List<PositionItem> basePositions = new ArrayList<>();
    private List<TradeRecordItem> baseTrades = new ArrayList<>();
    private List<CurvePoint> allCurvePoints = new ArrayList<>();
    private List<CurvePoint> displayedCurvePoints = new ArrayList<>();
    private String defaultCurveMeta = "--";

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            requestSnapshot();
            refreshHandler.postDelayed(this, AppConstants.ACCOUNT_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountStatsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fallbackDataSource = new AccountStatsFallbackDataSource();
        gatewayClient = new Mt5BridgeGatewayClient();
        ioExecutor = Executors.newSingleThreadExecutor();

        overviewAdapter = new AccountMetricAdapter();
        indicatorAdapter = new StatsMetricAdapter();
        positionAggregateAdapter = new PositionAggregateAdapter();
        positionAdapter = new PositionAdapterV2();
        pendingOrderAdapter = new PendingOrderAdapter();
        tradeAdapter = new TradeRecordAdapterV2();
        statsAdapter = new StatsMetricAdapter();

        gestureDetector = new GestureDetector(this, new SwipeListener());

        setupBottomNav();
        setupRecyclers();
        setupFilters();
        setupRangeToggle();
        setupDatePickers();
        setupCurveInteraction();
        bindLocalMeta();
        requestSnapshot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, AppConstants.ACCOUNT_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable);
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View focus = getCurrentFocus();
            if (focus instanceof EditText) {
                int[] location = new int[2];
                focus.getLocationOnScreen(location);
                float x = event.getRawX();
                float y = event.getRawY();
                boolean outside = x < location[0] || x > location[0] + focus.getWidth()
                        || y < location[1] || y > location[1] + focus.getHeight();
                if (outside) {
                    focus.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void setupBottomNav() {
        updateBottomTabs(false);
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabAccountStats.setOnClickListener(v -> updateBottomTabs(false));
    }

    private void updateBottomTabs(boolean marketSelected) {
        binding.tabMarketMonitor.setBackground(marketSelected
                ? AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected)
                : AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
        binding.tabMarketMonitor.setTextColor(ContextCompat.getColor(this,
                marketSelected ? R.color.bg_primary : R.color.text_secondary));

        binding.tabAccountStats.setBackground(marketSelected
                ? AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected)
                : AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected));
        binding.tabAccountStats.setTextColor(ContextCompat.getColor(this,
                marketSelected ? R.color.text_secondary : R.color.bg_primary));
    }

    private void setupRecyclers() {
        binding.recyclerOverview.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerOverview.setAdapter(overviewAdapter);

        binding.recyclerCurveIndicators.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerCurveIndicators.setAdapter(indicatorAdapter);

        binding.recyclerPositionByProduct.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositionByProduct.setAdapter(positionAggregateAdapter);

        binding.recyclerPositions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositions.setAdapter(positionAdapter);

        binding.recyclerPendingOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPendingOrders.setAdapter(pendingOrderAdapter);

        binding.recyclerTrades.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTrades.setAdapter(tradeAdapter);

        binding.recyclerStats.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerStats.setAdapter(statsAdapter);
    }

    private void setupFilters() {
        ArrayAdapter<String> positionSortAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"按产品", "按市值", "按盈亏", "按收益率", "按手数"});
        positionSortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerPositionSort.setAdapter(positionSortAdapter);
        binding.spinnerPositionSort.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshPositions));

        ArrayAdapter<String> productAdapter = createTradeFilterAdapter(
                new String[]{FILTER_PRODUCT, "XAUUSD", "BTCUSD", "NAS100", "WTI", "EURUSD", "GBPUSD"});
        binding.spinnerTradeProduct.setAdapter(productAdapter);
        binding.spinnerTradeProduct.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        ArrayAdapter<String> sideAdapter = createTradeFilterAdapter(new String[]{FILTER_SIDE, "买入", "卖出"});
        binding.spinnerTradeSide.setAdapter(sideAdapter);
        binding.spinnerTradeSide.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        ArrayAdapter<String> timeAdapter = createTradeFilterAdapter(
                new String[]{FILTER_DATE, FILTER_LAST_1D, FILTER_LAST_7D, FILTER_LAST_30D});
        binding.spinnerTradeTime.setAdapter(timeAdapter);
        binding.spinnerTradeTime.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        binding.btnApplyManualRange.setOnClickListener(v -> applyManualCurveRange());
    }

    private ArrayAdapter<String> createTradeFilterAdapter(String[] options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.item_spinner_filter, options);
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    private void setupDatePickers() {
        binding.etRangeStart.setOnClickListener(v -> showDatePicker(binding.etRangeStart));
        binding.etRangeEnd.setOnClickListener(v -> showDatePicker(binding.etRangeEnd));
    }

    private void setupCurveInteraction() {
        binding.equityCurveView.setOnPointHighlightListener(point -> {
            if (point == null) {
                binding.tvCurveMeta.setText(defaultCurveMeta);
                return;
            }
            String detail = String.format(Locale.getDefault(),
                    "时间 %s | 净值 $%s | 结余 $%s | 差值 %s",
                    FormatUtils.formatDateTime(point.getTimestamp()),
                    FormatUtils.formatPrice(point.getEquity()),
                    FormatUtils.formatPrice(point.getBalance()),
                    signedMoney(point.getEquity() - point.getBalance()));
            binding.tvCurveMeta.setText(detail);
        });
    }

    private void showDatePicker(EditText target) {
        long selection = MaterialDatePicker.todayInUtcMilliseconds();
        String text = trim(target.getText() == null ? "" : target.getText().toString());
        if (!text.isEmpty()) {
            try {
                Date parsed = dateOnlyFormat.parse(text);
                if (parsed != null) {
                    Calendar local = Calendar.getInstance();
                    local.setTime(parsed);
                    Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    utc.clear();
                    utc.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
                    selection = utc.getTimeInMillis();
                }
            } catch (Exception ignored) {
            }
        }

        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("选择日期")
                .setSelection(selection)
                .build();
        picker.addOnPositiveButtonClickListener(value -> {
            if (value == null) {
                return;
            }
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc.setTimeInMillis(value);
            Calendar local = Calendar.getInstance();
            local.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
            local.set(Calendar.MILLISECOND, 0);
            target.setText(dateOnlyFormat.format(local.getTime()));
        });
        picker.show(getSupportFragmentManager(), "date_picker_" + target.getId());
    }

    private void setupRangeToggle() {
        binding.toggleTimeRange.check(R.id.btnRange7d);
        binding.toggleTimeRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnRange1d) {
                selectedRange = AccountTimeRange.D1;
            } else if (checkedId == R.id.btnRange7d) {
                selectedRange = AccountTimeRange.D7;
            } else if (checkedId == R.id.btnRange1m) {
                selectedRange = AccountTimeRange.M1;
            } else if (checkedId == R.id.btnRange3m) {
                selectedRange = AccountTimeRange.M3;
            } else if (checkedId == R.id.btnRange1y) {
                selectedRange = AccountTimeRange.Y1;
            } else {
                selectedRange = AccountTimeRange.ALL;
            }
            applyPresetCurveRangeFromAllPoints();
            requestSnapshot();
        });
    }

    private void bindLocalMeta() {
        String maskedPassword = PASSWORD.substring(0, 2) + "******" + PASSWORD.substring(PASSWORD.length() - 1);
        binding.tvAccountMeta.setText(String.format(Locale.getDefault(),
                "账号 %s | 只读密码 %s | 服务器 %s | 数据源 历史数据（网关离线）",
                ACCOUNT, maskedPassword, SERVER));
    }

    private void requestSnapshot() {
        if (loading) {
            return;
        }
        loading = true;
        AccountTimeRange fetchRange = AccountTimeRange.ALL;

        ioExecutor.execute(() -> {
            Mt5BridgeGatewayClient.SnapshotResult remote = gatewayClient.fetch(fetchRange);
            AccountSnapshot snapshot;
            String meta;
            if (remote.isSuccess()) {
                snapshot = remote.getSnapshot();
                meta = remote.buildMetaLine(ACCOUNT, SERVER);
                runOnUiThread(() -> setConnectionStatus(true));
            } else {
                snapshot = fallbackDataSource.load(fetchRange);
                String update = FormatUtils.formatTime(System.currentTimeMillis());
                meta = "账号 " + ACCOUNT + " | 服务器 " + SERVER
                        + " | 数据源 历史数据（网关离线） | 更新时间 " + update
                        + " | 原因 " + remote.getError();
                runOnUiThread(() -> setConnectionStatus(false));
            }

            runOnUiThread(() -> {
                applySnapshot(snapshot);
                binding.tvAccountMeta.setText(meta);
                loading = false;
            });
        });
    }

    private void setConnectionStatus(boolean connected) {
        binding.tvAccountConnectionStatus.setText(connected ? "已连接账户" : "未连接账户");
        binding.tvAccountConnectionStatus.setBackground(connected
                ? AppCompatResources.getDrawable(this, R.drawable.bg_chip_selected)
                : AppCompatResources.getDrawable(this, R.drawable.bg_chip_unselected));
        binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(this,
                connected ? R.color.bg_primary : R.color.text_secondary));
    }

    private void applySnapshot(AccountSnapshot snapshot) {
        basePositions = new ArrayList<>(snapshot.getPositions());
        baseTrades = new ArrayList<>(snapshot.getTrades());
        allCurvePoints = new ArrayList<>(snapshot.getCurvePoints());
        allCurvePoints.sort(Comparator.comparingLong(CurvePoint::getTimestamp));

        List<AccountMetric> overview = snapshot.getOverviewMetrics();
        if (overview == null || overview.isEmpty()) {
            overview = buildOverviewFallbackMetrics();
        }
        List<AccountMetric> stats = snapshot.getStatsMetrics();
        if (stats == null || stats.isEmpty()) {
            stats = buildStatsFallbackMetrics();
        }

        overviewAdapter.submitList(overview);
        statsAdapter.submitList(stats);
        applyPresetCurveRangeFromAllPoints();
        refreshPositions();
        refreshTrades();
    }

    private void applyPresetCurveRangeFromAllPoints() {
        displayedCurvePoints = filterCurveByRange(allCurvePoints, selectedRange);
        renderCurveWithIndicators(displayedCurvePoints);
    }

    private List<CurvePoint> filterCurveByRange(List<CurvePoint> source, AccountTimeRange range) {
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
        if (end <= 0L) {
            end = System.currentTimeMillis();
        }
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

        int size = source.size();
        if (size >= 2) {
            return new ArrayList<>(source.subList(size - 2, size));
        }
        return new ArrayList<>(source);
    }

    private void renderCurveWithIndicators(List<CurvePoint> points) {
        binding.equityCurveView.setPoints(points);
        defaultCurveMeta = buildCurveMeta(points);
        binding.tvCurveMeta.setText(defaultCurveMeta);
        indicatorAdapter.submitList(buildCurveIndicators(points));
    }

    private String buildCurveMeta(List<CurvePoint> points) {
        if (points == null || points.isEmpty()) {
            return "--";
        }
        double start = points.get(0).getEquity();
        double current = points.get(points.size() - 1).getEquity();
        double peak = start;
        double valley = start;
        int peakIndex = 0;
        int valleyIndex = 0;
        for (int i = 1; i < points.size(); i++) {
            double value = points.get(i).getEquity();
            if (value >= peak) {
                peak = value;
                peakIndex = i;
            }
            if (value <= valley) {
                valley = value;
                valleyIndex = i;
            }
        }
        double drawdown = peak == 0d ? 0d : (peak - valley) / peak;
        return String.format(Locale.getDefault(),
                "起点净值 $%s | 当前净值 $%s | 峰值 %s | 谷值 %s | 最大回撤 %.2f%% | 收益率 %+.2f%%",
                FormatUtils.formatPrice(start),
                FormatUtils.formatPrice(current),
                FormatUtils.formatTime(points.get(peakIndex).getTimestamp()),
                FormatUtils.formatTime(points.get(valleyIndex).getTimestamp()),
                drawdown * 100d,
                (current - start) * 100d / Math.max(1d, start));
    }

    private List<AccountMetric> buildCurveIndicators(List<CurvePoint> points) {
        List<AccountMetric> result = new ArrayList<>();
        if (points == null || points.size() < 2) {
            result.add(new AccountMetric("近1日收益", "--"));
            result.add(new AccountMetric("近7日收益", "--"));
            result.add(new AccountMetric("近30日收益", "--"));
            result.add(new AccountMetric("最大回撤", "--"));
            result.add(new AccountMetric("波动率", "--"));
            result.add(new AccountMetric("Sharpe Ratio", "--"));
            return result;
        }

        double[] values = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            values[i] = points.get(i).getEquity();
        }

        double peak = values[0];
        double maxDd = 0d;
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < values.length; i++) {
            peak = Math.max(peak, values[i]);
            maxDd = Math.max(maxDd, safeDivide(peak - values[i], peak));
            returns.add(safeDivide(values[i] - values[i - 1], values[i - 1]));
        }

        double r1 = returnN(values, 24);
        double r7 = returnN(values, 24 * 7);
        double r30 = returnN(values, 24 * 30);
        double vol = calcStd(returns) * Math.sqrt(365d);
        double mean = returns.stream().mapToDouble(v -> v).average().orElse(0d);
        double sharpe = vol == 0d ? 0d : (mean * 365d) / vol;

        result.add(new AccountMetric("近1日收益", String.format(Locale.getDefault(), "%+.2f%%", r1 * 100d)));
        result.add(new AccountMetric("近7日收益", String.format(Locale.getDefault(), "%+.2f%%", r7 * 100d)));
        result.add(new AccountMetric("近30日收益", String.format(Locale.getDefault(), "%+.2f%%", r30 * 100d)));
        result.add(new AccountMetric("最大回撤", String.format(Locale.getDefault(), "%.2f%%", maxDd * 100d)));
        result.add(new AccountMetric("波动率", String.format(Locale.getDefault(), "%.2f%%", vol * 100d)));
        result.add(new AccountMetric("Sharpe Ratio", String.format(Locale.getDefault(), "%.2f", sharpe)));
        return result;
    }

    private List<AccountMetric> buildOverviewFallbackMetrics() {
        List<AccountMetric> result = new ArrayList<>();
        double equity = displayedCurvePoints.isEmpty() ? 0d : displayedCurvePoints.get(displayedCurvePoints.size() - 1).getEquity();
        double balance = displayedCurvePoints.isEmpty() ? 0d : displayedCurvePoints.get(displayedCurvePoints.size() - 1).getBalance();
        double marketValue = 0d;
        double totalPnl = 0d;
        for (PositionItem item : basePositions) {
            marketValue += item.getMarketValue();
            totalPnl += item.getTotalPnL();
        }
        double margin = equity * 0.3d;
        double free = Math.max(0d, equity - margin);
        double dayReturn = equity == 0d ? 0d : (equity - balance) / equity;
        double totalReturn = balance == 0d ? 0d : totalPnl / balance;

        result.add(new AccountMetric("总资产", "$" + FormatUtils.formatPrice(equity)));
        result.add(new AccountMetric("保证金金额", "$" + FormatUtils.formatPrice(margin)));
        result.add(new AccountMetric("可用资金", "$" + FormatUtils.formatPrice(free)));
        result.add(new AccountMetric("持仓市值", "$" + FormatUtils.formatPrice(marketValue)));
        result.add(new AccountMetric("持仓盈亏", signedMoney(totalPnl)));
        result.add(new AccountMetric("当前净值", "$" + FormatUtils.formatPrice(equity)));
        result.add(new AccountMetric("当日收益率", String.format(Locale.getDefault(), "%+.2f%%", dayReturn * 100d)));
        result.add(new AccountMetric("累计收益率", String.format(Locale.getDefault(), "%+.2f%%", totalReturn * 100d)));
        return result;
    }

    private List<AccountMetric> buildStatsFallbackMetrics() {
        List<AccountMetric> result = new ArrayList<>();
        double totalPnl = 0d;
        double maxPos = 0d;
        for (PositionItem item : basePositions) {
            totalPnl += item.getTotalPnL();
            maxPos = Math.max(maxPos, item.getPositionRatio());
        }

        int buy = 0;
        int sell = 0;
        for (TradeRecordItem item : baseTrades) {
            if ("BUY".equalsIgnoreCase(item.getSide())) {
                buy++;
            } else {
                sell++;
            }
        }

        double maxDd = 0d;
        if (displayedCurvePoints.size() > 1) {
            double peak = displayedCurvePoints.get(0).getEquity();
            for (CurvePoint p : displayedCurvePoints) {
                peak = Math.max(peak, p.getEquity());
                maxDd = Math.max(maxDd, safeDivide(peak - p.getEquity(), peak));
            }
        }

        result.add(new AccountMetric("累计收益额", signedMoney(totalPnl)));
        result.add(new AccountMetric("总交易次数", String.valueOf(baseTrades.size())));
        result.add(new AccountMetric("买入次数", String.valueOf(buy)));
        result.add(new AccountMetric("卖出次数", String.valueOf(sell)));
        result.add(new AccountMetric("最大回撤", String.format(Locale.getDefault(), "%.2f%%", maxDd * 100d)));
        result.add(new AccountMetric("单一持仓最大占比", String.format(Locale.getDefault(), "%.2f%%", maxPos * 100d)));
        result.add(new AccountMetric("连续盈利/连续亏损", "5 / 3"));
        result.add(new AccountMetric("数据来源", "网关离线时本地估算"));
        return result;
    }

    private void refreshPositions() {
        List<PositionItem> list = new ArrayList<>(basePositions);
        int index = binding.spinnerPositionSort.getSelectedItemPosition();
        if (index == 1) {
            list.sort((a, b) -> Double.compare(b.getMarketValue(), a.getMarketValue()));
        } else if (index == 2) {
            list.sort((a, b) -> Double.compare(b.getTotalPnL(), a.getTotalPnL()));
        } else if (index == 3) {
            list.sort((a, b) -> Double.compare(b.getReturnRate(), a.getReturnRate()));
        } else if (index == 4) {
            list.sort((a, b) -> Double.compare(b.getQuantity(), a.getQuantity()));
        } else {
            list.sort(Comparator.comparing(PositionItem::getProductName));
        }

        positionAggregateAdapter.submitList(buildPositionAggregates(list));
        positionAdapter.submitList(list);
        List<PositionItem> pendingOrders = buildPendingOrders(list);
        pendingOrderAdapter.submitList(pendingOrders);
        int pendingVisibility = pendingOrders.isEmpty() ? View.GONE : View.VISIBLE;
        binding.tvPendingOrdersTitle.setVisibility(pendingVisibility);
        binding.recyclerPendingOrders.setVisibility(pendingVisibility);
        double totalPnl = 0d;
        double totalValue = 0d;
        for (PositionItem item : list) {
            totalPnl += item.getTotalPnL();
            totalValue += item.getMarketValue();
        }
        double ratio = totalValue == 0d ? 0d : totalPnl / totalValue;
        binding.tvPositionPnlSummary.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        binding.tvPositionPnlSummary.setText(buildPositionPnlSummary(totalPnl, ratio));
    }

    private List<PositionAggregateAdapter.AggregateItem> buildPositionAggregates(List<PositionItem> list) {
        Map<String, PositionAggregate> grouped = new LinkedHashMap<>();
        for (PositionItem item : list) {
            String side = normalizeSideCn(item.getSide());
            String key = item.getCode() + "|" + side;
            PositionAggregate aggregate = grouped.get(key);
            if (aggregate == null) {
                aggregate = new PositionAggregate(item.getProductName(), side);
                grouped.put(key, aggregate);
            }
            double quantity = Math.max(0d, item.getQuantity());
            aggregate.totalQuantity += quantity;
            aggregate.weightedCostAmount += quantity * item.getCostPrice();
            aggregate.weightedQuantity += quantity;
            aggregate.totalPnl += item.getTotalPnL();
        }

        List<PositionAggregateAdapter.AggregateItem> result = new ArrayList<>();
        for (PositionAggregate aggregate : grouped.values()) {
            double avgCost = aggregate.weightedQuantity <= 0d
                    ? 0d
                    : aggregate.weightedCostAmount / aggregate.weightedQuantity;
            result.add(new PositionAggregateAdapter.AggregateItem(
                    aggregate.productName,
                    aggregate.side,
                    aggregate.totalQuantity,
                    avgCost,
                    aggregate.totalPnl
            ));
        }
        return result;
    }

    private CharSequence buildPositionPnlSummary(double totalPnl, double ratio) {
        String pnlText = signedMoney(totalPnl);
        String ratioText = String.format(Locale.getDefault(), "%+.2f%%", ratio * 100d);
        String summary = String.format(Locale.getDefault(),
                "持仓盈亏: %s | 持仓盈亏比例: %s",
                pnlText,
                ratioText);
        SpannableString spannable = new SpannableString(summary);

        int pnlColor = ContextCompat.getColor(this, totalPnl >= 0d ? R.color.accent_green : R.color.accent_red);
        int ratioColor = ContextCompat.getColor(this, ratio >= 0d ? R.color.accent_green : R.color.accent_red);

        int pnlStart = summary.indexOf(pnlText);
        if (pnlStart >= 0) {
            spannable.setSpan(
                    new ForegroundColorSpan(pnlColor),
                    pnlStart,
                    pnlStart + pnlText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        int ratioStart = summary.lastIndexOf(ratioText);
        if (ratioStart >= 0) {
            spannable.setSpan(
                    new ForegroundColorSpan(ratioColor),
                    ratioStart,
                    ratioStart + ratioText.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return spannable;
    }

    private List<PositionItem> buildPendingOrders(List<PositionItem> list) {
        List<PositionItem> pending = new ArrayList<>();
        for (PositionItem item : list) {
            if (item.getPendingCount() > 0 || item.getPendingLots() > 0d) {
                pending.add(item);
            }
        }
        pending.sort((a, b) -> Double.compare(b.getPendingLots(), a.getPendingLots()));
        return pending;
    }

    private void refreshTrades() {
        List<TradeRecordItem> filtered = new ArrayList<>();
        String product = (String) binding.spinnerTradeProduct.getSelectedItem();
        String side = (String) binding.spinnerTradeSide.getSelectedItem();
        String time = (String) binding.spinnerTradeTime.getSelectedItem();

        long now = System.currentTimeMillis();
        long limit;
        if (FILTER_LAST_1D.equals(time)) {
            limit = now - 24L * 60L * 60L * 1000L;
        } else if (FILTER_LAST_7D.equals(time)) {
            limit = now - 7L * 24L * 60L * 60L * 1000L;
        } else if (FILTER_LAST_30D.equals(time)) {
            limit = now - 30L * 24L * 60L * 60L * 1000L;
        } else {
            limit = 0L;
        }

        for (TradeRecordItem item : baseTrades) {
            if (limit > 0L && item.getTimestamp() < limit) {
                continue;
            }
            if (!FILTER_PRODUCT.equals(product) && !item.getCode().equalsIgnoreCase(product)) {
                continue;
            }
            if (!FILTER_SIDE.equals(side) && !item.getSide().equalsIgnoreCase(normalizeSide(side))) {
                continue;
            }
            filtered.add(item);
        }
        filtered.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        tradeAdapter.submitList(filtered);
    }

    private void applyManualCurveRange() {
        String startText = trim(binding.etRangeStart.getText() == null ? "" : binding.etRangeStart.getText().toString());
        String endText = trim(binding.etRangeEnd.getText() == null ? "" : binding.etRangeEnd.getText().toString());
        if (startText.isEmpty() || endText.isEmpty()) {
            displayedCurvePoints = new ArrayList<>(allCurvePoints);
            renderCurveWithIndicators(displayedCurvePoints);
            Toast.makeText(this, "已恢复当前时间维度完整区间", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Date startDate = dateOnlyFormat.parse(startText);
            Date endDate = dateOnlyFormat.parse(endText);
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException();
            }
            long start = startDate.getTime();
            long end = endDate.getTime() + 24L * 60L * 60L * 1000L - 1L;
            if (start > end) {
                long tmp = start;
                start = end;
                end = tmp;
            }

            List<CurvePoint> filtered = new ArrayList<>();
            for (CurvePoint p : allCurvePoints) {
                if (p.getTimestamp() >= start && p.getTimestamp() <= end) {
                    filtered.add(p);
                }
            }
            if (filtered.size() < 2) {
                Toast.makeText(this, "该区间数据不足，请调整日期", Toast.LENGTH_SHORT).show();
                return;
            }
            displayedCurvePoints = filtered;
            renderCurveWithIndicators(displayedCurvePoints);
        } catch (Exception e) {
            Toast.makeText(this, "日期格式应为 yyyy-MM-dd", Toast.LENGTH_SHORT).show();
        }
    }

    private String trim(String source) {
        return source == null ? "" : source.trim();
    }

    private double safeDivide(double a, double b) {
        return Math.abs(b) < 1e-9 ? 0d : a / b;
    }

    private double returnN(double[] values, int n) {
        if (values.length < 2) {
            return 0d;
        }
        int from = Math.max(0, values.length - 1 - n);
        return safeDivide(values[values.length - 1] - values[from], values[from]);
    }

    private double calcStd(List<Double> values) {
        if (values.isEmpty()) {
            return 0d;
        }
        double avg = values.stream().mapToDouble(v -> v).average().orElse(0d);
        double sum = 0d;
        for (double value : values) {
            double diff = value - avg;
            sum += diff * diff;
        }
        return Math.sqrt(sum / values.size());
    }

    private String signedMoney(double value) {
        return (value >= 0d ? "+" : "-") + "$" + FormatUtils.formatPrice(Math.abs(value));
    }

    private String normalizeSide(String side) {
        if ("买入".equals(side)) {
            return "Buy";
        }
        if ("卖出".equals(side)) {
            return "Sell";
        }
        return side;
    }

    private String normalizeSideCn(String side) {
        if ("buy".equalsIgnoreCase(side) || "买入".equals(side)) {
            return "买入";
        }
        if ("sell".equalsIgnoreCase(side) || "卖出".equals(side)) {
            return "卖出";
        }
        return side;
    }

    private static class PositionAggregate {
        private final String productName;
        private final String side;
        private double totalQuantity;
        private double weightedCostAmount;
        private double weightedQuantity;
        private double totalPnl;

        private PositionAggregate(String productName, String side) {
            this.productName = productName;
            this.side = side;
        }
    }

    private void openMarketMonitor() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }

    private class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) {
                return false;
            }
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();
            if (Math.abs(diffX) < Math.abs(diffY)) {
                return false;
            }
            if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0f) {
                    openMarketMonitor();
                }
                return true;
            }
            return false;
        }
    }
}
