/*
 * 账户统计日期选择值辅助，负责把年月选项转换成稳定的中文标签和索引映射。
 * 供 AccountStatsBridgeActivity 的 NumberPicker 使用，避免月份列显示不稳定。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AccountDatePickerValueHelper {

    private AccountDatePickerValueHelper() {
    }

    // 把年份列表转成带“年”后缀的标签。
    static String[] buildYearLabels(List<Integer> years) {
        List<String> labels = new ArrayList<>();
        if (years != null) {
            for (Integer year : years) {
                if (year == null) {
                    continue;
                }
                labels.add(String.format(Locale.getDefault(), "%d年", year));
            }
        }
        return labels.toArray(new String[0]);
    }

    // 根据可用月份生成 NumberPicker 需要的标签和索引映射。
    static MonthState buildMonthState(@Nullable boolean[] months, int preferredMonth) {
        List<Integer> visibleMonths = new ArrayList<>();
        if (months != null) {
            for (int month = 1; month <= 12; month++) {
                if (months[month]) {
                    visibleMonths.add(month);
                }
            }
        }
        if (visibleMonths.isEmpty()) {
            for (int month = 1; month <= 12; month++) {
                visibleMonths.add(month);
            }
        }
        int selectedMonth = visibleMonths.get(0);
        if (visibleMonths.contains(preferredMonth)) {
            selectedMonth = preferredMonth;
        }
        String[] labels = new String[visibleMonths.size()];
        for (int index = 0; index < visibleMonths.size(); index++) {
            labels[index] = String.format(Locale.getDefault(), "%d月", visibleMonths.get(index));
        }
        return new MonthState(visibleMonths, labels, visibleMonths.indexOf(selectedMonth));
    }

    static final class MonthState {
        private final List<Integer> months;
        private final String[] labels;
        private final int selectedIndex;

        MonthState(List<Integer> months, String[] labels, int selectedIndex) {
            this.months = new ArrayList<>(months);
            this.labels = labels.clone();
            this.selectedIndex = Math.max(0, selectedIndex);
        }

        List<Integer> getMonths() {
            return new ArrayList<>(months);
        }

        String[] getLabels() {
            return labels.clone();
        }

        int getSelectedIndex() {
            return selectedIndex;
        }

        int getSelectedMonth() {
            if (months.isEmpty()) {
                return 1;
            }
            int index = Math.max(0, Math.min(months.size() - 1, selectedIndex));
            return months.get(index);
        }
    }
}
