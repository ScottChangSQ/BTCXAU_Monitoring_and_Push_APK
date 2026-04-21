package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;

public class ChartOverlayRefreshDiffTest {

    @Test
    public void summaryOnlyChangeShouldNotRequireOverlayRebuild() {
        ChartOverlaySnapshot previous = new ChartOverlaySnapshot(
                Collections.singletonList(createAnnotation("BUY", 100d)),
                Collections.emptyList(),
                Collections.emptyList(),
                new KlineChartView.AggregateCostAnnotation(100d, "100"),
                "盈亏：10 | 持仓：1手",
                "meta",
                "sig-1"
        );
        ChartOverlaySnapshot current = new ChartOverlaySnapshot(
                Collections.singletonList(createAnnotation("BUY", 100d)),
                Collections.emptyList(),
                Collections.emptyList(),
                new KlineChartView.AggregateCostAnnotation(100d, "100"),
                "盈亏：12 | 持仓：1手",
                "meta",
                "sig-2"
        );

        assertFalse(ChartOverlayRefreshDiff.hasOverlayVisualChange(previous, current));
    }

    @Test
    public void annotationChangeShouldRequireOverlayRebuild() {
        ChartOverlaySnapshot previous = new ChartOverlaySnapshot(
                Collections.singletonList(createAnnotation("BUY", 100d)),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                "盈亏：10 | 持仓：1手",
                "meta",
                "sig-1"
        );
        ChartOverlaySnapshot current = new ChartOverlaySnapshot(
                Collections.singletonList(createAnnotation("BUY", 101d)),
                Collections.emptyList(),
                Collections.emptyList(),
                null,
                "盈亏：10 | 持仓：1手",
                "meta",
                "sig-2"
        );

        assertTrue(ChartOverlayRefreshDiff.hasOverlayVisualChange(previous, current));
    }

    @Test
    public void aggregateCostChangeShouldRequireOverlayRebuild() {
        ChartOverlaySnapshot previous = new ChartOverlaySnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                new KlineChartView.AggregateCostAnnotation(100d, "100"),
                "盈亏：10 | 持仓：1手",
                "meta",
                "sig-1"
        );
        ChartOverlaySnapshot current = new ChartOverlaySnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                new KlineChartView.AggregateCostAnnotation(101d, "101"),
                "盈亏：10 | 持仓：1手",
                "meta",
                "sig-2"
        );

        assertTrue(ChartOverlayRefreshDiff.hasOverlayVisualChange(previous, current));
    }

    @Test
    public void tradeLayerSnapshotChangeShouldRequireOverlayRebuild() {
        ChartOverlaySnapshot previous = new ChartOverlaySnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                new ChartTradeLayerSnapshot(
                        Collections.singletonList(new ChartTradeLine("pos-1", 100d, "持仓", ChartTradeLineState.LIVE_POSITION)),
                        Collections.emptyList()
                ),
                null,
                "盈亏：10 | 持仓：1手",
                "meta",
                "sig-1"
        );
        ChartOverlaySnapshot current = new ChartOverlaySnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                new ChartTradeLayerSnapshot(
                        Collections.singletonList(new ChartTradeLine("pos-1", 101d, "持仓", ChartTradeLineState.LIVE_POSITION)),
                        Collections.emptyList()
                ),
                null,
                "盈亏：10 | 持仓：1手",
                "meta",
                "sig-2"
        );

        assertTrue(ChartOverlayRefreshDiff.hasOverlayVisualChange(previous, current));
    }

    private static KlineChartView.PriceAnnotation createAnnotation(String label, double price) {
        return new KlineChartView.PriceAnnotation(
                1710000000000L,
                price,
                label,
                0xFF00FF00,
                "group",
                1,
                0f,
                0L,
                Double.NaN,
                KlineChartView.ANNOTATION_KIND_DEFAULT,
                new String[]{"detail"}
        );
    }
}
