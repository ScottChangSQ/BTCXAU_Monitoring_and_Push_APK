/*
 * 账户曲线布局辅助测试，确保四张图共用同一横向边界，主图线宽也保持收窄后的固定值。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CurvePaneLayoutHelperTest {

    @Test
    public void shouldKeepUnifiedHorizontalInsets() {
        assertEquals(34f, CurvePaneLayoutHelper.resolveChartLeftDp(), 1e-6f);
        assertEquals(28f, CurvePaneLayoutHelper.resolveChartRightInsetDp(), 1e-6f);
    }

    @Test
    public void shouldUseSlimmerMainCurveStrokeWidths() {
        assertEquals(1.6f, CurvePaneLayoutHelper.resolveEquityStrokeDp(), 1e-6f);
        assertEquals(1.3f, CurvePaneLayoutHelper.resolveBalanceStrokeDp(), 1e-6f);
    }
}
