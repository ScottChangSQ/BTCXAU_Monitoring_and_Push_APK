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
        assertTrue(controller.contains("UiPaletteManager.styleActionButton("));
        assertTrue(controller.contains("UiPaletteManager.styleSelectFieldLabel("));
        assertTrue(controller.contains("UiPaletteManager.styleToggleChoice("));
        assertTrue(controller.contains("UiPaletteManager.styleInputField("));
        assertTrue(controller.contains("binding.btnGlobalStatusSettings"));
        assertTrue(controller.contains("binding.btnGlobalStatusLogs"));
        assertTrue(controller.contains("binding.btnGlobalStatusAbnormal"));
        assertTrue(controller.contains("binding.getRoot().post(() -> applyActionRowLayout(binding));"));
        assertTrue(controller.contains("applyActionRowLayout(@NonNull DialogGlobalStatusSheetBinding binding)"));
        assertTrue(controller.contains("SpacingTokenResolver.inlineGapPx(activity)"));
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
        assertTrue(thresholdLayout.contains("@+id/switchAbnormalThresholdVolume"));
        assertTrue(thresholdLayout.contains("@+id/switchAbnormalThresholdAmount"));
        assertTrue(thresholdLayout.contains("@+id/switchAbnormalThresholdPrice"));
        assertTrue(thresholdLayout.contains("@+id/radioAbnormalLogicGroup"));
        assertTrue(thresholdLayout.contains("@+id/radioAbnormalLogicOr"));
        assertTrue(thresholdLayout.contains("@+id/radioAbnormalLogicAnd"));
        assertTrue(thresholdLayout.contains("@style/Widget.BinanceMonitor.Subject.SelectField.Label"));
        assertTrue(thresholdLayout.contains("@style/Widget.BinanceMonitor.Subject.ToggleChoice"));
        assertTrue(thresholdLayout.contains("@style/Widget.BinanceMonitor.Subject.InputField"));
        assertTrue(controller.contains("binding.switchAbnormalThresholdVolume.setChecked(config.isVolumeEnabled())"));
        assertTrue(controller.contains("binding.switchAbnormalThresholdAmount.setChecked(config.isAmountEnabled())"));
        assertTrue(controller.contains("binding.switchAbnormalThresholdPrice.setChecked(config.isPriceChangeEnabled())"));
        assertTrue(controller.contains("dialogBinding.radioAbnormalLogicAnd.setChecked(configManager.isUseAndMode())"));
        assertTrue(controller.contains("dialogBinding.radioAbnormalLogicOr.setChecked(!configManager.isUseAndMode())"));
        assertTrue(controller.contains("current.setVolumeEnabled(binding.switchAbnormalThresholdVolume.isChecked())"));
        assertTrue(controller.contains("current.setAmountEnabled(binding.switchAbnormalThresholdAmount.isChecked())"));
        assertTrue(controller.contains("current.setPriceChangeEnabled(binding.switchAbnormalThresholdPrice.isChecked())"));
        assertTrue(controller.contains("configManager.setUseAndMode(binding.radioAbnormalLogicAnd.isChecked())"));
        assertTrue(paletteManager.contains("ViewCompat.setBackgroundTintList(button, null);"));
        assertTrue(abnormalAdapter.contains("UiPaletteManager.resolve(binding.getRoot().getContext())"));
        assertTrue(abnormalAdapter.contains("binding.getRoot().setCardBackgroundColor(palette.card);"));
        assertTrue(abnormalAdapter.contains("binding.tvSymbol.setTextColor(palette.primary);"));
        assertTrue(abnormalAdapter.contains("binding.tvSymbol.setTextColor(palette.xau);"));
        assertTrue(strings.contains("<string name=\"global_status_button_offline\">未连接 | 账户离线</string>"));
        assertTrue(strings.contains("<string name=\"global_status_button_compact\">%1$s | %2$s</string>"));
        assertTrue(strings.contains("<string name=\"global_status_action_threshold\">阈值设置</string>"));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("@style/Widget.BinanceMonitor.Subject.ActionButton.Secondary"));
        assertFalse(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("@style/Widget.BinanceMonitor.Subject.TextTrigger"));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("android:id=\"@+id/tvGlobalStatusConnectionValue\""));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.ValueCompact\""));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("@dimen/sheet_content_padding"));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("@dimen/row_gap"));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("@dimen/inline_gap"));
        assertTrue(readUtf8("src/main/res/layout/dialog_account_connection_sheet.xml").contains("@dimen/sheet_content_padding"));
        assertTrue(readUtf8("src/main/res/layout/dialog_account_connection_sheet.xml").contains("@dimen/row_gap"));
        assertTrue(readUtf8("src/main/res/layout/dialog_account_connection_sheet.xml").contains("@dimen/inline_gap"));
        assertFalse(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("TextAppearance.BinanceMonitor.TinyStrong"));
        assertFalse(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("@dimen/card_content_padding"));
        assertFalse(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("@dimen/global_status_sheet_row_gap"));
        assertFalse(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("@dimen/control_group_gap"));
        assertFalse(readUtf8("src/main/res/layout/dialog_account_connection_sheet.xml").contains("@dimen/card_content_padding"));
        assertFalse(readUtf8("src/main/res/layout/dialog_account_connection_sheet.xml").contains("@dimen/global_status_sheet_row_gap"));
        assertFalse(readUtf8("src/main/res/layout/dialog_account_connection_sheet.xml").contains("@dimen/control_group_gap"));
        assertFalse(controller.contains("tvGlobalStatusSubtitle"));
        assertFalse(controller.contains("UiPaletteManager.styleSquareTextAction("));
        assertFalse(controller.contains("\"异常列表\".equals(label) ? 1.4f : 0.8f"));
        assertFalse(controller.contains("styleTextTrigger(binding.btnGlobalStatusAbnormal, palette);"));
        assertFalse(controller.contains("R.dimen.control_group_gap"));
        assertFalse(strings.contains("global_status_sheet_subtitle"));
        assertFalse(strings.contains("%3$d异常"));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("android:layout_width=\"0dp\""));
        assertTrue(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("android:layout_weight=\"1\""));
        assertTrue(paletteManager.contains("SpacingTokenResolver.screenEdgePx(dialog.getContext())"));
        assertTrue(paletteManager.contains("bottomSheet.setLayoutParams(bottomSheetParams);"));
        assertFalse(readUtf8("src/main/res/layout/dialog_global_status_sheet.xml").contains("android:layout_marginHorizontal=\"3dp\""));
    }

    private static String readUtf8(String relativePath) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(relativePath)
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
    }
}
