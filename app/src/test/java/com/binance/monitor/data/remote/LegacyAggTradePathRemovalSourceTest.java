package com.binance.monitor.data.remote;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LegacyAggTradePathRemovalSourceTest {

    @Test
    public void legacyAggTradeAssemblerFilesShouldBeRemoved() {
        assertFalse("旧 aggTrade 成交模型应从生产代码中移除",
                exists("app/src/main/java/com/binance/monitor/data/model/TradeTickData.java",
                        "src/main/java/com/binance/monitor/data/model/TradeTickData.java"));
        assertFalse("旧 aggTrade 本地拼 K 组装器应从生产代码中移除",
                exists("app/src/main/java/com/binance/monitor/data/remote/RealtimeMinuteKlineAssembler.java",
                        "src/main/java/com/binance/monitor/data/remote/RealtimeMinuteKlineAssembler.java"));
    }

    private static boolean exists(String... candidates) {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            if (Files.exists(workingDir.resolve(candidate).normalize())) {
                return true;
            }
        }
        return false;
    }
}
