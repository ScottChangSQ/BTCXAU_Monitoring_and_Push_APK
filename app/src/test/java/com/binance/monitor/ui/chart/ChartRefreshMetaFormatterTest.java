/*
 * 倒计时与延迟文案格式化测试，确保右上角显示符合页面约定。
 */
package com.binance.monitor.ui.chart;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ChartRefreshMetaFormatterTest {

    @Test
    public void buildLatencyOnlyText_showsPlaceholder_whenNoSuccessfulRequestYet() {
        String text = ChartRefreshMetaFormatter.buildLatencyOnlyText(-1L);

        assertEquals("--ms", text);
    }

    @Test
    public void buildLatencyOnlyText_showsLatency_whenRequestSucceeded() {
        String text = ChartRefreshMetaFormatter.buildLatencyOnlyText(128L);

        assertEquals("128ms", text);
    }

    @Test
    public void buildCountdownText_appendsLatency_whenCountdownIsEnabled() {
        String text = ChartRefreshMetaFormatter.buildCountdownText(
                6_000L,
                1_000L,
                5_000L,
                128L
        );

        assertEquals("5秒/5秒 128ms", text);
    }
}
