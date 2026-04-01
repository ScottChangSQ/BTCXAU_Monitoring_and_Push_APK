/*
 * 账户快照请求守卫测试，确保退出登录后旧请求回包不会把账户旧数据写回界面。
 */
package com.binance.monitor.ui.account;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccountSnapshotRequestGuardTest {

    @Test
    // 当前会话内发起的请求回包应允许落地。
    public void shouldAcceptResponseFromCurrentSession() {
        AccountSnapshotRequestGuard guard = new AccountSnapshotRequestGuard();

        AccountSnapshotRequestGuard.RequestToken token = guard.openRequest();

        assertTrue(guard.shouldApply(token));
    }

    @Test
    // 会话失效后，旧请求回包必须被丢弃，避免退出登录后旧数据回灌。
    public void shouldRejectResponseAfterSessionInvalidated() {
        AccountSnapshotRequestGuard guard = new AccountSnapshotRequestGuard();

        AccountSnapshotRequestGuard.RequestToken staleToken = guard.openRequest();
        guard.invalidateSession();

        assertFalse(guard.shouldApply(staleToken));
    }
}
