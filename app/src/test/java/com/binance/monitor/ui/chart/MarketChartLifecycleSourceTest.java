package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartLifecycleSourceTest {

    @Test
    public void chartResumeShouldUseUnifiedScreenEntryInsteadOfResettingTransport() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("图表页应提供统一的前台进入入口，避免 onCreate/onResume 各自散落恢复逻辑",
                source.contains("private void enterChartScreen(boolean coldStart)"));
        assertTrue("图表页 onResume 应先恢复账户叠加层，再按隐私态重绘，避免当前持仓先闪成空白",
                source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();\n        applyPaletteStyles();\n        if (accountStatsPreloadManager != null) {\n            accountStatsPreloadManager.addCacheListener(accountCacheListener);\n        }\n        restoreChartOverlayFromLatestCacheOrEmpty();\n        applyPrivacyMaskState();\n        enterChartScreen(!chartScreenEntered);\n        chartScreenEntered = true;\n    }"));
        assertFalse("普通 tab 返回图表页时不应无条件重建行情 HTTP transport",
                source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();\n        applyPaletteStyles();\n        applyPrivacyMaskState();\n        if (gatewayV2Client != null) {\n            gatewayV2Client.resetTransport();"));
        assertFalse("普通 tab 返回图表页时不应无条件重建交易 HTTP transport",
                source.contains("protected void onResume() {\n        super.onResume();\n        ensureMonitorServiceStarted();\n        applyPaletteStyles();\n        applyPrivacyMaskState();\n        if (gatewayV2TradeClient != null) {\n            gatewayV2TradeClient.resetTransport();"));
        assertFalse("图表页作为纯消费层，不应再通过前后台切换驱动 full snapshot 调度",
                source.contains("accountStatsPreloadManager.setFullSnapshotActive(true);")
                        || source.contains("accountStatsPreloadManager.setFullSnapshotActive(false);"));
    }

    @Test
    public void abnormalOverlayShouldSkipRebuildWhenInputsUnchanged() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("异常标注层应维护上次签名，避免切 tab 时重复重建",
                source.contains("private String lastAbnormalOverlaySignature = \"\";"));
        assertTrue("异常标注层应提供统一签名构建方法",
                source.contains("private String buildAbnormalOverlaySignature("));
        assertTrue("异常标注层在输入未变化时应直接跳过重建",
                source.contains("if (abnormalOverlaySignature.equals(lastAbnormalOverlaySignature)) {\n            return;\n        }"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MarketChartActivity.java");
    }
}
