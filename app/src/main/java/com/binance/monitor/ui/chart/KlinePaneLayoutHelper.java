/*
 * K 线主图与附图的布局辅助工具，负责统一计算各子图高度和连续边界。
 * KlineChartView 通过这里保证 VOL 增高且各子图之间无额外空隙。
 */
package com.binance.monitor.ui.chart;

final class KlinePaneLayoutHelper {
    private static final float PRICE_WEIGHT = 8.76f;
    private static final float VOLUME_WEIGHT = 2.4f;
    private static final float MACD_WEIGHT = 2.3f;
    private static final float STOCH_WEIGHT = 2.3f;
    private static final float RSI_WEIGHT = 2f;
    private static final float KDJ_WEIGHT = 2f;

    private KlinePaneLayoutHelper() {
    }

    // 按当前可见子图计算连续布局，所有子图之间保持零间距。
    static PaneLayout compute(float top,
                              float bottom,
                              boolean showVolume,
                              boolean showMacd,
                              boolean showStochRsi,
                              boolean showRsi,
                              boolean showKdj) {
        float totalWeight = PRICE_WEIGHT
                + (showVolume ? VOLUME_WEIGHT : 0f)
                + (showMacd ? MACD_WEIGHT : 0f)
                + (showStochRsi ? STOCH_WEIGHT : 0f)
                + (showRsi ? RSI_WEIGHT : 0f)
                + (showKdj ? KDJ_WEIGHT : 0f);
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

        PaneBounds stoch = showStochRsi
                ? new PaneBounds(cursor, cursor + STOCH_WEIGHT * unit)
                : PaneBounds.empty(cursor);
        cursor = stoch.bottom;

        PaneBounds rsi = showRsi
                ? new PaneBounds(cursor, cursor + RSI_WEIGHT * unit)
                : PaneBounds.empty(cursor);
        cursor = rsi.bottom;

        PaneBounds kdj = showKdj
                ? new PaneBounds(cursor, bottom)
                : PaneBounds.empty(cursor);

        return new PaneLayout(price, volume, macd, stoch, rsi, kdj);
    }

    static final class PaneLayout {
        final PaneBounds price;
        final PaneBounds volume;
        final PaneBounds macd;
        final PaneBounds stoch;
        final PaneBounds rsi;
        final PaneBounds kdj;

        private PaneLayout(PaneBounds price,
                           PaneBounds volume,
                           PaneBounds macd,
                           PaneBounds stoch,
                           PaneBounds rsi,
                           PaneBounds kdj) {
            this.price = price;
            this.volume = volume;
            this.macd = macd;
            this.stoch = stoch;
            this.rsi = rsi;
            this.kdj = kdj;
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
