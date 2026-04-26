package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TradeStatsChartRenderCacheSourceTest {

    @Test
    public void tradeStatsChartsShouldKeepPreparedDrawItems() throws Exception {
        assertPrepared("TradePnlBarChartView.java", "List<BarDrawItem> drawItems");
        assertPrepared("TradeWeekdayBarChartView.java", "List<BarDrawItem> drawItems");
        assertPrepared("TradeDistributionScatterView.java", "List<ScatterDrawItem> drawItems");
        assertPrepared("HoldingDurationDistributionView.java", "List<BucketDrawItem> drawItems");
    }

    private static void assertPrepared(String fileName, String marker) throws Exception {
        String source = readUtf8("app/src/main/java/com/binance/monitor/ui/account/" + fileName,
                "src/main/java/com/binance/monitor/ui/account/" + fileName);
        String onDraw = methodBody(source, "protected void onDraw");
        assertTrue(fileName + " 必须保留绘制缓存", source.contains(marker));
        assertTrue(fileName + " 必须在尺寸变化时重建绘制缓存", source.contains("protected void onSizeChanged"));
        assertFalse(fileName + " onDraw 不应每次重新计算正向最大值", onDraw.contains("maxPositive"));
        assertFalse(fileName + " onDraw 不应每次重新计算图表最小值", onDraw.contains("chartMin ="));
        assertFalse(fileName + " onDraw 不应每次遍历原始 entries", onDraw.contains("entries.get(i)"));
        assertFalse(fileName + " onDraw 不应每次遍历原始 buckets", onDraw.contains("buckets.get(i)"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到源码文件");
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new IllegalStateException("找不到方法: " + signature);
        }
        int braceStart = source.indexOf('{', start);
        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(braceStart, i + 1);
                }
            }
        }
        throw new IllegalStateException("方法括号不完整: " + signature);
    }
}
