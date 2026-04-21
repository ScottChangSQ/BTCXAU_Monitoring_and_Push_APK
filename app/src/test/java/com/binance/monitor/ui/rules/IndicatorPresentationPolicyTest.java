/*
 * 全局展示策略测试，锁定颜色判断和页面迁移期的标签兼容。
 */
package com.binance.monitor.ui.rules;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IndicatorPresentationPolicyTest {

    @Test
    public void presentationPolicyShouldResolveColorFromDefinitionRule() {
        IndicatorPresentation presentation = IndicatorPresentationPolicy.present(
                IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_RETURN_AMOUNT),
                -23.5d,
                false
        );

        assertEquals("累计收益额", presentation.getLabel());
        assertEquals("-$23.50", presentation.getFormattedValue());
        assertEquals(IndicatorColorRule.PROFIT_UP_LOSS_DOWN, presentation.getColorRule());
        assertEquals(IndicatorPresentation.Direction.NEGATIVE, presentation.getDirection());
    }

    @Test
    public void presentationPolicyShouldCanonicalizeLegacyMetricLabel() {
        IndicatorPresentation presentation = IndicatorPresentationPolicy.presentText(
                "Total Return",
                "+12.35%",
                false
        );

        assertEquals("累计收益率", presentation.getLabel());
        assertEquals("+12.35%", presentation.getFormattedValue());
    }
}
