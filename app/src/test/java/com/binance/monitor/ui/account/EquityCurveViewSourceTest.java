/*
 * 账户曲线视图源码约束测试，确保结余曲线保持白色。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class EquityCurveViewSourceTest {

    @Test
    public void equityCurveViewShouldUseWhiteBalanceLine() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
                "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java"
        );
        assertTrue("结余曲线应改为白色", source.contains("balancePaint.setColor(Color.WHITE)"));
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
