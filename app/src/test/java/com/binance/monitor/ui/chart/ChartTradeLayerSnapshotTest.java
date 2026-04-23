package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class ChartTradeLayerSnapshotTest {

    @Test
    public void snapshotShouldSeparateLiveLinesAndDraftLines() {
        ChartTradeLine liveLine = new ChartTradeLine("pos-1", 65000d, "持仓", ChartTradeLineState.LIVE_POSITION);
        ChartTradeLine draftLine = new ChartTradeLine("draft-1", 65100d, "草稿挂单", ChartTradeLineState.DRAFT_PENDING);

        ChartTradeLayerSnapshot snapshot = new ChartTradeLayerSnapshot(
                Arrays.asList(liveLine),
                Arrays.asList(draftLine)
        );

        assertEquals(1, snapshot.getLiveLines().size());
        assertEquals(1, snapshot.getDraftLines().size());
        assertEquals(ChartTradeLineState.DRAFT_PENDING, snapshot.getDraftLines().get(0).getState());
    }

    @Test
    public void tradeLineShouldExposeCenterLabelAndTone() {
        ChartTradeLine line = new ChartTradeLine(
                "pos-2",
                "pos-2",
                65123d,
                "买 0.10手 +$12.30",
                "$65123.00",
                ChartTradeLineState.LIVE_POSITION,
                ChartTradeLineTone.POSITIVE
        );

        assertEquals("买 0.10手 +$12.30", line.getLabel());
        assertEquals("$65123.00", line.getCenterLabel());
        assertEquals(ChartTradeLineTone.POSITIVE, line.getTone());
    }
}
