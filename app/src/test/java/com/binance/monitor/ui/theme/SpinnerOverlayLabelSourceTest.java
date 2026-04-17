/*
 * 校验带覆盖标签的下拉统一走隐藏折叠文字的公共资源，避免同一位置显示两层文字。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SpinnerOverlayLabelSourceTest {

    @Test
    public void overlayLabelSpinnersShouldUseAnchorLayout() throws Exception {
        String marketRuntime = readUtf8("src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java");
        String chartScreen = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
        String tradeHistory = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountTradeHistoryBottomSheetController.java");
        String accountStats = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java");
        String accountStatsBridge = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");

        assertTrue(marketRuntime.contains("R.layout.item_spinner_filter_anchor"));
        assertTrue(chartScreen.contains("R.layout.item_spinner_filter_anchor"));
        assertTrue(tradeHistory.contains("R.layout.item_spinner_filter_anchor"));
        assertTrue(accountStats.contains("R.layout.item_spinner_filter_anchor"));
        assertTrue(accountStatsBridge.contains("R.layout.item_spinner_filter_anchor"));
    }

    @Test
    public void regularVisibleSpinnersShouldKeepVisibleLayout() throws Exception {
        String globalStatus = readUtf8("src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java");
        String tradeDialog = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");

        assertTrue(globalStatus.contains("R.layout.item_spinner_filter,"));
        assertTrue(tradeDialog.contains("R.layout.item_spinner_filter,"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
