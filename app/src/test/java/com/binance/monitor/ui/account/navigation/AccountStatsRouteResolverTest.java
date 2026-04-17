package com.binance.monitor.ui.account.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.ui.account.AccountStatsBridgeActivity;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public class AccountStatsRouteResolverTest {

    @Test
    public void analysisDeepLinkTargetShouldExposeMinimalNavigationContract() {
        AnalysisDeepLinkTarget target = new AnalysisDeepLinkTarget(
                AnalysisDeepLinkTarget.TargetType.ANALYSIS_FULL,
                "acc@server",
                "BTCUSD",
                "30d",
                "stats",
                null,
                "bridge"
        );

        assertEquals(AnalysisDeepLinkTarget.TargetType.ANALYSIS_FULL, target.getTargetType());
        assertEquals("BTCUSD", target.getSymbol());
        assertEquals("bridge", target.getSource());
        assertTrue(target.requiresDirectAnalysisPage());
    }

    @Test
    public void resolverShouldTranslateHomeAnalysisWhenDirectFlagIsOff() {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("symbol", "BTCUSD");

        AnalysisDeepLinkTarget target = AccountStatsRouteResolver.resolveRaw(extras, false, "bridge");

        assertEquals(AnalysisDeepLinkTarget.TargetType.ANALYSIS_HOME, target.getTargetType());
        assertEquals("BTCUSD", target.getSymbol());
        assertFalse(target.requiresDirectAnalysisPage());
    }

    @Test
    public void resolverShouldTranslateTradeHistoryRequestToTradeHistoryTarget() {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put("symbol", "XAUUSD");
        extras.put(AccountStatsBridgeActivity.EXTRA_ANALYSIS_TARGET_SECTION,
                AccountStatsBridgeActivity.ANALYSIS_TARGET_TRADE_HISTORY);

        AnalysisDeepLinkTarget target = AccountStatsRouteResolver.resolveRaw(extras, true, "bridge");

        assertEquals(AnalysisDeepLinkTarget.TargetType.TRADE_HISTORY_FULL, target.getTargetType());
        assertEquals("XAUUSD", target.getSymbol());
        assertEquals(AccountStatsBridgeActivity.ANALYSIS_TARGET_TRADE_HISTORY, target.getFocusSection());
    }

    @Test
    public void normalizeFocusSectionShouldRejectUnknownTarget() {
        Map<String, String> extras = new LinkedHashMap<>();
        extras.put(AccountStatsBridgeActivity.EXTRA_ANALYSIS_TARGET_SECTION, "unknown");

        assertEquals("", AccountStatsRouteResolver.normalizeFocusSection(extras));
    }
}
