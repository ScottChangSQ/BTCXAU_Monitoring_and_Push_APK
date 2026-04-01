/*
 * 账户快照展示解析器测试，确保图表页和账户页首屏优先使用新鲜缓存并正确回退到本地快照。
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
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AccountSnapshotDisplayResolverTest {

    @Test
    public void shouldUseStoredSnapshotWhenCacheIsMissing() {
        PositionItem storedPosition = buildPosition("BTCUSD", 2d);
        AccountStorageRepository.StoredSnapshot storedSnapshot = buildStoredSnapshot(
                Collections.singletonList(storedPosition),
                Collections.emptyList(),
                Collections.emptyList()
        );

        AccountSnapshot resolved = AccountSnapshotDisplayResolver.resolve(null, storedSnapshot, 10_000L);

        assertNotNull(resolved);
        assertEquals(1, resolved.getPositions().size());
        assertEquals("BTCUSD", resolved.getPositions().get(0).getCode());
    }

    @Test
    public void shouldUseStoredSnapshotWhenCacheIsExpired() {
        AccountSnapshot preloadSnapshot = new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Collections.singletonList(buildPosition("XAUUSD", 1d)),
                new ArrayList<>(),
                new ArrayList<>()
        );
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                preloadSnapshot,
                "",
                "",
                "",
                "",
                0L,
                "",
                1L
        );
        AccountStorageRepository.StoredSnapshot storedSnapshot = buildStoredSnapshot(
                Collections.singletonList(buildPosition("BTCUSD", 3d)),
                Collections.emptyList(),
                Collections.emptyList()
        );

        AccountSnapshot resolved = AccountSnapshotDisplayResolver.resolve(cache, storedSnapshot, 60_000L);

        assertNotNull(resolved);
        assertEquals(1, resolved.getPositions().size());
        assertEquals("BTCUSD", resolved.getPositions().get(0).getCode());
    }

    @Test
    public void shouldPreferFreshCacheWhenAvailable() {
        AccountSnapshot preloadSnapshot = new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Collections.singletonList(buildPosition("XAUUSD", 1d)),
                new ArrayList<>(),
                new ArrayList<>()
        );
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                preloadSnapshot,
                "",
                "",
                "",
                "",
                0L,
                "",
                10_000L
        );
        AccountStorageRepository.StoredSnapshot storedSnapshot = buildStoredSnapshot(
                Collections.singletonList(buildPosition("BTCUSD", 3d)),
                Collections.emptyList(),
                Collections.emptyList()
        );

        AccountSnapshot resolved = AccountSnapshotDisplayResolver.resolve(cache, storedSnapshot, 12_000L);

        assertNotNull(resolved);
        assertEquals(1, resolved.getPositions().size());
        assertEquals("XAUUSD", resolved.getPositions().get(0).getCode());
    }

    @Test
    public void shouldBackfillStoredTradesWhenFreshCacheMissesThem() {
        AccountSnapshot preloadSnapshot = new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Collections.singletonList(buildPosition("XAUUSD", 1d)),
                new ArrayList<>(),
                new ArrayList<>()
        );
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                preloadSnapshot,
                "",
                "",
                "",
                "",
                0L,
                "",
                10_000L
        );
        TradeRecordItem storedTrade = new TradeRecordItem(
                1L,
                "BTC",
                "BTCUSD",
                "Buy",
                1d,
                1d,
                1d,
                0d,
                "",
                10d,
                1L,
                2L,
                0d
        );
        AccountStorageRepository.StoredSnapshot storedSnapshot = buildStoredSnapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(storedTrade)
        );

        AccountSnapshot resolved = AccountSnapshotDisplayResolver.resolve(cache, storedSnapshot, 12_000L);

        assertNotNull(resolved);
        assertEquals(1, resolved.getTrades().size());
        assertEquals("BTCUSD", resolved.getTrades().get(0).getCode());
    }

    @Test
    public void shouldNotRestoreAnySnapshotWhenSessionIsInactive() {
        AccountSnapshot preloadSnapshot = new AccountSnapshot(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Collections.singletonList(buildPosition("XAUUSD", 1d)),
                new ArrayList<>(),
                new ArrayList<>()
        );
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                preloadSnapshot,
                "",
                "",
                "",
                "",
                0L,
                "",
                10_000L
        );
        AccountStorageRepository.StoredSnapshot storedSnapshot = buildStoredSnapshot(
                Collections.singletonList(buildPosition("BTCUSD", 3d)),
                Collections.emptyList(),
                Collections.emptyList()
        );

        AccountSnapshot resolved = AccountSnapshotDisplayResolver.resolve(cache, storedSnapshot, 12_000L, false);

        assertNull(resolved);
    }

    // 构造最小持仓数据，便于测试快照选择逻辑。
    private PositionItem buildPosition(String code, double quantity) {
        return new PositionItem(
                code,
                code,
                "Buy",
                quantity,
                quantity,
                100d,
                100d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0d,
                0,
                0d
        );
    }

    // 构造本地持久化快照，模拟页面首屏读取数据库后的回填数据。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshot(List<PositionItem> positions,
                                                                       List<PositionItem> pendingOrders,
                                                                       List<TradeRecordItem> trades) {
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
                positions,
                pendingOrders,
                trades,
                new ArrayList<AccountMetric>()
        );
    }
}
