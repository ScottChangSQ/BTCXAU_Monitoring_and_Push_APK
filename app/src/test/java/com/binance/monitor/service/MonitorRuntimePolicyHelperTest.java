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
        assertTrue(MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(false, false)
                > MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(false, true));
        assertTrue(MonitorRuntimePolicyHelper.resolveAbnormalSyncDelayMs(false)
                > MonitorRuntimePolicyHelper.resolveAbnormalSyncDelayMs(true));
        // 前台悬浮窗主动放慢到 1s，后台亮屏先维持原节奏，后续再按场景继续拆分。
        assertTrue(MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(true)
                > MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(false));
    }

    @Test
    public void foregroundIntervalsShouldMatchCurrentBaseline() {
        assertEquals(30_000L, MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(true));
        assertEquals(60_000L, MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(false, true));
        assertEquals(90_000L, MonitorRuntimePolicyHelper.resolveHeartbeatDelayMs(false, false));
        assertEquals(8_000L, MonitorRuntimePolicyHelper.resolveAbnormalSyncDelayMs(true));
        assertEquals(1_000L, MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(true));
        assertEquals(500L, MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(false));
    }
}
