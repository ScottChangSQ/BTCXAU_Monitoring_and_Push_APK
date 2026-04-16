/*
 * 图表页异常快看底部弹层控制器，负责承接风险提示条和异常摘要区的轻量查看。
 * 与全局异常列表弹窗协同工作。
 */
package com.binance.monitor.ui.chart;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.R;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.databinding.DialogChartAbnormalSheetBinding;
import com.binance.monitor.ui.host.GlobalStatusBottomSheetController;
import com.binance.monitor.ui.theme.UiPaletteManager;
import com.binance.monitor.util.FormatUtils;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

public final class ChartAbnormalBottomSheetController {

    private final AppCompatActivity activity;
    private final GlobalStatusBottomSheetController globalStatusBottomSheetController;

    // 创建图表页异常快看弹层控制器。
    public ChartAbnormalBottomSheetController(@NonNull AppCompatActivity activity,
                                             @NonNull GlobalStatusBottomSheetController globalStatusBottomSheetController) {
        this.activity = activity;
        this.globalStatusBottomSheetController = globalStatusBottomSheetController;
    }

    // 展示当前品种的异常快看弹层，先给用户一层摘要，再决定是否看完整列表。
    public void show(@NonNull String symbol, @NonNull List<AbnormalRecord> records) {
        View sheetView = activity.getLayoutInflater().inflate(R.layout.dialog_chart_abnormal_sheet, null, false);
        DialogChartAbnormalSheetBinding binding = DialogChartAbnormalSheetBinding.bind(sheetView);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setContentView(sheetView);
        UiPaletteManager.Palette palette = UiPaletteManager.resolve(activity);
        UiPaletteManager.applyPageTheme(binding.getRoot(), palette);
        binding.btnChartAbnormalViewAll.setBackground(
                UiPaletteManager.createOutlinedDrawable(activity, palette.control, palette.stroke)
        );
        binding.btnChartAbnormalViewAll.setTextColor(palette.textPrimary);
        if (records.isEmpty()) {
            binding.tvChartAbnormalQuickTitle.setText(activity.getString(R.string.chart_abnormal_quick_empty_title, symbol));
            binding.tvChartAbnormalQuickSubtitle.setText(R.string.chart_abnormal_quick_empty_subtitle);
            binding.tvChartAbnormalQuickDetails.setText(R.string.chart_abnormal_quick_empty_details);
            binding.btnChartAbnormalViewAll.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
            return;
        }
        AbnormalRecord latest = records.get(0);
        binding.tvChartAbnormalQuickTitle.setText(activity.getString(R.string.chart_abnormal_quick_title, symbol));
        binding.tvChartAbnormalQuickSubtitle.setText(activity.getString(
                R.string.chart_abnormal_quick_subtitle,
                records.size(),
                FormatUtils.formatTime(latest.getTimestamp())
        ));
        binding.tvChartAbnormalQuickDetails.setText(activity.getString(
                R.string.chart_abnormal_quick_details,
                safeText(latest.getTriggerSummary()),
                FormatUtils.formatPrice(latest.getOpenPrice()),
                FormatUtils.formatPrice(latest.getClosePrice()),
                FormatUtils.formatPercent(latest.getPercentChange()),
                FormatUtils.formatAmount(latest.getAmount())
        ));
        binding.btnChartAbnormalViewAll.setOnClickListener(v -> {
            dialog.dismiss();
            globalStatusBottomSheetController.showAbnormalRecords(records);
        });
        dialog.show();
    }

    // 统一清洗空文案，避免快看层出现空字符串断行。
    @NonNull
    private String safeText(String value) {
        return value == null || value.trim().isEmpty()
                ? activity.getString(R.string.chart_risk_banner_action)
                : value.trim();
    }
}
