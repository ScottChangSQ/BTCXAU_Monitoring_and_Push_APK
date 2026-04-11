/*
 * v2 stream 顺序守卫测试，确保旧 busSeq 不会回写当前主链状态。
 */
package com.binance.monitor.service.stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class V2StreamSequenceGuardTest {

    @Test
    public void shouldRejectOlderOrDuplicateBusSeqAfterNewerMessageApplied() {
        V2StreamSequenceGuard guard = new V2StreamSequenceGuard();

        assertTrue(guard.shouldApply(10L));
        assertFalse(guard.shouldApply(10L));
        assertFalse(guard.shouldApply(9L));
        assertTrue(guard.shouldApply(11L));
    }

    @Test
    public void resetShouldAllowNewConnectionSequenceToStartAgain() {
        V2StreamSequenceGuard guard = new V2StreamSequenceGuard();

        assertTrue(guard.shouldApply(5L));
        guard.reset();
        assertTrue(guard.shouldApply(1L));
    }
}
