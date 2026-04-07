/*
 * 远程会话状态机测试，负责锁定 accepted/syncing/active/failed 的状态边界。
 * 这些测试用于防止页面把“已受理”误显示成“已完成切换”。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountSessionStateMachineTest {

    @Test
    public void acceptedFlowShouldStaySyncingUntilSnapshotArrives() {
        AccountSessionStateMachine machine = new AccountSessionStateMachine();

        machine.moveTo(AccountSessionStateMachine.AccountSessionUiState.ENCRYPTING, "正在安全加密");
        machine.moveTo(AccountSessionStateMachine.AccountSessionUiState.SUBMITTING, "正在提交登录");
        machine.markSyncing("acct-2", "正在同步账户数据");

        AccountSessionStateMachine.StateSnapshot syncing = machine.snapshot();
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.SYNCING, syncing.getState());
        assertEquals("acct-2", syncing.getActiveProfileId());
        assertTrue(syncing.isAwaitingSync());

        machine.markActive("acct-2", "账户数据已对齐");

        AccountSessionStateMachine.StateSnapshot active = machine.snapshot();
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.ACTIVE, active.getState());
        assertFalse(active.isAwaitingSync());
        assertEquals("acct-2", active.getActiveProfileId());
    }

    @Test
    public void failShouldKeepLatestMessageAndStopAwaitingSync() {
        AccountSessionStateMachine machine = new AccountSessionStateMachine();

        machine.markSyncing("acct-3", "正在同步");
        machine.markFailed("同步失败");

        AccountSessionStateMachine.StateSnapshot failed = machine.snapshot();
        assertEquals(AccountSessionStateMachine.AccountSessionUiState.FAILED, failed.getState());
        assertEquals("同步失败", failed.getMessage());
        assertFalse(failed.isAwaitingSync());
    }
}
