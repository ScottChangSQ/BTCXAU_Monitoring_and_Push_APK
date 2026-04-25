package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ThemeLauncherIconManagerSourceTest {

    @Test
    public void themeLauncherIconManagerShouldOwnAllLauncherAliases() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/theme/ThemeLauncherIconManager.java")),
                StandardCharsets.UTF_8
        );
        assertTrue(source.contains("IconFinancialAlias"));
        assertTrue(source.contains("IconVintageAlias"));
        assertTrue(source.contains("IconBinanceAlias"));
        assertTrue(source.contains("IconTradingViewAlias"));
        assertTrue(source.contains("IconLightAlias"));
    }
}
