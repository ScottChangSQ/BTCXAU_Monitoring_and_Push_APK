/*
 * 账户概览标题辅助逻辑测试，确保标题优先显示数字账号而不是英文账户名。
 */
package com.binance.monitor.ui.account;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccountOverviewTitleHelperTest {

    @Test
    public void shouldPreferNumericAccountForOverviewTitle() {
        String displayAccount = AccountOverviewTitleHelper.resolveDisplayAccount("7400048", "Demo Account", "7400048");

        assertEquals("7400048", displayAccount);
    }

    @Test
    public void shouldFallbackToDefaultAccountWhenConnectedAccountEmpty() {
        String displayAccount = AccountOverviewTitleHelper.resolveDisplayAccount("", "Demo Account", "7400048");

        assertEquals("7400048", displayAccount);
    }
}
