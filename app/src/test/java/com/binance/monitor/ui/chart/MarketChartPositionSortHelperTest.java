package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.domain.account.model.PositionItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MarketChartPositionSortHelperTest {

    @Test
    public void openTimeDescShouldPutNewestPositionFirst() {
        List<PositionItem> sorted = MarketChartPositionSortHelper.sortPositions(samplePositions(),
                MarketChartPositionSortHelper.SortOption.OPEN_TIME_DESC);

        assertEquals("XAU", sorted.get(0).getProductName());
        assertEquals("BTC", sorted.get(1).getProductName());
        assertEquals("ETH", sorted.get(2).getProductName());
    }

    @Test
    public void productSortShouldUseProductNameAscending() {
        List<PositionItem> sorted = MarketChartPositionSortHelper.sortPositions(samplePositions(),
                MarketChartPositionSortHelper.SortOption.PRODUCT_ASC);

        assertEquals("BTC", sorted.get(0).getProductName());
        assertEquals("ETH", sorted.get(1).getProductName());
        assertEquals("XAU", sorted.get(2).getProductName());
    }

    @Test
    public void pnlAscShouldUseDisplayedPnlValue() {
        List<PositionItem> sorted = MarketChartPositionSortHelper.sortPositions(samplePositions(),
                MarketChartPositionSortHelper.SortOption.PNL_ASC);

        assertEquals("ETH", sorted.get(0).getProductName());
        assertEquals("XAU", sorted.get(1).getProductName());
        assertEquals("BTC", sorted.get(2).getProductName());
    }

    @Test
    public void pnlDescShouldUseDisplayedPnlValue() {
        List<PositionItem> sorted = MarketChartPositionSortHelper.sortPositions(samplePositions(),
                MarketChartPositionSortHelper.SortOption.PNL_DESC);

        assertEquals("BTC", sorted.get(0).getProductName());
        assertEquals("XAU", sorted.get(1).getProductName());
        assertEquals("ETH", sorted.get(2).getProductName());
    }

    private List<PositionItem> samplePositions() {
        List<PositionItem> list = new ArrayList<>();
        list.add(new PositionItem("BTC", "BTCUSDT", "buy", 1L, 0L, 2_000L,
                1d, 1d, 100d, 102d, 100d, 0d, 0d, 10d, 0d,
                0d, 0, 0d, 0d, 0d, -2d));
        list.add(new PositionItem("ETH", "ETHUSDT", "buy", 2L, 0L, 1_000L,
                1d, 1d, 100d, 98d, 100d, 0d, 0d, -3d, 0d,
                0d, 0, 0d, 0d, 0d, -1d));
        list.add(new PositionItem("XAU", "XAUUSD", "sell", 3L, 0L, 3_000L,
                1d, 1d, 100d, 108d, 100d, 0d, 0d, 6d, 0d,
                0d, 0, 0d, 0d, 0d, 1d));
        return list;
    }
}
