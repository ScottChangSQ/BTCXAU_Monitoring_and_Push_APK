package com.binance.monitor.ui.market;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MarketMonitorLayoutResourceTest {

    @Test
    public void legacyTradingBridgeLayoutShouldNotKeepVolumeAndAmountModules() throws Exception {
        String xml = readUtf8(
                "app/src/main/res/layout/activity_main.xml",
                "src/main/res/layout/activity_main.xml"
        );

        assertFalse("交易页旧桥接布局不应继续保留成交量模块",
                xml.contains("android:id=\"@+id/layoutMetricVolume\""));
        assertFalse("交易页旧桥接布局不应继续保留成交额模块",
                xml.contains("android:id=\"@+id/layoutMetricAmount\""));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到布局文件");
    }
}
