/*
 * K 线主图与附图的布局辅助工具，负责统一计算各子图高度和连续边界。
 * KlineChartView 通过这里保证 VOL 增高且各子图之间无额外空隙。
 */
package com.binance.monitor.ui.chart;

final class KlinePaneLayoutHelper {
    private static final float PRICE_WEIGHT = 9.36f;
    private static final float VOLUME_WEIGHT = 2.4f;
    private static final float MACD_WEIGHT = 2f;
    private static final float OSCILLATOR_WEIGHT = 2f;

    private KlinePaneLayoutHelper() {
    }

    // 按当前可见子图计算连续布局，所有子图之间保持零间距。
    static PaneLayout compute(float top,
                              float bottom,
                              boolean showVolume,
                              boolean showMacd,
                              boolean showOscillator) {
        float totalWeight = PRICE_WEIGHT
                + (showVolume ? VOLUME_WEIGHT : 0f)
                + (showMacd ? MACD_WEIGHT : 0f)
                + (showOscillator ? OSCILLATOR_WEIGHT : 0f);
        float height = Math.max(0f, bottom - top);
        float unit = totalWeight <= 0f ? 0f : height / totalWeight;

        float cursor = top;
        PaneBounds price = new PaneBounds(cursor, cursor + PRICE_WEIGHT * unit);
        cursor = price.bottom;

        PaneBounds volume = showVolume
                ? new PaneBounds(cursor, cursor + VOLUME_WEIGHT * unit)
                : PaneBounds.empty(cursor);
        cursor = volume.bottom;

        PaneBounds macd = showMacd
                ? new PaneBounds(cursor, cursor + MACD_WEIGHT * unit)
                : PaneBounds.empty(cursor);
        cursor = macd.bottom;

        PaneBounds oscillator = showOscillator
                ? new PaneBounds(cursor, bottom)
                : PaneBounds.empty(cursor);

        return new PaneLayout(price, volume, macd, oscillator);
    }

    static final class PaneLayout {
        final PaneBounds price;
        final PaneBounds volume;
        final PaneBounds macd;
        final PaneBounds oscillator;

        private PaneLayout(PaneBounds price, PaneBounds volume, PaneBounds macd, PaneBounds oscillator) {
            this.price = price;
            this.volume = volume;
            this.macd = macd;
            this.oscillator = oscillator;
        }
    }

    static final class PaneBounds {
        final float top;
        final float bottom;

        private PaneBounds(float top, float bottom) {
            this.top = top;
            this.bottom = bottom;
        }

        static PaneBounds empty(float anchor) {
            return new PaneBounds(anchor, anchor);
        }

        float height() {
            return Math.max(0f, bottom - top);
        }
    }
}
