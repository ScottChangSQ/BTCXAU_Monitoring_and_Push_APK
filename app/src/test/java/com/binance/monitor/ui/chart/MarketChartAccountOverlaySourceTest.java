package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
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
                source.contains("if (cache == null) {\n            return;\n        }"));
    }

    @Test
    public void updateAccountAnnotationsOverlayShouldRejectCacheFromAnotherActiveSession() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("图表页消费账户缓存前应校验缓存账号和当前激活会话一致，避免切号后短暂显示旧仓位",
                source.contains("if (!matchesActiveSessionIdentity(cache.getAccount(), cache.getServer())) {\n            clearStoredChartOverlaySnapshot();\n            clearAccountAnnotationsOverlay();\n            return;\n        }"));
    }

    @Test
    public void setupChartPositionPanelShouldRestoreLatestCacheInsteadOfBindingEmptyStateFirst() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("图表页初次创建时应直接从最新账户缓存恢复持仓和标注，避免先显示空白",
                source.contains("dataCoordinator.restoreChartOverlayFromLatestCacheOrEmpty();"));
        assertFalse("图表页不应继续保留专门的首帧账户叠加层恢复包装方法",
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

    @Test
    public void chartOverlayShouldClearWhenActiveSessionChangesButFreshSnapshotIsStillMissing() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("图表页应记录最后一次已应用叠加层的账号归属，避免切号后继续保留旧仓位标注",
                source.contains("private String lastAppliedOverlayAccount = \"\";"));
        assertTrue("图表页应记录最后一次已应用叠加层的服务器归属，避免同账号多服务器串数据",
                source.contains("private String lastAppliedOverlayServer = \"\";"));
        assertTrue("缓存尚未回填时，只能在当前叠加层仍属于活动会话时保留旧状态",
                source.contains("if (snapshot == null) {\n            if (!isCurrentOverlayBoundToActiveSession()) {\n                clearAccountAnnotationsOverlay();\n            }\n            return;\n        }"));
        assertTrue("图表页应提供当前叠加层和活动会话的身份比对方法",
                source.contains("private boolean isCurrentOverlayBoundToActiveSession() {"));
        assertTrue("每次真正应用叠加层后都应同步记录它的会话归属",
                source.contains("syncLastAppliedOverlayIdentity("));
    }

    @Test
    public void resolveTradeTargetSnapshotShouldNotReadRoomSynchronouslyOnMainThread() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java",
                "src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("图表页交易目标恢复应优先复用页面级已恢复快照，避免点击交易按钮时主线程直读 Room",
                source.contains("if (storedChartOverlaySnapshot != null\n                && matchesStoredChartOverlayIdentity(resolveActiveSessionAccount(), resolveActiveSessionServer())) {\n            return storedChartOverlaySnapshot;\n        }"));
        assertFalse("图表页交易目标恢复不应在主线程直接调用 hydrateLatestCacheFromStorage",
                source.contains("cache = accountStatsPreloadManager.hydrateLatestCacheFromStorage();"));
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
