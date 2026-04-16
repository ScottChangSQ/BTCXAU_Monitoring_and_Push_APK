package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HostTabNavigatorSourceTest {

    @Test
    public void hostTabShouldExposeTradingAccountAnalysisAndCompatibilityAliases() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/HostTab.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("TRADING(\"trading\""));
        assertTrue(source.contains("ACCOUNT(\"account\""));
        assertTrue(source.contains("ANALYSIS(\"analysis\""));
        assertTrue(source.contains("public static final HostTab MARKET_MONITOR = TRADING;"));
        assertTrue(source.contains("public static final HostTab MARKET_CHART = TRADING;"));
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
