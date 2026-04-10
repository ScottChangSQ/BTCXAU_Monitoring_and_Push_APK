package com.binance.monitor.ui.main;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainActivityStateRestoreSourceTest {

    @Test
    public void mainActivityShouldRestoreSelectedSymbolFromSavedInstanceState() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/main/MainActivity.java",
                "src/main/java/com/binance/monitor/ui/main/MainActivity.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue("主界面应声明可恢复的当前品种 key",
                source.contains("private static final String STATE_SELECTED_SYMBOL = \"state_selected_symbol\";"));
        assertTrue("onCreate 应先从 savedInstanceState 恢复当前品种，再加载配置",
                source.contains("restoreSelectedSymbol(savedInstanceState);")
                        && source.contains("loadSymbolConfig(selectedSymbol);"));
        assertTrue("主界面应提供统一的当前品种恢复入口",
                source.contains("private void restoreSelectedSymbol(@Nullable Bundle savedInstanceState) {"));
        assertTrue("主界面销毁重建时应把当前品种写回 Bundle",
                source.contains("protected void onSaveInstanceState(@NonNull Bundle outState) {")
                        && source.contains("outState.putString(STATE_SELECTED_SYMBOL, selectedSymbol);"));
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
