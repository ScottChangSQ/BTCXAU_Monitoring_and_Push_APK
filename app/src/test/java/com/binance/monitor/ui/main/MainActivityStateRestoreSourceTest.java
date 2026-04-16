package com.binance.monitor.ui.main;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainActivityStateRestoreSourceTest {

    @Test
    public void mainActivityShouldStayAsStatelessBridgeEntry() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java"
        );

        assertTrue("桥接页应只保留 onCreate 即时转发入口",
                source.contains("protected void onCreate(@Nullable Bundle savedInstanceState)"));
        assertFalse("桥接页不应继续保留旧的品种状态恢复 key",
                source.contains("STATE_SELECTED_SYMBOL"));
        assertFalse("桥接页不应继续保留旧的状态恢复函数",
                source.contains("restoreSelectedSymbol("));
        assertFalse("桥接页不应继续覆写 onSaveInstanceState",
                source.contains("onSaveInstanceState("));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 MainActivity.java");
    }
}
