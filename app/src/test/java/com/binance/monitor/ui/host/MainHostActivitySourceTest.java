package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainHostActivitySourceTest {

    @Test
    public void mainHostActivityShouldRetargetTabWhenReceivingNewIntent() throws Exception {
        String source = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/MainHostActivity.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("protected void onNewIntent(@NonNull android.content.Intent intent) {"));
        assertTrue(source.contains("setIntent(intent);"));
        assertTrue(source.contains("selectedTab = HostTab.fromKey(intent.getStringExtra(EXTRA_TARGET_TAB));"));
        assertTrue(source.contains("showSelectedTab();"));
        assertTrue(source.contains("private void updateBottomTabs() {"));
    }
}
