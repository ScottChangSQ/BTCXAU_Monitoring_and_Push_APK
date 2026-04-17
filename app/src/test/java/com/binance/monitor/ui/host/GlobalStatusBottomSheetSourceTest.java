package com.binance.monitor.ui.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GlobalStatusBottomSheetSourceTest {

    @Test
    public void marketChartShouldExposeStatusButtonAndBottomSheetController() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_market_chart.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String controller = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String strings = new String(Files.readAllBytes(
                Paths.get("src/main/res/values/strings.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String thresholdLayout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/dialog_abnormal_threshold_settings.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String abnormalAdapter = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/adapter/AbnormalRecordAdapter.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String paletteManager = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/btnGlobalStatus"));
        assertTrue(layout.contains("@+id/spinnerSymbolPicker"));
        assertTrue(layout.contains("@string/global_status_button_offline"));
        assertTrue(controller.contains("BottomSheetDialog"));
        assertTrue(controller.contains("dialog_global_status_sheet"));
        assertTrue(controller.contains("snapshot.getCompactAccountText()"));
        assertTrue(controller.contains("UiPaletteManager.applyBottomSheetSurface(dialog, palette);"));
        assertTrue(controller.contains("UiPaletteManager.applyAlertDialogSurface(dialog, palette);"));
        assertTrue(controller.contains("new Intent(activity, SettingsActivity.class)"));
        assertTrue(controller.contains("UiPaletteManager.styleSquareTextAction("));
        assertTrue(controller.contains("R.dimen.control_height_sm"));
        assertTrue(controller.contains("binding.btnGlobalStatusSettings"));
        assertTrue(controller.contains("binding.btnGlobalStatusLogs"));
        assertTrue(controller.contains("binding.btnGlobalStatusAbnormal"));
        assertTrue(controller.contains("palette.card"));
        assertTrue(controller.contains("binding.getRoot().post(() -> applyActionRowLayout(binding));"));
        assertTrue(controller.contains("applyActionRowLayout(@NonNull DialogGlobalStatusSheetBinding binding)"));
        assertTrue(controller.contains("params.weight = 1f;"));
        assertTrue(controller.contains(".setNeutralButton(R.string.global_status_action_threshold"));
        assertTrue(controller.contains("showAbnormalThresholdDialog();"));
        assertTrue(controller.contains("ConfigManager.getInstance(activity)"));
        assertTrue(controller.contains("MonitorServiceController.dispatch(activity, AppConstants.ACTION_REFRESH_CONFIG);"));
        assertTrue(controller.contains("dialogBinding.tvAbnormalDialogTitle.setTextColor(palette.textPrimary);"));
        assertTrue(controller.contains("dialogBinding.recyclerAbnormalRecords.setBackground("));
        assertTrue(controller.contains("dialogBinding.etAbnormalSearch.setTextColor(palette.textPrimary);"));
        assertTrue(thresholdLayout.contains("@+id/spinnerAbnormalThresholdSymbol"));
        assertTrue(thresholdLayout.contains("@+id/etAbnormalThresholdVolume"));
        assertTrue(thresholdLayout.contains("@+id/etAbnormalThresholdAmount"));
        assertTrue(thresholdLayout.contains("@+id/etAbnormalThresholdPrice"));
        assertTrue(paletteManager.contains("ViewCompat.setBackgroundTintList(button, null);"));
        assertTrue(abnormalAdapter.contains("UiPaletteManager.resolve(binding.getRoot().getContext())"));
        assertTrue(abnormalAdapter.contains("binding.getRoot().setCardBackgroundColor(palette.card);"));
        assertTrue(abnormalAdapter.contains("binding.tvSymbol.setTextColor(palette.primary);"));
        assertTrue(abnormalAdapter.contains("binding.tvSymbol.setTextColor(palette.xau);"));
        assertTrue(strings.contains("<string name=\"global_status_button_offline\">未连接 | 账户离线</string>"));
        assertTrue(strings.contains("<string name=\"global_status_button_compact\">%1$s | %2$s</string>"));
        assertTrue(strings.contains("<string name=\"global_status_action_threshold\">阈值设置</string>"));
        assertFalse(controller.contains("tvGlobalStatusSubtitle"));
        assertFalse(controller.contains("\"异常列表\".equals(label) ? 1.4f : 0.8f"));
        assertFalse(strings.contains("global_status_sheet_subtitle"));
        assertFalse(strings.contains("%3$d异常"));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("android:layout_width=\"0dp\""));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("android:layout_weight=\"1\""));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("android:layout_marginHorizontal=\"3dp\""));
    }

    private static String readUtf8(String relativePath) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(relativePath)
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
    }
}
