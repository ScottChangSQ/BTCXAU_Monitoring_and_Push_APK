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
        assertEquals(5d / 95d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(5d / 190d, resolved.get(1).getPositionRatio(), 1e-9);
    }

    @Test
    public void ensureVisibleRatiosShouldKeepExistingHistoricalRatios() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 95d, 0.80d),
                new CurvePoint(2_000L, 200d, 190d, 0.70d)
        );
        List<PositionItem> positions = Arrays.asList(
                new PositionItem("BTC", "BTC", "BUY", 1L, 1L,
                        1d, 1d, 100d, 120d, 50d, 0.2d,
                        0d, 0d, 0d, 0d, 0, 0d, 0d, 0d, 0d)
        );

        List<CurvePoint> resolved = AccountCurvePositionRatioHelper.ensureVisibleRatios(points, positions, null, 10d);

        assertEquals(5d / 95d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(5d / 190d, resolved.get(1).getPositionRatio(), 1e-9);
    }

    @Test
    public void ensureVisibleRatiosShouldReplayLifecycleTradesInsteadOfKeepingServerRatios() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 95d, 0.12d),
                new CurvePoint(2_000L, 200d, 190d, 0.10d),
                new CurvePoint(3_000L, 200d, 190d, 0.08d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        2_000L,
                        "BTC",
                        "BTCUSDT",
                        "BUY",
                        40d,
                        1d,
                        40d,
                        0d,
                        "",
                        10d,
                        1_000L,
                        2_000L,
                        0d,
                        40d,
                        50d,
                        2L,
                        1L,
                        1L,
                        1
                )
        );

        List<CurvePoint> resolved = AccountCurvePositionRatioHelper.ensureVisibleRatios(
                points,
                Collections.emptyList(),
                trades,
                10d
        );

        assertEquals(4d / 95d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(0d, resolved.get(1).getPositionRatio(), 1e-9);
        assertEquals(0d, resolved.get(2).getPositionRatio(), 1e-9);
    }

    @Test
    public void ensureVisibleRatiosShouldZeroServerRatiosWhenNoLocalExposureExists() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 95d, 0.12d),
                new CurvePoint(2_000L, 200d, 190d, 0.10d)
        );

        List<CurvePoint> resolved = AccountCurvePositionRatioHelper.ensureVisibleRatios(
                points,
                Collections.emptyList(),
                Collections.emptyList(),
                10d
        );

        assertEquals(0d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(0d, resolved.get(1).getPositionRatio(), 1e-9);
    }

    @Test
    public void ensureVisibleRatiosShouldReplayExposureFromOpenAndCloseDeals() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 80d, 0d),
                new CurvePoint(2_000L, 100d, 80d, 0d),
                new CurvePoint(3_000L, 100d, 80d, 0d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        1_500L,
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
                        1_500L,
                        0d,
                        40d,
                        40d,
                        1L,
                        1L,
                        1L,
                        0
                ),
                new TradeRecordItem(
                        2_500L,
                        "BTC",
                        "BTCUSDT",
                        "BUY",
                        42d,
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
                        2L,
                        1L,
                        1L,
                        1
                )
        );

        List<CurvePoint> resolved = AccountCurvePositionRatioHelper.ensureVisibleRatios(points, null, trades, 10d);

        assertEquals(3, resolved.size());
        assertEquals(0d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(0.05d, resolved.get(1).getPositionRatio(), 1e-9);
        assertEquals(0d, resolved.get(2).getPositionRatio(), 1e-9);
    }

    @Test
    public void ensureVisibleRatiosShouldUseDealTimestampWhenClosingTradeMissesCloseTime() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 100d, 80d, 0d),
                new CurvePoint(2_000L, 100d, 80d, 0d),
                new CurvePoint(3_000L, 100d, 80d, 0d),
                new CurvePoint(4_000L, 100d, 80d, 0d)
        );
        List<TradeRecordItem> trades = Arrays.asList(
                new TradeRecordItem(
                        1_500L,
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
                        1_500L,
                        0d,
                        40d,
                        40d,
                        1L,
                        1L,
                        1L,
                        0
                ),
                new TradeRecordItem(
                        3_000L,
                        "BTC",
                        "BTCUSDT",
                        "BUY",
                        42d,
                        1d,
                        40d,
                        0d,
                        "",
                        0d,
                        1_500L,
                        1_500L,
                        0d,
                        40d,
                        42d,
                        2L,
                        1L,
                        1L,
                        1
                )
        );

        List<CurvePoint> resolved = AccountCurvePositionRatioHelper.ensureVisibleRatios(points, null, trades, 10d);

        assertEquals(0d, resolved.get(0).getPositionRatio(), 1e-9);
        assertEquals(0.05d, resolved.get(1).getPositionRatio(), 1e-9);
        assertEquals(0d, resolved.get(2).getPositionRatio(), 1e-9);
        assertEquals(0d, resolved.get(3).getPositionRatio(), 1e-9);
    }
}
