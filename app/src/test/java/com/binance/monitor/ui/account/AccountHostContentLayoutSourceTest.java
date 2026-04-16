package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountHostContentLayoutSourceTest {

    @Test
    public void accountPositionContentShouldNotKeepZeroHeightWeightedRootInHostFragment() throws Exception {
        String xml = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_account_position.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(xml.contains("android:id=\"@+id/scrollAccountPosition\""));
        assertTrue(xml.contains("android:id=\"@+id/scrollAccountPosition\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"match_parent\""));
        assertFalse(xml.contains("android:id=\"@+id/scrollAccountPosition\"\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"0dp\""));
    }

    @Test
    public void accountStatsContentShouldNotKeepZeroHeightWeightedRootInHostFragment() throws Exception {
        String xml = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_account_stats.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(xml.contains("android:id=\"@+id/scrollAccountStats\""));
        assertTrue(xml.contains("<FrameLayout\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"match_parent\">"));
        assertFalse(xml.contains("<FrameLayout\n        android:layout_width=\"match_parent\"\n        android:layout_height=\"0dp\""));
    }
}
