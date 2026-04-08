package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CurveAnalyticsHelperSourceTest {

    @Test
    public void contractMultiplierShouldUseCanonicalProductSymbolMapper() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertTrue(source.contains("ProductSymbolMapper.toTradeSymbol(safeLabel(item))"));
        assertTrue(source.contains("ProductSymbolMapper.TRADE_SYMBOL_XAU"));
    }
}
