/*
 * 日期选择器值映射测试，确保年月选择列能稳定显示中文标签并保留正确索引。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class AccountDatePickerValueHelperTest {

    @Test
    public void buildYearLabelsShouldAppendChineseSuffix() {
        String[] labels = AccountDatePickerValueHelper.buildYearLabels(Arrays.asList(2024, 2025, 2026));

        assertArrayEquals(new String[]{"2024年", "2025年", "2026年"}, labels);
    }

    @Test
    public void buildMonthStateShouldReturnVisibleMonthLabelsAndSelectedIndex() {
        boolean[] months = new boolean[13];
        months[2] = true;
        months[4] = true;
        months[7] = true;

        AccountDatePickerValueHelper.MonthState state =
                AccountDatePickerValueHelper.buildMonthState(months, 4);

        assertArrayEquals(new String[]{"2月", "4月", "7月"}, state.getLabels());
        assertEquals(1, state.getSelectedIndex());
        assertEquals(4, state.getSelectedMonth());
    }
}
