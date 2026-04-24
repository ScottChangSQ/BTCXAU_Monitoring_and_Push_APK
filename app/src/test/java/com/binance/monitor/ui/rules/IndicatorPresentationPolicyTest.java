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

    @Test
    public void presentationPolicyShouldNormalizePositiveDrawdownIntoNegativeDisplay() {
        IndicatorPresentation presentation = IndicatorPresentationPolicy.present(
                IndicatorRegistry.require(IndicatorId.ACCOUNT_MAX_DRAWDOWN),
                0.1234d,
                false
        );

        assertEquals("最大回撤", presentation.getLabel());
        assertEquals("-12.34%", presentation.getFormattedValue());
        assertEquals(IndicatorPresentation.Direction.NEGATIVE, presentation.getDirection());
    }

    @Test
    public void presentationPolicyShouldNormalizeLegacyPositiveDrawdownText() {
        IndicatorPresentation presentation = IndicatorPresentationPolicy.presentText(
                "最大回撤",
                "+12.34%",
                false
        );

        assertEquals("最大回撤", presentation.getLabel());
        assertEquals("-12.34%", presentation.getFormattedValue());
        assertEquals(IndicatorPresentation.Direction.NEGATIVE, presentation.getDirection());
    }

    @Test
    public void presentationPolicyShouldKeepAverageLossAsNegativeDirection() {
        IndicatorPresentation presentation = IndicatorPresentationPolicy.present(
                IndicatorRegistry.require(IndicatorId.TRADE_AVG_LOSS),
                23.5d,
                false
        );

        assertEquals("平均每笔亏损", presentation.getLabel());
        assertEquals("-$23.50", presentation.getFormattedValue());
        assertEquals(IndicatorPresentation.Direction.NEGATIVE, presentation.getDirection());
    }

    @Test
    public void presentationPolicyShouldKeepWinRateNeutralAndTintMonthReturn() {
        IndicatorPresentation winRatePresentation = IndicatorPresentationPolicy.presentText(
                "胜率",
                "+57.00%",
                false
        );
        IndicatorPresentation monthReturnPresentation = IndicatorPresentationPolicy.presentText(
                "本月收益",
                "+$123.45",
                false
        );

        assertEquals(IndicatorPresentation.Direction.NONE, winRatePresentation.getDirection());
        assertEquals(IndicatorPresentation.Direction.POSITIVE, monthReturnPresentation.getDirection());
        assertEquals("+$123.45", monthReturnPresentation.getFormattedValue());
    }
}
