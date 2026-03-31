/*
 * 验证设置页缓存分类逻辑会把不同清理动作映射到正确的数据范围，
 * 避免“清理历史交易”时误删历史行情，或反过来误删交易历史。
 */
package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CacheSectionClassifierTest {

    // 清理历史行情时，只应包含行情表，不应包含交易表。
    @Test
    public void historyMarketSelectionOnlyTargetsMarketData() {
        CacheSectionClassifier.CacheSelection selection =
                CacheSectionClassifier.fromSelection(true, false, false);

        assertTrue(selection.shouldClearHistoryMarket());
        assertFalse(selection.shouldClearHistoryTrade());
        assertFalse(selection.shouldClearRuntime());
    }

    // 清理历史交易时，只应包含交易表，不应包含行情表。
    @Test
    public void historyTradeSelectionOnlyTargetsTradeData() {
        CacheSectionClassifier.CacheSelection selection =
                CacheSectionClassifier.fromSelection(false, true, false);

        assertFalse(selection.shouldClearHistoryMarket());
        assertTrue(selection.shouldClearHistoryTrade());
        assertFalse(selection.shouldClearRuntime());
    }
}
