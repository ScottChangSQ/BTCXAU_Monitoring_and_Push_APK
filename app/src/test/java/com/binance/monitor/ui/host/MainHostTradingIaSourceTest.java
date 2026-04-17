package com.binance.monitor.ui.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainHostTradingIaSourceTest {

    @Test
    public void mainHostShouldUseTradingAccountAnalysisTabs() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_main_host.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String hostTab = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/HostTab.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String strings = new String(Files.readAllBytes(
                Paths.get("src/main/res/values/strings.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/tabTrading"));
        assertTrue(layout.contains("@+id/tabAccount"));
        assertTrue(layout.contains("@+id/tabAnalysis"));
        assertFalse(layout.contains("@+id/tabSettings"));
        assertFalse(layout.contains("@+id/tabMarketMonitor"));

        assertTrue(hostTab.contains("TRADING(\"trading\""));
        assertTrue(hostTab.contains("ACCOUNT(\"account\""));
        assertTrue(hostTab.contains("ANALYSIS(\"analysis\""));
        assertTrue(strings.contains("<string name=\"nav_trading\">交易</string>"));
        assertTrue(strings.contains("<string name=\"nav_account\">账户</string>"));
        assertTrue(strings.contains("<string name=\"nav_analysis\">分析</string>"));
    }
}
