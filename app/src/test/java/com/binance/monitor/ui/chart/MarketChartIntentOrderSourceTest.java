/*
 * 锁定图表页首次初始化时的 runtime 绑定顺序，避免 intent 早于 runtime attach 被消费。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketChartIntentOrderSourceTest {

    @Test
    public void marketChartFragmentShouldAttachPageRuntimeBeforeFirstIntentConsumption() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java");

        int attachIndex = source.indexOf("screen.attachPageRuntime(pageRuntime);");
        int firstIntentIndex = source.indexOf("screen.onNewIntent(requireActivity().getIntent());");

        assertTrue("源码中必须存在 runtime attach", attachIndex >= 0);
        assertTrue("源码中必须存在首次 intent 消费", firstIntentIndex >= 0);
        assertTrue("首次 intent 消费必须发生在 runtime attach 之后", attachIndex < firstIntentIndex);
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
