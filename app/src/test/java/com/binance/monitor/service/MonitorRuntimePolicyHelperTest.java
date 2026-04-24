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
        // 悬浮窗在前后台都保持 0.5s 刷新，保证后台可见持仓手数和盈亏及时同步。
        assertEquals(MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(true),
                MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(false));
    }

    @Test
    public void foregroundIntervalsShouldMatchCurrentBaseline() {
        assertEquals(30_000L, MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(true));
        assertEquals(8_000L, MonitorRuntimePolicyHelper.resolveAbnormalSyncDelayMs(true));
        assertEquals(500L, MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(true));
    }
}
