/*
 * 账户区间收益率辅助测试，确保收益率统一按“当期收益额 / 期初总资产”计算。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.domain.account.model.CurvePoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class AccountPeriodReturnHelperTest {

    @Test
    public void resolvePeriodReturnRateShouldUseLatestBalanceBeforePeriodStart() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(1_000L, 1_000d, 1_000d),
                new CurvePoint(2_000L, 1_050d, 1_050d),
                new CurvePoint(3_000L, 1_100d, 1_100d)
        );

        double rate = AccountPeriodReturnHelper.resolvePeriodReturnRate(points, 2_500L, 105d);

        assertEquals(0.10d, rate, 1e-9);
    }

    @Test
    public void resolvePeriodReturnRateShouldFallbackToFirstPointWhenNoEarlierBalanceExists() {
        List<CurvePoint> points = Arrays.asList(
                new CurvePoint(2_000L, 1_200d, 1_200d),
                new CurvePoint(3_000L, 1_260d, 1_260d)
        );

        double rate = AccountPeriodReturnHelper.resolvePeriodReturnRate(points, 1_000L, 120d);

        assertEquals(0.10d, rate, 1e-9);
    }

    @Test
    public void resolvePeriodReturnRateShouldReturnZeroWhenCurveIsMissing() {
        double rate = AccountPeriodReturnHelper.resolvePeriodReturnRate(null, 1_000L, 120d);

        assertEquals(0d, rate, 1e-9);
    }
}
