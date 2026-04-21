package com.binance.monitor.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class FloatingRevisionRefreshPolicyTest {

    @Test
    public void firstNonImmediateRefreshShouldPass() {
        FloatingRevisionRefreshPolicy policy = new FloatingRevisionRefreshPolicy();

        assertTrue(policy.shouldRefresh(Arrays.asList(3L, 7L), Arrays.asList("btc@1", "xau@1")));
    }

    @Test
    public void sameRevisionSignatureShouldBeSkippedAfterApplied() {
        FloatingRevisionRefreshPolicy policy = new FloatingRevisionRefreshPolicy();

        policy.markApplied(Arrays.asList(3L, 7L), Arrays.asList("btc@1", "xau@1"));

        assertFalse(policy.shouldRefresh(Arrays.asList(3L, 7L), Arrays.asList("btc@1", "xau@1")));
    }

    @Test
    public void changedProductRevisionShouldTriggerRefresh() {
        FloatingRevisionRefreshPolicy policy = new FloatingRevisionRefreshPolicy();

        policy.markApplied(Arrays.asList(3L, 7L), Arrays.asList("btc@1", "xau@1"));

        assertTrue(policy.shouldRefresh(Arrays.asList(3L, 8L), Arrays.asList("btc@1", "xau@1")));
    }

    @Test
    public void emptyVisibleProductsShouldStillDeduplicate() {
        FloatingRevisionRefreshPolicy policy = new FloatingRevisionRefreshPolicy();

        policy.markApplied(Collections.emptyList(), Collections.emptyList());

        assertFalse(policy.shouldRefresh(Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void changedMarketWindowSignatureShouldTriggerRefreshEvenWhenProductRevisionMatches() {
        FloatingRevisionRefreshPolicy policy = new FloatingRevisionRefreshPolicy();

        policy.markApplied(Arrays.asList(3L, 7L), Arrays.asList("btc@1", "xau@1"));

        assertTrue(policy.shouldRefresh(Arrays.asList(3L, 7L), Arrays.asList("btc@2", "xau@1")));
    }
}
