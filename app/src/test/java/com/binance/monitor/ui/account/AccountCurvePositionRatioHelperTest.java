/*
 * 仓位比例曲线兜底测试，确保历史仓位缺失时按保证金 / 净资产口径补出可显示曲线。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AccountCurvePositionRatioHelperTest {

    @Test
    public void ensureVisibleRatiosShouldEstimateWhenAllHistoricalRatiosMissing() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 95d, 0d),
                new CurvePoint(2_000L, 200d, 190d, 0d)
        );
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTC", "BTC", "BUY", 1L, 1L,
                        1d, 1d, 100d, 120d, 50d, 0.2d,
                        0d, 0d, 0d, 0d, 0, 0d, 0d, 0d, 0d)
        );

        List<CurvePoint> resolved = AccountCurvePositionRatioHelper.ensureVisibleRatios(points, positions, null, 10d);

        assertEquals(2, resolved.size());
        assertTrue(resolved.get(0).getPositionRatio() > 0d);
        assertTrue(resolved.get(1).getPositionRatio() > 0d);
        assertEquals(0.05d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(0.025d, resolved.get(1).getPositionRatio(), 1e-9);
    }

    @Test
    public void ensureVisibleRatiosShouldKeepExistingHistoricalRatios() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 95d, 0.15d),
                new CurvePoint(2_000L, 200d, 190d, 0.30d)
        );

        List<CurvePoint> resolved = AccountCurvePositionRatioHelper.ensureVisibleRatios(points, null, null, 0d);

        assertEquals(0.15d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(0.30d, resolved.get(1).getPositionRatio(), 1e-9);
    }

    @Test
    public void ensureVisibleRatiosShouldReplayClosedTradesWhenLivePositionsMissing() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 100d, 0d),
                new CurvePoint(2_000L, 100d, 100d, 0d),
                new CurvePoint(3_000L, 100d, 100d, 0d)
        );
        List<TradeRecordItem> trades = Collections.singletonList(new TradeRecordItem(
                2_500L,
                "BTC",
                "BTCUSDT",
                "BUY",
                40d,
                1d,
                40d,
                0d,
                "",
                0d,
                1_500L,
                2_500L,
                0d,
                40d,
                42d,
                1L,
                1L,
                1L,
                1
        ));

        List<CurvePoint> resolved = AccountCurvePositionRatioHelper.ensureVisibleRatios(points, null, trades, 10d);

        assertEquals(3, resolved.size());
        assertEquals(0d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(0.04d, resolved.get(1).getPositionRatio(), 1e-9);
        assertEquals(0d, resolved.get(2).getPositionRatio(), 1e-9);
    }
}
