/*
 * 附图共享高亮源码约束测试，确保三张附图和主图一样保留精确高亮时间，不再只靠最近真实点索引。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class CurvePaneSharedHighlightSourceTest {

    @Test
    public void secondaryCurvePanesShouldKeepExactHighlightedTimestamp() throws Exception {
        assertKeepsExactHighlightTimestamp(
                readUtf8(
                        "app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java",
                        "src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java"
                )
        );
        assertKeepsExactHighlightTimestamp(
                readUtf8(
                        "app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java",
                        "src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java"
                )
        );
        assertKeepsExactHighlightTimestamp(
                readUtf8(
                        "app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java",
                        "src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java"
                )
        );
    }

    private static void assertKeepsExactHighlightTimestamp(String source) {
        assertTrue("附图应保存精确高亮时间，而不是只保存最近真实点索引",
                source.contains("private long highlightedTimestamp = -1L;"));
        assertTrue("宿主同步共享高亮时，附图应记录精确时间",
                source.contains("highlightedTimestamp = timestamp;"));
        assertTrue("用户拖动附图时，附图应记录当前手指对应的精确时间",
                source.contains("highlightedTimestamp = targetTs;"));
        assertTrue("清理共享高亮时，附图应一并清掉精确时间",
                source.contains("highlightedTimestamp = -1L;"));
        assertTrue("附图竖线位置应优先按精确高亮时间映射，而不是继续吸附到最近真实点",
                source.contains("if (highlightedTimestamp > 0L) {"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到附图源码文件");
    }
}
