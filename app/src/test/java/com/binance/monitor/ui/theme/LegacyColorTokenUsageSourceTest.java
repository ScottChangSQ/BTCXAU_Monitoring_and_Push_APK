/*
 * 锁定低频入口和旧适配器的颜色迁移，防止再回到 legacy accent/divider 命名。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LegacyColorTokenUsageSourceTest {

    @Test
    public void legacyAdaptersAndScrollBarShouldUseCanonicalTokens() throws Exception {
        String scrollBar = readUtf8("src/main/java/com/binance/monitor/ui/widget/TradeScrollBarView.java");
        String styleHelper = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountValueStyleHelper.java");
        String metricBinder = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricViewBinder.java");
        String positionAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapter.java");
        String tradeRecordAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapter.java");

        assertTrue(scrollBar.contains("R.color.border_subtle"));
        assertTrue(scrollBar.contains("R.color.accent_primary"));
        assertFalse(scrollBar.contains("R.color.divider"));
        assertFalse(scrollBar.contains("R.color.accent_blue"));

        assertTrue(styleHelper.contains("R.color.pnl_profit"));
        assertTrue(styleHelper.contains("R.color.pnl_loss"));
        assertFalse(styleHelper.contains("R.color.accent_green"));
        assertFalse(styleHelper.contains("R.color.accent_red"));

        assertTrue(metricBinder.contains("IndicatorPresentationPolicy.applyDirectionalSpanForValueRange("));
        assertFalse(metricBinder.contains("new ForegroundColorSpan("));
        assertFalse(metricBinder.contains("R.color.pnl_profit"));
        assertFalse(metricBinder.contains("R.color.pnl_loss"));
        assertFalse(metricBinder.contains("R.color.accent_green"));
        assertFalse(metricBinder.contains("R.color.accent_red"));

        assertTrue(positionAdapter.contains("R.color.pnl_profit"));
        assertTrue(positionAdapter.contains("R.color.pnl_loss"));
        assertFalse(positionAdapter.contains("R.color.accent_green"));
        assertFalse(positionAdapter.contains("R.color.accent_red"));

        assertTrue(tradeRecordAdapter.contains("R.color.trade_buy"));
        assertTrue(tradeRecordAdapter.contains("R.color.trade_sell"));
        assertFalse(tradeRecordAdapter.contains("R.color.accent_green"));
        assertFalse(tradeRecordAdapter.contains("R.color.accent_red"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
