/*
 * 账户历史补拉 gate 测试，确保并发补拉只允许一条执行，其余 revision 只排队最新值。
 */
package com.binance.monitor.runtime.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AccountHistoryRefreshGateTest {

    @Test
    public void tryStartShouldQueueLatestRevisionWhenRefreshAlreadyInFlight() {
        AccountHistoryRefreshGate gate = new AccountHistoryRefreshGate();

        AccountHistoryRefreshGate.StartDecision first = gate.tryStart("rev-1");
        AccountHistoryRefreshGate.StartDecision second = gate.tryStart("rev-2");

        assertTrue(first.shouldStart());
        assertFalse(second.shouldStart());
        AccountHistoryRefreshGate.FinishDecision finish = gate.finish("rev-1");
        assertTrue(finish.shouldContinue());
        assertEquals("rev-2", finish.getNextRevision());
    }

    @Test
    public void finishShouldDropSameRevisionReplay() {
        AccountHistoryRefreshGate gate = new AccountHistoryRefreshGate();

        gate.tryStart("rev-3");
        gate.tryStart("rev-3");
        AccountHistoryRefreshGate.FinishDecision finish = gate.finish("rev-3");

        assertFalse(finish.shouldContinue());
        assertEquals("", finish.getNextRevision());
    }
}
