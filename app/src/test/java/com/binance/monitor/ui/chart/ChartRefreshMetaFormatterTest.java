/*
 * 倒计时与延迟文案格式化测试，确保右上角显示符合页面约定。
 */
package com.binance.monitor.ui.chart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChartRefreshMetaFormatterTest {

    @Test
    public void buildCountdownText_showsPlaceholderLatency_whenNoSuccessfulRequestYet() {
        String text = ChartRefreshMetaFormatter.buildCountdownText(
                0L,
                1_000L,
                5_000L,
                -1L
        );

        assertEquals("--秒/5秒 --ms", text);
    }

    @Test
    public void buildCountdownText_appendsLatency_whenRequestSucceeded() {
        String text = ChartRefreshMetaFormatter.buildCountdownText(
                6_000L,
                1_000L,
                5_000L,
                128L
        );

        assertEquals("5秒/5秒 128ms", text);
    }
}
