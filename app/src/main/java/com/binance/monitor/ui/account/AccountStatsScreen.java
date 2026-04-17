/*
 * 账户统计桥接页，负责历史分析、交易记录与统计图表展示。
 * 该页面只保留历史分析链路，并对接网关快照、历史缓存和本地筛选交互。
 */
package com.binance.monitor.ui.account;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfileDeduplicationHelper;
import com.binance.monitor.data.model.v2.session.SessionPublicKeyPayload;
import com.binance.monitor.data.model.v2.session.SessionReceipt;
import com.binance.monitor.data.model.v2.session.SessionStatusPayload;
import com.binance.monitor.data.remote.v2.GatewayV2SessionClient;
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.databinding.ActivityAccountStatsBinding;
import com.binance.monitor.databinding.ContentAccountStatsBinding;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionCredentialEncryptor;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.service.MonitorServiceController;
import com.binance.monitor.ui.account.history.AccountHistoryPayload;
import com.binance.monitor.ui.account.history.AccountHistorySnapshotStore;
import com.binance.monitor.ui.account.history.AccountStatsRenderSignature;
import com.binance.monitor.ui.account.history.AccountStatsSectionDiff;
import com.binance.monitor.ui.account.adapter.AccountMetricAdapter;
import com.binance.monitor.ui.account.session.AccountSessionRestoreHelper;
import com.binance.monitor.ui.account.adapter.StatsMetricAdapter;
import com.binance.monitor.ui.account.adapter.TradeRecordAdapterV2;
import com.binance.monitor.util.ProductSymbolMapper;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.main.MainActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.ChainLatencyTracer;
import com.binance.monitor.util.FormatUtils;
import com.binance.monitor.util.SensitiveDisplayMasker;
import com.binance.monitor.ui.widget.TradeScrollBarView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AccountStatsScreen extends android.view.ContextThemeWrapper {
    public static final String EXTRA_OPEN_LOGIN_DIALOG = "com.binance.monitor.ui.account.extra.OPEN_LOGIN_DIALOG";
    public static final String EXTRA_FINISH_AFTER_LOGIN_DIALOG = "com.binance.monitor.ui.account.extra.FINISH_AFTER_LOGIN_DIALOG";
    public static final String EXTRA_LOGIN_DIALOG_RESULT_MESSAGE = "com.binance.monitor.ui.account.extra.LOGIN_DIALOG_RESULT_MESSAGE";
    public static final String EXTRA_LOGIN_DIALOG_RESULT_ACCOUNT = "com.binance.monitor.ui.account.extra.LOGIN_DIALOG_RESULT_ACCOUNT";
    public static final String EXTRA_LOGIN_DIALOG_RESULT_SERVER = "com.binance.monitor.ui.account.extra.LOGIN_DIALOG_RESULT_SERVER";
    private static final double ACCOUNT_INITIAL_BALANCE = 15_019.45d;
    private static final double TRADE_PNL_ZERO_THRESHOLD = 0.01d;
    private static final int RETURNS_CELL_MARGIN_DP = 1;
    private static final int RETURNS_HEADER_HEIGHT_DP = 28;
    private static final int RETURNS_BODY_HEIGHT_DP = 42;
    private static final int RETURNS_MONTH_GROUP_HEIGHT_DP = 44;
    private static final int RETURNS_STAGE_HEIGHT_DP = 38;
    @Nullable
    private AlertDialog activeLoginDialog;
    private boolean pendingOpenLoginDialogFromIntent;
    private boolean finishAfterLoginDialog;
    private boolean loginDialogSubmissionInFlight;
    private boolean analysisTargetScrollCompleted;
    private String pendingAnalysisTargetSection = "";
    private static final String ACCOUNT = "";
    private static final String SERVER = "";

    private static final String FILTER_PRODUCT = "全部产品";
    private static final String FILTER_SIDE = "全部方向";
    private static final String FILTER_DATE = "全部日期";
    private static final String FILTER_SORT = "排序方式";
    private static final String SORT_CLOSE_TIME = "平仓时间";
    private static final String SORT_OPEN_TIME = "开仓时间";
    private static final String SORT_PROFIT = "盈利水平";
    private static final long ACCOUNT_REFRESH_MIN_MS = AppConstants.ACCOUNT_REFRESH_INTERVAL_MS;
    private static final long ACCOUNT_REFRESH_MAX_MS = AppConstants.ACCOUNT_REFRESH_MAX_INTERVAL_MS;
    private static final TimeZone BEIJING_TIME_ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    private static final String PREFS_UI_STATE = "account_stats_ui_state";
    private static final String PREF_RANGE = "pref_range";
    private static final String PREF_RETURN_MODE = "pref_return_mode";
    private static final String PREF_RETURN_VALUE_MODE = "pref_return_value_mode";
    private static final String PREF_TRADE_PNL_SIDE = "pref_trade_pnl_side";
    private static final String PREF_RETURN_ANCHOR = "pref_return_anchor";
    private static final String PREF_MANUAL_ENABLED = "pref_manual_enabled";
    private static final String PREF_MANUAL_START_MS = "pref_manual_start_ms";
    private static final String PREF_MANUAL_END_MS = "pref_manual_end_ms";
    private static final String PREF_MANUAL_START_TEXT = "pref_manual_start_text";
    private static final String PREF_MANUAL_END_TEXT = "pref_manual_end_text";
    private static final String PREF_FILTER_PRODUCT = "pref_filter_product";
    private static final String PREF_FILTER_SIDE = "pref_filter_side";
    private static final String PREF_FILTER_SORT = "pref_filter_sort";
    private static final String PREF_FILTER_SORT_DESC = "pref_filter_sort_desc";
    private static final String PREF_LOGIN_ENABLED = "pref_login_enabled";
    private static final String PREF_LOGIN_ACCOUNT = "pref_login_account";
    private static final String PREF_LOGIN_PASSWORD = "pref_login_password";
    private static final String PREF_LOGIN_SERVER = "pref_login_server";
    private static final int DATE_TARGET_START = 0;
    private static final int DATE_TARGET_END = 1;

    private enum ReturnStatsMode {
        DAY,
        MONTH,
        YEAR,
        STAGE
    }

    private enum ReturnValueMode {
        RATE,
        AMOUNT
    }

    private enum TradePnlSideMode {
        ALL,
        BUY,
        SELL
    }

    private enum TradeWeekdayBasis {
        CLOSE_TIME,
        OPEN_TIME
    }

    private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat monthTitleFormat = new SimpleDateFormat("yyyy年M月", Locale.getDefault());

    private ContentAccountStatsBinding binding;
    private AppCompatActivity activity;


    AccountStatsScreen(@NonNull AppCompatActivity activity,
                       @NonNull ContentAccountStatsBinding binding) {
        super(activity, activity.getTheme());
        this.activity = activity;
        this.binding = binding;
    }

    void initialize() {
        preloadManager = AccountStatsPreloadManager.getInstance(getApplicationContext());
        accountStorageRepository = new AccountStorageRepository(getApplicationContext());
        logManager = LogManager.getInstance(getApplicationContext());
        ioExecutor = Executors.newSingleThreadExecutor();
        sessionExecutor = Executors.newSingleThreadExecutor();
        sessionClient = new GatewayV2SessionClient(getApplicationContext());
        secureSessionPrefs = new SecureSessionPrefs(getApplicationContext());
        sessionCredentialEncryptor = new SessionCredentialEncryptor();
        sessionStateMachine = new AccountSessionStateMachine();
        remoteSessionCoordinator = buildRemoteSessionCoordinator();
        snapshotRefreshCoordinator = new AccountSnapshotRefreshCoordinator(createSnapshotRefreshHost());
        renderCoordinator = new AccountStatsRenderCoordinator(createRenderCoordinatorHost());
        indicatorAdapter = new AccountMetricAdapter();
        tradeAdapter = new TradeRecordAdapterV2();
        statsSummaryDetailAdapter = new StatsMetricAdapter();
        curveRenderHelper = new AccountStatsCurveRenderHelper(binding, indicatorAdapter);
        returnsTableHelper = createReturnsTableHelper();
        restoreUiState();
    }

    void bindPageContent() {
        initializePageContent();
    }

    @NonNull
    AppCompatActivity requireActivity() {
        return activity;
    }

    void attachPageRuntime(@NonNull AccountStatsPageRuntime runtime) {
        this.pageRuntime = runtime;
    }

    void setPendingAnalysisTargetSection(@NonNull String targetSection) {
        pendingAnalysisTargetSection = targetSection;
        analysisTargetScrollCompleted = false;
    }

    void onNewIntent(@NonNull Intent intent) {
        activity.setIntent(intent);
        consumeLoginDialogIntent(intent);
        openLoginDialogIfRequested();
    }

    void onDestroyView() {
        dismissActiveLoginDialog();
        clearTransientUiCallbacks();
        shutdownExecutors();
    }

    private void finishHost() {
        activity.finish();
    }

    private void overrideHostPendingTransition(int enterAnim, int exitAnim) {
        activity.overridePendingTransition(enterAnim, exitAnim);
    }

    private void setHostResult(int resultCode, @Nullable Intent data) {
        activity.setResult(resultCode, data);
    }

    private boolean isHostFinishing() {
        return activity.isFinishing();
    }

    private boolean isHostDestroyed() {
        return activity.isDestroyed();
    }

    private void runOnHostUiThread(@NonNull Runnable action) {
        activity.runOnUiThread(action);
    }

    private boolean isFinishing() {
        return activity.isFinishing();
    }

    private boolean isDestroyed() {
        return activity.isDestroyed();
    }

    private void runOnUiThread(@NonNull Runnable action) {
        activity.runOnUiThread(action);
    }
    private AccountStatsPreloadManager preloadManager;
    private AccountStorageRepository accountStorageRepository;
    private AccountMetricAdapter indicatorAdapter;
    private AccountStatsCurveRenderHelper curveRenderHelper;
    private AccountStatsReturnsTableHelper returnsTableHelper;
    private TradeRecordAdapterV2 tradeAdapter;
    private StatsMetricAdapter statsSummaryDetailAdapter;
    private LogManager logManager;
    private ExecutorService ioExecutor;
    private ExecutorService sessionExecutor;
    private GatewayV2SessionClient sessionClient;
    private SecureSessionPrefs secureSessionPrefs;
    private SessionCredentialEncryptor sessionCredentialEncryptor;
    private AccountSessionStateMachine sessionStateMachine;
    private AccountRemoteSessionCoordinator remoteSessionCoordinator;
    private AccountSnapshotRefreshCoordinator snapshotRefreshCoordinator;
    private AccountStatsRenderCoordinator renderCoordinator;
    private AccountStatsPageRuntime pageRuntime;
    private final AccountHistorySnapshotStore historySnapshotStore = new AccountHistorySnapshotStore();

    private final AccountSnapshotRequestGuard snapshotRequestGuard = new AccountSnapshotRequestGuard();
    private volatile boolean loading;
    private long connectedUpdateAtMs;

    private AccountTimeRange selectedRange = AccountTimeRange.D7;
    private ReturnStatsMode returnStatsMode = ReturnStatsMode.MONTH;
    private ReturnValueMode returnValueMode = ReturnValueMode.RATE;
    private TradePnlSideMode tradePnlSideMode = TradePnlSideMode.ALL;
    private TradeWeekdayBasis tradeWeekdayBasis = TradeWeekdayBasis.CLOSE_TIME;
    private long returnStatsAnchorDateMs;
    private String selectedTradeProductFilter = FILTER_PRODUCT;
    private String selectedTradeSideFilter = FILTER_SIDE;
    private String selectedTradeSortFilter = FILTER_SORT;
    private String lastExplicitTradeSortMode = SORT_CLOSE_TIME;
    private boolean tradeSortDescending = true;
    private double latestCumulativePnl;

    private boolean manualCurveRangeEnabled;
    private long manualCurveRangeStartMs;
    private long manualCurveRangeEndMs;
    private int manualDateTarget = DATE_TARGET_START;
    private final List<Integer> returnPickerYears = new ArrayList<>();
    private final List<Integer> returnPickerVisibleMonths = new ArrayList<>();
    private final Map<Integer, boolean[]> returnPickerYearMonthMap = new TreeMap<>();

    private List<PositionItem> basePositions = new ArrayList<>();
    private List<PositionItem> basePendingOrders = new ArrayList<>();
    private List<TradeRecordItem> baseTrades = new ArrayList<>();
    private List<CurvePoint> allCurvePoints = new ArrayList<>();
    private List<CurvePoint> displayedCurvePoints = new ArrayList<>();
    private List<CurveAnalyticsHelper.DrawdownPoint> displayedDrawdownPoints = new ArrayList<>();
    private List<CurveAnalyticsHelper.DailyReturnPoint> displayedDailyReturnPoints = new ArrayList<>();
    private List<AccountMetric> latestCurveIndicators = new ArrayList<>();
    private List<AccountMetric> latestStatsMetrics = new ArrayList<>();
    private List<AccountMetric> latestTradeStatsMetrics = new ArrayList<>();
    private String defaultCurveMeta = "--";
    private double curveBaseBalance = ACCOUNT_INITIAL_BALANCE;
    private boolean syncingCurveHighlight;
    private boolean statsSummaryExpanded;

    private String connectedAccount = ACCOUNT;
    private String connectedAccountName = ACCOUNT;
    private String connectedServer = SERVER;
    private String connectedSource = "历史数据（网关离线）";
    private String connectedGateway = "--";
    private String connectedUpdate = "--";
    private String connectedError = "";
    private String latestHistoryRevision = "";
    private String dataQualitySummary = "";
    private boolean userLoggedIn;
    private boolean gatewayConnected;
    private String loginAccountInput = ACCOUNT;
    private String loginServerInput = SERVER;
    private String sessionStorageError = "";
    private RemoteAccountProfile activeSessionAccount;
    private List<RemoteAccountProfile> savedSessionAccounts = new ArrayList<>();
    private final List<CurvePoint> curveHistory = new ArrayList<>();
    private final List<TradeRecordItem> tradeHistory = new ArrayList<>();
    private final Map<String, PositionLogState> lastPositionLogStates = new HashMap<>();
    private final Map<String, PendingLogState> lastPendingLogStates = new HashMap<>();
    private final Set<String> knownTradeOpenLogKeys = new HashSet<>();
    private final Set<String> knownTradeCloseLogKeys = new HashSet<>();
    private final Map<String, String> knownTradeStateByKey = new HashMap<>();
    private int lastHistoryRecordCount = 0;
    private Boolean lastTradePnlMismatchState;
    private boolean accountLogBaselineReady;
    private Boolean lastLoggedConnectedState;
    private String lastLoggedSource = "";
    private String lastLoggedGateway = "";
    private String lastLoggedError = "";
    private String lastTradeVisibilitySnapshotSignature = "";
    private String lastTradeFilterVisibilitySignature = "";
    private String lastAppliedSnapshotSignature = "";
    @Nullable
    private AccountStatsRenderSignature lastHistoryRenderSignature;
    @Nullable
    private AccountStatsRenderSignature pendingHistoryRenderSignature;
    @Nullable
    private AccountStatsSectionDiff pendingSectionDiff;
    private long dynamicRefreshDelayMs = ACCOUNT_REFRESH_MIN_MS;
    private int unchangedRefreshStreak = 0;
    private boolean draggingTradeScrollBar;
    private boolean secondarySectionsAttached;
    private boolean deferredSecondaryRenderPending;
    private boolean forceDeferredSectionRender;
    private boolean deferredSecondarySectionAttachPosted;
    private int deferredSecondaryRenderRevision;
    private boolean firstFrameCompleted;
    private boolean firstFrameCompletionPosted;
    private volatile boolean storedSnapshotRestorePending;
    private final Runnable hideLoginSuccessBannerRunnable = this::hideLoginSuccessBannerNow;
    private final Runnable deferredSecondarySectionAttachRunnable = () -> {
        deferredSecondarySectionAttachPosted = false;
        if (isHostFinishing() || isHostDestroyed() || binding == null) {
            return;
        }
        attachDeferredSecondarySections();
        if (secondarySectionsAttached && deferredSecondaryRenderPending && renderCoordinator != null) {
            renderCoordinator.renderDeferredSnapshotSections();
        }
    };
    @Nullable
    private ViewTreeObserver.OnDrawListener firstFrameDrawListener;
    private final AccountStatsPreloadManager.CacheListener preloadCacheListener = cache -> {
        if (cache == null || isHostFinishing() || isHostDestroyed() || loading) {
            return;
        }
        if (snapshotRefreshCoordinator != null) {
            snapshotRefreshCoordinator.applyPreloadedCacheIfAvailable();
        }
    };

    // 只用于承载登录弹窗的场景不应先把账户统计页画到前台。
    private boolean isLoginDialogOnlyMode(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }
        return intent.getBooleanExtra(EXTRA_OPEN_LOGIN_DIALOG, false)
                && intent.getBooleanExtra(EXTRA_FINISH_AFTER_LOGIN_DIALOG, false);
    }

    // 装配账户页快照刷新协调器，让刷新编排与页面渲染职责分开。
    private AccountSnapshotRefreshCoordinator.Host createSnapshotRefreshHost() {
        return new AccountSnapshotRefreshHostDelegate(new AccountSnapshotRefreshHostDelegate.Owner() {
            @Override
            public boolean isLoading() {
                return loading;
            }

            @Override
            public void setLoading(boolean value) {
                loading = value;
            }

            @Override
            public boolean isUserLoggedIn() {
                return userLoggedIn;
            }

            @Override
            public boolean isFinishingOrDestroyed() {
                return isHostFinishing() || isHostDestroyed();
            }

            @Override
            public boolean isAccountSessionReady() {
                return AccountStatsScreen.this.isAccountSessionReady();
            }

            @Override
            public AccountStatsPreloadManager.Cache resolveCurrentSessionCache() {
                return AccountStatsScreen.this.resolveCurrentSessionCache();
            }

            @Override
            public boolean isStoredSnapshotRestorePending() {
                return storedSnapshotRestorePending;
            }

            @Override
            public void setStoredSnapshotRestorePending(boolean pending) {
                storedSnapshotRestorePending = pending;
            }

            @Override
            public AccountStatsPreloadManager.Cache hydrateLatestCacheFromStorage() {
                return preloadManager == null ? null : preloadManager.hydrateLatestCacheFromStorage();
            }

            @Override
            public boolean isPreloadedCacheForCurrentSession(@Nullable AccountStatsPreloadManager.Cache cache) {
                return AccountStatsScreen.this.isPreloadedCacheForCurrentSession(cache);
            }

            @Override
            public void clearLatestCacheIfCurrent(@Nullable AccountStatsPreloadManager.Cache cache) {
                if (preloadManager != null && cache != null && cache == preloadManager.getLatestCache()) {
                    preloadManager.clearLatestCache();
                }
            }

            @Override
            public void applyLoggedOutEmptyState() {
                AccountStatsScreen.this.applyLoggedOutEmptyState();
            }

            @Override
            public void clearScheduledRefresh() {
                if (pageRuntime != null) {
                    pageRuntime.clearScheduledRefresh();
                }
            }

            @Override
            public void updateOverviewHeader() {
                AccountStatsScreen.this.updateOverviewHeader();
            }

            @Override
            public boolean hasRenderableCurrentSessionState() {
                return AccountStatsScreen.this.hasRenderableCurrentSessionState();
            }

            @Override
            public boolean hasRenderableHistorySections(@Nullable AccountSnapshot snapshot) {
                return AccountStatsScreen.this.hasRenderableHistorySections(snapshot);
            }

            @Override
            public boolean shouldKeepRefreshLoop() {
                return AccountStatsScreen.this.shouldKeepRefreshLoop();
            }

            @Override
            public long getDynamicRefreshDelayMs() {
                return dynamicRefreshDelayMs;
            }

            @Override
            public void scheduleNextSnapshot(long delayMs) {
                if (pageRuntime != null) {
                    pageRuntime.scheduleNextSnapshot(delayMs);
                }
            }

            @Override
            public boolean shouldBootstrapRemoteSession() {
                return AccountStatsScreen.this.shouldBootstrapRemoteSession();
            }

            @Override
            public void refreshRemoteSessionStatus(boolean requestSnapshotAfter) {
                AccountStatsScreen.this.refreshRemoteSessionStatus(requestSnapshotAfter);
            }

            @Override
            public void applyCacheMeta(@NonNull AccountStatsPreloadManager.Cache cache) {
                AccountStatsScreen.this.applyCacheMeta(cache);
            }

            @Override
            public void logConnectionEvent(boolean connected) {
                AccountStatsScreen.this.logConnectionEvent(connected);
            }

            @NonNull
            @Override
            public String buildRefreshSignature(@Nullable AccountSnapshot snapshot,
                                                @Nullable String historyRevision,
                                                boolean connected,
                                                @Nullable String account,
                                                @Nullable String server) {
                return AccountStatsScreen.this.buildRefreshSignature(
                        snapshot,
                        historyRevision,
                        connected,
                        account,
                        server
                );
            }

            @NonNull
            @Override
            public String getLastAppliedSnapshotSignature() {
                return lastAppliedSnapshotSignature;
            }

            @Override
            public void setLastAppliedSnapshotSignature(@NonNull String signature) {
                lastAppliedSnapshotSignature = signature;
            }

            @Override
            public boolean isOlderThanCurrentSnapshot(long incomingUpdatedAt) {
                return AccountStatsScreen.this.isOlderThanCurrentSnapshot(incomingUpdatedAt);
            }

            @Override
            public boolean isLoginCredentialMatched(@Nullable String remoteAccount, @Nullable String remoteServer) {
                return AccountStatsScreen.this.isLoginCredentialMatched(remoteAccount, remoteServer);
            }

            @NonNull
            @Override
            public AccountSnapshot buildEmptyAccountSnapshot() {
                return AccountStatsScreen.this.buildEmptyAccountSnapshot();
            }

            @NonNull
            @Override
            public String normalizeSource(@Nullable String source) {
                return AccountStatsScreen.this.normalizeSource(source);
            }

            @NonNull
            @Override
            public String getLoginAccountInput() {
                return loginAccountInput;
            }

            @NonNull
            @Override
            public String getLoginServerInput() {
                return loginServerInput;
            }

            @NonNull
            @Override
            public String getDefaultAccount() {
                return ACCOUNT;
            }

            @NonNull
            @Override
            public String getDefaultServer() {
                return SERVER;
            }

            @Override
            public void applyConnectedMeta(boolean connected,
                                           @NonNull String account,
                                           @NonNull String accountName,
                                           @NonNull String server,
                                           @NonNull String source,
                                           @NonNull String gateway,
                                           long updatedAt,
                                           @NonNull String error) {
                connectedAccount = account;
                connectedAccountName = accountName;
                connectedServer = server;
                connectedSource = source;
                connectedGateway = gateway;
                connectedUpdateAtMs = updatedAt;
                connectedUpdate = FormatUtils.formatDateTime(updatedAt);
                connectedError = error;
            }

            @Override
            public void setConnectionStatus(boolean connected) {
                AccountStatsScreen.this.setConnectionStatus(connected);
            }

            @Override
            public boolean onSnapshotApplied(@NonNull String account, @NonNull String server) {
                boolean activated = remoteSessionCoordinator != null
                        && remoteSessionCoordinator.onSnapshotApplied(account, server);
                if (activated) {
                    ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(true);
                }
                return activated;
            }

            @Override
            public boolean isAwaitingSync() {
                return remoteSessionCoordinator != null && remoteSessionCoordinator.isAwaitingSync();
            }

            @Override
            public void markSyncFailed(@NonNull String message) {
                if (remoteSessionCoordinator != null) {
                    remoteSessionCoordinator.markSyncFailed(message);
                }
            }

            @Override
            public void markAwaitingGatewaySync(@NonNull String message) {
                if (remoteSessionCoordinator != null) {
                    remoteSessionCoordinator.markAwaitingGatewaySync(message);
                }
            }

            @Override
            public void showLoginSuccessBanner() {
                AccountStatsScreen.this.showLoginSuccessBanner();
            }

            @Override
            public void applySnapshot(@NonNull AccountSnapshot snapshot, boolean remoteConnected) {
                if (renderCoordinator != null) {
                    renderCoordinator.applySnapshot(snapshot, remoteConnected);
                }
                updateEmptyStateVisibility();
            }

            @Override
            public boolean shouldApplyFetchedSnapshot(@Nullable AccountSnapshot snapshot,
                                                      boolean remoteConnected,
                                                      boolean syntheticDisconnectedSnapshot,
                                                      @Nullable String incomingHistoryRevision,
                                                      @Nullable String requestStartHistoryRevision) {
                return AccountStatsScreen.this.shouldApplyFetchedSnapshot(
                        snapshot,
                        remoteConnected,
                        syntheticDisconnectedSnapshot,
                        incomingHistoryRevision,
                        requestStartHistoryRevision
                );
            }

            @Override
            public void adjustRefreshCadence(boolean connected, boolean unchanged) {
                AccountStatsScreen.this.adjustRefreshCadence(connected, unchanged);
            }

            @Override
            public AccountStatsPreloadManager.Cache fetchForUi(@NonNull AccountTimeRange range) {
                return preloadManager == null ? null : preloadManager.fetchForUi(range);
            }

            @Override
            public void executeIo(@NonNull Runnable action) {
                if (ioExecutor != null) {
                    ioExecutor.execute(action);
                }
            }

            @Override
            public void runOnUiThread(@NonNull Runnable action) {
                AccountStatsScreen.this.runOnUiThreadIfAlive(action);
            }

            @Override
            public void logWarning(@NonNull String message) {
                if (logManager != null) {
                    logManager.warn(message);
                }
            }
        });
    }

    // 装配账户统计渲染协调器，把快照落地和次级区块主链从旧 Activity 抽离。
    private AccountStatsRenderCoordinator.Host createRenderCoordinatorHost() {
        return new AccountStatsRenderHostDelegate(new AccountStatsRenderHostDelegate.Owner() {
            @Override
            public void replaceTradeHistory(@Nullable List<TradeRecordItem> source) {
                AccountStatsScreen.this.replaceTradeHistory(source);
            }

            @Override
            public void replaceCurveHistory(@Nullable List<CurvePoint> source) {
                AccountStatsScreen.this.replaceCurveHistory(source);
            }

            @Override
            public void setLatestCurveIndicators(@Nullable List<AccountMetric> indicators) {
                latestCurveIndicators = indicators == null ? new ArrayList<>() : new ArrayList<>(indicators);
            }

            @Override
            public void setLatestStatsMetrics(@Nullable List<AccountMetric> metrics) {
                latestStatsMetrics = metrics == null ? new ArrayList<>() : new ArrayList<>(metrics);
            }

            @Override
            public void setBasePositions(@Nullable List<PositionItem> positions) {
                basePositions = positions == null ? new ArrayList<>() : new ArrayList<>(positions);
            }

            @Override
            public void setBasePendingOrders(@Nullable List<PositionItem> pendingOrders) {
                basePendingOrders = pendingOrders == null ? new ArrayList<>() : new ArrayList<>(pendingOrders);
            }

            @NonNull
            @Override
            public List<TradeRecordItem> getTradeHistory() {
                return tradeHistory;
            }

            @NonNull
            @Override
            public List<CurvePoint> getCurveHistory() {
                return curveHistory;
            }

            @NonNull
            @Override
            public String buildDataQualitySummary(@Nullable List<TradeRecordItem> trades,
                                                  @Nullable List<CurvePoint> curves,
                                                  @Nullable List<PositionItem> positions) {
                return AccountStatsScreen.this.buildDataQualitySummary(trades, curves, positions);
            }

            @Override
            public void setDataQualitySummary(@NonNull String summary) {
                dataQualitySummary = summary;
            }

            @Override
            public long resolveCloseTime(@Nullable TradeRecordItem item) {
                return AccountStatsScreen.this.resolveCloseTime(item);
            }

            @Override
            public void setBaseTrades(@Nullable List<TradeRecordItem> trades) {
                baseTrades = trades == null ? new ArrayList<>() : new ArrayList<>(trades);
            }

            @NonNull
            @Override
            public List<TradeRecordItem> getBaseTrades() {
                return baseTrades;
            }

            @NonNull
            @Override
            public List<PositionItem> getBasePositions() {
                return basePositions;
            }

            @NonNull
            @Override
            public List<CurvePoint> normalizeCurvePoints(@Nullable List<CurvePoint> source) {
                return AccountStatsScreen.this.normalizeCurvePoints(source);
            }

            @Override
            public void setAllCurvePoints(@Nullable List<CurvePoint> points) {
                allCurvePoints = points == null ? new ArrayList<>() : new ArrayList<>(points);
            }

            @NonNull
            @Override
            public List<CurvePoint> getAllCurvePoints() {
                return allCurvePoints;
            }

            @Override
            public void setLatestCumulativePnl(double cumulativePnl) {
                latestCumulativePnl = cumulativePnl;
            }

            @NonNull
            @Override
            public List<CurvePoint> resolveImmediateCurvePoints() {
                return AccountStatsScreen.this.resolveImmediateCurvePoints();
            }

            @Override
            public void renderCurveWithIndicators(@NonNull List<CurvePoint> points) {
                AccountStatsScreen.this.renderCurveWithIndicators(points);
            }

            @Override
            public void logTradeVisibilitySnapshot(@Nullable List<TradeRecordItem> snapshotTrades,
                                                   @Nullable List<TradeRecordItem> effectiveTrades,
                                                   @Nullable List<TradeRecordItem> baseTrades) {
                AccountStatsScreen.this.logTradeVisibilitySnapshot(snapshotTrades, effectiveTrades, baseTrades);
            }

            @Override
            public void logAccountSnapshotEvents(@Nullable List<PositionItem> positions,
                                                 @Nullable List<PositionItem> pendingOrders,
                                                 @Nullable List<TradeRecordItem> trades,
                                                 boolean remoteConnected) {
                AccountStatsScreen.this.logAccountSnapshotEvents(positions, pendingOrders, trades, remoteConnected);
            }

            @Override
            public void ensureReturnStatsAnchor() {
                AccountStatsScreen.this.ensureReturnStatsAnchor();
            }

            @Override
            public void updateOverviewHeader() {
                AccountStatsScreen.this.updateOverviewHeader();
            }

            @Override
            public void setDeferredSecondaryRenderPending(boolean pending) {
                deferredSecondaryRenderPending = pending;
            }

            @Override
            public boolean isSecondarySectionsAttached() {
                return secondarySectionsAttached;
            }

            @Override
            public void scheduleDeferredSecondarySectionAttach() {
                AccountStatsScreen.this.scheduleDeferredSecondarySectionAttach();
            }

            @Override
            public void traceAccountRenderPhase(@NonNull String phase,
                                                long stageStartedAt,
                                                int tradeCount,
                                                int positionCount,
                                                int curveCount) {
                AccountStatsScreen.this.traceAccountRenderPhase(
                        phase,
                        stageStartedAt,
                        tradeCount,
                        positionCount,
                        curveCount
                );
            }

            @NonNull
            @Override
            public AccountStatsRenderSignature buildCurrentHistoryRenderSignature() {
                return AccountStatsScreen.this.buildCurrentHistoryRenderSignature();
            }

            @Override
            public boolean isForceDeferredSectionRender() {
                return forceDeferredSectionRender;
            }

            @Override
            public void setForceDeferredSectionRender(boolean value) {
                forceDeferredSectionRender = value;
            }

            @Nullable
            @Override
            public AccountStatsRenderSignature getLastHistoryRenderSignature() {
                return lastHistoryRenderSignature;
            }

            @Override
            public void setPendingHistoryRenderSignature(@Nullable AccountStatsRenderSignature signature) {
                pendingHistoryRenderSignature = signature;
            }

            @Override
            public void setPendingSectionDiff(@Nullable AccountStatsSectionDiff diff) {
                pendingSectionDiff = diff;
            }

            @Override
            public void setLastHistoryRenderSignature(@Nullable AccountStatsRenderSignature signature) {
                lastHistoryRenderSignature = signature;
            }

            @Override
            public void hideTradeStatsSectionUntilFreshContentReady() {
                AccountStatsScreen.this.hideTradeStatsSectionUntilFreshContentReady();
            }

            @Override
            public int nextDeferredSecondaryRenderRevision() {
                return ++deferredSecondaryRenderRevision;
            }

            @Override
            public boolean canExecuteDeferredSecondarySectionRender() {
                return binding != null && ioExecutor != null && !ioExecutor.isShutdown();
            }

            @Override
            public void executeDeferredSecondaryRender(@NonNull Runnable action) {
                ioExecutor.execute(action);
            }

            @Override
            public void runOnUiThread(@NonNull Runnable action) {
                AccountStatsScreen.this.runOnHostUiThread(action);
            }

            @Override
            public boolean shouldIgnoreDeferredSecondaryRenderResult(int renderRevision) {
                return isFinishing()
                        || isDestroyed()
                        || binding == null
                        || renderRevision != deferredSecondaryRenderRevision;
            }

            @Override
            public void logRenderWarning(@NonNull String message) {
                if (logManager != null) {
                    logManager.warn(message);
                }
            }

            @Nullable
            @Override
            public AccountStatsSectionDiff getPendingSectionDiff() {
                return pendingSectionDiff;
            }

            @Nullable
            @Override
            public AccountStatsRenderSignature getPendingHistoryRenderSignature() {
                return pendingHistoryRenderSignature;
            }

            @NonNull
            @Override
            public List<AccountMetric> getLatestStatsMetrics() {
                return latestStatsMetrics;
            }

            @NonNull
            @Override
            public AccountTimeRange getSelectedRange() {
                return selectedRange;
            }

            @Override
            public boolean isManualCurveRangeEnabled() {
                return manualCurveRangeEnabled;
            }

            @Override
            public long getManualCurveRangeStartMs() {
                return manualCurveRangeStartMs;
            }

            @Override
            public long getManualCurveRangeEndMs() {
                return manualCurveRangeEndMs;
            }

            @NonNull
            @Override
            public AccountDeferredSnapshotRenderHelper.TradePnlSideMode getTradePnlSideMode() {
                return AccountStatsScreen.this.toHelperTradePnlSideMode(tradePnlSideMode);
            }

            @NonNull
            @Override
            public AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis getTradeWeekdayBasis() {
                return AccountStatsScreen.this.toHelperTradeWeekdayBasis(tradeWeekdayBasis);
            }

            @Override
            public void bindTradeAnalytics(@Nullable List<AccountMetric> tradeStatsMetrics,
                                           @Nullable List<TradePnlBarChartView.Entry> entries,
                                           @Nullable List<CurveAnalyticsHelper.TradeScatterPoint> tradeScatterPoints,
                                           @Nullable List<CurveAnalyticsHelper.DurationBucket> holdingDurationBuckets,
                                           @Nullable List<TradeWeekdayBarChartHelper.Entry> weekdayEntries,
                                           double totalPnl) {
                AccountStatsScreen.this.bindTradeAnalytics(
                        tradeStatsMetrics,
                        entries,
                        tradeScatterPoints,
                        holdingDurationBuckets,
                        weekdayEntries,
                        totalPnl
                );
            }

            @Override
            public void collapseAllExpandedRows() {
                if (tradeAdapter != null) {
                    tradeAdapter.collapseAllExpandedRows();
                }
            }

            @NonNull
            @Override
            public String readTradeProductFilter() {
                String product = binding.spinnerTradeProduct == null
                        ? FILTER_PRODUCT
                        : (String) binding.spinnerTradeProduct.getSelectedItem();
                return product == null || product.trim().isEmpty() ? FILTER_PRODUCT : product;
            }

            @NonNull
            @Override
            public String readTradeSideFilter() {
                String side = binding.spinnerTradeSide == null
                        ? FILTER_SIDE
                        : (String) binding.spinnerTradeSide.getSelectedItem();
                return side == null || side.trim().isEmpty() ? FILTER_SIDE : side;
            }

            @NonNull
            @Override
            public String readTradeSortFilter() {
                String sort = binding.spinnerTradeSort == null
                        ? FILTER_SORT
                        : (String) binding.spinnerTradeSort.getSelectedItem();
                return sort == null || sort.trim().isEmpty() ? FILTER_SORT : sort;
            }

            @Override
            public void setSelectedTradeProductFilter(@NonNull String filter) {
                selectedTradeProductFilter = filter;
            }

            @Override
            public void setSelectedTradeSideFilter(@NonNull String filter) {
                selectedTradeSideFilter = filter;
            }

            @Override
            public void setSelectedTradeSortFilter(@NonNull String filter) {
                selectedTradeSortFilter = filter;
            }

            @NonNull
            @Override
            public String getTradeProductFilterLabel() {
                return FILTER_PRODUCT;
            }

            @NonNull
            @Override
            public String getTradeSideFilterLabel() {
                return FILTER_SIDE;
            }

            @NonNull
            @Override
            public String getTradeSortFilterLabel() {
                return FILTER_SORT;
            }

            @NonNull
            @Override
            public String getSelectedTradeProductFilterValue() {
                return selectedTradeProductFilter == null ? "" : selectedTradeProductFilter;
            }

            @NonNull
            @Override
            public String getSelectedTradeSideFilterValue() {
                return selectedTradeSideFilter == null ? "" : selectedTradeSideFilter;
            }

            @NonNull
            @Override
            public String getSelectedTradeSortFilterValue() {
                return selectedTradeSortFilter == null ? "" : selectedTradeSortFilter;
            }

            @Override
            public void updateTradeFilterDisplayTexts(@NonNull String product,
                                                      @NonNull String side,
                                                      @NonNull String sort) {
                AccountStatsScreen.this.updateTradeFilterDisplayTexts(product, side, sort);
            }

            @Override
            public void updateTradeProductOptions(@Nullable List<String> products,
                                                  @NonNull String selectedProduct) {
                AccountStatsScreen.this.updateTradeProductOptions(products, selectedProduct);
            }

            @Override
            public void renderReturnStatsTable(@NonNull List<CurvePoint> curvePoints) {
                AccountStatsScreen.this.renderReturnStatsTable(curvePoints);
            }

            @Override
            public void setManualCurveRangeEnabled(boolean enabled) {
                manualCurveRangeEnabled = enabled;
            }

            @Override
            public void syncRangeInputsWithDisplayedCurve(@Nullable List<CurvePoint> displayedPoints) {
                AccountStatsScreen.this.syncRangeInputsWithDisplayedCurve(displayedPoints);
            }

            @Override
            public void applyPreparedCurveProjection(@NonNull AccountDeferredSnapshotRenderHelper.CurveProjection curveProjection) {
                AccountStatsScreen.this.applyPreparedCurveProjection(curveProjection);
            }

            @Override
            public int getDisplayedCurvePointCount() {
                return displayedCurvePoints == null ? 0 : displayedCurvePoints.size();
            }

            @NonNull
            @Override
            public String getLastExplicitTradeSortMode() {
                return lastExplicitTradeSortMode;
            }

            @NonNull
            @Override
            public String normalizeSortValue(@Nullable String rawSort) {
                return AccountStatsScreen.this.normalizeSortValue(rawSort);
            }

            @Override
            public boolean isTradeSortDescending() {
                return tradeSortDescending;
            }

            @NonNull
            @Override
            public AccountDeferredSnapshotRenderHelper.SortMode toHelperSortMode(@NonNull String normalizedSort) {
                return AccountStatsScreen.this.toHelperSortMode(normalizedSort);
            }

            @Override
            public void bindFilteredTrades(@Nullable List<TradeRecordItem> filtered,
                                           @NonNull AccountDeferredSnapshotRenderHelper.TradeSummary tradeSummary,
                                           boolean scrollToTop,
                                           @NonNull String product,
                                           @NonNull String side,
                                           @NonNull String normalizedSort) {
                AccountStatsScreen.this.bindFilteredTrades(
                        filtered,
                        tradeSummary,
                        scrollToTop,
                        product,
                        side,
                        normalizedSort
                );
            }
        });
    }

    // 装配收益表渲染助手，把收益表主链从旧 Activity 中抽离。
    private AccountStatsReturnsTableHelper createReturnsTableHelper() {
        return new AccountStatsReturnsTableHelper(new AccountStatsReturnsTableHelper.Host() {
            @NonNull
            @Override
            public ContentAccountStatsBinding getBinding() {
                return binding;
            }

            @Override
            public boolean isPrivacyMasked() {
                return AccountStatsScreen.this.isPrivacyMasked();
            }

            @Override
            public long resolveCloseTime(@Nullable TradeRecordItem item) {
                return AccountStatsScreen.this.resolveCloseTime(item);
            }

            @NonNull
            @Override
            public String formatMonthLabel(long timeMs) {
                return AccountStatsScreen.this.formatMonthLabel(timeMs);
            }

            @Override
            public int resolveReturnDisplayColor(double rate, double amount, int neutralColorRes) {
                return AccountStatsScreen.this.resolveReturnDisplayColor(rate, amount, neutralColorRes);
            }

            @NonNull
            @Override
            public String formatReturnValue(double rate, double amount, boolean dayMode) {
                return AccountStatsScreen.this.formatReturnValue(rate, amount, dayMode);
            }

            @Override
            public void applyCurveRangeFromTableSelection(long startMs, long endMs) {
                AccountStatsScreen.this.applyCurveRangeFromTableSelection(startMs, endMs);
            }

            @Override
            public long startOfDay(long timeMs) {
                return AccountStatsScreen.this.startOfDay(timeMs);
            }

            @Override
            public long endOfDay(long timeMs) {
                return AccountStatsScreen.this.endOfDay(timeMs);
            }

            @Override
            public long startOfMonth(long timeMs) {
                return AccountStatsScreen.this.startOfMonth(timeMs);
            }

            @Override
            public long endOfMonth(long timeMs) {
                return AccountStatsScreen.this.endOfMonth(timeMs);
            }

            @Override
            public long startOfYear(long timeMs) {
                return AccountStatsScreen.this.startOfYear(timeMs);
            }

            @NonNull
            @Override
            public List<CurvePoint> filterCurveByManualRange(@Nullable List<CurvePoint> source, long startInclusive, long endInclusive) {
                return AccountStatsScreen.this.filterCurveByManualRange(source, startInclusive, endInclusive);
            }

            @Override
            public void applyReturnsCellLayout(@NonNull View cell, int widthDp, float weight, int heightDp, int marginLeftDp, int marginTopDp, int marginRightDp, int marginBottomDp) {
                AccountStatsScreen.this.applyReturnsCellLayout(cell, widthDp, weight, heightDp, marginLeftDp, marginTopDp, marginRightDp, marginBottomDp);
            }

            @NonNull
            @Override
            public TableRow createSimpleHeaderRow(@NonNull String[] headers, int widthDp) {
                return AccountStatsScreen.this.createSimpleHeaderRow(headers, widthDp);
            }

            @NonNull
            @Override
            public TableRow createAlignedReturnsRow(@NonNull CharSequence label, @NonNull CharSequence value, boolean header, @Nullable Integer valueColor, @Nullable Double heatRate) {
                return AccountStatsScreen.this.createAlignedReturnsRow(label, value, header, valueColor, heatRate);
            }

            @NonNull
            @Override
            public TableRow createAlignedReturnsRow(@NonNull CharSequence label, @NonNull CharSequence value, boolean header, @Nullable Integer valueColor, @Nullable Double heatRate, @Nullable View.OnClickListener clickListener) {
                return AccountStatsScreen.this.createAlignedReturnsRow(label, value, header, valueColor, heatRate, clickListener);
            }

            @NonNull
            @Override
            public View createDailyReturnsCell(@NonNull String label, @Nullable String value, int labelColor, @Nullable Integer valueColor, @Nullable View.OnClickListener clickListener, @Nullable Double heatRate) {
                return AccountStatsScreen.this.createDailyReturnsCell(label, value, labelColor, valueColor, clickListener, heatRate);
            }

            @NonNull
            @Override
            public LinearLayout createMonthlyGroupedBlock(@NonNull AccountStatsReturnsTableHelper.YearlyReturnRow rowData) {
                return AccountStatsScreen.this.createMonthlyGroupedBlock(rowData);
            }
        });
    }

    void attachForegroundRefresh() {
        if (preloadManager != null) {
            preloadManager.addCacheListener(preloadCacheListener);
            preloadManager.setLiveScreenActive(true);
        }
    }

    void applyPagePalette() {
        applyPaletteStyles();
    }

    void enterAccountScreen(boolean coldStart) {
        if (snapshotRefreshCoordinator != null) {
            snapshotRefreshCoordinator.enterAccountScreen(coldStart);
        }
    }

    void clearTransientUiCallbacks() {
        binding.tvLoginSuccessBanner.removeCallbacks(hideLoginSuccessBannerRunnable);
        binding.scrollAccountStats.removeCallbacks(deferredSecondarySectionAttachRunnable);
        deferredSecondarySectionAttachPosted = false;
        clearFirstFrameCompletionListener();
        hideLoginSuccessBannerNow();
    }

    void detachForegroundRefresh() {
        if (preloadManager != null) {
            preloadManager.removeCacheListener(preloadCacheListener);
            preloadManager.setLiveScreenActive(false);
        }
    }

    void clearDestroyCallbacks() {
        clearTransientUiCallbacks();
    }

    void requestScheduledSnapshot() {
        if (snapshotRefreshCoordinator != null) {
            snapshotRefreshCoordinator.requestSnapshot();
        }
    }

    private void setupOverviewHeader() {
    }

    private void restoreUiState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_UI_STATE, MODE_PRIVATE);
        selectedRange = safeEnumValue(AccountTimeRange.class,
                prefs.getString(PREF_RANGE, selectedRange.name()),
                AccountTimeRange.D7);
        returnStatsMode = safeEnumValue(ReturnStatsMode.class,
                prefs.getString(PREF_RETURN_MODE, returnStatsMode.name()),
                ReturnStatsMode.MONTH);
        returnValueMode = safeEnumValue(ReturnValueMode.class,
                prefs.getString(PREF_RETURN_VALUE_MODE, returnValueMode.name()),
                ReturnValueMode.RATE);
        tradePnlSideMode = safeEnumValue(TradePnlSideMode.class,
                prefs.getString(PREF_TRADE_PNL_SIDE, tradePnlSideMode.name()),
                TradePnlSideMode.ALL);
        returnStatsAnchorDateMs = prefs.getLong(PREF_RETURN_ANCHOR, 0L);

        manualCurveRangeEnabled = prefs.getBoolean(PREF_MANUAL_ENABLED, false);
        manualCurveRangeStartMs = prefs.getLong(PREF_MANUAL_START_MS, 0L);
        manualCurveRangeEndMs = prefs.getLong(PREF_MANUAL_END_MS, 0L);
        String manualStartText = prefs.getString(PREF_MANUAL_START_TEXT, "");
        String manualEndText = prefs.getString(PREF_MANUAL_END_TEXT, "");
        if (binding != null) {
            if (manualStartText == null || manualStartText.trim().isEmpty()) {
                if (manualCurveRangeStartMs > 0L) {
                    binding.etRangeStart.setText(dateOnlyFormat.format(new Date(manualCurveRangeStartMs)));
                }
            } else {
                binding.etRangeStart.setText(manualStartText);
            }
            if (manualEndText == null || manualEndText.trim().isEmpty()) {
                if (manualCurveRangeEndMs > 0L) {
                    binding.etRangeEnd.setText(dateOnlyFormat.format(new Date(manualCurveRangeEndMs)));
                }
            } else {
                binding.etRangeEnd.setText(manualEndText);
            }
        }
        if (manualCurveRangeEnabled && (manualCurveRangeStartMs <= 0L || manualCurveRangeEndMs <= 0L)) {
            try {
                Date start = manualStartText == null ? null : dateOnlyFormat.parse(manualStartText);
                Date end = manualEndText == null ? null : dateOnlyFormat.parse(manualEndText);
                if (start != null && end != null) {
                    manualCurveRangeStartMs = start.getTime();
                    manualCurveRangeEndMs = end.getTime() + 24L * 60L * 60L * 1000L - 1L;
                }
            } catch (Exception ignored) {
            }
        }

        selectedTradeProductFilter = prefs.getString(PREF_FILTER_PRODUCT, FILTER_PRODUCT);
        selectedTradeSideFilter = prefs.getString(PREF_FILTER_SIDE, FILTER_SIDE);
        selectedTradeSortFilter = prefs.getString(PREF_FILTER_SORT, FILTER_SORT);
        tradeSortDescending = prefs.getBoolean(PREF_FILTER_SORT_DESC, true);
        boolean persistedLoginEnabled = prefs.getBoolean(PREF_LOGIN_ENABLED, false);
        boolean sessionActive = ConfigManager.getInstance(getApplicationContext()).isAccountSessionActive();
        SessionSummarySnapshot sessionSummary = secureSessionPrefs == null
                ? SessionSummarySnapshot.empty()
                : secureSessionPrefs.loadSessionSummary();
        AccountSessionRestoreHelper.RestoreResult restoredSession = AccountSessionRestoreHelper.restore(
                new AccountSessionRestoreHelper.RestoreRequest(
                        sessionSummary,
                        persistedLoginEnabled,
                        sessionActive,
                        prefs.getString(PREF_LOGIN_ACCOUNT, ACCOUNT),
                        prefs.getString(PREF_LOGIN_SERVER, SERVER),
                        ACCOUNT,
                        SERVER
                )
        );
        userLoggedIn = restoredSession.isUserLoggedIn();
        activeSessionAccount = restoredSession.getActiveSessionAccount();
        savedSessionAccounts = restoredSession.getSavedSessionAccounts();
        loginAccountInput = restoredSession.getLoginAccountInput();
        loginServerInput = restoredSession.getLoginServerInput();
        sessionStorageError = restoredSession.getStorageError();
        if (!sessionStorageError.isEmpty() && logManager != null) {
            logManager.warn(sessionStorageError);
        }

        if (selectedTradeProductFilter == null || selectedTradeProductFilter.trim().isEmpty()) {
            selectedTradeProductFilter = FILTER_PRODUCT;
        }
        if (selectedTradeSideFilter == null || selectedTradeSideFilter.trim().isEmpty()) {
            selectedTradeSideFilter = FILTER_SIDE;
        }
        if (selectedTradeSortFilter == null || selectedTradeSortFilter.trim().isEmpty()) {
            selectedTradeSortFilter = FILTER_SORT;
        }
        lastExplicitTradeSortMode = FILTER_SORT.equals(selectedTradeSortFilter)
                ? SORT_CLOSE_TIME
                : normalizeSortValue(selectedTradeSortFilter);
        ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(userLoggedIn);
    }

    void persistUiState() {
        if (binding == null) {
            return;
        }
        selectedTradeProductFilter = safeSpinnerValue(binding.spinnerTradeProduct, selectedTradeProductFilter, FILTER_PRODUCT);
        selectedTradeSideFilter = safeSpinnerValue(binding.spinnerTradeSide, selectedTradeSideFilter, FILTER_SIDE);
        selectedTradeSortFilter = safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT);

        String manualStartText = trim(binding.etRangeStart.getText() == null
                ? ""
                : binding.etRangeStart.getText().toString());
        String manualEndText = trim(binding.etRangeEnd.getText() == null
                ? ""
                : binding.etRangeEnd.getText().toString());

        SharedPreferences.Editor editor = getSharedPreferences(PREFS_UI_STATE, MODE_PRIVATE).edit();
        editor.putString(PREF_RANGE, selectedRange.name());
        editor.putString(PREF_RETURN_MODE, returnStatsMode.name());
        editor.putString(PREF_RETURN_VALUE_MODE, returnValueMode.name());
        editor.putString(PREF_TRADE_PNL_SIDE, tradePnlSideMode.name());
        editor.putLong(PREF_RETURN_ANCHOR, returnStatsAnchorDateMs);
        editor.putBoolean(PREF_MANUAL_ENABLED, manualCurveRangeEnabled);
        editor.putLong(PREF_MANUAL_START_MS, manualCurveRangeStartMs);
        editor.putLong(PREF_MANUAL_END_MS, manualCurveRangeEndMs);
        editor.putString(PREF_MANUAL_START_TEXT, manualStartText);
        editor.putString(PREF_MANUAL_END_TEXT, manualEndText);
        editor.putString(PREF_FILTER_PRODUCT, selectedTradeProductFilter);
        editor.putString(PREF_FILTER_SIDE, selectedTradeSideFilter);
        editor.putString(PREF_FILTER_SORT, selectedTradeSortFilter);
        editor.putBoolean(PREF_FILTER_SORT_DESC, tradeSortDescending);
        editor.putBoolean(PREF_LOGIN_ENABLED, userLoggedIn);
        editor.putString(PREF_LOGIN_ACCOUNT, trim(loginAccountInput).isEmpty() ? ACCOUNT : trim(loginAccountInput));
        editor.putString(PREF_LOGIN_SERVER, trim(loginServerInput).isEmpty() ? SERVER : trim(loginServerInput));
        editor.remove(PREF_LOGIN_PASSWORD);
        editor.apply();
        if (secureSessionPrefs != null) {
            secureSessionPrefs.saveDraftIdentity(loginAccountInput, loginServerInput);
            refreshSessionStorageErrorFromPrefs();
        }
    }

    private String safeSpinnerValue(Spinner spinner, String fallback, String defaultValue) {
        if (spinner == null) {
            return fallback == null || fallback.trim().isEmpty() ? defaultValue : fallback;
        }
        Object selected = spinner.getSelectedItem();
        String value = selected == null ? "" : selected.toString();
        if (value == null || value.trim().isEmpty()) {
            value = fallback;
        }
        if (value == null || value.trim().isEmpty()) {
            value = defaultValue;
        }
        return value;
    }

    private <T extends Enum<T>> T safeEnumValue(Class<T> enumClass, @Nullable String stored, T fallback) {
        if (stored == null || stored.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Enum.valueOf(enumClass, stored);
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private void showConnectionDialog() {
        boolean masked = isPrivacyMasked();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.card, palette.stroke));
        content.setPadding(dpToPx(18), dpToPx(14), dpToPx(18), dpToPx(6));

        TextView titleView = new TextView(this);
        titleView.setText("账户连接详情");
        titleView.setTextColor(palette.textPrimary);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        content.addView(titleView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText("当前账户连接状态");
        subtitleView.setTextColor(palette.textSecondary);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dpToPx(4);
        content.addView(subtitleView, subtitleParams);

        content.addView(createConnectionDetailRow("会话状态",
                AccountStatsPrivacyFormatter.maskValue(describeSessionState(), masked), palette));
        content.addView(createConnectionDetailRow("账号信息",
                AccountStatsPrivacyFormatter.maskValue(connectedAccount, masked), palette));
        content.addView(createConnectionDetailRow("服务器信息",
                AccountStatsPrivacyFormatter.maskValue(connectedServer, masked), palette));
        content.addView(createConnectionDetailRow("数据源信息",
                AccountStatsPrivacyFormatter.maskValue(connectedSource, masked), palette));
        content.addView(createConnectionDetailRow("网关信息",
                AccountStatsPrivacyFormatter.maskValue(connectedGateway, masked), palette));
        content.addView(createConnectionDetailRow("更新时间信息",
                AccountStatsPrivacyFormatter.maskValue(connectedUpdate, masked), palette));
        if (!sessionStorageError.isEmpty()) {
            content.addView(createConnectionDetailRow("本地会话摘要",
                    AccountStatsPrivacyFormatter.maskValue(sessionStorageError, masked), palette));
        }
        if (!connectedError.isEmpty()) {
            content.addView(createConnectionDetailRow("失败原因",
                    AccountStatsPrivacyFormatter.maskValue(connectedError, masked), palette));
        }
        if (!dataQualitySummary.isEmpty()) {
            content.addView(createConnectionDetailRow("数据校对",
                    AccountStatsPrivacyFormatter.maskValue(dataQualitySummary, masked), palette));
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setPositiveButton("确定", null);
        if (!savedSessionAccounts.isEmpty()) {
            builder.setNeutralButton("切换账号", (dialog, which) -> showLoginDialog());
        }
        if (userLoggedIn) {
            builder.setNegativeButton("退出登录", (dialog, which) -> logoutAccount());
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
        if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
        }
        if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(palette.textPrimary);
        }
        if (userLoggedIn && dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(palette.fall);
        }
    }

    // 用当前主题渲染连接详情行，保证文字可读性和整体风格一致。
    private View createConnectionDetailRow(String label,
                                           String value,
                                           UiPaletteManager.Palette palette) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dpToPx(8);
        row.setLayoutParams(params);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(palette.textSecondary);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(trim(value).isEmpty() ? "--" : value);
        valueView.setTextColor(palette.textPrimary);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dpToPx(4);
        row.addView(valueView, valueParams);
        return row;
    }

    private void showLoginDialog() {
        logRemoteSessionDebug("准备展示登录弹窗");
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dpToPx(16);
        int top = dpToPx(8);
        int bottom = dpToPx(4);
        container.setPadding(horizontal, top, horizontal, bottom);
        container.setBackground(UiPaletteManager.createSurfaceDrawable(this, palette.card, palette.stroke));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 远程交易账号输入只允许用户直接填写，避免系统自动填充抢占弹窗交互。
            container.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 远程会话属于敏感凭据输入，不允许辅助功能服务抓取明文内容。
            container.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        }

        EditText accountInput = createLoginField("账户名称", false);
        accountInput.setText(trim(loginAccountInput).isEmpty() ? ACCOUNT : trim(loginAccountInput));
        container.addView(accountInput);

        EditText passwordInput = createLoginField("账户密码", true);
        container.addView(passwordInput);

        EditText serverInput = createLoginField("服务器信息", false);
        serverInput.setText(trim(loginServerInput).isEmpty() ? SERVER : trim(loginServerInput));
        container.addView(serverInput);

        CheckBox rememberCheckBox = createRememberCheckBox(palette);
        container.addView(rememberCheckBox);

        LinearLayout savedAccountsContainer = appendSavedAccountsSection(container, palette);
        populateSavedAccountRows(savedAccountsContainer, palette, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("远程账户会话")
                .setView(container)
                .create();
        activeLoginDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        dialog.setOnDismissListener(ignored -> {
            if (activeLoginDialog == dialog) {
                activeLoginDialog = null;
            }
            if (finishAfterLoginDialog && !loginDialogSubmissionInFlight && !isHostFinishing() && !isHostDestroyed()) {
                finishHost();
                overrideHostPendingTransition(0, 0);
            }
        });
        LinearLayout actionRow = createLoginActionRow(dialog, palette, accountInput, passwordInput, serverInput, rememberCheckBox);
        container.addView(actionRow);
        dialog.setOnShowListener(ignored -> {
            logRemoteSessionDebug("登录弹窗已展示");
            refreshSavedAccountsForDialog(savedAccountsContainer, palette, dialog);
        });
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
    }

    // 登录弹窗使用页面自管操作按钮，避免部分机型把系统正按钮点击直接吞成 dismiss。
    private LinearLayout createLoginActionRow(@NonNull AlertDialog dialog,
                                              @NonNull UiPaletteManager.Palette palette,
                                              @NonNull EditText accountInput,
                                              @NonNull EditText passwordInput,
                                              @NonNull EditText serverInput,
                                              @NonNull CheckBox rememberCheckBox) {
        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dpToPx(10);
        actionRow.setLayoutParams(rowParams);

        MaterialButton cancelButton = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancelButton.setText("取消");
        cancelButton.setTextColor(palette.textSecondary);
        cancelButton.setStrokeColor(ColorStateList.valueOf(palette.stroke));
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        actionRow.addView(cancelButton);

        MaterialButton continueButton = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonStyle);
        continueButton.setText("继续");
        continueButton.setTextColor(Color.WHITE);
        continueButton.setBackgroundTintList(ColorStateList.valueOf(palette.primary));
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        continueParams.leftMargin = dpToPx(10);
        continueButton.setLayoutParams(continueParams);
        continueButton.setOnClickListener(v -> {
            String account = trim(accountInput.getText() == null ? "" : accountInput.getText().toString());
            char[] password = readPasswordChars(passwordInput);
            String server = trim(serverInput.getText() == null ? "" : serverInput.getText().toString());
            logRemoteSessionDebug("点击登录继续: accountEmpty=" + account.isEmpty()
                    + ", passwordEmpty=" + (password.length == 0)
                    + ", serverEmpty=" + server.isEmpty()
                    + ", remember=" + rememberCheckBox.isChecked());
            if (account.isEmpty() || password.length == 0 || server.isEmpty()) {
                clearPasswordChars(password);
                logRemoteSessionDebug("登录继续被字段校验拦截");
                Toast.makeText(this, "请完整填写账户、密码和服务器信息", Toast.LENGTH_SHORT).show();
                return;
            }
            logRemoteSessionDebug("登录继续通过校验，准备提交");
            loginDialogSubmissionInFlight = true;
            passwordInput.setText("");
            dialog.dismiss();
            submitRemoteLogin(account, password, server, rememberCheckBox.isChecked());
        });
        actionRow.addView(continueButton);
        return actionRow;
    }

    private EditText createLoginField(String hint, boolean passwordMode) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        input.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        input.setTextColor(palette.textPrimary);
        input.setHintTextColor(palette.textSecondary);
        input.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(8);
        input.setLayoutParams(params);
        if (passwordMode) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 远程会话凭据不接入系统自动填充，避免 ColorOS 自动填充窗口打断确认按钮点击。
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            input.setAutofillHints((String[]) null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 远程交易凭据属于敏感辅助功能数据，不允许第三方浮窗服务读取。
            input.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        }
        return input;
    }

    // 创建“记住账号”选项，明确说明密码仅加密保存在服务器端。
    private CheckBox createRememberCheckBox(UiPaletteManager.Palette palette) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText("记住此账号（密码仅加密保存在服务器）");
        checkBox.setTextColor(palette.textPrimary);
        checkBox.setButtonTintList(ColorStateList.valueOf(palette.primary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(8);
        checkBox.setLayoutParams(params);
        return checkBox;
    }

    // 在登录弹窗中追加已保存账号区域。
    private LinearLayout appendSavedAccountsSection(LinearLayout container, UiPaletteManager.Palette palette) {
        TextView title = new TextView(this);
        title.setText("已保存账号");
        title.setTextColor(palette.textSecondary);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dpToPx(6);
        titleParams.bottomMargin = dpToPx(6);
        container.addView(title, titleParams);

        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        container.addView(section);
        return section;
    }

    // 渲染已保存账号列表，允许直接发起远程切换。
    private void populateSavedAccountRows(LinearLayout container,
                                          UiPaletteManager.Palette palette,
                                          @Nullable AlertDialog dialog) {
        if (container == null) {
            return;
        }
        container.removeAllViews();
        if (savedSessionAccounts == null || savedSessionAccounts.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("暂无已保存账号");
            emptyView.setTextColor(palette.textSecondary);
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            container.addView(emptyView);
            return;
        }
        for (RemoteAccountProfile profile : savedSessionAccounts) {
            if (profile == null) {
                continue;
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
            row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dpToPx(8);
            row.setLayoutParams(rowParams);

            TextView label = new TextView(this);
            label.setText(buildSessionProfileLabel(profile));
            label.setTextColor(palette.textPrimary);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label, labelParams);

            MaterialButton actionButton = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            actionButton.setText(profile.isActive() ? "当前账号" : "切换");
            actionButton.setEnabled(!profile.isActive());
            actionButton.setTextColor(profile.isActive()
                    ? UiPaletteManager.controlUnselectedText(this)
                    : UiPaletteManager.controlSelectedText(this));
            actionButton.setStrokeColor(ColorStateList.valueOf(palette.stroke));
            actionButton.setOnClickListener(v -> {
                loginDialogSubmissionInFlight = true;
                if (dialog != null) {
                    dialog.dismiss();
                }
                submitSavedAccountSwitch(profile);
            });
            row.addView(actionButton);
            container.addView(row);
        }
    }

    // 刷新弹窗中的已保存账号列表，优先以服务端真值覆盖本地缓存。
    private void refreshSavedAccountsForDialog(LinearLayout container,
                                               UiPaletteManager.Palette palette,
                                               @Nullable AlertDialog dialog) {
        if (ioExecutor == null || sessionClient == null) {
            return;
        }
        ioExecutor.execute(() -> {
            try {
                SessionPublicKeyPayload payload = sessionClient.fetchPublicKey();
                runOnUiThreadIfAlive(() -> {
                    updateSessionProfiles(payload.getActiveAccount(), payload.getSavedAccounts(), payload.getActiveAccount() != null);
                    populateSavedAccountRows(container, palette, dialog);
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void logoutAccount() {
        if (sessionExecutor == null || remoteSessionCoordinator == null) {
            sessionStateMachine.markFailed("退出登录服务未就绪");
            Toast.makeText(this, "退出登录服务未就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        sessionStateMachine.moveTo(AccountSessionStateMachine.AccountSessionUiState.SUBMITTING, "正在退出登录");
        sessionExecutor.execute(() -> {
            try {
                remoteSessionCoordinator.logoutCurrent();
                runOnUiThreadIfAlive(this::applyLoggedOutSessionState);
            } catch (Exception ex) {
                runOnUiThreadIfAlive(() -> {
                    sessionStateMachine.markFailed(ex.getMessage());
                    Toast.makeText(this, trim(ex.getMessage()).isEmpty() ? "退出登录失败" : ex.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // 远程 logout 成功后，再在本地收口页面状态。
    private void applyLoggedOutSessionState() {
        snapshotRequestGuard.invalidateSession();
        if (snapshotRefreshCoordinator != null) {
            snapshotRefreshCoordinator.invalidateSession();
        }
        userLoggedIn = false;
        gatewayConnected = false;
        loading = false;
        activeSessionAccount = null;
        connectedAccount = "";
        if (pageRuntime != null) {
            pageRuntime.clearScheduledRefresh();
        }
        ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(false);
        if (preloadManager != null) {
            preloadManager.setFullSnapshotActive(false);
            preloadManager.clearLatestCache();
        }
        clearRuntimeAccountState();
        clearPersistedAccountStateAsync();
        updateSessionProfiles(null, savedSessionAccounts, false);
        if (secureSessionPrefs != null) {
            secureSessionPrefs.saveDraftIdentity(loginAccountInput, loginServerInput);
            refreshSessionStorageErrorFromPrefs();
        }
        sessionStateMachine.reset();
        connectedError = "";
        connectedSource = "未登录";
        connectedGateway = "--";
        connectedAccountName = "";
        connectedUpdateAtMs = 0L;
        connectedUpdate = "--";
        requestMonitorServiceAccountRuntimeClear();
        setConnectionStatus(false);
        updateOverviewHeader();
        applyLoggedOutEmptyState();
        persistUiState();
    }

    // 退出登录时清空页面持有的账户内存态，避免旧数据继续展示。
    private void clearRuntimeAccountState() {
        basePositions = new ArrayList<>();
        basePendingOrders = new ArrayList<>();
        baseTrades = new ArrayList<>();
        allCurvePoints = new ArrayList<>();
        displayedCurvePoints = new ArrayList<>();
        latestCurveIndicators = new ArrayList<>();
        latestStatsMetrics = new ArrayList<>();
        latestTradeStatsMetrics = new ArrayList<>();
        curveHistory.clear();
        tradeHistory.clear();
        lastPositionLogStates.clear();
        lastPendingLogStates.clear();
        knownTradeOpenLogKeys.clear();
        knownTradeCloseLogKeys.clear();
        knownTradeStateByKey.clear();
        lastHistoryRecordCount = 0;
        lastTradePnlMismatchState = null;
        accountLogBaselineReady = false;
        latestCumulativePnl = 0d;
        latestHistoryRevision = "";
        dataQualitySummary = "";
        lastHistoryRenderSignature = null;
        pendingHistoryRenderSignature = null;
        pendingSectionDiff = null;
        forceDeferredSectionRender = false;
    }

    // 退出登录后顺手清掉本地账户缓存，避免其他页面继续读到旧快照。
    private void clearPersistedAccountState() {
        if (accountStorageRepository == null) {
            return;
        }
        String[] identity = resolvePersistedStorageIdentity();
        if (identity == null) {
            accountStorageRepository.clearRuntimeSnapshot();
            accountStorageRepository.clearTradeHistory();
            return;
        }
        accountStorageRepository.clearRuntimeSnapshot(identity[0], identity[1]);
        accountStorageRepository.clearTradeHistory(identity[0], identity[1]);
    }

    private void clearPersistedAccountStateAsync() {
        if (accountStorageRepository == null) {
            return;
        }
        ExecutorService targetExecutor = sessionExecutor != null ? sessionExecutor : ioExecutor;
        if (targetExecutor == null) {
            return;
        }
        targetExecutor.execute(this::clearPersistedAccountState);
    }

    // 构造一个空账户快照，复用现有页面渲染链路统一清空界面。
    private AccountSnapshot buildEmptyAccountSnapshot() {
        return new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

    // 登出后立即切回彻底空态，避免列表或图表继续残留上一账户数据。
    private void applyLoggedOutEmptyState() {
        latestCurveIndicators = new ArrayList<>();
        latestStatsMetrics = new ArrayList<>();
        latestTradeStatsMetrics = new ArrayList<>();
        AccountSnapshot emptySnapshot = buildEmptyAccountSnapshot();
        if (renderCoordinator != null) {
            renderCoordinator.applySnapshot(emptySnapshot, false);
        }
        lastAppliedSnapshotSignature = buildRefreshSignature(
                emptySnapshot,
                "",
                false,
                connectedAccount,
                connectedServer
        );
        updateEmptyStateVisibility();
    }

    // 组装远程会话协调器，把网络、加密、本地缓存清理串到统一入口。
    private AccountRemoteSessionCoordinator buildRemoteSessionCoordinator() {
        return new AccountRemoteSessionCoordinator(
                sessionStateMachine,
                new AccountRemoteSessionCoordinator.SessionGateway() {
                    @Override
                    public SessionPublicKeyPayload fetchPublicKey() throws Exception {
                        return sessionClient.fetchPublicKey();
                    }

                    @Override
                    public SessionReceipt login(SessionCredentialEncryptor.LoginEnvelope envelope, boolean saveAccount) throws Exception {
                        return sessionClient.login(envelope, saveAccount);
                    }

                    @Override
                    public SessionReceipt switchAccount(String profileId, String requestId) throws Exception {
                        return sessionClient.switchAccount(profileId, requestId);
                    }

                    @Override
                    public SessionReceipt logout(String requestId) throws Exception {
                        return sessionClient.logout(requestId);
                    }

                    @Override
                    public SessionStatusPayload fetchStatus() throws Exception {
                        return sessionClient.fetchStatus();
                    }
                },
                (publicKeyPem, keyId, login, password, server, remember, clientTime) ->
                        sessionCredentialEncryptor.encrypt(publicKeyPem, keyId, login, password, server, remember, clientTime),
                new AccountRemoteSessionCoordinator.CacheResetter() {
                    @Override
                    public void clearAccountSnapshot() {
                        clearPersistedAccountState();
                        runOnUiThread(() -> {
                            snapshotRequestGuard.invalidateSession();
                            if (snapshotRefreshCoordinator != null) {
                                snapshotRefreshCoordinator.invalidateSession();
                            }
                            loading = false;
                            clearRuntimeAccountState();
                            applyLoggedOutEmptyState();
                            if (preloadManager != null) {
                                preloadManager.clearLatestCache();
                                preloadManager.setFullSnapshotActive(true);
                            }
                            requestMonitorServiceAccountRuntimeClear();
                        });
                    }

                    @Override
                    public void clearTradeHistory() {
                        if (accountStorageRepository != null) {
                            String[] identity = resolvePersistedStorageIdentity();
                            if (identity == null) {
                                accountStorageRepository.clearTradeHistory();
                                return;
                            }
                            accountStorageRepository.clearTradeHistory(identity[0], identity[1]);
                        }
                    }

                    @Override
                    public void clearChartTradeDrafts() {
                        // 当前版本没有独立持久化图表草稿，切账号时只需清空快照和页面态。
                    }

                    @Override
                    public void clearPendingExpandedState() {
                        // 账户页已不再展示挂单列表，这里无需额外清理列表展开态。
                    }

                    @Override
                    public void clearPositionExpandedState() {
                        // 账户页已不再展示持仓列表，这里无需额外清理列表展开态。
                    }

                    @Override
                    public void clearTradeExpandedState() {
                        runOnUiThread(() -> {
                            tradeAdapter.collapseAllExpandedRows();
                            tradeAdapter.submitList(new ArrayList<>());
                        });
                    }
                },
                secureSessionPrefs,
                () -> UUID.randomUUID().toString()
        );
    }

    // 判断是否需要先向服务端对齐远程会话状态。
    private boolean shouldBootstrapRemoteSession() {
        return userLoggedIn || activeSessionAccount != null || (savedSessionAccounts != null && !savedSessionAccounts.isEmpty());
    }

    // 刷新服务器上的远程会话状态，并用真值覆盖本地缓存。
    private void refreshRemoteSessionStatus(boolean requestSnapshotAfter) {
        if (sessionExecutor == null || sessionClient == null) {
            return;
        }
        sessionExecutor.execute(() -> {
            try {
                sessionClient.resetTransport();
                SessionStatusPayload status = sessionClient.fetchStatus();
                runOnUiThreadIfAlive(() -> applyRemoteSessionStatus(status, requestSnapshotAfter));
            } catch (Exception ex) {
                runOnUiThreadIfAlive(() -> {
                    connectedError = trim(ex.getMessage()).isEmpty() ? connectedError : ex.getMessage();
                    updateOverviewHeader();
                    if (requestSnapshotAfter && userLoggedIn) {
                        logRemoteSessionDebug("请求 requestForegroundEntrySnapshot");
                        snapshotRefreshCoordinator.requestForegroundEntrySnapshot();
                    }
                });
            }
        });
    }

    // 用服务端返回的 active/saved accounts 覆盖本地会话摘要缓存。
    private void applyRemoteSessionStatus(@Nullable SessionStatusPayload status, boolean requestSnapshotAfter) {
        if (status == null || !status.isOk()) {
            return;
        }
        RemoteAccountProfile activeAccount = sanitizeRemoteSessionProfile(status.getActiveAccount());
        updateSessionProfiles(activeAccount, status.getSavedAccounts(), activeAccount != null);
        if (activeAccount != null) {
            userLoggedIn = true;
            ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(true);
            applyRemoteSessionIdentity(activeAccount);
            if (!remoteSessionCoordinator.isAwaitingSync()) {
                sessionStateMachine.markActive(activeAccount.getProfileId(), "远程会话已恢复");
            }
            persistUiState();
            updateOverviewHeader();
            if (requestSnapshotAfter) {
                logRemoteSessionDebug("请求 requestForegroundEntrySnapshot");
                snapshotRefreshCoordinator.requestForegroundEntrySnapshot();
            }
            return;
        }
        if (userLoggedIn) {
            applyLoggedOutSessionState();
            return;
        }
        updateOverviewHeader();
    }

    // 普通状态刷新也只接受完整的远程会话身份，避免把缺字段脏状态恢复成本地激活态。
    @Nullable
    private RemoteAccountProfile sanitizeRemoteSessionProfile(@Nullable RemoteAccountProfile profile) {
        return isCompleteRemoteSessionProfile(profile) ? profile : null;
    }

    // 会话主链只认 profileId/login/server 完整的账号摘要。
    private boolean isCompleteRemoteSessionProfile(@Nullable RemoteAccountProfile profile) {
        return profile != null
                && !trim(profile.getProfileId()).isEmpty()
                && !trim(profile.getLogin()).isEmpty()
                && !trim(profile.getServer()).isEmpty();
    }

    // 提交新账号远程登录。
    private void submitRemoteLogin(String account, char[] password, String server, boolean remember) {
        logRemoteSessionDebug("进入 submitRemoteLogin: account=" + account
                + ", server=" + server
                + ", remember=" + remember);
        if (sessionExecutor == null || remoteSessionCoordinator == null) {
            clearPasswordChars(password);
            logRemoteSessionDebug("submitRemoteLogin 失败: 会话执行器未初始化");
            handleRemoteSessionFailed("远程会话未初始化");
            return;
        }
        loginAccountInput = account;
        loginServerInput = server;
        if (secureSessionPrefs != null) {
            secureSessionPrefs.saveDraftIdentity(account, server);
            refreshSessionStorageErrorFromPrefs();
        }
        sessionExecutor.execute(() -> {
            logRemoteSessionDebug("submitRemoteLogin 后台任务已启动: account=" + account
                    + ", server=" + server);
            try {
                AccountRemoteSessionCoordinator.SessionActionResult result = remoteSessionCoordinator.loginNewAccount(
                        new AccountRemoteSessionCoordinator.LoginRequest(
                                account,
                                password,
                                server,
                                remember,
                                System.currentTimeMillis()
                        )
                );
                logRemoteSessionDebug("submitRemoteLogin 后台任务成功，准备直连校验账户快照");
                verifyRemoteSessionAndApply(result, "登录成功");
            } catch (Exception ex) {
                logRemoteSessionDebug("submitRemoteLogin 后台任务失败: " + ex.getMessage());
                runOnUiThreadIfAlive(() -> handleRemoteSessionFailed(ex.getMessage()));
            } finally {
                clearPasswordChars(password);
            }
        });
    }

    // 统一记录远程会话调试日志，便于真机排查点击链和提交链。
    private void logRemoteSessionDebug(@NonNull String message) {
        // 远程会话链路已稳定，默认不再写入调试日志。
    }

    // 提交已保存账号切换。
    private void submitSavedAccountSwitch(@NonNull RemoteAccountProfile profile) {
        if (sessionExecutor == null || remoteSessionCoordinator == null || profile == null) {
            logRemoteSessionDebug("submitSavedAccountSwitch 失败: 会话执行器未初始化");
            handleRemoteSessionFailed("远程会话未初始化");
            return;
        }
        logRemoteSessionDebug("进入 submitSavedAccountSwitch: profileId=" + trim(profile.getProfileId())
                + ", login=" + trim(profile.getLogin())
                + ", server=" + trim(profile.getServer()));
        sessionExecutor.execute(() -> {
            try {
                AccountRemoteSessionCoordinator.SessionActionResult result = remoteSessionCoordinator.switchSavedAccount(profile.getProfileId());
                logRemoteSessionDebug("submitSavedAccountSwitch 后台任务成功，准备直连校验账户快照");
                verifyRemoteSessionAndApply(result, "登录成功");
            } catch (Exception ex) {
                logRemoteSessionDebug("submitSavedAccountSwitch 后台任务失败: " + ex.getMessage());
                runOnUiThreadIfAlive(() -> handleRemoteSessionFailed(ex.getMessage()));
            }
        });
    }

    // 登录弹窗主链：服务器会话校验通过后，立刻拉账户快照并在当前页应用成功态。
    private void verifyRemoteSessionAndApply(@Nullable AccountRemoteSessionCoordinator.SessionActionResult result,
                                             @NonNull String successMessage) {
        if (result == null || result.getReceipt() == null || result.getReceipt().isFailed()) {
            runOnUiThreadIfAlive(() -> handleRemoteSessionFailed(result == null || result.getReceipt() == null
                    ? "远程会话失败"
                    : result.getReceipt().getMessage()));
            return;
        }
        if (preloadManager == null) {
            runOnUiThreadIfAlive(() -> handleRemoteSessionFailed("账户快照服务未初始化"));
            return;
        }
        ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(true);
        AccountStatsPreloadManager.Cache verifiedCache = preloadManager.fetchForUi(AccountTimeRange.ALL);
        ensureVerifiedRemoteCache(verifiedCache, result.getActiveAccount());
        runOnUiThreadIfAlive(() -> applyVerifiedRemoteSession(result, verifiedCache, successMessage));
    }

    // 只有拿到与当前账号完全一致的远端快照后，才允许把登录收口为成功。
    private void ensureVerifiedRemoteCache(@Nullable AccountStatsPreloadManager.Cache verifiedCache,
                                           @Nullable RemoteAccountProfile profile) {
        if (verifiedCache == null || !verifiedCache.isConnected() || verifiedCache.getSnapshot() == null) {
            throw new IllegalStateException("账户登录成功，但账户数据尚未返回");
        }
        if (!isCompleteRemoteSessionProfile(profile)) {
            throw new IllegalStateException("会话账号摘要缺失");
        }
        String expectedAccount = trim(profile.getLogin());
        String expectedServer = trim(profile.getServer());
        String actualAccount = trim(verifiedCache.getAccount());
        String actualServer = trim(verifiedCache.getServer());
        if (!expectedAccount.equalsIgnoreCase(actualAccount) || !expectedServer.equalsIgnoreCase(actualServer)) {
            throw new IllegalStateException("账户数据与当前登录账号不一致");
        }
    }

    // 把已验证的账户快照直接应用到页面，完成登录成功收口。
    private void applyVerifiedRemoteSession(@NonNull AccountRemoteSessionCoordinator.SessionActionResult result,
                                            @NonNull AccountStatsPreloadManager.Cache verifiedCache,
                                            @NonNull String successMessage) {
        loginDialogSubmissionInFlight = false;
        userLoggedIn = true;
        ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(true);
        updateSessionProfiles(result.getActiveAccount(), result.getSavedAccounts(), true);
        applyRemoteSessionIdentity(result.getActiveAccount());
        applyCacheMeta(verifiedCache);
        gatewayConnected = true;
        connectedError = "";
        setConnectionStatus(true);
        String verifiedSignature = buildRefreshSignature(
                verifiedCache.getSnapshot(),
                verifiedCache.getHistoryRevision(),
                true,
                verifiedCache.getAccount(),
                verifiedCache.getServer()
        );
        if (renderCoordinator != null) {
            renderCoordinator.applySnapshot(verifiedCache.getSnapshot(), true);
        }
        lastAppliedSnapshotSignature = verifiedSignature;
        persistUiState();
        showLoginSuccessBanner();
        Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
        if (finishAfterLoginDialog) {
            setHostResult(android.app.Activity.RESULT_OK, buildLoginDialogResultIntent(result.getActiveAccount(), successMessage));
            finishHost();
            overrideHostPendingTransition(0, 0);
        }
    }

    // 服务器接受新账号后，立即切空旧页面并等待新快照完成收口。
    private void applyRemoteSessionAccepted(@Nullable AccountRemoteSessionCoordinator.SessionActionResult result,
                                            @NonNull String sourceText) {
        loginDialogSubmissionInFlight = false;
        logRemoteSessionDebug("进入 applyRemoteSessionAccepted: source=" + sourceText);
        if (result == null || result.getReceipt() == null || result.getReceipt().isFailed()) {
            handleRemoteSessionFailed(result == null || result.getReceipt() == null
                    ? "远程会话失败"
                    : result.getReceipt().getMessage());
            return;
        }
        userLoggedIn = true;
        ConfigManager.getInstance(getApplicationContext()).setAccountSessionActive(true);
        updateSessionProfiles(result.getActiveAccount(), result.getSavedAccounts(), false);
        applyRemoteSessionIdentity(result.getActiveAccount());
        connectedSource = sourceText;
        connectedGateway = "--";
        connectedError = "";
        gatewayConnected = false;
        setConnectionStatus(false);
        updateOverviewHeader();
        persistUiState();
        logRemoteSessionDebug("applyRemoteSessionAccepted 已切到可刷新会话，准备请求前台快照");
        if (snapshotRefreshCoordinator != null) {
            logRemoteSessionDebug("请求 requestForegroundEntrySnapshot");
            snapshotRefreshCoordinator.requestForegroundEntrySnapshot();
        }
        if (finishAfterLoginDialog) {
            setHostResult(android.app.Activity.RESULT_OK, buildLoginDialogResultIntent(result.getActiveAccount(), sourceText));
            logRemoteSessionDebug("applyRemoteSessionAccepted 已回传结果并准备返回原页");
            finishHost();
            overrideHostPendingTransition(0, 0);
            return;
        }
    }

    // 统一处理远程会话失败，避免残留伪成功状态。
    private void handleRemoteSessionFailed(@Nullable String message) {
        loginDialogSubmissionInFlight = false;
        String safeMessage = trim(message).isEmpty() ? "远程会话失败" : trim(message);
        logRemoteSessionDebug("handleRemoteSessionFailed: " + safeMessage);
        sessionStateMachine.markFailed(safeMessage);
        connectedError = safeMessage;
        connectedSource = "远程会话失败";
        updateOverviewHeader();
        Toast.makeText(this, safeMessage, Toast.LENGTH_SHORT).show();
        if (finishAfterLoginDialog) {
            pendingOpenLoginDialogFromIntent = true;
            openLoginDialogIfRequested();
        }
    }

    // 更新本地缓存中的 active/saved account 摘要。
    private void updateSessionProfiles(@Nullable RemoteAccountProfile activeAccount,
                                       @Nullable List<RemoteAccountProfile> savedAccounts,
                                       boolean active) {
        activeSessionAccount = activeAccount;
        savedSessionAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(savedAccounts);
        if (secureSessionPrefs != null) {
            secureSessionPrefs.saveSession(activeAccount, savedSessionAccounts, active);
            refreshSessionStorageErrorFromPrefs();
        }
    }

    // 每次本地会话摘要读写后都同步错误状态，避免页面继续展示过期失败信息。
    private void refreshSessionStorageErrorFromPrefs() {
        if (secureSessionPrefs == null) {
            sessionStorageError = "";
            return;
        }
        sessionStorageError = trim(secureSessionPrefs.getLastStorageError());
    }

    // 用新的远程账号摘要覆盖页面当前账号身份。
    private void applyRemoteSessionIdentity(@Nullable RemoteAccountProfile profile) {
        if (profile == null) {
            return;
        }
        activeSessionAccount = profile;
        if (!trim(profile.getLogin()).isEmpty()) {
            loginAccountInput = trim(profile.getLogin());
        }
        if (!trim(profile.getServer()).isEmpty()) {
            loginServerInput = trim(profile.getServer());
        }
        connectedAccount = trim(loginAccountInput).isEmpty() ? ACCOUNT : trim(loginAccountInput);
        connectedAccountName = trim(profile.getDisplayName()).isEmpty() ? connectedAccount : trim(profile.getDisplayName());
        connectedServer = trim(loginServerInput).isEmpty() ? SERVER : trim(loginServerInput);
        connectedUpdateAtMs = System.currentTimeMillis();
        connectedUpdate = FormatUtils.formatDateTime(connectedUpdateAtMs);
    }

    // 构造登录弹窗专用模式的回传结果，让原页能显示明确的同步状态。
    @NonNull
    private Intent buildLoginDialogResultIntent(@Nullable RemoteAccountProfile profile,
                                                @NonNull String statusMessage) {
        Intent result = new Intent();
        result.putExtra(EXTRA_LOGIN_DIALOG_RESULT_MESSAGE, statusMessage);
        if (profile != null) {
            result.putExtra(EXTRA_LOGIN_DIALOG_RESULT_ACCOUNT, trim(profile.getLogin()));
            result.putExtra(EXTRA_LOGIN_DIALOG_RESULT_SERVER, trim(profile.getServer()));
        }
        return result;
    }

    private void runOnUiThreadIfAlive(@NonNull Runnable action) {
        if (action == null || isHostFinishing() || isHostDestroyed()) {
            return;
        }
        runOnUiThread(() -> {
            if (isHostFinishing() || isHostDestroyed()) {
                return;
            }
            action.run();
        });
    }

    void dismissActiveLoginDialog() {
        if (activeLoginDialog == null) {
            return;
        }
        AlertDialog dialog = activeLoginDialog;
        activeLoginDialog = null;
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    // 统一关闭页面内部执行器，避免控制器在多个宿主入口重复展开同一段释放逻辑。
    void shutdownExecutors() {
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }
        if (sessionExecutor != null) {
            sessionExecutor.shutdownNow();
        }
    }

    // 统一消费外部传入的“直接打开登录弹窗”请求，避免旧页面实例漏处理。
    private void consumeLoginDialogIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }
        finishAfterLoginDialog = intent.getBooleanExtra(EXTRA_FINISH_AFTER_LOGIN_DIALOG, false);
        if (intent.getBooleanExtra(EXTRA_OPEN_LOGIN_DIALOG, false)) {
            pendingOpenLoginDialogFromIntent = true;
            intent.removeExtra(EXTRA_OPEN_LOGIN_DIALOG);
        }
        intent.removeExtra(EXTRA_FINISH_AFTER_LOGIN_DIALOG);
    }

    // 只在页面可交互时真正拉起登录弹窗，避免 onCreate/onNewIntent 阶段过早操作窗口。
    void openLoginDialogIfRequested() {
        if (!pendingOpenLoginDialogFromIntent || binding == null) {
            return;
        }
        pendingOpenLoginDialogFromIntent = false;
        binding.getRoot().post(() -> {
            if (isHostFinishing() || isHostDestroyed()) {
                return;
            }
            dismissActiveLoginDialog();
            showLoginDialog();
        });
    }

    // 兼容桥接页残留失败链路：真实登录弹窗仍统一交给共享屏幕处理。
    void retryLoginDialogFromBridge() {
        pendingOpenLoginDialogFromIntent = true;
        openLoginDialogIfRequested();
    }

    @NonNull
    private char[] readPasswordChars(@Nullable EditText input) {
        if (input == null || input.getText() == null) {
            return new char[0];
        }
        CharSequence value = input.getText();
        char[] password = new char[value.length()];
        for (int i = 0; i < value.length(); i++) {
            password[i] = value.charAt(i);
        }
        return password;
    }

    private void clearPasswordChars(@Nullable char[] password) {
        if (password == null) {
            return;
        }
        Arrays.fill(password, '\0');
    }

    // 生成人类可读的会话状态说明，用于连接详情弹窗。
    private String describeSessionState() {
        AccountSessionStateMachine.StateSnapshot snapshot = sessionStateMachine == null
                ? null
                : sessionStateMachine.snapshot();
        if (snapshot == null) {
            return userLoggedIn ? "已登录" : "未登录";
        }
        switch (snapshot.getState()) {
            case ENCRYPTING:
                return "正在加密";
            case SUBMITTING:
                return "正在提交";
            case SWITCHING:
                return "正在切换";
            case SYNCING:
                return trim(snapshot.getMessage()).isEmpty() ? "正在同步" : trim(snapshot.getMessage());
            case ACTIVE:
                return "已激活";
            case FAILED:
                return trim(snapshot.getMessage()).isEmpty() ? "失败" : snapshot.getMessage();
            case IDLE:
            default:
                return userLoggedIn ? "已登录" : "未登录";
        }
    }

    // 生成已保存账号的展示文案。
    private String buildSessionProfileLabel(@NonNull RemoteAccountProfile profile) {
        String name = trim(profile.getDisplayName());
        String maskedLogin = trim(profile.getLoginMasked());
        String server = trim(profile.getServer());
        StringBuilder builder = new StringBuilder();
        if (!name.isEmpty()) {
            builder.append(name);
        } else if (!maskedLogin.isEmpty()) {
            builder.append(maskedLogin);
        } else {
            builder.append(profile.getProfileId());
        }
        if (!server.isEmpty()) {
            builder.append(" · ").append(server);
        }
        return builder.toString();
    }

    // 装配账户统计页的静态页面结构，便于后续 Fragment 复用同一套实现。
    private void initializePageContent() {
        registerFirstFrameCompletionListener();
        setupRecyclers();
        setupFilters();
        configureToggleButtonsV2();
        setupRangeToggle();
        setupReturnStatsModeToggle();
        setupReturnStatsValueToggle();
        setupTradePnlSideToggle();
        setupTradeWeekdayBasisToggle();
        setupDatePickers();
        setupCurveInteraction();
        setupOverviewHeader();
        setupAnalysisSummaryActions();
    }

    void placeCurveSectionToBottom() {
        if (binding.cardCurveSection == null) {
            return;
        }
        android.view.ViewParent parent = binding.cardCurveSection.getParent();
        if (!(parent instanceof android.widget.LinearLayout)) {
            return;
        }
        android.widget.LinearLayout container = (android.widget.LinearLayout) parent;
        android.view.View returnStatsSection = binding.cardReturnStatsSection;
        container.removeView(binding.cardCurveSection);
        if (returnStatsSection != null && returnStatsSection.getParent() == container) {
            container.removeView(returnStatsSection);
        }
        container.addView(binding.cardCurveSection);
        if (returnStatsSection != null) {
            container.addView(returnStatsSection);
        }
    }

    private void setupRecyclers() {
        binding.recyclerCurveIndicators.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerCurveIndicators.setAdapter(indicatorAdapter);

        binding.recyclerTrades.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerTrades.setItemAnimator(null);
        binding.recyclerTrades.setAdapter(tradeAdapter);
        configureTradeScrollHandle();

        binding.recyclerStatsSummaryDetails.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerStatsSummaryDetails.setAdapter(statsSummaryDetailAdapter);
    }

    // 监听账户页第一次真正绘制完成，再开始挂载首屏之外的长列表和图表区块。
    private void registerFirstFrameCompletionListener() {
        if (binding == null || firstFrameCompleted || firstFrameDrawListener != null) {
            return;
        }
        firstFrameDrawListener = new ViewTreeObserver.OnDrawListener() {
            @Override
            public void onDraw() {
                if (firstFrameCompletionPosted) {
                    return;
                }
                firstFrameCompletionPosted = true;
                if (binding == null) {
                    return;
                }
                binding.scrollAccountStats.post(() -> {
                    clearFirstFrameCompletionListener();
                    markFirstFrameCompleted();
                });
            }
        };
        binding.scrollAccountStats.getViewTreeObserver().addOnDrawListener(firstFrameDrawListener);
    }

    // 首帧完成后立刻允许补挂次屏区块，保证剩余内容在首帧之后继续渲染。
    private void markFirstFrameCompleted() {
        if (firstFrameCompleted) {
            return;
        }
        firstFrameCompleted = true;
        scheduleDeferredSecondarySectionAttach();
    }

    // 首帧监听器只需要触发一次，页面离开前主动清理避免重复回调。
    private void clearFirstFrameCompletionListener() {
        if (binding == null || firstFrameDrawListener == null) {
            return;
        }
        ViewTreeObserver observer = binding.scrollAccountStats.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnDrawListener(firstFrameDrawListener);
        }
        firstFrameDrawListener = null;
        firstFrameCompletionPosted = false;
    }

    // 首屏先只保留可见区块，次屏区块等首帧完成后再挂载，减少账户页首次测量和绘制成本。
    private void scheduleDeferredSecondarySectionAttach() {
        if (binding == null
                || secondarySectionsAttached
                || deferredSecondarySectionAttachPosted
                || !firstFrameCompleted) {
            return;
        }
        deferredSecondarySectionAttachPosted = true;
        binding.scrollAccountStats.post(deferredSecondarySectionAttachRunnable);
    }

    // 首帧完成后再把次屏区块挂回页面，避免首进账户页时一次性测量整张长页面。
    private void attachDeferredSecondarySections() {
        if (binding == null || secondarySectionsAttached) {
            return;
        }
        long stageStartedAt = SystemClock.elapsedRealtime();
        binding.layoutCurveSecondarySection.setVisibility(View.VISIBLE);
        binding.cardReturnStatsSection.setVisibility(View.VISIBLE);
        binding.cardTradeRecordsSection.setVisibility(View.GONE);
        refreshAnalysisSummaryCards();
        secondarySectionsAttached = true;
        traceAccountRenderPhase("attach_secondary_sections",
                stageStartedAt,
                baseTrades.size(),
                basePositions.size(),
                allCurvePoints.size());
    }

    // 新一轮交易统计还在后台准备时，只在首显前保留占位，避免曲线区顶上来造成跳动。
    private void hideTradeStatsSectionUntilFreshContentReady() {
        if (binding.cardTradeStatsSection.getVisibility() != View.VISIBLE) {
            binding.cardTradeStatsSection.setVisibility(View.INVISIBLE);
        }
    }

    // 账户统计页不再用整页遮罩，而是统一切到局部打码 + 图表占位态。
    void applyPrivacyMaskState() {
        if (binding == null) {
            return;
        }
        boolean masked = isPrivacyMasked();
        indicatorAdapter.setMasked(masked);
        tradeAdapter.setMasked(masked);
        statsSummaryDetailAdapter.setMasked(masked);
        binding.tradeWeekdayBarChart.setMasked(masked);
        binding.equityCurveView.setMasked(masked);
        binding.positionRatioChartView.setMasked(masked);
        binding.drawdownChartView.setMasked(masked);
        binding.dailyReturnChartView.setMasked(masked);
        binding.tradePnlBarChart.setMasked(masked);
        binding.tradeDistributionScatterView.setMasked(masked);
        binding.holdingDurationDistributionView.setMasked(masked);
        updateOverviewHeader();
        refreshAnalysisSummaryCards();
        if (!hasImmediateAccountContent()) {
            return;
        }
        forceDeferredSectionRender = true;
        deferredSecondaryRenderPending = true;
        if (secondarySectionsAttached) {
            if (renderCoordinator != null) {
                renderCoordinator.renderDeferredSnapshotSections();
            }
        } else {
            scheduleDeferredSecondarySectionAttach();
        }
    }

    // 首屏还没有任何可见账户真值时，不再为了打码切换提前重刷整页次屏区块。
    private boolean hasImmediateAccountContent() {
        return !baseTrades.isEmpty()
                || !allCurvePoints.isEmpty()
                || !latestCurveIndicators.isEmpty();
    }

    private void updateEmptyStateVisibility() {
        if (binding == null || binding.cardStatsEmptyState == null) {
            return;
        }
        boolean hasVisibleSection = binding.cardCurveSection.getVisibility() == View.VISIBLE
                || binding.cardStatsSummarySection.getVisibility() == View.VISIBLE
                || binding.cardReturnStatsSection.getVisibility() == View.VISIBLE
                || binding.cardTradeStatsSection.getVisibility() == View.VISIBLE;
        if (hasVisibleSection) {
            binding.cardStatsEmptyState.setVisibility(View.GONE);
            return;
        }
        binding.cardCurveSection.setVisibility(View.VISIBLE);
        binding.tvCurveMeta.setVisibility(View.VISIBLE);
        binding.tvCurveMeta.setText("暂无历史曲线数据");
        binding.layoutCurveSecondarySection.setVisibility(View.GONE);
        binding.cardReturnStatsSection.setVisibility(View.GONE);
        binding.tvReturnsPeriod.setText("--");
        binding.tableMonthlyReturns.removeAllViews();
        binding.tvMonthlyReturnsHint.setVisibility(View.VISIBLE);
        binding.tvMonthlyReturnsHint.setText("暂无历史收益数据");
        binding.cardTradeRecordsSection.setVisibility(View.GONE);
        binding.cardTradeStatsSection.setVisibility(View.GONE);
        refreshAnalysisSummaryCards();
        binding.cardStatsEmptyState.setVisibility(View.GONE);
    }

    // 分析页首屏需要立刻露出曲线模块，真实数据到达后再直接覆盖占位内容。
    private void showInitialCurvePlaceholder() {
        if (binding == null) {
            return;
        }
        binding.cardCurveSection.setVisibility(View.VISIBLE);
        binding.tvCurveMeta.setVisibility(View.VISIBLE);
        if (!displayedCurvePoints.isEmpty() && !defaultCurveMeta.isEmpty()) {
            binding.tvCurveMeta.setText(defaultCurveMeta);
            return;
        }
        binding.tvCurveMeta.setText(resolveInitialCurvePlaceholderText());
    }

    // 占位文案只区分“正在拉取曲线”和“当前没有曲线”两种首屏状态。
    @NonNull
    private String resolveInitialCurvePlaceholderText() {
        if (isAccountSessionReady() || storedSnapshotRestorePending || resolveCurrentSessionCache() != null) {
            return getString(R.string.analysis_curve_loading_placeholder);
        }
        return getString(R.string.analysis_curve_empty_placeholder);
    }

    // 绑定分析页首屏交互，当前仅保留核心统计卡的展开能力。
    private void setupAnalysisSummaryActions() {
        if (binding == null) {
            return;
        }
        binding.cardStatsSummarySection.setOnClickListener(v -> toggleStatsSummaryExpanded());
        refreshAnalysisSummaryCards();
    }

    // 根据当前快照刷新分析页首屏摘要卡，避免再次把长列表堆回一级页。
    private void refreshAnalysisSummaryCards() {
        if (binding == null) {
            return;
        }
        binding.cardStatsSummarySection.setVisibility(View.VISIBLE);
        binding.cardStructureAnalysisSection.setVisibility(View.GONE);
        binding.cardTradeAnalysisEntrySection.setVisibility(View.GONE);
        binding.cardReturnStatsSection.setVisibility(View.VISIBLE);

        List<AccountMetric> detailMetrics = resolveStatsSummaryDetailMetrics();
        boolean hasDetailMetrics = !detailMetrics.isEmpty();
        statsSummaryDetailAdapter.submitList(detailMetrics);
        binding.tvStatsSummaryExpandHint.setText(activity.getString(
                statsSummaryExpanded
                        ? R.string.analysis_stats_collapse
                        : R.string.analysis_stats_expand
        ));
        binding.recyclerStatsSummaryDetails.setVisibility(
                statsSummaryExpanded && hasDetailMetrics ? View.VISIBLE : View.GONE
        );
        binding.tvStatsSummaryDetailsEmpty.setVisibility(
                statsSummaryExpanded && !hasDetailMetrics ? View.VISIBLE : View.GONE
        );

        bindSummaryMetricValue(binding.tvStatsSummaryMetricOneValue,
                findSummaryMetricValue("累计收益额", "累计盈亏"));
        bindSummaryMetricValue(binding.tvStatsSummaryMetricTwoValue,
                findSummaryMetricValue("最大回撤"));
        bindSummaryMetricValue(binding.tvStatsSummaryMetricThreeValue,
                findSummaryMetricValue("胜率"));
        binding.tvStatsSummaryMetricFourValue.setText(String.format(
                Locale.getDefault(),
                "%d笔",
                baseTrades == null ? 0 : baseTrades.size()
        ));
        maybeScrollToAnalysisTarget();
    }

    // 读取核心统计里的优先指标，首屏只展示最关键的几项。
    @NonNull
    private String findSummaryMetricValue(@NonNull String... preferredNames) {
        List<AccountMetric> metrics = resolveStatsSummaryDetailMetrics();
        if (metrics.isEmpty()) {
            return "--";
        }
        for (String preferredName : preferredNames) {
            for (AccountMetric metric : metrics) {
                if (metric == null || metric.getName() == null) {
                    continue;
                }
                String name = metric.getName();
                if (preferredName.equals(name) || name.contains(preferredName)) {
                    return safeMetricValue(metric.getValue());
                }
            }
        }
        return safeMetricValue(metrics.get(0).getValue());
    }

    // 首屏摘要值统一走这里，便于和隐私打码状态保持一致。
    private void bindSummaryMetricValue(@NonNull TextView view, @NonNull String value) {
        if (isPrivacyMasked() && !"--".equals(value)) {
            view.setText(SensitiveDisplayMasker.MASK_TEXT);
            return;
        }
        view.setText(value);
    }

    // 规整摘要值的空态文本，避免出现空字符串。
    @NonNull
    private String safeMetricValue(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return "--";
        }
        return value;
    }

    // 切换核心统计摘要卡的展开态，让一级分析页按需显示更完整的统计指标。
    private void toggleStatsSummaryExpanded() {
        statsSummaryExpanded = !statsSummaryExpanded;
        refreshAnalysisSummaryCards();
    }

    // 核心统计详细区优先使用当前已计算好的交易统计指标，没有时再回退快照原始指标。
    @NonNull
    private List<AccountMetric> resolveStatsSummaryDetailMetrics() {
        if (latestTradeStatsMetrics != null && !latestTradeStatsMetrics.isEmpty()) {
            return new ArrayList<>(latestTradeStatsMetrics);
        }
        if (latestStatsMetrics != null && !latestStatsMetrics.isEmpty()) {
            return new ArrayList<>(latestStatsMetrics);
        }
        return new ArrayList<>();
    }

    private boolean isPrivacyMasked() {
        return SensitiveDisplayMasker.isEnabled(this);
    }

    private void togglePrivacyMaskState() {
        boolean masked = !isPrivacyMasked();
        ConfigManager.getInstance(getApplicationContext()).setDataMasked(masked);
        MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        applyPrivacyMaskState();
    }

    private void setupFilters() {
        ArrayAdapter<String> productAdapter = createTradeFilterAdapter(new String[]{FILTER_PRODUCT});
        binding.spinnerTradeProduct.setAdapter(productAdapter);
        binding.spinnerTradeProduct.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            updateTradeFilterDisplayTexts();
            if (renderCoordinator != null) {
                renderCoordinator.refreshTrades(true, false);
            }
        }));

        ArrayAdapter<String> sideAdapter = createTradeFilterAdapter(new String[]{FILTER_SIDE, "\u4e70\u5165", "\u5356\u51fa"});
        binding.spinnerTradeSide.setAdapter(sideAdapter);
        binding.spinnerTradeSide.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            updateTradeFilterDisplayTexts();
            if (renderCoordinator != null) {
                renderCoordinator.refreshTrades(true, false);
            }
        }));

        ArrayAdapter<String> sortAdapter = createTradeFilterAdapter(createTradeSortOptions());
        binding.spinnerTradeSort.setAdapter(sortAdapter);
        binding.spinnerTradeSort.setOnItemSelectedListener(new SimpleSelectionListener(() -> {
            handleSortSelection(safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT), true);
            updateTradeFilterDisplayTexts();
            if (renderCoordinator != null) {
                renderCoordinator.refreshTrades(true, true);
            }
        }));

        setSpinnerSelectionByValue(binding.spinnerTradeProduct, selectedTradeProductFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeSide, selectedTradeSideFilter);
        setSpinnerSelectionByValue(binding.spinnerTradeSort, resolveSortSelectionValue(selectedTradeSortFilter));
        selectedTradeProductFilter = safeSpinnerValue(binding.spinnerTradeProduct, selectedTradeProductFilter, FILTER_PRODUCT);
        selectedTradeSideFilter = safeSpinnerValue(binding.spinnerTradeSide, selectedTradeSideFilter, FILTER_SIDE);
        selectedTradeSortFilter = safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT);
        handleSortSelection(selectedTradeSortFilter, false);
        updateTradeFilterDisplayTexts();
        binding.tvTradeProductLabel.setOnClickListener(v -> binding.spinnerTradeProduct.performClick());
        binding.tvTradeSideLabel.setOnClickListener(v -> binding.spinnerTradeSide.performClick());
        binding.tvTradeSortLabel.setOnClickListener(v -> binding.spinnerTradeSort.performClick());

        binding.btnApplyManualRange.setOnClickListener(v -> applyManualCurveRange());
    }

    private ArrayAdapter<String> createTradeFilterAdapter(String[] options) {
        final List<String> items = new ArrayList<>();
        if (options != null) {
            for (String option : options) {
                String value = option == null ? "" : option.trim();
                if (!value.isEmpty()) {
                    items.add(value);
                }
            }
        }
        if (items.isEmpty()) {
            items.add("--");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.item_spinner_filter_anchor,
                android.R.id.text1,
                items
        );
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    private void updateTradeFilterDisplayTexts() {
        updateTradeFilterDisplayTexts(
                safeSpinnerValue(binding.spinnerTradeProduct, selectedTradeProductFilter, FILTER_PRODUCT),
                safeSpinnerValue(binding.spinnerTradeSide, selectedTradeSideFilter, FILTER_SIDE),
                safeSpinnerValue(binding.spinnerTradeSort, selectedTradeSortFilter, FILTER_SORT)
        );
    }

    private void updateTradeFilterDisplayTexts(String product, String side, String sort) {
        setTradeFilterLabel(binding.tvTradeProductLabel, product, FILTER_PRODUCT);
        setTradeFilterLabel(binding.tvTradeSideLabel, side, FILTER_SIDE);
        String sortText = trim(sort);
        if (sortText.isEmpty()) {
            sortText = FILTER_SORT;
        }
        binding.tvTradeSortLabel.setText(sortText);
    }

    private void setTradeFilterLabel(TextView labelView, String value, String fallback) {
        if (labelView == null) {
            return;
        }
        String text = trim(value);
        if (text.isEmpty()) {
            text = fallback;
        }
        labelView.setText(text);
    }

    private void handleSortSelection(String rawSortValue, boolean userAction) {
        String raw = (rawSortValue == null || rawSortValue.trim().isEmpty()) ? FILTER_SORT : rawSortValue;
        String normalized = normalizeSortValue(raw);
        if (FILTER_SORT.equals(raw)) {
            selectedTradeSortFilter = raw;
            return;
        }
        tradeSortDescending = isDescendingSortLabel(raw);
        lastExplicitTradeSortMode = normalized;
        selectedTradeSortFilter = raw;
    }

    private String normalizeSortValue(String rawSort) {
        if (rawSort == null || rawSort.trim().isEmpty() || FILTER_SORT.equals(rawSort)) {
            return SORT_CLOSE_TIME;
        }
        return stripSortDirection(rawSort);
    }

    private String[] createTradeSortOptions() {
        return new String[]{
                FILTER_SORT,
                buildSortOption(SORT_CLOSE_TIME, true),
                buildSortOption(SORT_CLOSE_TIME, false),
                buildSortOption(SORT_OPEN_TIME, true),
                buildSortOption(SORT_OPEN_TIME, false),
                buildSortOption(SORT_PROFIT, true),
                buildSortOption(SORT_PROFIT, false)
        };
    }

    private String buildSortOption(String base, boolean descending) {
        return base + (descending ? " ↓" : " ↑");
    }

    private String resolveSortSelectionValue(String rawValue) {
        String safeValue = trim(rawValue);
        if (safeValue.isEmpty() || FILTER_SORT.equals(safeValue)) {
            return FILTER_SORT;
        }
        if (safeValue.endsWith("↓") || safeValue.endsWith("↑")) {
            return safeValue;
        }
        return buildSortOption(normalizeSortValue(safeValue), tradeSortDescending);
    }

    private String stripSortDirection(String value) {
        return trim(value).replace(" ↓", "").replace(" ↑", "");
    }

    private boolean isDescendingSortLabel(String value) {
        return !trim(value).endsWith("↑");
    }

    private void showTradeSortPopupMenu(View anchor) {
        View menuAnchor = anchor == null ? binding.tvTradeSortLabel : anchor;
        PopupMenu popup = new PopupMenu(this, menuAnchor);
        String[] sortOptions = new String[]{SORT_CLOSE_TIME, SORT_OPEN_TIME, SORT_PROFIT};
        String current = normalizeSortValue(selectedTradeSortFilter);
        for (int i = 0; i < sortOptions.length; i++) {
            String option = sortOptions[i];
            String label = option;
            if (option.equals(current)) {
                label = option + (tradeSortDescending ? " ↓" : " ↑");
            }
            popup.getMenu().add(0, i, i, label);
        }
        popup.setOnMenuItemClickListener(item -> {
            int which = item.getItemId();
            if (which < 0 || which >= sortOptions.length) {
                return false;
            }
            String chosen = sortOptions[which];
            handleSortSelection(chosen, true);
            setSpinnerSelectionByValue(binding.spinnerTradeSort, chosen);
            updateTradeFilterDisplayTexts();
            if (renderCoordinator != null) {
                renderCoordinator.refreshTrades(true, true);
            }
            return true;
        });
        popup.show();
    }

    private void setSpinnerSelectionByValue(Spinner spinner, String value) {
        if (spinner == null || value == null) {
            return;
        }
        SpinnerAdapter adapter = spinner.getAdapter();
        if (adapter == null) {
            return;
        }
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item != null && value.equals(item.toString())) {
                spinner.setSelection(i, false);
                return;
            }
        }
        if (adapter.getCount() > 0) {
            spinner.setSelection(0, false);
        }
    }

    private void configureToggleButtonsV2() {
        binding.tvCurveTitle.setText("\u51c0\u503c/\u7ed3\u4f59\u66f2\u7ebf");

        styleSegmentButton(binding.btnRange1d, "1D", 10f);
        styleSegmentButton(binding.btnRange7d, "7D", 10f);
        styleSegmentButton(binding.btnRange1m, "1M", 10f);
        styleSegmentButton(binding.btnRange3m, "3M", 10f);
        styleSegmentButton(binding.btnRange1y, "1Y", 10f);
        styleSegmentButton(binding.btnRangeAll, "ALL", 10f);

        styleSegmentButton(binding.btnReturnDay, "\u65e5\u6536\u76ca", 11f);
        styleSegmentButton(binding.btnReturnMonth, "\u6708\u6536\u76ca", 11f);
        styleSegmentButton(binding.btnReturnYear, "\u5e74\u6536\u76ca", 11f);
        styleSegmentButton(binding.btnReturnStage, "\u9636\u6bb5\u6536\u76ca", 11f);
        styleSegmentButton(binding.btnReturnsRate, "\u6536\u76ca\u7387", 10f);
        styleSegmentButton(binding.btnReturnsAmount, "\u6536\u76ca\u989d", 10f);

        styleSegmentButton(binding.btnTradePnlAll, "\u5168\u90e8", 11f);
        styleSegmentButton(binding.btnTradePnlBuy, "\u4e70\u5165", 11f);
        styleSegmentButton(binding.btnTradePnlSell, "\u5356\u51fa", 11f);
        styleSegmentButton(binding.btnTradeWeekdayCloseTime, "\u6309\u5e73\u4ed3\u65f6\u95f4", 11f);
        styleSegmentButton(binding.btnTradeWeekdayOpenTime, "\u6309\u5f00\u4ed3\u65f6\u95f4", 11f);

        binding.toggleTimeRange.post(() -> autoFitSegmentButtons(
                binding.toggleTimeRange,
                new MaterialButton[]{
                        binding.btnRange1d, binding.btnRange7d, binding.btnRange1m,
                        binding.btnRange3m, binding.btnRange1y, binding.btnRangeAll
                },
                10f,
                8f));
        binding.toggleReturnStatsMode.post(() -> autoFitSegmentButtons(
                binding.toggleReturnStatsMode,
                new MaterialButton[]{
                        binding.btnReturnDay, binding.btnReturnMonth, binding.btnReturnYear, binding.btnReturnStage
                },
                11f,
                8f));
        binding.toggleReturnsValueMode.post(() -> autoFitSegmentButtons(
                binding.toggleReturnsValueMode,
                new MaterialButton[]{
                        binding.btnReturnsRate, binding.btnReturnsAmount
                },
                11f,
                9f));
        binding.toggleTradePnlSide.post(() -> autoFitSegmentButtons(
                binding.toggleTradePnlSide,
                new MaterialButton[]{
                        binding.btnTradePnlAll, binding.btnTradePnlBuy, binding.btnTradePnlSell
                },
                11f,
                9f));
        binding.toggleTradeWeekdayBasis.post(() -> autoFitSegmentButtons(
                binding.toggleTradeWeekdayBasis,
                new MaterialButton[]{
                        binding.btnTradeWeekdayCloseTime, binding.btnTradeWeekdayOpenTime
                },
                11f,
                9f));
    }

    private void configureToggleButtons() {
        binding.tvCurveTitle.setText("净值/结余曲线");
        styleSegmentButton(binding.btnRange1d, "1D", 10f);
        styleSegmentButton(binding.btnRange7d, "7D", 10f);
        styleSegmentButton(binding.btnRange1m, "1M", 10f);
        styleSegmentButton(binding.btnRange3m, "3M", 10f);
        styleSegmentButton(binding.btnRange1y, "1Y", 10f);
        styleSegmentButton(binding.btnRangeAll, "ALL", 10f);

        styleSegmentButton(binding.btnReturnDay, "日收益", 11f);
        styleSegmentButton(binding.btnReturnMonth, "月收益", 11f);
        styleSegmentButton(binding.btnReturnYear, "年收益", 11f);
        styleSegmentButton(binding.btnReturnStage, "阶段收益", 11f);

        styleSegmentButton(binding.btnTradePnlAll, "全部", 11f);
        styleSegmentButton(binding.btnTradePnlBuy, "买入", 11f);
        styleSegmentButton(binding.btnTradePnlSell, "卖出", 11f);
        binding.tvCurveTitle.setText("净值/结余曲线");
        styleSegmentButton(binding.btnReturnDay, "日收益", 11f);
        styleSegmentButton(binding.btnReturnMonth, "月收益", 11f);
        styleSegmentButton(binding.btnReturnYear, "年收益", 11f);
        styleSegmentButton(binding.btnReturnStage, "阶段收益", 11f);
        styleSegmentButton(binding.btnTradePnlAll, "全部", 11f);
        styleSegmentButton(binding.btnTradePnlBuy, "买入", 11f);
        styleSegmentButton(binding.btnTradePnlSell, "卖出", 11f);

        binding.toggleTimeRange.post(() -> autoFitSegmentButtons(
                binding.toggleTimeRange,
                new MaterialButton[]{
                        binding.btnRange1d, binding.btnRange7d, binding.btnRange1m,
                        binding.btnRange3m, binding.btnRange1y, binding.btnRangeAll
                },
                10f,
                8f));
        binding.toggleReturnStatsMode.post(() -> autoFitSegmentButtons(
                binding.toggleReturnStatsMode,
                new MaterialButton[]{
                        binding.btnReturnDay, binding.btnReturnMonth, binding.btnReturnYear, binding.btnReturnStage
                },
                11f,
                8f));
    }

    private void styleSegmentButton(MaterialButton button, String text, float sizeSp) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.styleSegmentedButton(button, palette, text, sizeSp);
    }

    private void autoFitSegmentButtons(MaterialButtonToggleGroup group,
                                       MaterialButton[] buttons,
                                       float maxTextSizeSp,
                                       float minTextSizeSp) {
        UiPaletteManager.applyContentAwareButtonGroupLayout(
                group,
                buttons,
                maxTextSizeSp,
                minTextSizeSp,
                8
        );
    }

    private void setupDatePickers() {
        setupManualDatePickerPanel();
        setupReturnPeriodPickerPanel();
    }

    private void setupCurveInteraction() {
        binding.equityCurveView.setShowBottomTimeLabels(false);
        binding.equityCurveView.setMergeWithPreviousPane(false);
        binding.equityCurveView.setMergeWithNextPane(true);
        binding.positionRatioChartView.setMergeWithPreviousPane(true);
        binding.positionRatioChartView.setMergeWithNextPane(true);
        binding.drawdownChartView.setMergeWithPreviousPane(true);
        binding.drawdownChartView.setMergeWithNextPane(true);
        binding.dailyReturnChartView.setShowBottomTimeLabels(true);
        binding.dailyReturnChartView.setMergeWithPreviousPane(true);
        binding.dailyReturnChartView.setMergeWithNextPane(false);
        binding.equityCurveView.setOnPointHighlightListener((point, xRatio) -> {
            if (syncingCurveHighlight) {
                return;
            }
            if (point == null) {
                clearSharedCurveHighlight();
                return;
            }
            applySharedCurveHighlight(point.getTimestamp(), xRatio, true);
        });
        binding.drawdownChartView.setOnTimeHighlightListener((timestamp, xRatio) -> {
            if (syncingCurveHighlight) {
                return;
            }
            if (timestamp == null) {
                clearSharedCurveHighlight();
                return;
            }
            applySharedCurveHighlight(timestamp, xRatio, true);
        });
        binding.positionRatioChartView.setOnTimeHighlightListener((timestamp, xRatio) -> {
            if (syncingCurveHighlight) {
                return;
            }
            if (timestamp == null) {
                clearSharedCurveHighlight();
                return;
            }
            applySharedCurveHighlight(timestamp, xRatio, true);
        });
        binding.dailyReturnChartView.setOnTimeHighlightListener((timestamp, xRatio) -> {
            if (syncingCurveHighlight) {
                return;
            }
            if (timestamp == null) {
                clearSharedCurveHighlight();
                return;
            }
            applySharedCurveHighlight(timestamp, xRatio, true);
        });
    }

    private void setupManualDatePickerPanel() {
        binding.etRangeStart.setOnClickListener(v -> showManualDatePickerPanel(DATE_TARGET_START));
        binding.etRangeEnd.setOnClickListener(v -> showManualDatePickerPanel(DATE_TARGET_END));
        binding.btnManualDateCancel.setOnClickListener(v -> hideManualDatePickerPanel());
        binding.btnManualDateConfirm.setOnClickListener(v -> {
            Calendar chosen = Calendar.getInstance();
            chosen.set(Calendar.YEAR, binding.npManualYear.getValue());
            chosen.set(Calendar.MONTH, binding.npManualMonth.getValue() - 1);
            chosen.set(Calendar.DAY_OF_MONTH, binding.npManualDay.getValue());
            chosen.set(Calendar.HOUR_OF_DAY, 0);
            chosen.set(Calendar.MINUTE, 0);
            chosen.set(Calendar.SECOND, 0);
            chosen.set(Calendar.MILLISECOND, 0);

            EditText target = manualDateTarget == DATE_TARGET_END ? binding.etRangeEnd : binding.etRangeStart;
            target.setText(dateOnlyFormat.format(chosen.getTime()));
            hideManualDatePickerPanel();
        });
        binding.npManualYear.setOnValueChangedListener((picker, oldVal, newVal) -> updateManualPickerDayRange());
        binding.npManualMonth.setOnValueChangedListener((picker, oldVal, newVal) -> updateManualPickerDayRange());
    }

    private void showManualDatePickerPanel(int target) {
        manualDateTarget = target;
        binding.tvManualDatePickerTitle.setText(target == DATE_TARGET_START ? "选择开始日期" : "选择结束日期");

        Calendar floor = Calendar.getInstance();
        floor.add(Calendar.YEAR, -8);
        int minYear = floor.get(Calendar.YEAR);
        Calendar ceil = Calendar.getInstance();
        ceil.add(Calendar.YEAR, 2);
        int maxYear = ceil.get(Calendar.YEAR);
        binding.npManualYear.setMinValue(minYear);
        binding.npManualYear.setMaxValue(maxYear);
        binding.npManualYear.setWrapSelectorWheel(false);

        binding.npManualMonth.setMinValue(1);
        binding.npManualMonth.setMaxValue(12);
        binding.npManualMonth.setWrapSelectorWheel(true);

        EditText targetView = target == DATE_TARGET_END ? binding.etRangeEnd : binding.etRangeStart;
        Calendar chosen = Calendar.getInstance();
        String text = trim(targetView.getText() == null ? "" : targetView.getText().toString());
        if (!text.isEmpty()) {
            try {
                Date parsed = dateOnlyFormat.parse(text);
                if (parsed != null) {
                    chosen.setTime(parsed);
                }
            } catch (Exception ignored) {
            }
        }

        int year = chosen.get(Calendar.YEAR);
        if (year < minYear) {
            year = minYear;
        } else if (year > maxYear) {
            year = maxYear;
        }
        binding.npManualYear.setValue(year);
        binding.npManualMonth.setValue(chosen.get(Calendar.MONTH) + 1);
        updateManualPickerDayRange();
        binding.npManualDay.setValue(chosen.get(Calendar.DAY_OF_MONTH));
        binding.layoutManualDatePickerPanel.setVisibility(View.VISIBLE);
        binding.layoutManualDatePickerPanel.post(() ->
                applyPickerPanelStyle(binding.npManualYear, binding.npManualMonth, binding.npManualDay));
    }

    private void updateManualPickerDayRange() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, binding.npManualYear.getValue());
        calendar.set(Calendar.MONTH, Math.max(0, binding.npManualMonth.getValue() - 1));
        int maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        binding.npManualDay.setMinValue(1);
        binding.npManualDay.setMaxValue(maxDay);
        binding.npManualDay.setWrapSelectorWheel(true);
    }

    private void hideManualDatePickerPanel() {
        binding.layoutManualDatePickerPanel.setVisibility(View.GONE);
    }

    private void setupReturnPeriodPickerPanel() {
        binding.tvReturnsPeriod.setOnClickListener(v -> showReturnPeriodPickerPanel());
        binding.btnReturnPeriodCancel.setOnClickListener(v -> hideReturnPeriodPickerPanel());
        binding.btnReturnPeriodConfirm.setOnClickListener(v -> {
            if (returnPickerYears.isEmpty()) {
                hideReturnPeriodPickerPanel();
                return;
            }
            int selectedYear = returnPickerYears.get(binding.npReturnYear.getValue());
            int monthIndex = binding.npReturnMonth.getValue();
            int selectedMonth = monthIndex >= 0 && monthIndex < returnPickerVisibleMonths.size()
                    ? returnPickerVisibleMonths.get(monthIndex)
                    : 1;
            Calendar selected = Calendar.getInstance();
            selected.set(selectedYear, selectedMonth - 1, 1, 0, 0, 0);
            selected.set(Calendar.MILLISECOND, 0);
            returnStatsAnchorDateMs = selected.getTimeInMillis();
            hideReturnPeriodPickerPanel();
            if (renderCoordinator != null) {
                renderCoordinator.refreshReturnStats();
            }
        });
        binding.npReturnYear.setOnValueChangedListener((picker, oldVal, newVal) ->
                syncReturnPickerMonthForYearIndex(newVal));
    }

    private void showReturnPeriodPickerPanel() {
        if (returnStatsMode != ReturnStatsMode.DAY) {
            binding.layoutReturnPeriodPickerPanel.setVisibility(View.GONE);
            return;
        }

        returnPickerYearMonthMap.clear();
        returnPickerYearMonthMap.putAll(buildYearMonthAvailability(allCurvePoints));
        if (returnPickerYearMonthMap.isEmpty()) {
            binding.layoutReturnPeriodPickerPanel.setVisibility(View.GONE);
            return;
        }

        returnPickerYears.clear();
        returnPickerYears.addAll(returnPickerYearMonthMap.keySet());
        returnPickerYears.sort(Integer::compareTo);

        String[] yearLabels = AccountDatePickerValueHelper.buildYearLabels(returnPickerYears);

        Calendar anchor = Calendar.getInstance();
        anchor.setTimeInMillis(returnStatsAnchorDateMs > 0L ? returnStatsAnchorDateMs : System.currentTimeMillis());
        int year = anchor.get(Calendar.YEAR);
        int month = anchor.get(Calendar.MONTH) + 1;
        int yearIndex = returnPickerYears.indexOf(year);
        if (yearIndex < 0) {
            yearIndex = returnPickerYears.size() - 1;
            year = returnPickerYears.get(yearIndex);
        }

        binding.npReturnYear.setMinValue(0);
        binding.npReturnYear.setMaxValue(yearLabels.length - 1);
        binding.npReturnYear.setDisplayedValues(null);
        binding.npReturnYear.setDisplayedValues(yearLabels);
        binding.npReturnYear.setWrapSelectorWheel(false);
        binding.npReturnYear.setValue(yearIndex);

        binding.npReturnMonth.setWrapSelectorWheel(false);
        applyMonthPickerRange(binding.npReturnMonth, returnPickerYearMonthMap.get(year), month);
        binding.layoutReturnPeriodPickerPanel.setVisibility(View.VISIBLE);
        binding.layoutReturnPeriodPickerPanel.post(() ->
                applyPickerPanelStyle(binding.npReturnYear, binding.npReturnMonth));
    }

    private void syncReturnPickerMonthForYearIndex(int yearIndex) {
        if (returnPickerYears.isEmpty() || yearIndex < 0 || yearIndex >= returnPickerYears.size()) {
            return;
        }
        int selectedYear = returnPickerYears.get(yearIndex);
        applyMonthPickerRange(binding.npReturnMonth,
                returnPickerYearMonthMap.get(selectedYear),
                resolveSelectedReturnMonth());
        binding.layoutReturnPeriodPickerPanel.post(() ->
                applyPickerPanelStyle(binding.npReturnYear, binding.npReturnMonth));
    }

    // 统一提升日期选择器的文字和分隔线可见性。
    private void applyPickerPanelStyle(NumberPicker... pickers) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        int textColor = palette.textPrimary;
        int dividerColor = palette.stroke;
        applyPickerPanelTheme(palette);
        for (NumberPicker picker : pickers) {
            applyNumberPickerStyle(picker, textColor, dividerColor);
        }
    }

    // 把日期选择面板的容器、标题和按钮统一切到当前主题。
    private void applyPickerPanelTheme(UiPaletteManager.Palette palette) {
        if (binding == null || palette == null) {
            return;
        }
        binding.layoutManualDatePickerPanel.setBackground(
                UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.layoutReturnPeriodPickerPanel.setBackground(
                UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
        binding.tvManualDatePickerTitle.setTextColor(palette.textPrimary);
        binding.tvReturnPeriodPickerTitle.setTextColor(palette.textPrimary);
        stylePickerPanelButton(binding.btnManualDateCancel, false, palette);
        stylePickerPanelButton(binding.btnReturnPeriodCancel, false, palette);
        stylePickerPanelButton(binding.btnManualDateConfirm, true, palette);
        stylePickerPanelButton(binding.btnReturnPeriodConfirm, true, palette);
    }

    // 通过子控件和反射同步 NumberPicker 颜色，解决深色界面数字发灰的问题。
    private void applyNumberPickerStyle(@Nullable NumberPicker picker, int textColor, int dividerColor) {
        if (picker == null) {
            return;
        }
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setVerticalFadingEdgeEnabled(false);
        picker.setFadingEdgeLength(0);
        picker.setAlpha(1f);
        for (int i = 0; i < picker.getChildCount(); i++) {
            View child = picker.getChildAt(i);
            if (child instanceof EditText) {
                EditText editText = (EditText) child;
                editText.setTextColor(textColor);
                editText.setHintTextColor(textColor);
                editText.setHighlightColor(dividerColor);
                editText.setAlpha(1f);
                editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            }
        }
        try {
            java.lang.reflect.Field selectorWheelPaintField =
                    NumberPicker.class.getDeclaredField("mSelectorWheelPaint");
            selectorWheelPaintField.setAccessible(true);
            Paint paint = (Paint) selectorWheelPaintField.get(picker);
            if (paint != null) {
                paint.setColor(textColor);
                paint.setAlpha(255);
                paint.setTextSize(dpToPx(18));
            }
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field selectionDividerField =
                    NumberPicker.class.getDeclaredField("mSelectionDivider");
            selectionDividerField.setAccessible(true);
            selectionDividerField.set(picker, new android.graphics.drawable.ColorDrawable(dividerColor));
        } catch (Exception ignored) {
        }
        picker.setWillNotDraw(false);
        picker.invalidate();
        picker.postInvalidate();
        picker.requestLayout();
    }

    // 统一日期选择面板按钮风格，避免 XML 固定颜色压过主题。
    private void stylePickerPanelButton(@Nullable Button button,
                                        boolean primary,
                                        UiPaletteManager.Palette palette) {
        if (button == null || palette == null) {
            return;
        }
        if (primary) {
            button.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
            button.setTextColor(UiPaletteManager.controlSelectedText(this));
        } else {
            button.setBackground(UiPaletteManager.createOutlinedDrawable(this, palette.card, palette.stroke));
            button.setTextColor(UiPaletteManager.controlUnselectedText(this));
        }
    }

    private void hideReturnPeriodPickerPanel() {
        binding.layoutReturnPeriodPickerPanel.setVisibility(View.GONE);
    }

    private Map<Integer, boolean[]> buildYearMonthAvailability(List<CurvePoint> points) {
        Map<Integer, boolean[]> yearMonthMap = new TreeMap<>();
        if (points == null) {
            return yearMonthMap;
        }
        for (CurvePoint point : points) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(point.getTimestamp());
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH) + 1;
            boolean[] months = yearMonthMap.get(year);
            if (months == null) {
                months = new boolean[13];
                yearMonthMap.put(year, months);
            }
            months[month] = true;
        }
        return yearMonthMap;
    }

    private void applyMonthPickerRange(NumberPicker picker, @Nullable boolean[] months, int preferredMonth) {
        AccountDatePickerValueHelper.MonthState state =
                AccountDatePickerValueHelper.buildMonthState(months, preferredMonth);
        returnPickerVisibleMonths.clear();
        returnPickerVisibleMonths.addAll(state.getMonths());
        picker.setDisplayedValues(null);
        picker.setMinValue(0);
        picker.setMaxValue(Math.max(0, state.getLabels().length - 1));
        picker.setDisplayedValues(state.getLabels());
        picker.setValue(state.getSelectedIndex());
    }

    // 把月份选择器当前索引还原成真实月份。
    private int resolveSelectedReturnMonth() {
        int monthIndex = binding.npReturnMonth.getValue();
        if (monthIndex >= 0 && monthIndex < returnPickerVisibleMonths.size()) {
            return returnPickerVisibleMonths.get(monthIndex);
        }
        return 1;
    }

    private void setupRangeToggle() {
        binding.toggleTimeRange.check(mapRangeButtonId(selectedRange));
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
            clearManualCurveRange(true);
            if (renderCoordinator != null) {
                renderCoordinator.refreshCurveProjection();
            }
        });
    }

    private void setupReturnStatsModeToggle() {
        binding.toggleReturnStatsMode.check(mapReturnModeButtonId(returnStatsMode));
        binding.tvReturnsPeriod.setEnabled(returnStatsMode == ReturnStatsMode.DAY);
        binding.tvReturnsPeriod.setAlpha(returnStatsMode == ReturnStatsMode.DAY ? 1f : 0.65f);
        binding.toggleReturnStatsMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnReturnDay) {
                returnStatsMode = ReturnStatsMode.DAY;
            } else if (checkedId == R.id.btnReturnMonth) {
                returnStatsMode = ReturnStatsMode.MONTH;
            } else if (checkedId == R.id.btnReturnYear) {
                returnStatsMode = ReturnStatsMode.YEAR;
            } else {
                returnStatsMode = ReturnStatsMode.STAGE;
            }
            if (returnStatsMode != ReturnStatsMode.DAY) {
                hideReturnPeriodPickerPanel();
            }
            binding.tvReturnsPeriod.setEnabled(returnStatsMode == ReturnStatsMode.DAY);
            binding.tvReturnsPeriod.setAlpha(returnStatsMode == ReturnStatsMode.DAY ? 1f : 0.65f);
            if (renderCoordinator != null) {
                renderCoordinator.refreshReturnStats();
            }
        });
    }

    private void setupReturnStatsValueToggle() {
        binding.toggleReturnsValueMode.check(returnValueMode == ReturnValueMode.AMOUNT
                ? R.id.btnReturnsAmount
                : R.id.btnReturnsRate);
        binding.toggleReturnsValueMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            returnValueMode = checkedId == R.id.btnReturnsAmount
                    ? ReturnValueMode.AMOUNT
                    : ReturnValueMode.RATE;
            if (renderCoordinator != null) {
                renderCoordinator.refreshReturnStats();
            }
        });
    }

    private void setupTradePnlSideToggle() {
        binding.toggleTradePnlSide.check(mapTradePnlSideButtonId(tradePnlSideMode));
        binding.toggleTradePnlSide.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnTradePnlBuy) {
                tradePnlSideMode = TradePnlSideMode.BUY;
            } else if (checkedId == R.id.btnTradePnlSell) {
                tradePnlSideMode = TradePnlSideMode.SELL;
            } else {
                tradePnlSideMode = TradePnlSideMode.ALL;
            }
            if (renderCoordinator != null) {
                renderCoordinator.refreshTradeStats();
            }
        });
    }

    private void setupTradeWeekdayBasisToggle() {
        binding.toggleTradeWeekdayBasis.check(mapTradeWeekdayBasisButtonId(tradeWeekdayBasis));
        binding.toggleTradeWeekdayBasis.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            tradeWeekdayBasis = checkedId == R.id.btnTradeWeekdayOpenTime
                    ? TradeWeekdayBasis.OPEN_TIME
                    : TradeWeekdayBasis.CLOSE_TIME;
            if (renderCoordinator != null) {
                renderCoordinator.refreshTradeStats();
            }
        });
    }

    private int mapRangeButtonId(AccountTimeRange range) {
        if (range == null) {
            return R.id.btnRange7d;
        }
        switch (range) {
            case D1:
                return R.id.btnRange1d;
            case D7:
                return R.id.btnRange7d;
            case M1:
                return R.id.btnRange1m;
            case M3:
                return R.id.btnRange3m;
            case Y1:
                return R.id.btnRange1y;
            case ALL:
            default:
                return R.id.btnRangeAll;
        }
    }

    private int mapReturnModeButtonId(ReturnStatsMode mode) {
        if (mode == ReturnStatsMode.DAY) {
            return R.id.btnReturnDay;
        }
        if (mode == ReturnStatsMode.YEAR) {
            return R.id.btnReturnYear;
        }
        if (mode == ReturnStatsMode.STAGE) {
            return R.id.btnReturnStage;
        }
        return R.id.btnReturnMonth;
    }

    private int mapTradeWeekdayBasisButtonId(TradeWeekdayBasis basis) {
        return basis == TradeWeekdayBasis.OPEN_TIME
                ? R.id.btnTradeWeekdayOpenTime
                : R.id.btnTradeWeekdayCloseTime;
    }

    private int mapTradePnlSideButtonId(TradePnlSideMode mode) {
        if (mode == TradePnlSideMode.BUY) {
            return R.id.btnTradePnlBuy;
        }
        if (mode == TradePnlSideMode.SELL) {
            return R.id.btnTradePnlSell;
        }
        return R.id.btnTradePnlAll;
    }

    void bindLocalMeta() {
        AccountStatsPreloadManager.Cache cache = resolveCurrentSessionCache();
        if (cache != null) {
            applyCacheMeta(cache);
            showInitialCurvePlaceholder();
            updateEmptyStateVisibility();
            maybeScrollToAnalysisTarget();
            return;
        }
        if (snapshotRefreshCoordinator != null) {
            snapshotRefreshCoordinator.primeStoredSnapshotRestoreIfNeeded();
        }
        String defaultAccount = activeSessionAccount != null && !trim(activeSessionAccount.getLogin()).isEmpty()
                ? trim(activeSessionAccount.getLogin())
                : loginAccountInput;
        String defaultServer = activeSessionAccount != null && !trim(activeSessionAccount.getServer()).isEmpty()
                ? trim(activeSessionAccount.getServer())
                : loginServerInput;
        connectedAccount = userLoggedIn ? defaultAccount : ACCOUNT;
        connectedAccountName = userLoggedIn && activeSessionAccount != null && !trim(activeSessionAccount.getDisplayName()).isEmpty()
                ? trim(activeSessionAccount.getDisplayName())
                : connectedAccount;
        connectedServer = userLoggedIn ? defaultServer : SERVER;
        connectedSource = userLoggedIn ? "远程会话待同步" : "未登录";
        connectedGateway = "--";
        connectedUpdateAtMs = System.currentTimeMillis();
        connectedUpdate = FormatUtils.formatDateTime(connectedUpdateAtMs);
        connectedError = "";
        dataQualitySummary = "";
        gatewayConnected = false;
        lastAppliedSnapshotSignature = "";
        setConnectionStatus(false);
        updateOverviewHeader();
        showInitialCurvePlaceholder();
        updateEmptyStateVisibility();
        maybeScrollToAnalysisTarget();
    }

    // 只要当前页已有本账户可渲染快照，就不应把切页/回前台误当成一次新的页面 bootstrap。
    private boolean hasRenderableCurrentSessionState() {
        if (!lastAppliedSnapshotSignature.isEmpty()) {
            return true;
        }
        AccountStatsPreloadManager.Cache cache = resolveCurrentSessionCache();
        return cache != null && hasRenderableHistorySections(cache.getSnapshot());
    }

    // 只有真正拿到历史成交或净值曲线时，才能把缓存当成完整统计快照；纯运行态缓存仍需继续走正式历史刷新。
    private boolean hasRenderableHistorySections(@Nullable AccountSnapshot snapshot) {
        AccountHistoryPayload payload = historySnapshotStore.build(snapshot, latestHistoryRevision);
        return !payload.getTrades().isEmpty() || !payload.getCurvePoints().isEmpty();
    }

    // 统一解析当前活动远程会话可消费的缓存，避免页面不同入口各自重复判断。
    private AccountStatsPreloadManager.Cache resolveCurrentSessionCache() {
        if (preloadManager == null) {
            return null;
        }
        AccountStatsPreloadManager.Cache cache = preloadManager.getLatestCache();
        if (cache == null) {
            return null;
        }
        if (!isPreloadedCacheForCurrentSession(cache)) {
            return null;
        }
        return cache;
    }

    // 当前页首帧先消费缓存元数据，避免先把连接态写成未连接再被真实快照纠正。
    private void applyCacheMeta(@NonNull AccountStatsPreloadManager.Cache cache) {
        long updateAt = cache.getUpdatedAt() > 0L ? cache.getUpdatedAt() : cache.getFetchedAt();
        connectedAccount = cache.getAccount().isEmpty() ? ACCOUNT : cache.getAccount();
        connectedAccountName = connectedAccount;
        connectedServer = cache.getServer().isEmpty() ? SERVER : cache.getServer();
        connectedSource = normalizeSource(cache.getSource());
        connectedGateway = cache.getGateway().isEmpty() ? "--" : cache.getGateway();
        connectedUpdateAtMs = updateAt;
        connectedUpdate = FormatUtils.formatDateTime(updateAt);
        connectedError = cache.getError();
        latestHistoryRevision = trim(cache.getHistoryRevision());
        setConnectionStatus(cache.isConnected());
    }

    // 只消费当前活动远程会话对应的预加载缓存，避免旧账号缓存短暂回灌到页面。
    private boolean isPreloadedCacheForCurrentSession(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null) {
            return false;
        }
        String expectedAccount = "";
        String expectedServer = "";
        if (remoteSessionCoordinator != null && remoteSessionCoordinator.isAwaitingSync()) {
            expectedAccount = trim(remoteSessionCoordinator.getPendingLogin());
            expectedServer = trim(remoteSessionCoordinator.getPendingServer());
        } else if (activeSessionAccount != null) {
            expectedAccount = trim(activeSessionAccount.getLogin());
            expectedServer = trim(activeSessionAccount.getServer());
        } else if (userLoggedIn) {
            expectedAccount = trim(loginAccountInput);
            expectedServer = trim(loginServerInput);
        }
        String cachedAccount = trim(cache.getAccount());
        String cachedServer = trim(cache.getServer());
        if (!expectedAccount.isEmpty() && !expectedAccount.equalsIgnoreCase(cachedAccount)) {
            return false;
        }
        if (!expectedServer.isEmpty() && !expectedServer.equalsIgnoreCase(cachedServer)) {
            return false;
        }
        return true;
    }

    private void updateOverviewHeader() {
        // 账户页已不再承载实时总览头部，连接状态由曲线卡右上角和会话状态单独展示。
    }

    private boolean isAccountSessionReady() {
        return userLoggedIn && ConfigManager.getInstance(getApplicationContext()).isAccountSessionActive();
    }

    // 页面自己合成的断线空快照只用于“完全无可渲染状态”的场景，不能覆盖当前已展示的真实持仓。
    private boolean shouldApplyFetchedSnapshot(@Nullable AccountSnapshot snapshot,
                                               boolean remoteConnected,
                                               boolean syntheticDisconnectedSnapshot,
                                               @Nullable String incomingHistoryRevision,
                                               @Nullable String requestStartHistoryRevision) {
        if (snapshot == null) {
            return false;
        }
        if (shouldRejectStaleHistorySnapshot(incomingHistoryRevision, requestStartHistoryRevision)) {
            return false;
        }
        if (remoteConnected) {
            return true;
        }
        if (syntheticDisconnectedSnapshot && hasRenderableCurrentSessionState()) {
            return false;
        }
        return true;
    }

    // 如果请求发出后页面已经收到了更新过的历史修订号，旧回包就不能再把交易记录覆盖回去。
    private boolean shouldRejectStaleHistorySnapshot(@Nullable String incomingHistoryRevision,
                                                     @Nullable String requestStartHistoryRevision) {
        String requestRevision = trim(requestStartHistoryRevision);
        if (requestRevision.isEmpty()) {
            return false;
        }
        AccountStatsPreloadManager.Cache currentCache = resolveCurrentSessionCache();
        if (currentCache == null) {
            return false;
        }
        String currentRevision = trim(currentCache.getHistoryRevision());
        String incomingRevision = trim(incomingHistoryRevision);
        if (currentRevision.isEmpty() || incomingRevision.isEmpty()) {
            return false;
        }
        if (currentRevision.equals(requestRevision)) {
            return false;
        }
        return !currentRevision.equals(incomingRevision);
    }

    private boolean isOlderThanCurrentSnapshot(long incomingUpdatedAt) {
        if (incomingUpdatedAt <= 0L || connectedUpdateAtMs <= 0L) {
            return false;
        }
        return incomingUpdatedAt < connectedUpdateAtMs;
    }

    private boolean isLoginCredentialMatched(String remoteAccount, String remoteServer) {
        String expectedAccount = trim(loginAccountInput);
        String expectedServer = trim(loginServerInput);
        if (remoteSessionCoordinator != null && remoteSessionCoordinator.isAwaitingSync()) {
            // syncing 阶段必须按会话协调器的目标账号比对，避免被旧输入值误判成“登录校验失败”。
            expectedAccount = trim(remoteSessionCoordinator.getPendingLogin());
            expectedServer = trim(remoteSessionCoordinator.getPendingServer());
        }
        String normalizedRemoteAccount = trim(remoteAccount);
        String normalizedRemoteServer = trim(remoteServer);
        boolean accountMatched = expectedAccount.isEmpty() || normalizedRemoteAccount.equalsIgnoreCase(expectedAccount);
        boolean serverMatched = expectedServer.isEmpty() || normalizedRemoteServer.equalsIgnoreCase(expectedServer);
        return accountMatched && serverMatched;
    }

    private void adjustRefreshCadence(boolean connected, boolean unchanged) {
        if (!connected) {
            unchangedRefreshStreak = 0;
            dynamicRefreshDelayMs = Math.min(ACCOUNT_REFRESH_MAX_MS, ACCOUNT_REFRESH_MIN_MS * 2L);
            return;
        }
        if (unchanged) {
            unchangedRefreshStreak = Math.min(12, unchangedRefreshStreak + 1);
        } else {
            unchangedRefreshStreak = 0;
        }
        dynamicRefreshDelayMs = Math.min(
                ACCOUNT_REFRESH_MAX_MS,
                ACCOUNT_REFRESH_MIN_MS + unchangedRefreshStreak * 2_000L);
    }

    // 构建当前账户展示快照签名，用于判断“本轮数据是否与上一轮一致”。
    private String buildRefreshSignature(@Nullable AccountSnapshot snapshot,
                                         @Nullable String historyRevision,
                                         boolean connected,
                                         @Nullable String account,
                                         @Nullable String server) {
        return buildHistoryRenderSignature(snapshot, historyRevision).asText();
    }

    @NonNull
    private AccountStatsRenderSignature buildCurrentHistoryRenderSignature() {
        return buildHistoryRenderSignature(
                new AccountHistoryPayload(
                        latestHistoryRevision,
                        new ArrayList<>(baseTrades),
                        new ArrayList<>(allCurvePoints),
                        new ArrayList<>(latestStatsMetrics),
                        new ArrayList<>(latestCurveIndicators)
                )
        );
    }

    @NonNull
    private AccountStatsRenderSignature buildHistoryRenderSignature(@Nullable AccountSnapshot snapshot,
                                                                    @Nullable String historyRevision) {
        return buildHistoryRenderSignature(historySnapshotStore.build(snapshot, historyRevision));
    }

    @NonNull
    private AccountStatsRenderSignature buildHistoryRenderSignature(@NonNull AccountHistoryPayload payload) {
        return AccountStatsRenderSignature.from(
                payload.getHistoryRevision(),
                payload.getTrades(),
                payload.getCurvePoints(),
                payload.getStatsMetrics(),
                payload.getCurveIndicators(),
                selectedTradeProductFilter,
                selectedTradeSideFilter,
                selectedTradeSortFilter,
                tradeSortDescending
        );
    }

    // 追加指标签名，统一按 name/value 顺序编码。
    private void appendMetricsSignature(StringBuilder builder, @Nullable List<AccountMetric> metrics) {
        if (metrics == null) {
            appendStringToken(builder, "metric:null");
            return;
        }
        appendStringToken(builder, "metric:size:" + metrics.size());
        List<String> entries = new ArrayList<>(metrics.size());
        for (AccountMetric item : metrics) {
            if (item == null) {
                entries.add("metric:item:null");
                continue;
            }
            StringBuilder entry = new StringBuilder(64);
            appendStringToken(entry, item.getName());
            appendStringToken(entry, item.getValue());
            entries.add(entry.toString());
        }
        Collections.sort(entries);
        for (String entry : entries) {
            appendStringToken(builder, entry);
        }
    }

    // 追加持仓/挂单签名，覆盖展示层核心字段。
    private void appendPositionSignature(StringBuilder builder, @Nullable List<PositionItem> positions) {
        if (positions == null) {
            appendStringToken(builder, "position:null");
            return;
        }
        appendStringToken(builder, "position:size:" + positions.size());
        List<String> entries = new ArrayList<>(positions.size());
        for (PositionItem item : positions) {
            if (item == null) {
                entries.add("position:item:null");
                continue;
            }
            StringBuilder entry = new StringBuilder(192);
            appendStringToken(entry, item.getProductName());
            appendStringToken(entry, item.getCode());
            appendStringToken(entry, item.getSide());
            appendLongToken(entry, item.getPositionTicket());
            appendLongToken(entry, item.getOrderId());
            appendDoubleToken(entry, item.getQuantity());
            appendDoubleToken(entry, item.getSellableQuantity());
            appendDoubleToken(entry, item.getCostPrice());
            appendDoubleToken(entry, item.getLatestPrice());
            appendDoubleToken(entry, item.getMarketValue());
            appendDoubleToken(entry, item.getPositionRatio());
            appendDoubleToken(entry, item.getDayPnL());
            appendDoubleToken(entry, item.getTotalPnL());
            appendDoubleToken(entry, item.getReturnRate());
            appendDoubleToken(entry, item.getPendingLots());
            appendLongToken(entry, item.getPendingCount());
            appendDoubleToken(entry, item.getPendingPrice());
            appendDoubleToken(entry, item.getTakeProfit());
            appendDoubleToken(entry, item.getStopLoss());
            appendDoubleToken(entry, item.getStorageFee());
            entries.add(entry.toString());
        }
        Collections.sort(entries);
        for (String entry : entries) {
            appendStringToken(builder, entry);
        }
    }

    // 追加净值曲线签名，避免曲线刷新后被误判为同一份页面快照。
    private void appendCurveSignature(StringBuilder builder, @Nullable List<CurvePoint> points) {
        if (points == null) {
            appendStringToken(builder, "curve:null");
            return;
        }
        appendStringToken(builder, "curve:size:" + points.size());
        List<String> entries = new ArrayList<>(points.size());
        for (CurvePoint item : points) {
            if (item == null) {
                entries.add("curve:item:null");
                continue;
            }
            StringBuilder entry = new StringBuilder(96);
            appendLongToken(entry, item.getTimestamp());
            appendDoubleToken(entry, item.getEquity());
            appendDoubleToken(entry, item.getBalance());
            appendDoubleToken(entry, item.getPositionRatio());
            entries.add(entry.toString());
        }
        Collections.sort(entries);
        for (String entry : entries) {
            appendStringToken(builder, entry);
        }
    }

    // 追加交易记录签名，确保历史成交变更时页面不会跳过重绘。
    private void appendTradeSignature(StringBuilder builder, @Nullable List<TradeRecordItem> trades) {
        if (trades == null) {
            appendStringToken(builder, "trade:null");
            return;
        }
        appendStringToken(builder, "trade:size:" + trades.size());
        List<String> entries = new ArrayList<>(trades.size());
        for (TradeRecordItem item : trades) {
            if (item == null) {
                entries.add("trade:item:null");
                continue;
            }
            StringBuilder entry = new StringBuilder(160);
            appendStringToken(entry, item.getProductName());
            appendStringToken(entry, item.getCode());
            appendStringToken(entry, item.getSide());
            appendLongToken(entry, item.getDealTicket());
            appendLongToken(entry, item.getOrderId());
            appendLongToken(entry, item.getPositionId());
            appendLongToken(entry, item.getOpenTime());
            appendLongToken(entry, item.getCloseTime());
            appendLongToken(entry, item.getTimestamp());
            appendDoubleToken(entry, item.getQuantity());
            appendDoubleToken(entry, item.getProfit());
            appendDoubleToken(entry, item.getStorageFee());
            appendDoubleToken(entry, item.getOpenPrice());
            appendDoubleToken(entry, item.getClosePrice());
            entries.add(entry.toString());
        }
        Collections.sort(entries);
        for (String entry : entries) {
            appendStringToken(builder, entry);
        }
    }

    // 统一字符串 token 编码，避免分隔符歧义。
    private void appendStringToken(StringBuilder builder, @Nullable String value) {
        String safe = value == null ? "" : value;
        builder.append(safe.length()).append('#').append(safe).append('|');
    }

    // 统一 long token 编码。
    private void appendLongToken(StringBuilder builder, long value) {
        builder.append(value).append('|');
    }

    // 统一 double token 编码，避免浮点格式化差异。
    private void appendDoubleToken(StringBuilder builder, double value) {
        builder.append(Double.doubleToLongBits(value)).append('|');
    }

    // 仅在页面处于活跃状态且用户保持登录时，才继续排队下一次刷新。
    private boolean shouldKeepRefreshLoop() {
        return pageRuntime != null && pageRuntime.shouldKeepRefreshLoop(userLoggedIn, isFinishing(), isDestroyed());
    }

    private String normalizeSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return "MT5网关";
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("fallback") || normalized.contains("offline")) {
            return "历史数据（网关离线）";
        }
        if (source.contains("缃戝叧") || source.contains("网关")) {
            return "MT5网关";
        }
        if (source.contains("鍘嗗彶") || source.contains("历史")) {
            return "历史数据（网关离线）";
        }
        return source;
    }

    private void setConnectionStatus(boolean connected) {
        gatewayConnected = connected;
    }

    private void logConnectionEvent(boolean connected) {
        if (logManager == null) {
            return;
        }
        boolean stateChanged = lastLoggedConnectedState == null || lastLoggedConnectedState != connected;
        if (stateChanged) {
            if (connected) {
                logManager.info("AccountStats connected: account=" + connectedAccount
                        + ", server=" + connectedServer
                        + ", source=" + trim(connectedSource));
            } else {
                String message = "AccountStats disconnected: fallback source=" + trim(connectedSource);
                String error = trim(connectedError);
                if (!error.isEmpty()) {
                    message = message + ", error=" + error;
                }
                logManager.warn(message);
            }
        }

        String source = trim(connectedSource);
        if (!source.equals(lastLoggedSource)) {
            logManager.info("AccountStats data source -> " + source);
            lastLoggedSource = source;
        }

        String gateway = trim(connectedGateway);
        if (!gateway.equals(lastLoggedGateway) && !gateway.isEmpty()) {
            logManager.info("AccountStats gateway -> " + gateway);
            lastLoggedGateway = gateway;
        }

        String error = trim(connectedError);
        if (!connected && !error.isEmpty() && !error.equals(lastLoggedError)) {
            logManager.warn("AccountStats gateway error: " + error);
        }
        lastLoggedError = error;
        lastLoggedConnectedState = connected;
    }

    private void logAccountSnapshotEvents(List<PositionItem> positions,
                                          List<PositionItem> pendingOrders,
                                          List<TradeRecordItem> trades,
                                          boolean remoteConnected) {
        if (logManager == null || !remoteConnected) {
            return;
        }

        Map<String, PositionLogState> currentPositions = buildPositionLogStateMap(positions);
        Map<String, PendingLogState> currentPending = buildPendingLogStateMap(pendingOrders);

        if (!accountLogBaselineReady) {
            lastPositionLogStates.clear();
            lastPositionLogStates.putAll(currentPositions);
            lastPendingLogStates.clear();
            lastPendingLogStates.putAll(currentPending);
            knownTradeOpenLogKeys.clear();
            knownTradeCloseLogKeys.clear();
            knownTradeStateByKey.clear();
            if (trades != null) {
                for (TradeRecordItem item : trades) {
                    knownTradeOpenLogKeys.add(buildTradeOpenLogKey(item));
                    knownTradeCloseLogKeys.add(buildTradeCloseLogKey(item));
                    knownTradeStateByKey.put(buildTradeDedupeKey(item), buildTradeStateSignature(item));
                }
            }
            lastHistoryRecordCount = trades == null ? 0 : trades.size();
            accountLogBaselineReady = true;
            return;
        }

        final double qtyEps = 1e-6;
        Set<String> positionKeys = new HashSet<>();
        positionKeys.addAll(lastPositionLogStates.keySet());
        positionKeys.addAll(currentPositions.keySet());
        for (String key : positionKeys) {
            PositionLogState previous = lastPositionLogStates.get(key);
            PositionLogState current = currentPositions.get(key);
            if (previous == null && current != null && Math.abs(current.quantity) > qtyEps) {
                logManager.info("Position opened: " + current.code + " " + current.side
                        + " qty=" + shortQty(current.quantity)
                        + " avgCost=$" + FormatUtils.formatPrice(current.avgCostPrice));
                continue;
            }
            if (previous != null && (current == null || Math.abs(current.quantity) <= qtyEps)) {
                logManager.info("Position closed: " + previous.code + " " + previous.side
                        + " qty=" + shortQty(previous.quantity));
                continue;
            }
            if (previous == null || current == null) {
                continue;
            }
            double delta = current.quantity - previous.quantity;
            double threshold = Math.max(1e-4, Math.max(Math.abs(previous.quantity), Math.abs(current.quantity)) * 0.001d);
            if (Math.abs(delta) >= threshold) {
                if (delta > 0d) {
                    logManager.info("Position increased: " + current.code + " " + current.side
                            + " +" + shortQty(delta) + " -> " + shortQty(current.quantity));
                } else {
                    logManager.info("Position reduced: " + current.code + " " + current.side
                            + " " + shortQty(delta) + " -> " + shortQty(current.quantity));
                }
            }
        }

        Set<String> pendingKeys = new HashSet<>();
        pendingKeys.addAll(lastPendingLogStates.keySet());
        pendingKeys.addAll(currentPending.keySet());
        for (String key : pendingKeys) {
            PendingLogState previous = lastPendingLogStates.get(key);
            PendingLogState current = currentPending.get(key);
            if (previous == null && current != null && current.count > 0) {
                logManager.info("Pending added: " + current.code + " " + current.side
                        + " lots=" + shortQty(current.lots) + ", count=" + current.count);
                continue;
            }
            if (previous != null && (current == null || current.count <= 0 || Math.abs(current.lots) <= qtyEps)) {
                logManager.info("Pending removed: " + previous.code + " " + previous.side
                        + " lots=" + shortQty(previous.lots) + ", count=" + previous.count);
                continue;
            }
            if (previous == null || current == null) {
                continue;
            }
            boolean countChanged = previous.count != current.count;
            boolean lotsChanged = Math.abs(current.lots - previous.lots) >= Math.max(1e-4, Math.abs(previous.lots) * 0.01d);
            if (countChanged || lotsChanged) {
                logManager.info("Pending changed: " + current.code + " " + current.side
                        + " lots " + shortQty(previous.lots) + " -> " + shortQty(current.lots)
                        + ", count " + previous.count + " -> " + current.count);
            }
        }

        if (trades != null && !trades.isEmpty()) {
            List<TradeRecordItem> orderedTrades = new ArrayList<>(trades);
            orderedTrades.sort(Comparator.comparingLong(this::resolveCloseTime));
            Map<String, String> currentTradeStates = new HashMap<>();
            int budget = 80;
            for (TradeRecordItem item : orderedTrades) {
                String tradeKey = buildTradeDedupeKey(item);
                String tradeState = buildTradeStateSignature(item);
                currentTradeStates.put(tradeKey, tradeState);
                String previousState = knownTradeStateByKey.get(tradeKey);
                if (previousState != null && !previousState.equals(tradeState) && budget > 0) {
                    logManager.info("Trade changed: " + symbolForLog(item.getCode(), item.getProductName())
                            + " " + sideForLog(item.getSide())
                            + " qty=" + shortQty(item.getQuantity())
                            + " pnl=" + signedMoney(item.getProfit())
                            + " close=" + FormatUtils.formatDateTime(resolveCloseTime(item)));
                    budget--;
                }

                String openKey = buildTradeOpenLogKey(item);
                boolean newOpenKey = previousState == null && knownTradeOpenLogKeys.add(openKey);
                if (newOpenKey && budget > 0) {
                    logManager.info("Trade opened: " + symbolForLog(item.getCode(), item.getProductName())
                            + " " + sideForLog(item.getSide())
                            + " qty=" + shortQty(item.getQuantity())
                            + " open=" + FormatUtils.formatDateTime(resolveOpenTime(item)));
                    budget--;
                }

                String closeKey = buildTradeCloseLogKey(item);
                boolean newCloseKey = previousState == null && knownTradeCloseLogKeys.add(closeKey);
                if (newCloseKey && budget > 0) {
                    logManager.info("Trade closed: " + symbolForLog(item.getCode(), item.getProductName())
                            + " " + sideForLog(item.getSide())
                            + " qty=" + shortQty(item.getQuantity())
                            + " pnl=" + signedMoney(item.getProfit())
                            + " close=" + FormatUtils.formatDateTime(resolveCloseTime(item)));
                    budget--;
                }
            }

            int historyRecordCount = currentTradeStates.size();
            if (historyRecordCount != lastHistoryRecordCount) {
                int delta = historyRecordCount - lastHistoryRecordCount;
                if (delta > 0) {
                    logManager.info("History records added: +" + delta + " -> " + historyRecordCount);
                } else if (lastHistoryRecordCount > 0) {
                    logManager.warn("History records reduced: " + delta + " -> " + historyRecordCount);
                }
                lastHistoryRecordCount = historyRecordCount;
            }

            int removedTrades = 0;
            for (String key : new HashSet<>(knownTradeStateByKey.keySet())) {
                if (!currentTradeStates.containsKey(key)) {
                    removedTrades++;
                }
            }
            if (removedTrades > 0) {
                logManager.warn("Trade records removed from snapshot: " + removedTrades);
            }

            knownTradeStateByKey.clear();
            knownTradeStateByKey.putAll(currentTradeStates);
        }

        if (knownTradeOpenLogKeys.size() > 50_000 || knownTradeCloseLogKeys.size() > 50_000) {
            knownTradeOpenLogKeys.clear();
            knownTradeCloseLogKeys.clear();
            if (trades != null) {
                for (TradeRecordItem item : trades) {
                    knownTradeOpenLogKeys.add(buildTradeOpenLogKey(item));
                    knownTradeCloseLogKeys.add(buildTradeCloseLogKey(item));
                }
            }
        }

        lastPositionLogStates.clear();
        lastPositionLogStates.putAll(currentPositions);
        lastPendingLogStates.clear();
        lastPendingLogStates.putAll(currentPending);
    }

    private Map<String, PositionLogState> buildPositionLogStateMap(List<PositionItem> positions) {
        Map<String, PositionLogState> result = new HashMap<>();
        if (positions == null || positions.isEmpty()) {
            return result;
        }
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            double qty = Math.max(0d, Math.abs(item.getQuantity()));
            if (qty <= 1e-9) {
                continue;
            }
            String code = symbolForLog(item.getCode(), item.getProductName());
            String side = sideForLog(item.getSide());
            String key = code + "|" + side;
            PositionLogState state = result.get(key);
            if (state == null) {
                state = new PositionLogState();
                state.productName = trim(item.getProductName());
                state.code = code;
                state.side = side;
                result.put(key, state);
            }
            state.quantity += qty;
            state.costAmount += qty * Math.max(0d, item.getCostPrice());
            state.latestPrice = Math.max(state.latestPrice, Math.max(0d, item.getLatestPrice()));
        }
        for (PositionLogState state : result.values()) {
            state.avgCostPrice = state.quantity > 0d ? state.costAmount / state.quantity : 0d;
        }
        return result;
    }

    private Map<String, PendingLogState> buildPendingLogStateMap(List<PositionItem> pendingOrders) {
        Map<String, PendingLogState> result = new HashMap<>();
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return result;
        }
        for (PositionItem item : pendingOrders) {
            if (item == null) {
                continue;
            }
            double lots = Math.max(0d, item.getPendingLots());
            int count = Math.max(0, item.getPendingCount());
            if (count <= 0) {
                continue;
            }
            String code = symbolForLog(item.getCode(), item.getProductName());
            String side = sideForLog(item.getSide());
            String key = code + "|" + side;
            PendingLogState state = result.get(key);
            if (state == null) {
                state = new PendingLogState();
                state.productName = trim(item.getProductName());
                state.code = code;
                state.side = side;
                result.put(key, state);
            }
            state.lots += lots;
            state.count += count;
            double pendingPrice = item.getPendingPrice() > 0d ? item.getPendingPrice() : item.getLatestPrice();
            if (pendingPrice > 0d) {
                state.priceAmount += pendingPrice * Math.max(1d, lots);
                state.priceWeight += Math.max(1d, lots);
            }
        }
        for (PendingLogState state : result.values()) {
            state.price = state.priceWeight > 0d ? state.priceAmount / state.priceWeight : 0d;
        }
        return result;
    }

    private String buildTradeOpenLogKey(TradeRecordItem item) {
        String code = symbolForLog(item == null ? "" : item.getCode(), item == null ? "" : item.getProductName());
        String side = sideForLog(item == null ? "" : item.getSide());
        long open = item == null ? 0L : resolveOpenTime(item);
        long qty = item == null ? 0L : Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long price = item == null ? 0L : Math.round(Math.abs(item.getPrice()) * 100d);
        return code + "|" + side + "|" + open + "|" + qty + "|" + price;
    }

    private String buildTradeCloseLogKey(TradeRecordItem item) {
        return buildTradeDedupeKey(item);
    }

    private String buildTradeStateSignature(TradeRecordItem item) {
        if (item == null) {
            return "";
        }
        long open = resolveOpenTime(item);
        long close = resolveCloseTime(item);
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long price = Math.round(Math.abs(item.getPrice()) * 100d);
        long profit = Math.round(item.getProfit() * 100d);
        long storage = Math.round(item.getStorageFee() * 100d);
        String remark = trim(item.getRemark());
        String side = normalizeSide(trim(item.getSide())).toLowerCase(Locale.ROOT);
        return open + "|" + close + "|" + qty + "|" + price + "|" + profit + "|" + storage
                + "|" + side + "|" + remark;
    }

    private String symbolForLog(String code, String productName) {
        String normalizedCode = trim(code).toUpperCase(Locale.ROOT);
        if (!normalizedCode.isEmpty()) {
            return normalizedCode;
        }
        return trim(productName).toUpperCase(Locale.ROOT);
    }

    private String sideForLog(String side) {
        String normalized = normalizeSide(trim(side));
        if ("buy".equalsIgnoreCase(normalized)) {
            return "Buy";
        }
        if ("sell".equalsIgnoreCase(normalized)) {
            return "Sell";
        }
        return normalized;
    }

    private String shortQty(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private static final class PositionLogState {
        private String productName;
        private String code;
        private String side;
        private double quantity;
        private double costAmount;
        private double avgCostPrice;
        private double latestPrice;
    }

    private static final class PendingLogState {
        private String productName;
        private String code;
        private String side;
        private double lots;
        private int count;
        private double priceAmount;
        private double priceWeight;
        private double price;
    }

    // 统一输出账户页主线程阶段耗时，便于真机点击后直接定位最重的一段。
    private void traceAccountRenderPhase(String phase,
                                         long stageStartedAt,
                                         int tradeCount,
                                         int positionCount,
                                         int curveCount) {
        ChainLatencyTracer.markAccountRenderPhase(
                buildAccountTraceKey(),
                phase,
                SystemClock.elapsedRealtime() - stageStartedAt,
                tradeCount,
                positionCount,
                curveCount
        );
    }

    // 账户页性能日志按账号@服务器聚合，方便区分不同远程会话。
    private String buildAccountTraceKey() {
        String account = trim(connectedAccount);
        if (account.isEmpty() && activeSessionAccount != null) {
            account = trim(activeSessionAccount.getLogin());
        }
        if (account.isEmpty()) {
            account = trim(loginAccountInput);
        }
        String server = trim(connectedServer);
        if (server.isEmpty() && activeSessionAccount != null) {
            server = trim(activeSessionAccount.getServer());
        }
        if (server.isEmpty()) {
            server = trim(loginServerInput);
        }
        return account + "@" + server;
    }

    // 解析当前页面正在处理的远程会话身份，供本地分区缓存读写复用。
    private String[] resolvePersistedStorageIdentity() {
        String account = trim(connectedAccount);
        if (account.isEmpty() && activeSessionAccount != null) {
            account = trim(activeSessionAccount.getLogin());
        }
        if (account.isEmpty()) {
            account = trim(loginAccountInput);
        }
        String server = trim(connectedServer);
        if (server.isEmpty() && activeSessionAccount != null) {
            server = trim(activeSessionAccount.getServer());
        }
        if (server.isEmpty()) {
            server = trim(loginServerInput);
        }
        if (account.isEmpty() || server.isEmpty()) {
            return null;
        }
        return new String[]{account, server};
    }

    private void replaceCurveHistory(List<CurvePoint> source) {
        curveHistory.clear();
        if (source != null && !source.isEmpty()) {
            curveHistory.addAll(source);
        }
        refreshAnalysisSummaryCards();
    }

    private void replaceTradeHistory(List<TradeRecordItem> source) {
        tradeHistory.clear();
        if (source != null && !source.isEmpty()) {
            tradeHistory.addAll(source);
        }
        refreshAnalysisSummaryCards();
    }

    private String buildDataQualitySummary(List<TradeRecordItem> trades,
                                           List<CurvePoint> curves,
                                           List<PositionItem> positions) {
        int tradeCount = trades == null ? 0 : trades.size();
        int curveCount = curves == null ? 0 : curves.size();
        int missingOpen = 0;
        int missingClose = 0;
        int nearZeroProfit = 0;
        if (trades != null) {
            for (TradeRecordItem item : trades) {
                if (item == null) {
                    continue;
                }
                if (resolveOpenTime(item) <= 0L) {
                    missingOpen++;
                }
                if (resolveCloseTime(item) <= 0L) {
                    missingClose++;
                }
                if (Math.abs(item.getProfit()) < TRADE_PNL_ZERO_THRESHOLD) {
                    nearZeroProfit++;
                }
            }
        }
        int missingTp = 0;
        int missingSl = 0;
        if (positions != null) {
            for (PositionItem item : positions) {
                if (item == null || Math.abs(item.getQuantity()) <= 1e-9) {
                    continue;
                }
                if (item.getTakeProfit() <= 0d) {
                    missingTp++;
                }
                if (item.getStopLoss() <= 0d) {
                    missingSl++;
                }
            }
        }
        String capHint = tradeCount >= 500 ? ", 疑似存在上限截断" : "";
        return "交易" + tradeCount
                + "条, 曲线" + curveCount
                + "点, 开仓时间缺失" + missingOpen
                + "条, 平仓时间缺失" + missingClose
                + "条, 近零盈亏" + nearZeroProfit
                + "条, 持仓缺止盈" + missingTp
                + "条, 持仓缺止损" + missingSl + "条" + capHint;
    }

    private List<CurvePoint> normalizeCurvePoints(List<CurvePoint> source) {
        return AccountCurvePointNormalizer.normalize(source, ACCOUNT_INITIAL_BALANCE);
    }

    // 把已准备好的交易统计结果绑定到页面，避免 UI 层再做集合遍历。
    private void bindTradeAnalytics(List<AccountMetric> tradeStatsMetrics,
                                    List<TradePnlBarChartView.Entry> entries,
                                    List<CurveAnalyticsHelper.TradeScatterPoint> tradeScatterPoints,
                                    List<CurveAnalyticsHelper.DurationBucket> holdingDurationBuckets,
                                    List<TradeWeekdayBarChartHelper.Entry> weekdayEntries,
                                    double totalPnl) {
        latestTradeStatsMetrics = tradeStatsMetrics == null ? new ArrayList<>() : new ArrayList<>(tradeStatsMetrics);
        boolean masked = isPrivacyMasked();
        binding.tradePnlBarChart.setEntries(entries == null ? new ArrayList<>() : new ArrayList<>(entries));
        binding.tradeDistributionScatterView.setPoints(
                tradeScatterPoints == null ? new ArrayList<>() : new ArrayList<>(tradeScatterPoints));
        binding.holdingDurationDistributionView.setBuckets(
                holdingDurationBuckets == null ? new ArrayList<>() : new ArrayList<>(holdingDurationBuckets));
        binding.tradeWeekdayBarChart.setEntries(
                weekdayEntries == null ? new ArrayList<>() : new ArrayList<>(weekdayEntries));
        refreshAnalysisSummaryCards();
        maybeScrollToAnalysisTarget();
        String sideLabel;
        if (tradePnlSideMode == TradePnlSideMode.BUY) {
            sideLabel = "买入";
        } else if (tradePnlSideMode == TradePnlSideMode.SELL) {
            sideLabel = "卖出";
        } else {
            sideLabel = "全部";
        }
        String pnlText = signedMoney(totalPnl);
        if (masked) {
            binding.tvTradePnlSummary.setText(String.format(
                    Locale.getDefault(),
                    "全周期总计盈亏（%s）: %s",
                    sideLabel,
                    SensitiveDisplayMasker.MASK_TEXT));
            binding.tvTradePnlSummary.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            binding.tvTradePnlLegend.setVisibility(View.GONE);
            binding.cardTradeStatsSection.setVisibility(View.VISIBLE);
            maybeScrollToAnalysisTarget();
            return;
        }
        String summaryText = String.format(
                Locale.getDefault(),
                "全周期总计盈亏（%s）: %s",
                sideLabel,
                pnlText);
        SpannableString summarySpan = new SpannableString(summaryText);
        int pnlStart = summaryText.lastIndexOf(pnlText);
        if (pnlStart >= 0) {
            int pnlColor = resolveSignedValueColor(totalPnl);
            summarySpan.setSpan(new ForegroundColorSpan(pnlColor),
                    pnlStart, pnlStart + pnlText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        binding.tvTradePnlSummary.setText(summarySpan);
        binding.tvTradePnlLegend.setVisibility(View.GONE);
        binding.cardTradeStatsSection.setVisibility(View.VISIBLE);
        maybeScrollToAnalysisTarget();
    }

    private long resolveOpenTime(TradeRecordItem item) {
        return item.getOpenTime();
    }

    private long resolveCloseTime(TradeRecordItem item) {
        return item.getCloseTime();
    }

    private String percentRaw(double ratio) {
        return String.format(Locale.getDefault(), "%.2f%%", ratio * 100d);
    }

    // 首屏先只解析当前范围下的主曲线点，避免等待次级区块后台结果后才看到历史曲线。
    private List<CurvePoint> resolveImmediateCurvePoints() {
        if (manualCurveRangeEnabled) {
            List<CurvePoint> manualPoints = filterCurveByManualRange(
                    allCurvePoints,
                    manualCurveRangeStartMs,
                    manualCurveRangeEndMs
            );
            if (manualPoints.size() >= 2) {
                syncRangeInputsWithDisplayedCurve(manualPoints);
                return manualPoints;
            }
        }
        List<CurvePoint> rangedPoints = filterCurveByRange(allCurvePoints, selectedRange);
        syncRangeInputsWithDisplayedCurve(rangedPoints);
        return rangedPoints;
    }

    private void syncRangeInputsWithDisplayedCurve(List<CurvePoint> points) {
        if (binding == null) {
            return;
        }
        if (points == null || points.isEmpty()) {
            binding.etRangeStart.setText("");
            binding.etRangeEnd.setText("");
            return;
        }
        CurvePoint start = points.get(0);
        CurvePoint end = points.get(points.size() - 1);
        binding.etRangeStart.setText(dateOnlyFormat.format(new Date(start.getTimestamp())));
        binding.etRangeEnd.setText(dateOnlyFormat.format(new Date(end.getTimestamp())));
    }

    private List<CurvePoint> filterCurveByManualRange(List<CurvePoint> source, long startInclusive, long endInclusive) {
        List<CurvePoint> filtered = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return filtered;
        }
        for (CurvePoint point : source) {
            long ts = point.getTimestamp();
            if (ts >= startInclusive && ts <= endInclusive) {
                filtered.add(point);
            }
        }
        return filtered;
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

    private void renderCurveWithIndicators(List<CurvePoint> points) {
        if (curveRenderHelper == null) {
            return;
        }
        binding.cardCurveSection.setVisibility(View.VISIBLE);
        binding.tvCurveMeta.setVisibility(View.VISIBLE);
        AccountStatsCurveRenderHelper.RenderResult result = curveRenderHelper.renderImmediate(
                points,
                latestCurveIndicators,
                secondarySectionsAttached,
                isPrivacyMasked()
        );
        displayedCurvePoints = result.getDisplayedCurvePoints();
        displayedDrawdownPoints = result.getDisplayedDrawdownPoints();
        displayedDailyReturnPoints = result.getDisplayedDailyReturnPoints();
        defaultCurveMeta = result.getDefaultCurveMeta();
        curveBaseBalance = result.getCurveBaseBalance();
        clearSharedCurveHighlight();
        refreshAnalysisSummaryCards();
    }

    // 绑定后台已准备好的曲线投影结果，避免主线程再次重复推导附图。
    private void applyPreparedCurveProjection(AccountDeferredSnapshotRenderHelper.CurveProjection curveProjection) {
        if (curveRenderHelper == null) {
            return;
        }
        binding.cardCurveSection.setVisibility(View.VISIBLE);
        binding.tvCurveMeta.setVisibility(View.VISIBLE);
        AccountStatsCurveRenderHelper.RenderResult result = curveRenderHelper.applyPreparedProjection(
                curveProjection,
                latestCurveIndicators,
                secondarySectionsAttached,
                isPrivacyMasked()
        );
        displayedCurvePoints = result.getDisplayedCurvePoints();
        displayedDrawdownPoints = result.getDisplayedDrawdownPoints();
        displayedDailyReturnPoints = result.getDisplayedDailyReturnPoints();
        defaultCurveMeta = result.getDefaultCurveMeta();
        curveBaseBalance = result.getCurveBaseBalance();
        refreshAnalysisSummaryCards();
        clearSharedCurveHighlight();
    }

    // 把任一子图的时间点同步成多联图共享十字光标。
    private void applySharedCurveHighlight(long timestamp, float xRatio, boolean preferExactTimestamp) {
        if (isPrivacyMasked()) {
            clearSharedCurveHighlight();
            return;
        }
        AccountCurveHighlightHelper.HighlightSnapshot snapshot =
                AccountCurveHighlightHelper.resolveSharedHighlight(
                        displayedCurvePoints,
                        displayedDrawdownPoints,
                        displayedDailyReturnPoints,
                        timestamp,
                        xRatio,
                        preferExactTimestamp
                );
        if (snapshot == null) {
            clearSharedCurveHighlight();
            return;
        }
        CurvePoint point = snapshot.getCurvePoint();
        CurveAnalyticsHelper.DrawdownPoint drawdownPoint = snapshot.getDrawdownPoint();
        CurveAnalyticsHelper.DailyReturnPoint dailyReturnPoint = snapshot.getDailyReturnPoint();
        List<String> extraLines = new ArrayList<>();
        extraLines.add("仓位 " + formatPercentValue(point.getPositionRatio(), false));
        extraLines.add("回撤 " + formatPercentValue(drawdownPoint == null ? null : drawdownPoint.getDrawdownRate(), false));
        extraLines.add("日收益 " + formatPercentValue(dailyReturnPoint == null ? null : dailyReturnPoint.getReturnRate(), true));
        float syncedRatio = AccountCurveHighlightHelper.resolveTimestampRatio(displayedCurvePoints, snapshot.getTargetTimestamp());
        syncingCurveHighlight = true;
        binding.tvCurveMeta.setText(isPrivacyMasked()
                ? AccountStatsPrivacyFormatter.maskValue(defaultCurveMeta, true)
                : buildHighlightCurveMeta(snapshot));
        binding.equityCurveView.setTooltipExtraLines(extraLines);
        binding.equityCurveView.syncHighlightPoint(point, snapshot.getTargetTimestamp(), syncedRatio >= 0f ? syncedRatio : xRatio);
        binding.positionRatioChartView.syncHighlightTimestamp(snapshot.getTargetTimestamp(), syncedRatio >= 0f ? syncedRatio : xRatio);
        binding.drawdownChartView.syncHighlightTimestamp(snapshot.getTargetTimestamp(), syncedRatio >= 0f ? syncedRatio : xRatio);
        binding.dailyReturnChartView.syncHighlightTimestamp(snapshot.getTargetTimestamp(), syncedRatio >= 0f ? syncedRatio : xRatio);
        syncingCurveHighlight = false;
    }

    // 清除多联图共享十字光标和附带弹窗。
    private void clearSharedCurveHighlight() {
        syncingCurveHighlight = true;
        binding.equityCurveView.setTooltipExtraLines(null);
        binding.equityCurveView.setTooltipPointOverride(null);
        binding.equityCurveView.clearSyncedHighlight();
        binding.positionRatioChartView.clearSyncedHighlight();
        binding.drawdownChartView.clearSyncedHighlight();
        binding.dailyReturnChartView.clearSyncedHighlight();
        binding.tvCurveMeta.setText(isPrivacyMasked()
                ? AccountStatsPrivacyFormatter.maskValue(defaultCurveMeta, true)
                : defaultCurveMeta);
        syncingCurveHighlight = false;
    }

    // 生成长按时的区间曲线摘要，让图外信息栏也随手指位置实时变化。
    private String buildHighlightCurveMeta(AccountCurveHighlightHelper.HighlightSnapshot snapshot) {
        if (snapshot == null) {
            return defaultCurveMeta;
        }
        CurvePoint point = snapshot.getCurvePoint();
        CurveAnalyticsHelper.DrawdownPoint drawdownPoint = snapshot.getDrawdownPoint();
        CurveAnalyticsHelper.DailyReturnPoint dailyReturnPoint = snapshot.getDailyReturnPoint();
        double rangeReturn = safeDivide(point.getEquity() - curveBaseBalance, Math.max(1d, curveBaseBalance)) * 100d;
        return String.format(Locale.getDefault(),
                "时间 %s | 当前净值 $%s | 当前结余 $%s | 仓位 %s | 回撤 %s | 日收益 %s | 区间收益 %+.2f%%",
                FormatUtils.formatTime(snapshot.getTargetTimestamp()),
                FormatUtils.formatPrice(point.getEquity()),
                FormatUtils.formatPrice(point.getBalance()),
                formatPercentValue(point.getPositionRatio(), false),
                formatPercentValue(drawdownPoint == null ? null : drawdownPoint.getDrawdownRate(), false),
                formatPercentValue(dailyReturnPoint == null ? null : dailyReturnPoint.getReturnRate(), true),
                rangeReturn);
    }

    // 找到当前主图里离目标时间最近的点。
    @Nullable
    private CurvePoint findNearestCurvePoint(long timestamp) {
        if (displayedCurvePoints == null || displayedCurvePoints.isEmpty()) {
            return null;
        }
        CurvePoint bestPoint = displayedCurvePoints.get(0);
        long bestDistance = Math.abs(bestPoint.getTimestamp() - timestamp);
        for (CurvePoint point : displayedCurvePoints) {
            long distance = Math.abs(point.getTimestamp() - timestamp);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = point;
            }
        }
        return bestPoint;
    }

    // 找到离目标时间最近的回撤点。
    @Nullable
    private CurveAnalyticsHelper.DrawdownPoint findNearestDrawdownPoint(long timestamp) {
        if (displayedDrawdownPoints == null || displayedDrawdownPoints.isEmpty()) {
            return null;
        }
        CurveAnalyticsHelper.DrawdownPoint bestPoint = displayedDrawdownPoints.get(0);
        long bestDistance = Math.abs(bestPoint.getTimestamp() - timestamp);
        for (CurveAnalyticsHelper.DrawdownPoint point : displayedDrawdownPoints) {
            long distance = Math.abs(point.getTimestamp() - timestamp);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = point;
            }
        }
        return bestPoint;
    }

    // 找到离目标时间最近的日收益点。
    @Nullable
    private CurveAnalyticsHelper.DailyReturnPoint findNearestDailyReturnPoint(long timestamp) {
        if (displayedDailyReturnPoints == null || displayedDailyReturnPoints.isEmpty()) {
            return null;
        }
        CurveAnalyticsHelper.DailyReturnPoint bestPoint = displayedDailyReturnPoints.get(0);
        long bestDistance = Math.abs(bestPoint.getTimestamp() - timestamp);
        for (CurveAnalyticsHelper.DailyReturnPoint point : displayedDailyReturnPoints) {
            long distance = Math.abs(point.getTimestamp() - timestamp);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPoint = point;
            }
        }
        return bestPoint;
    }

    // 统一格式化附图百分比，缺数据时用 --。
    private String formatPercentValue(@Nullable Double value, boolean alwaysShowSign) {
        if (value == null) {
            return "--";
        }
        if (alwaysShowSign) {
            return String.format(Locale.getDefault(), "%+.2f%%", value * 100d);
        }
        return String.format(Locale.getDefault(), "%.2f%%", value * 100d);
    }

    private List<AccountMetric> buildTradeStatsMetrics(List<AccountMetric> snapshotStats) {
        if (snapshotStats != null && !snapshotStats.isEmpty()) {
            return new ArrayList<>(snapshotStats);
        }
        return new ArrayList<>();
    }

    private void renderReturnStatsTable(List<CurvePoint> source) {
        if (returnsTableHelper == null) {
            return;
        }
        AccountStatsReturnsTableHelper.RenderResult result = returnsTableHelper.render(
                new AccountStatsReturnsTableHelper.RenderRequest(
                        source,
                        baseTrades,
                        allCurvePoints,
                        AccountStatsReturnsTableHelper.ReturnStatsMode.valueOf(returnStatsMode.name()),
                        returnStatsAnchorDateMs,
                        manualCurveRangeEnabled,
                        manualCurveRangeStartMs,
                        manualCurveRangeEndMs
                )
        );
        returnStatsAnchorDateMs = result.getResolvedAnchorDateMs();
    }

    private String formatMonthLabel(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        return String.format(Locale.getDefault(), "%d年%d月",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1);
    }

    private boolean shouldUseTradeBasedReturns() {
        return baseTrades != null && !baseTrades.isEmpty();
    }

    private long resolveTradeReturnReferenceTime() {
        if (baseTrades == null || baseTrades.isEmpty()) {
            return System.currentTimeMillis();
        }
        long latest = 0L;
        for (TradeRecordItem item : baseTrades) {
            if (item == null) {
                continue;
            }
            latest = Math.max(latest, resolveCloseTime(item));
        }
        if (returnStatsAnchorDateMs <= 0L || returnStatsAnchorDateMs > latest) {
            returnStatsAnchorDateMs = latest;
        }
        return Math.min(returnStatsAnchorDateMs, latest);
    }

    private void ensureReturnStatsAnchor() {
        if (allCurvePoints == null || allCurvePoints.isEmpty()) {
            returnStatsAnchorDateMs = 0L;
            return;
        }
        long latest = allCurvePoints.get(allCurvePoints.size() - 1).getTimestamp();
        if (returnStatsAnchorDateMs <= 0L || returnStatsAnchorDateMs > latest) {
            returnStatsAnchorDateMs = latest;
        }
    }

    private long resolveReturnStatsReferenceTime(List<CurvePoint> source) {
        if (shouldUseTradeBasedReturns()) {
            return resolveTradeReturnReferenceTime();
        }
        long latest = source.get(source.size() - 1).getTimestamp();
        if (returnStatsAnchorDateMs <= 0L) {
            returnStatsAnchorDateMs = latest;
            return latest;
        }
        return Math.min(returnStatsAnchorDateMs, latest);
    }

    private void legacyRenderDailyReturnsTableFromTrades(long referenceTime) {
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setShrinkAllColumns(true);
        table.setStretchAllColumns(true);
        table.addView(createSimpleHeaderRow(new String[]{"一", "二", "三", "四", "五", "六", "日"}, 48));

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(referenceTime);
        int year = target.get(Calendar.YEAR);
        int month = target.get(Calendar.MONTH);

        Map<Integer, MonthReturnInfo> dayInfoMap = new LinkedHashMap<>();
        for (TradeRecordItem trade : baseTrades) {
            if (trade == null) {
                continue;
            }
            long closeTime = resolveCloseTime(trade);
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
                info.startMs = startOfDay(closeTime);
                info.endMs = endOfDay(closeTime);
                info.hasData = true;
                dayInfoMap.put(day, info);
            }
            info.returnAmount += trade.getProfit() + trade.getStorageFee();
        }
        for (MonthReturnInfo info : dayInfoMap.values()) {
            info.returnRate = AccountPeriodReturnHelper.resolvePeriodReturnRate(
                    allCurvePoints,
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
            TableRow tableRow = new TableRow(this);
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || dayValue > daysInMonth) {
                    String ghostLabel = index < firstWeek
                            ? String.valueOf(prevDaysInMonth - (firstWeek - index) + 1)
                            : String.valueOf(nextDayValue++);
                    View ghostCell = createDailyReturnsCell(
                            ghostLabel,
                            null,
                            ContextCompat.getColor(this, R.color.text_secondary),
                            null,
                            null,
                            null);
                    applyReturnsCellLayout(ghostCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                    tableRow.addView(ghostCell);
                    continue;
                }

                MonthReturnInfo info = dayInfoMap.get(dayValue);
                String valueText = formatReturnValue(
                        info == null ? 0d : info.returnRate,
                        info == null ? 0d : info.returnAmount,
                        true);
                int color = resolveReturnDisplayColor(
                        info == null ? 0d : info.returnRate,
                        info == null ? 0d : info.returnAmount,
                        R.color.text_secondary);
                View.OnClickListener click = null;
                Double heatRate = 0d;
                if (info != null && info.hasData && info.endMs > info.startMs) {
                    long startMs = info.startMs;
                    long endMs = info.endMs;
                    click = v -> applyCurveRangeFromTableSelection(startMs, endMs);
                    heatRate = info.returnRate;
                }
                View dayCell = createDailyReturnsCell(
                        String.valueOf(dayValue),
                        valueText,
                        ContextCompat.getColor(this, R.color.text_primary),
                        color,
                        click,
                        heatRate);
                applyReturnsCellLayout(dayCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                        RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                tableRow.addView(dayCell);
                dayValue++;
            }
            table.addView(tableRow);
        }
    }

    private void legacyRenderMonthlyReturnsTableFromTrades() {
        legacyRebuildMonthlyTableThreeRowsV4(binding.tableMonthlyReturns, legacyBuildMonthlyReturnRowsFromTrades(baseTrades));
    }

    private void legacyRenderYearlyReturnsTableFromTrades() {
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        String valueHeader = returnValueMode == ReturnValueMode.RATE ? "收益率" : "收益额";
        table.addView(createAlignedReturnsRow("年份", valueHeader, true, null, null));

        List<YearlyReturnRow> rows = legacyBuildMonthlyReturnRowsFromTrades(baseTrades);
        for (YearlyReturnRow row : rows) {
            int color = resolveReturnDisplayColor(row.yearReturnRate, row.yearReturnAmount, R.color.text_secondary);
            String valueText = formatReturnValue(row.yearReturnRate, row.yearReturnAmount);
            long startMs = row.startMs;
            long endMs = row.endMs;
            table.addView(createAlignedReturnsRow(
                    row.year + "年",
                    valueText,
                    false,
                    color,
                    row.yearReturnRate,
                    v -> applyCurveRangeFromTableSelection(startMs, endMs)));
        }
    }

    private void legacyRenderStageReturnsTableFromTrades(long referenceTime) {
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        String valueHeader = returnValueMode == ReturnValueMode.RATE ? "收益率" : "收益额";
        table.addView(createAlignedReturnsRow("阶段", valueHeader, true, null, null));

        long endMs = endOfDay(referenceTime);
        long allStart = resolveTradeRangeStart(baseTrades);
        List<StageRange> stageRanges = new ArrayList<>();
        stageRanges.add(new StageRange("近1日", endMs - 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近7日", endMs - 7L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近30日", endMs - 30L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近3月", endMs - 90L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近1年", endMs - 365L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("今年以来", startOfYear(endMs), endMs));
        stageRanges.add(new StageRange("全部", allStart, endMs));
        if (manualCurveRangeEnabled) {
            stageRanges.add(new StageRange("自定义区间", manualCurveRangeStartMs, manualCurveRangeEndMs));
        }

        for (StageRange stage : stageRanges) {
            MonthReturnInfo info = buildTradeReturnInfo(baseTrades, stage.startMs, stage.endMs);
            if (info == null || !info.hasData) {
                table.addView(createAlignedReturnsRow(stage.label, "--", false, null, null));
                continue;
            }
            int color = resolveReturnDisplayColor(info.returnRate, info.returnAmount, R.color.text_secondary);
            String valueText = formatReturnValue(info.returnRate, info.returnAmount);
            table.addView(createAlignedReturnsRow(
                    stage.label,
                    valueText,
                    false,
                    color,
                    info.returnRate,
                    v -> applyCurveRangeFromTableSelection(stage.startMs, stage.endMs)));
        }
    }

    private long endOfDay(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private long startOfDay(long timeMs) {
        Calendar calendar = Calendar.getInstance(BEIJING_TIME_ZONE);
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private void renderDailyReturnsTable(List<CurvePoint> source, long referenceTime) {
        List<CurvePoint> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));
        if (sorted.size() < 2) {
            return;
        }

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
            int y = calendar.get(Calendar.YEAR);
            int m = calendar.get(Calendar.MONTH);
            int d = calendar.get(Calendar.DAY_OF_MONTH);
            int dayKey = y * 10_000 + (m + 1) * 100 + d;

            closeByDay.put(dayKey, point.getBalance());
            if (dayOrder.isEmpty() || dayOrder.get(dayOrder.size() - 1) != dayKey) {
                dayOrder.add(dayKey);
            }

            if (y == year && m == month) {
                DayBucket bucket = dayBuckets.get(d);
                if (bucket == null) {
                    bucket = new DayBucket(d);
                    dayBuckets.put(d, bucket);
                }
                if (bucket.startMs == 0L || point.getTimestamp() < bucket.startMs) {
                    bucket.startMs = point.getTimestamp();
                }
                if (point.getTimestamp() >= bucket.endMs) {
                    bucket.endMs = point.getTimestamp();
                    bucket.closeEquity = point.getBalance();
                }
            }
        }

        rebuildDailyTableV3(binding.tableMonthlyReturns, year, month, dayBuckets, closeByDay, dayOrder);
    }

    private void renderMonthlyReturnsTable(List<CurvePoint> source) {
        List<YearlyReturnRow> rows = buildMonthlyReturnRows(source);
        if (rows.isEmpty()) {
            return;
        }
        legacyRebuildMonthlyTableThreeRowsV4(binding.tableMonthlyReturns, rows);
    }

    private void rebuildDailyTable(TableLayout table,
                                   int year,
                                   int month,
                                   Map<Integer, DayBucket> dayBuckets,
                                   Map<Integer, Double> closeByDay,
                                   List<Integer> dayOrder) {
        table.removeAllViews();
        table.addView(createSimpleHeaderRow(new String[]{"一", "二", "三", "四", "五", "六", "日"}, 46));

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(year, month, 1, 0, 0, 0);
        firstDay.set(Calendar.MILLISECOND, 0);
        int firstWeek = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);

        int totalCells = firstWeek + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7d);
        int day = 1;
        for (int row = 0; row < rows; row++) {
            TableRow tableRow = new TableRow(this);
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || day > daysInMonth) {
                    tableRow.addView(createReturnsCell("", 46, false, null, null));
                    continue;
                }

                DayBucket bucket = dayBuckets.get(day);
                if (bucket == null) {
                    CharSequence plainLabel = buildLabelValueText(day + "日", "--", null);
                    tableRow.addView(createReturnsCell(plainLabel, 46, false, null, null));
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
                    double dayReturn = safeDivide(bucket.closeEquity - prevClose, prevClose);
                    int color = resolveReturnDisplayColor(dayReturn, dayAmount, R.color.text_primary);
                    String valueText = formatReturnValue(dayReturn, dayAmount, true);
                    long startMs = bucket.startMs;
                    long endMs = bucket.endMs;
                    tableRow.addView(createReturnsCell(
                            buildLabelValueText(day + "日", valueText, color),
                            46,
                            false,
                            null,
                            v -> applyCurveRangeFromTableSelection(startMs, endMs)));
                }
                day++;
            }
            table.addView(tableRow);
        }
    }

    private void rebuildMonthlyTableTwoRows(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();

        for (YearlyReturnRow row : rows) {
            View.OnClickListener yearClick = null;
            if (row.startMs > 0L && row.endMs > row.startMs) {
                long startMs = row.startMs;
                long endMs = row.endMs;
                yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
            }

            TableRow firstRow = new TableRow(this);
            int yearColor = resolveReturnDisplayColor(row.yearReturnRate, row.yearReturnAmount, R.color.text_primary);
            String yearValueText = formatReturnValue(row.yearReturnRate, row.yearReturnAmount);
            TextView yearCell = createReturnsCell(
                    buildLabelValueText(row.year + "年", yearValueText, yearColor),
                    72,
                    false,
                    null,
                    yearClick);
            TableRow.LayoutParams yearParams = (TableRow.LayoutParams) yearCell.getLayoutParams();
            yearParams.height = dpToPx(102);
            yearCell.setLayoutParams(yearParams);
            firstRow.addView(yearCell);
            for (int month = 1; month <= 6; month++) {
                firstRow.addView(createMonthReturnCell(month, row.monthly.get(month), 46));
            }
            table.addView(firstRow);

            TableRow secondRow = new TableRow(this);
            TextView placeholder = createReturnsCell("", 72, false, null, null);
            placeholder.setVisibility(View.INVISIBLE);
            placeholder.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            secondRow.addView(placeholder);
            for (int month = 7; month <= 12; month++) {
                secondRow.addView(createMonthReturnCell(month, row.monthly.get(month), 46));
            }
            table.addView(secondRow);
        }
    }

    private TextView createMonthReturnCell(int month, @Nullable MonthReturnInfo info, int widthDp) {
        if (info == null || !info.hasData) {
            return createReturnsCell(buildLabelValueText(month + "月", "--", null), widthDp, false, null, null);
        }
        int textColor = resolveReturnDisplayColor(info.returnRate, info.returnAmount, R.color.text_primary);
        String text = formatReturnValue(info.returnRate, info.returnAmount);
        long startMs = info.startMs;
        long endMs = info.endMs;
        return createReturnsCell(
                buildLabelValueText(month + "月", text, textColor),
                widthDp,
                false,
                null,
                v -> applyCurveRangeFromTableSelection(startMs, endMs));
    }

    private void renderYearlyReturnsTable(List<CurvePoint> source) {
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);

        List<CurvePoint> sorted = new ArrayList<>(source);
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

        String valueHeader = returnValueMode == ReturnValueMode.RATE ? "收益率" : "收益额";
        table.addView(createAlignedReturnsRow("年份", valueHeader, true, null, null));

        double previousClose = Math.max(1e-9, sorted.get(0).getBalance());
        for (Map.Entry<Integer, PeriodBucket> entry : yearBuckets.entrySet()) {
            PeriodBucket bucket = entry.getValue();
            double yearAmount = bucket.closeEquity - previousClose;
            double yearReturn = safeDivide(yearAmount, previousClose);
            previousClose = bucket.closeEquity;

            int color = resolveReturnDisplayColor(yearReturn, yearAmount, R.color.text_secondary);
            String valueText = formatReturnValue(yearReturn, yearAmount);
            long startMs = bucket.startMs;
            long endMs = bucket.endMs;
            table.addView(createAlignedReturnsRow(
                    entry.getKey() + "年",
                    valueText,
                    false,
                    color,
                    yearReturn,
                    v -> applyCurveRangeFromTableSelection(startMs, endMs)));
        }
    }

    private void renderStageReturnsTable(List<CurvePoint> source, long referenceTime) {
        TableLayout table = binding.tableMonthlyReturns;
        table.removeAllViews();
        table.setStretchAllColumns(false);
        table.setShrinkAllColumns(false);
        String valueHeader = returnValueMode == ReturnValueMode.RATE ? "收益率" : "收益额";
        table.addView(createAlignedReturnsRow("阶段", valueHeader, true, null, null));

        long endMs = Math.min(source.get(source.size() - 1).getTimestamp(), endOfDay(referenceTime));
        long allStart = source.get(0).getTimestamp();

        List<StageRange> stageRanges = new ArrayList<>();
        stageRanges.add(new StageRange("近1日", endMs - 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近7日", endMs - 7L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近30日", endMs - 30L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近3月", endMs - 90L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("近1年", endMs - 365L * 24L * 60L * 60L * 1000L, endMs));
        stageRanges.add(new StageRange("今年以来", startOfYear(endMs), endMs));
        stageRanges.add(new StageRange("全部", allStart, endMs));
        if (manualCurveRangeEnabled) {
            stageRanges.add(new StageRange("自定义区间", manualCurveRangeStartMs, manualCurveRangeEndMs));
        }

        for (StageRange stage : stageRanges) {
            List<CurvePoint> range = filterCurveByManualRange(source, stage.startMs, stage.endMs);
            if (range.size() < 2) {
                table.addView(createAlignedReturnsRow(stage.label, "--", false, null, null));
                continue;
            }

            double startEquity = range.get(0).getBalance();
            double endEquity = range.get(range.size() - 1).getBalance();
            double profit = endEquity - startEquity;
            double rate = safeDivide(profit, startEquity);
            int color = resolveReturnDisplayColor(rate, profit, R.color.text_secondary);
            String valueText = formatReturnValue(rate, profit);

            table.addView(createAlignedReturnsRow(
                    stage.label,
                    valueText,
                    false,
                    color,
                    rate,
                    v -> applyCurveRangeFromTableSelection(stage.startMs, stage.endMs)));
        }
    }

    private void rebuildDailyTableV2(TableLayout table,
                                     int year,
                                     int month,
                                     Map<Integer, DayBucket> dayBuckets,
                                     Map<Integer, Double> closeByDay,
                                     List<Integer> dayOrder) {
        table.removeAllViews();
        table.addView(createSimpleHeaderRow(new String[]{"一", "二", "三", "四", "五", "六", "日"}, 48));

        Calendar firstDay = Calendar.getInstance();
        firstDay.set(year, month, 1, 0, 0, 0);
        firstDay.set(Calendar.MILLISECOND, 0);
        int firstWeek = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH);

        int totalCells = firstWeek + daysInMonth;
        int rows = (int) Math.ceil(totalCells / 7d);
        int day = 1;
        for (int row = 0; row < rows; row++) {
            TableRow tableRow = new TableRow(this);
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || day > daysInMonth) {
                    tableRow.addView(createReturnsCell("", 48, false, null, null));
                    continue;
                }

                DayBucket bucket = dayBuckets.get(day);
                if (bucket == null) {
                    tableRow.addView(createReturnsCell(
                            buildLabelValueText(day + "日", "--", null),
                            48,
                            false,
                            null,
                            null));
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
                    int color = resolveReturnDisplayColor(dayReturn, dayAmount, R.color.text_primary);
                    String valueText = formatReturnValue(dayReturn, dayAmount, true);
                    long startMs = bucket.startMs;
                    long endMs = bucket.endMs;
                    tableRow.addView(createReturnsCell(
                            buildLabelValueText(day + "日", valueText, color),
                            48,
                            false,
                            null,
                            v -> applyCurveRangeFromTableSelection(startMs, endMs)));
                }
                day++;
            }
            table.addView(tableRow);
        }
    }

    private void rebuildMonthlyTableTwoRowsV2(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();
        for (YearlyReturnRow row : rows) {
            View.OnClickListener yearClick = null;
            if (row.startMs > 0L && row.endMs > row.startMs) {
                long startMs = row.startMs;
                long endMs = row.endMs;
                yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
            }

            int yearColor = resolveReturnDisplayColor(row.yearReturnRate, row.yearReturnAmount, R.color.text_primary);
            String yearValueText = formatReturnValue(row.yearReturnRate, row.yearReturnAmount);

            TableRow firstRow = new TableRow(this);
            TextView yearCell = createReturnsCell(
                    buildLabelValueText(row.year + "年", yearValueText, yearColor),
                    74,
                    false,
                    null,
                    yearClick);
            TableRow.LayoutParams yearParams = (TableRow.LayoutParams) yearCell.getLayoutParams();
            yearParams.height = dpToPx(106);
            yearCell.setLayoutParams(yearParams);
            firstRow.addView(yearCell);
            for (int month = 1; month <= 6; month++) {
                firstRow.addView(createMonthReturnCellV2(month, row.monthly.get(month), 50));
            }
            table.addView(firstRow);

            TableRow secondRow = new TableRow(this);
            TextView placeholder = createReturnsCell("", 74, false, null, null);
            placeholder.setVisibility(View.INVISIBLE);
            placeholder.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent));
            secondRow.addView(placeholder);
            for (int month = 7; month <= 12; month++) {
                secondRow.addView(createMonthReturnCellV2(month, row.monthly.get(month), 50));
            }
            table.addView(secondRow);
        }
    }

    private TextView createMonthReturnCellV2(int month, @Nullable MonthReturnInfo info, int widthDp) {
        if (info == null || !info.hasData) {
            return createReturnsCell(buildLabelValueText(month + "月", "--", null), widthDp, false, null, null);
        }
        int textColor = resolveReturnDisplayColor(info.returnRate, info.returnAmount, R.color.text_primary);
        String text = formatReturnValue(info.returnRate, info.returnAmount);
        long startMs = info.startMs;
        long endMs = info.endMs;
        return createReturnsCell(
                buildLabelValueText(month + "月", text, textColor),
                widthDp,
                false,
                null,
                v -> applyCurveRangeFromTableSelection(startMs, endMs));
    }

    private void rebuildDailyTableV3(TableLayout table,
                                     int year,
                                     int month,
                                     Map<Integer, DayBucket> dayBuckets,
                                     Map<Integer, Double> closeByDay,
                                     List<Integer> dayOrder) {
        table.removeAllViews();
        table.setShrinkAllColumns(true);
        table.setStretchAllColumns(true);

        TableRow headerRow = new TableRow(this);
        String[] weekHeaders = new String[]{"一", "二", "三", "四", "五", "六", "日"};
        for (String header : weekHeaders) {
            TextView headerCell = createReturnsCell(header, 0, true, null, null);
            applyReturnsCellLayout(headerCell, 0, 1f, RETURNS_HEADER_HEIGHT_DP,
                    RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
            headerCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
            headerRow.addView(headerCell);
        }
        table.addView(headerRow);

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
            TableRow tableRow = new TableRow(this);
            for (int col = 0; col < 7; col++) {
                int index = row * 7 + col;
                if (index < firstWeek || day > daysInMonth) {
                    String ghostLabel;
                    if (index < firstWeek) {
                        int prevDay = prevDaysInMonth - (firstWeek - index) + 1;
                        ghostLabel = String.valueOf(prevDay);
                    } else {
                        ghostLabel = String.valueOf(nextDayValue++);
                    }
                    View emptyCell = createDailyReturnsCell(
                            ghostLabel,
                            null,
                            ContextCompat.getColor(this, R.color.text_secondary),
                            null,
                            null,
                            null);
                    applyReturnsCellLayout(emptyCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                    tableRow.addView(emptyCell);
                    continue;
                }

                DayBucket bucket = dayBuckets.get(day);
                if (bucket == null) {
                    View dayCell = createDailyReturnsCell(
                            String.valueOf(day),
                            formatReturnValue(0d, 0d, true),
                            ContextCompat.getColor(this, R.color.text_primary),
                            resolveReturnDisplayColor(0d, 0d, R.color.text_secondary),
                            null,
                            0d);
                    applyReturnsCellLayout(dayCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
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
                    int color = resolveReturnDisplayColor(dayReturn, dayAmount, R.color.text_secondary);
                    String valueText = formatReturnValue(dayReturn, dayAmount, true);
                    long startMs = bucket.startMs;
                    long endMs = bucket.endMs;
                    View dayCell = createDailyReturnsCell(
                            String.valueOf(day),
                            valueText,
                            ContextCompat.getColor(this, R.color.text_primary),
                            color,
                            v -> applyCurveRangeFromTableSelection(startMs, endMs),
                            dayReturn);
                    applyReturnsCellLayout(dayCell, 0, 1f, RETURNS_BODY_HEIGHT_DP,
                            RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP, RETURNS_CELL_MARGIN_DP);
                    tableRow.addView(dayCell);
                }
                day++;
            }
            table.addView(tableRow);
        }
    }

    private void rebuildMonthlyTableTwoRowsV3(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();
        table.setShrinkAllColumns(false);
        table.setStretchAllColumns(false);
        table.addView(createMonthlyReturnsHeaderRow());

        for (YearlyReturnRow row : rows) {
            table.addView(createMonthlyReturnsYearRow(row));
        }
    }

    private void legacyRebuildMonthlyTableThreeRowsV4(TableLayout table, List<YearlyReturnRow> rows) {
        table.removeAllViews();
        table.setShrinkAllColumns(false);
        table.setStretchAllColumns(false);
        for (YearlyReturnRow row : rows) {
            table.addView(legacyCreateMonthlyGroupedBlock(row));
        }
    }

    LinearLayout createMonthlyGroupedBlock(AccountStatsReturnsTableHelper.YearlyReturnRow rowData) {
        return legacyCreateMonthlyGroupedBlock(convertReturnsTableRow(rowData));
    }

    private LinearLayout legacyCreateMonthlyGroupedBlock(YearlyReturnRow rowData) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.HORIZONTAL);
        block.setGravity(Gravity.TOP);
        TableLayout.LayoutParams blockParams = new TableLayout.LayoutParams(
                TableLayout.LayoutParams.MATCH_PARENT,
                TableLayout.LayoutParams.WRAP_CONTENT);
        blockParams.topMargin = dpToPx(RETURNS_CELL_MARGIN_DP);
        block.setLayoutParams(blockParams);

        TextView yearCell = createMonthlyYearSummaryCell(rowData);
        LinearLayout.LayoutParams yearParams = new LinearLayout.LayoutParams(
                0,
                dpToPx(RETURNS_MONTH_GROUP_HEIGHT_DP * 3 + RETURNS_CELL_MARGIN_DP * 2),
                0.94f);
        yearParams.rightMargin = dpToPx(RETURNS_CELL_MARGIN_DP);
        yearCell.setLayoutParams(yearParams);
        block.addView(yearCell);

        LinearLayout monthColumn = new LinearLayout(this);
        monthColumn.setOrientation(LinearLayout.VERTICAL);
        monthColumn.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                4f));
        monthColumn.addView(createMonthlyGroupedLine(rowData, 1, 4));
        monthColumn.addView(createMonthlyGroupedLine(rowData, 5, 8));
        monthColumn.addView(createMonthlyGroupedLine(rowData, 9, 12));
        block.addView(monthColumn);
        return block;
    }

    private YearlyReturnRow convertReturnsTableRow(AccountStatsReturnsTableHelper.YearlyReturnRow rowData) {
        YearlyReturnRow legacy = new YearlyReturnRow(rowData.year);
        legacy.startMs = rowData.startMs;
        legacy.endMs = rowData.endMs;
        legacy.yearReturnAmount = rowData.yearReturnAmount;
        legacy.yearReturnRate = rowData.yearReturnRate;
        for (Map.Entry<Integer, AccountStatsReturnsTableHelper.MonthReturnInfo> entry : rowData.monthly.entrySet()) {
            legacy.monthly.put(entry.getKey(), convertReturnsTableMonth(entry.getValue()));
        }
        return legacy;
    }

    @Nullable
    private MonthReturnInfo convertReturnsTableMonth(@Nullable AccountStatsReturnsTableHelper.MonthReturnInfo info) {
        if (info == null) {
            return null;
        }
        MonthReturnInfo legacy = new MonthReturnInfo();
        legacy.startMs = info.startMs;
        legacy.endMs = info.endMs;
        legacy.startEquity = info.startEquity;
        legacy.endEquity = info.endEquity;
        legacy.returnAmount = info.returnAmount;
        legacy.returnRate = info.returnRate;
        legacy.hasData = info.hasData;
        return legacy;
    }

    private LinearLayout createMonthlyGroupedLine(YearlyReturnRow rowData,
                                                  int startMonth,
                                                  int endMonth) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        for (int month = startMonth; month <= endMonth; month++) {
            TextView cell = createMonthlyGroupedCell(month, rowData.monthly.get(month));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    dpToPx(RETURNS_MONTH_GROUP_HEIGHT_DP),
                    1f);
            if (month < endMonth) {
                params.rightMargin = dpToPx(RETURNS_CELL_MARGIN_DP);
            }
            if (endMonth < 12) {
                params.bottomMargin = dpToPx(RETURNS_CELL_MARGIN_DP);
            }
            cell.setLayoutParams(params);
            row.addView(cell);
        }
        return row;
    }

    private TextView createMonthlyYearSummaryCell(YearlyReturnRow rowData) {
        View.OnClickListener yearClick = null;
        if (rowData.startMs > 0L && rowData.endMs > rowData.startMs) {
            long startMs = rowData.startMs;
            long endMs = rowData.endMs;
            yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
        }
        int valueColor = resolveReturnDisplayColor(rowData.yearReturnRate, rowData.yearReturnAmount, R.color.text_primary);
        TextView cell = createReturnsCell(
                buildLabelValueText(rowData.year + "年",
                        formatReturnValue(rowData.yearReturnRate, rowData.yearReturnAmount),
                        valueColor),
                0,
                false,
                null,
                yearClick);
        cell.setGravity(Gravity.CENTER);
        cell.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.8f);
        cell.setPadding(dpToPx(4), dpToPx(3), dpToPx(4), dpToPx(3));
        return cell;
    }

    private TextView createMonthlyGroupedCell(int month, @Nullable MonthReturnInfo info) {
        Integer valueColor = info != null && info.hasData
                ? resolveReturnDisplayColor(info.returnRate, info.returnAmount, R.color.text_primary)
                : null;
        TextView cell = createReturnsCell(
                buildLabelValueText(month + "月", formatMonthlyGroupedValue(info), valueColor),
                0,
                false,
                null,
                resolveMonthlyHeatCellClickListener(info));
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.2f);
        cell.setPadding(dpToPx(3), dpToPx(3), dpToPx(3), dpToPx(3));
        cell.setBackground(buildReturnsCellBackground(resolveMonthlyHeatCellRate(info), false));
        return cell;
    }

    private String formatMonthlyGroupedValue(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData) {
            return "--";
        }
        if (isPrivacyMasked()) {
            return SensitiveDisplayMasker.MASK_TEXT;
        }
        return formatReturnValue(info.returnRate, info.returnAmount);
    }

    private TableRow createMonthlyReturnsHeaderRow() {
        TableRow row = new TableRow(this);
        row.addView(createMonthlyHeatCell("年份", 1.18f, true, null, null, null));
        for (int month = 1; month <= 12; month++) {
            row.addView(createMonthlyHeatCell(month + "月", 1f, true, null, null, null));
        }
        return row;
    }

    private TableRow createMonthlyReturnsYearRow(YearlyReturnRow rowData) {
        TableRow row = new TableRow(this);
        View.OnClickListener yearClick = null;
        if (rowData.startMs > 0L && rowData.endMs > rowData.startMs) {
            long startMs = rowData.startMs;
            long endMs = rowData.endMs;
            yearClick = v -> applyCurveRangeFromTableSelection(startMs, endMs);
        }
        row.addView(createMonthlyHeatCell(rowData.year + "年", 1.18f, false, null, null, yearClick));
        for (int month = 1; month <= 12; month++) {
            row.addView(createMonthlyHeatCell(
                    legacyFormatMonthlyHeatCellValue(rowData.monthly.get(month)),
                    1f,
                    false,
                    resolveMonthlyHeatCellTextColor(rowData.monthly.get(month)),
                    resolveMonthlyHeatCellRate(rowData.monthly.get(month)),
                    resolveMonthlyHeatCellClickListener(rowData.monthly.get(month))));
        }
        return row;
    }

    private String legacyFormatMonthlyHeatCellValue(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData) {
            return "--";
        }
        if (isPrivacyMasked()) {
            return SensitiveDisplayMasker.MASK_TEXT;
        }
        if (returnValueMode == ReturnValueMode.AMOUNT) {
            return formatCompactSignedAmount(info.returnAmount);
        }
        return String.format(Locale.getDefault(), "%+.2f", info.returnRate * 100d);
    }

    private String formatCompactSignedAmount(double amount) {
        double absAmount = Math.abs(amount);
        if (absAmount >= 10_000d) {
            return String.format(Locale.getDefault(), "%+.1fk", amount / 1_000d);
        }
        return String.format(Locale.getDefault(), "%+,.0f", amount);
    }

    @Nullable
    private Integer resolveMonthlyHeatCellTextColor(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData) {
            return null;
        }
        return resolveReturnDisplayColor(info.returnRate, info.returnAmount, R.color.text_primary);
    }

    @Nullable
    private Double resolveMonthlyHeatCellRate(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData) {
            return null;
        }
        return info.returnRate;
    }

    @Nullable
    private View.OnClickListener resolveMonthlyHeatCellClickListener(@Nullable MonthReturnInfo info) {
        if (info == null || !info.hasData || info.startMs <= 0L || info.endMs <= info.startMs) {
            return null;
        }
        long startMs = info.startMs;
        long endMs = info.endMs;
        return v -> applyCurveRangeFromTableSelection(startMs, endMs);
    }

    private TextView createMonthlyHeatCell(CharSequence text,
                                           float weight,
                                           boolean header,
                                           @Nullable Integer textColor,
                                           @Nullable Double heatRate,
                                           @Nullable View.OnClickListener clickListener) {
        TextView cell = createReturnsCell(makeNonBreakingText(text == null ? "" : text.toString()), 0, header, textColor, clickListener);
        applyReturnsCellLayout(cell, 0, weight, header ? RETURNS_HEADER_HEIGHT_DP : RETURNS_BODY_HEIGHT_DP,
                0, RETURNS_CELL_MARGIN_DP, 0, RETURNS_CELL_MARGIN_DP);
        cell.setGravity(Gravity.CENTER);
        cell.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, header ? 8.6f : 7.2f);
        cell.setPadding(dpToPx(header ? 2 : 1), 0, dpToPx(header ? 2 : 1), 0);
        cell.setSingleLine(true);
        cell.setMaxLines(1);
        cell.setMinLines(1);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setBackground(buildReturnsCellBackground(heatRate, header));
        return cell;
    }

    private TableRow createAlignedReturnsRow(CharSequence label,
                                             CharSequence value,
                                             boolean header,
                                             @Nullable Integer valueColor,
                                             @Nullable Double heatRate) {
        return createAlignedReturnsRow(label, value, header, valueColor, heatRate, null);
    }

    private TableRow createAlignedReturnsRow(CharSequence label,
                                             CharSequence value,
                                             boolean header,
                                             @Nullable Integer valueColor,
                                             @Nullable Double heatRate,
                                             @Nullable View.OnClickListener clickListener) {
        TableRow row = new TableRow(this);
        row.addView(createAlignedReturnsCell(label, header, false, 1f, null, heatRate, clickListener));
        row.addView(createAlignedReturnsCell(value, header, true, 1f, valueColor, heatRate, clickListener));
        return row;
    }

    private TextView createAlignedReturnsCell(CharSequence text,
                                              boolean header,
                                              boolean alignEnd,
                                              float weight,
                                              @Nullable Integer textColor,
                                              @Nullable Double heatRate,
                                              @Nullable View.OnClickListener clickListener) {
        TextView cell = new TextView(this);
        applyReturnsCellLayout(cell, 0, weight, header ? RETURNS_HEADER_HEIGHT_DP : RETURNS_STAGE_HEIGHT_DP,
                0, RETURNS_CELL_MARGIN_DP, 0, RETURNS_CELL_MARGIN_DP);
        int horizontalPadding = dpToPx(header ? 8 : 10);
        cell.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        cell.setGravity((alignEnd ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
        cell.setTextAlignment(alignEnd ? View.TEXT_ALIGNMENT_VIEW_END : View.TEXT_ALIGNMENT_VIEW_START);
        cell.setIncludeFontPadding(false);
        cell.setSingleLine(true);
        cell.setMaxLines(1);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        String displayText = text == null ? "--" : text.toString();
        boolean maskedValue = !header && alignEnd && isPrivacyMasked() && !"--".equals(displayText.trim());
        if (maskedValue) {
            displayText = SensitiveDisplayMasker.MASK_TEXT;
        }
        cell.setText(makeNonBreakingText(displayText));
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, header ? 10f : 9.6f);
        int neutralColor = ContextCompat.getColor(this, header ? R.color.text_primary : R.color.text_secondary);
        cell.setTextColor(AccountStatsPrivacyFormatter.resolveValueColor(textColor, neutralColor, maskedValue));
        cell.setBackground(buildReturnsCellBackground(heatRate, header));
        if (clickListener != null) {
            cell.setOnClickListener(clickListener);
            cell.setClickable(true);
        }
        return cell;
    }

    private void applyReturnsCellLayout(View cell,
                                        int widthDp,
                                        float weight,
                                        int heightDp,
                                        int marginLeftDp,
                                        int marginTopDp,
                                        int marginRightDp,
                                        int marginBottomDp) {
        int widthPx = widthDp <= 0 ? 0 : dpToPx(widthDp);
        TableRow.LayoutParams params = new TableRow.LayoutParams(widthPx, dpToPx(heightDp), weight);
        params.setMargins(
                toMarginPx(marginLeftDp),
                toMarginPx(marginTopDp),
                toMarginPx(marginRightDp),
                toMarginPx(marginBottomDp));
        cell.setLayoutParams(params);
    }

    private int toMarginPx(int value) {
        return value < 0 ? value : dpToPx(value);
    }

    private long startOfYear(long timeMs) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long startOfMonth(long timeMs) {
        Calendar calendar = Calendar.getInstance(BEIJING_TIME_ZONE);
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long endOfMonth(long timeMs) {
        Calendar calendar = Calendar.getInstance(BEIJING_TIME_ZONE);
        calendar.setTimeInMillis(timeMs);
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private TableRow createSimpleHeaderRow(String[] headers) {
        return createSimpleHeaderRow(headers, 80);
    }

    private TableRow createSimpleHeaderRow(String[] headers, int widthDp) {
        TableRow row = new TableRow(this);
        for (String text : headers) {
            row.addView(createReturnsCell(text, widthDp, true, null, null));
        }
        return row;
    }

    private CharSequence buildLabelValueText(String label, String value, @Nullable Integer valueColor) {
        String safeLabel = trim(label);
        String safeValue = trim(value);
        if (safeValue.isEmpty()) {
            safeValue = "--";
        }
        boolean maskedValue = isPrivacyMasked() && !"--".equals(safeValue);
        if (maskedValue) {
            safeValue = SensitiveDisplayMasker.MASK_TEXT;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        if (!safeLabel.isEmpty()) {
            builder.append(safeLabel);
            builder.append('\n');
        }
        int valueStart = builder.length();
        builder.append(makeNonBreakingText(safeValue));
        if (valueColor != null && !maskedValue) {
            builder.setSpan(new ForegroundColorSpan(valueColor),
                    valueStart,
                    builder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return builder;
    }

    private String formatReturnValue(double rate, double amount) {
        return formatReturnValue(rate, amount, false);
    }

    private String formatReturnValue(double rate, double amount, boolean dayMode) {
        if (returnValueMode == ReturnValueMode.AMOUNT) {
            return String.format(Locale.getDefault(), "%+,.0f", amount);
        }
        return String.format(Locale.getDefault(), dayMode ? "%+.1f%%" : "%+.2f%%", rate * 100d);
    }

    private TextView createReturnsCell(CharSequence text,
                                       int minWidthDp,
                                       boolean header,
                                       @Nullable Integer textColor,
                                       @Nullable View.OnClickListener clickListener) {
        TextView cell = new TextView(this);
        int baseHeightDp = header ? RETURNS_HEADER_HEIGHT_DP : RETURNS_BODY_HEIGHT_DP;
        TableRow.LayoutParams params = new TableRow.LayoutParams(dpToPx(minWidthDp), dpToPx(baseHeightDp));
        int marginPx = dpToPx(RETURNS_CELL_MARGIN_DP);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);
        cell.setLayoutParams(params);
        int horizontalPadding = dpToPx(header ? 6 : 2);
        int verticalPadding = dpToPx(header ? 4 : 6);
        cell.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        cell.setGravity(android.view.Gravity.CENTER);
        cell.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        cell.setIncludeFontPadding(false);
        cell.setText(text);
        cell.setTextSize(header ? 11f : 8.6f);
        String plain = text == null ? "" : text.toString();
        cell.setEllipsize(null);
        if (plain.contains("\n")) {
            cell.setLines(2);
            cell.setMaxLines(2);
            cell.setLineSpacing(0f, 1.02f);
        } else {
            cell.setLines(1);
            cell.setMaxLines(1);
        }
        cell.setBackground(buildReturnsCellBackground(null, header));
        int defaultColor = ContextCompat.getColor(this, header ? R.color.text_primary : R.color.text_secondary);
        cell.setTextColor(textColor != null ? textColor : defaultColor);
        if (clickListener != null) {
            cell.setOnClickListener(clickListener);
            cell.setClickable(true);
        }
        return cell;
    }

    private View createDailyReturnsCell(String label,
                                        @Nullable String value,
                                        int labelColor,
                                        @Nullable Integer valueColor,
                                        @Nullable View.OnClickListener clickListener,
                                        @Nullable Double heatRate) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        container.setBackground(buildReturnsCellBackground(heatRate, false));
        container.setBaselineAligned(false);

        TextView labelView = new TextView(this);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        labelView.setIncludeFontPadding(false);
        labelView.setText(trim(label));
        labelView.setTextColor(labelColor);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        container.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView valueView = new TextView(this);
        valueView.setGravity(Gravity.CENTER);
        valueView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        valueView.setIncludeFontPadding(false);
        String displayValue = value == null ? "00.0%" : value;
        boolean masked = isPrivacyMasked() && value != null;
        if (masked) {
            displayValue = SensitiveDisplayMasker.MASK_TEXT;
        }
        valueView.setText(makeNonBreakingText(displayValue));
        valueView.setTextColor(masked
                ? ContextCompat.getColor(this, R.color.text_secondary)
                : (valueColor != null ? valueColor : ContextCompat.getColor(this, R.color.text_secondary)));
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.0f);
        valueView.setSingleLine(true);
        valueView.setMaxLines(1);
        valueView.setMinLines(1);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dpToPx(2);
        container.addView(valueView, valueParams);

        if (value == null) {
            valueView.setAlpha(0f);
        }
        if (clickListener != null) {
            container.setOnClickListener(clickListener);
            container.setClickable(true);
        }
        return container;
    }

    private GradientDrawable buildReturnsCellBackground(@Nullable Double rate, boolean header) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(0f);
        drawable.setStroke(dpToPx(1), palette.stroke);
        int fill = header
                ? blendColor(palette.surfaceEnd, palette.textPrimary, 0.10f)
                : palette.surfaceEnd;
        if (!header && rate != null && !isPrivacyMasked()) {
            fill = AccountReturnsHeatStyleHelper.resolveFillColor(
                    palette.surfaceEnd,
                    palette.rise,
                    palette.fall,
                    rate);
        }
        drawable.setColor(fill);
        return drawable;
    }

    private int blendColor(int startColor, int endColor, float ratio) {
        float safeRatio = Math.max(0f, Math.min(1f, ratio));
        int startA = Color.alpha(startColor);
        int startR = Color.red(startColor);
        int startG = Color.green(startColor);
        int startB = Color.blue(startColor);
        int endA = Color.alpha(endColor);
        int endR = Color.red(endColor);
        int endG = Color.green(endColor);
        int endB = Color.blue(endColor);
        return Color.argb(
                Math.round(startA + (endA - startA) * safeRatio),
                Math.round(startR + (endR - startR) * safeRatio),
                Math.round(startG + (endG - startG) * safeRatio),
                Math.round(startB + (endB - startB) * safeRatio));
    }

    private CharSequence makeNonBreakingText(String value) {
        String safe = trim(value);
        if (safe.isEmpty()) {
            return "--";
        }
        return safe.replace(" ", "\u00A0");
    }

    private List<YearlyReturnRow> legacyBuildMonthlyReturnRowsFromTrades(List<TradeRecordItem> trades) {
        List<YearlyReturnRow> rows = new ArrayList<>();
        if (trades == null || trades.isEmpty()) {
            return rows;
        }

        Map<Integer, MonthReturnInfo> monthReturnMap = new TreeMap<>();
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            long closeTime = resolveCloseTime(trade);
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
                info.startMs = startOfMonth(closeTime);
                info.endMs = endOfMonth(closeTime);
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

        int firstKey = monthReturnMap.keySet().iterator().next();
        int lastKey = firstKey;
        for (Integer key : monthReturnMap.keySet()) {
            lastKey = key;
        }

        int firstYear = firstKey / 100;
        int lastYear = lastKey / 100;
        for (int year = firstYear; year <= lastYear; year++) {
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

    @Nullable
    private MonthReturnInfo buildTradeReturnInfo(List<TradeRecordItem> trades, long startMs, long endMs) {
        if (trades == null || trades.isEmpty()) {
            return null;
        }
        MonthReturnInfo info = new MonthReturnInfo();
        info.startMs = startMs;
        info.endMs = endMs;
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            long closeTime = resolveCloseTime(trade);
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

    private long resolveTradeRangeStart(List<TradeRecordItem> trades) {
        if (trades == null || trades.isEmpty()) {
            return 0L;
        }
        long start = Long.MAX_VALUE;
        for (TradeRecordItem trade : trades) {
            if (trade == null) {
                continue;
            }
            long closeTime = resolveCloseTime(trade);
            if (closeTime > 0L) {
                start = Math.min(start, closeTime);
            }
        }
        return start == Long.MAX_VALUE ? 0L : start;
    }

    private List<YearlyReturnRow> buildMonthlyReturnRows(List<CurvePoint> source) {
        List<YearlyReturnRow> rows = new ArrayList<>();
        if (source == null || source.size() < 2) {
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

        int firstKey = monthBuckets.keySet().iterator().next();
        int lastKey = firstKey;
        for (Integer key : monthBuckets.keySet()) {
            lastKey = key;
        }

        int firstYear = firstKey / 100;
        int lastYear = lastKey / 100;
        for (int year = firstYear; year <= lastYear; year++) {
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

    private void applyCurveRangeFromTableSelection(long startMs, long endMs) {
        if (startMs <= 0L || endMs <= startMs) {
            return;
        }
        List<CurvePoint> filtered = filterCurveByManualRange(allCurvePoints, startMs, endMs);
        if (filtered.size() < 2) {
            return;
        }
        manualCurveRangeEnabled = true;
        manualCurveRangeStartMs = startMs;
        manualCurveRangeEndMs = endMs;
        binding.etRangeStart.setText(dateOnlyFormat.format(new Date(startMs)));
        binding.etRangeEnd.setText(dateOnlyFormat.format(new Date(endMs)));
        displayedCurvePoints = filtered;
        renderCurveWithIndicators(displayedCurvePoints);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private String buildTradeDedupeKey(TradeRecordItem item) {
        if (item.getDealTicket() > 0L) {
            return "deal|" + item.getDealTicket();
        }
        if (item.getOrderId() > 0L || item.getPositionId() > 0L) {
            return "trade|" + item.getOrderId() + "|" + item.getPositionId()
                    + "|" + item.getEntryType()
                    + "|" + resolveOpenTime(item)
                    + "|" + resolveCloseTime(item)
                    + "|" + Math.round(Math.abs(item.getQuantity()) * 10_000d);
        }
        String code = trim(item.getCode()).toUpperCase(Locale.ROOT);
        long open = resolveOpenTime(item);
        long close = resolveCloseTime(item);
        long qty = Math.round(Math.abs(item.getQuantity()) * 10_000d);
        long price = Math.round(Math.abs(item.getPrice()) * 100d);
        long profit = Math.round(item.getProfit() * 100d);
        String side = normalizeSide(trim(item.getSide())).toLowerCase(Locale.ROOT);
        return code + "|" + side + "|" + open + "|" + close + "|" + qty + "|" + price + "|" + profit;
    }

    // 统一构建交易列表筛选请求，保持同步刷新和后台准备走同一套规则。
    private AccountDeferredSnapshotRenderHelper.TradeFilterRequest buildTradeFilterRequest(String product,
                                                                                           String side,
                                                                                           String normalizedSort) {
        return new AccountDeferredSnapshotRenderHelper.TradeFilterRequest(
                product,
                FILTER_PRODUCT.equals(product),
                side,
                FILTER_SIDE.equals(side),
                toHelperSortMode(normalizedSort),
                tradeSortDescending
        );
    }

    // 页面枚举映射到后台计算 helper，避免两套方向口径分叉。
    private AccountDeferredSnapshotRenderHelper.TradePnlSideMode toHelperTradePnlSideMode(TradePnlSideMode sideMode) {
        if (sideMode == TradePnlSideMode.BUY) {
            return AccountDeferredSnapshotRenderHelper.TradePnlSideMode.BUY;
        }
        if (sideMode == TradePnlSideMode.SELL) {
            return AccountDeferredSnapshotRenderHelper.TradePnlSideMode.SELL;
        }
        return AccountDeferredSnapshotRenderHelper.TradePnlSideMode.ALL;
    }

    // 页面枚举映射到后台计算 helper，避免星期统计时间基准分叉。
    private AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis toHelperTradeWeekdayBasis(TradeWeekdayBasis basis) {
        if (basis == TradeWeekdayBasis.OPEN_TIME) {
            return AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis.OPEN_TIME;
        }
        return AccountDeferredSnapshotRenderHelper.TradeWeekdayBasis.CLOSE_TIME;
    }

    // 页面排序标签映射到后台计算 helper，保持同步刷新和异步准备同口径。
    private AccountDeferredSnapshotRenderHelper.SortMode toHelperSortMode(String normalizedSort) {
        if (SORT_OPEN_TIME.equals(normalizedSort)) {
            return AccountDeferredSnapshotRenderHelper.SortMode.OPEN_TIME;
        }
        if (SORT_PROFIT.equals(normalizedSort)) {
            return AccountDeferredSnapshotRenderHelper.SortMode.PROFIT;
        }
        return AccountDeferredSnapshotRenderHelper.SortMode.CLOSE_TIME;
    }

    // 把已计算好的交易列表和摘要一次性绑定到页面。
    private void bindFilteredTrades(List<TradeRecordItem> filtered,
                                    AccountDeferredSnapshotRenderHelper.TradeSummary tradeSummary,
                                    boolean scrollToTop,
                                    String product,
                                    String side,
                                    String normalizedSort) {
        logTradeFilterVisibility(filtered, product, side, normalizedSort);
        tradeAdapter.submitList(filtered == null ? new ArrayList<>() : new ArrayList<>(filtered));
        binding.recyclerTrades.post(this::updateTradeScrollHandle);
        if (scrollToTop) {
            scrollTradesToTop();
        }
        updateTradePnlSummary(tradeSummary, product, side, FILTER_DATE);
        maybeScrollToAnalysisTarget();
    }

    // 从分析摘要卡进入深页后，自动把页面滚到用户点击的那块完整分析区域。
    private void maybeScrollToAnalysisTarget() {
        if (analysisTargetScrollCompleted || binding == null) {
            return;
        }
        View targetView = resolveAnalysisTargetView();
        if (targetView == null || targetView.getVisibility() != View.VISIBLE) {
            return;
        }
        analysisTargetScrollCompleted = true;
        binding.scrollAccountStats.post(() -> {
            if (binding == null) {
                return;
            }
            int top = Math.max(0, targetView.getTop() - dpToPx(12));
            binding.scrollAccountStats.smoothScrollTo(0, top);
        });
    }

    // 根据入口决定深页应该停在哪个完整分析模块。
    @Nullable
    private View resolveAnalysisTargetView() {
        if (AccountStatsBridgeActivity.ANALYSIS_TARGET_STRUCTURE.equals(pendingAnalysisTargetSection)) {
            return binding.cardTradeStatsSection;
        }
        if (AccountStatsBridgeActivity.ANALYSIS_TARGET_TRADE_HISTORY.equals(pendingAnalysisTargetSection)) {
            return binding.cardTradeRecordsSection;
        }
        return null;
    }

    private List<TradeRecordItem> filterTradeDistributionSymbols(List<TradeRecordItem> source) {
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

    // 记录快照交易、历史缓存和页面基表的诊断摘要，定位漏单发生在数据装载的哪一层。
    private void logTradeVisibilitySnapshot(List<TradeRecordItem> snapshotTrades,
                                            List<TradeRecordItem> effectiveTrades,
                                            List<TradeRecordItem> visibleBaseTrades) {
        if (logManager == null) {
            return;
        }
        String signature = "snapshot{" + TradeVisibilityDiagnosticsHelper.buildTradeSignature(snapshotTrades) + "}"
                + ", effective{" + TradeVisibilityDiagnosticsHelper.buildTradeSignature(effectiveTrades) + "}"
                + ", base{" + TradeVisibilityDiagnosticsHelper.buildTradeSignature(visibleBaseTrades) + "}";
        if (signature.equals(lastTradeVisibilitySnapshotSignature)) {
            return;
        }
        lastTradeVisibilitySnapshotSignature = signature;
    }

    // 记录交易筛选条件与筛选结果，判断漏单是否只是被产品/方向/排序状态隐藏。
    private void logTradeFilterVisibility(List<TradeRecordItem> filteredTrades,
                                          String product,
                                          String side,
                                          String normalizedSort) {
        if (logManager == null) {
            return;
        }
        String signature = TradeVisibilityDiagnosticsHelper.buildFilterSignature(
                product,
                side,
                normalizedSort,
                filteredTrades
        );
        if (signature.equals(lastTradeFilterVisibilitySignature)) {
            return;
        }
        lastTradeFilterVisibilitySignature = signature;
    }

    private void scrollTradesToTop() {
        RecyclerView.LayoutManager layoutManager = binding.recyclerTrades.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager)) {
            binding.recyclerTrades.scrollToPosition(0);
            return;
        }
        binding.recyclerTrades.post(() ->
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(0, 0));
    }
    private void configureTradeScrollHandle() {
        binding.recyclerTrades.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateTradeScrollHandle();
            }
        });
        binding.viewTradeScrollBar.setOnThumbDragListener(new TradeScrollBarView.OnThumbDragListener() {
            @Override
            public void onDragFractionChanged(float fraction) {
                int range = binding.recyclerTrades.computeVerticalScrollRange();
                int extent = binding.recyclerTrades.computeVerticalScrollExtent();
                int currentOffset = binding.recyclerTrades.computeVerticalScrollOffset();
                int scrollable = Math.max(0, range - extent);
                if (scrollable <= 0) {
                    return;
                }
                int targetOffset = Math.round(fraction * scrollable);
                binding.recyclerTrades.scrollBy(0, targetOffset - currentOffset);
            }

            @Override
            public void onDragStateChanged(boolean dragging) {
                draggingTradeScrollBar = dragging;
                if (!dragging) {
                    updateTradeScrollHandle();
                }
            }
        });
        binding.recyclerTrades.post(this::updateTradeScrollHandle);
    }

    private void updateTradeScrollHandle() {
        int range = binding.recyclerTrades.computeVerticalScrollRange();
        int extent = binding.recyclerTrades.computeVerticalScrollExtent();
        int offset = binding.recyclerTrades.computeVerticalScrollOffset();
        int scrollable = Math.max(0, range - extent);
        if (scrollable <= 0) {
            binding.viewTradeScrollBar.setVisibility(View.INVISIBLE);
            return;
        }
        binding.viewTradeScrollBar.setVisibility(View.VISIBLE);
        if (!draggingTradeScrollBar) {
            binding.viewTradeScrollBar.setScrollMetrics(offset, extent, range);
        }
    }


    private void updateTradePnlSummary(List<TradeRecordItem> trades,
                                       String productFilter,
                                       String sideFilter,
                                       String dateFilter) {
        updateTradePnlSummary(
                AccountDeferredSnapshotRenderHelper.buildTradeSummary(trades),
                productFilter,
                sideFilter,
                dateFilter
        );
    }

    // 使用已汇总好的交易摘要绑定盈亏区，避免主线程重复求和。
    private void updateTradePnlSummary(AccountDeferredSnapshotRenderHelper.TradeSummary tradeSummary,
                                       String productFilter,
                                       String sideFilter,
                                       String dateFilter) {
        boolean masked = isPrivacyMasked();
        int tradeCount = tradeSummary == null ? 0 : tradeSummary.getTradeCount();
        double total = tradeSummary == null ? 0d : tradeSummary.getTradeProfitTotal();
        double storageTotal = tradeSummary == null ? 0d : tradeSummary.getTradeStorageTotal();
        boolean mismatch = Math.abs(total - latestCumulativePnl) >= TRADE_PNL_ZERO_THRESHOLD;
        if (isDefaultTradeFilters(productFilter, sideFilter, dateFilter)
                && logManager != null
                && mismatch
                && !Boolean.TRUE.equals(lastTradePnlMismatchState)) {
            logManager.warn("Trade pnl summary mismatch: tradeSum="
                    + FormatUtils.formatPrice(total)
                    + ", cumulative=" + FormatUtils.formatPrice(latestCumulativePnl)
                    + ", rawTrades=" + baseTrades.size());
        }
        lastTradePnlMismatchState = mismatch;
        String pnlText = signedMoney(total);
        String storageText = signedMoney(storageTotal);
        double balanceTotal = total + storageTotal;
        String balanceText = signedMoney(balanceTotal);
        if (masked) {
            binding.tvTradeSummaryCountValue.setText(SensitiveDisplayMasker.MASK_TEXT);
            binding.tvTradeSummaryBalanceValue.setText(SensitiveDisplayMasker.MASK_TEXT);
            binding.tvTradeSummaryPnlValue.setText(SensitiveDisplayMasker.MASK_TEXT);
            binding.tvTradeSummaryStorageValue.setText(SensitiveDisplayMasker.MASK_TEXT);
            int defaultColor = ContextCompat.getColor(this, R.color.text_primary);
            binding.tvTradeSummaryCountValue.setTextColor(defaultColor);
            binding.tvTradeSummaryBalanceValue.setTextColor(defaultColor);
            binding.tvTradeSummaryPnlValue.setTextColor(defaultColor);
            binding.tvTradeSummaryStorageValue.setTextColor(defaultColor);
            return;
        }
        binding.tvTradeSummaryCountValue.setText(tradeCount + "次");
        binding.tvTradeSummaryBalanceValue.setText(storageText);
        binding.tvTradeSummaryPnlValue.setText(pnlText);
        binding.tvTradeSummaryStorageValue.setText(balanceText);
        binding.tvTradeSummaryCountValue.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        binding.tvTradeSummaryBalanceValue.setTextColor(resolveSignedValueColor(storageTotal));
        binding.tvTradeSummaryPnlValue.setTextColor(resolveSignedValueColor(total));
        binding.tvTradeSummaryStorageValue.setTextColor(resolveSignedValueColor(balanceTotal));
    }

    // 统一给盈亏数字着色，0 值保持主文字色。
    private int resolveSignedValueColor(double value) {
        return resolveSignedValueColor(value, R.color.text_primary);
    }

    // 允许调用方按场景传入中性色，避免列表明细的 0 值比正文更醒目。
    private int resolveSignedValueColor(double value, int neutralColorRes) {
        AccountValueStyleHelper.Direction direction = AccountValueStyleHelper.resolveNumericDirection(value);
        if (direction == AccountValueStyleHelper.Direction.POSITIVE) {
            return ContextCompat.getColor(this, R.color.accent_green);
        }
        if (direction == AccountValueStyleHelper.Direction.NEGATIVE) {
            return ContextCompat.getColor(this, R.color.accent_red);
        }
        return ContextCompat.getColor(this, neutralColorRes);
    }

    // 收益统计根据当前显示模式切换颜色口径，收益率或收益额为 0 时统一回到中性色。
    private int resolveReturnDisplayColor(double rate, double amount, int neutralColorRes) {
        double referenceValue = returnValueMode == ReturnValueMode.AMOUNT ? amount : rate;
        return resolveSignedValueColor(referenceValue, neutralColorRes);
    }

    private boolean isDefaultTradeFilters(String productFilter,
                                          String sideFilter,
                                          String dateFilter) {
        return isDefaultOrBlank(productFilter, FILTER_PRODUCT)
                && isDefaultOrBlank(sideFilter, FILTER_SIDE)
                && isDefaultOrBlank(dateFilter, FILTER_DATE);
    }

    private boolean isDefaultOrBlank(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        return defaultValue.equals(value);
    }

    private void updateTradeProductOptions() {
        String current = selectedTradeProductFilter;
        if (current == null || current.trim().isEmpty()) {
            current = FILTER_PRODUCT;
        }
        updateTradeProductOptions(AccountDeferredSnapshotRenderHelper.buildTradeProducts(baseTrades), current);
    }

    // 把已准备好的产品列表绑定到筛选框，避免首帧后再次遍历全部交易。
    private void updateTradeProductOptions(List<String> products, String current) {
        List<String> options = new ArrayList<>();
        options.add(FILTER_PRODUCT);
        if (products != null) {
            options.addAll(products);
        }
        ArrayAdapter<String> adapter = createTradeFilterAdapter(options.toArray(new String[0]));
        binding.spinnerTradeProduct.setAdapter(adapter);
        setSpinnerSelectionByValue(binding.spinnerTradeProduct, current);
        selectedTradeProductFilter = safeSpinnerValue(binding.spinnerTradeProduct, current, FILTER_PRODUCT);
        updateTradeFilterDisplayTexts();
    }

    private void applyManualCurveRange() {
        hideManualDatePickerPanel();
        String startText = trim(binding.etRangeStart.getText() == null ? "" : binding.etRangeStart.getText().toString());
        String endText = trim(binding.etRangeEnd.getText() == null ? "" : binding.etRangeEnd.getText().toString());
        if (startText.isEmpty() || endText.isEmpty()) {
            clearManualCurveRange(false);
            if (renderCoordinator != null) {
                renderCoordinator.refreshCurveProjection();
            }
            Toast.makeText(this, "已恢复当前时间维度默认区间", Toast.LENGTH_SHORT).show();
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

            List<CurvePoint> filtered = filterCurveByManualRange(allCurvePoints, start, end);
            if (filtered.size() < 2) {
                Toast.makeText(this, "该区间数据不足，请调整日期", Toast.LENGTH_SHORT).show();
                return;
            }
            manualCurveRangeEnabled = true;
            manualCurveRangeStartMs = start;
            manualCurveRangeEndMs = end;
            if (renderCoordinator != null) {
                renderCoordinator.refreshCurveProjection();
            }
        } catch (Exception e) {
            Toast.makeText(this, "日期格式应为 yyyy-MM-dd", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearManualCurveRange(boolean clearInputText) {
        manualCurveRangeEnabled = false;
        manualCurveRangeStartMs = 0L;
        manualCurveRangeEndMs = 0L;
        if (clearInputText) {
            binding.etRangeStart.setText("");
            binding.etRangeEnd.setText("");
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
        return FormatUtils.formatSignedMoney(value);
    }

    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(activity, palette);
        configureToggleButtonsV2();
        flattenCardSections(binding.scrollAccountStats, palette);
        binding.equityCurveView.refreshPalette();
        binding.positionRatioChartView.refreshPalette();
        binding.drawdownChartView.refreshPalette();
        binding.dailyReturnChartView.refreshPalette();
        binding.tradeDistributionScatterView.refreshPalette();
        binding.holdingDurationDistributionView.refreshPalette();
        updateLoginSuccessBannerStyle();
        setConnectionStatus(gatewayConnected);
        applyPrivacyMaskState();
    }

    // 登录真正连上网关后，在界面中央显示一条不会拦截点击的短提示。
    private void showLoginSuccessBanner() {
        if (binding == null || binding.tvLoginSuccessBanner == null) {
            return;
        }
        updateLoginSuccessBannerStyle();
        TextView banner = binding.tvLoginSuccessBanner;
        banner.removeCallbacks(hideLoginSuccessBannerRunnable);
        banner.animate().cancel();
        banner.setVisibility(View.VISIBLE);
        banner.setAlpha(0f);
        banner.setScaleX(0.92f);
        banner.setScaleY(0.92f);
        banner.setTranslationY(dpToPx(8));
        banner.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(180L)
                .start();
        banner.postDelayed(hideLoginSuccessBannerRunnable, 900L);
    }

    // 统一刷新登录成功提示的颜色，保证主题切换时样式同步。
    private void updateLoginSuccessBannerStyle() {
        if (binding == null || binding.tvLoginSuccessBanner == null) {
            return;
        }
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        binding.tvLoginSuccessBanner.setText(R.string.account_login_success_banner);
        binding.tvLoginSuccessBanner.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
        binding.tvLoginSuccessBanner.setTextColor(ContextCompat.getColor(this, R.color.white));
    }

    // 让成功提示快速淡出，避免遮挡中间操作区。
    private void hideLoginSuccessBannerNow() {
        if (binding == null || binding.tvLoginSuccessBanner == null) {
            return;
        }
        TextView banner = binding.tvLoginSuccessBanner;
        banner.removeCallbacks(hideLoginSuccessBannerRunnable);
        if (banner.getVisibility() != View.VISIBLE) {
            banner.setAlpha(0f);
            banner.setTranslationY(dpToPx(8));
            banner.setVisibility(View.GONE);
            return;
        }
        banner.animate().cancel();
        banner.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .translationY(-dpToPx(4))
                .setDuration(160L)
                .withEndAction(() -> {
                    if (binding == null || binding.tvLoginSuccessBanner == null) {
                        return;
                    }
                    binding.tvLoginSuccessBanner.setVisibility(View.GONE);
                    binding.tvLoginSuccessBanner.setAlpha(0f);
                    binding.tvLoginSuccessBanner.setScaleX(1f);
                    binding.tvLoginSuccessBanner.setScaleY(1f);
                    binding.tvLoginSuccessBanner.setTranslationY(dpToPx(8));
                })
                .start();
    }

    private void flattenCardSections(View view, UiPaletteManager.Palette palette) {
        if (view instanceof MaterialCardView) {
            MaterialCardView cardView = (MaterialCardView) view;
            cardView.setCardElevation(0f);
            cardView.setRadius(0f);
            cardView.setStrokeWidth(0);
            cardView.setCardBackgroundColor(palette.surfaceEnd);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                flattenCardSections(group.getChildAt(i), palette);
            }
        }
    }

    private String percent(double ratio) {
        return String.format(Locale.getDefault(), "%+.2f%%", ratio * 100d);
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

    // 会话切换或退出登录时，显式通知服务清掉流式账户运行态，避免旧仓位短暂回灌。
    private void requestMonitorServiceAccountRuntimeClear() {
        MonitorServiceController.dispatch(this, AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME);
    }
    private static class DayBucket {
        private final int day;
        private long startMs;
        private long endMs;
        private double closeEquity;

        private DayBucket(int day) {
            this.day = day;
        }
    }

    private static class StageRange {
        private final String label;
        private final long startMs;
        private final long endMs;

        private StageRange(String label, long startMs, long endMs) {
            this.label = label;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    private static class PeriodBucket {
        private long startMs;
        private long endMs;
        private double closeEquity;
    }

    private static class MonthReturnInfo {
        private long startMs;
        private long endMs;
        private double startEquity;
        private double endEquity;
        private double returnAmount;
        private double returnRate;
        private boolean hasData;
    }

    private static class YearlyReturnRow {
        private final int year;
        private final Map<Integer, MonthReturnInfo> monthly = new LinkedHashMap<>();
        private long startMs;
        private long endMs;
        private double yearReturnAmount;
        private double yearReturnRate;

        private YearlyReturnRow(int year) {
            this.year = year;
        }
    }

    private static class LegacyDrawdownSegment {
        private final long peakTimestamp;
        private final long valleyTimestamp;
        private final double peakBalance;
        private final double valleyBalance;
        private final double drawdownRate;

        private LegacyDrawdownSegment(long peakTimestamp,
                                      long valleyTimestamp,
                                      double peakBalance,
                                      double valleyBalance,
                                      double drawdownRate) {
            this.peakTimestamp = peakTimestamp;
            this.valleyTimestamp = valleyTimestamp;
            this.peakBalance = peakBalance;
            this.valleyBalance = valleyBalance;
            this.drawdownRate = drawdownRate;
        }
    }

}
