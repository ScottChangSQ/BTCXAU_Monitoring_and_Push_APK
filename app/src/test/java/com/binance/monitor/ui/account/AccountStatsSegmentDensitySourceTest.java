/*
 * 锁定分析页关键分段按钮的字号与宽度收口，避免收益统计与交易统计继续使用过大的按钮文案。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class AccountStatsSegmentDensitySourceTest {

    @Test
    public void statsScreenAndBridgeShouldUseDenseSegmentLabelsForAnalysisToggles() throws Exception {
        assertSegmentDensityContract(readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ));
        assertSegmentDensityContract(readProjectFile(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java"
        ));
    }

    private static void assertSegmentDensityContract(String source) {
        assertTrue(source.contains("styleSegmentButton(binding.btnReturnDay, \"\\u65e5\\u6536\\u76ca\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("styleSegmentButton(binding.btnReturnMonth, \"\\u6708\\u6536\\u76ca\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("styleSegmentButton(binding.btnReturnYear, \"\\u5e74\\u6536\\u76ca\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("styleSegmentButton(binding.btnReturnStage, \"\\u9636\\u6bb5\\u6536\\u76ca\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("styleSegmentButton(binding.btnTradePnlAll, \"\\u5168\\u90e8\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("styleSegmentButton(binding.btnTradePnlBuy, \"\\u4e70\\u5165\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("styleSegmentButton(binding.btnTradePnlSell, \"\\u5356\\u51fa\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("styleSegmentButton(binding.btnTradeWeekdayCloseTime, \"\\u6309\\u5e73\\u4ed3\\u65f6\\u95f4\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));
        assertTrue(source.contains("styleSegmentButton(binding.btnTradeWeekdayOpenTime, \"\\u6309\\u5f00\\u4ed3\\u65f6\\u95f4\", R.style.TextAppearance_BinanceMonitor_ChartDense);"));

        Pattern returnGroupPaddingPattern = Pattern.compile(
                "toggleReturnStatsMode\\.post\\(\\(\\) -> autoFitSegmentButtons\\(\\s*binding\\.toggleReturnStatsMode,\\s*new MaterialButton\\[]\\{\\s*binding\\.btnReturnDay, binding\\.btnReturnMonth, binding\\.btnReturnYear, binding\\.btnReturnStage\\s*},\\s*6\\)\\);",
                Pattern.DOTALL
        );
        assertTrue(returnGroupPaddingPattern.matcher(source).find());
    }

    private static String readProjectFile(String... candidates) throws Exception {
        for (String candidate : candidates) {
            Path path = Paths.get(System.getProperty("user.dir")).resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("未找到源码文件");
    }
}
