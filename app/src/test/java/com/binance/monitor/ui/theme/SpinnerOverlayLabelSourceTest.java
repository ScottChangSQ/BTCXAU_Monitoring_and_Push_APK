/*
 * 选择字段主体边界测试，只防止单独长出第 8 类 Spinner 主体。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SpinnerOverlayLabelSourceTest {

    @Test
    public void selectFieldLabelBoundaryShouldNotSplitIntoSubjectSpinner() throws Exception {
        String styles = readUtf8("src/main/res/values/styles.xml");
        String anchor = readUtf8("src/main/res/layout/item_spinner_filter_anchor.xml");
        String item = readUtf8("src/main/res/layout/item_spinner_filter.xml");
        String dropdown = readUtf8("src/main/res/layout/item_spinner_filter_dropdown.xml");

        assertFalse(styles.contains("Widget.BinanceMonitor.Subject.Spinner"));
        assertTrue(anchor.contains("Widget.BinanceMonitor.Subject.SelectField.Label"));
        assertTrue(item.contains("Widget.BinanceMonitor.Subject.SelectField.DropdownItem"));
        assertTrue(dropdown.contains("Widget.BinanceMonitor.Subject.SelectField.DropdownItem"));
        assertFalse(anchor.contains("Widget.BinanceMonitor.Spinner.AnchorItem"));
        assertFalse(item.contains("Widget.BinanceMonitor.Spinner.DropdownItem"));
        assertFalse(dropdown.contains("Widget.BinanceMonitor.Spinner.DropdownItem"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
