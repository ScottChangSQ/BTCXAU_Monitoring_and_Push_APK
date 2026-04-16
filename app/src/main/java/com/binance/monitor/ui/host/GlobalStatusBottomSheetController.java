/*
 * 全局状态底部弹层控制器，负责承接交易页顶部状态按钮和状态详情弹层。
 * 与主壳导航、日志页和异常记录列表协同工作。
 */
package com.binance.monitor.ui.host;

import android.content.Intent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.binance.monitor.R;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.databinding.DialogAbnormalRecordsBinding;
import com.binance.monitor.databinding.DialogGlobalStatusSheetBinding;
import com.binance.monitor.ui.adapter.AbnormalRecordAdapter;
import com.binance.monitor.ui.log.LogActivity;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GlobalStatusBottomSheetController {

    private final AppCompatActivity activity;

    // 创建交易页全局状态底部弹层控制器。
    public GlobalStatusBottomSheetController(@NonNull AppCompatActivity activity) {
        this.activity = activity;
    }

    // 刷新顶部状态按钮，让按钮文案与弹层使用同一份状态快照。
    public void bindCompactButton(@NonNull TextView button, @NonNull StatusSnapshot snapshot) {
        button.setText(activity.getString(
                R.string.global_status_button_compact,
                snapshot.getStageText(),
                snapshot.getAccountText(),
                snapshot.getAbnormalCount()
        ));
    }

    // 展示底部弹层，并提供设置、日志和异常列表三个快捷入口。
    public void show(@NonNull StatusSnapshot snapshot) {
        View sheetView = activity.getLayoutInflater().inflate(R.layout.dialog_global_status_sheet, null, false);
        DialogGlobalStatusSheetBinding binding = DialogGlobalStatusSheetBinding.bind(sheetView);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(sheetView);
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        styleActionButton(binding.btnGlobalStatusSettings, palette);
        styleActionButton(binding.btnGlobalStatusLogs, palette);
        styleActionButton(binding.btnGlobalStatusAbnormal, palette);
        binding.tvGlobalStatusConnectionValue.setText(snapshot.getConnectionText());
        binding.tvGlobalStatusAccountValue.setText(snapshot.getAccountText());
        binding.tvGlobalStatusSyncValue.setText(snapshot.getSyncText());
        binding.tvGlobalStatusUpdatedValue.setText(snapshot.getRefreshedText());
        binding.tvGlobalStatusAbnormalValue.setText(activity.getString(
                R.string.global_status_abnormal_count,
                snapshot.getAbnormalCount()
        ));
        binding.btnGlobalStatusSettings.setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(HostNavigationIntentFactory.forTab(activity, HostTab.SETTINGS));
        });
        binding.btnGlobalStatusLogs.setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, LogActivity.class));
        });
        binding.btnGlobalStatusAbnormal.setOnClickListener(v -> {
            dialog.dismiss();
            showAbnormalRecords(snapshot.getAbnormalRecords());
        });
        dialog.show();
    }

    // 对外暴露异常列表弹窗，供交易页风险提示条和摘要区复用。
    public void showAbnormalRecords(@NonNull List<AbnormalRecord> records) {
        showAbnormalRecordsDialog(records);
    }

    // 统一渲染底部弹层里的轻操作按钮，避免与主界面风格脱节。
    private void styleActionButton(@NonNull TextView button, @NonNull UiPaletteManager.Palette palette) {
        button.setBackground(UiPaletteManager.createOutlinedDrawable(activity, palette.control, palette.stroke));
        button.setTextColor(palette.textPrimary);
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
                .setPositiveButton(R.string.global_status_action_close, null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(dialogBinding.getRoot(), palette);
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
        if (dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(palette.primary);
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
        private final String syncText;
        private final String refreshedText;
        private final int abnormalCount;
        private final List<AbnormalRecord> abnormalRecords;

        // 构造当前全局状态快照，供按钮和底部弹层共享。
        public StatusSnapshot(@NonNull String stageText,
                              @NonNull String connectionText,
                              @NonNull String accountText,
                              @NonNull String syncText,
                              @NonNull String refreshedText,
                              int abnormalCount,
                              @NonNull List<AbnormalRecord> abnormalRecords) {
            this.stageText = stageText;
            this.connectionText = connectionText;
            this.accountText = accountText;
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
