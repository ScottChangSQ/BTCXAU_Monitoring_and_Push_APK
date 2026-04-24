/*
 * 账户持仓页控制器，统一承接账户概览、当前持仓、挂单三段的页面状态与生命周期。
 * Activity 和 Fragment 都通过这一个控制器复用同一套账户持仓页面实现。
 */
package com.binance.monitor.ui.account;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.data.remote.v2.GatewayV2TradeClient;
import com.binance.monitor.databinding.ContentAccountPositionBinding;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.ui.PageBootstrapSnapshot;
import com.binance.monitor.runtime.ui.PageBootstrapState;
import com.binance.monitor.runtime.ui.PageBootstrapStateMachine;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.service.MonitorServiceController;
import com.binance.monitor.ui.account.adapter.AccountMetricAdapter;
import com.binance.monitor.ui.account.adapter.PendingOrderAdapter;
import com.binance.monitor.ui.account.adapter.PositionAggregateAdapter;
import com.binance.monitor.ui.account.adapter.PositionAdapterV2;
import com.binance.monitor.ui.chart.MarketChartTradeSupport;
import com.binance.monitor.ui.main.BottomTabVisibilityManager;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.ui.trade.BatchTradeCoordinator;
import com.binance.monitor.ui.trade.TradeAuditStore;
import com.binance.monitor.ui.trade.TradeBatchActionDialogCoordinator;
import com.binance.monitor.ui.trade.TradeCommandFactory;
import com.binance.monitor.ui.trade.TradeConfirmDialogController;
import com.binance.monitor.ui.trade.TradeExecutionCoordinator;
import com.binance.monitor.ui.trade.TradeRiskGuard;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
    private final AccountStatsPreloadManager.CacheListener cacheListener = this::handleCacheChanged;

    private AccountMetricAdapter overviewAdapter;
    private PositionAggregateAdapter positionAggregateAdapter;
    private PositionAdapterV2 positionAdapter;
    private PendingOrderAdapter pendingOrderAdapter;
    private ExecutorService uiModelExecutor;
    private ExecutorService tradeExecutor;
    private AccountStatsPreloadManager preloadManager;
    private SecureSessionPrefs secureSessionPrefs;
    private AccountPositionUiModelFactory uiModelFactory;
    private AccountSessionDialogController accountSessionDialogController;
    private AccountTradeHistoryBottomSheetController tradeHistoryBottomSheetController;
    private GatewayV2TradeClient gatewayV2TradeClient;
    private TradeExecutionCoordinator tradeExecutionCoordinator;
    private BatchTradeCoordinator batchTradeCoordinator;
    private TradeBatchActionDialogCoordinator batchActionDialogCoordinator;
    private AccountPositionUiModel currentUiModel = AccountPositionUiModel.empty();
    private AccountPositionUiModel lastStableUiModel = AccountPositionUiModel.empty();
    private List<TradeRecordItem> currentTradeHistory = Collections.emptyList();
    private List<TradeRecordItem> lastStableTradeHistory = Collections.emptyList();
    private PageBootstrapStateMachine accountBootstrapStateMachine = new PageBootstrapStateMachine();
    private PageBootstrapSnapshot accountBootstrapSnapshot = PageBootstrapSnapshot.initial();
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
        gatewayV2TradeClient = new GatewayV2TradeClient(appContext);
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
                        applyBootstrapState(accountBootstrapStateMachine.onMemoryDataReady(buildAccountBootstrapRevision(verifiedCache)));
                        scheduleUiModelBuild(verifiedCache);
                    }

                    @Override
                    public void onSessionFailed(@NonNull String message) {
                        pendingConnectionStatusText = buildFailureConnectionStatusText(message);
                        restoreLastStableUiModel();
                    }

                    @Override
                    public void onSessionLoggedOut(@NonNull String message) {
                        applyLoggedOutSessionState();
                    }
                }
        );
        tradeHistoryBottomSheetController = new AccountTradeHistoryBottomSheetController(host.requireActivity());
        uiModelExecutor = Executors.newSingleThreadExecutor();
        tradeExecutor = Executors.newSingleThreadExecutor();
        tradeExecutionCoordinator = createTradeExecutionCoordinator();
        batchTradeCoordinator = createBatchTradeCoordinator();
        batchActionDialogCoordinator = new TradeBatchActionDialogCoordinator(
                host.requireActivity(),
                tradeExecutor,
                batchTradeCoordinator,
                this::refreshAccountTradeSnapshot
        );
        ensureMonitorServiceStarted();
        preloadManager.start();
        initializePageContent();
        applyPaletteStyles();
        applyPrivacyToggleState(isPrivacyMasked());
        AccountStatsPreloadManager.Cache initialCache = resolveCurrentSessionCache();
        if (initialCache != null) {
            applyBootstrapState(accountBootstrapStateMachine.onMemoryDataReady(buildAccountBootstrapRevision(initialCache)));
        } else {
            applyBootstrapState(accountBootstrapStateMachine.onStorageRestoreStarted());
        }
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
        if (tradeHistoryBottomSheetController != null) {
            tradeHistoryBottomSheetController.dismiss();
        }
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
        if (tradeHistoryBottomSheetController != null) {
            tradeHistoryBottomSheetController.dismiss();
        }
        if (accountSessionDialogController != null) {
            accountSessionDialogController.shutdown();
        }
        if (uiModelExecutor != null) {
            uiModelExecutor.shutdownNow();
        }
        if (tradeExecutor != null) {
            tradeExecutor.shutdownNow();
        }
        if (batchActionDialogCoordinator != null) {
            batchActionDialogCoordinator.cancelRunningTask();
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
                requestClosePosition(item);
            }

            @Override
            public void onModifyRequested(PositionItem item) {
                showModifyPositionDialog(item);
            }
        });
        binding.recyclerPendingOrders.setLayoutManager(new LinearLayoutManager(host.requireActivity()));
        binding.recyclerPendingOrders.setItemAnimator(null);
        binding.recyclerPendingOrders.setAdapter(pendingOrderAdapter);
        pendingOrderAdapter.setActionListener(new PendingOrderAdapter.ActionListener() {
            @Override
            public void onModifyRequested(PositionItem item) {
                showModifyPendingDialog(item);
            }

            @Override
            public void onDeleteRequested(PositionItem item) {
                requestCancelPendingOrder(item);
            }
        });
    }

    // 绑定隐私按钮与连接状态入口。
    private void setupActions() {
        binding.ivAccountPrivacyToggle.setOnClickListener(v -> togglePrivacyMaskState());
        binding.tvAccountConnectionStatus.setOnClickListener(v -> openAccountLogin());
        binding.btnOpenAccountHistory.setOnClickListener(v -> openTradeHistorySheet());
        binding.btnAccountBatchActions.setOnClickListener(v -> openBatchTradeActions());
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
        bindHistorySection(Collections.emptyList());
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
            AccountPositionUiModel nextModel = resolveVisibleUiModel(cache);
            List<TradeRecordItem> tradeHistory = resolveTradeHistory(cache);
            mainHandler.post(() -> applyUiModel(nextModel, tradeHistory));
        });
    }

    // 监听到缓存变化时，先推进 bootstrap 状态，再更新可见模型。
    private void handleCacheChanged(@Nullable AccountStatsPreloadManager.Cache cache) {
        AccountStatsPreloadManager.Cache effectiveCache = filterCurrentSessionCache(cache);
        if (accountSessionDialogController != null) {
            accountSessionDialogController.onCacheUpdated(effectiveCache);
        }
        if (effectiveCache != null) {
            applyBootstrapState(accountBootstrapStateMachine.onMemoryDataReady(buildAccountBootstrapRevision(effectiveCache)));
        }
        scheduleUiModelBuild(effectiveCache);
    }

    // 根据当前 bootstrap 状态决定首帧应显示恢复态还是真正空态。
    @NonNull
    private AccountPositionUiModel resolveVisibleUiModel(@Nullable AccountStatsPreloadManager.Cache cache) {
        AccountStatsPreloadManager.Cache effectiveCache = filterCurrentSessionCache(cache);
        if (effectiveCache != null) {
            return uiModelFactory.build(effectiveCache);
        }
        if (accountBootstrapSnapshot != null
                && accountBootstrapSnapshot.getState() == PageBootstrapState.STORAGE_RESTORING) {
            return AccountPositionUiModel.restoring();
        }
        return AccountPositionUiModel.empty();
    }

    // 从当前缓存里提取完整历史成交，供账户页底部抽屉直接展示。
    @NonNull
    private List<TradeRecordItem> resolveTradeHistory(@Nullable AccountStatsPreloadManager.Cache cache) {
        AccountStatsPreloadManager.Cache effectiveCache = filterCurrentSessionCache(cache);
        if (effectiveCache == null
                || effectiveCache.getSnapshot() == null
                || effectiveCache.getSnapshot().getTrades() == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(effectiveCache.getSnapshot().getTrades());
    }

    // 将任意来源的缓存统一收口到“当前活动会话对应的 cache 或空”。
    @Nullable
    private AccountStatsPreloadManager.Cache filterCurrentSessionCache(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null) {
            return null;
        }
        return matchesActiveSessionIdentity(cache.getAccount(), cache.getServer()) ? cache : null;
    }

    // 只消费当前活动远程会话对应的内存缓存，避免切号后短暂回灌旧账号数据。
    @Nullable
    private AccountStatsPreloadManager.Cache resolveCurrentSessionCache() {
        if (preloadManager == null) {
            return null;
        }
        AccountStatsPreloadManager.Cache cache = preloadManager.getLatestCache();
        return filterCurrentSessionCache(cache);
    }

    // 本地缓存恢复必须留在后台线程，避免页面首帧同步读库。
    private void restoreStoredCurrentSessionCacheAsync() {
        if (uiModelExecutor == null || uiModelExecutor.isShutdown()) {
            return;
        }
        uiModelExecutor.execute(() -> {
            AccountStatsPreloadManager.Cache cache = resolveStoredCurrentSessionCacheOnWorkerThread();
            mainHandler.post(() -> {
                if (cache == null) {
                    applyBootstrapState(accountBootstrapStateMachine.onStorageMiss());
                    scheduleUiModelBuild(null);
                    return;
                }
                applyBootstrapState(accountBootstrapStateMachine.onStorageDataReady(buildAccountBootstrapRevision(cache)));
                scheduleUiModelBuild(cache);
            });
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

    // 用更新时间优先、拉取时间兜底，生成账户页首帧状态机使用的稳定版本号。
    @NonNull
    private String buildAccountBootstrapRevision(@NonNull AccountStatsPreloadManager.Cache cache) {
        long updatedAt = cache.getUpdatedAt() > 0L ? cache.getUpdatedAt() : cache.getFetchedAt();
        return String.valueOf(updatedAt);
    }

    // 写入新的 bootstrap 状态，账户页可见模型会在下一轮构建时按状态切换。
    private void applyBootstrapState(@NonNull PageBootstrapSnapshot snapshot) {
        accountBootstrapSnapshot = snapshot;
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
        boolean masked = isPrivacyMasked();
        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(currentUiModel, nextModel, currentTradeHistory, tradeHistory);
        currentTradeHistory = Collections.unmodifiableList(new ArrayList<>(tradeHistory));
        if (diff.isOverviewChanged()) {
            bindOverview(nextModel, masked);
        }
        if (diff.isPositionsChanged()) {
            bindPositions(nextModel, masked);
        }
        if (diff.isPendingChanged()) {
            bindPendingOrders(nextModel, masked);
        }
        // 历史入口只有一个按钮，但状态必须始终跟最新历史快照同步，不能只靠 diff 决定是否刷新。
        bindHistorySection(currentTradeHistory);
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
        List<PositionAggregateItem> aggregates = model.getPositionAggregates();
        positionAggregateAdapter.setMasked(masked);
        positionAggregateAdapter.submitList(aggregates);
        positionAdapter.setRuntimeIdentity(model.getAccount(), model.getServer());
        positionAdapter.setMasked(masked);
        positionAdapter.submitList(positions);
        binding.tvPositionSummary.setText(masked
                ? "当前持仓 " + SensitiveDisplayMasker.MASK_TEXT
                : model.getPositionSummaryText());
        binding.tvPositionAggregateTitle.setVisibility(aggregates.isEmpty() ? View.GONE : View.VISIBLE);
        binding.recyclerPositionAggregates.setVisibility(aggregates.isEmpty() ? View.GONE : View.VISIBLE);
        binding.recyclerPositions.setVisibility(positions.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvPositionsEmpty.setVisibility(positions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // 绑定挂单分段。
    private void bindPendingOrders(@NonNull AccountPositionUiModel model, boolean masked) {
        List<PositionItem> pendingOrders = model.getPendingOrders();
        pendingOrderAdapter.setRuntimeIdentity(model.getAccount(), model.getServer());
        pendingOrderAdapter.setMasked(masked);
        pendingOrderAdapter.submitList(pendingOrders);
        binding.tvPendingSummary.setText(masked
                ? "挂单 " + SensitiveDisplayMasker.MASK_TEXT
                : model.getPendingSummaryText());
        binding.recyclerPendingOrders.setVisibility(pendingOrders.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvPendingOrdersEmpty.setVisibility(pendingOrders.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // 绑定历史成交分段，只在历史 revision 变化时更新入口状态。
    private void bindHistorySection(@NonNull List<TradeRecordItem> tradeHistory) {
        boolean hasHistory = !tradeHistory.isEmpty();
        binding.btnOpenAccountHistory.setEnabled(hasHistory);
        binding.btnOpenAccountHistory.setAlpha(hasHistory ? 1f : 0.6f);
        binding.btnOpenAccountHistory.setText(hasHistory
                ? host.requireActivity().getString(R.string.account_history_entry_action_with_count, tradeHistory.size())
                : host.requireActivity().getString(R.string.account_history_entry_action));
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
        UiPaletteManager.styleTextTrigger(
                binding.btnOpenAccountHistory,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnAccountBatchActions,
                palette,
                palette.primarySoft,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control,
                8,
                R.dimen.position_row_action_height
        );
        ViewCompat.setBackgroundTintList(binding.btnOpenAccountHistory, null);
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

    // 用标准主体更新连接状态入口，避免页面继续手写 chip 壳子。
    private void updateConnectionStatusChip(@Nullable String connectionStatusText) {
        AppCompatActivity activity = host.requireActivity();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        String safeText = connectionStatusText == null ? "未连接账户" : connectionStatusText;
        boolean connected = safeText.startsWith("已连接");
        binding.tvAccountConnectionStatus.setText(safeText);
        if (connected) {
            UiPaletteManager.styleTextTrigger(
                    binding.tvAccountConnectionStatus,
                    palette,
                    palette.primarySoft,
                    UiPaletteManager.controlSelectedText(activity),
                    R.style.TextAppearance_BinanceMonitor_ControlCompact
            );
            return;
        }
        UiPaletteManager.styleTextTrigger(
                binding.tvAccountConnectionStatus,
                palette,
                palette.control,
                UiPaletteManager.controlUnselectedText(activity),
                R.style.TextAppearance_BinanceMonitor_ControlCompact
        );
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
        bindHistorySection(Collections.emptyList());
        updateConnectionStatusChip(resolveDisplayedConnectionStatusText(currentUiModel));
    }

    // 退出登录后把账户页切回未登录空态，避免旧账号数据继续残留在页面和服务运行态里。
    private void applyLoggedOutSessionState() {
        pendingConnectionStatusText = "";
        currentTradeHistory = Collections.emptyList();
        lastStableTradeHistory = Collections.emptyList();
        currentUiModel = AccountPositionUiModel.empty();
        lastStableUiModel = AccountPositionUiModel.empty();
        applyBootstrapState(accountBootstrapStateMachine.onStorageMiss());
        if (preloadManager != null) {
            preloadManager.setFullSnapshotActive(false);
            preloadManager.clearLatestCache();
        }
        ConfigManager.getInstance(host.getApplicationContext()).setAccountSessionActive(false);
        requestMonitorServiceAccountRuntimeClear();
        boolean masked = isPrivacyMasked();
        bindOverview(currentUiModel, masked);
        bindPositions(currentUiModel, masked);
        bindPendingOrders(currentUiModel, masked);
        bindHistorySection(Collections.emptyList());
        updateConnectionStatusChip(resolveDisplayedConnectionStatusText(currentUiModel));
    }

    // 账户持仓页回到前台时先走轻量运行态确认，避免重新把登录收口绑回 full 全量链。
    private void requestForegroundEntrySnapshot() {
        if (preloadManager == null || uiModelExecutor == null || uiModelExecutor.isShutdown()) {
            return;
        }
        uiModelExecutor.execute(() -> preloadManager.fetchSnapshotForUi());
    }

    // 会话提交失败后恢复上一份稳定读模型，避免页面长期停在空白态。
    private void restoreLastStableUiModel() {
        if (!lastStableUiModel.getSignature().isEmpty()) {
            applyUiModel(lastStableUiModel, lastStableTradeHistory);
            return;
        }
        scheduleUiModelBuild(resolveCurrentSessionCache());
    }

    // 登录失败后顶部入口只保留短状态，完整细节交给失败弹窗展示。
    @NonNull
    private String buildFailureConnectionStatusText(@Nullable String message) {
        String safeMessage = trimToEmpty(message);
        if (safeMessage.isEmpty()) {
            return "登录失败";
        }
        int lineBreak = safeMessage.indexOf('\n');
        String firstLine = lineBreak >= 0 ? safeMessage.substring(0, lineBreak).trim() : safeMessage;
        if (firstLine.isEmpty()) {
            return "登录失败";
        }
        return firstLine.length() > 20 ? "登录失败" : firstLine;
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
        String displayedConnectionStatus = resolveDisplayedConnectionStatusText(currentUiModel);
        if (displayedConnectionStatus.startsWith("已连接")) {
            accountSessionDialogController.showAccountConnectionDialog(resolveCurrentSessionCache(), displayedConnectionStatus);
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

    // 账户页批量入口与图表页共用同一条正式批量链。
    private void openBatchTradeActions() {
        if (batchActionDialogCoordinator == null) {
            return;
        }
        batchActionDialogCoordinator.showEntry(new TradeBatchActionDialogCoordinator.BatchActionContext(
                currentUiModel.getAccount(),
                resolveCurrentAccountMode(),
                "",
                currentUiModel.getPositions(),
                currentUiModel.getPendingOrders()
        ));
    }

    // 账户页平仓按钮直接走正式交易主链。
    private void requestClosePosition(@Nullable PositionItem item) {
        if (item == null) {
            return;
        }
        long positionTicket = item.getPositionTicket();
        if (positionTicket <= 0L) {
            Toast.makeText(host.requireActivity(), "当前持仓缺少有效票号，暂时不能平仓", Toast.LENGTH_SHORT).show();
            return;
        }
        double volume = Math.abs(item.getQuantity());
        if (volume <= 0d) {
            Toast.makeText(host.requireActivity(), "当前持仓手数无效，暂时不能平仓", Toast.LENGTH_SHORT).show();
            return;
        }
        executeTradeCommand(TradeCommandFactory.closePosition(
                currentUiModel.getAccount(),
                resolveTradeSymbol(item),
                positionTicket,
                volume,
                0d
        ));
    }

    // 账户页撤单按钮直接走正式交易主链。
    private void requestCancelPendingOrder(@Nullable PositionItem item) {
        if (item == null) {
            return;
        }
        long orderTicket = item.getOrderId();
        if (orderTicket <= 0L) {
            Toast.makeText(host.requireActivity(), "当前挂单缺少有效票号，暂时不能撤销", Toast.LENGTH_SHORT).show();
            return;
        }
        executeTradeCommand(TradeCommandFactory.pendingCancel(
                currentUiModel.getAccount(),
                resolveTradeSymbol(item),
                orderTicket
        ));
    }

    // 账户页改单对话框。
    private void showModifyPendingDialog(@Nullable PositionItem item) {
        if (item == null) {
            return;
        }
        LinearLayout container = createTradeFormContainer();
        TextInputLayout priceLayout = createTradeInputLayout("挂单价格");
        TextInputEditText priceInput = createTradeInput();
        if (item.getPendingPrice() > 0d) {
            priceInput.setText(FormatUtils.formatPrice(item.getPendingPrice()));
        }
        priceLayout.addView(priceInput);
        container.addView(priceLayout);

        TextInputLayout slLayout = createTradeInputLayout("止损");
        TextInputEditText slInput = createTradeInput();
        if (item.getStopLoss() > 0d) {
            slInput.setText(FormatUtils.formatPrice(item.getStopLoss()));
        }
        slLayout.addView(slInput);
        container.addView(slLayout);

        TextInputLayout tpLayout = createTradeInputLayout("止盈");
        TextInputEditText tpInput = createTradeInput();
        if (item.getTakeProfit() > 0d) {
            tpInput.setText(FormatUtils.formatPrice(item.getTakeProfit()));
        }
        tpLayout.addView(tpInput);
        container.addView(tpLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(host.requireActivity())
                .setTitle("修改挂单")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(host.requireActivity()));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double price = parseTradeValue(priceInput, item.getPendingPrice());
            double sl = parseTradeValue(slInput, item.getStopLoss());
            double tp = parseTradeValue(tpInput, item.getTakeProfit());
            executeTradeCommand(TradeCommandFactory.pendingModify(
                    currentUiModel.getAccount(),
                    resolveTradeSymbol(item),
                    item.getOrderId(),
                    price,
                    sl,
                    tp
            ));
            dialog.dismiss();
        });
    }

    // 账户页修改 TP/SL 对话框。
    private void showModifyPositionDialog(@Nullable PositionItem item) {
        if (item == null) {
            return;
        }
        LinearLayout container = createTradeFormContainer();
        TextInputLayout slLayout = createTradeInputLayout("止损");
        TextInputEditText slInput = createTradeInput();
        if (item.getStopLoss() > 0d) {
            slInput.setText(FormatUtils.formatPrice(item.getStopLoss()));
        }
        slLayout.addView(slInput);
        container.addView(slLayout);

        TextInputLayout tpLayout = createTradeInputLayout("止盈");
        TextInputEditText tpInput = createTradeInput();
        if (item.getTakeProfit() > 0d) {
            tpInput.setText(FormatUtils.formatPrice(item.getTakeProfit()));
        }
        tpLayout.addView(tpInput);
        container.addView(tpLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(host.requireActivity())
                .setTitle("修改止盈止损")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("继续", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(host.requireActivity()));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double sl = parseTradeValue(slInput, item.getStopLoss());
            double tp = parseTradeValue(tpInput, item.getTakeProfit());
            if (sl <= 0d && tp <= 0d) {
                Toast.makeText(host.requireActivity(), "请至少填写一个新的止盈或止损", Toast.LENGTH_SHORT).show();
                return;
            }
            executeTradeCommand(TradeCommandFactory.modifyTpSl(
                    currentUiModel.getAccount(),
                    resolveTradeSymbol(item),
                    item.getPositionTicket(),
                    item.getCostPrice() > 0d ? item.getCostPrice() : item.getLatestPrice(),
                    sl,
                    tp
            ));
            dialog.dismiss();
        });
    }

    // 账户页单笔交易统一复用检查、确认和提交流程。
    private void executeTradeCommand(@Nullable TradeCommand command) {
        if (command == null || tradeExecutionCoordinator == null || tradeExecutor == null) {
            return;
        }
        AccountStatsPreloadManager.Cache baselineCache = resolveCurrentSessionCache();
        tradeExecutor.execute(() -> {
            try {
                TradeExecutionCoordinator.PreparedTrade preparedTrade = tradeExecutionCoordinator.prepareExecution(command);
                mainHandler.post(() -> handlePreparedTrade(command, preparedTrade, baselineCache));
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "交易准备失败" : exception.getMessage().trim();
                mainHandler.post(() -> Toast.makeText(host.requireActivity(), message, Toast.LENGTH_SHORT).show());
            }
        });
    }

    // 检查通过后再决定是否弹确认框。
    private void handlePreparedTrade(@NonNull TradeCommand command,
                                     @Nullable TradeExecutionCoordinator.PreparedTrade preparedTrade,
                                     @Nullable AccountStatsPreloadManager.Cache baselineCache) {
        if (preparedTrade == null) {
            Toast.makeText(host.requireActivity(), "交易准备失败，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (preparedTrade.getUiState() != TradeExecutionCoordinator.UiState.AWAITING_CONFIRMATION) {
            showTradeOutcome(preparedTrade.getMessage());
            return;
        }
        if (!preparedTrade.requiresConfirmation()) {
            submitTradeAfterConfirmation(preparedTrade.markConfirmed(), baselineCache);
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(host.requireActivity())
                .setTitle("确认交易")
                .setMessage(TradeCommandFactory.describe(command) + "\n\n" + preparedTrade.getMessage())
                .setNegativeButton("取消", null)
                .setPositiveButton("确认", (dialogInterface, which) -> submitTradeAfterConfirmation(preparedTrade.markConfirmed(), baselineCache))
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(host.requireActivity()));
    }

    // 确认后的正式提交。
    private void submitTradeAfterConfirmation(@NonNull TradeExecutionCoordinator.PreparedTrade preparedTrade,
                                              @Nullable AccountStatsPreloadManager.Cache baselineCache) {
        if (tradeExecutionCoordinator == null || tradeExecutor == null) {
            return;
        }
        tradeExecutor.execute(() -> {
            TradeExecutionCoordinator.ExecutionResult executionResult =
                    tradeExecutionCoordinator.submitAfterConfirmation(preparedTrade, baselineCache);
            mainHandler.post(() -> handleTradeExecutionResult(executionResult));
        });
    }

    // 单笔交易结果统一提示并刷新账户页。
    private void handleTradeExecutionResult(@Nullable TradeExecutionCoordinator.ExecutionResult executionResult) {
        if (executionResult == null) {
            showTradeOutcome("结果未确认，请稍后刷新");
            return;
        }
        if (executionResult.getLatestCache() != null) {
            scheduleUiModelBuild(executionResult.getLatestCache());
        } else {
            refreshAccountTradeSnapshot();
        }
        showTradeOutcome(executionResult.getMessage());
    }

    private void showTradeOutcome(@Nullable String message) {
        String safeMessage = trimToEmpty(message);
        if (safeMessage.isEmpty()) {
            safeMessage = "交易结果未返回";
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(host.requireActivity())
                .setTitle("交易结果")
                .setMessage(safeMessage)
                .setPositiveButton("知道了", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(host.requireActivity()));
    }

    private void refreshAccountTradeSnapshot() {
        scheduleUiModelBuild(resolveCurrentSessionCache());
        requestForegroundEntrySnapshot();
    }

    private TradeExecutionCoordinator createTradeExecutionCoordinator() {
        TradeAuditStore auditStore = new TradeAuditStore(host.requireActivity());
        return new TradeExecutionCoordinator(
                new TradeExecutionCoordinator.TradeGateway() {
                    @Override
                    public com.binance.monitor.data.model.v2.trade.TradeCheckResult check(TradeCommand command) throws Exception {
                        return gatewayV2TradeClient.check(command);
                    }

                    @Override
                    public com.binance.monitor.data.model.v2.trade.TradeReceipt submit(TradeCommand command) throws Exception {
                        return gatewayV2TradeClient.submit(command);
                    }

                    @Override
                    public com.binance.monitor.data.model.v2.trade.TradeReceipt result(String requestId) throws Exception {
                        return gatewayV2TradeClient.result(requestId);
                    }
                },
                range -> preloadManager == null ? null : preloadManager.fetchFullForUi(range),
                new TradeConfirmDialogController(this::buildTradeRiskConfig),
                3,
                this::buildTradeRiskConfig,
                auditStore
        );
    }

    private BatchTradeCoordinator createBatchTradeCoordinator() {
        TradeAuditStore auditStore = new TradeAuditStore(host.requireActivity());
        return new BatchTradeCoordinator(
                new BatchTradeCoordinator.BatchTradeGateway() {
                    @Override
                    public com.binance.monitor.data.model.v2.trade.BatchTradeReceipt submit(com.binance.monitor.data.model.v2.trade.BatchTradePlan plan) throws Exception {
                        return gatewayV2TradeClient.submitBatch(plan);
                    }

                    @Override
                    public com.binance.monitor.data.model.v2.trade.BatchTradeReceipt result(String batchId) throws Exception {
                        return gatewayV2TradeClient.batchResult(batchId);
                    }
                },
                range -> preloadManager == null ? null : preloadManager.fetchFullForUi(range),
                2,
                this::buildTradeRiskConfig,
                auditStore
        );
    }

    @NonNull
    private TradeRiskGuard.Config buildTradeRiskConfig() {
        ConfigManager manager = ConfigManager.getInstance(host.getApplicationContext());
        return new TradeRiskGuard.Config(0.10d, 999d, 999, 999d, true, true, manager.isTradeOneClickModeEnabled());
    }

    @NonNull
    private String resolveCurrentAccountMode() {
        AccountStatsPreloadManager.Cache cache = resolveCurrentSessionCache();
        return cache == null ? "" : trimToEmpty(cache.getAccountMode());
    }

    @NonNull
    private String resolveTradeSymbol(@Nullable PositionItem item) {
        if (item == null) {
            return "";
        }
        String code = trimToEmpty(item.getCode());
        if (!code.isEmpty()) {
            return MarketChartTradeSupport.toTradeSymbol(code);
        }
        return MarketChartTradeSupport.toTradeSymbol(item.getProductName());
    }

    @NonNull
    private LinearLayout createTradeFormContainer() {
        LinearLayout container = new LinearLayout(host.requireActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(8);
        container.setPadding(padding, padding, padding, padding);
        return container;
    }

    @NonNull
    private TextInputLayout createTradeInputLayout(@NonNull String hint) {
        TextInputLayout layout = new TextInputLayout(host.requireActivity());
        layout.setHint(hint);
        UiPaletteManager.styleInputField(layout, UiPaletteManager.resolve(host.requireActivity()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(8);
        layout.setLayoutParams(params);
        return layout;
    }

    @NonNull
    private TextInputEditText createTradeInput() {
        TextInputEditText input = new TextInputEditText(host.requireActivity());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setSingleLine(true);
        return input;
    }

    private double parseTradeValue(@Nullable EditText input, double fallback) {
        double value = MarketChartTradeSupport.parseOptionalDouble(
                input == null || input.getText() == null ? "" : input.getText().toString(),
                fallback
        );
        return value <= 0d ? fallback : value;
    }

    private int dp(int value) {
        return Math.round(host.requireActivity().getResources().getDisplayMetrics().density * value);
    }

    // 账户持仓页也需要保证监控服务在线，避免只剩本地旧缓存而没有实时主链。
    private void ensureMonitorServiceStarted() {
        MonitorServiceController.ensureStarted(host.requireActivity());
    }

    // 注销后显式通知服务清掉账户运行态，避免旧仓位短暂回灌到页面。
    private void requestMonitorServiceAccountRuntimeClear() {
        MonitorServiceController.dispatch(host.requireActivity(), AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME);
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
