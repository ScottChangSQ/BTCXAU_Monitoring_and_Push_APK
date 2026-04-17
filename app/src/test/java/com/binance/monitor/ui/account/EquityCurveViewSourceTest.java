/*
 * 账户曲线视图源码约束测试，确保曲线和回撤高亮跟随当前主题 palette。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EquityCurveViewSourceTest {

    @Test
    public void equityCurveViewShouldUsePaletteDrivenBalanceAndDrawdownColors() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
                "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java"
        );

        assertTrue("结余线应跟随主题主文字色，而不是固定白色",
                source.contains("balancePaint.setColor(applyAlpha(palette.textPrimary"));
        assertTrue("回撤高亮应跟随主题 warning/xau 色",
                source.contains("drawdownStrokePaint.setColor(applyAlpha(palette.xau"));
        assertTrue("回撤填充应跟随主题 warning/xau 色",
                source.contains("drawdownFillPaint.setColor(applyAlpha(palette.xau"));
        assertTrue("回撤峰值标记应跟随主题警示色弱化层",
                source.contains("drawdownPeakMarkerPaint.setColor(blendColor("));
        assertFalse("结余线不能再固定写死白色",
                source.contains("balancePaint.setColor(Color.WHITE)"));
        assertFalse("回撤高亮不能再保留硬编码色值",
                source.contains("0x33FFD54F"));
        assertFalse("回撤高亮不能再保留硬编码色值",
                source.contains("0xFFFFB300"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 EquityCurveView.java");
    }
}
