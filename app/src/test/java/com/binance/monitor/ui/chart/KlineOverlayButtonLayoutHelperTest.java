package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KlineOverlayButtonLayoutHelperTest {

    @Test
    public void resolveHistoryTradeButtonPositionShouldPinToPricePaneBottomLeftEvenWhenVolumePaneExists() {
        KlineOverlayButtonLayoutHelper.Bounds priceBounds =
                new KlineOverlayButtonLayoutHelper.Bounds(20, 10, 220, 150);
        KlineOverlayButtonLayoutHelper.Bounds volumeBounds =
                new KlineOverlayButtonLayoutHelper.Bounds(20, 150, 220, 220);

        KlineOverlayButtonLayoutHelper.Position position =
                KlineOverlayButtonLayoutHelper.resolveHistoryTradeButtonPosition(
                        priceBounds,
                        volumeBounds,
                        60,
                        20,
                        2
                );

        assertEquals(22, position.left);
        assertEquals(128, position.top);
    }

    @Test
    public void resolveHistoryTradeButtonPositionShouldFallbackToPricePaneWhenVolumeHidden() {
        KlineOverlayButtonLayoutHelper.Bounds priceBounds =
                new KlineOverlayButtonLayoutHelper.Bounds(20, 10, 220, 150);

        KlineOverlayButtonLayoutHelper.Position position =
                KlineOverlayButtonLayoutHelper.resolveHistoryTradeButtonPosition(
                        priceBounds,
                        KlineOverlayButtonLayoutHelper.Bounds.empty(),
                        60,
                        20,
                        2
                );

        assertEquals(22, position.left);
        assertEquals(128, position.top);
    }
}
