/*
 * K 线悬浮按钮布局辅助，负责按主图与 VOL 图的实际坐标计算按钮落点。
 * 供 MarketChartActivity 统一放置“历史成交 开/关”“当前仓位 开/关”等图层按钮。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;

final class KlineOverlayButtonLayoutHelper {

    static final class Bounds {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Bounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        static Bounds empty() {
            return new Bounds(0, 0, 0, 0);
        }

        boolean isEmpty() {
            return right <= left || bottom <= top;
        }
    }

    static final class Position {
        final int left;
        final int top;

        Position(int left, int top) {
            this.left = left;
            this.top = top;
        }
    }

    private KlineOverlayButtonLayoutHelper() {
    }

    // 左下角图层开关统一按堆叠层级定位，最底部贴主图左下角，其余按钮依次向上排布。
    @NonNull
    static Position resolveBottomLeftStackedButtonPosition(Bounds priceBounds,
                                                           int buttonWidth,
                                                           int buttonHeight,
                                                           int insetPx,
                                                           int stackIndex,
                                                           int spacingPx) {
        Bounds anchor = priceBounds;
        if (anchor == null || anchor.isEmpty()) {
            return new Position(0, 0);
        }
        int left = Math.max(0, anchor.left + Math.max(0, insetPx));
        int verticalOffset = Math.max(0, stackIndex) * (Math.max(0, buttonHeight) + Math.max(0, spacingPx));
        int top = Math.max(0, anchor.bottom - Math.max(0, buttonHeight) - Math.max(0, insetPx) - verticalOffset);
        return new Position(left, top);
    }
}
