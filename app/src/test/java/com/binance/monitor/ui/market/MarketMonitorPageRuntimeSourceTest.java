/*
 * 监控页运行时源码约束测试，锁定连接详情弹窗的 spacing 必须走全局 token 入口。
 */
package com.binance.monitor.ui.market;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketMonitorPageRuntimeSourceTest {

    @Test
    public void connectionDialogShouldResolveRuntimeSpacingFromTokenResolver() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java",
                "src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java"
        );

        assertTrue(source.contains("import com.binance.monitor.ui.theme.SpacingTokenResolver;"));
        assertTrue(source.contains("SpacingTokenResolver.px(requireActivity(), R.dimen.dialog_content_padding)"));
        assertTrue(source.contains("SpacingTokenResolver.rowGapPx(requireActivity())"));
        assertTrue(source.contains("SpacingTokenResolver.px(requireActivity(), R.dimen.list_item_padding_x)"));
        assertTrue(source.contains("SpacingTokenResolver.px(requireActivity(), R.dimen.list_item_padding_y)"));
        assertFalse(source.contains("content.setPadding(dp(18), dp(14), dp(18), dp(6));"));
        assertFalse(source.contains("row.setPadding(dp(12), dp(10), dp(12), dp(10));"));
        assertFalse(source.contains("params.topMargin = dp(10);"));
        assertFalse(source.contains("valueParams.topMargin = dp(4);"));
    }

    @Test
    public void marketMonitorShouldObserveUnifiedMarketRuntimeSnapshot() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java",
                "src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java"
        );

        assertTrue(source.contains("viewModel.getMarketRuntimeSnapshotLiveData().observe(lifecycleOwner"));
        assertTrue(source.contains("viewModel.getMarketTruthSnapshotLiveData().observe(lifecycleOwner"));
        assertTrue(source.contains("renderMarketIfNeeded();"));
        assertFalse(source.contains("viewModel.getMarketRuntimeSnapshotLiveData().getValue()"));
        assertFalse(source.contains("viewModel.getDisplayPrices().observe(lifecycleOwner"));
        assertFalse(source.contains("viewModel.getDisplayOverviewKlines().observe(lifecycleOwner"));
        assertFalse(source.contains("import com.binance.monitor.runtime.market.MarketSelector;"));
        assertFalse(source.contains("import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;"));
        assertFalse(source.contains("private MarketRuntimeSnapshot latestMarketRuntimeSnapshot"));
        assertTrue(source.contains("viewModel.selectLatestPrice(selectedSymbol)"));
        assertTrue(source.contains("viewModel.selectClosedMinute(selectedSymbol)"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到 MarketMonitorPageRuntime.java");
    }
}
