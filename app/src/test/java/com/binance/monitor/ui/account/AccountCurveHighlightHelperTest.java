/*
 * 账户曲线共享高亮测试，确保附图长按会按当前横轴位置联动主图数据。
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

public class AccountCurveHighlightHelperTest {

    @Test
    public void resolveSharedHighlightShouldPreferCurrentXAxisRatio() {
        List<CurvePoint> curvePoints = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(2_000L, 110d, 108d, 0.30d),
                new CurvePoint(3_000L, 120d, 118d, 0.60d)
        );
        List<DrawdownPoint> drawdownPoints = Arrays.asList(
                new DrawdownPoint(1_000L, 0d),
                new DrawdownPoint(2_000L, -0.05d),
                new DrawdownPoint(3_000L, -0.12d)
        );
        List<DailyReturnPoint> dailyReturnPoints = Arrays.asList(
                new DailyReturnPoint(1_000L, 0.01d),
                new DailyReturnPoint(3_000L, 0.08d)
        );

        AccountCurveHighlightHelper.HighlightSnapshot snapshot =
                AccountCurveHighlightHelper.resolveSharedHighlight(
                        curvePoints,
                        drawdownPoints,
                        dailyReturnPoints,
                        1_000L,
                        1f
                );

        assertNotNull(snapshot);
        assertEquals(3_000L, snapshot.getTargetTimestamp());
        assertEquals(3_000L, snapshot.getCurvePoint().getTimestamp());
        assertEquals(3_000L, snapshot.getDrawdownPoint().getTimestamp());
        assertEquals(3_000L, snapshot.getDailyReturnPoint().getTimestamp());
        assertEquals(0.60d, snapshot.getCurvePoint().getPositionRatio(), 1e-9);
    }

    @Test
    public void resolveSharedHighlightShouldInterpolateCurveValuesForMidpoint() {
        List<CurvePoint> curvePoints = Arrays.asList(
                new CurvePoint(1_000L, 100d, 90d, 0.10d),
                new CurvePoint(3_000L, 140d, 110d, 0.50d)
        );
        List<DrawdownPoint> drawdownPoints = Arrays.asList(
                new DrawdownPoint(1_000L, -0.20d),
                new DrawdownPoint(3_000L, -0.04d)
        );
        List<DailyReturnPoint> dailyReturnPoints = Arrays.asList(
                new DailyReturnPoint(1_000L, -0.08d),
                new DailyReturnPoint(3_000L, 0.12d)
        );

        AccountCurveHighlightHelper.HighlightSnapshot snapshot =
                AccountCurveHighlightHelper.resolveSharedHighlight(
                        curvePoints,
                        drawdownPoints,
                        dailyReturnPoints,
                        1_000L,
                        0.5f
                );

        assertNotNull(snapshot);
        assertEquals(2_000L, snapshot.getTargetTimestamp());
        assertEquals(2_000L, snapshot.getCurvePoint().getTimestamp());
        assertEquals(120d, snapshot.getCurvePoint().getEquity(), 1e-9);
        assertEquals(100d, snapshot.getCurvePoint().getBalance(), 1e-9);
        assertEquals(0.30d, snapshot.getCurvePoint().getPositionRatio(), 1e-9);
        assertEquals(-0.12d, snapshot.getDrawdownPoint().getDrawdownRate(), 1e-9);
        assertEquals(0.02d, snapshot.getDailyReturnPoint().getReturnRate(), 1e-9);
    }

    @Test
    public void resolveSharedHighlightShouldFallbackToTimestampWhenRatioMissing() {
        List<CurvePoint> curvePoints = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0.10d),
                new CurvePoint(2_000L, 110d, 108d, 0.30d),
                new CurvePoint(3_000L, 120d, 118d, 0.60d)
        );

        AccountCurveHighlightHelper.HighlightSnapshot snapshot =
                AccountCurveHighlightHelper.resolveSharedHighlight(
                        curvePoints,
                        null,
                        null,
                        2_000L,
                        -1f
                );

        assertNotNull(snapshot);
        assertEquals(2_000L, snapshot.getTargetTimestamp());
        assertEquals(2_000L, snapshot.getCurvePoint().getTimestamp());
    }

    @Test
    public void resolveSharedHighlightShouldAllowExactTimestampToWinForSubCharts() {
        List<CurvePoint> curvePoints = Arrays.asList(
                new CurvePoint(1_000L, 100d, 90d, 0.10d),
                new CurvePoint(2_000L, 140d, 110d, 0.50d),
                new CurvePoint(3_000L, 180d, 130d, 0.90d)
        );

        AccountCurveHighlightHelper.HighlightSnapshot snapshot =
                AccountCurveHighlightHelper.resolveSharedHighlight(
                        curvePoints,
                        null,
                        null,
                        2_500L,
                        0.1f,
                        true
                );

        assertNotNull(snapshot);
        assertEquals(2_500L, snapshot.getTargetTimestamp());
        assertEquals(2_500L, snapshot.getCurvePoint().getTimestamp());
        assertEquals(160d, snapshot.getCurvePoint().getEquity(), 1e-9);
        assertEquals(120d, snapshot.getCurvePoint().getBalance(), 1e-9);
        assertEquals(0.70d, snapshot.getCurvePoint().getPositionRatio(), 1e-9);
    }
}
