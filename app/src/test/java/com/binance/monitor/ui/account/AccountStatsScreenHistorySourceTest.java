package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsScreenHistorySourceTest {

    @Test
    public void preloadedRuntimeOnlyCacheShouldNotBlockCanonicalHistoryRefresh() throws Exception {
        String coordinatorSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java",
                "src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java"
        ).replace("\r\n", "\n").replace('\r', '\n');
        String screenSource = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("快照协调器应显式区分“已加载历史段”和“只有运行态”的缓存",
                coordinatorSource.contains("boolean hasRenderableHistorySections(@Nullable AccountSnapshot snapshot);"));
        assertTrue("预加载缓存只有运行态时，不应直接把它当成完整历史快照应用到页面",
                coordinatorSource.contains("if (!host.hasRenderableHistorySections(cache.getSnapshot())) {\n            return;\n        }"));
        assertTrue("账户统计共享页应提供统一的历史段判定入口",
                screenSource.contains("public boolean hasRenderableHistorySections(@Nullable AccountSnapshot snapshot) {\n                return AccountStatsScreen.this.hasRenderableHistorySections(snapshot);\n            }"));
        assertTrue("历史段判定应至少检查成交记录或净值曲线是否已经存在",
                screenSource.contains("private boolean hasRenderableHistorySections(@Nullable AccountSnapshot snapshot) {")
                        && screenSource.contains("return !payload.getTrades().isEmpty() || !payload.getCurvePoints().isEmpty();"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到账户统计源码文件");
    }
}
