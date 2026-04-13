/*
 * 账户持仓页，负责承接账户概览、当前持仓和挂单三段展示。
 * 页面只消费 AccountStatsPreloadManager.Cache 这一份账户真值，不再自建第二条实时链路。
 */
package com.binance.monitor.ui.account;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.databinding.ActivityAccountPositionBinding;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.service.MonitorServiceController;
import com.binance.monitor.ui.account.adapter.AccountMetricAdapter;
import com.binance.monitor.ui.account.adapter.PendingOrderAdapter;
import com.binance.monitor.ui.account.adapter.PositionAggregateAdapter;
import com.binance.monitor.ui.account.adapter.PositionAdapterV2;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.ProductSymbolMapper;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AccountPositionActivity extends AppCompatActivity {

    private ActivityAccountPositionBinding binding;
    private AccountMetricAdapter overviewAdapter;
    private PositionAggregateAdapter positionAggregateAdapter;
    private PositionAdapterV2 positionAdapter;
    private PendingOrderAdapter pendingOrderAdapter;
    private ExecutorService uiModelExecutor;
    private AccountStatsPreloadManager preloadManager;
    private SecureSessionPrefs secureSessionPrefs;
    private AccountPositionUiModelFactory uiModelFactory;
    private AccountSessionDialogController accountSessionDialogController;
    private AccountPositionUiModel currentUiModel = AccountPositionUiModel.empty();
    private AccountPositionUiModel lastStableUiModel = AccountPositionUiModel.empty();
    private String pendingConnectionStatusText = "";
    private final AccountStatsPreloadManager.CacheListener cacheListener = this::scheduleUiModelBuild;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAccountPositionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preloadManager = AccountStatsPreloadManager.getInstance(getApplicationContext());
        secureSessionPrefs = new SecureSessionPrefs(getApplicationContext());
        uiModelFactory = new AccountPositionUiModelFactory();
        accountSessionDialogController = new AccountSessionDialogController(
                this,
                new AccountSessionDialogController.Callback() {
                    @Override
                    public void onSessionSubmitting(@NonNull String statusMessage,
                                                   @Nullable String account,
                                                   @Nullable String server) {
                        applyPendingSessionState(statusMessage, account, server);
                    }

                    @Override
                    public void onSessionVerified(@NonNull AccountStatsPreloadManager.Cache verifiedCache,
                                                  @Nullable RemoteAccountProfile profile,
                                                  @NonNull String successMessage) {
                        handleAcceptedLoginResult(successMessage,
                                profile == null ? null : profile.getLogin(),
                                profile == null ? null : profile.getServer());
                        scheduleUiModelBuild(verifiedCache);
                    }

                    @Override
                    public void onSessionFailed(@NonNull String message) {
                        pendingConnectionStatusText = "";
                        restoreLastStableUiModel();
                    }
                }
        );
        uiModelExecutor = Executors.newSingleThreadExecutor();
        setupRecyclerViews();
        setupActions();
        setupBottomNav();
        ensureMonitorServiceStarted();
        preloadManager.start();
        AccountStatsPreloadManager.Cache initialCache = resolveCurrentSessionCache();
        scheduleUiModelBuild(initialCache);
        if (initialCache == null) {
            restoreStoredCurrentSessionCacheAsync();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ensureMonitorServiceStarted();
        MonitorServiceController.dispatch(this, AppConstants.ACTION_BOOTSTRAP);
        applyPaletteStyles();
        applyPrivacyToggleState(isPrivacyMasked());
        updateBottomTabs(false, false, false, true, false);
        preloadManager.addCacheListener(cacheListener);
        scheduleUiModelBuild(resolveCurrentSessionCache());
        requestForegroundEntrySnapshot();
    }

    @Override
    protected void onPause() {
        preloadManager.removeCacheListener(cacheListener);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (preloadManager != null) {
            preloadManager.removeCacheListener(cacheListener);
        }
        if (accountSessionDialogController != null) {
            accountSessionDialogController.shutdown();
        }
        if (uiModelExecutor != null) {
            uiModelExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    // 初始化三段列表，只负责显示，不在页面层重复排序。
    private void setupRecyclerViews() {
        overviewAdapter = new AccountMetricAdapter();
        positionAggregateAdapter = new PositionAggregateAdapter();
        positionAdapter = new PositionAdapterV2();
        pendingOrderAdapter = new PendingOrderAdapter();
        binding.recyclerOverviewMetrics.setLayoutManager(new GridLayoutManager(this, 2));
        binding.recyclerOverviewMetrics.setAdapter(overviewAdapter);
        binding.recyclerPositionAggregates.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositionAggregates.setItemAnimator(null);
        binding.recyclerPositionAggregates.setAdapter(positionAggregateAdapter);
        binding.recyclerPositions.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPositions.setItemAnimator(null);
        binding.recyclerPositions.setAdapter(positionAdapter);
        positionAdapter.setActionListener(new PositionAdapterV2.ActionListener() {
            @Override
            public void onCloseRequested(PositionItem item) {
                openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_CLOSE_POSITION);
            }

            @Override
            public void onModifyRequested(PositionItem item) {
                openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_MODIFY_POSITION);
            }
        });
        binding.recyclerPendingOrders.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPendingOrders.setItemAnimator(null);
        binding.recyclerPendingOrders.setAdapter(pendingOrderAdapter);
        pendingOrderAdapter.setActionListener(new PendingOrderAdapter.ActionListener() {
            @Override
            public void onModifyRequested(PositionItem item) {
                openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_MODIFY_PENDING);
            }

            @Override
            public void onDeleteRequested(PositionItem item) {
                openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_CANCEL_PENDING);
            }
        });
    }

    // 绑定隐私按钮与连接状态入口。
    private void setupActions() {
        binding.ivAccountPrivacyToggle.setOnClickListener(v -> togglePrivacyMaskState());
        binding.tvAccountConnectionStatus.setOnClickListener(v -> openAccountLogin());
    }

    // 绑定底部导航。
    private void setupBottomNav() {
        updateBottomTabs(false, false, false, true, false);
        binding.tabMarketMonitor.setOnClickListener(v -> openMarketMonitor());
        binding.tabMarketChart.setOnClickListener(v -> openMarketChart());
        binding.tabAccountStats.setOnClickListener(v -> openAccountStats());
        binding.tabAccountPosition.setOnClickListener(v -> updateBottomTabs(false, false, false, true, false));
        binding.tabSettings.setOnClickListener(v -> openSettings());
    }

    // 刷新底部导航状态。
    private void updateBottomTabs(boolean marketSelected,
                                  boolean chartSelected,
                                  boolean accountStatsSelected,
                                  boolean accountPositionSelected,
                                  boolean settingsSelected) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        BottomTabVisibilityManager.apply(this,
                binding.tabMarketMonitor,
                binding.tabMarketChart,
                binding.tabAccountStats,
                binding.tabAccountPosition,
                binding.tabSettings);
        binding.tabBar.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.surfaceEnd, palette.stroke));
        styleNavTab(binding.tabMarketMonitor, marketSelected);
        styleNavTab(binding.tabMarketChart, chartSelected);
        styleNavTab(binding.tabAccountStats, accountStatsSelected);
        styleNavTab(binding.tabAccountPosition, accountPositionSelected);
        styleNavTab(binding.tabSettings, settingsSelected);
    }

    // 绘制底部导航项。
    private void styleNavTab(@Nullable TextView tab, boolean selected) {
        UiPaletteManager.styleBottomNavTab(tab, selected, UiPaletteManager.resolve(this));
    }

    // 后台构建新读模型，主线程只做最终绑定。
    private void scheduleUiModelBuild(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (uiModelExecutor == null || uiModelExecutor.isShutdown()) {
            return;
        }
        uiModelExecutor.execute(() -> {
            AccountPositionUiModel nextModel = uiModelFactory.build(cache);
            runOnUiThread(() -> applyUiModel(nextModel));
        });
    }

    // 只消费当前活动远程会话对应的内存缓存，避免切号后短暂回灌旧账号数据。
    @Nullable
    private AccountStatsPreloadManager.Cache resolveCurrentSessionCache() {
        if (preloadManager == null) {
            return null;
        }
        AccountStatsPreloadManager.Cache cache = preloadManager.getLatestCache();
        if (cache == null) {
            return null;
        }
        if (!matchesActiveSessionIdentity(cache.getAccount(), cache.getServer())) {
            return null;
        }
        return cache;
    }

    // 本地缓存恢复必须留在后台线程，避免页面首帧同步读库。
    private void restoreStoredCurrentSessionCacheAsync() {
        if (uiModelExecutor == null || uiModelExecutor.isShutdown()) {
            return;
        }
        uiModelExecutor.execute(() -> {
            AccountStatsPreloadManager.Cache cache = resolveStoredCurrentSessionCacheOnWorkerThread();
            if (cache == null) {
                return;
            }
            runOnUiThread(() -> scheduleUiModelBuild(cache));
        });
    }

    // 内存缓存为空时，只恢复当前活动会话对应的本地缓存，旧账号数据直接丢弃。
    @Nullable
    private AccountStatsPreloadManager.Cache resolveStoredCurrentSessionCacheOnWorkerThread() {
        if (preloadManager == null) {
            return null;
        }
        AccountStatsPreloadManager.Cache cache = preloadManager.hydrateLatestCacheFromStorage();
        if (cache == null) {
            return null;
        }
        if (!matchesActiveSessionIdentity(cache.getAccount(), cache.getServer())) {
            preloadManager.clearLatestCache();
            return null;
        }
        return cache;
    }

    // 校验缓存账号与当前活动远程会话是否一致，避免旧账号快照误上屏。
    private boolean matchesActiveSessionIdentity(@Nullable String account, @Nullable String server) {
        if (secureSessionPrefs == null || !ConfigManager.getInstance(this).isAccountSessionActive()) {
            return false;
        }
        SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();
        if (sessionSummary.hasStorageFailure()) {
            return false;
        }
        RemoteAccountProfile activeAccount = sessionSummary.getActiveAccount();
        if (activeAccount == null) {
            return false;
        }
        String expectedAccount = trimToEmpty(activeAccount.getLogin());
        String expectedServer = trimToEmpty(activeAccount.getServer());
        if (expectedAccount.isEmpty() || expectedServer.isEmpty()) {
            return false;
        }
        return expectedAccount.equalsIgnoreCase(trimToEmpty(account))
                && expectedServer.equalsIgnoreCase(trimToEmpty(server));
    }

    @NonNull
    private String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    // 按分段差异只刷新发生变化的区块。
    private void applyUiModel(@NonNull AccountPositionUiModel nextModel) {
        if (isFinishing() || isDestroyed() || binding == null) {
            return;
        }
        if (nextModel.getSnapshotVersionMs() < currentUiModel.getSnapshotVersionMs()) {
            return;
        }
        boolean masked = isPrivacyMasked();
        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(currentUiModel, nextModel);
        if (diff.isOverviewChanged()) {
            bindOverview(nextModel, masked);
        }
        if (diff.isPositionsChanged()) {
            bindPositions(nextModel, masked);
        }
        if (diff.isPendingChanged()) {
            bindPendingOrders(nextModel, masked);
        }
        if (diff.hasAnyChange()) {
            currentUiModel = nextModel;
        }
        if (!nextModel.getSignature().isEmpty()) {
            lastStableUiModel = nextModel;
        }
        updateConnectionStatusChip(resolveDisplayedConnectionStatusText(nextModel));
        applyPrivacyToggleState(masked);
    }

    // 绑定账户概览分段。
    private void bindOverview(@NonNull AccountPositionUiModel model, boolean masked) {
        overviewAdapter.setMasked(masked);
        overviewAdapter.submitList(model.getOverviewMetrics());
        binding.tvUpdatedAt.setText(model.getUpdatedAtText());
    }

    // 绑定当前持仓分段。
    private void bindPositions(@NonNull AccountPositionUiModel model, boolean masked) {
        List<PositionItem> positions = model.getPositions();
        positionAggregateAdapter.setMasked(masked);
        positionAggregateAdapter.submitList(model.getPositionAggregates());
        positionAdapter.setMasked(masked);
        positionAdapter.submitList(positions);
        binding.tvPositionSummary.setText(masked
                ? "当前持仓 " + SensitiveDisplayMasker.MASK_TEXT
                : model.getPositionSummaryText());
        binding.tvPositionAggregateTitle.setVisibility(positions.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.recyclerPositionAggregates.setVisibility(positions.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.recyclerPositions.setVisibility(positions.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.tvPositionsEmpty.setVisibility(positions.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // 绑定挂单分段。
    private void bindPendingOrders(@NonNull AccountPositionUiModel model, boolean masked) {
        List<PositionItem> pendingOrders = model.getPendingOrders();
        pendingOrderAdapter.setMasked(masked);
        pendingOrderAdapter.submitList(pendingOrders);
        binding.tvPendingSummary.setText(masked
                ? "挂单 " + SensitiveDisplayMasker.MASK_TEXT
                : model.getPendingSummaryText());
        binding.recyclerPendingOrders.setVisibility(pendingOrders.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.tvPendingOrdersEmpty.setVisibility(pendingOrders.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // 应用页面主题。
    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        binding.cardOverviewSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardPositionSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardPendingSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.tvOverviewTitle.setTextColor(palette.textPrimary);
        binding.tvUpdatedAt.setTextColor(palette.textSecondary);
        binding.tvPositionSummary.setTextColor(palette.textSecondary);
        binding.tvPositionAggregateTitle.setTextColor(palette.textSecondary);
        binding.tvPendingSummary.setTextColor(palette.textSecondary);
        binding.tvPositionsEmpty.setTextColor(palette.textSecondary);
        binding.tvPendingOrdersEmpty.setTextColor(palette.textSecondary);
        binding.ivAccountPrivacyToggle.setImageTintList(ColorStateList.valueOf(palette.textSecondary));
        updateConnectionStatusChip(resolveDisplayedConnectionStatusText(currentUiModel));
    }

    // 判断当前是否启用隐私隐藏。
    private boolean isPrivacyMasked() {
        return SensitiveDisplayMasker.isEnabled(this);
    }

    // 切换隐私显示状态，并通知悬浮窗和主链同步刷新。
    private void togglePrivacyMaskState() {
        boolean masked = !isPrivacyMasked();
        ConfigManager.getInstance(getApplicationContext()).setDataMasked(masked);
        MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        applyPrivacyToggleState(masked);
        bindOverview(currentUiModel, masked);
        bindPositions(currentUiModel, masked);
        bindPendingOrders(currentUiModel, masked);
    }

    // 更新隐私按钮图标。
    private void applyPrivacyToggleState(boolean masked) {
        binding.ivAccountPrivacyToggle.setImageResource(masked
                ? R.drawable.ic_privacy_hidden
                : R.drawable.ic_privacy_visible);
        binding.ivAccountPrivacyToggle.setContentDescription(masked ? "显示隐私数据" : "隐藏隐私数据");
        binding.ivAccountPrivacyToggle.setAlpha(masked ? 0.9f : 1f);
    }

    // 用统一样式更新连接状态按钮。
    private void updateConnectionStatusChip(@Nullable String connectionStatusText) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        String safeText = connectionStatusText == null ? "未连接账户" : connectionStatusText;
        boolean connected = safeText.startsWith("已连接");
        binding.tvAccountConnectionStatus.setText(safeText);
        if (connected) {
            binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
            binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.white));
            return;
        }
        binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createOutlinedDrawable(
                this,
                UiPaletteManager.neutralFill(this),
                UiPaletteManager.neutralStroke(this)));
        binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
    }

    // 登录已受理但新快照尚未回到页面时，顶部入口先展示同步中的明确状态。
    @NonNull
    private String resolveDisplayedConnectionStatusText(@NonNull AccountPositionUiModel nextModel) {
        String resolvedText = nextModel.getConnectionStatusText();
        if (resolvedText.startsWith("已连接")) {
            pendingConnectionStatusText = "";
            return resolvedText;
        }
        if (!trimToEmpty(pendingConnectionStatusText).isEmpty()) {
            return pendingConnectionStatusText;
        }
        return resolvedText;
    }

    // 消费登录弹窗返回的受理结果，避免原页在成功提交后仍表现成“无反应”。
    private void handleAcceptedLoginResult(@Nullable String statusMessage,
                                           @Nullable String account,
                                           @Nullable String server) {
        String safeMessage = trimToEmpty(statusMessage);
        pendingConnectionStatusText = safeMessage.isEmpty()
                ? buildAcceptedConnectionStatusText(account)
                : safeMessage;
        updateConnectionStatusChip(resolveDisplayedConnectionStatusText(currentUiModel));
    }

    // 提交新会话后立即清空旧账号内容，避免等待新真值期间继续显示上一个账号的数据。
    private void applyPendingSessionState(@Nullable String statusMessage,
                                          @Nullable String account,
                                          @Nullable String server) {
        pendingConnectionStatusText = trimToEmpty(statusMessage).isEmpty()
                ? "正在同步账户"
                : trimToEmpty(statusMessage);
        currentUiModel = AccountPositionUiModel.empty();
        boolean masked = isPrivacyMasked();
        bindOverview(currentUiModel, masked);
        bindPositions(currentUiModel, masked);
        bindPendingOrders(currentUiModel, masked);
        updateConnectionStatusChip(resolveDisplayedConnectionStatusText(currentUiModel));
    }

    // 账户持仓页回到前台时立即走一次正式账户同步，避免只显示旧缓存等待下一次 stream。
    private void requestForegroundEntrySnapshot() {
        if (preloadManager == null || uiModelExecutor == null || uiModelExecutor.isShutdown()) {
            return;
        }
        uiModelExecutor.execute(() -> preloadManager.fetchForUi(AccountTimeRange.ALL));
    }

    // 会话提交失败后恢复上一份稳定读模型，避免页面长期停在空白态。
    private void restoreLastStableUiModel() {
        if (!lastStableUiModel.getSignature().isEmpty()) {
            applyUiModel(lastStableUiModel);
            return;
        }
        scheduleUiModelBuild(resolveCurrentSessionCache());
    }

    // 登录成功后给顶部入口一个明确成功态，直到新快照把真实连接状态覆盖回来。
    @NonNull
    private String buildAcceptedConnectionStatusText(@Nullable String account) {
        String safeAccount = trimToEmpty(account);
        return safeAccount.isEmpty() ? "登录成功" : "已连接账户 " + safeAccount;
    }

    private void openMarketMonitor() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openMarketChart() {
        Intent intent = new Intent(this, MarketChartActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    // 把账户持仓页的操作按钮统一转给图表页交易链处理，避免本页再维护第二套交易主链。
    private void openChartTradeAction(@Nullable PositionItem item, @NonNull String tradeAction) {
        Intent intent = new Intent(this, MarketChartActivity.class);
        String symbol = item == null ? "" : item.getCode();
        if (symbol == null || symbol.trim().isEmpty()) {
            symbol = item == null ? "" : item.getProductName();
        }
        symbol = ProductSymbolMapper.toMarketSymbol(symbol);
        intent.putExtra(MarketChartActivity.EXTRA_TARGET_SYMBOL, symbol);
        intent.putExtra(MarketChartActivity.EXTRA_TRADE_ACTION, tradeAction);
        intent.putExtra(MarketChartActivity.EXTRA_TRADE_POSITION_TICKET, item == null ? 0L : item.getPositionTicket());
        intent.putExtra(MarketChartActivity.EXTRA_TRADE_ORDER_TICKET, item == null ? 0L : item.getOrderId());
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void openAccountStats() {
        Intent intent = new Intent(this, AccountStatsBridgeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    // 顶部账户入口应直接进入登录弹窗，而不是先落到账户统计页内容。
    private void openAccountLogin() {
        if (accountSessionDialogController == null) {
            return;
        }
        accountSessionDialogController.showLoginDialog();
    }

    // 账户持仓页也需要保证监控服务在线，避免只剩本地旧缓存而没有实时主链。
    private void ensureMonitorServiceStarted() {
        MonitorServiceController.ensureStarted(this);
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }
}
