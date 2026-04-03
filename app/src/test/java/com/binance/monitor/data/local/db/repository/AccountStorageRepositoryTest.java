/*
 * 验证账户持久层在合并交易历史和权益曲线时，会保留旧历史并按稳定键去重，
 * 避免每次刷新后把已拉到的历史记录覆盖掉或重复堆叠。
 */
package com.binance.monitor.data.local.db.repository;

import static org.junit.Assert.assertEquals;

import com.binance.monitor.data.local.db.dao.AccountSnapshotDao;
import com.binance.monitor.data.local.db.dao.TradeHistoryDao;
import com.binance.monitor.data.local.db.entity.AccountSnapshotMetaEntity;
import com.binance.monitor.data.local.db.entity.PendingOrderSnapshotEntity;
import com.binance.monitor.data.local.db.entity.PositionSnapshotEntity;
import com.binance.monitor.data.local.db.entity.TradeHistoryEntity;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

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

    // 权益曲线合并后，应保留旧点位，并按 timestamp 用最新值覆盖。
    @Test
    public void mergeCurvePointsKeepsHistoryAndDeduplicatesByTimestamp() {
        List<CurvePoint> existing = Arrays.asList(
                new CurvePoint(1000L, 100d, 90d, 0.10d),
                new CurvePoint(2000L, 101d, 91d, 0.20d)
        );
        List<CurvePoint> incoming = Arrays.asList(
                new CurvePoint(2000L, 110d, 95d, 0.55d),
                new CurvePoint(3000L, 120d, 100d, 0.80d)
        );

        List<CurvePoint> merged = AccountStorageRepository.mergeCurvePoints(existing, incoming);

        assertEquals(3, merged.size());
        assertEquals(1000L, merged.get(0).getTimestamp());
        assertEquals(110d, merged.get(1).getEquity(), 0.0001d);
        assertEquals(0.55d, merged.get(1).getPositionRatio(), 0.0001d);
        assertEquals(3000L, merged.get(2).getTimestamp());
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

    // 轻实时快照只应刷新持仓和摘要，不应清空已有挂单与历史交易。
    @Test
    public void persistLiveSnapshotUpdatesPositionsAndKeepsPendingAndTrades() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        snapshotDao.meta = metaEntity();
        snapshotDao.positions.add(positionEntity("position|old", "BTCUSD", 0.01d));
        snapshotDao.pendingOrders.add(pendingEntity("pending|1", "XAUUSD", 0.02d));
        tradeDao.items.add(tradeEntity("deal|1", 1000L, 11d));

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

        assertEquals(1, snapshotDao.positions.size());
        assertEquals(0.05d, snapshotDao.positions.get(0).quantity, 0.0001d);
        assertEquals(1, snapshotDao.pendingOrders.size());
        assertEquals("XAUUSD", snapshotDao.pendingOrders.get(0).code);
        assertEquals(1, tradeDao.items.size());
        assertEquals("deal|1", tradeDao.items.get(0).tradeKey);
    }

    // 轻量复合快照应把新增交易写入本地，同时保留既有历史记录。
    @Test
    public void persistIncrementalSnapshotShouldAppendAndRefreshTradeHistory() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        snapshotDao.meta = metaEntity();
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

        assertEquals(2, tradeDao.items.size());
        assertEquals(15d, tradeDao.items.stream()
                .filter(item -> "deal|1".equals(item.tradeKey))
                .findFirst()
                .orElseThrow(IllegalStateException::new)
                .profit, 0.0001d);
        assertEquals(1, snapshotDao.positions.size());
        assertEquals(1, snapshotDao.pendingOrders.size());
    }

    // 全量快照应覆盖旧交易历史，避免修正后的交易时间仍被本地旧错记录残留污染。
    @Test
    public void persistSnapshotShouldReplaceTradeHistoryWithLatestFullSnapshot() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        tradeDao.items.add(tradeEntity("deal|old", 1000L, -8d));
        tradeDao.items.add(tradeEntity("deal|legacy", 2000L, -5d));

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
        assertEquals("deal|201", tradeDao.items.get(0).tradeKey);
        assertEquals(4000L, tradeDao.items.get(0).closeTime);
    }

    // v2 全量替换应原子覆盖交易和曲线，不再和本地旧曲线做增量拼接。
    @Test
    public void persistV2SnapshotShouldAtomicallyReplaceTradesAndCurvePoints() {
        FakeTradeHistoryDao tradeDao = new FakeTradeHistoryDao();
        FakeAccountSnapshotDao snapshotDao = new FakeAccountSnapshotDao();
        AccountStorageRepository repository = new AccountStorageRepository(tradeDao, snapshotDao);

        snapshotDao.meta = metaEntity();
        snapshotDao.meta.curvePointsJson = "[{\"timestamp\":1000,\"equity\":100,\"balance\":90,\"positionRatio\":0.1}]";
        tradeDao.items.add(tradeEntity("deal|legacy", 1000L, -8d));

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
                Arrays.asList(new CurvePoint(4000L, 120d, 110d, 0.2d)),
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
        assertEquals("deal|300", tradeDao.items.get(0).tradeKey);
        assertEquals(1, restored.getCurvePoints().size());
        assertEquals(4000L, restored.getCurvePoints().get(0).getTimestamp());
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
    }

    private static class FakeAccountSnapshotDao implements AccountSnapshotDao {
        private AccountSnapshotMetaEntity meta;
        private final List<PositionSnapshotEntity> positions = new ArrayList<>();
        private final List<PendingOrderSnapshotEntity> pendingOrders = new ArrayList<>();

        @Override
        public AccountSnapshotMetaEntity loadMeta() {
            return meta;
        }

        @Override
        public void upsertMeta(AccountSnapshotMetaEntity entity) {
            meta = entity;
        }

        @Override
        public List<PositionSnapshotEntity> loadPositions() {
            return new ArrayList<>(positions);
        }

        @Override
        public List<PendingOrderSnapshotEntity> loadPendingOrders() {
            return new ArrayList<>(pendingOrders);
        }

        @Override
        public int clearPositions() {
            int size = positions.size();
            positions.clear();
            return size;
        }

        @Override
        public int clearPendingOrders() {
            int size = pendingOrders.size();
            pendingOrders.clear();
            return size;
        }

        @Override
        public int clearMeta() {
            meta = null;
            return 1;
        }

        @Override
        public void insertPositions(List<PositionSnapshotEntity> items) {
            if (items != null) {
                positions.addAll(items);
            }
        }

        @Override
        public void insertPendingOrders(List<PendingOrderSnapshotEntity> items) {
            if (items != null) {
                pendingOrders.addAll(items);
            }
        }
    }
}
