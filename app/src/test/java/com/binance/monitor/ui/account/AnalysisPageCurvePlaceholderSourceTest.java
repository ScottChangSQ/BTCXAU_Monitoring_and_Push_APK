package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AnalysisPageCurvePlaceholderSourceTest {

    @Test
    public void bindLocalMetaShouldShowCurveSectionPlaceholderBeforeSnapshotArrives() throws IOException {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("showInitialCurvePlaceholder();"));
        assertTrue(source.contains("binding.cardCurveSection.setVisibility(View.VISIBLE);"));
        assertTrue(source.contains("binding.tvCurveMeta.setVisibility(View.GONE);"));
    }
}
