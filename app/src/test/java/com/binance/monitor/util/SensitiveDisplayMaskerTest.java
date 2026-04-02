package com.binance.monitor.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SensitiveDisplayMaskerTest {

    @Test
    public void maskAccount_returnsMaskText_whenPrivacyEnabled() {
        assertEquals(SensitiveDisplayMasker.MASK_TEXT, SensitiveDisplayMasker.maskAccount("7400048", true));
    }

    @Test
    public void maskPrice_returnsOriginalText_whenPrivacyDisabled() {
        assertEquals("$123.45", SensitiveDisplayMasker.maskPrice("$123.45", false));
    }

    @Test
    public void maskQuantity_returnsMaskText_whenPrivacyEnabled() {
        assertEquals(SensitiveDisplayMasker.MASK_TEXT, SensitiveDisplayMasker.maskQuantity("2.50 手", true));
    }

    @Test
    public void maskAccountId_returnsMaskText_whenPrivacyEnabled() {
        assertEquals(SensitiveDisplayMasker.MASK_TEXT, SensitiveDisplayMasker.maskAccountId("7400048", true));
    }

    @Test
    public void maskSignedValue_returnsOriginalText_whenPrivacyDisabled() {
        assertEquals("-$381", SensitiveDisplayMasker.maskSignedValue("-$381", false));
    }

    @Test
    public void maskAmount_keepsPlaceholder_whenValueMissing() {
        assertEquals("--", SensitiveDisplayMasker.maskAmount("--", true));
    }
}
