/*
 * 监控服务运行策略测试，确保前后台切换后会自动使用不同的刷新节奏。
 */
package com.binance.monitor.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MonitorRuntimePolicyHelperTest {

    @Test
    public void backgroundIntervalsShouldBeSlowerThanForeground() {
        assertTrue(MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(false)
                > MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(true));
        assertTrue(MonitorRuntimePolicyHelper.resolveAbnormalSyncDelayMs(false)
                > MonitorRuntimePolicyHelper.resolveAbnormalSyncDelayMs(true));
        assertTrue(MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(false)
                > MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(true));
    }

    @Test
    public void foregroundIntervalsShouldMatchCurrentBaseline() {
        assertEquals(30_000L, MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(true));
        assertEquals(8_000L, MonitorRuntimePolicyHelper.resolveAbnormalSyncDelayMs(true));
        assertEquals(1_500L, MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(true));
    }
}
