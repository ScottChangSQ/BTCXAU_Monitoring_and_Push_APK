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

        assertTrue(guard.shouldApplyBusSeq(10L));
        assertTrue(guard.shouldApplyBusSeq(10L));
        guard.commitAppliedBusSeq(10L);
        assertFalse(guard.shouldApplyBusSeq(10L));
        assertFalse(guard.shouldApplyBusSeq(9L));
        assertTrue(guard.shouldApplyBusSeq(11L));
    }

    @Test
    public void shouldRejectOlderOrDuplicateMarketSeqAfterNewerTickApplied() {
        V2StreamSequenceGuard guard = new V2StreamSequenceGuard();

        assertTrue(guard.shouldApplyMarketSeq(3L));
        assertTrue(guard.shouldApplyMarketSeq(3L));
        guard.commitAppliedMarketSeq(3L);
        assertFalse(guard.shouldApplyMarketSeq(3L));
        assertFalse(guard.shouldApplyMarketSeq(2L));
        assertTrue(guard.shouldApplyMarketSeq(4L));
    }

    @Test
    public void resetShouldAllowNewConnectionSequenceToStartAgain() {
        V2StreamSequenceGuard guard = new V2StreamSequenceGuard();

        assertTrue(guard.shouldApplyBusSeq(5L));
        guard.commitAppliedBusSeq(5L);
        guard.reset();
        assertTrue(guard.shouldApplyBusSeq(1L));
        assertTrue(guard.shouldApplyMarketSeq(1L));
    }
}
