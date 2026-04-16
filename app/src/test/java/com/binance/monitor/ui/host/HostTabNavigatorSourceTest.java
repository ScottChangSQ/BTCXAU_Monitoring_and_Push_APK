package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HostTabNavigatorSourceTest {

    @Test
    public void hostTabShouldExposeFiveStableEntries() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/HostTab.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("MARKET_MONITOR(\"market_monitor\""));
        assertTrue(source.contains("MARKET_CHART(\"market_chart\""));
        assertTrue(source.contains("ACCOUNT_STATS(\"account_stats\""));
        assertTrue(source.contains("ACCOUNT_POSITION(\"account_position\""));
        assertTrue(source.contains("SETTINGS(\"settings\""));
    }

    @Test
    public void hostNavigatorShouldUseFragmentShowHideInsteadOfPeerActivities() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("transaction.add(containerId, fragment, targetTab.getKey())"));
        assertTrue(source.contains("transaction.show(fragment);"));
        assertTrue(source.contains("transaction.hide(fragment);"));
    }

    @Test
    public void hostNavigatorShouldKeepFirstCreatedTargetFragmentInShowHideLoop() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("targetFragment = fragment;"));
    }
}
