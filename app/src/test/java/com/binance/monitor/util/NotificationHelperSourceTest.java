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
    public void helperShouldKeepServiceAndAlertChannels() throws Exception {
        Path helperFile = Paths.get("src/main/java/com/binance/monitor/util/NotificationHelper.java");
        String helperSource = new String(Files.readAllBytes(helperFile), StandardCharsets.UTF_8);

        assertTrue(helperSource.contains("AppConstants.SERVICE_CHANNEL_ID"));
        assertTrue(helperSource.contains("AppConstants.ALERT_CHANNEL_ID"));
        assertTrue(helperSource.contains("notifyAbnormalAlert("));
        assertTrue(helperSource.contains("notifyAccountMismatch("));
        assertTrue(helperSource.contains("服务器端当前实际账号已经不是原来的账号了"));
        assertTrue(helperSource.contains("alertChannel"));
        assertTrue(helperSource.contains("HostNavigationIntentFactory.forTab(context, HostTab.MARKET_MONITOR)"));
    }

    @Test
    public void appConstantsShouldKeepAlertChannelId() throws Exception {
        Path constantsFile = Paths.get("src/main/java/com/binance/monitor/constants/AppConstants.java");
        String constantsSource = new String(Files.readAllBytes(constantsFile), StandardCharsets.UTF_8);

        assertTrue(constantsSource.contains("ALERT_CHANNEL_ID"));
    }
}
