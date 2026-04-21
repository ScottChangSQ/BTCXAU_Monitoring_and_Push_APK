/*
 * 图表交易状态层快照，集中承载真实线与草稿线。
 * 与 ChartTradeLine、KlineChartView、MarketChartScreen 协同工作。
 */
package com.binance.monitor.ui.chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ChartTradeLayerSnapshot {
    private final List<ChartTradeLine> liveLines;
    private final List<ChartTradeLine> draftLines;

    ChartTradeLayerSnapshot(List<ChartTradeLine> liveLines, List<ChartTradeLine> draftLines) {
        this.liveLines = liveLines == null ? new ArrayList<>() : new ArrayList<>(liveLines);
        this.draftLines = draftLines == null ? new ArrayList<>() : new ArrayList<>(draftLines);
    }

    List<ChartTradeLine> getLiveLines() {
        return Collections.unmodifiableList(liveLines);
    }

    List<ChartTradeLine> getDraftLines() {
        return Collections.unmodifiableList(draftLines);
    }
}
