/*
 * 方角按钮主体层级测试，只锁旧按钮语义向 ActionButton 层级收口的最小合同。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class SquareButtonStyleResourceTest {

    @Test
    public void stylesShouldKeepActionButtonHierarchy() throws Exception {
        String styles = readUtf8(
                "app/src/main/res/values/styles.xml",
                "src/main/res/values/styles.xml"
        );

        assertMatches(
                styles,
                "Widget\\.BinanceMonitor\\.Subject\\.ActionButton\\.Primary\"\\s+parent=\"Widget\\.BinanceMonitor\\.Subject\\.ActionButton\\.Base"
        );
        assertMatches(
                styles,
                "Widget\\.BinanceMonitor\\.Subject\\.ActionButton\\.Secondary\"\\s+parent=\"Widget\\.BinanceMonitor\\.Subject\\.ActionButton\\.Base"
        );
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("未找到资源文件");
    }

    private static void assertMatches(String text, String regex) {
        assertTrue(Pattern.compile(regex).matcher(text).find());
    }
}
