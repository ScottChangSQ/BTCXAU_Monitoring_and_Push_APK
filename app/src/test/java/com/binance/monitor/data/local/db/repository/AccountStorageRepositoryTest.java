/*
 * 验证账户持久层只保存服务端最新真值，避免把旧历史或旧曲线重新拼回主链。
 */
package com.binance.monitor.data.local.db.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.local.db.dao.AccountSnapshotDao;
import com.binance.monitor.data.local.db.dao.TradeHistoryDao;
import com.binance.monitor.data.local.db.entity.AccountSnapshotMetaEntity;
import com.binance.monitor.data.local.db.entity.PendingOrderSnapshotEntity;
import com.binance.monitor.data.local.db.entity.PositionSnapshotEntity;
import com.binance.monitor.data.local.db.entity.TradeHistoryEntity;
import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class AccountStorageRepositoryTest {

    // 交易历史合并后，应保留旧记录，并用同键的新记录覆盖旧值。
    @Test
    public void mergeTradesKeepsHistoryAndDeduplicatesByStableKey() {
        List<TradeRecordItem> existing = Arrays.asList(
                trade(1000L, 101L, 201L, 301L, -10d),
                trade(2000L, 102L, 202L, 302L, -20d)
        );
        List<TradeRecordItem> incoming = Arrays.asList(
                trade(2000L, 102L, 202L, 302L, -25d),
                trade(3000L, 103L, 203L, 303L, 12d)
        );

        List<TradeRecordItem> merged = AccountStorageRepository.mergeTrades(existing, incoming);

        assertEquals(3, merged.size());
        assertEquals(3000L, merged.get(0).getCloseTime());
        assertEquals(-25d, merged.get(1).getProfit(), 0.0001d);
        assertEquals(1000L, merged.get(2).getCloseTime());
    }

    @Test
    public void mergeTradesShouldDropItemsWithoutStableIdentity() {
        List<TradeRecordItem> existing = Arrays.asList(
                trade(1000L, 101L, 201L, 301L, -10d),
                tradeWithoutStableIdentity(2000L, -20d)
        );
        List<TradeRecordItem> incoming = Arrays.asList(
                tradeWithoutStableIdentity(3000L, 12d),
                trade(4000L, 104L, 204L, 304L, 6d)
        );

        List<TradeRecordItem> merged = AccountStorageRepository.mergeTrades(existing, incoming);

        assertEquals(2, merged.size());
        assertEquals(4000L, merged.get(0).getCloseTime());
        assertEquals(1000L, merged.get(1).getCloseTime());
    }

    // 曲线点写入本地缓存后，历史仓位比例也必须能完整恢复。
    @Test
    public void persistMetaSnapshotShouldKeepCurvePointPositionRatio() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistMetaSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                2000L,
                "",
                2100L,
                Arrays.asList(new AccountMetric("Total Asset", "$1,000.00")),
                Arrays.asList(new CurvePoint(1000L, 100d, 90d, 0.32d)),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        AccountStorageRepository.StoredSnapshot restored = repository.loadStoredSnapshot();

        assertEquals(1, restored.getCurvePoints().size());
        assertEquals(0.32d, restored.getCurvePoints().get(0).getPositionRatio(), 0.0001d);
    }

    // 摘要快照更新时，应直接覆盖旧曲线，不能把本地旧点位继续拼回去。
    @Test
    public void persistMetaSnapshotShouldReplaceCurvePointsInsteadOfMergingLocalHistory() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        snapshotDao.meta = metaEntity();
        snapshotDao.meta.curvePointsJson = "[{\"timestamp\":1000,\"equity\":100,\"balance\":90,\"positionRatio\":0.1}]";

        repository.persistMetaSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                2000L,
                "",
                2100L,
                Arrays.asList(new AccountMetric("Total Asset", "$1,000.00")),
                Arrays.asList(new CurvePoint(2000L, 120d, 110d, 0.32d)),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        AccountStorageRepository.StoredSnapshot restored = repository.loadStoredSnapshot();

        assertEquals(1, restored.getCurvePoints().size());
        assertEquals(2000L, restored.getCurvePoints().get(0).getTimestamp());
        assertEquals(120d, restored.getCurvePoints().get(0).getEquity(), 0.0001d);
    }

    // 本地缓存层只消费已标准化的曲线时间戳，不再偷偷把秒级口径升成毫秒。
    @Test
    public void loadStoredSnapshotShouldKeepRawCurvePointTimestamp() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        snapshotDao.meta = metaEntity();
        snapshotDao.meta.curvePointsJson = "[{\"timestamp\":1704067200,\"equity\":100,\"balance\":90,\"positionRatio\":0.1}]";

        AccountStorageRepository.StoredSnapshot restored = repository.loadStoredSnapshot();

        assertEquals(1, restored.getCurvePoints().size());
        assertEquals(1_704_067_200L, restored.getCurvePoints().get(0).getTimestamp());
    }

    // 轻实时快照只应刷新持仓和摘要，不应清空已有挂单与历史交易。
    @Test
    public void persistLiveSnapshotUpdatesPositionsAndKeepsPendingAndTrades() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                1000L,
                "",
                1100L,
                Arrays.asList(new AccountMetric("Total Asset", "$900.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 1001L, 0L, 0.01d, 9d)),
                Arrays.asList(pending("XAUUSD", 1002L, 2002L, 0.02d, 0L)),
                Arrays.asList(trade(1000L, 1L, 11L, 21L, 11d)),
                new ArrayList<>()
        ));

        repository.persistLiveSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                2000L,
                "",
                2100L,
                Arrays.asList(new AccountMetric("Total Asset", "$1,000.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 0.05d, 22d)),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(new AccountMetric("Cumulative Profit", "+$10.00"))
        ));

        AccountStorageRepository.StoredSnapshot restored =
                repository.loadStoredSnapshot("7400048", "ICMarketsSC-MT5-6");
        assertEquals(1, restored.getPositions().size());
        assertEquals(0.05d, restored.getPositions().get(0).getQuantity(), 0.0001d);
        assertEquals(1, restored.getPendingOrders().size());
        assertEquals("XAUUSD", restored.getPendingOrders().get(0).getCode());
        assertEquals(1, restored.getTrades().size());
        assertEquals(11d, restored.getTrades().get(0).getProfit(), 0.0001d);
    }

    @Test
    public void persistLiveSnapshotShouldKeepOpenTimeForPositionsWithoutOverwritingPendingOrders() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                1000L,
                "",
                1100L,
                Arrays.asList(new AccountMetric("Total Asset", "$900.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(pending("XAUUSD", 1002L, 2002L, 0.02d, 223344L)),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        repository.persistLiveSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                2000L,
                "",
                2100L,
                Arrays.asList(new AccountMetric("Total Asset", "$1,000.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 1001L, 2001L, 0.05d, 22d, 123456L)),
                Arrays.asList(pending("XAUUSD", 1002L, 2002L, 0.03d, 223344L)),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        AccountStorageRepository.StoredSnapshot restored = repository.loadStoredSnapshot();
        assertEquals(123456L, restored.getPositions().get(0).getOpenTime());
        assertEquals(223344L, restored.getPendingOrders().get(0).getOpenTime());
    }

    @Test
    public void persistIncrementalSnapshotShouldKeepOpenTimeForPositionsAndPendingOrders() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistIncrementalSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                3000L,
                "",
                3100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 1001L, 2001L, 0.05d, 22d, 123456L)),
                Arrays.asList(pending("XAUUSD", 1002L, 2002L, 0.03d, 223344L)),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        assertEquals(123456L, snapshotDao.positions.get(0).openTime);
        assertEquals(223344L, snapshotDao.pendingOrders.get(0).openTime);

        AccountStorageRepository.StoredSnapshot restored = repository.loadStoredSnapshot();
        assertEquals(123456L, restored.getPositions().get(0).getOpenTime());
        assertEquals(223344L, restored.getPendingOrders().get(0).getOpenTime());
    }

    // 轻实时快照如果没有曲线，就必须显式清空旧曲线，不能继续把历史曲线伪装成当前运行态。
    @Test
    public void persistLiveSnapshotShouldClearLegacyCurvePointsWhenIncomingCurveIsEmpty() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        snapshotDao.meta = metaEntity();
        snapshotDao.meta.curvePointsJson = "[{\"timestamp\":1000,\"equity\":100,\"balance\":90,\"positionRatio\":0.1}]";

        repository.persistLiveSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                2000L,
                "",
                2100L,
                Arrays.asList(new AccountMetric("Total Asset", "$1,000.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 0.05d, 22d)),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(new AccountMetric("Cumulative Profit", "+$10.00"))
        ));

        assertEquals("[]", snapshotDao.meta.curvePointsJson);
    }

    // 轻量运行态快照只刷新当前持仓/挂单与摘要，旧历史曲线与历史统计要继续保留到下次全量刷新。
    @Test
    public void persistIncrementalSnapshotShouldOnlyRefreshRuntimeState() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        snapshotDao.meta = metaEntity();
        snapshotDao.meta.curveIndicatorsJson = "[{\"name\":\"最大回撤\",\"value\":\"-1.00%\"}]";
        snapshotDao.meta.statsMetricsJson = "[{\"name\":\"累计盈亏\",\"value\":\"+$11.00\"}]";
        snapshotDao.meta.curvePointsJson = "[{\"timestamp\":1000,\"equity\":100,\"balance\":90,\"positionRatio\":0.1}]";
        tradeDao.items.add(tradeEntity("deal|1", 1000L, 11d));

        repository.persistIncrementalSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                3000L,
                "",
                3100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 0.05d, 22d)),
                Arrays.asList(new PositionItem(
                        "XAUUSD",
                        "XAUUSD",
                        "Sell",
                        0L,
                        2001L,
                        0d,
                        0d,
                        0d,
                        2300d,
                        0d,
                        0d,
                        0d,
                        0d,
                        0d,
                        0.02d,
                        1,
                        2310d,
                        0d,
                        0d,
                        0d
                )),
                Arrays.asList(
                        trade(1000L, 1L, 11L, 21L, 15d),
                        trade(4000L, 2L, 12L, 22L, -5d)
                ),
                Arrays.asList(new AccountMetric("Cumulative Profit", "+$10.00"))
        ));

        assertEquals(1, tradeDao.items.size());
        assertEquals(11d, tradeDao.items.get(0).profit, 0.0001d);
        assertEquals(1, snapshotDao.positions.size());
        assertEquals(1, snapshotDao.pendingOrders.size());
        assertEquals("[{\"timestamp\":1000,\"equity\":100,\"balance\":90,\"positionRatio\":0.1}]", snapshotDao.meta.curvePointsJson);
        assertEquals("[{\"name\":\"最大回撤\",\"value\":\"-1.00%\"}]", snapshotDao.meta.curveIndicatorsJson);
        assertEquals("[{\"name\":\"Cumulative Profit\",\"value\":\"+$10.00\"}]", snapshotDao.meta.statsMetricsJson);
    }

    @Test
    public void partitionedStorageShouldKeepSnapshotsIsolatedByAccountAndServer() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway-a",
                1000L,
                "",
                1100L,
                Arrays.asList(new AccountMetric("Total Asset", "$1,000.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 9001L, 0L, 0.10d, 11d)),
                new ArrayList<>(),
                Arrays.asList(trade(1000L, 101L, 201L, 301L, 11d)),
                new ArrayList<>()
        ));
        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "8800001",
                "Pepperstone-MT5",
                "MT5网关",
                "http://gateway-b",
                2000L,
                "",
                2100L,
                Arrays.asList(new AccountMetric("Total Asset", "$2,000.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("XAUUSD", 9002L, 0L, 0.20d, 22d)),
                new ArrayList<>(),
                Arrays.asList(trade(2000L, 102L, 202L, 302L, 22d)),
                new ArrayList<>()
        ));

        AccountStorageRepository.StoredSnapshot firstAccount =
                repository.loadStoredSnapshot("7400048", "ICMarketsSC-MT5-6");
        AccountStorageRepository.StoredSnapshot secondAccount =
                repository.loadStoredSnapshot("8800001", "Pepperstone-MT5");

        assertEquals("7400048", firstAccount.getAccount());
        assertEquals("BTCUSD", firstAccount.getPositions().get(0).getCode());
        assertEquals(301L, firstAccount.getTrades().get(0).getPositionId());
        assertEquals("8800001", secondAccount.getAccount());
        assertEquals("XAUUSD", secondAccount.getPositions().get(0).getCode());
        assertEquals(302L, secondAccount.getTrades().get(0).getPositionId());
    }

    @Test
    public void clearingOneIdentityShouldNotDeleteOtherIdentitySnapshot() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway-a",
                1000L,
                "",
                1100L,
                Arrays.asList(new AccountMetric("Total Asset", "$1,000.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 9001L, 0L, 0.10d, 11d)),
                new ArrayList<>(),
                Arrays.asList(trade(1000L, 101L, 201L, 301L, 11d)),
                new ArrayList<>()
        ));
        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "8800001",
                "Pepperstone-MT5",
                "MT5网关",
                "http://gateway-b",
                2000L,
                "",
                2100L,
                Arrays.asList(new AccountMetric("Total Asset", "$2,000.00")),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("XAUUSD", 9002L, 0L, 0.20d, 22d)),
                new ArrayList<>(),
                Arrays.asList(trade(2000L, 102L, 202L, 302L, 22d)),
                new ArrayList<>()
        ));

        repository.clearRuntimeSnapshot("7400048", "ICMarketsSC-MT5-6");
        repository.clearTradeHistory("7400048", "ICMarketsSC-MT5-6");

        AccountStorageRepository.StoredSnapshot firstAccount =
                repository.loadStoredSnapshot("7400048", "ICMarketsSC-MT5-6");
        AccountStorageRepository.StoredSnapshot secondAccount =
                repository.loadStoredSnapshot("8800001", "Pepperstone-MT5");

        assertTrue(firstAccount.getPositions().isEmpty());
        assertTrue(firstAccount.getTrades().isEmpty());
        assertEquals("XAUUSD", secondAccount.getPositions().get(0).getCode());
        assertEquals(1, secondAccount.getTrades().size());
    }

    // 轻量运行态快照写库后，historyRevision 也必须持久化，避免冷启动丢失版本号后重复全量补拉历史。
    @Test
    public void persistIncrementalSnapshotShouldPersistHistoryRevision() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        snapshotDao.meta = metaEntity();
        snapshotDao.meta.historyRevision = "history-old";

        repository.persistIncrementalSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                3000L,
                "",
                3100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 0.05d, 22d)),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        assertEquals("history-old", repository.loadStoredSnapshot().getHistoryRevision());
    }

    // 当轻量运行态切到新账户时，旧账户的历史交易和曲线不能继续保留到新账户缓存里。
    @Test
    public void persistIncrementalSnapshotShouldClearHistorySideDataWhenAccountIdentityChanges() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                1000L,
                "",
                1100L,
                "history-old",
                new ArrayList<>(),
                Arrays.asList(new CurvePoint(1000L, 100d, 90d, 0.1d)),
                Arrays.asList(new AccountMetric("最大回撤", "-1.00%")),
                Arrays.asList(position("BTCUSD", 0.01d, 11d)),
                new ArrayList<>(),
                Arrays.asList(trade(1000L, 101L, 201L, 301L, 11d)),
                Arrays.asList(new AccountMetric("累计盈亏", "+$11.00"))
        ));

        repository.persistIncrementalSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "8800001",
                "Pepperstone-MT5",
                "MT5网关",
                "http://gateway",
                3000L,
                "",
                3100L,
                "history-new",
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 0.05d, 22d)),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        assertEquals(1, repository.loadStoredSnapshot("7400048", "ICMarketsSC-MT5-6").getTrades().size());
        assertEquals(0, repository.loadStoredSnapshot("8800001", "Pepperstone-MT5").getTrades().size());
        assertEquals("[]", snapshotDao.meta.curvePointsJson);
        assertEquals("[]", snapshotDao.meta.curveIndicatorsJson);
        assertEquals("[]", snapshotDao.meta.statsMetricsJson);
        assertEquals("history-new", snapshotDao.meta.historyRevision);
        assertEquals("8800001", snapshotDao.meta.account);
        assertEquals("Pepperstone-MT5", snapshotDao.meta.server);
    }

    // 全量快照应覆盖旧交易历史，避免修正后的交易时间仍被本地旧错记录残留污染。
    @Test
    public void persistSnapshotShouldReplaceTradeHistoryWithLatestFullSnapshot() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                1000L,
                "",
                1100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(
                        trade(1000L, 100L, 200L, 300L, -8d),
                        trade(2000L, 101L, 201L, 301L, -5d)
                ),
                new ArrayList<>()
        ));

        repository.persistSnapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "MT5网关",
                "http://gateway",
                3000L,
                "",
                3100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(trade(4000L, 201L, 301L, 401L, 12d)),
                new ArrayList<>()
        ));

        assertEquals(1, tradeDao.items.size());
        assertTrue(tradeDao.items.get(0).tradeKey.endsWith("deal|201"));
        assertEquals(4000L, tradeDao.items.get(0).closeTime);
    }

    // v2 全量替换应原子覆盖交易和曲线，不再和本地旧曲线做增量拼接。
    @Test
    public void persistV2SnapshotShouldAtomicallyReplaceTradesAndCurvePoints() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "历史网关",
                "http://gateway",
                1000L,
                "",
                1100L,
                Arrays.asList(new AccountMetric("总资产", "$900.00")),
                Arrays.asList(new CurvePoint(1000L, 100d, 90d, 0.1d)),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(trade(1000L, 100L, 200L, 300L, -8d)),
                new ArrayList<>()
        ));

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "http://gateway",
                5000L,
                "",
                5100L,
                Arrays.asList(new AccountMetric("总资产", "$1000.00")),
                Arrays.asList(new CurvePoint(1_704_067_200_000L, 120d, 110d, 0.2d)),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 0.05d, 10d)),
                Arrays.asList(new PositionItem(
                        "XAUUSD",
                        "XAUUSD",
                        "Sell",
                        0L,
                        2001L,
                        0d,
                        0d,
                        0d,
                        2300d,
                        0d,
                        0d,
                        0d,
                        0d,
                        0d,
                        0.02d,
                        1,
                        2310d,
                        0d,
                        0d,
                        0d
                )),
                Arrays.asList(trade(4500L, 300L, 301L, 302L, 6d)),
                Arrays.asList(new AccountMetric("累计盈亏", "+$6.00"))
        ));

        AccountStorageRepository.StoredSnapshot restored = repository.loadStoredSnapshot();
        assertEquals(1, restored.getTrades().size());
        assertTrue(tradeDao.items.get(0).tradeKey.endsWith("deal|300"));
        assertEquals(1, restored.getCurvePoints().size());
        assertEquals(1_704_067_200_000L, restored.getCurvePoints().get(0).getTimestamp());
    }

    // 当前持仓若共享同一个 positionTicket，也不能在写库时互相覆盖。
    @Test
    public void persistV2SnapshotShouldKeepMultiplePositionsWhenPositionTicketMatchesButOrderDiffers() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "http://gateway",
                5000L,
                "",
                5100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(
                        position("BTCUSD", 9001L, 10001L, 0.05d, 10d),
                        position("BTCUSD", 9001L, 10002L, 0.03d, 8d)
                ),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        AccountStorageRepository.StoredSnapshot restored = repository.loadStoredSnapshot();
        assertEquals(2, restored.getPositions().size());
    }

    // 即便没有 positionTicket/orderId，两笔同品种同方向同数量同开仓价的持仓也不能被压成一条。
    @Test
    public void persistV2SnapshotShouldKeepDuplicateFallbackPositionsWhenIdentityFieldsMatch() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "http://gateway",
                5000L,
                "",
                5100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(
                        position("BTCUSD", 0L, 0L, 0.05d, 10d),
                        position("BTCUSD", 0L, 0L, 0.05d, 8d)
                ),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        ));

        AccountStorageRepository.StoredSnapshot restored = repository.loadStoredSnapshot();
        assertEquals(2, restored.getPositions().size());
    }

    @Test
    public void persistV2SnapshotShouldKeepDifferentAccountPartitionsWithoutCrossOverwrite() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "http://gateway",
                5000L,
                "",
                5100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 9001L, 10001L, 0.05d, 10d)),
                Arrays.asList(pending("XAUUSD", 9002L, 20001L, 0.03d, 111111L)),
                Arrays.asList(trade(5000L, 30001L, 40001L, 50001L, 15d)),
                new ArrayList<>()
        ));
        repository.persistV2Snapshot(new AccountStorageRepository.StoredSnapshot(
                true,
                "8800099",
                "Pepperstone-MT5",
                "V2网关",
                "http://gateway",
                6000L,
                "",
                6100L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Arrays.asList(position("BTCUSD", 9001L, 10001L, 0.05d, 12d)),
                Arrays.asList(pending("XAUUSD", 9002L, 20001L, 0.03d, 222222L)),
                Arrays.asList(trade(6000L, 30001L, 40001L, 50001L, 18d)),
                new ArrayList<>()
        ));

        assertEquals(2, tradeDao.items.size());
        assertEquals(2, snapshotDao.positions.size());
        assertEquals(2, snapshotDao.pendingOrders.size());
    }

    private TradeRecordItem trade(long closeTime,
                                  long dealTicket,
                                  long orderId,
                                  long positionId,
                                  double profit) {
        return new TradeRecordItem(
                closeTime,
                "BTCUSD",
                "BTCUSD",
                "Sell",
                60000d,
                0.05d,
                3000d,
                0d,
                "",
                profit,
                closeTime - 1000L,
                closeTime,
                0d,
                59000d,
                60000d,
                dealTicket,
                orderId,
                positionId,
                1
        );
    }

    private TradeRecordItem tradeWithoutStableIdentity(long closeTime, double profit) {
        return new TradeRecordItem(
                closeTime,
                "BTCUSD",
                "BTCUSD",
                "Sell",
                60000d,
                0.05d,
                3000d,
                0d,
                "",
                profit,
                closeTime - 1000L,
                closeTime,
                0d,
                59000d,
                60000d,
                0L,
                0L,
                0L,
                1
        );
    }

    private PositionItem position(String code, double quantity, double profit) {
        return new PositionItem(
                code,
                code,
                "Buy",
                1001L,
                0L,
                quantity,
                quantity,
                100d,
                120d,
                quantity * 120d,
                0.5d,
                profit,
                profit,
                0.2d,
                0d,
                0,
                0d,
                0d,
                0d,
                0d
        );
    }

    private PositionItem position(String code,
                                  long positionTicket,
                                  long orderId,
                                  double quantity,
                                  double profit) {
        return position(code, positionTicket, orderId, quantity, profit, 0L);
    }

    private PositionItem position(String code,
                                  long positionTicket,
                                  long orderId,
                                  double quantity,
                                  double profit,
                                  long openTime) {
        return new PositionItem(
                code,
                code,
                "Buy",
                positionTicket,
                orderId,
                openTime,
                quantity,
                quantity,
                100d,
                120d,
                quantity * 120d,
                0.5d,
                profit,
                profit,
                0.2d,
                0d,
                0,
                0d,
                0d,
                0d,
                0d
        );
    }

    private PositionItem position(String code,
                                  double quantity,
                                  long positionTicket,
                                  long orderId) {
        return position(code, positionTicket, orderId, quantity, quantity);
    }

    private PositionItem pending(String code,
                                 long positionTicket,
                                 long orderId,
                                 double pendingLots,
                                 long openTime) {
        return new PositionItem(
                code,
                code,
                "Buy",
                positionTicket,
                orderId,
                openTime,
                0d,
                0d,
                100d,
                120d,
                0d,
                0d,
                0d,
                0d,
                0d,
                pendingLots,
                1,
                101d,
                130d,
                90d,
                0d
        );
    }

    private AccountSnapshotMetaEntity metaEntity() {
        AccountSnapshotMetaEntity entity = new AccountSnapshotMetaEntity();
        entity.id = 1;
        entity.account = "7400048";
        entity.server = "ICMarketsSC-MT5-6";
        entity.source = "历史数据";
        entity.gateway = "http://gateway";
        entity.connected = true;
        entity.updatedAt = 1000L;
        entity.fetchedAt = 1100L;
        entity.historyRevision = "";
        entity.overviewMetricsJson = "[]";
        entity.curveIndicatorsJson = "[]";
        entity.statsMetricsJson = "[]";
        entity.curvePointsJson = "[]";
        return entity;
    }

    private PositionSnapshotEntity positionEntity(String key, String code, double quantity) {
        PositionSnapshotEntity entity = new PositionSnapshotEntity();
        entity.snapshotKey = key;
        entity.productName = code;
        entity.code = code;
        entity.side = "Buy";
        entity.positionTicket = 1L;
        entity.openTime = 0L;
        entity.quantity = quantity;
        entity.sellableQuantity = quantity;
        return entity;
    }

    private PendingOrderSnapshotEntity pendingEntity(String key, String code, double pendingLots) {
        PendingOrderSnapshotEntity entity = new PendingOrderSnapshotEntity();
        entity.snapshotKey = key;
        entity.productName = code;
        entity.code = code;
        entity.side = "Buy";
        entity.orderId = 1L;
        entity.openTime = 0L;
        entity.pendingLots = pendingLots;
        entity.pendingCount = 1;
        return entity;
    }

    private TradeHistoryEntity tradeEntity(String key, long closeTime, double profit) {
        TradeHistoryEntity entity = new TradeHistoryEntity();
        entity.tradeKey = key;
        entity.closeTime = closeTime;
        entity.timestamp = closeTime;
        entity.profit = profit;
        return entity;
    }

    private static class FakeTradeHistoryDao implements TradeHistoryDao {
        private final List<TradeHistoryEntity> items = new ArrayList<>();

        @Override
        public List<TradeHistoryEntity> loadAll() {
            return new ArrayList<>(items);
        }

        @Override
        public List<TradeHistoryEntity> loadAll(String identityPrefix) {
            List<TradeHistoryEntity> result = new ArrayList<>();
            for (TradeHistoryEntity item : items) {
                if (item != null && item.tradeKey.startsWith(identityPrefix)) {
                    result.add(item);
                }
            }
            result.sort((left, right) -> Long.compare(right.closeTime, left.closeTime));
            return result;
        }

        @Override
        public void upsertAll(List<TradeHistoryEntity> incoming) {
            if (incoming == null) {
                return;
            }
            for (TradeHistoryEntity item : incoming) {
                if (item == null) {
                    continue;
                }
                items.removeIf(existing -> existing.tradeKey.equals(item.tradeKey));
                items.add(item);
            }
        }

        @Override
        public int clearAll() {
            int size = items.size();
            items.clear();
            return size;
        }

        @Override
        public int clearAll(String identityPrefix) {
            int before = items.size();
            items.removeIf(existing -> existing != null && existing.tradeKey.startsWith(identityPrefix));
            return before - items.size();
        }
    }

    private static class FakeAccountSnapshotDao implements AccountSnapshotDao {
        private AccountSnapshotMetaEntity meta;
        private final List<AccountSnapshotMetaEntity> metaItems = new ArrayList<>();
        private final List<PositionSnapshotEntity> positions = new ArrayList<>();
        private final List<PendingOrderSnapshotEntity> pendingOrders = new ArrayList<>();

        @Override
        public AccountSnapshotMetaEntity loadMeta() {
            seedMetaIfNeeded();
            AccountSnapshotMetaEntity latest = null;
            for (AccountSnapshotMetaEntity item : metaItems) {
                if (item == null) {
                    continue;
                }
                if (latest == null
                        || item.updatedAt > latest.updatedAt
                        || (item.updatedAt == latest.updatedAt && item.id > latest.id)) {
                    latest = item;
                }
            }
            return latest;
        }

        @Override
        public AccountSnapshotMetaEntity loadMeta(String account, String server) {
            seedMetaIfNeeded();
            for (AccountSnapshotMetaEntity item : metaItems) {
                if (item == null) {
                    continue;
                }
                if (item.account.equals(account) && item.server.equals(server)) {
                    return item;
                }
            }
            return null;
        }

        @Override
        public int loadMaxMetaId() {
            seedMetaIfNeeded();
            int max = 0;
            for (AccountSnapshotMetaEntity item : metaItems) {
                if (item != null && item.id > max) {
                    max = item.id;
                }
            }
            return max;
        }

        @Override
        public void upsertMeta(AccountSnapshotMetaEntity entity) {
            seedMetaIfNeeded();
            metaItems.removeIf(existing -> existing != null
                    && existing.account.equals(entity.account)
                    && existing.server.equals(entity.server));
            metaItems.add(entity);
            meta = entity;
        }

        @Override
        public List<PositionSnapshotEntity> loadPositions() {
            return new ArrayList<>(positions);
        }

        @Override
        public List<PositionSnapshotEntity> loadPositions(String identityPrefix) {
            List<PositionSnapshotEntity> result = new ArrayList<>();
            for (PositionSnapshotEntity item : positions) {
                if (item != null && item.snapshotKey.startsWith(identityPrefix)) {
                    result.add(item);
                }
            }
            return result;
        }

        @Override
        public List<PendingOrderSnapshotEntity> loadPendingOrders() {
            return new ArrayList<>(pendingOrders);
        }

        @Override
        public List<PendingOrderSnapshotEntity> loadPendingOrders(String identityPrefix) {
            List<PendingOrderSnapshotEntity> result = new ArrayList<>();
            for (PendingOrderSnapshotEntity item : pendingOrders) {
                if (item != null && item.snapshotKey.startsWith(identityPrefix)) {
                    result.add(item);
                }
            }
            return result;
        }

        @Override
        public int clearPositions() {
            int size = positions.size();
            positions.clear();
            return size;
        }

        @Override
        public int clearPositions(String identityPrefix) {
            int before = positions.size();
            positions.removeIf(existing -> existing != null && existing.snapshotKey.startsWith(identityPrefix));
            return before - positions.size();
        }

        @Override
        public int clearPendingOrders() {
            int size = pendingOrders.size();
            pendingOrders.clear();
            return size;
        }

        @Override
        public int clearPendingOrders(String identityPrefix) {
            int before = pendingOrders.size();
            pendingOrders.removeIf(existing -> existing != null && existing.snapshotKey.startsWith(identityPrefix));
            return before - pendingOrders.size();
        }

        @Override
        public int clearMeta() {
            seedMetaIfNeeded();
            int size = metaItems.size();
            metaItems.clear();
            meta = null;
            return size;
        }

        @Override
        public int clearMeta(String account, String server) {
            seedMetaIfNeeded();
            int before = metaItems.size();
            metaItems.removeIf(existing -> existing != null
                    && existing.account.equals(account)
                    && existing.server.equals(server));
            meta = loadMeta();
            return before - metaItems.size();
        }

        private void seedMetaIfNeeded() {
            if (meta == null) {
                return;
            }
            boolean exists = false;
            for (AccountSnapshotMetaEntity item : metaItems) {
                if (item != null && item.account.equals(meta.account) && item.server.equals(meta.server)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                metaItems.add(meta);
            }
        }

        @Override
        public void insertPositions(List<PositionSnapshotEntity> items) {
            if (items == null) {
                return;
            }
            for (PositionSnapshotEntity item : items) {
                if (item == null) {
                    continue;
                }
                positions.removeIf(existing -> existing.snapshotKey.equals(item.snapshotKey));
                positions.add(item);
            }
        }

        @Override
        public void insertPendingOrders(List<PendingOrderSnapshotEntity> items) {
            if (items == null) {
                return;
            }
            for (PendingOrderSnapshotEntity item : items) {
                if (item == null) {
                    continue;
                }
                pendingOrders.removeIf(existing -> existing.snapshotKey.equals(item.snapshotKey));
                pendingOrders.add(item);
            }
        }
    }
}
