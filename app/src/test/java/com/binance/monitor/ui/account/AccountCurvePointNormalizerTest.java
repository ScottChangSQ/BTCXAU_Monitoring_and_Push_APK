/*
 * 账户曲线归一化测试，确保这里只做严格清洗，不再本地补点或补值。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.domain.account.model.CurvePoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AccountCurvePointNormalizerTest {

    @Test
    public void normalizeShouldSortAndKeepValidPointsOnly() {
        List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(
                Arrays.asList(
                        new CurvePoint(2_000L, 110d, 100d, 0.45d),
                        new CurvePoint(1_000L, 95d, 90d, 0.12d),
                        new CurvePoint(0L, 100d, 90d, 0.10d),
                        new CurvePoint(3_000L, 0d, 100d, 0.20d),
                        new CurvePoint(4_000L, 120d, 0d, 0.30d),
                        null
                ),
                100d,
                9_999L
        );

        assertEquals(2, normalized.size());
        assertEquals(1_000L, normalized.get(0).getTimestamp());
        assertEquals(95d, normalized.get(0).getEquity(), 1e-9);
        assertEquals(90d, normalized.get(0).getBalance(), 1e-9);
        assertEquals(0.12d, normalized.get(0).getPositionRatio(), 1e-9);
        assertEquals(0.45d, normalized.get(1).getPositionRatio(), 1e-9);
    }

    @Test
    public void normalizeShouldNotDuplicateSinglePoint() {
        List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(
                Arrays.asList(new CurvePoint(5_000L, 120d, 118d, 0.66d)),
                100d,
                9_999L
        );

        assertEquals(1, normalized.size());
        assertEquals(5_000L, normalized.get(0).getTimestamp());
        assertEquals(0.66d, normalized.get(0).getPositionRatio(), 1e-9);
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

    @Test
    public void normalizeShouldReturnEmptyWhenNoValidCurvePointExists() {
        List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(
                Arrays.asList(
                        new CurvePoint(0L, 100d, 90d, 0.1d),
                        new CurvePoint(1_000L, 0d, 90d, 0.2d),
                        new CurvePoint(2_000L, 120d, 0d, 0.3d)
                ),
                100d,
                9_999L
        );

        assertTrue(normalized.isEmpty());
    }
}
