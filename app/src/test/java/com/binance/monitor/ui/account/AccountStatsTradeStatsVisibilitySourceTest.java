package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsTradeStatsVisibilitySourceTest {

    @Test
    public void tradeStatsRefreshShouldPreserveCardSlotInScreenAndBridge() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");

        assertTrue(screenSource.contains("if (binding.cardTradeStatsSection.getVisibility() != View.VISIBLE) {\n            binding.cardTradeStatsSection.setVisibility(View.INVISIBLE);\n        }"));
        assertTrue(activitySource.contains("if (binding.cardTradeStatsSection.getVisibility() != View.VISIBLE) {\n            binding.cardTradeStatsSection.setVisibility(View.INVISIBLE);\n        }"));
        assertFalse(screenSource.contains("private void hideTradeStatsSectionUntilFreshContentReady() {\n        binding.cardTradeStatsSection.setVisibility(View.GONE);\n    }"));
        assertFalse(activitySource.contains("private void hideTradeStatsSectionUntilFreshContentReady() {\n        binding.cardTradeStatsSection.setVisibility(View.GONE);\n    }"));
    }

    @Test
    public void tradeStatsBindShouldRevealImmediatelyAfterBindingInsteadOfWaitingExtraPredraw() throws Exception {
        String screenSource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");

        assertFalse(screenSource.contains("private void revealTradeStatsSectionWhenReady()"));
        assertFalse(activitySource.contains("private void revealTradeStatsSectionWhenReady()"));
        assertFalse(screenSource.contains("binding.recyclerStats.getViewTreeObserver().addOnPreDrawListener("));
        assertFalse(activitySource.contains("binding.recyclerStats.getViewTreeObserver().addOnPreDrawListener("));
        assertFalse(screenSource.contains("boolean firstReveal = binding.cardTradeStatsSection.getVisibility() != View.VISIBLE;"));
        assertFalse(activitySource.contains("boolean firstReveal = binding.cardTradeStatsSection.getVisibility() != View.VISIBLE;"));
        assertTrue(screenSource.contains("binding.tvTradePnlLegend.setVisibility(View.GONE);\n        binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
        assertTrue(activitySource.contains("binding.tvTradePnlLegend.setVisibility(View.GONE);\n        binding.cardTradeStatsSection.setVisibility(View.VISIBLE);"));
    }

    private static String readUtf8(String candidate) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        Path direct = workingDir.resolve(candidate).normalize();
        if (Files.exists(direct)) {
            return new String(Files.readAllBytes(direct), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        }
        Path nested = workingDir.resolve("app").resolve(candidate).normalize();
        return new String(Files.readAllBytes(nested), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
    }
}
