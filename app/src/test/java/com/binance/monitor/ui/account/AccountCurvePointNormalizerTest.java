/*
 * 账户曲线归一化测试，确保净值/结余修正时不会丢掉仓位比例。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.ui.account.model.CurvePoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AccountCurvePointNormalizerTest {

    @Test
    public void normalizeShouldPreservePositionRatioForExistingPoints() {
        List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(
                Arrays.asList(
                        new CurvePoint(2_000L, 110d, 100d, 0.45d),
                        new CurvePoint(1_000L, 0d, 95d, 0.12d)
                ),
                100d,
                9_999L
        );

        assertEquals(2, normalized.size());
        assertEquals(1_000L, normalized.get(0).getTimestamp());
        assertEquals(95d, normalized.get(0).getEquity(), 1e-9);
        assertEquals(95d, normalized.get(0).getBalance(), 1e-9);
        assertEquals(0.12d, normalized.get(0).getPositionRatio(), 1e-9);
        assertEquals(0.45d, normalized.get(1).getPositionRatio(), 1e-9);
    }

    @Test
    public void normalizeShouldKeepPositionRatioWhenDuplicatingSinglePoint() {
        List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(
                Arrays.asList(new CurvePoint(5_000L, 120d, 118d, 0.66d)),
                100d,
                9_999L
        );

        assertEquals(2, normalized.size());
        assertEquals(65_000L, normalized.get(1).getTimestamp());
        assertEquals(0.66d, normalized.get(0).getPositionRatio(), 1e-9);
        assertEquals(0.66d, normalized.get(1).getPositionRatio(), 1e-9);
    }

    @Test
    public void normalizeShouldDeduplicateSameTimestampUsingLatestPoint() {
        List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(
                Arrays.asList(
                        new CurvePoint(1_000L, 100d, 100d, 0.10d),
                        new CurvePoint(1_000L, 150d, 120d, 0.30d),
                        new CurvePoint(2_000L, 160d, 125d, 0.35d)
                ),
                100d,
                9_999L
        );

        assertEquals(2, normalized.size());
        assertEquals(1_000L, normalized.get(0).getTimestamp());
        assertEquals(150d, normalized.get(0).getEquity(), 1e-9);
        assertEquals(120d, normalized.get(0).getBalance(), 1e-9);
        assertEquals(0.30d, normalized.get(0).getPositionRatio(), 1e-9);
        assertEquals(2_000L, normalized.get(1).getTimestamp());
    }
}
