/*
 * K 线子图布局辅助测试，确保各子图连续拼接且 VOL 高度按要求放大。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KlinePaneLayoutHelperTest {

    @Test
    public void computeShouldKeepPanesTouchingWithoutGap() {
        KlinePaneLayoutHelper.PaneLayout layout = KlinePaneLayoutHelper.compute(
                10f,
                210f,
                true,
                true,
                true
        );

        assertEquals(layout.price.bottom, layout.volume.top, 1e-6f);
        assertEquals(layout.volume.bottom, layout.macd.top, 1e-6f);
        assertEquals(layout.macd.bottom, layout.oscillator.top, 1e-6f);
        assertEquals(210f, layout.oscillator.bottom, 1e-6f);
    }

    @Test
    public void computeShouldIncreaseVolumePaneHeightByTwentyPercentAgainstMacd() {
        KlinePaneLayoutHelper.PaneLayout layout = KlinePaneLayoutHelper.compute(
                0f,
                200f,
                true,
                true,
                true
        );

        float volumeHeight = layout.volume.height();
        float macdHeight = layout.macd.height();

        assertTrue(volumeHeight > macdHeight);
        assertEquals(1.2f, volumeHeight / macdHeight, 0.0001f);
    }

    @Test
    public void computeShouldSkipHiddenPanesAndKeepRemainingContinuous() {
        KlinePaneLayoutHelper.PaneLayout layout = KlinePaneLayoutHelper.compute(
                5f,
                105f,
                false,
                false,
                true
        );

        assertEquals(0f, layout.volume.height(), 0.0001f);
        assertEquals(0f, layout.macd.height(), 0.0001f);
        assertEquals(layout.price.bottom, layout.oscillator.top, 1e-6f);
    }
}
