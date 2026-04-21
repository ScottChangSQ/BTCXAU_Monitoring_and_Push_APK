/*
 * 锁定 7 类标准主体的资源合同，防止后续实现继续沿用旧主体命名或引入第 8 类主体。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class StandardSubjectContractSourceTest {

    @Test
    public void stylesShouldExposeStandardSubjectStyleEntries() throws Exception {
        String styles = readUtf8("src/main/res/values/styles.xml");

        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.ActionButton.Base"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.ActionButton.Primary"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.ActionButton.Secondary"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.TextTrigger"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.TextTrigger.Compact"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.SegmentedOption"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.SelectField.Label"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.SelectField.DropdownItem"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.InputField"));
        assertTrue(styles.contains("Widget.BinanceMonitor.Subject.ToggleChoice"));
    }

    @Test
    public void pickerWheelShouldStayOnDedicatedSubjectSource() throws Exception {
        String pickerWheel = readUtf8("src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java");

        assertTrue(pickerWheel.matches("(?s).*\\bThemedNumberPicker\\b.*"));
        assertTrue(pickerWheel.matches("(?s).*\\bapplyThemeTextStyle\\s*\\(.*"));
    }

    @Test
    public void themesShouldKeepSingleDarkThemeEntryForStandardSubjects() throws Exception {
        String themes = readUtf8("src/main/res/values/themes.xml");

        assertMatches(themes, "Theme\\.BinanceMonitor\"\\s+parent=\"Theme\\.Material3\\.Dark\\.NoActionBar");
        assertMatches(themes, "materialButtonStyle\">\\s*@style/Widget\\.BinanceMonitor\\.Subject\\.ActionButton\\.Primary");
        assertMatches(themes, "materialButtonOutlinedStyle\">\\s*@style/Widget\\.BinanceMonitor\\.Subject\\.ActionButton\\.Secondary");
        assertMatches(themes, "textInputStyle\">\\s*@style/Widget\\.BinanceMonitor\\.Subject\\.InputField");
        assertFalse(themes.contains("Theme.Material3.Light"));
        assertFalse(themes.contains("Theme.BinanceMonitor.Light"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static void assertMatches(String text, String regex) {
        assertTrue(Pattern.compile(regex).matcher(text).find());
    }
}
