/*
 * 账户会话弹窗控制器，负责承载登录弹窗 UI、远程会话提交和结果回传。
 * 该组件不依赖账户统计页，账户持仓页可直接持有它来完成登录、切换和快照校验主链。
 */
package com.binance.monitor.ui.account;

import android.content.res.ColorStateList;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
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
import com.binance.monitor.domain.account.AccountTimeRange;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.security.SecureSessionPrefs;
import com.binance.monitor.security.SessionCredentialEncryptor;
import com.binance.monitor.security.SessionSummarySnapshot;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    @Nullable
    private AlertDialog activeLoginDialog;
    private boolean loginDialogSubmissionInFlight;
    private String loginAccountInput = "";
    private String loginServerInput = "";
    @NonNull
    private List<RemoteAccountProfile> savedSessionAccounts = new ArrayList<>();

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
        sessionExecutor.shutdownNow();
    }

    // 直接在当前页面上展示登录弹窗。
    public void showLoginDialog() {
        restoreDraftInputs();
        logRemoteSessionDebug("准备展示独立登录弹窗");
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dpToPx(16);
        int top = dpToPx(8);
        int bottom = dpToPx(4);
        container.setPadding(horizontal, top, horizontal, bottom);
        container.setBackground(UiPaletteManager.createFilledDrawable(activity, palette.surfaceEnd));
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
            dialog.getWindow().setBackgroundDrawable(UiPaletteManager.createFilledDrawable(activity, palette.surfaceEnd));
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
        rowParams.topMargin = dpToPx(10);
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
        continueParams.leftMargin = dpToPx(10);
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
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        input.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        input.setTextColor(palette.textPrimary);
        input.setHintTextColor(palette.textSecondary);
        input.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.card, palette.stroke));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dpToPx(8);
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
        params.bottomMargin = dpToPx(8);
        checkBox.setLayoutParams(params);
        return checkBox;
    }

    // 追加已保存账号区块。
    private LinearLayout appendSavedAccountsSection(@NonNull LinearLayout container,
                                                    @NonNull UiPaletteManager.Palette palette) {
        TextView title = new TextView(activity);
        title.setText("已保存账号");
        title.setTextColor(palette.textSecondary);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dpToPx(6);
        titleParams.bottomMargin = dpToPx(6);
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
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
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
            row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dpToPx(8);
            row.setLayoutParams(rowParams);

            TextView label = new TextView(activity);
            label.setText(buildSessionProfileLabel(profile));
            label.setTextColor(palette.textPrimary);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label, labelParams);

            MaterialButton actionButton = new MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            actionButton.setText(profile.isActive() ? "当前账号" : "切换");
            actionButton.setEnabled(!profile.isActive());
            actionButton.setTextColor(profile.isActive() ? palette.textSecondary : palette.primary);
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
                verifyRemoteSessionAndApply(result, "登录成功");
            } catch (Exception exception) {
                notifySessionFailed(exception.getMessage(), true);
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
                verifyRemoteSessionAndApply(result, "登录成功");
            } catch (Exception exception) {
                notifySessionFailed(exception.getMessage(), true);
            }
        });
    }

    // 服务器确认会话后，立刻拉取对应账户快照并回传给页面。
    private void verifyRemoteSessionAndApply(@Nullable AccountRemoteSessionCoordinator.SessionActionResult result,
                                             @NonNull String successMessage) {
        if (result == null || result.getReceipt() == null || result.getReceipt().isFailed()) {
            notifySessionFailed(result == null || result.getReceipt() == null
                    ? "远程会话失败"
                    : result.getReceipt().getMessage(), true);
            return;
        }
        ConfigManager.getInstance(activity.getApplicationContext()).setAccountSessionActive(true);
        AccountStatsPreloadManager.Cache verifiedCache = preloadManager.fetchForUi(AccountTimeRange.ALL);
        ensureVerifiedRemoteCache(verifiedCache, result.getActiveAccount());
        boolean sessionActivated = remoteSessionCoordinator.onSnapshotApplied(
                verifiedCache.getAccount(),
                verifiedCache.getServer()
        );
        if (!sessionActivated) {
            throw new IllegalStateException("账户快照已返回，但会话状态未完成收口");
        }
        runOnUiThreadIfAlive(() -> {
            loginDialogSubmissionInFlight = false;
            callback.onSessionVerified(verifiedCache, result.getActiveAccount(), successMessage);
            Toast.makeText(activity, successMessage, Toast.LENGTH_SHORT).show();
        });
    }

    // 只有快照账号和服务器与当前登录账号完全一致时才视为登录成功。
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

    // 统一通知失败结果，并按需重新打开弹窗。
    private void notifySessionFailed(@Nullable String message, boolean reopenDialog) {
        String safeMessage = trim(message).isEmpty() ? "远程会话失败" : trim(message);
        runOnUiThreadIfAlive(() -> {
            loginDialogSubmissionInFlight = false;
            callback.onSessionFailed(safeMessage);
            Toast.makeText(activity, safeMessage, Toast.LENGTH_SHORT).show();
            if (reopenDialog && !activity.isFinishing() && !activity.isDestroyed()) {
                showLoginDialog();
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
        logManager.info("RemoteSessionDebug: " + message);
    }

    @NonNull
    private String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private int dpToPx(int dp) {
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
