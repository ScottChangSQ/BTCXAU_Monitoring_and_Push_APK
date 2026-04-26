package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.domain.account.model.CurvePoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CurvePointBinarySearchTest {

    @Test
    public void nearestCurvePointIndexShouldReturnClosestTimestamp() {
        List<CurvePoint> points = Arrays.asList(
                point(1000L),
                point(2000L),
                point(4000L)
        );

        assertEquals(0, CurvePointBinarySearch.nearestCurvePointIndex(points, 1200L));
        assertEquals(1, CurvePointBinarySearch.nearestCurvePointIndex(points, 2600L));
        assertEquals(2, CurvePointBinarySearch.nearestCurvePointIndex(points, 3900L));
        assertEquals(0, CurvePointBinarySearch.nearestCurvePointIndex(points, 100L));
        assertEquals(2, CurvePointBinarySearch.nearestCurvePointIndex(points, 8000L));
    }

    @Test
    public void nearestCurvePointIndexShouldHandleEmptyInput() {
        assertEquals(-1, CurvePointBinarySearch.nearestCurvePointIndex(Collections.emptyList(), 1000L));
    }

    @Test
    public void nearestDrawdownAndDailyReturnPointIndexShouldReuseTimestampOrdering() {
        List<CurveAnalyticsHelper.DrawdownPoint> drawdownPoints = Arrays.asList(
                new CurveAnalyticsHelper.DrawdownPoint(1000L, 0d),
                new CurveAnalyticsHelper.DrawdownPoint(3000L, -0.1d)
        );
        List<CurveAnalyticsHelper.DailyReturnPoint> dailyReturnPoints = Arrays.asList(
                new CurveAnalyticsHelper.DailyReturnPoint(1000L, 0.01d),
                new CurveAnalyticsHelper.DailyReturnPoint(3000L, -0.02d)
        );

        assertEquals(1, CurvePointBinarySearch.nearestDrawdownPointIndex(drawdownPoints, 2600L));
        assertEquals(1, CurvePointBinarySearch.nearestDailyReturnPointIndex(dailyReturnPoints, 2600L));
    }

    private static CurvePoint point(long timestamp) {
        return new CurvePoint(timestamp, 100d, 100d, 0d);
    }
}
