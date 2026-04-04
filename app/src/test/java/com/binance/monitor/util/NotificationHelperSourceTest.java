package com.binance.monitor.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NotificationHelperSourceTest {

    @Test
    public void helperShouldOnlyKeepServiceNotificationChannel() throws Exception {
        Path helperFile = Paths.get("src/main/java/com/binance/monitor/util/NotificationHelper.java");
        String helperSource = new String(Files.readAllBytes(helperFile), StandardCharsets.UTF_8);

        assertTrue(helperSource.contains("AppConstants.SERVICE_CHANNEL_ID"));
        assertFalse(helperSource.contains("AppConstants.ALERT_CHANNEL_ID"));
        assertFalse(helperSource.contains("notifyAlert("));
        assertFalse(helperSource.contains("alertChannel"));
    }

    @Test
    public void appConstantsShouldNotKeepLegacyAlertChannelId() throws Exception {
        Path constantsFile = Paths.get("src/main/java/com/binance/monitor/constants/AppConstants.java");
        String constantsSource = new String(Files.readAllBytes(constantsFile), StandardCharsets.UTF_8);

        assertFalse(constantsSource.contains("ALERT_CHANNEL_ID"));
    }
}
