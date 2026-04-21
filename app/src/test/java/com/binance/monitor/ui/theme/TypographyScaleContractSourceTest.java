/*
 * 锁定全局字号真值，只允许 6 档正式字号。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypographyScaleContractSourceTest {

    @Test
    public void stylesShouldCollapseTypographyIntoSixSpLevels() throws Exception {
        String styles = readUtf8("src/main/res/values/styles.xml");

        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.PageHero\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.ValueHero\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.Section\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.Body\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.Compact\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.Dense\""));

        Set<String> actual = extractSpValues(styles);
        Set<String> expected = new LinkedHashSet<>(Arrays.asList("22", "18", "16", "14", "12", "10"));
        assertEquals(expected, actual);
    }

    private static Set<String> extractSpValues(String styles) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("<item name=\"android:textSize\">([0-9.]+)sp</item>").matcher(styles);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
