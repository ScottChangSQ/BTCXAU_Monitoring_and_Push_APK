/*
 * 长周期图表拉取策略测试，确保周/月线优先直连官方周期接口，异常情况下再回退日线聚合。
 */
package com.binance.monitor.data.remote;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChartLongIntervalFetchPolicyHelperTest {

    @Test
    public void shouldUseDirectRestFirst_onlyForWeeklyAndMonthly() {
        assertTrue(ChartLongIntervalFetchPolicyHelper.shouldUseDirectRestFirst("1w"));
        assertTrue(ChartLongIntervalFetchPolicyHelper.shouldUseDirectRestFirst("1M"));
        assertFalse(ChartLongIntervalFetchPolicyHelper.shouldUseDirectRestFirst("1d"));
        assertFalse(ChartLongIntervalFetchPolicyHelper.shouldUseDirectRestFirst("1h"));
    }

    @Test
    public void shouldFallbackToDailyAggregation_whenDirectLongIntervalResultIsSuspiciouslyShort() {
        assertTrue(ChartLongIntervalFetchPolicyHelper.shouldFallbackToDailyAggregation("1w", 1500, 1));
        assertTrue(ChartLongIntervalFetchPolicyHelper.shouldFallbackToDailyAggregation("1M", 200, 1));
        assertFalse(ChartLongIntervalFetchPolicyHelper.shouldFallbackToDailyAggregation("1w", 1500, 8));
        assertFalse(ChartLongIntervalFetchPolicyHelper.shouldFallbackToDailyAggregation("1d", 1500, 1));
    }
}
