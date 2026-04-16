/*
 * 账户统计页面源码约束测试，确保本地恢复/预加载快照里的历史交易不会被旧内存历史挡住。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsBridgeActivityTradeHistorySourceTest {

    @Test
    public void applySnapshotShouldReplaceTradeHistoryInsteadOfMergingLocalMemory() throws Exception {
        String activitySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        String coordinatorSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java"
        );

        assertTrue("applySnapshot 应直接用服务端 canonical trades 覆盖页面历史",
                coordinatorSource.contains("host.replaceTradeHistory(snapshotTrades);"));
        assertTrue("页面主链不应再把快照历史和本地内存历史做 merge",
                !activitySource.contains("mergeTradeHistory(snapshotTrades);"));
    }

    @Test
    public void activityShouldNotKeepHeuristicTradeMergeHelpers() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("页面源码里不应继续保留启发式成交归并 helper",
                !source.contains("mergeOpenCloseTrades("));
        assertTrue("页面源码里不应继续保留零盈亏成交折叠 helper",
                !source.contains("collapseZeroProfitForDisplay("));
    }

    @Test
    public void activityAndStatsHelpersShouldNotNormalizeSecondTimestampsLocally() throws Exception {
        String activitySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );
        String weekdaySource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/TradeWeekdayStatsHelper.java",
                "src/main/java/com/binance/monitor/ui/account/TradeWeekdayStatsHelper.java"
        );
        String analyticsSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java",
                "src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java"
        );

        assertTrue("账户页主链不应继续保留秒转毫秒兼容",
                !activitySource.contains("normalizePossibleEpochMs("));
        assertTrue("星期统计不应继续保留秒转毫秒兼容",
                !weekdaySource.contains("normalizePossibleEpochMs("));
        assertTrue("曲线分析不应继续保留秒转毫秒兼容",
                !analyticsSource.contains("normalizePossibleEpochMs("));
    }

    @Test
    public void activityShouldNotKeepHistoryMergeHelpersBehindReplaceFacade() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户页不应再保留本地历史 merge helper",
                !source.contains("private void mergeTradeHistory("));
        assertTrue("账户页不应再保留本地曲线 merge helper",
                !source.contains("private void mergeCurveHistory("));
        assertTrue("账户页不应再保留本地成交 stable key 重组",
                !source.contains("private String buildTradeHistoryKey("));
    }

    @Test
    public void positionsShouldConsumeServerDayPnlAndRawListDirectly() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        );

        assertTrue("账户页不应继续本地补算自然日持仓盈亏",
                !source.contains("buildPositionListWithNaturalDayPnl("));
        assertTrue("账户页不应继续本地去重持仓列表",
                !source.contains("deduplicatePositionItems("));
        assertTrue("账户页不应继续拼 position fallback key 做去重",
                !source.contains("buildPositionUniqueKey("));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AccountStatsBridgeActivity.java");
    }
}
