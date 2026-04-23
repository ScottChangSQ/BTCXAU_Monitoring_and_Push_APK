/*
 * 账户概览指标帮助类测试，确保固定展示顺序符合账户持仓页最新口径。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.ui.rules.IndicatorId;
import com.binance.monitor.ui.rules.IndicatorRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public class AccountOverviewMetricsHelperTest {

    // 账户总览不再展示累计盈亏与累计收益率，首屏只保留当前账户核心状态。
    @Test
    public void buildOverviewMetricsShouldExcludeCumulativeMetrics() {
        List<AccountMetric> overview = Arrays.asList(
                new AccountMetric("保证金", "$300.00"),
                new AccountMetric("总资产", "$1000.00"),
                new AccountMetric("净值", "$920.00"),
                new AccountMetric("可用预付款", "$620.00"),
                new AccountMetric("累计盈亏", "+$88.00"),
                new AccountMetric("累计收益率", "+8.80%"),
                new AccountMetric("持仓盈亏", "+$20.00"),
                new AccountMetric("持仓收益率", "+2.00%")
        );

        List<AccountMetric> result = AccountOverviewMetricsHelper.buildOverviewMetrics(
                overview,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                1710000000000L,
                TimeZone.getDefault()
        );

        assertEquals(IndicatorRegistry.require(IndicatorId.ACCOUNT_TOTAL_ASSET).getDisplayName(), result.get(0).getName());
        assertEquals(IndicatorRegistry.require(IndicatorId.ACCOUNT_NET_ASSET).getDisplayName(), result.get(1).getName());
        assertEquals(IndicatorRegistry.require(IndicatorId.ACCOUNT_AVAILABLE_FUNDS).getDisplayName(), result.get(2).getName());
        assertEquals(IndicatorRegistry.require(IndicatorId.ACCOUNT_MARGIN).getDisplayName(), result.get(3).getName());
        assertEquals(IndicatorRegistry.require(IndicatorId.ACCOUNT_POSITION_PNL).getDisplayName(), result.get(4).getName());
        assertEquals(IndicatorRegistry.require(IndicatorId.ACCOUNT_POSITION_PNL_RATE).getDisplayName(), result.get(5).getName());
        assertEquals("$620.00", result.get(2).getValue());
        assertEquals(6, result.size());
    }
}
