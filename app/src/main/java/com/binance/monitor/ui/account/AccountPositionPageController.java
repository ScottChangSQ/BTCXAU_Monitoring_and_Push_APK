/*
 * 账户持仓页控制器，统一承接账户概览、当前持仓、挂单三段的页面状态与生命周期。
 * Activity 和 Fragment 都通过这一个控制器复用同一套账户持仓页面实现。
 */
package com.binance.monitor.ui.account;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.databinding.ContentAccountPositionBinding;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
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
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.SensitiveDisplayMasker;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountPositionPageController {

    private final Host host;
    private final ContentAccountPositionBinding binding;
    @Nullable
    private final BottomNavBinding bottomNavBinding;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AccountStatsPreloadManager.CacheListener cacheListener = this::scheduleUiModelBuild;

    private AccountMetricAdapter overviewAdapter;
    private PositionAggregateAdapter positionAggregateAdapter;
    private PositionAdapterV2 positionAdapter;
    private PendingOrderAdapter pendingOrderAdapter;
    private ExecutorService uiModelExecutor;
    private AccountStatsPreloadManager preloadManager;
    private SecureSessionPrefs secureSessionPrefs;
    private AccountPositionUiModelFactory uiModelFactory;
    private AccountSessionDialogController accountSessionDialogController;
    private AccountTradeHistoryBottomSheetController tradeHistoryBottomSheetController;
    private AccountPositionUiModel currentUiModel = AccountPositionUiModel.empty();
    private AccountPositionUiModel lastStableUiModel = AccountPositionUiModel.empty();
    private List<TradeRecordItem> currentTradeHistory = Collections.emptyList();
    private List<TradeRecordItem> lastStableTradeHistory = Collections.emptyList();
    private String pendingConnectionStatusText = "";
    private boolean bound;
    private boolean destroyed;
    private boolean cacheListenerRegistered;

    public AccountPositionPageController(@NonNull Host host,
                                         @NonNull ContentAccountPositionBinding binding,
                                         @Nullable BottomNavBinding bottomNavBinding) {
        this.host = host;
        this.binding = binding;
        this.bottomNavBinding = bottomNavBinding;
    }

    // 首次装配账户持仓页，建立依赖、静态 UI 和初始数据链。
    public void bind() {
        if (bound) {
            return;
        }
        Context appContext = host.getApplicationContext();
        preloadManager = AccountStatsPreloadManager.getInstance(appContext);
        secureSessionPrefs = new SecureSessionPrefs(appContext);
        uiModelFactory = new AccountPositionUiModelFactory();
        accountSessionDialogController = new AccountSessionDialogController(
                host.requireActivity(),
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
        tradeHistoryBottomSheetController = new AccountTradeHistoryBottomSheetController(host.requireActivity());
        uiModelExecutor = Executors.newSingleThreadExecutor();
        ensureMonitorServiceStarted();
        preloadManager.start();
        initializePageContent();
        applyPaletteStyles();
        applyPrivacyToggleState(isPrivacyMasked());
        AccountStatsPreloadManager.Cache initialCache = resolveCurrentSessionCache();
        scheduleUiModelBuild(initialCache);
        if (initialCache == null) {
            restoreStoredCurrentSessionCacheAsync();
        }
        bound = true;
    }

    // 页面进入可见态时恢复主题、监听和正式账户同步。
    public void onPageShown() {
        if (destroyed || !bound) {
            return;
        }
        ensureMonitorServiceStarted();
        MonitorServiceController.dispatch(host.requireActivity(), AppConstants.ACTION_BOOTSTRAP);
        applyPaletteStyles();
        applyPrivacyToggleState(isPrivacyMasked());
        if (!host.isEmbeddedInHostShell()) {
            updateBottomTabs(false, false, false, true);
        }
        if (!cacheListenerRegistered && preloadManager != null) {
            preloadManager.addCacheListener(cacheListener);
            cacheListenerRegistered = true;
        }
        scheduleUiModelBuild(resolveCurrentSessionCache());
        requestForegroundEntrySnapshot();
    }

    // 页面离开可见态时取消刷新监听，避免后台页继续参与刷新。
    public void onPageHidden() {
        if (preloadManager != null && cacheListenerRegistered) {
            preloadManager.removeCacheListener(cacheListener);
            cacheListenerRegistered = false;
        }
    }

    // 页面销毁时释放执行器、会话弹窗和监听器。
    public void onDestroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        onPageHidden();
        if (accountSessionDialogController != null) {
            accountSessionDialogController.shutdown();
        }
        if (uiModelExecutor != null) {
            uiModelExecutor.shutdownNow();
        }
    }

    // 初始化三段列表，只负责显示，不在页面层重复排序。
    private void setupRecyclerViews() {
        overviewAdapter = new AccountMetricAdapter();
        positionAggregateAdapter = new PositionAggregateAdapter();
        positionAdapter = new PositionAdapterV2();
        pendingOrderAdapter = new PendingOrderAdapter();
        binding.recyclerOverviewMetrics.setLayoutManager(new GridLayoutManager(host.requireActivity(), 2));
        binding.recyclerOverviewMetrics.setAdapter(overviewAdapter);
        binding.recyclerPositionAggregates.setLayoutManager(new LinearLayoutManager(host.requireActivity()));
        binding.recyclerPositionAggregates.setItemAnimator(null);
        binding.recyclerPositionAggregates.setAdapter(positionAggregateAdapter);
        binding.recyclerPositions.setLayoutManager(new LinearLayoutManager(host.requireActivity()));
        binding.recyclerPositions.setItemAnimator(null);
        binding.recyclerPositions.setAdapter(positionAdapter);
        positionAdapter.setActionListener(new PositionAdapterV2.ActionListener() {
            @Override
            public void onCloseRequested(PositionItem item) {
                host.openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_CLOSE_POSITION);
            }

            @Override
            public void onModifyRequested(PositionItem item) {
                host.openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_MODIFY_POSITION);
            }
        });
        binding.recyclerPendingOrders.setLayoutManager(new LinearLayoutManager(host.requireActivity()));
        binding.recyclerPendingOrders.setItemAnimator(null);
        binding.recyclerPendingOrders.setAdapter(pendingOrderAdapter);
        pendingOrderAdapter.setActionListener(new PendingOrderAdapter.ActionListener() {
            @Override
            public void onModifyRequested(PositionItem item) {
                host.openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_MODIFY_PENDING);
            }

            @Override
            public void onDeleteRequested(PositionItem item) {
                host.openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_CANCEL_PENDING);
            }
        });
    }

    // 绑定隐私按钮与连接状态入口。
    private void setupActions() {
        binding.ivAccountPrivacyToggle.setOnClickListener(v -> togglePrivacyMaskState());
        binding.tvAccountConnectionStatus.setOnClickListener(v -> openAccountLogin());
        binding.btnOpenAccountHistory.setOnClickListener(v -> openTradeHistorySheet());
    }

    // 绑定底部导航；主壳承载时隐藏页内 Tab，避免双层导航。
    private void setupBottomNav() {
        if (bottomNavBinding == null) {
            return;
        }
        if (host.isEmbeddedInHostShell()) {
            bottomNavBinding.tabBar.setVisibility(View.GONE);
            return;
        }
        bottomNavBinding.tabBar.setVisibility(View.VISIBLE);
        updateBottomTabs(false, false, false, true);
        bottomNavBinding.tabMarketMonitor.setOnClickListener(v -> host.openMarketMonitor());
        bottomNavBinding.tabMarketChart.setOnClickListener(v -> host.openMarketChart());
        bottomNavBinding.tabAccountStats.setOnClickListener(v -> host.openAccountStats());
        bottomNavBinding.tabAccountPosition.setOnClickListener(v -> updateBottomTabs(false, false, false, true));
    }

    // 装配账户持仓页的静态页面结构，供 Activity 和 Fragment 共用。
    private void initializePageContent() {
        setupRecyclerViews();
        setupActions();
        setupBottomNav();
    }

    // 刷新底部导航状态。
    private void updateBottomTabs(boolean marketSelected,
                                  boolean chartSelected,
                                  boolean accountStatsSelected,
                                  boolean accountPositionSelected) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(host.requireActivity());
        if (bottomNavBinding == null) {
            return;
        }
        BottomTabVisibilityManager.apply(host.requireActivity(),
                bottomNavBinding.tabMarketMonitor,
                bottomNavBinding.tabMarketChart,
                bottomNavBinding.tabAccountStats,
                bottomNavBinding.tabAccountPosition,
                null);
        bottomNavBinding.tabBar.setBackground(UiPaletteManager.createOutlinedDrawable(
                host.requireActivity(),
                palette.surfaceEnd,
                palette.stroke));
        styleNavTab(bottomNavBinding.tabMarketMonitor, marketSelected);
        styleNavTab(bottomNavBinding.tabMarketChart, chartSelected);
        styleNavTab(bottomNavBinding.tabAccountStats, accountStatsSelected);
        styleNavTab(bottomNavBinding.tabAccountPosition, accountPositionSelected);
    }

    // 绘制底部导航项。
    private void styleNavTab(@Nullable TextView tab, boolean selected) {
        UiPaletteManager.styleBottomNavTab(tab, selected, UiPaletteManager.resolve(host.requireActivity()));
    }

    // 后台构建新读模型，主线程只做最终绑定。
    private void scheduleUiModelBuild(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (uiModelExecutor == null || uiModelExecutor.isShutdown()) {
            return;
        }
        uiModelExecutor.execute(() -> {
            AccountPositionUiModel nextModel = uiModelFactory.build(cache);
            List<TradeRecordItem> tradeHistory = resolveTradeHistory(cache);
            mainHandler.post(() -> applyUiModel(nextModel, tradeHistory));
        });
    }

    // 从当前缓存里提取完整历史成交，供账户页底部抽屉直接展示。
    @NonNull
    private List<TradeRecordItem> resolveTradeHistory(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null || cache.getSnapshot() == null || cache.getSnapshot().getTrades() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(cache.getSnapshot().getTrades());
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
            mainHandler.post(() -> scheduleUiModelBuild(cache));
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
        if (secureSessionPrefs == null
                || !ConfigManager.getInstance(host.requireActivity()).isAccountSessionActive()) {
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
    private void applyUiModel(@NonNull AccountPositionUiModel nextModel,
                              @NonNull List<TradeRecordItem> tradeHistory) {
        if (destroyed || !host.isPageReady()) {
            return;
        }
        if (nextModel.getSnapshotVersionMs() < currentUiModel.getSnapshotVersionMs()) {
            return;
        }
        currentTradeHistory = Collections.unmodifiableList(new ArrayList<>(tradeHistory));
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
            lastStableTradeHistory = currentTradeHistory;
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
        binding.tvPositionAggregateTitle.setVisibility(View.GONE);
        binding.recyclerPositionAggregates.setVisibility(View.GONE);
        binding.recyclerPositions.setVisibility(positions.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvPositionsEmpty.setVisibility(positions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // 绑定挂单分段。
    private void bindPendingOrders(@NonNull AccountPositionUiModel model, boolean masked) {
        List<PositionItem> pendingOrders = model.getPendingOrders();
        pendingOrderAdapter.setMasked(masked);
        pendingOrderAdapter.submitList(pendingOrders);
        binding.tvPendingSummary.setText(masked
                ? "挂单 " + SensitiveDisplayMasker.MASK_TEXT
                : model.getPendingSummaryText());
        binding.recyclerPendingOrders.setVisibility(pendingOrders.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvPendingOrdersEmpty.setVisibility(pendingOrders.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // 应用页面主题。
    private void applyPaletteStyles() {
        AppCompatActivity activity = host.requireActivity();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(activity, palette);
        applySectionCardStyle(binding.cardOverviewSection, palette);
        applySectionCardStyle(binding.cardPositionSection, palette);
        applySectionCardStyle(binding.cardPendingSection, palette);
        applySectionCardStyle(binding.cardHistorySection, palette);
        binding.tvOverviewTitle.setTextColor(palette.textPrimary);
        binding.tvUpdatedAt.setTextColor(palette.textSecondary);
        binding.tvPositionSummary.setTextColor(palette.textSecondary);
        binding.tvPositionAggregateTitle.setTextColor(palette.textSecondary);
        binding.tvPendingSummary.setTextColor(palette.textSecondary);
        binding.tvHistorySectionTitle.setTextColor(palette.textPrimary);
        binding.tvPositionsEmpty.setTextColor(palette.textSecondary);
        binding.tvPendingOrdersEmpty.setTextColor(palette.textSecondary);
        binding.btnOpenAccountHistory.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.card, palette.stroke));
        ViewCompat.setBackgroundTintList(binding.btnOpenAccountHistory, null);
        binding.btnOpenAccountHistory.setTextColor(palette.textPrimary);
        binding.ivAccountPrivacyToggle.setImageTintList(ColorStateList.valueOf(palette.textSecondary));
        updateConnectionStatusChip(resolveDisplayedConnectionStatusText(currentUiModel));
    }

    // 账户页四个主卡片是 CardView，需要直接刷卡片自身底色和圆角，setBackground 不会真正改掉卡片面板。
    private void applySectionCardStyle(@NonNull CardView cardView,
                                       @NonNull UiPaletteManager.Palette palette) {
        AppCompatActivity activity = host.requireActivity();
        cardView.setCardBackgroundColor(palette.surfaceEnd);
        cardView.setCardElevation(0f);
        cardView.setRadius(UiPaletteManager.radiusLgPx(activity, palette));
    }

    // 判断当前是否启用隐私隐藏。
    private boolean isPrivacyMasked() {
        return SensitiveDisplayMasker.isEnabled(host.requireActivity());
    }

    // 切换隐私显示状态，并通知悬浮窗和主链同步刷新。
    private void togglePrivacyMaskState() {
        boolean masked = !isPrivacyMasked();
        ConfigManager.getInstance(host.getApplicationContext()).setDataMasked(masked);
        MonitorServiceController.dispatch(host.requireActivity(), AppConstants.ACTION_REFRESH_CONFIG);
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
        AppCompatActivity activity = host.requireActivity();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        String safeText = connectionStatusText == null ? "未连接账户" : connectionStatusText;
        boolean connected = safeText.startsWith("已连接");
        binding.tvAccountConnectionStatus.setText(safeText);
        if (connected) {
            binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createFilledDrawable(activity, palette.primary));
            binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(activity, R.color.white));
            return;
        }
        binding.tvAccountConnectionStatus.setBackground(UiPaletteManager.createOutlinedDrawable(
                activity,
                UiPaletteManager.neutralFill(activity),
                UiPaletteManager.neutralStroke(activity)));
        binding.tvAccountConnectionStatus.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary));
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
        currentTradeHistory = Collections.emptyList();
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
            applyUiModel(lastStableUiModel, lastStableTradeHistory);
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

    // 顶部账户入口应直接进入登录弹窗，而不是先落到账户统计页内容。
    private void openAccountLogin() {
        if (accountSessionDialogController == null) {
            return;
        }
        accountSessionDialogController.showLoginDialog();
    }

    // 历史成交入口直接弹出完整列表抽屉，不再跳转到分析页。
    private void openTradeHistorySheet() {
        if (tradeHistoryBottomSheetController == null) {
            return;
        }
        tradeHistoryBottomSheetController.show(currentTradeHistory);
    }

    // 账户持仓页也需要保证监控服务在线，避免只剩本地旧缓存而没有实时主链。
    private void ensureMonitorServiceStarted() {
        MonitorServiceController.ensureStarted(host.requireActivity());
    }

    public interface Host {
        @NonNull
        AppCompatActivity requireActivity();

        @NonNull
        Context getApplicationContext();

        boolean isEmbeddedInHostShell();

        boolean isPageReady();

        void openMarketMonitor();

        void openMarketChart();

        void openAccountStats();

        void openSettings();
        void openChartTradeAction(@Nullable PositionItem item, @NonNull String tradeAction);
    }

    public static final class BottomNavBinding {
        final View tabBar;
        final TextView tabMarketMonitor;
        final TextView tabMarketChart;
        final TextView tabAccountPosition;
        final TextView tabAccountStats;

        public BottomNavBinding(@NonNull View tabBar,
                                @NonNull TextView tabMarketMonitor,
                                @NonNull TextView tabMarketChart,
                                @NonNull TextView tabAccountPosition,
                                @NonNull TextView tabAccountStats) {
            this.tabBar = tabBar;
            this.tabMarketMonitor = tabMarketMonitor;
            this.tabMarketChart = tabMarketChart;
            this.tabAccountPosition = tabAccountPosition;
            this.tabAccountStats = tabAccountStats;
        }
    }
}
