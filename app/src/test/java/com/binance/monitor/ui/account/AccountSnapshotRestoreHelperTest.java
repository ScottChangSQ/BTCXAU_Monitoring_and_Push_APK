/*
 * 账户快照恢复逻辑测试，确保预加载快照缺少交易记录时仍能先展示本地留存数据。
 */
package com.binance.monitor.ui.account;

import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AccountSnapshotRestoreHelperTest {

    @Test
    public void shouldUseStoredTradesWhenPreloadSnapshotHasNone() {
        AccountSnapshot preloadSnapshot = new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
        TradeRecordItem storedTrade = new TradeRecordItem(
                1L, "BTC", "BTCUSD", "Buy", 1d, 1d, 1d, 0d, "",
                10d, 1L, 2L, 0d
        );
        AccountStorageRepository.StoredSnapshot storedSnapshot = buildStoredSnapshot(
                Collections.singletonList(storedTrade)
        );

        AccountSnapshot merged = AccountSnapshotRestoreHelper.mergeMissingTrades(preloadSnapshot, storedSnapshot);

        assertEquals(1, merged.getTrades().size());
        assertEquals("BTCUSD", merged.getTrades().get(0).getCode());
    }

    @Test
    public void shouldKeepPreloadTradesWhenTheyAlreadyExist() {
        TradeRecordItem preloadTrade = new TradeRecordItem(
                3L, "XAU", "XAUUSD", "Sell", 2d, 1d, 2d, 0d, "",
                -5d, 3L, 4L, 0d
        );
        AccountSnapshot preloadSnapshot = new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(preloadTrade),
                new ArrayList<>()
        );
        TradeRecordItem storedTrade = new TradeRecordItem(
                1L, "BTC", "BTCUSD", "Buy", 1d, 1d, 1d, 0d, "",
                10d, 1L, 2L, 0d
        );
        AccountStorageRepository.StoredSnapshot storedSnapshot = buildStoredSnapshot(
                Collections.singletonList(storedTrade)
        );

        AccountSnapshot merged = AccountSnapshotRestoreHelper.mergeMissingTrades(preloadSnapshot, storedSnapshot);

        assertEquals(1, merged.getTrades().size());
        assertEquals("XAUUSD", merged.getTrades().get(0).getCode());
    }

    private AccountStorageRepository.StoredSnapshot buildStoredSnapshot(List<TradeRecordItem> trades) {
        return new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "server",
                "source",
                "gateway",
                1L,
                "",
                1L,
                new ArrayList<AccountMetric>(),
                new ArrayList<CurvePoint>(),
                new ArrayList<AccountMetric>(),
                new ArrayList<PositionItem>(),
                new ArrayList<PositionItem>(),
                trades,
                new ArrayList<AccountMetric>()
        );
    }
}
