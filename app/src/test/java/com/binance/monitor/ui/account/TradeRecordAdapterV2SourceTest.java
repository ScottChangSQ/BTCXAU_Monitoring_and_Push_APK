package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TradeRecordAdapterV2SourceTest {

    @Test
    public void adapterShouldNotBackfillLifecycleTimeFromTimestampForRowIdentity() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapterV2.java")),
                StandardCharsets.UTF_8
        );

        assertFalse(source.contains("resolveTime(item.getOpenTime(), item.getTimestamp())"));
        assertFalse(source.contains("resolveTime(item.getCloseTime(), item.getTimestamp())"));
        assertFalse(source.contains("private long resolveTime(long value, long fallback)"));
    }

    @Test
    public void adapterShouldUsePaletteDrivenTradeHistoryRows() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapterV2.java")),
                StandardCharsets.UTF_8
        );

        assertTrue(source.contains("UiPaletteManager.Palette palette = UiPaletteManager.resolve(binding.getRoot().getContext());"));
        assertTrue(source.contains("binding.layoutHeader.setBackground(UiPaletteManager.createListRowBackground("));
        assertTrue(source.contains("binding.tvExpandHint.setTextColor(palette.textSecondary);"));
        assertTrue(source.contains("IndicatorFormatterCenter.formatMoney"));
        assertTrue(source.contains("IndicatorPresentationPolicy.applyDirectionalSpanForExactToken("));
    }
}
