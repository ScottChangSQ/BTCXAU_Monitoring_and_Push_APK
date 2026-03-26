package com.binance.monitor.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.databinding.ActivityAccountStatsBinding;
import com.binance.monitor.ui.account.adapter.AccountMetricAdapter;
import com.binance.monitor.ui.account.adapter.PositionAdapter;
import com.binance.monitor.ui.account.adapter.StatsMetricAdapter;
import com.binance.monitor.ui.account.adapter.TradeRecordAdapter;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountStatsLiveActivity extends AppCompatActivity {

    private static final float SWIPE_THRESHOLD = 120f;
    private static final float SWIPE_VELOCITY_THRESHOLD = 120f;
    private static final String ACCOUNT = "7400048";
    private static final String PASSWORD = "_fWsAeW1";
    private static final String SERVER = "ICMarketsSC-MT5-6";

    private ActivityAccountStatsBinding binding;
    private AccountStatsRepository fallbackRepository;
    private Mt5GatewayClient gatewayClient;
    private AccountMetricAdapter overviewAdapter;
    private StatsMetricAdapter indicatorAdapter;
    private PositionAdapter positionAdapter;
    private TradeRecordAdapter tradeAdapter;
    private StatsMetricAdapter statsAdapter;
    private GestureDetector gestureDetector;
    private ExecutorService ioExecutor;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private volatile boolean loading;

    private AccountStatsRepository.TimeRange selectedRange = AccountStatsRepository.TimeRange.D7;
    private List<PositionItem> basePositions = new ArrayList<>();
    private List<TradeRecordItem> baseTrades = new ArrayList<>();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            requestSnapshot(false);
            refreshHandler.postDelayed(this, AppConstants.ACCOUNT_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountStatsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fallbackRepository = new AccountStatsRepository();
        gatewayClient = new Mt5GatewayClient();
        ioExecutor = Executors.newSingleThreadExecutor();
        overviewAdapter = new AccountMetricAdapter();
        indicatorAdapter = new StatsMetricAdapter();
        positionAdapter = new PositionAdapter();
        tradeAdapter = new TradeRecordAdapter();
        statsAdapter = new StatsMetricAdapter();

        gestureDetector = new GestureDetector(this, new SwipeListener());
        setupBottomNav();
        setupRecyclers();
        setupFilters();
        setupRangeToggle();
        bindLocalMeta();
        requestSnapshot(true);
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
        return super.dispatchTouchEvent(event);
    }

    private void setupBottomNav() {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_account_stats);
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_market_monitor) {
                openMarketMonitor();
                return true;
            }
            return item.getItemId() == R.id.nav_account_stats;
        });
    }

    private void setupRecyclers() {
        binding.recyclerOverview.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerOverview.setAdapter(overviewAdapter);

        binding.recyclerCurveIndicators.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerCurveIndicators.setAdapter(indicatorAdapter);

        binding.recyclerPositions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositions.setAdapter(positionAdapter);

        binding.recyclerTrades.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTrades.setAdapter(tradeAdapter);

        binding.recyclerStats.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerStats.setAdapter(statsAdapter);
    }

    private void setupFilters() {
        ArrayAdapter<String> positionSortAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"按产品", "按市值", "按盈亏", "按收益率", "按持仓数量"});
        positionSortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerPositionSort.setAdapter(positionSortAdapter);
        binding.spinnerPositionSort.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshPositions));

        ArrayAdapter<String> productAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"全部产品", "黄金", "比特币", "纳指", "原油", "欧元", "英镑"});
        productAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTradeProduct.setAdapter(productAdapter);
        binding.spinnerTradeProduct.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        ArrayAdapter<String> sideAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"全部方向", "买入", "卖出"});
        sideAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTradeSide.setAdapter(sideAdapter);
        binding.spinnerTradeSide.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        ArrayAdapter<String> timeAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"全部时间", "近1日", "近7日", "近30日"});
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerTradeTime.setAdapter(timeAdapter);
        binding.spinnerTradeTime.setOnItemSelectedListener(new SimpleSelectionListener(this::refreshTrades));

        binding.etTradeSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshTrades();
            }
        });
    }

    private void setupRangeToggle() {
        binding.toggleTimeRange.check(R.id.btnRange7d);
        binding.toggleTimeRange.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnRange1d) {
                selectedRange = AccountStatsRepository.TimeRange.D1;
            } else if (checkedId == R.id.btnRange7d) {
                selectedRange = AccountStatsRepository.TimeRange.D7;
            } else if (checkedId == R.id.btnRange1m) {
                selectedRange = AccountStatsRepository.TimeRange.M1;
            } else if (checkedId == R.id.btnRange3m) {
                selectedRange = AccountStatsRepository.TimeRange.M3;
            } else if (checkedId == R.id.btnRange1y) {
                selectedRange = AccountStatsRepository.TimeRange.Y1;
            } else {
                selectedRange = AccountStatsRepository.TimeRange.ALL;
            }
            requestSnapshot(true);
        });
    }

    private void bindLocalMeta() {
        String maskedPassword = PASSWORD.substring(0, 2) + "******" + PASSWORD.substring(PASSWORD.length() - 1);
        binding.tvAccountMeta.setText(String.format(Locale.getDefault(),
                "账号 %s  |  只读密码 %s  |  服务器 %s  |  数据源 本地",
                ACCOUNT, maskedPassword, SERVER));
    }

    private void requestSnapshot(boolean force) {
        if (loading && !force) {
            return;
        }
        loading = true;
        ioExecutor.execute(() -> {
            Mt5GatewayClient.SnapshotResult remote = gatewayClient.fetch(selectedRange);
            AccountSnapshot snapshot;
            String meta;
            if (remote.isSuccess()) {
                snapshot = remote.getSnapshot();
                meta = remote.buildMetaLine(ACCOUNT, SERVER);
            } else {
                snapshot = fallbackRepository.load(selectedRange);
                String update = FormatUtils.formatTime(System.currentTimeMillis());
                meta = "账号 " + ACCOUNT + "  |  服务器 " + SERVER + "  |  数据源 模拟(网关离线)  |  更新 " + update;
            }
            runOnUiThread(() -> {
                applySnapshot(snapshot);
                binding.tvAccountMeta.setText(meta);
                loading = false;
            });
        });
    }

    private void applySnapshot(AccountSnapshot snapshot) {
        overviewAdapter.submitList(snapshot.getOverviewMetrics());
        indicatorAdapter.submitList(snapshot.getCurveIndicators());
        statsAdapter.submitList(snapshot.getStatsMetrics());
        basePositions = new ArrayList<>(snapshot.getPositions());
        baseTrades = new ArrayList<>(snapshot.getTrades());
        binding.equityCurveView.setPoints(snapshot.getCurvePoints());
        binding.tvCurveMeta.setText(buildCurveMeta(snapshot.getCurvePoints()));
        refreshPositions();
        refreshTrades();
    }

    private String buildCurveMeta(List<CurvePoint> points) {
        if (points == null || points.isEmpty()) {
            return "--";
        }
        double start = points.get(0).getEquity();
        double current = points.get(points.size() - 1).getEquity();
        int peakIndex = 0;
        int valleyIndex = 0;
        double peak = points.get(0).getEquity();
        double valley = points.get(0).getEquity();
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
                "起点净值 $%s | 当前净值 $%s | 峰值 %s | 谷值 %s | 最大回撤 %.2f%% | 收益率变化 %+.2f%%",
                FormatUtils.formatPrice(start),
                FormatUtils.formatPrice(current),
                FormatUtils.formatTime(points.get(peakIndex).getTimestamp()),
                FormatUtils.formatTime(points.get(valleyIndex).getTimestamp()),
                drawdown * 100d,
                (current - start) * 100d / Math.max(1d, start));
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
        positionAdapter.submitList(list);
        binding.tvPositionCostSummary.setText(buildPositionSummary(list));
    }

    private String buildPositionSummary(List<PositionItem> list) {
        StringBuilder builder = new StringBuilder();
        for (PositionItem item : list) {
            if (builder.length() > 0) {
                builder.append("  |  ");
            }
            builder.append(item.getProductName())
                    .append(" 成本 $")
                    .append(FormatUtils.formatPrice(item.getCostPrice()))
                    .append(" / 市值 $")
                    .append(FormatUtils.formatPrice(item.getMarketValue()));
        }
        return builder.toString();
    }

    private void refreshTrades() {
        List<TradeRecordItem> filtered = new ArrayList<>();
        String keyword = text(binding.etTradeSearch.getText() == null ? "" : binding.etTradeSearch.getText().toString());
        String product = (String) binding.spinnerTradeProduct.getSelectedItem();
        String side = (String) binding.spinnerTradeSide.getSelectedItem();
        String time = (String) binding.spinnerTradeTime.getSelectedItem();
        long now = System.currentTimeMillis();
        long limit;
        if ("近1日".equals(time)) {
            limit = now - 24L * 60L * 60L * 1000L;
        } else if ("近7日".equals(time)) {
            limit = now - 7L * 24L * 60L * 60L * 1000L;
        } else if ("近30日".equals(time)) {
            limit = now - 30L * 24L * 60L * 60L * 1000L;
        } else {
            limit = 0L;
        }
        for (TradeRecordItem item : baseTrades) {
            if (limit > 0L && item.getTimestamp() < limit) {
                continue;
            }
            if (!"全部产品".equals(product) && !item.getProductName().equals(product)) {
                continue;
            }
            if (!"全部方向".equals(side) && !item.getSide().equals(side)) {
                continue;
            }
            if (!keyword.isEmpty()) {
                String lower = keyword.toLowerCase(Locale.ROOT);
                String target = (item.getProductName() + item.getCode() + item.getRemark()).toLowerCase(Locale.ROOT);
                if (!target.contains(lower)) {
                    continue;
                }
            }
            filtered.add(item);
        }
        filtered.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        tradeAdapter.submitList(filtered);
    }

    private String text(String source) {
        return source == null ? "" : source.trim();
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
