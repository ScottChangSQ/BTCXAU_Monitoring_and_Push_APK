/*
 * 长周期预显示策略测试，确保周/月/年线不会继续拿分钟底稿硬聚合成单根假数据。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChartWarmDisplayPolicyHelperTest {

    @Test
    public void canWarmDisplayFromShouldRejectMinuteSourceForWeeklyAndMonthly() {
        assertFalse(ChartWarmDisplayPolicyHelper.canWarmDisplayFrom("1m", false, "1w", false));
        assertFalse(ChartWarmDisplayPolicyHelper.canWarmDisplayFrom("1m", false, "1M", false));
    }

    @Test
    public void canWarmDisplayFromShouldAllowDailySourceForWeeklyAndMonthly() {
        assertTrue(ChartWarmDisplayPolicyHelper.canWarmDisplayFrom("1d", false, "1w", false));
        assertTrue(ChartWarmDisplayPolicyHelper.canWarmDisplayFrom("1d", false, "1M", false));
    }

    @Test
    public void canWarmDisplayFromShouldRestrictYearAggregateToMonthlySource() {
        assertFalse(ChartWarmDisplayPolicyHelper.canWarmDisplayFrom("1d", false, "1y", true));
        assertFalse(ChartWarmDisplayPolicyHelper.canWarmDisplayFrom("1w", false, "1y", true));
        assertTrue(ChartWarmDisplayPolicyHelper.canWarmDisplayFrom("1M", false, "1y", true));
    }

    @Test
    public void canRefreshFromMinuteTailShouldRejectWeeklyAndMonthly() {
        assertFalse(ChartWarmDisplayPolicyHelper.canRefreshFromMinuteTail("1w", false));
        assertFalse(ChartWarmDisplayPolicyHelper.canRefreshFromMinuteTail("1M", false));
        assertFalse(ChartWarmDisplayPolicyHelper.canRefreshFromMinuteTail("1y", true));
        assertTrue(ChartWarmDisplayPolicyHelper.canRefreshFromMinuteTail("1d", false));
    }

    @Test
    public void shouldWarmDisplayShouldOnlyRunWhenCurrentWindowMissingOrSwitched() {
        assertTrue(ChartWarmDisplayPolicyHelper.shouldWarmDisplay(true, false));
        assertTrue(ChartWarmDisplayPolicyHelper.shouldWarmDisplay(false, true));
        assertFalse(ChartWarmDisplayPolicyHelper.shouldWarmDisplay(false, false));
    }
}
