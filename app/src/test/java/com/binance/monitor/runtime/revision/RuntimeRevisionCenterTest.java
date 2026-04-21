/*
 * revision 中心测试，锁定不同真值域的独立递增与签名去重规则。
 */
package com.binance.monitor.runtime.revision;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RuntimeRevisionCenterTest {

    @Test
    public void advanceIfChangedShouldSkipDuplicateSignature() {
        RuntimeRevisionCenter center = new RuntimeRevisionCenter();

        long first = center.advanceIfChanged(RuntimeRevisionCenter.RevisionType.MARKET_BASE, "same");
        long second = center.advanceIfChanged(RuntimeRevisionCenter.RevisionType.MARKET_BASE, "same");
        long third = center.advanceIfChanged(RuntimeRevisionCenter.RevisionType.MARKET_BASE, "next");

        assertEquals(1L, first);
        assertEquals(1L, second);
        assertEquals(2L, third);
    }

    @Test
    public void revisionTypesShouldAdvanceIndependently() {
        RuntimeRevisionCenter center = new RuntimeRevisionCenter();

        long marketBase = center.advanceIfChanged(RuntimeRevisionCenter.RevisionType.MARKET_BASE, "base-1");
        long accountRuntime = center.advanceIfChanged(RuntimeRevisionCenter.RevisionType.ACCOUNT_RUNTIME, "account-1");
        long productRuntime = center.advanceIfChanged(RuntimeRevisionCenter.RevisionType.PRODUCT_RUNTIME, "product-1");

        assertEquals(1L, marketBase);
        assertEquals(1L, accountRuntime);
        assertEquals(1L, productRuntime);
        assertEquals(1L, center.current(RuntimeRevisionCenter.RevisionType.MARKET_BASE));
        assertEquals(1L, center.current(RuntimeRevisionCenter.RevisionType.ACCOUNT_RUNTIME));
        assertEquals(1L, center.current(RuntimeRevisionCenter.RevisionType.PRODUCT_RUNTIME));
    }

    @Test
    public void snapshotShouldExposeAllFiveCanonicalRevisions() {
        RuntimeRevisionCenter center = new RuntimeRevisionCenter();
        center.advance(RuntimeRevisionCenter.RevisionType.MARKET_BASE);
        center.advance(RuntimeRevisionCenter.RevisionType.MARKET_WINDOW);
        center.advance(RuntimeRevisionCenter.RevisionType.ACCOUNT_RUNTIME);
        center.advance(RuntimeRevisionCenter.RevisionType.ACCOUNT_HISTORY);
        center.advance(RuntimeRevisionCenter.RevisionType.PRODUCT_RUNTIME);

        RuntimeRevisionCenter.RevisionSnapshot snapshot = center.snapshot();

        assertEquals(1L, snapshot.getMarketBaseRevision());
        assertEquals(1L, snapshot.getMarketWindowRevision());
        assertEquals(1L, snapshot.getAccountRuntimeRevision());
        assertEquals(1L, snapshot.getAccountHistoryRevision());
        assertEquals(1L, snapshot.getProductRuntimeRevision());
    }
}
