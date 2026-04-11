package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KlineOverlayButtonLayoutHelperTest {

    @Test
    public void resolveBottomLeftStackedButtonPositionShouldPinHistoryTradeToPricePaneBottomLeft() {
        KlineOverlayButtonLayoutHelper.Bounds priceBounds =
                new KlineOverlayButtonLayoutHelper.Bounds(20, 10, 220, 150);

        KlineOverlayButtonLayoutHelper.Position position =
                KlineOverlayButtonLayoutHelper.resolveBottomLeftStackedButtonPosition(
                        priceBounds,
                        60,
                        20,
                        2,
                        0,
                        4
                );

        assertEquals(22, position.left);
        assertEquals(128, position.top);
    }

    @Test
    public void resolveBottomLeftStackedButtonPositionShouldPlacePositionToggleAboveHistoryTrade() {
        KlineOverlayButtonLayoutHelper.Bounds priceBounds =
                new KlineOverlayButtonLayoutHelper.Bounds(20, 10, 220, 150);

        KlineOverlayButtonLayoutHelper.Position position =
                KlineOverlayButtonLayoutHelper.resolveBottomLeftStackedButtonPosition(
                        priceBounds,
                        60,
                        20,
                        2,
                        1,
                        4
                );

        assertEquals(22, position.left);
        assertEquals(104, position.top);
    }
}
