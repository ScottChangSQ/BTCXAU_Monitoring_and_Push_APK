/*
 * 账户概览标题辅助类，统一决定顶部标题展示哪个账号值。
 * 账户总览标题固定优先显示数字账号，避免中英文账户名来回切换。
 */
package com.binance.monitor.ui.account;

final class AccountOverviewTitleHelper {

    private AccountOverviewTitleHelper() {
    }

    // 优先显示已连接的数字账号，缺失时回退到默认账号。
    static String resolveDisplayAccount(String connectedAccount, String connectedAccountName, String defaultAccount) {
        String account = safe(connectedAccount);
        if (!account.isEmpty()) {
            return account;
        }
        String fallback = safe(defaultAccount);
        if (!fallback.isEmpty()) {
            return fallback;
        }
        return safe(connectedAccountName);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
