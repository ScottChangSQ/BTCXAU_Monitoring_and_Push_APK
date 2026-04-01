/*
 * K 线缩放手势判定测试，确保横向、纵向和斜向缩放的分支稳定。
 */
package com.binance.monitor.ui.chart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChartScaleGestureResolverTest {

    @Test
    public void resolveMode_returnsVertical_whenVerticalDeltaDominates() {
        assertEquals(
                ChartScaleGestureResolver.Mode.VERTICAL,
                ChartScaleGestureResolver.resolveMode(10f, 18f)
        );
    }

    @Test
    public void resolveMode_returnsHorizontal_whenHorizontalDeltaDominates() {
        assertEquals(
                ChartScaleGestureResolver.Mode.HORIZONTAL,
                ChartScaleGestureResolver.resolveMode(18f, 10f)
        );
    }

    @Test
    public void resolveMode_returnsDiagonal_whenTwoDirectionsAreClose() {
        assertEquals(
                ChartScaleGestureResolver.Mode.DIAGONAL,
                ChartScaleGestureResolver.resolveMode(12f, 11f)
        );
    }
}
