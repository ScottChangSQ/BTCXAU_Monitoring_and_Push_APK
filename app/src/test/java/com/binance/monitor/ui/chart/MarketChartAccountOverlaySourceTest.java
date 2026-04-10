package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartAccountOverlaySourceTest {

    @Test
    public void updateAccountAnnotationsOverlayShouldKeepPreviousPositionsWhileSessionCacheIsNotReady() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("会话仍激活但缓存尚未就绪时，图表页不应先清空当前持仓再等待下一轮刷新",
                source.contains("if (sessionActive && (cache == null || snapshot == null)) {\n            return;\n        }"));
    }

    @Test
    public void setupChartPositionPanelShouldRestoreLatestCacheInsteadOfBindingEmptyStateFirst() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("图表页初次创建时应直接从最新账户缓存恢复持仓和标注，避免先显示空白",
                source.contains("restoreChartOverlayFromLatestCacheOrEmpty();"));
        assertTrue("图表页应提供专门的首帧账户叠加层恢复入口",
                source.contains("private void restoreChartOverlayFromLatestCacheOrEmpty() {"));
        assertTrue("内存缓存尚未回填时，图表页仍应保留本地持久化快照恢复能力",
                source.contains("private AccountSnapshot resolveChartOverlaySnapshot(")
                        && source.contains("scheduleStoredChartOverlayRestore();"));
    }

    @Test
    public void storedOverlaySnapshotShouldBeBoundToCurrentSessionIdentity() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("图表页应记录本地叠加层快照归属账号，避免切号后继续复用旧账号快照",
                source.contains("private String storedChartOverlayAccount = \"\";"));
        assertTrue("图表页应记录本地叠加层快照归属服务器，避免同账号多服务器串数据",
                source.contains("private String storedChartOverlayServer = \"\";"));
        assertTrue("恢复本地叠加层前应校验当前会话身份是否仍匹配",
                source.contains("private boolean matchesStoredChartOverlayIdentity("));
        assertTrue("切号或会话失活时应统一清掉页面级叠加层缓存归属",
                source.contains("private void clearStoredChartOverlaySnapshot() {"));
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
