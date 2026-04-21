/*
 * 账户曲线布局辅助测试，确保四张图共用同一横向边界，主图线宽也保持收窄后的固定值。
 */
package com.binance.monitor.ui.account;

import com.binance.monitor.R;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CurvePaneLayoutHelperTest {

    @Test
    public void shouldKeepUnifiedHorizontalInsets() {
        assertEquals(R.dimen.curve_chart_left_inset, CurvePaneLayoutHelper.resolveChartLeftInsetRes());
        assertEquals(R.dimen.curve_chart_right_inset, CurvePaneLayoutHelper.resolveChartRightInsetRes());
    }

    @Test
    public void shouldUseSlimmerMainCurveStrokeWidths() {
        assertEquals(R.dimen.curve_equity_stroke, CurvePaneLayoutHelper.resolveEquityStrokeRes());
        assertEquals(R.dimen.curve_balance_stroke, CurvePaneLayoutHelper.resolveBalanceStrokeRes());
    }
}
