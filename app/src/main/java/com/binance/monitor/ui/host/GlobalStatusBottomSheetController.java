/*
 * 全局状态底部弹层控制器，负责承接交易页顶部状态按钮和状态详情弹层。
 * 与主壳导航、日志页和异常记录列表协同工作。
 */
package com.binance.monitor.ui.host;

import android.text.TextUtils;
import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.SymbolConfig;
import com.binance.monitor.databinding.DialogAbnormalRecordsBinding;
import com.binance.monitor.databinding.DialogAbnormalThresholdSettingsBinding;
import com.binance.monitor.databinding.DialogGlobalStatusSheetBinding;
import com.binance.monitor.service.MonitorServiceController;
import com.binance.monitor.ui.adapter.AbnormalRecordAdapter;
import com.binance.monitor.ui.log.LogActivity;
import com.binance.monitor.ui.settings.SettingsActivity;
import com.binance.monitor.ui.theme.SpacingTokenResolver;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.ui.trade.TradeAuditActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GlobalStatusBottomSheetController {

    private final AppCompatActivity activity;
    @Nullable
    private BottomSheetDialog activeDialog;
    @Nullable
    private DialogGlobalStatusSheetBinding activeBinding;

    // 创建交易页全局状态底部弹层控制器。
    public GlobalStatusBottomSheetController(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    // 刷新顶部状态按钮，让按钮文案与弹层使用同一份状态快照。
    public void bindCompactButton(@NonNull TextView button, @NonNull StatusSnapshot snapshot) {
        button.setText(activity.getString(
                R.string.global_status_button_compact,
                snapshot.getStageText(),
                snapshot.getCompactAccountText()
        ));
    }

    // 展示底部弹层，并提供设置、交易追踪、日志和异常列表四个快捷入口。
    public void show(@NonNull StatusSnapshot snapshot) {
        View sheetView = activity.getLayoutInflater().inflate(R.layout.dialog_global_status_sheet, null, false);
        DialogGlobalStatusSheetBinding binding = DialogGlobalStatusSheetBinding.bind(sheetView);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(sheetView);
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        styleActionButton(binding.btnGlobalStatusSettings, palette);
        styleActionButton(binding.btnGlobalStatusTradeAudit, palette);
        styleActionButton(binding.btnGlobalStatusLogs, palette);
        styleActionButton(binding.btnGlobalStatusAbnormal, palette);
        bindSnapshot(binding, snapshot);
        binding.btnGlobalStatusSettings.setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, SettingsActivity.class));
        });
        binding.btnGlobalStatusTradeAudit.setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, TradeAuditActivity.class));
        });
        binding.btnGlobalStatusLogs.setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, LogActivity.class));
        });
        binding.btnGlobalStatusAbnormal.setOnClickListener(v -> {
            dialog.dismiss();
            showAbnormalRecords(snapshot.getAbnormalRecords());
        });
        dialog.setOnDismissListener(ignored -> {
            if (activeDialog == dialog) {
                activeDialog = null;
                activeBinding = null;
            }
        });
        activeDialog = dialog;
        activeBinding = binding;
        dialog.show();
        UiPaletteManager.applyBottomSheetSurface(dialog, palette);
        binding.getRoot().post(() -> applyActionRowLayout(binding));
    }

    // 仅在状态弹层当前可见时刷新内容，避免图表页自己持有弹层内部视图。
    public void updateVisibleSheet(@NonNull StatusSnapshot snapshot) {
        if (activeDialog == null || activeBinding == null || !activeDialog.isShowing()) {
            return;
        }
        bindSnapshot(activeBinding, snapshot);
    }

    // 对外暴露异常列表弹窗，供交易页风险提示条和摘要区复用。
    public void showAbnormalRecords(@NonNull List<AbnormalRecord> records) {
        showAbnormalRecordsDialog(records);
    }

    // 统一渲染底部弹层里的轻操作按钮，避免与主界面风格脱节。
    private void styleActionButton(@NonNull TextView button, @NonNull UiPaletteManager.Palette palette) {
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

    private void bindSnapshot(@NonNull DialogGlobalStatusSheetBinding binding,
                              @NonNull StatusSnapshot snapshot) {
        binding.tvGlobalStatusConnectionValue.setText(snapshot.getConnectionText());
        binding.tvGlobalStatusAccountValue.setText(snapshot.getAccountText());
        binding.tvGlobalStatusSyncValue.setText(snapshot.getSyncText());
        binding.tvGlobalStatusUpdatedValue.setText(snapshot.getRefreshedText());
        binding.tvGlobalStatusAbnormalValue.setText(activity.getString(
                R.string.global_status_abnormal_count,
                snapshot.getAbnormalCount()
        ));
    }

    // 底部弹层三个快捷入口统一按等分宽度铺满整行，避免不同设备上退回内容宽度。
    private void applyActionRowLayout(@NonNull DialogGlobalStatusSheetBinding binding) {
        int controlGapPx = SpacingTokenResolver.inlineGapPx(activity);
        applyWeightedActionButton(binding.btnGlobalStatusSettings, 0);
        applyWeightedActionButton(binding.btnGlobalStatusTradeAudit, controlGapPx);
        applyWeightedActionButton(binding.btnGlobalStatusLogs, controlGapPx);
        applyWeightedActionButton(binding.btnGlobalStatusAbnormal, controlGapPx);
        binding.btnGlobalStatusAbnormal.requestLayout();
    }

    // 统一动作按钮的运行时布局参数，确保三个按钮继续等宽对齐。
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

    // 复用现有异常列表样式，把状态弹层里的“异常列表”收口到完整弹窗。
    private void showAbnormalRecordsDialog(@NonNull List<AbnormalRecord> records) {
        DialogAbnormalRecordsBinding dialogBinding =
                DialogAbnormalRecordsBinding.inflate(activity.getLayoutInflater());
        AbnormalRecordAdapter dialogAdapter = new AbnormalRecordAdapter(true);
        dialogBinding.recyclerAbnormalRecords.setLayoutManager(new LinearLayoutManager(activity));
        dialogBinding.recyclerAbnormalRecords.setAdapter(dialogAdapter);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(dialogBinding.getRoot())
                .setNeutralButton(R.string.global_status_action_threshold,
                        (dialogInterface, which) -> {
                            showAbnormalThresholdDialog();
                        })
                .setPositiveButton(R.string.global_status_action_close, null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(dialogBinding.getRoot(), palette);
        dialogBinding.tvAbnormalDialogTitle.setTextColor(palette.textPrimary);
        dialogBinding.tvAbnormalDialogSubtitle.setTextColor(palette.textSecondary);
        dialogBinding.tvAbnormalRecordsEmpty.setTextColor(palette.textSecondary);
        dialogBinding.recyclerAbnormalRecords.setBackground(
                UiPaletteManager.createSectionBackground(activity, palette.surfaceEnd, palette.stroke));
        dialogBinding.etAbnormalSearch.setTextColor(palette.textPrimary);
        dialogBinding.etAbnormalSearch.setHintTextColor(palette.textSecondary);
        bindAllAbnormalRecords(dialogBinding, dialogAdapter, records, "");
        dialogBinding.etAbnormalSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                bindAllAbnormalRecords(dialogBinding, dialogAdapter, records, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setTextColor(palette.primary);
        }
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
        }
    }

    // 展示异常阈值快捷设置弹窗，直接复用现有 SymbolConfig 存储和刷新主链。
    private void showAbnormalThresholdDialog() {
        DialogAbnormalThresholdSettingsBinding dialogBinding =
                DialogAbnormalThresholdSettingsBinding.inflate(activity.getLayoutInflater());
        ConfigManager configManager = ConfigManager.getInstance(activity);
        List<String> symbols = new ArrayList<>(AppConstants.MONITOR_SYMBOLS);
        if (symbols.isEmpty()) {
            symbols.add(AppConstants.SYMBOL_BTC);
        }
        ArrayAdapter<String> adapter = buildThresholdSymbolAdapter(symbols);
        dialogBinding.spinnerAbnormalThresholdSymbol.setAdapter(adapter);
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(dialogBinding.getRoot(), palette);
        styleThresholdDialogSubjects(dialogBinding, palette);
        dialogBinding.radioAbnormalLogicAnd.setChecked(configManager.isUseAndMode());
        dialogBinding.radioAbnormalLogicOr.setChecked(!configManager.isUseAndMode());
        String initialSymbol = symbols.get(0);
        dialogBinding.tvAbnormalThresholdSymbolLabel.setOnClickListener(
                v -> dialogBinding.spinnerAbnormalThresholdSymbol.performClick());
        dialogBinding.spinnerAbnormalThresholdSymbol.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Object item = parent.getItemAtPosition(position);
                bindAbnormalThresholdSymbolLabel(dialogBinding, item);
                bindAbnormalThresholdFields(dialogBinding, configManager.getSymbolConfig(resolveThresholdSymbol(item)));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        bindAbnormalThresholdSymbolLabel(dialogBinding, initialSymbol);
        bindAbnormalThresholdFields(dialogBinding, configManager.getSymbolConfig(initialSymbol));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.global_status_action_close, null)
                .setPositiveButton(R.string.global_status_action_save, (dialogInterface, which) -> {
                    saveAbnormalThresholdConfig(dialogBinding);
                    Toast.makeText(activity, R.string.global_status_threshold_saved, Toast.LENGTH_SHORT).show();
                });
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        UiPaletteManager.applyAlertDialogSurface(dialog, palette);
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(palette.textSecondary);
        }
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
        }
    }

    // 异常阈值弹窗里的低频字段统一切到标准主体，避免再长出独立表单语言。
    private void styleThresholdDialogSubjects(@NonNull DialogAbnormalThresholdSettingsBinding binding,
                                              @NonNull UiPaletteManager.Palette palette) {
        UiPaletteManager.styleSelectFieldLabel(
                binding.tvAbnormalThresholdSymbolLabel,
                palette,
                palette.control,
                palette.textPrimary,
                R.style.TextAppearance_BinanceMonitor_Control
        );
        binding.spinnerAbnormalThresholdSymbol.setBackground(null);
        UiPaletteManager.styleInputField(binding.inputLayoutAbnormalThresholdVolume, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutAbnormalThresholdAmount, palette);
        UiPaletteManager.styleInputField(binding.inputLayoutAbnormalThresholdPrice, palette);
        UiPaletteManager.styleToggleChoice(binding.switchAbnormalThresholdVolume, palette);
        UiPaletteManager.styleToggleChoice(binding.switchAbnormalThresholdAmount, palette);
        UiPaletteManager.styleToggleChoice(binding.switchAbnormalThresholdPrice, palette);
        UiPaletteManager.styleToggleChoice(binding.radioAbnormalLogicOr, palette);
        UiPaletteManager.styleToggleChoice(binding.radioAbnormalLogicAnd, palette);
    }

    // 产品选择展示统一显示短标签，和 SelectField 标签保持同一份文案。
    private void bindAbnormalThresholdSymbolLabel(@NonNull DialogAbnormalThresholdSettingsBinding binding,
                                                  @Nullable Object item) {
        binding.tvAbnormalThresholdSymbolLabel.setText(shortSymbolName(resolveThresholdSymbol(item)));
    }

    // 统一绑定当前产品的阈值输入框，保持异常列表里的快捷设置和正式配置使用同一套数值。
    private void bindAbnormalThresholdFields(@NonNull DialogAbnormalThresholdSettingsBinding binding,
                                             @NonNull SymbolConfig config) {
        binding.etAbnormalThresholdVolume.setText(String.valueOf(config.getVolumeThreshold()));
        binding.etAbnormalThresholdAmount.setText(String.valueOf(config.getAmountThreshold() / 1_000_000d));
        binding.etAbnormalThresholdPrice.setText(String.valueOf(config.getPriceChangeThreshold()));
        binding.switchAbnormalThresholdVolume.setChecked(config.isVolumeEnabled());
        binding.switchAbnormalThresholdAmount.setChecked(config.isAmountEnabled());
        binding.switchAbnormalThresholdPrice.setChecked(config.isPriceChangeEnabled());
    }

    // 保存异常阈值后直接通知服务刷新配置并回推后端。
    private void saveAbnormalThresholdConfig(@NonNull DialogAbnormalThresholdSettingsBinding binding) {
        ConfigManager configManager = ConfigManager.getInstance(activity);
        String symbol = resolveThresholdSymbol(binding.spinnerAbnormalThresholdSymbol.getSelectedItem());
        SymbolConfig current = configManager.getSymbolConfig(symbol);
        current.setVolumeThreshold(parseThresholdInput(
                binding.etAbnormalThresholdVolume.getText() == null
                        ? null
                        : binding.etAbnormalThresholdVolume.getText().toString(),
                current.getVolumeThreshold()
        ));
        current.setAmountThreshold(parseThresholdInput(
                binding.etAbnormalThresholdAmount.getText() == null
                        ? null
                        : binding.etAbnormalThresholdAmount.getText().toString(),
                current.getAmountThreshold() / 1_000_000d
        ) * 1_000_000d);
        current.setPriceChangeThreshold(parseThresholdInput(
                binding.etAbnormalThresholdPrice.getText() == null
                        ? null
                        : binding.etAbnormalThresholdPrice.getText().toString(),
                current.getPriceChangeThreshold()
        ));
        current.setVolumeEnabled(binding.switchAbnormalThresholdVolume.isChecked());
        current.setAmountEnabled(binding.switchAbnormalThresholdAmount.isChecked());
        current.setPriceChangeEnabled(binding.switchAbnormalThresholdPrice.isChecked());
        configManager.saveSymbolConfig(current);
        configManager.setUseAndMode(binding.radioAbnormalLogicAnd.isChecked());
        MonitorServiceController.dispatch(activity, AppConstants.ACTION_REFRESH_CONFIG);
    }

    // 给产品选择器统一提供短标签，避免弹窗里继续显示冗长交易代码。
    @NonNull
    private ArrayAdapter<String> buildThresholdSymbolAdapter(@NonNull List<String> symbols) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                activity,
                R.layout.item_spinner_filter,
                android.R.id.text1,
                symbols
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                bindThresholdSymbolLabel(view, getItem(position));
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                bindThresholdSymbolLabel(view, getItem(position));
                return view;
            }
        };
        adapter.setDropDownViewResource(R.layout.item_spinner_filter_dropdown);
        return adapter;
    }

    // 把 BTCUSDT/XAUUSDT 收口成更短的产品文案，便于弹窗里快速切换。
    private void bindThresholdSymbolLabel(@NonNull View view, String symbol) {
        if (!(view instanceof TextView)) {
            return;
        }
        ((TextView) view).setText(shortSymbolName(symbol));
    }

    // 把选择器里的展示名映射回系统内部使用的标准产品代码。
    @NonNull
    private String resolveThresholdSymbol(Object item) {
        String value = item == null ? "" : String.valueOf(item).trim().toUpperCase(Locale.ROOT);
        if ("XAU".equals(value) || AppConstants.SYMBOL_XAU.equalsIgnoreCase(value)) {
            return AppConstants.SYMBOL_XAU;
        }
        return AppConstants.SYMBOL_BTC;
    }

    // 统一把产品代码转成更短的展示名。
    @NonNull
    private String shortSymbolName(String symbol) {
        if (AppConstants.SYMBOL_XAU.equalsIgnoreCase(symbol)) {
            return "XAU";
        }
        return "BTC";
    }

    // 解析用户输入的阈值，空值或异常时保留原值，避免误写成 0。
    private double parseThresholdInput(String value, double fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    // 根据搜索词过滤异常列表，保持状态入口里的异常查看也能快速定位。
    private void bindAllAbnormalRecords(@NonNull DialogAbnormalRecordsBinding dialogBinding,
                                        @NonNull AbnormalRecordAdapter adapter,
                                        @NonNull List<AbnormalRecord> records,
                                        @NonNull String keyword) {
        List<AbnormalRecord> filtered = filterAbnormalRecords(records, keyword);
        adapter.submitList(filtered);
        dialogBinding.tvAbnormalRecordsEmpty.setVisibility(filtered.isEmpty()
                ? android.view.View.VISIBLE
                : android.view.View.GONE);
    }

    // 只保留当前搜索词命中的异常记录，避免长列表直接压满弹窗。
    @NonNull
    private List<AbnormalRecord> filterAbnormalRecords(@NonNull List<AbnormalRecord> records,
                                                       @NonNull String keyword) {
        List<AbnormalRecord> filtered = new ArrayList<>();
        if (records.isEmpty()) {
            return filtered;
        }
        String query = keyword.trim().toUpperCase(Locale.ROOT);
        for (AbnormalRecord item : records) {
            if (item == null) {
                continue;
            }
            if (query.isEmpty()) {
                filtered.add(item);
            } else {
                String haystack = (safeText(item.getSymbol()) + " "
                        + safeText(item.getTriggerSummary()) + " "
                        + safeText(String.valueOf(item.getTimestamp()))).toUpperCase(Locale.ROOT);
                if (haystack.contains(query)) {
                    filtered.add(item);
                }
            }
            if (filtered.size() >= 500) {
                break;
            }
        }
        return filtered;
    }

    // 统一收口文本空值，避免状态按钮和搜索过滤出现空指针。
    @NonNull
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class StatusSnapshot {
        private final String stageText;
        private final String connectionText;
        private final String accountText;
        private final String compactAccountText;
        private final String syncText;
        private final String refreshedText;
        private final int abnormalCount;
        private final List<AbnormalRecord> abnormalRecords;

        // 构造当前全局状态快照，供按钮和底部弹层共享。
        public StatusSnapshot(@NonNull String stageText,
                              @NonNull String connectionText,
                              @NonNull String accountText,
                              @NonNull String compactAccountText,
                              @NonNull String syncText,
                              @NonNull String refreshedText,
                              int abnormalCount,
                              @NonNull List<AbnormalRecord> abnormalRecords) {
            this.stageText = stageText;
            this.connectionText = connectionText;
            this.accountText = accountText;
            this.compactAccountText = compactAccountText;
            this.syncText = syncText;
            this.refreshedText = refreshedText;
            this.abnormalCount = Math.max(0, abnormalCount);
            this.abnormalRecords = new ArrayList<>(abnormalRecords);
        }

        @NonNull
        public String getStageText() {
            return stageText;
        }

        @NonNull
        public String getConnectionText() {
            return connectionText;
        }

        @NonNull
        public String getAccountText() {
            return accountText;
        }

        @NonNull
        public String getCompactAccountText() {
            return compactAccountText;
        }

        @NonNull
        public String getSyncText() {
            return syncText;
        }

        @NonNull
        public String getRefreshedText() {
            return refreshedText;
        }

        public int getAbnormalCount() {
            return abnormalCount;
        }

        @NonNull
        public List<AbnormalRecord> getAbnormalRecords() {
            return new ArrayList<>(abnormalRecords);
        }
    }
}
