package com.binance.monitor.ui.account;

import com.binance.monitor.util.SensitiveDisplayMasker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccountStatsPrivacyFormatterTest {

    @Test
    public void formatOverviewTitle_returnsMaskedAccount_whenPrivacyEnabled() {
        assertEquals("账户-" + SensitiveDisplayMasker.MASK_TEXT,
                AccountStatsPrivacyFormatter.formatOverviewTitle("7400048", true));
    }

    @Test
    public void formatOverviewTitle_returnsOriginalAccount_whenPrivacyDisabled() {
        assertEquals("账户-7400048",
                AccountStatsPrivacyFormatter.formatOverviewTitle("7400048", false));
    }

    @Test
    public void formatRefreshMeta_replacesValue_whenPrivacyEnabled() {
        assertEquals("更新时间 " + SensitiveDisplayMasker.MASK_TEXT,
                AccountStatsPrivacyFormatter.formatRefreshMeta("09:30 / 5s", true));
    }

    @Test
    public void maskValue_returnsMaskText_forReturnTableValue_whenPrivacyEnabled() {
        assertEquals(SensitiveDisplayMasker.MASK_TEXT,
                AccountStatsPrivacyFormatter.maskValue("+12.43%", true));
    }

    @Test
    public void resolveValueColor_returnsNeutralColor_whenPrivacyEnabled() {
        assertEquals(11,
                AccountStatsPrivacyFormatter.resolveValueColor(22, 11, true));
    }

    @Test
    public void resolveValueColor_returnsActualColor_whenPrivacyDisabled() {
        assertEquals(22,
                AccountStatsPrivacyFormatter.resolveValueColor(22, 11, false));
    }
}
