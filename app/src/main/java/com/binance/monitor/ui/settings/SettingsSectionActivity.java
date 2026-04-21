/*
 * 设置二级页，负责展示某一个设置分组的具体内容。
 * 复用原有设置逻辑，但按分类只显示当前需要操作的模块。
 */
package com.binance.monitor.ui.settings;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.data.local.db.repository.ChartHistoryRepository;
import com.binance.monitor.data.model.v2.trade.TradeTemplate;
import com.binance.monitor.databinding.ActivitySettingsDetailBinding;
import com.binance.monitor.service.MonitorServiceController;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.ui.chart.MarketChartActivity;
import com.binance.monitor.ui.log.LogActivity;
import com.binance.monitor.ui.main.MainViewModel;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.ui.trade.TradeAuditActivity;
import com.binance.monitor.ui.trade.TradeTemplateRepository;
import com.binance.monitor.util.PermissionHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SettingsSectionActivity extends AppCompatActivity {
    private static final String TEMPLATE_SCOPE_BOTH = "both";
    private static final String TEMPLATE_SCOPE_MARKET = "market";
    private static final String TEMPLATE_SCOPE_PENDING = "pending";
    private static final String SYSTEM_TEMPLATE_DEFAULT = "default_market";
    private static final String SYSTEM_TEMPLATE_SCALP = "scalp_fast";
    private static final String SYSTEM_TEMPLATE_SWING = "swing_basic";

    public static final String EXTRA_SECTION = "extra_section";
    public static final String EXTRA_TITLE = "extra_title";

    private ActivitySettingsDetailBinding binding;
    private MainViewModel viewModel;
    private boolean applying;
    private String sectionKey = SettingsActivity.SECTION_DISPLAY;
    private String sectionTitle = "设置";
    private java.util.concurrent.ExecutorService cacheExecutor;
    private ConfigManager configManager;
    private TradeTemplateRepository tradeTemplateRepository;
    private String selectedDefaultTemplateId = "";
    private String selectedQuickTemplateId = "";
    private final List<TradeTemplate> draftTradeTemplates = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sectionKey = readExtra(EXTRA_SECTION, SettingsActivity.SECTION_DISPLAY);
        sectionTitle = readExtra(EXTRA_TITLE, "设置");
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        configManager = ConfigManager.getInstance(getApplicationContext());
        tradeTemplateRepository = new TradeTemplateRepository(this);
        cacheExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        binding.btnBack.setOnClickListener(v -> finish());
        setupActions();
        lockGatewayEntrySection();
        applyVisibleSection();
    }

    @Override
    protected void onDestroy() {
        if (cacheExecutor != null) {
            cacheExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPaletteStyles();
        applySettings();
        applyVisibleSection();
    }

    // 读取页面参数，缺失时使用默认值。
    private String readExtra(String key, String fallback) {
        String value = getIntent().getStringExtra(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    // 绑定各个设置控件行为。
    private void setupActions() {
        binding.btnClearCache.setOnClickListener(v -> confirmAndClearCache());
        binding.btnOpenTradeAudit.setOnClickListener(v -> TradeAuditActivity.open(this, ""));
        binding.btnManageTradeTemplates.setOnClickListener(v -> showTradeTemplateManagementDialog());
        binding.btnSelectDefaultTradeTemplate.setOnClickListener(v -> showTradeTemplatePicker(false));
        binding.btnSelectQuickTradeTemplate.setOnClickListener(v -> showTradeTemplatePicker(true));
        binding.btnSaveTradeSettings.setOnClickListener(v -> saveTradeSettings());
        binding.switchFloatingEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            if (isChecked && !PermissionHelper.canDrawOverlays(this)) {
                PermissionHelper.openOverlaySettings(this);
                return;
            }
            viewModel.setFloatingEnabled(isChecked);
            MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.seekFloatingAlpha.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int safeValue = Math.max(20, progress);
                binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, safeValue));
                if (fromUser && !applying) {
                    viewModel.setFloatingAlpha(safeValue);
                    MonitorServiceController.dispatch(SettingsSectionActivity.this, AppConstants.ACTION_REFRESH_CONFIG);
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
            }
        });
        binding.switchShowBtc.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            viewModel.setShowBtc(isChecked);
            MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        });
        binding.switchShowXau.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (applying) {
                return;
            }
            viewModel.setShowXau(isChecked);
            MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        });
    }

    // 弹出缓存清理分类选择。
    private void confirmAndClearCache() {
        final boolean[] checked = new boolean[]{true, false, true};
        final String[] items = new String[]{"历史行情数据", "历史交易数据", "运行时缓存"};
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("清理缓存")
                .setMultiChoiceItems(items, checked, (dialogInterface, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("取消", null)
                .setPositiveButton("清理", (dialogInterface, which) -> clearCacheDataAsync(
                        CacheSectionClassifier.fromSelection(checked[0], checked[1], checked[2])))
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(this));
    }

    private void clearCacheDataAsync(CacheSectionClassifier.CacheSelection selection) {
        if (selection == null || cacheExecutor == null) {
            return;
        }
        cacheExecutor.execute(() -> {
            CacheClearResult result = clearCacheData(selection);
            runOnUiThread(() -> Toast.makeText(this,
                    "已清理：历史行情 " + result.marketDeleted
                            + "，历史交易 " + result.tradeDeleted
                            + "，运行时文件 " + result.cacheDeleted,
                    Toast.LENGTH_SHORT).show());
        });
    }

    // 按勾选分类执行缓存清理。
    private CacheClearResult clearCacheData(CacheSectionClassifier.CacheSelection selection) {
        if (selection == null) {
            return new CacheClearResult(0, 0, 0);
        }
        int marketDeleted = 0;
        int tradeDeleted = 0;
        int cacheDeleted = 0;
        if (selection.shouldClearHistoryMarket()) {
            marketDeleted += new ChartHistoryRepository(this).clearAllHistory();
        }
        if (selection.shouldClearHistoryTrade()) {
            tradeDeleted = new AccountStorageRepository(this).clearTradeHistory();
        }
        if (selection.shouldClearRuntime()) {
            new AccountStorageRepository(this).clearRuntimeSnapshot();
            AccountStatsPreloadManager.getInstance(getApplicationContext()).clearLatestCache();
            getSharedPreferences(MarketChartActivity.PREF_RUNTIME_NAME, MODE_PRIVATE).edit().clear().apply();
            cacheDeleted = deleteDirectoryChildren(getCacheDir());
        }
        return new CacheClearResult(marketDeleted, tradeDeleted, cacheDeleted);
    }

    private static final class CacheClearResult {
        private final int marketDeleted;
        private final int tradeDeleted;
        private final int cacheDeleted;

        private CacheClearResult(int marketDeleted, int tradeDeleted, int cacheDeleted) {
            this.marketDeleted = marketDeleted;
            this.tradeDeleted = tradeDeleted;
            this.cacheDeleted = cacheDeleted;
        }
    }

    // 清理目录下所有子文件。
    private int deleteDirectoryChildren(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return 0;
        }
        File[] children = directory.listFiles();
        if (children == null || children.length == 0) {
            return 0;
        }
        int deleted = 0;
        for (File child : children) {
            deleted += deleteRecursively(child);
        }
        return deleted;
    }

    // 递归删除单个文件或目录。
    private int deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return 0;
        }
        int deleted = 0;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleted += deleteRecursively(child);
                }
            }
        }
        if (file.delete()) {
            deleted++;
        }
        return deleted;
    }

    // 把当前持久化设置回显到页面。
    private void applySettings() {
        applying = true;
        binding.switchFloatingEnabled.setChecked(viewModel.isFloatingEnabled());
        int alpha = viewModel.getFloatingAlpha();
        binding.seekFloatingAlpha.setProgress(alpha);
        binding.tvAlphaValue.setText(getString(R.string.alpha_suffix, alpha));
        binding.switchShowBtc.setChecked(viewModel.isShowBtc());
        binding.switchShowXau.setChecked(viewModel.isShowXau());
        applyTradeSettings();
        lockGatewayEntrySection();
        applying = false;
    }

    // 把交易默认参数、模板和风控阈值回显到设置页。
    private void applyTradeSettings() {
        draftTradeTemplates.clear();
        draftTradeTemplates.addAll(copyTradeTemplates(tradeTemplateRepository.getTemplates()));
        TradeTemplate defaultTemplate = findTemplateById(draftTradeTemplates, tradeTemplateRepository.getDefaultTemplate().getTemplateId());
        TradeTemplate quickTemplate = findTemplateById(draftTradeTemplates, tradeTemplateRepository.getQuickTradeTemplate().getTemplateId());
        selectedDefaultTemplateId = defaultTemplate.getTemplateId();
        selectedQuickTemplateId = quickTemplate.getTemplateId();
        binding.tvTradeDefaultTemplate.setText(getString(R.string.settings_trade_default_template_label, defaultTemplate.getDisplayName()));
        binding.tvTradeQuickTemplate.setText(getString(R.string.settings_trade_quick_template_label, quickTemplate.getDisplayName()));
        updateTradeTemplateManageSummary();
        binding.etTradeDefaultVolume.setText(formatDecimal(configManager.getTradeDefaultVolume()));
        binding.etTradeDefaultSl.setText(formatDecimal(configManager.getTradeDefaultSl()));
        binding.etTradeDefaultTp.setText(formatDecimal(configManager.getTradeDefaultTp()));
        binding.etTradeMaxQuickVolume.setText(formatDecimal(configManager.getTradeMaxQuickMarketVolume()));
        binding.etTradeMaxSingleVolume.setText(formatDecimal(configManager.getTradeMaxSingleMarketVolume()));
        binding.etTradeMaxBatchItems.setText(String.valueOf(configManager.getTradeMaxBatchItems()));
        binding.etTradeMaxBatchTotalVolume.setText(formatDecimal(configManager.getTradeMaxBatchTotalVolume()));
        binding.switchTradeForceConfirmAddPosition.setChecked(configManager.isTradeForceConfirmAddPosition());
        binding.switchTradeForceConfirmReverse.setChecked(configManager.isTradeForceConfirmReverse());
    }

    // 弹出模板选择列表，只改变待保存的模板指向。
    private void showTradeTemplatePicker(boolean quickTemplate) {
        List<TradeTemplate> templates = draftTradeTemplates.isEmpty()
                ? copyTradeTemplates(tradeTemplateRepository.getTemplates())
                : copyTradeTemplates(draftTradeTemplates);
        if (templates.isEmpty()) {
            return;
        }
        String[] displayNames = new String[templates.size()];
        int checkedIndex = 0;
        String currentId = quickTemplate ? selectedQuickTemplateId : selectedDefaultTemplateId;
        for (int index = 0; index < templates.size(); index++) {
            TradeTemplate template = templates.get(index);
            displayNames[index] = template.getDisplayName();
            if (template.getTemplateId().equals(currentId)) {
                checkedIndex = index;
            }
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(quickTemplate ? R.string.settings_trade_quick_template_action : R.string.settings_trade_default_template_action)
                .setSingleChoiceItems(displayNames, checkedIndex, (dialogInterface, which) -> {
                    TradeTemplate template = templates.get(which);
                    if (quickTemplate) {
                        selectedQuickTemplateId = template.getTemplateId();
                        binding.tvTradeQuickTemplate.setText(getString(R.string.settings_trade_quick_template_label, template.getDisplayName()));
                    } else {
                        selectedDefaultTemplateId = template.getTemplateId();
                        binding.tvTradeDefaultTemplate.setText(getString(R.string.settings_trade_default_template_label, template.getDisplayName()));
                    }
                    dialogInterface.dismiss();
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(this));
    }

    // 弹出模板管理列表，统一承接查看、新增和进入编辑删除分支。
    private void showTradeTemplateManagementDialog() {
        List<TradeTemplate> templates = draftTradeTemplates.isEmpty()
                ? copyTradeTemplates(tradeTemplateRepository.getTemplates())
                : copyTradeTemplates(draftTradeTemplates);
        if (templates.isEmpty()) {
            Toast.makeText(this, R.string.settings_trade_template_manage_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[templates.size()];
        for (int index = 0; index < templates.size(); index++) {
            TradeTemplate template = templates.get(index);
            items[index] = buildTemplateManageItemText(template);
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_trade_template_manage_title)
                .setItems(items, (dialogInterface, which) -> showTradeTemplateActionDialog(templates.get(which)))
                .setPositiveButton(R.string.settings_trade_template_add, (dialogInterface, which) -> showTradeTemplateEditorDialog(null))
                .setNegativeButton("关闭", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(this));
    }

    // 针对单个模板展示编辑或删除动作。
    private void showTradeTemplateActionDialog(@NonNull TradeTemplate template) {
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.settings_trade_template_edit));
        if (!isSystemTradeTemplate(template.getTemplateId())) {
            actions.add(getString(R.string.settings_trade_template_delete));
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(template.getDisplayName())
                .setItems(actions.toArray(new String[0]), (dialogInterface, which) -> {
                    String action = actions.get(which);
                    if (getString(R.string.settings_trade_template_edit).equals(action)) {
                        showTradeTemplateEditorDialog(template);
                        return;
                    }
                    deleteTradeTemplate(template);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(this));
    }

    // 弹出模板编辑对话框，负责新增或修改单个模板。
    private void showTradeTemplateEditorDialog(@Nullable TradeTemplate editingTemplate) {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        LinearLayout container = createTemplateEditorContainer();
        TextInputLayout nameLayout = createTemplateInputLayout(R.string.settings_trade_template_name_hint);
        TextInputEditText nameInput = createTemplateInputEditText(InputType.TYPE_CLASS_TEXT);
        nameInput.setText(editingTemplate == null ? "" : editingTemplate.getDisplayName());
        nameLayout.addView(nameInput);
        container.addView(nameLayout);

        TextInputLayout volumeLayout = createTemplateInputLayout(R.string.settings_trade_default_volume);
        TextInputEditText volumeInput = createTemplateInputEditText(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        volumeInput.setText(formatDecimal(editingTemplate == null ? 0d : editingTemplate.getDefaultVolume()));
        volumeLayout.addView(volumeInput);
        container.addView(volumeLayout);

        TextInputLayout slLayout = createTemplateInputLayout(R.string.settings_trade_default_sl);
        TextInputEditText slInput = createTemplateInputEditText(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        slInput.setText(formatDecimal(editingTemplate == null ? 0d : editingTemplate.getDefaultSl()));
        slLayout.addView(slInput);
        container.addView(slLayout);

        TextInputLayout tpLayout = createTemplateInputLayout(R.string.settings_trade_default_tp);
        TextInputEditText tpInput = createTemplateInputEditText(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tpInput.setText(formatDecimal(editingTemplate == null ? 0d : editingTemplate.getDefaultTp()));
        tpLayout.addView(tpInput);
        container.addView(tpLayout);

        TextView scopeLabel = new TextView(this);
        scopeLabel.setTextAppearance(this, R.style.TextAppearance_BinanceMonitor_Caption);
        scopeLabel.setTextColor(palette.textSecondary);
        scopeLabel.setText(R.string.settings_trade_template_scope);
        LinearLayout.LayoutParams scopeLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scopeLabelParams.topMargin = dp(12);
        scopeLabel.setLayoutParams(scopeLabelParams);
        container.addView(scopeLabel);

        Button scopeButton = new Button(this);
        LinearLayout.LayoutParams scopeButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.control_height_md)
        );
        scopeButtonParams.topMargin = dp(8);
        scopeButton.setLayoutParams(scopeButtonParams);
        UiPaletteManager.styleActionButton(
                scopeButton,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        final String[] selectedScope = new String[]{normalizeTemplateScope(editingTemplate == null ? TEMPLATE_SCOPE_BOTH : editingTemplate.getEntryScope())};
        scopeButton.setText(resolveTemplateScopeLabel(selectedScope[0]));
        scopeButton.setOnClickListener(v -> showTradeTemplateScopePicker(scopeButton, selectedScope));
        container.addView(scopeButton);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(editingTemplate == null ? R.string.settings_trade_template_add : R.string.settings_trade_template_edit)
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton(editingTemplate == null ? R.string.settings_trade_template_add : R.string.settings_trade_template_save, null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String displayName = safeText(nameInput.getText());
            if (displayName.isEmpty()) {
                Toast.makeText(this, R.string.settings_trade_template_name_required, Toast.LENGTH_SHORT).show();
                return;
            }
            double defaultVolume = parseDouble(volumeInput.getText(), -1d, 0d);
            if (defaultVolume <= 0d) {
                Toast.makeText(this, R.string.settings_trade_template_volume_required, Toast.LENGTH_SHORT).show();
                return;
            }
            TradeTemplate savedTemplate = new TradeTemplate(
                    editingTemplate == null ? buildTradeTemplateId() : editingTemplate.getTemplateId(),
                    displayName,
                    defaultVolume,
                    parseDouble(slInput.getText(), 0d, 0d),
                    parseDouble(tpInput.getText(), 0d, 0d),
                    selectedScope[0]
            );
            saveTradeTemplate(savedTemplate, editingTemplate == null);
            dialog.dismiss();
        });
    }

    // 删除一个非系统模板，并同步默认模板与快捷模板的待保存指向。
    private void deleteTradeTemplate(@NonNull TradeTemplate template) {
        if (isSystemTradeTemplate(template.getTemplateId())) {
            Toast.makeText(this, R.string.settings_trade_template_delete_system_blocked, Toast.LENGTH_SHORT).show();
            return;
        }
        List<TradeTemplate> remaining = new ArrayList<>();
        for (TradeTemplate item : draftTradeTemplates) {
            if (item != null && !template.getTemplateId().equals(item.getTemplateId())) {
                remaining.add(item);
            }
        }
        if (remaining.isEmpty()) {
            Toast.makeText(this, R.string.settings_trade_template_delete_last_blocked, Toast.LENGTH_SHORT).show();
            return;
        }
        draftTradeTemplates.clear();
        draftTradeTemplates.addAll(remaining);
        TradeTemplate fallbackTemplate = remaining.get(0);
        if (template.getTemplateId().equals(selectedDefaultTemplateId)) {
            selectedDefaultTemplateId = fallbackTemplate.getTemplateId();
        }
        if (template.getTemplateId().equals(selectedQuickTemplateId)) {
            selectedQuickTemplateId = findTemplateForScope(remaining, fallbackTemplate.getTemplateId(), TEMPLATE_SCOPE_MARKET).getTemplateId();
        }
        syncTemplateLabels();
        Toast.makeText(this, R.string.settings_trade_template_deleted, Toast.LENGTH_SHORT).show();
    }

    // 保存模板到当前草稿列表，不直接落盘，仍由交易设置保存按钮统一提交。
    private void saveTradeTemplate(@NonNull TradeTemplate template, boolean addingNew) {
        boolean replaced = false;
        for (int index = 0; index < draftTradeTemplates.size(); index++) {
            TradeTemplate current = draftTradeTemplates.get(index);
            if (current != null && template.getTemplateId().equals(current.getTemplateId())) {
                draftTradeTemplates.set(index, template);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            draftTradeTemplates.add(template);
        }
        if (addingNew && selectedDefaultTemplateId.trim().isEmpty()) {
            selectedDefaultTemplateId = template.getTemplateId();
        }
        if (addingNew && selectedQuickTemplateId.trim().isEmpty()) {
            selectedQuickTemplateId = template.getTemplateId();
        }
        syncTemplateLabels();
        Toast.makeText(this,
                getString(addingNew ? R.string.settings_trade_template_added : R.string.settings_trade_template_updated),
                Toast.LENGTH_SHORT).show();
    }

    // 保存交易低频设置，并刷新交易页共享配置真值。
    private void saveTradeSettings() {
        List<TradeTemplate> templates = draftTradeTemplates.isEmpty()
                ? copyTradeTemplates(tradeTemplateRepository.getTemplates())
                : copyTradeTemplates(draftTradeTemplates);
        tradeTemplateRepository.saveTemplates(templates, selectedDefaultTemplateId, selectedQuickTemplateId);
        configManager.setTradeDefaultVolume(parseDouble(binding.etTradeDefaultVolume.getText(), configManager.getTradeDefaultVolume(), 0d));
        configManager.setTradeDefaultSl(parseDouble(binding.etTradeDefaultSl.getText(), configManager.getTradeDefaultSl(), 0d));
        configManager.setTradeDefaultTp(parseDouble(binding.etTradeDefaultTp.getText(), configManager.getTradeDefaultTp(), 0d));
        configManager.setTradeMaxQuickMarketVolume(parseDouble(binding.etTradeMaxQuickVolume.getText(), configManager.getTradeMaxQuickMarketVolume(), 0d));
        configManager.setTradeMaxSingleMarketVolume(parseDouble(binding.etTradeMaxSingleVolume.getText(), configManager.getTradeMaxSingleMarketVolume(), configManager.getTradeMaxQuickMarketVolume()));
        configManager.setTradeMaxBatchItems(parseInt(binding.etTradeMaxBatchItems.getText(), configManager.getTradeMaxBatchItems(), 1));
        configManager.setTradeMaxBatchTotalVolume(parseDouble(binding.etTradeMaxBatchTotalVolume.getText(), configManager.getTradeMaxBatchTotalVolume(), 0d));
        configManager.setTradeForceConfirmAddPosition(binding.switchTradeForceConfirmAddPosition.isChecked());
        configManager.setTradeForceConfirmReverse(binding.switchTradeForceConfirmReverse.isChecked());
        applyTradeSettings();
        MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        Toast.makeText(this, getString(R.string.settings_trade_saved), Toast.LENGTH_SHORT).show();
    }

    // 第 1 步入口唯一化后，设置页只展示固定公网入口，不再允许本地改写主链。
    private void lockGatewayEntrySection() {
        binding.etMt5GatewayUrl.setText(AppConstants.MT5_GATEWAY_BASE_URL);
        binding.etMt5GatewayUrl.setSelection(AppConstants.MT5_GATEWAY_BASE_URL.length());
        binding.etMt5GatewayUrl.setEnabled(false);
        binding.etMt5GatewayUrl.setFocusable(false);
        binding.etMt5GatewayUrl.setFocusableInTouchMode(false);
        binding.etMt5GatewayUrl.setClickable(false);
        binding.btnSaveMt5GatewayUrl.setVisibility(View.GONE);
    }

    // 设置页里的低频布尔项、输入项和动作按钮统一走标准主体。
    private void applyStandardSubjectStyles(UiPaletteManager.Palette palette) {
        UiPaletteManager.styleToggleChoice(binding.switchFloatingEnabled, palette);
        UiPaletteManager.styleToggleChoice(binding.switchShowBtc, palette);
        UiPaletteManager.styleToggleChoice(binding.switchShowXau, palette);
        UiPaletteManager.styleToggleChoice(binding.switchTradeForceConfirmAddPosition, palette);
        UiPaletteManager.styleToggleChoice(binding.switchTradeForceConfirmReverse, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutMt5GatewayUrl, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutTradeDefaultVolume, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutTradeDefaultSl, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutTradeDefaultTp, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutTradeMaxQuickVolume, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutTradeMaxSingleVolume, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutTradeMaxBatchItems, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutTradeMaxBatchTotalVolume, palette);
        UiPaletteManager.styleActionButton(
                binding.btnClearCache,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnOpenTradeAudit,
                palette,
                palette.primarySoft,
                UiPaletteManager.controlSelectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnSaveMt5GatewayUrl,
                palette,
                palette.primarySoft,
                UiPaletteManager.controlSelectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnSelectDefaultTradeTemplate,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnManageTradeTemplates,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnSelectQuickTradeTemplate,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        UiPaletteManager.styleActionButton(
                binding.btnSaveTradeSettings,
                palette,
                palette.primarySoft,
                UiPaletteManager.controlSelectedText(this),
                R.style.TextAppearance_BinanceMonitor_Control
        );
    }

    // 应用当前主题色。
    private void applyPaletteStyles() {
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(this);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        UiPaletteManager.applySystemBars(this, palette);
        binding.btnBack.setTextColor(palette.textPrimary);
        binding.tvSettingsDetailTitle.setText(sectionTitle);
        binding.tvSettingsDetailTitle.setTextColor(palette.textPrimary);
        applyStandardSubjectStyles(palette);
        binding.cardFloatingSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardGatewaySection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardTradeSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.cardCacheSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
        binding.tvTradeSectionSummary.setTextColor(palette.textSecondary);
        binding.tvTradeTemplateManageSummary.setTextColor(palette.textSecondary);
        binding.tvTradeDefaultTemplate.setTextColor(palette.textPrimary);
        binding.tvTradeQuickTemplate.setTextColor(palette.textPrimary);
    }

    // 保存网关地址设置。
    private void saveMt5GatewayAddress() {
        viewModel.setMt5GatewayBaseUrl(AppConstants.MT5_GATEWAY_BASE_URL);
        binding.etMt5GatewayUrl.setText(AppConstants.MT5_GATEWAY_BASE_URL);
        binding.etMt5GatewayUrl.setSelection(AppConstants.MT5_GATEWAY_BASE_URL.length());
        AccountStatsPreloadManager.getInstance(getApplicationContext()).clearLatestCache();
        MonitorServiceController.dispatch(this, AppConstants.ACTION_REFRESH_CONFIG);
        Toast.makeText(this, getString(R.string.mt5_gateway_saved, AppConstants.MT5_GATEWAY_BASE_URL), Toast.LENGTH_SHORT).show();
    }

    // 根据分类控制可见模块。
    private void applyVisibleSection() {
        binding.cardFloatingSection.setVisibility(SettingsActivity.SECTION_DISPLAY.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardGatewaySection.setVisibility(SettingsActivity.SECTION_GATEWAY.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardTradeSection.setVisibility(SettingsActivity.SECTION_TRADE.equals(sectionKey) ? View.VISIBLE : View.GONE);
        binding.cardCacheSection.setVisibility(SettingsActivity.SECTION_CACHE.equals(sectionKey) ? View.VISIBLE : View.GONE);
    }

    // 统一格式化设置页小数展示，避免无意义的尾零噪音。
    private String formatDecimal(double value) {
        if (Math.abs(value) < 0.0000001d) {
            return "0";
        }
        String text = String.format(Locale.US, "%.4f", value);
        while (text.contains(".") && (text.endsWith("0") || text.endsWith("."))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    // 解析小数输入，失败时回退到当前值，并保证不低于给定下限。
    private double parseDouble(@Nullable CharSequence input, double fallback, double minValue) {
        try {
            double parsed = Double.parseDouble(input == null ? "" : input.toString().trim());
            return Math.max(minValue, parsed);
        } catch (Exception ignored) {
            return Math.max(minValue, fallback);
        }
    }

    // 解析整数输入，失败时回退到当前值，并保证不低于给定下限。
    private int parseInt(@Nullable CharSequence input, int fallback, int minValue) {
        try {
            int parsed = Integer.parseInt(input == null ? "" : input.toString().trim());
            return Math.max(minValue, parsed);
        } catch (Exception ignored) {
            return Math.max(minValue, fallback);
        }
    }

    // 刷新模板管理摘要与默认模板标签。
    private void syncTemplateLabels() {
        TradeTemplate defaultTemplate = findTemplateById(draftTradeTemplates, selectedDefaultTemplateId);
        TradeTemplate quickTemplate = findTemplateForScope(draftTradeTemplates, selectedQuickTemplateId, TEMPLATE_SCOPE_MARKET);
        selectedDefaultTemplateId = defaultTemplate.getTemplateId();
        selectedQuickTemplateId = quickTemplate.getTemplateId();
        binding.tvTradeDefaultTemplate.setText(getString(R.string.settings_trade_default_template_label, defaultTemplate.getDisplayName()));
        binding.tvTradeQuickTemplate.setText(getString(R.string.settings_trade_quick_template_label, quickTemplate.getDisplayName()));
        updateTradeTemplateManageSummary();
    }

    // 更新模板管理摘要，告诉用户当前草稿里有多少模板。
    private void updateTradeTemplateManageSummary() {
        binding.tvTradeTemplateManageSummary.setText(getString(
                R.string.settings_trade_template_manage_summary,
                draftTradeTemplates.size()
        ));
    }

    // 生成模板管理列表里的单行说明。
    @NonNull
    private String buildTemplateManageItemText(@NonNull TradeTemplate template) {
        return getString(
                R.string.settings_trade_template_manage_item,
                template.getDisplayName(),
                formatDecimal(template.getDefaultVolume()),
                resolveTemplateScopeLabel(template.getEntryScope())
        );
    }

    // 弹出模板适用范围选择框。
    private void showTradeTemplateScopePicker(@NonNull Button scopeButton, @NonNull String[] selectedScope) {
        String[] values = new String[]{TEMPLATE_SCOPE_BOTH, TEMPLATE_SCOPE_MARKET, TEMPLATE_SCOPE_PENDING};
        String[] labels = new String[]{
                getString(R.string.settings_trade_template_scope_both),
                getString(R.string.settings_trade_template_scope_market),
                getString(R.string.settings_trade_template_scope_pending)
        };
        int checkedIndex = 0;
        for (int index = 0; index < values.length; index++) {
            if (values[index].equals(normalizeTemplateScope(selectedScope[0]))) {
                checkedIndex = index;
                break;
            }
        }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_trade_template_scope)
                .setSingleChoiceItems(labels, checkedIndex, (dialogInterface, which) -> {
                    selectedScope[0] = values[which];
                    scopeButton.setText(labels[which]);
                    dialogInterface.dismiss();
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, UiPaletteManager.resolve(this));
    }

    @NonNull
    private LinearLayout createTemplateEditorContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = dp(4);
        container.setPadding(horizontalPadding, dp(4), horizontalPadding, 0);
        return container;
    }

    @NonNull
    private TextInputLayout createTemplateInputLayout(int hintResId) {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint(getString(hintResId));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        if (hintResId != R.string.settings_trade_template_name_hint) {
            params.topMargin = dp(8);
        }
        inputLayout.setLayoutParams(params);
        UiPaletteManager.styleInputField(inputLayout, UiPaletteManager.resolve(this));
        return inputLayout;
    }

    @NonNull
    private TextInputEditText createTemplateInputEditText(int inputType) {
        TextInputEditText editText = new TextInputEditText(this);
        editText.setInputType(inputType);
        editText.setSingleLine(true);
        editText.setTextAppearance(this, R.style.TextAppearance_BinanceMonitor_BodyCompact);
        return editText;
    }

    @NonNull
    private List<TradeTemplate> copyTradeTemplates(@Nullable List<TradeTemplate> source) {
        List<TradeTemplate> copied = new ArrayList<>();
        if (source == null) {
            return copied;
        }
        for (TradeTemplate template : source) {
            if (template == null) {
                continue;
            }
            copied.add(new TradeTemplate(
                    template.getTemplateId(),
                    template.getDisplayName(),
                    template.getDefaultVolume(),
                    template.getDefaultSl(),
                    template.getDefaultTp(),
                    template.getEntryScope()
            ));
        }
        return copied;
    }

    @NonNull
    private TradeTemplate findTemplateById(@Nullable List<TradeTemplate> templates, @Nullable String templateId) {
        String safeTemplateId = templateId == null ? "" : templateId.trim();
        if (templates != null) {
            for (TradeTemplate template : templates) {
                if (template != null && template.getTemplateId().equals(safeTemplateId) && !safeTemplateId.isEmpty()) {
                    return template;
                }
            }
            if (!templates.isEmpty()) {
                return templates.get(0);
            }
        }
        return tradeTemplateRepository.getDefaultTemplate();
    }

    @NonNull
    private TradeTemplate findTemplateForScope(@Nullable List<TradeTemplate> templates,
                                               @Nullable String templateId,
                                               @NonNull String requiredScope) {
        TradeTemplate matched = findTemplateById(templates, templateId);
        if (supportsTemplateScope(matched, requiredScope)) {
            return matched;
        }
        if (templates != null) {
            for (TradeTemplate template : templates) {
                if (supportsTemplateScope(template, requiredScope)) {
                    return template;
                }
            }
        }
        return matched;
    }

    private boolean supportsTemplateScope(@Nullable TradeTemplate template, @NonNull String requiredScope) {
        if (template == null) {
            return false;
        }
        String scope = normalizeTemplateScope(template.getEntryScope());
        return TEMPLATE_SCOPE_BOTH.equals(scope) || requiredScope.equals(scope);
    }

    private boolean isSystemTradeTemplate(@Nullable String templateId) {
        String safeTemplateId = templateId == null ? "" : templateId.trim();
        return SYSTEM_TEMPLATE_DEFAULT.equals(safeTemplateId)
                || SYSTEM_TEMPLATE_SCALP.equals(safeTemplateId)
                || SYSTEM_TEMPLATE_SWING.equals(safeTemplateId);
    }

    @NonNull
    private String resolveTemplateScopeLabel(@Nullable String scope) {
        String safeScope = normalizeTemplateScope(scope);
        if (TEMPLATE_SCOPE_MARKET.equals(safeScope)) {
            return getString(R.string.settings_trade_template_scope_market);
        }
        if (TEMPLATE_SCOPE_PENDING.equals(safeScope)) {
            return getString(R.string.settings_trade_template_scope_pending);
        }
        return getString(R.string.settings_trade_template_scope_both);
    }

    @NonNull
    private String normalizeTemplateScope(@Nullable String scope) {
        String safeScope = scope == null ? "" : scope.trim().toLowerCase(Locale.ROOT);
        if (TEMPLATE_SCOPE_MARKET.equals(safeScope) || TEMPLATE_SCOPE_PENDING.equals(safeScope)) {
            return safeScope;
        }
        return TEMPLATE_SCOPE_BOTH;
    }

    @NonNull
    private String buildTradeTemplateId() {
        return "custom_" + System.currentTimeMillis();
    }

    @NonNull
    private String safeText(@Nullable CharSequence input) {
        return input == null ? "" : input.toString().trim();
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

}
