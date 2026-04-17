/*
 * 底部导航图标映射测试，确保账户与分析入口使用独立图标资源。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BottomNavIconSourceTest {

    // 校验主壳底部导航的账户/分析图标不再共用同一资源。
    @Test
    public void hostBottomNavShouldUseDedicatedAccountAndAnalysisIcons() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java",
                "src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java"
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("} else if (viewId == R.id.tabAccount) {\n            iconRes = R.drawable.ic_nav_account;"));
        assertTrue(source.contains("} else if (viewId == R.id.tabAnalysis) {\n            iconRes = R.drawable.ic_nav_analysis;"));
        assertTrue(Files.exists(resolvePath(
                "app/src/main/res/drawable/ic_nav_account.xml",
                "src/main/res/drawable/ic_nav_account.xml"
        )));
        assertTrue(Files.exists(resolvePath(
                "app/src/main/res/drawable/ic_nav_analysis.xml",
                "src/main/res/drawable/ic_nav_analysis.xml"
        )));
    }

    // 按 UTF-8 读取源码，兼容从仓库根目录和 app 目录执行测试。
    private static String readUtf8(String... relativePaths) throws Exception {
        Path path = resolvePath(relativePaths);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    // 解析存在的候选路径，兼容不同测试工作目录。
    private static Path resolvePath(String... relativePaths) {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String relativePath : relativePaths) {
            Path path = workingDir.resolve(relativePath).normalize();
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new IllegalStateException("找不到目标文件");
    }
}
