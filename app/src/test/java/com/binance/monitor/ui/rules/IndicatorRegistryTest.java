/*
 * 全局指标注册表测试，锁定首批正式指标和名称映射合同。
 */
package com.binance.monitor.ui.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

public class IndicatorRegistryTest {

    @Test
    public void indicatorRegistryShouldExposeCanonicalIds() {
        assertNotNull(IndicatorId.ACCOUNT_TOTAL_ASSET);
        assertNotNull(IndicatorId.ACCOUNT_TOTAL_RETURN_RATE);
        assertNotNull(IndicatorId.ACCOUNT_MAX_DRAWDOWN);
        assertNotNull(IndicatorId.TRADE_WIN_RATE);
    }

    @Test
    public void indicatorDefinitionShouldKeepCoreContractFields() {
        IndicatorDefinition definition = new IndicatorDefinition(
                IndicatorId.ACCOUNT_TOTAL_RETURN_RATE,
                "累计收益率",
                "累计收益率",
                IndicatorCategory.RETURN,
                IndicatorValueType.PERCENT,
                "%",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN
        );

        assertEquals("累计收益率", definition.getDisplayName());
        assertEquals(2, definition.getPrecision());
        assertEquals(IndicatorColorRule.PROFIT_UP_LOSS_DOWN, definition.getColorRule());
    }

    @Test
    public void registryShouldExposeCoreAccountAndTradeIndicators() {
        assertEquals("总资产", IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_ASSET).getDisplayName());
        assertEquals("累计收益率", IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_RETURN_RATE).getDisplayName());
        assertEquals("最大回撤", IndicatorRegistry.require(IndicatorId.ACCOUNT_MAX_DRAWDOWN).getDisplayName());
        assertEquals("胜率", IndicatorRegistry.require(IndicatorId.TRADE_WIN_RATE).getDisplayName());
    }

    @Test
    public void registryShouldResolveLegacyAliasToCanonicalDisplayName() {
        assertEquals("净资产", IndicatorRegistry.findByDisplayName("Net Asset").getDisplayName());
        assertEquals("累计收益额", IndicatorRegistry.findByDisplayName("累计盈亏").getDisplayName());
    }
}
