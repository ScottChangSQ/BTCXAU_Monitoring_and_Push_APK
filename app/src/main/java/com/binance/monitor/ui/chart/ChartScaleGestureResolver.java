/*
 * K 线缩放手势方向判定工具，统一横向、纵向和斜向缩放的分支规则。
 */
package com.binance.monitor.ui.chart;

public final class ChartScaleGestureResolver {

    public enum Mode {
        HORIZONTAL,
        VERTICAL,
        DIAGONAL
    }

    private static final float DOMINANCE_RATIO = 1.12f;

    private ChartScaleGestureResolver() {
    }

    // 根据横纵跨度变化量判断本次缩放更接近哪一类手势。
    public static Mode resolveMode(float absXDelta, float absYDelta) {
        if (absYDelta > absXDelta * DOMINANCE_RATIO) {
            return Mode.VERTICAL;
        }
        if (absXDelta > absYDelta * DOMINANCE_RATIO) {
            return Mode.HORIZONTAL;
        }
        return Mode.DIAGONAL;
    }
}
