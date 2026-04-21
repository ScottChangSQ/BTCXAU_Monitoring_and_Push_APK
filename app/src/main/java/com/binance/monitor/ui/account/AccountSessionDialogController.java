/*
 * 账户会话弹窗控制器，负责承载登录弹窗 UI、远程会话提交和结果回传。
 * 该组件不依赖账户统计页，账户持仓页可直接持有它来完成登录、切换和快照校验主链。
 */
package com.binance.monitor.ui.account;

import android.content.res.ColorStateList;
import android.os.Build;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.R;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfile;
import com.binance.monitor.data.model.v2.session.RemoteAccountProfileDeduplicationHelper;
import com.binance.monitor.data.model.v2.session.SessionPublicKeyPayload;
import com.binance.monitor.data.model.v2.session.SessionReceipt;
import com.binance.monitor.data.model.v2.session.SessionStatusPayload;
import com.binance.monitor.data.remote.v2.GatewayV2SessionClient;
import com.binance.monitor.databinding.DialogAccountConnectionSheetBinding;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.main.ConnectionDetailNetworkHelper;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionCredentialEncryptor;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.ui.theme.SpacingTokenResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.SensitiveDisplayMasker;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountSessionDialogController {

    public interface Callback {
        // 会话请求已提交，页面应切空旧数据并展示同步中的明确状态。
        void onSessionSubmitting(@NonNull String statusMessage,
                                 @Nullable String account,
                                 @Nullable String server);

        // 会话已校验通过并拿到对应账户快照，页面应直接绑定新真值。
        void onSessionVerified(@NonNull AccountStatsPreloadManager.Cache verifiedCache,
                               @Nullable RemoteAccountProfile profile,
                               @NonNull String successMessage);

        // 会话请求失败，页面应恢复可重试状态。
        void onSessionFailed(@NonNull String message);

        // 当前账号已注销，页面应切到未登录空态。
        void onSessionLoggedOut(@NonNull String message);
    }

    private final AppCompatActivity activity;
    private final Callback callback;
    private final AccountStatsPreloadManager preloadManager;
    private final SecureSessionPrefs secureSessionPrefs;
    private final AccountStorageRepository accountStorageRepository;
    private final GatewayV2SessionClient sessionClient;
    private final SessionCredentialEncryptor sessionCredentialEncryptor;
    private final AccountSessionStateMachine sessionStateMachine;
    private final AccountRemoteSessionCoordinator remoteSessionCoordinator;
    private final ExecutorService sessionExecutor;
    private final LogManager logManager;
    private final Object acceptedSessionLock = new Object();

    @Nullable
    private AlertDialog activeLoginDialog;
    @Nullable
    private BottomSheetDialog activeAccountConnectionDialog;
    private boolean loginDialogSubmissionInFlight;
    private String loginAccountInput = "";
    private String loginServerInput = "";
    @NonNull
    private List<RemoteAccountProfile> savedSessionAccounts = new ArrayList<>();
    @Nullable
    private AcceptedSession pendingAcceptedSession;

    // 创建独立账户会话弹窗控制器。
    public AccountSessionDialogController(@NonNull AppCompatActivity activity,
                                          @NonNull Callback callback) {
        this.activity = activity;
        this.callback = callback;
        this.preloadManager = AccountStatsPreloadManager.getInstance(activity.getApplicationContext());
        this.secureSessionPrefs = new SecureSessionPrefs(activity.getApplicationContext());
        this.accountStorageRepository = new AccountStorageRepository(activity.getApplicationContext());
        this.sessionClient = new GatewayV2SessionClient(activity.getApplicationContext());
        this.sessionCredentialEncryptor = new SessionCredentialEncryptor();
        this.sessionStateMachine = new AccountSessionStateMachine();
        this.sessionExecutor = Executors.newSingleThreadExecutor();
        this.logManager = LogManager.getInstance(activity.getApplicationContext());
        this.remoteSessionCoordinator = buildRemoteSessionCoordinator();
        restoreDraftInputs();
    }

    // 页面销毁时释放弹窗和后台执行器。
    public void shutdown() {
        dismissActiveLoginDialog();
        dismissActiveAccountConnectionDialog();
        synchronized (acceptedSessionLock) {
            pendingAcceptedSession = null;
        }
        sessionExecutor.shutdownNow();
    }

    // 账户页监听到缓存变化时，尝试把“已受理、待同步”的会话正式收口为成功。
    public void onCacheUpdated(@Nullable AccountStatsPreloadManager.Cache cache) {
        AcceptedSession acceptedSession;
        synchronized (acceptedSessionLock) {
            acceptedSession = pendingAcceptedSession;
        }
        if (acceptedSession == null || !isVerifiedRemoteCache(cache, acceptedSession.result.getActiveAccount())) {
            return;
        }
        boolean sessionActivated = remoteSessionCoordinator.onSnapshotApplied(
                cache == null ? "" : cache.getAccount(),
                cache == null ? "" : cache.getServer()
        );
        if (!sessionActivated) {
            synchronized (acceptedSessionLock) {
                if (pendingAcceptedSession == acceptedSession) {
                    pendingAcceptedSession = null;
                }
            }
            notifySessionFailed("账户快照已返回，但会话状态未完成收口", false);
            return;
        }
        synchronized (acceptedSessionLock) {
            if (pendingAcceptedSession != acceptedSession) {
                return;
            }
            pendingAcceptedSession = null;
        }
        runOnUiThreadIfAlive(() -> {
            callback.onSessionVerified(cache, acceptedSession.result.getActiveAccount(), acceptedSession.successMessage);
            Toast.makeText(activity, acceptedSession.successMessage, Toast.LENGTH_SHORT).show();
        });
    }

    // 直接在当前页面上展示登录弹窗。
    public void showLoginDialog() {
        dismissActiveAccountConnectionDialog();
        restoreDraftInputs();
        logRemoteSessionDebug("准备展示独立登录弹窗");
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = SpacingTokenResolver.px(activity, R.dimen.dialog_content_padding);
        int top = SpacingTokenResolver.rowGapPx(activity);
        int bottom = SpacingTokenResolver.rowGapCompactPx(activity);
        container.setPadding(horizontal, top, horizontal, bottom);
        container.setBackground(UiPaletteManager.createSurfaceDrawable(activity, palette.card, palette.stroke));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            container.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            container.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        }

        EditText accountInput = createLoginField("账户名称", false);
        accountInput.setText(loginAccountInput);
        container.addView(accountInput);

        EditText passwordInput = createLoginField("账户密码", true);
        container.addView(passwordInput);

        EditText serverInput = createLoginField("服务器信息", false);
        serverInput.setText(loginServerInput);
        container.addView(serverInput);

        CheckBox rememberCheckBox = createRememberCheckBox(palette);
        container.addView(rememberCheckBox);

        LinearLayout savedAccountsContainer = appendSavedAccountsSection(container, palette);
        populateSavedAccountRows(savedAccountsContainer, palette, null);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
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
        });
        LinearLayout actionRow = createLoginActionRow(dialog, palette, accountInput, passwordInput, serverInput, rememberCheckBox);
        container.addView(actionRow);
        dialog.setOnShowListener(ignored -> {
            logRemoteSessionDebug("独立登录弹窗已展示");
            refreshSavedAccountsForDialog(savedAccountsContainer, palette, dialog);
        });
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
    }

    // 已连接时先展示账户详情，再由用户决定切换账号或注销。
    public void showAccountConnectionDialog(@Nullable AccountStatsPreloadManager.Cache cache,
                                            @Nullable String connectionStatusText) {
        dismissActiveLoginDialog();
        dismissActiveAccountConnectionDialog();
        restoreDraftInputs();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        boolean masked = SensitiveDisplayMasker.isEnabled(activity);
        SessionSummarySnapshot sessionSummary = secureSessionPrefs.loadSessionSummary();
        RemoteAccountProfile activeAccount = sessionSummary.getActiveAccount();
        String safeStatus = trim(connectionStatusText).isEmpty() ? "已连接账户" : trim(connectionStatusText);
        String connectedAccount = resolveConnectedAccount(cache, activeAccount);
        String connectedServer = resolveConnectedServer(cache, activeAccount);
        String connectedSource = trim(cache == null ? "" : cache.getSource());
        String connectedGateway = trim(cache == null ? "" : cache.getGateway());
        String connectedUpdate = formatConnectionUpdatedAt(cache);

        View sheetView = activity.getLayoutInflater().inflate(R.layout.dialog_account_connection_sheet, null, false);
        DialogAccountConnectionSheetBinding binding = DialogAccountConnectionSheetBinding.bind(sheetView);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(sheetView);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        styleConnectionActionButton(binding.btnAccountConnectionSwitch, palette);
        styleConnectionActionButton(binding.btnAccountConnectionLogout, palette);
        styleConnectionActionButton(binding.btnAccountConnectionClose, palette);
        bindConnectionDetail(binding.tvAccountConnectionStatusValue, safeStatus, masked);
        bindConnectionDetail(binding.tvAccountConnectionAccountValue, connectedAccount, masked);
        bindConnectionDetail(binding.tvAccountConnectionServerValue, connectedServer, masked);
        bindConnectionDetail(binding.tvAccountConnectionSourceValue, connectedSource, masked);
        bindConnectionDetail(binding.tvAccountConnectionGatewayValue, connectedGateway, masked);
        bindConnectionDetail(binding.tvAccountConnectionUpdatedValue, connectedUpdate, masked);
        bindConnectionDetail(binding.tvAccountConnectionLatencyValue, "检测中...", masked);
        binding.btnAccountConnectionSwitch.setVisibility(savedSessionAccounts.isEmpty() ? View.GONE : View.VISIBLE);
        binding.btnAccountConnectionSwitch.setOnClickListener(v -> {
            dialog.dismiss();
            showLoginDialog();
        });
        binding.btnAccountConnectionLogout.setOnClickListener(v -> {
            dialog.dismiss();
            logoutCurrentAccount();
        });
        binding.btnAccountConnectionClose.setOnClickListener(v -> dialog.dismiss());
        activeAccountConnectionDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }
        dialog.setOnDismissListener(ignored -> {
            if (activeAccountConnectionDialog == dialog) {
                activeAccountConnectionDialog = null;
            }
        });
        dialog.show();
        UiPaletteManager.applyBottomSheetSurface(dialog, palette);
        binding.getRoot().post(() -> applyConnectionActionRowLayout(binding));
        loadConnectionDiagnosticsAsync(connectedGateway, dialog, binding.tvAccountConnectionLatencyValue, masked);
    }

    // 使用自管按钮避免系统正按钮链吞掉提交。
    private LinearLayout createLoginActionRow(@NonNull AlertDialog dialog,
                                              @NonNull UiPaletteManager.Palette palette,
                                              @NonNull EditText accountInput,
                                              @NonNull EditText passwordInput,
                                              @NonNull EditText serverInput,
                                              @NonNull CheckBox rememberCheckBox) {
        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = SpacingTokenResolver.rowGapPx(activity);
        actionRow.setLayoutParams(rowParams);

        MaterialButton cancelButton = new MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancelButton.setText("取消");
        cancelButton.setTextColor(palette.textSecondary);
        cancelButton.setStrokeColor(ColorStateList.valueOf(palette.stroke));
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        actionRow.addView(cancelButton);

        MaterialButton continueButton = new MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonStyle);
        continueButton.setText("继续");
        continueButton.setTextColor(android.graphics.Color.WHITE);
        continueButton.setBackgroundTintList(ColorStateList.valueOf(palette.primary));
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        continueParams.leftMargin = SpacingTokenResolver.inlineGapPx(activity);
        continueButton.setLayoutParams(continueParams);
        continueButton.setOnClickListener(v -> {
            String account = trim(accountInput.getText() == null ? "" : accountInput.getText().toString());
            char[] password = readPasswordChars(passwordInput);
            String server = trim(serverInput.getText() == null ? "" : serverInput.getText().toString());
            logRemoteSessionDebug("点击独立登录继续: accountEmpty=" + account.isEmpty()
                    + ", passwordEmpty=" + (password.length == 0)
                    + ", serverEmpty=" + server.isEmpty()
                    + ", remember=" + rememberCheckBox.isChecked());
            if (account.isEmpty() || password.length == 0 || server.isEmpty()) {
                clearPasswordChars(password);
                Toast.makeText(activity, "请完整填写账户、密码和服务器信息", Toast.LENGTH_SHORT).show();
                return;
            }
            loginDialogSubmissionInFlight = true;
            passwordInput.setText("");
            dialog.dismiss();
            submitRemoteLogin(account, password, server, rememberCheckBox.isChecked());
        });
        actionRow.addView(continueButton);
        return actionRow;
    }

    // 创建登录输入框，并关闭系统自动填充。
    private EditText createLoginField(@NonNull String hint, boolean passwordMode) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setSingleLine(true);
        UiPaletteManager.styleInputField(input, palette, R.style.TextAppearance_BinanceMonitor_Body);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = SpacingTokenResolver.rowGapPx(activity);
        input.setLayoutParams(params);
        input.setInputType(passwordMode
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            input.setAutofillHints((String[]) null);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            input.setAccessibilityDataSensitive(View.ACCESSIBILITY_DATA_SENSITIVE_YES);
        }
        return input;
    }

    // 创建“记住账号”选项。
    private CheckBox createRememberCheckBox(@NonNull UiPaletteManager.Palette palette) {
        CheckBox checkBox = new CheckBox(activity);
        checkBox.setText("记住此账号（密码仅加密保存在服务器）");
        checkBox.setTextColor(palette.textPrimary);
        checkBox.setButtonTintList(ColorStateList.valueOf(palette.primary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = SpacingTokenResolver.rowGapPx(activity);
        checkBox.setLayoutParams(params);
        return checkBox;
    }

    // 追加已保存账号区块。
    private LinearLayout appendSavedAccountsSection(@NonNull LinearLayout container,
                                                    @NonNull UiPaletteManager.Palette palette) {
        TextView title = new TextView(activity);
        title.setText("已保存账号");
        title.setTextColor(palette.textSecondary);
        UiPaletteManager.applyTextAppearance(title, R.style.TextAppearance_BinanceMonitor_Meta);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = SpacingTokenResolver.rowGapCompactPx(activity);
        titleParams.bottomMargin = SpacingTokenResolver.rowGapCompactPx(activity);
        container.addView(title, titleParams);

        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        container.addView(section);
        return section;
    }

    // 渲染已保存账号列表，允许直接切换。
    private void populateSavedAccountRows(@NonNull LinearLayout container,
                                          @NonNull UiPaletteManager.Palette palette,
                                          @Nullable AlertDialog dialog) {
        container.removeAllViews();
        if (savedSessionAccounts.isEmpty()) {
            TextView emptyView = new TextView(activity);
            emptyView.setText("暂无已保存账号");
            emptyView.setTextColor(palette.textSecondary);
            UiPaletteManager.applyTextAppearance(emptyView, R.style.TextAppearance_BinanceMonitor_Meta);
            container.addView(emptyView);
            return;
        }
        for (RemoteAccountProfile profile : savedSessionAccounts) {
            if (profile == null) {
                continue;
            }
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.card, palette.stroke));
            row.setPadding(
                    SpacingTokenResolver.px(activity, R.dimen.list_item_padding_x),
                    SpacingTokenResolver.px(activity, R.dimen.list_item_padding_y),
                    SpacingTokenResolver.px(activity, R.dimen.list_item_padding_x),
                    SpacingTokenResolver.px(activity, R.dimen.list_item_padding_y)
            );
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = SpacingTokenResolver.rowGapPx(activity);
            row.setLayoutParams(rowParams);

            TextView label = new TextView(activity);
            label.setText(buildSessionProfileLabel(profile));
            label.setTextColor(palette.textPrimary);
            UiPaletteManager.applyTextAppearance(label, R.style.TextAppearance_BinanceMonitor_Body);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label, labelParams);

            MaterialButton actionButton = new MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            actionButton.setText(profile.isActive() ? "当前账号" : "切换");
            actionButton.setEnabled(!profile.isActive());
            actionButton.setTextColor(profile.isActive()
                    ? UiPaletteManager.controlUnselectedText(activity)
                    : UiPaletteManager.controlSelectedText(activity));
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

    // 用服务端真值覆盖已保存账号列表。
    private void refreshSavedAccountsForDialog(@NonNull LinearLayout container,
                                               @NonNull UiPaletteManager.Palette palette,
                                               @Nullable AlertDialog dialog) {
        if (sessionExecutor.isShutdown()) {
            return;
        }
        sessionExecutor.execute(() -> {
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

    // 提交新账号登录。
    private void submitRemoteLogin(@NonNull String account,
                                   @NonNull char[] password,
                                   @NonNull String server,
                                   boolean remember) {
        if (sessionExecutor.isShutdown()) {
            clearPasswordChars(password);
            notifySessionFailed("远程会话未初始化", false);
            return;
        }
        loginAccountInput = account;
        loginServerInput = server;
        secureSessionPrefs.saveDraftIdentity(account, server);
        callback.onSessionSubmitting("正在同步账户", account, server);
        sessionExecutor.execute(() -> {
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
                runOnUiThreadIfAlive(() -> applyRemoteSessionAccepted(result, "登录已受理，正在同步账户", "登录成功"));
            } catch (Exception exception) {
                notifySessionFailed(buildFailureMessageWithDiagnostic(exception), true);
            } finally {
                clearPasswordChars(password);
            }
        });
    }

    // 提交已保存账号切换。
    private void submitSavedAccountSwitch(@NonNull RemoteAccountProfile profile) {
        if (sessionExecutor.isShutdown()) {
            notifySessionFailed("远程会话未初始化", false);
            return;
        }
        callback.onSessionSubmitting("正在同步账户", profile.getLogin(), profile.getServer());
        sessionExecutor.execute(() -> {
            try {
                AccountRemoteSessionCoordinator.SessionActionResult result =
                        remoteSessionCoordinator.switchSavedAccount(profile.getProfileId());
                runOnUiThreadIfAlive(() -> applyRemoteSessionAccepted(result, "切换已受理，正在同步账户", "登录成功"));
            } catch (Exception exception) {
                notifySessionFailed(buildFailureMessageWithDiagnostic(exception), true);
            }
        });
    }

    // 会话已受理后，先把页面切到同步中，再等待缓存监听在目标账号快照真正到达时收口成功。
    private void applyRemoteSessionAccepted(@Nullable AccountRemoteSessionCoordinator.SessionActionResult result,
                                            @NonNull String statusMessage,
                                            @NonNull String successMessage) {
        loginDialogSubmissionInFlight = false;
        if (result == null || result.getReceipt() == null || result.getReceipt().isFailed()) {
            notifySessionFailed(result == null || result.getReceipt() == null
                    ? "远程会话失败"
                    : result.getReceipt().getMessage(), true);
            return;
        }
        ConfigManager.getInstance(activity.getApplicationContext()).setAccountSessionActive(true);
        RemoteAccountProfile activeAccount = result.getActiveAccount();
        synchronized (acceptedSessionLock) {
            pendingAcceptedSession = new AcceptedSession(result, successMessage);
        }
        callback.onSessionSubmitting(
                statusMessage,
                activeAccount == null ? null : activeAccount.getLogin(),
                activeAccount == null ? null : activeAccount.getServer()
        );
        requestForegroundEntrySnapshot();
    }

    // 统一通知失败结果，并按需重新打开弹窗。
    private void notifySessionFailed(@Nullable String message, boolean reopenDialog) {
        String safeMessage = trim(message).isEmpty() ? "远程会话失败" : trim(message);
        runOnUiThreadIfAlive(() -> {
            loginDialogSubmissionInFlight = false;
            synchronized (acceptedSessionLock) {
                pendingAcceptedSession = null;
            }
            callback.onSessionFailed(safeMessage);
            showSessionFailureDialog(safeMessage, reopenDialog);
        });
    }

    // 在后台线程拼出“本地异常 + 服务端诊断时间线”的完整失败信息。
    @NonNull
    private String buildFailureMessageWithDiagnostic(@Nullable Exception exception) {
        String baseMessage = trim(exception == null ? "" : exception.getMessage());
        if (baseMessage.isEmpty()) {
            baseMessage = "远程会话失败";
        }
        String requestId = "";
        SessionReceipt structuredReceipt = null;
        if (exception instanceof AccountRemoteSessionCoordinator.SessionActionException) {
            requestId = ((AccountRemoteSessionCoordinator.SessionActionException) exception).getRequestId();
            structuredReceipt = ((AccountRemoteSessionCoordinator.SessionActionException) exception).getReceipt();
        }
        String structuredSummary = buildStructuredFailureSummary(structuredReceipt);
        if (!structuredSummary.isEmpty()) {
            baseMessage = structuredSummary;
        }
        try {
            String diagnosticTimeline = trim(sessionClient.fetchSessionDiagnosticTimeline(requestId));
            if (diagnosticTimeline.isEmpty()) {
                return baseMessage;
            }
            if (baseMessage.contains(diagnosticTimeline)) {
                return baseMessage;
            }
            return baseMessage + "\n\n服务器诊断：\n" + diagnosticTimeline;
        } catch (Exception ignored) {
            return baseMessage;
        }
    }

    // 优先用结构化 receipt 生成可复制摘要，避免只剩一段模糊错误文本。
    private String buildStructuredFailureSummary(@Nullable SessionReceipt receipt) {
        if (receipt == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendFailureLine(builder, trim(receipt.getMessage()));
        appendFailureLine(builder, trim(receipt.getStage()).isEmpty() ? "" : "stage=" + trim(receipt.getStage()));
        appendFailureLine(builder, trim(receipt.getLoginError()).isEmpty() ? "" : "loginError=" + trim(receipt.getLoginError()));
        RemoteAccountProfile lastObserved = receipt.getLastObservedAccount();
        if (lastObserved != null && !trim(lastObserved.getLogin()).isEmpty()) {
            appendFailureLine(
                    builder,
                    "lastObserved=" + trim(lastObserved.getLogin()) + " / " + trim(lastObserved.getServer())
            );
        }
        return builder.toString().trim();
    }

    // 统一追加失败摘要行，避免重复处理换行。
    private void appendFailureLine(@NonNull StringBuilder builder, @Nullable String line) {
        String safeLine = trim(line);
        if (safeLine.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(safeLine);
    }

    // 登录失败时用可停留的弹窗展示完整原因，避免再只剩短暂 toast。
    private void showSessionFailureDialog(@NonNull String message, boolean reopenDialog) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        TextView messageView = new TextView(activity);
        messageView.setText(message);
        messageView.setTextColor(UiPaletteManager.resolve(activity).textPrimary);
        messageView.setTextIsSelectable(true);
        messageView.setLongClickable(true);
        messageView.setMovementMethod(new ScrollingMovementMethod());
        messageView.setVerticalScrollBarEnabled(true);
        messageView.setTextAppearance(activity, R.style.TextAppearance_BinanceMonitor_Body);
        int horizontalPadding = SpacingTokenResolver.px(activity, R.dimen.dialog_content_padding);
        int verticalPadding = SpacingTokenResolver.rowGapPx(activity);
        messageView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("账户登录失败")
                .setView(messageView)
                .setPositiveButton("重新输入", (dialog, which) -> {
                    if (reopenDialog && !activity.isFinishing() && !activity.isDestroyed()) {
                        showLoginDialog();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    // 会话已受理后主动触发一次前台全量快照，帮助账户页更快拿到新账号真值。
    private void requestForegroundEntrySnapshot() {
        if (sessionExecutor.isShutdown()) {
            return;
        }
        sessionExecutor.execute(() -> preloadManager.fetchSnapshotForUi());
    }

    // 提交远程 logout，请页面在成功后切到未登录空态。
    private void logoutCurrentAccount() {
        if (sessionExecutor.isShutdown()) {
            runOnUiThreadIfAlive(() -> Toast.makeText(activity, "退出登录服务未就绪", Toast.LENGTH_SHORT).show());
            return;
        }
        sessionExecutor.execute(() -> {
            try {
                remoteSessionCoordinator.logoutCurrent();
                runOnUiThreadIfAlive(() -> {
                    callback.onSessionLoggedOut("已注销当前账户");
                    Toast.makeText(activity, "已注销当前账户", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception exception) {
                runOnUiThreadIfAlive(() -> Toast.makeText(
                        activity,
                        trim(exception.getMessage()).isEmpty() ? "退出登录失败" : exception.getMessage(),
                        Toast.LENGTH_SHORT
                ).show());
            }
        });
    }

    // 组装远程会话协调器，把网络、加密和缓存清理收口成统一入口。
    @NonNull
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
                        preloadManager.clearLatestCache();
                        preloadManager.setFullSnapshotActive(true);
                    }

                    @Override
                    public void clearTradeHistory() {
                        String[] identity = resolvePersistedStorageIdentity();
                        if (identity == null) {
                            accountStorageRepository.clearTradeHistory();
                            return;
                        }
                        accountStorageRepository.clearTradeHistory(identity[0], identity[1]);
                    }

                    @Override
                    public void clearChartTradeDrafts() {
                        // 账户持仓页不持有独立图表草稿，这里无需额外处理。
                    }

                    @Override
                    public void clearPendingExpandedState() {
                        // 持仓页会在提交回调里直接清空整页模型，这里无需额外展开态处理。
                    }

                    @Override
                    public void clearPositionExpandedState() {
                        // 持仓页会在提交回调里直接清空整页模型，这里无需额外展开态处理。
                    }

                    @Override
                    public void clearTradeExpandedState() {
                        // 账户持仓页不展示历史交易列表。
                    }
                },
                secureSessionPrefs,
                () -> UUID.randomUUID().toString()
        );
    }

    // 恢复最近输入的账号、服务器和已保存账号列表。
    private void restoreDraftInputs() {
        SessionSummarySnapshot summary = secureSessionPrefs.loadSessionSummary();
        loginAccountInput = secureSessionPrefs.getDraftAccount("");
        loginServerInput = secureSessionPrefs.getDraftServer("");
        savedSessionAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(
                summary.getSavedAccountsSnapshot()
        );
    }

    // 基于当前会话摘要推断旧账号持久化分区。
    @Nullable
    private String[] resolvePersistedStorageIdentity() {
        SessionSummarySnapshot summary = secureSessionPrefs.loadSessionSummary();
        RemoteAccountProfile activeAccount = summary.getActiveAccount();
        if (activeAccount == null) {
            return null;
        }
        String account = trim(activeAccount.getLogin());
        String server = trim(activeAccount.getServer());
        if (account.isEmpty() || server.isEmpty()) {
            return null;
        }
        return new String[]{account, server};
    }

    // 更新本地会话摘要缓存。
    private void updateSessionProfiles(@Nullable RemoteAccountProfile activeAccount,
                                       @Nullable List<RemoteAccountProfile> savedAccounts,
                                       boolean active) {
        savedSessionAccounts = RemoteAccountProfileDeduplicationHelper.deduplicate(savedAccounts);
        secureSessionPrefs.saveSession(activeAccount, savedSessionAccounts, active);
    }

    // 生成已保存账号展示文案。
    @NonNull
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

    // 只认 profileId/login/server 三项完整的账号摘要。
    private boolean isCompleteRemoteSessionProfile(@Nullable RemoteAccountProfile profile) {
        return profile != null
                && !trim(profile.getProfileId()).isEmpty()
                && !trim(profile.getLogin()).isEmpty()
                && !trim(profile.getServer()).isEmpty();
    }

    // 读取密码输入框字符数组。
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

    // 主动清空密码字符数组。
    private void clearPasswordChars(@Nullable char[] password) {
        if (password == null) {
            return;
        }
        Arrays.fill(password, '\0');
    }

    // 安全关闭当前弹窗。
    private void dismissActiveLoginDialog() {
        if (activeLoginDialog == null) {
            return;
        }
        AlertDialog dialog = activeLoginDialog;
        activeLoginDialog = null;
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    // 安全关闭当前账户详情弹窗。
    private void dismissActiveAccountConnectionDialog() {
        if (activeAccountConnectionDialog == null) {
            return;
        }
        BottomSheetDialog dialog = activeAccountConnectionDialog;
        activeAccountConnectionDialog = null;
        if (dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    // 把连接详情值统一写到抽屉右侧，保持与全局状态抽屉同一类左右对齐信息行。
    private void bindConnectionDetail(@NonNull TextView valueView,
                                      @Nullable String value,
                                      boolean masked) {
        String maskedValue = AccountStatsPrivacyFormatter.maskValue(trim(value), masked);
        valueView.setText(trim(maskedValue).isEmpty() ? "--" : maskedValue);
    }

    // 统一渲染账户详情抽屉里的轻操作按钮，保持与全局状态抽屉同一主体样式。
    private void styleConnectionActionButton(@NonNull TextView button,
                                             @NonNull UiPaletteManager.Palette palette) {
        UiPaletteManager.styleActionButton(
                button,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control,
                4,
                R.dimen.control_height_sm
        );
    }

    // 账户详情抽屉的可见按钮继续按等宽铺满整行，避免隐藏“切换账户”后左侧留假间距。
    private void applyConnectionActionRowLayout(@NonNull DialogAccountConnectionSheetBinding binding) {
        int controlGapPx = SpacingTokenResolver.inlineGapPx(activity);
        List<View> visibleButtons = new ArrayList<>();
        if (binding.btnAccountConnectionSwitch.getVisibility() == View.VISIBLE) {
            visibleButtons.add(binding.btnAccountConnectionSwitch);
        }
        visibleButtons.add(binding.btnAccountConnectionLogout);
        visibleButtons.add(binding.btnAccountConnectionClose);
        for (int i = 0; i < visibleButtons.size(); i++) {
            applyWeightedActionButton(visibleButtons.get(i), i == 0 ? 0 : controlGapPx);
        }
    }

    // 统一动作按钮的运行时布局参数，确保抽屉底部按钮始终等宽对齐。
    private void applyWeightedActionButton(@NonNull View button, int marginStartPx) {
        ViewGroup.LayoutParams rawParams = button.getLayoutParams();
        LinearLayout.LayoutParams params = rawParams instanceof LinearLayout.LayoutParams
                ? (LinearLayout.LayoutParams) rawParams
                : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.width = 0;
        params.weight = 1f;
        params.setMarginStart(marginStartPx);
        params.setMarginEnd(0);
        button.setLayoutParams(params);
    }

    // 异步回填服务器延迟，避免详情抽屉首屏被网络探测阻塞。
    private void loadConnectionDiagnosticsAsync(@Nullable String gatewayRoot,
                                                @NonNull BottomSheetDialog dialog,
                                                @NonNull TextView latencyView,
                                                boolean masked) {
        Thread worker = new Thread(() -> {
            ConnectionDetailNetworkHelper.ServerDiagnostics diagnostics = ConnectionDetailNetworkHelper.load(gatewayRoot);
            runOnUiThreadIfAlive(() -> {
                if (activeAccountConnectionDialog != dialog || !dialog.isShowing()) {
                    return;
                }
                bindConnectionDetail(latencyView, diagnostics.latencyText, masked);
            });
        }, "account-session-diag");
        worker.setDaemon(true);
        worker.start();
    }

    @NonNull
    private String resolveConnectedAccount(@Nullable AccountStatsPreloadManager.Cache cache,
                                           @Nullable RemoteAccountProfile activeAccount) {
        String cacheAccount = trim(cache == null ? "" : cache.getAccount());
        if (!cacheAccount.isEmpty()) {
            return cacheAccount;
        }
        return activeAccount == null ? "" : trim(activeAccount.getLogin());
    }

    @NonNull
    private String resolveConnectedServer(@Nullable AccountStatsPreloadManager.Cache cache,
                                          @Nullable RemoteAccountProfile activeAccount) {
        String cacheServer = trim(cache == null ? "" : cache.getServer());
        if (!cacheServer.isEmpty()) {
            return cacheServer;
        }
        return activeAccount == null ? "" : trim(activeAccount.getServer());
    }

    @NonNull
    private String formatConnectionUpdatedAt(@Nullable AccountStatsPreloadManager.Cache cache) {
        long updatedAt = cache == null ? 0L : Math.max(cache.getUpdatedAt(), cache.getFetchedAt());
        if (updatedAt <= 0L) {
            return "--";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        return format.format(new Date(updatedAt));
    }

    // 统一回到主线程并确认页面仍活着。
    private void runOnUiThreadIfAlive(@NonNull Runnable action) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            action.run();
        });
    }

    // 记录会话调试日志，便于真机排查。
    private void logRemoteSessionDebug(@NonNull String message) {
        // 远程会话链路已稳定，默认不再写入调试日志。
    }

    // 只在缓存真正切到目标账号时才允许把独立弹窗收口为成功。
    private boolean isVerifiedRemoteCache(@Nullable AccountStatsPreloadManager.Cache cache,
                                          @Nullable RemoteAccountProfile profile) {
        if (cache == null || !cache.isConnected() || cache.getSnapshot() == null) {
            return false;
        }
        if (!isCompleteRemoteSessionProfile(profile)) {
            return false;
        }
        return trim(cache.getAccount()).equalsIgnoreCase(trim(profile.getLogin()))
                && trim(cache.getServer()).equalsIgnoreCase(trim(profile.getServer()));
    }

    @NonNull
    private String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static final class AcceptedSession {
        private final AccountRemoteSessionCoordinator.SessionActionResult result;
        private final String successMessage;

        private AcceptedSession(@NonNull AccountRemoteSessionCoordinator.SessionActionResult result,
                                @NonNull String successMessage) {
            this.result = result;
            this.successMessage = successMessage;
        }
    }

}
