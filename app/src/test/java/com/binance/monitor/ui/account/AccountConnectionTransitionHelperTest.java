/*
 * 账户连接状态过渡辅助工具的单元测试。
 * 负责校验登录成功提示只在真正连通时触发一次。
 */
package com.binance.monitor.ui.account;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountConnectionTransitionHelperTest {

    @Test
    // 连通状态第一次建立时应显示成功提示。
    public void shouldShowLoginSuccessWhenConnectionTurnsOnlineAfterLogin() {
        assertTrue(AccountConnectionTransitionHelper.shouldShowLoginSuccess(false, true, true));
    }

    @Test
    // 已连接后的普通刷新不应重复提示。
    public void shouldNotShowLoginSuccessWhenAlreadyConnected() {
        assertFalse(AccountConnectionTransitionHelper.shouldShowLoginSuccess(true, true, true));
    }

    @Test
    // 未登录时即便网络连通也不应提示登录成功。
    public void shouldNotShowLoginSuccessWhenUserIsNotLoggedIn() {
        assertFalse(AccountConnectionTransitionHelper.shouldShowLoginSuccess(false, true, false));
    }

    @Test
    // 仍未连通时不应提示。
    public void shouldNotShowLoginSuccessWhenStillOffline() {
        assertFalse(AccountConnectionTransitionHelper.shouldShowLoginSuccess(false, false, true));
    }
}
