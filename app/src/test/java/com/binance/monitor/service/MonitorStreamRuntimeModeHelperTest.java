package com.binance.monitor.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MonitorStreamRuntimeModeHelperTest {

    @Test
    public void foregroundShouldKeepFullRuntime() {
        MonitorStreamRuntimeModeHelper.RuntimeMode mode =
                MonitorStreamRuntimeModeHelper.resolve(true, true, true);

        assertEquals(MonitorStreamRuntimeModeHelper.RuntimeMode.FOREGROUND_FULL, mode);
        assertTrue(MonitorStreamRuntimeModeHelper.shouldApplyFullAccountRuntime(mode));
        assertTrue(MonitorStreamRuntimeModeHelper.shouldApplyMarketSnapshot(mode));
        assertTrue(MonitorStreamRuntimeModeHelper.shouldPullAccountHistory(mode));
    }

    @Test
    public void backgroundInteractiveWithFloatingShouldUseMinimalFloatingMode() {
        MonitorStreamRuntimeModeHelper.RuntimeMode mode =
                MonitorStreamRuntimeModeHelper.resolve(false, true, true);

        assertEquals(MonitorStreamRuntimeModeHelper.RuntimeMode.BACKGROUND_FLOATING_MINIMAL, mode);
        assertFalse(MonitorStreamRuntimeModeHelper.shouldApplyFullAccountRuntime(mode));
        assertTrue(MonitorStreamRuntimeModeHelper.shouldApplyLiteAccountRuntime(mode));
        assertTrue(MonitorStreamRuntimeModeHelper.shouldApplyMarketSnapshot(mode));
        assertFalse(MonitorStreamRuntimeModeHelper.shouldPullAccountHistory(mode));
    }

    @Test
    public void screenOffOrNoFloatingShouldFallBackToAlertOnly() {
        MonitorStreamRuntimeModeHelper.RuntimeMode screenOffMode =
                MonitorStreamRuntimeModeHelper.resolve(false, false, true);
        MonitorStreamRuntimeModeHelper.RuntimeMode noFloatingMode =
                MonitorStreamRuntimeModeHelper.resolve(false, true, false);

        assertEquals(MonitorStreamRuntimeModeHelper.RuntimeMode.ALERT_ONLY, screenOffMode);
        assertEquals(MonitorStreamRuntimeModeHelper.RuntimeMode.ALERT_ONLY, noFloatingMode);
        assertFalse(MonitorStreamRuntimeModeHelper.shouldApplyMarketSnapshot(screenOffMode));
        assertFalse(MonitorStreamRuntimeModeHelper.shouldApplyLiteAccountRuntime(screenOffMode));
        assertFalse(MonitorStreamRuntimeModeHelper.shouldRefreshFloating(noFloatingMode));
    }
}
