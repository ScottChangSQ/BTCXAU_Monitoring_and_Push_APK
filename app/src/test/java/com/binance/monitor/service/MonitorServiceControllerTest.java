package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.constants.AppConstants;

import org.junit.Test;

public class MonitorServiceControllerTest {

    @Test
    public void refreshConfigShouldNotStartServiceWhenEverythingIsDisabledAndServiceIsStopped() {
        assertFalse(MonitorServiceController.shouldStartService(
                false,
                AppConstants.ACTION_REFRESH_CONFIG,
                false,
                false
        ));
    }

    @Test
    public void refreshConfigShouldStartServiceWhenFloatingOrMonitoringIsEnabled() {
        assertTrue(MonitorServiceController.shouldStartService(
                false,
                AppConstants.ACTION_REFRESH_CONFIG,
                true,
                false
        ));
        assertTrue(MonitorServiceController.shouldStartService(
                false,
                AppConstants.ACTION_REFRESH_CONFIG,
                false,
                true
        ));
    }

    @Test
    public void bootstrapShouldStillStartServiceEvenWhenEverythingIsDisabled() {
        assertTrue(MonitorServiceController.shouldStartService(
                false,
                AppConstants.ACTION_BOOTSTRAP,
                false,
                false
        ));
    }

    @Test
    public void runningServiceShouldAcceptFollowUpActions() {
        assertTrue(MonitorServiceController.shouldStartService(
                true,
                AppConstants.ACTION_REFRESH_CONFIG,
                false,
                false
        ));
        assertTrue(MonitorServiceController.shouldStartService(
                true,
                AppConstants.ACTION_CLEAR_ACCOUNT_RUNTIME,
                false,
                false
        ));
    }

    @Test
    public void stopMonitoringShouldNotRestartServiceWhenServiceIsAlreadyStopped() {
        assertFalse(MonitorServiceController.shouldStartService(
                false,
                AppConstants.ACTION_STOP_MONITORING,
                true,
                true
        ));
    }
}
