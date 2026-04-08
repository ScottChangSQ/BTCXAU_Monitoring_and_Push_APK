/*
 * 曲线采样点辅助测试，确保长按只吸附真实点，不再插值生成不存在的数据。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.binance.monitor.ui.account.CurveAnalyticsHelper.DailyReturnPoint;
import com.binance.monitor.ui.account.CurveAnalyticsHelper.DrawdownPoint;
import com.binance.monitor.ui.account.model.CurvePoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CurveSeriesInterpolationHelperTest {

    @Test
    public void interpolateCurvePoint_returnsMidpointValues() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 90d, 0.10d),
                new CurvePoint(3_000L, 140d, 110d, 0.50d)
        );

        CurvePoint point = CurveSeriesInterpolationHelper.interpolateCurvePoint(points, 2_000L);

        assertNotNull(point);
        assertEquals(1_000L, point.getTimestamp());
        assertEquals(100d, point.getEquity(), 1e-9);
        assertEquals(90d, point.getBalance(), 1e-9);
        assertEquals(0.10d, point.getPositionRatio(), 1e-9);
    }

    @Test
    public void interpolateDrawdownPoint_returnsMidpointValues() {
        List<DrawdownPoint> points = Arrays.asList(
                new DrawdownPoint(1_000L, -0.20d),
                new DrawdownPoint(3_000L, -0.04d)
        );

        DrawdownPoint point = CurveSeriesInterpolationHelper.interpolateDrawdownPoint(points, 2_000L);

        assertNotNull(point);
        assertEquals(1_000L, point.getTimestamp());
        assertEquals(-0.20d, point.getDrawdownRate(), 1e-9);
    }

    @Test
    public void interpolateDailyReturnPoint_returnsMidpointValues() {
        List<DailyReturnPoint> points = Arrays.asList(
                new DailyReturnPoint(1_000L, -0.08d),
                new DailyReturnPoint(3_000L, 0.12d)
        );

        DailyReturnPoint point = CurveSeriesInterpolationHelper.interpolateDailyReturnPoint(points, 2_000L);

        assertNotNull(point);
        assertEquals(1_000L, point.getTimestamp());
        assertEquals(-0.08d, point.getReturnRate(), 1e-9);
    }
}
