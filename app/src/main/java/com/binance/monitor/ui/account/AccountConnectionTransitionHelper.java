/*
 * 账户连接状态过渡辅助工具。
 * 负责判断是否需要展示一次性的登录成功提示动画。
 */
package com.binance.monitor.ui.account;

public final class AccountConnectionTransitionHelper {

    private AccountConnectionTransitionHelper() {
    }

    // 只有已登录且连接状态从未连接切到已连接时，才展示登录成功提示。
    public static boolean shouldShowLoginSuccess(boolean previousConnected,
                                                 boolean currentConnected,
                                                 boolean userLoggedIn) {
        return userLoggedIn && !previousConnected && currentConnected;
    }
}
