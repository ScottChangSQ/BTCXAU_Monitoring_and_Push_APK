/*
 * 账户持久层仓库，统一管理历史交易、当前持仓、当前挂单和账户摘要。
 * 账户页、悬浮窗、设置页都通过这里访问 Room 数据。
 */
package com.binance.monitor.data.local.db.repository;

import android.content.Context;

import com.binance.monitor.data.local.db.AppDatabase;
import com.binance.monitor.data.local.db.AppDatabaseProvider;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AccountStorageRepository {
    private static final String IDENTITY_SEPARATOR = "|";
    private static final String STORAGE_KEY_SEPARATOR = "||";

    private final AppDatabase database;
    private final TradeHistoryDao tradeHistoryDao;
    private final AccountSnapshotDao accountSnapshotDao;

    public AccountStorageRepository(Context context) {
        this(AppDatabaseProvider.getInstance(context));
    }

    AccountStorageRepository(AppDatabase database) {
        this(
                database,
                database == null ? null : database.tradeHistoryDao(),
                database == null ? null : database.accountSnapshotDao()
        );
    }

    AccountStorageRepository(TradeHistoryDao tradeHistoryDao,
                             AccountSnapshotDao accountSnapshotDao) {
        this(null, tradeHistoryDao, accountSnapshotDao);
    }

    AccountStorageRepository(AppDatabase database,
                             TradeHistoryDao tradeHistoryDao,
                             AccountSnapshotDao accountSnapshotDao) {
        this.database = database;
        this.tradeHistoryDao = tradeHistoryDao;
        this.accountSnapshotDao = accountSnapshotDao;
    }

    // 保存最新账户快照；全量快照直接替换历史交易，避免旧错记录残留。
    public void persistSnapshot(StoredSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        runInDatabaseTransaction(() -> persistSnapshotInternal(snapshot));
    }

    // 全量快照写入必须在同一事务里完成，避免多表落库只写一半。
    private void persistSnapshotInternal(StoredSnapshot snapshot) {
        String account = safe(snapshot.getAccount());
        String server = safe(snapshot.getServer());
        String identityPrefix = buildIdentityPrefix(account, server);
        AccountSnapshotMetaEntity existingMeta = loadMetaByIdentity(account, server);
        if (tradeHistoryDao != null) {
            List<TradeHistoryEntity> trades = toTradeEntities(snapshot.getTrades(), identityPrefix);
            tradeHistoryDao.clearAll(identityPrefix);
            if (!trades.isEmpty()) {
                tradeHistoryDao.upsertAll(trades);
            }
        }
        if (accountSnapshotDao == null) {
            return;
        }
        accountSnapshotDao.replacePositions(identityPrefix, toPositionEntities(snapshot.getPositions(), identityPrefix));
        accountSnapshotDao.replacePendingOrders(identityPrefix, toPendingEntities(snapshot.getPendingOrders(), identityPrefix));
        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = resolveMetaId(existingMeta);
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = account;
        metaEntity.server = server;
        metaEntity.source = safe(snapshot.getSource());
        metaEntity.gateway = safe(snapshot.getGateway());
        metaEntity.error = safe(snapshot.getError());
        metaEntity.historyRevision = safe(snapshot.getHistoryRevision());
        metaEntity.overviewMetricsJson = metricsToJsonString(snapshot.getOverviewMetrics());
        metaEntity.curveIndicatorsJson = metricsToJsonString(snapshot.getCurveIndicators());
        metaEntity.statsMetricsJson = metricsToJsonString(snapshot.getStatsMetrics());
        metaEntity.curvePointsJson = curvePointsToJsonString(snapshot.getCurvePoints());
        accountSnapshotDao.upsertMeta(metaEntity);
    }

    // v2 快照原子替换入口：交易、持仓、挂单和曲线都按最新全量覆盖，不走旧增量拼装。
    public void persistV2Snapshot(StoredSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        runInDatabaseTransaction(() -> persistV2SnapshotInternal(snapshot));
    }

    // v2 全量快照需要把交易、持仓、挂单和摘要作为一次原子替换。
    private void persistV2SnapshotInternal(StoredSnapshot snapshot) {
        String account = safe(snapshot.getAccount());
        String server = safe(snapshot.getServer());
        String identityPrefix = buildIdentityPrefix(account, server);
        AccountSnapshotMetaEntity existingMeta = loadMetaByIdentity(account, server);
        if (tradeHistoryDao != null) {
            List<TradeHistoryEntity> trades = toTradeEntities(snapshot.getTrades(), identityPrefix);
            tradeHistoryDao.clearAll(identityPrefix);
            if (!trades.isEmpty()) {
                tradeHistoryDao.upsertAll(trades);
            }
        }
        if (accountSnapshotDao == null) {
            return;
        }
        accountSnapshotDao.replacePositions(identityPrefix, toPositionEntities(snapshot.getPositions(), identityPrefix));
        accountSnapshotDao.replacePendingOrders(identityPrefix, toPendingEntities(snapshot.getPendingOrders(), identityPrefix));

        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = resolveMetaId(existingMeta);
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = account;
        metaEntity.server = server;
        metaEntity.source = safe(snapshot.getSource());
        metaEntity.gateway = safe(snapshot.getGateway());
        metaEntity.error = safe(snapshot.getError());
        metaEntity.historyRevision = resolveStoredHistoryRevision(existingMeta, snapshot);
        metaEntity.overviewMetricsJson = metricsToJsonString(snapshot.getOverviewMetrics());
        metaEntity.curveIndicatorsJson = metricsToJsonString(snapshot.getCurveIndicators());
        metaEntity.statsMetricsJson = metricsToJsonString(snapshot.getStatsMetrics());
        metaEntity.curvePointsJson = curvePointsToJsonString(snapshot.getCurvePoints());
        accountSnapshotDao.upsertMeta(metaEntity);
    }

    // 仅更新账户摘要，不覆盖当前持仓和历史交易。
    public void persistMetaSnapshot(StoredSnapshot snapshot) {
        if (snapshot == null || accountSnapshotDao == null) {
            return;
        }
        runInDatabaseTransaction(() -> persistMetaSnapshotInternal(snapshot));
    }

    // 摘要写入包含读旧值再写新值，同样要保证事务边界完整。
    private void persistMetaSnapshotInternal(StoredSnapshot snapshot) {
        String account = safe(snapshot.getAccount());
        String server = safe(snapshot.getServer());
        AccountSnapshotMetaEntity existingMeta = loadMetaByIdentity(account, server);
        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = resolveMetaId(existingMeta);
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = account;
        metaEntity.server = server;
        metaEntity.source = safe(snapshot.getSource());
        metaEntity.gateway = safe(snapshot.getGateway());
        metaEntity.error = safe(snapshot.getError());
        metaEntity.historyRevision = resolveStoredHistoryRevision(existingMeta, snapshot);
        metaEntity.overviewMetricsJson = metricsToJsonString(snapshot.getOverviewMetrics());
        metaEntity.curveIndicatorsJson = metricsToJsonString(snapshot.getCurveIndicators());
        metaEntity.statsMetricsJson = metricsToJsonString(snapshot.getStatsMetrics());
        metaEntity.curvePointsJson = curvePointsToJsonString(snapshot.getCurvePoints());
        accountSnapshotDao.upsertMeta(metaEntity);
    }

    // 仅更新账户摘要和当前持仓，不覆盖挂单与历史交易。
    public void persistLiveSnapshot(StoredSnapshot snapshot) {
        if (snapshot == null || accountSnapshotDao == null) {
            return;
        }
        runInDatabaseTransaction(() -> persistLiveSnapshotInternal(snapshot));
    }

    // 运行态写入同时改持仓和摘要，不能让两者出现新旧混写。
    private void persistLiveSnapshotInternal(StoredSnapshot snapshot) {
        String account = safe(snapshot.getAccount());
        String server = safe(snapshot.getServer());
        String identityPrefix = buildIdentityPrefix(account, server);
        accountSnapshotDao.replacePositions(identityPrefix, toPositionEntities(snapshot.getPositions(), identityPrefix));
        AccountSnapshotMetaEntity existingMeta = loadMetaByIdentity(account, server);

        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = resolveMetaId(existingMeta);
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = account;
        metaEntity.server = server;
        metaEntity.source = safe(snapshot.getSource());
        metaEntity.gateway = safe(snapshot.getGateway());
        metaEntity.error = safe(snapshot.getError());
        metaEntity.historyRevision = resolveStoredHistoryRevision(existingMeta, snapshot);
        metaEntity.overviewMetricsJson = metricsToJsonString(snapshot.getOverviewMetrics());
        metaEntity.curveIndicatorsJson = metricsToJsonString(snapshot.getCurveIndicators());
        metaEntity.statsMetricsJson = metricsToJsonString(snapshot.getStatsMetrics());
        metaEntity.curvePointsJson = curvePointsToJsonString(snapshot.getCurvePoints());
        accountSnapshotDao.upsertMeta(metaEntity);
    }

    // 轻量运行态快照只更新持仓、挂单和摘要，不再拼装历史交易或曲线。
    public void persistIncrementalSnapshot(StoredSnapshot snapshot) {
        if (snapshot == null || accountSnapshotDao == null) {
            return;
        }
        runInDatabaseTransaction(() -> persistIncrementalSnapshotInternal(snapshot));
    }

    // 轻量运行态也会跨表改写，并包含 identity 判断，必须整体提交。
    private void persistIncrementalSnapshotInternal(StoredSnapshot snapshot) {
        String account = safe(snapshot.getAccount());
        String server = safe(snapshot.getServer());
        String identityPrefix = buildIdentityPrefix(account, server);
        accountSnapshotDao.replacePositions(identityPrefix, toPositionEntities(snapshot.getPositions(), identityPrefix));
        accountSnapshotDao.replacePendingOrders(identityPrefix, toPendingEntities(snapshot.getPendingOrders(), identityPrefix));
        AccountSnapshotMetaEntity existingMeta = loadMetaByIdentity(account, server);

        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = resolveMetaId(existingMeta);
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = account;
        metaEntity.server = server;
        metaEntity.source = safe(snapshot.getSource());
        metaEntity.gateway = safe(snapshot.getGateway());
        metaEntity.error = safe(snapshot.getError());
        metaEntity.historyRevision = resolveIncrementalHistoryRevision(existingMeta, snapshot, false);
        metaEntity.overviewMetricsJson = metricsToJsonString(snapshot.getOverviewMetrics());
        metaEntity.curveIndicatorsJson = resolveIncrementalMetricsJson(
                snapshot.getCurveIndicators(),
                existingMeta == null ? "[]" : safe(existingMeta.curveIndicatorsJson),
                false
        );
        metaEntity.statsMetricsJson = resolveIncrementalMetricsJson(
                snapshot.getStatsMetrics(),
                existingMeta == null ? "[]" : safe(existingMeta.statsMetricsJson),
                false
        );
        metaEntity.curvePointsJson = resolveIncrementalCurvePointsJson(
                snapshot.getCurvePoints(),
                existingMeta == null ? "[]" : safe(existingMeta.curvePointsJson),
                false
        );
        accountSnapshotDao.upsertMeta(metaEntity);
    }

    // 正式环境走 Room 总事务，测试注入空数据库时退化为直接执行。
    private void runInDatabaseTransaction(Runnable action) {
        if (action == null) {
            return;
        }
        if (database == null) {
            action.run();
            return;
        }
        database.runInTransaction(() -> {
            action.run();
        });
    }

    // 读取当前数据库中保存的账户快照。
    public StoredSnapshot loadStoredSnapshot() {
        AccountSnapshotMetaEntity meta = accountSnapshotDao == null ? null : accountSnapshotDao.loadMeta();
        if (meta == null) {
            return new StoredSnapshot(
                    false,
                    "",
                    "",
                    "",
                    "",
                    0L,
                    "",
                    0L,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }
        return loadStoredSnapshot(meta.account, meta.server);
    }

    // 按账号身份读取对应分区的账户快照。
    public StoredSnapshot loadStoredSnapshot(String account, String server) {
        List<TradeRecordItem> trades = loadTrades(account, server);
        List<PositionItem> positions = loadPositions(account, server);
        List<PositionItem> pendingOrders = loadPendingOrders(account, server);
        AccountSnapshotMetaEntity meta = loadMetaByIdentity(account, server);
        if (meta == null) {
            return new StoredSnapshot(
                    false,
                    safe(account),
                    safe(server),
                    "",
                    "",
                    0L,
                    "",
                    0L,
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            );
        }
        return new StoredSnapshot(
                meta.connected,
                meta.account,
                meta.server,
                meta.source,
                meta.gateway,
                meta.updatedAt,
                meta.error,
                meta.fetchedAt,
                meta.historyRevision,
                jsonToMetrics(meta.overviewMetricsJson),
                jsonToCurvePoints(meta.curvePointsJson),
                jsonToMetrics(meta.curveIndicatorsJson),
                positions,
                pendingOrders,
                trades,
                jsonToMetrics(meta.statsMetricsJson)
        );
    }

    // 读取当前持仓列表。
    public List<PositionItem> loadPositions() {
        AccountSnapshotMetaEntity meta = accountSnapshotDao == null ? null : accountSnapshotDao.loadMeta();
        if (meta == null) {
            return new ArrayList<>();
        }
        return loadPositions(meta.account, meta.server);
    }

    // 按账号身份读取当前持仓列表。
    public List<PositionItem> loadPositions(String account, String server) {
        List<PositionItem> result = new ArrayList<>();
        if (accountSnapshotDao == null) {
            return result;
        }
        List<PositionSnapshotEntity> entities = accountSnapshotDao.loadPositions(buildIdentityPrefix(account, server));
        if (entities == null) {
            return result;
        }
        for (PositionSnapshotEntity entity : entities) {
            result.add(toPositionModel(entity));
        }
        return result;
    }

    // 读取当前挂单列表。
    public List<PositionItem> loadPendingOrders() {
        AccountSnapshotMetaEntity meta = accountSnapshotDao == null ? null : accountSnapshotDao.loadMeta();
        if (meta == null) {
            return new ArrayList<>();
        }
        return loadPendingOrders(meta.account, meta.server);
    }

    // 按账号身份读取当前挂单列表。
    public List<PositionItem> loadPendingOrders(String account, String server) {
        List<PositionItem> result = new ArrayList<>();
        if (accountSnapshotDao == null) {
            return result;
        }
        List<PendingOrderSnapshotEntity> entities = accountSnapshotDao.loadPendingOrders(buildIdentityPrefix(account, server));
        if (entities == null) {
            return result;
        }
        for (PendingOrderSnapshotEntity entity : entities) {
            result.add(toPendingModel(entity));
        }
        return result;
    }

    // 读取全部历史交易。
    public List<TradeRecordItem> loadTrades() {
        AccountSnapshotMetaEntity meta = accountSnapshotDao == null ? null : accountSnapshotDao.loadMeta();
        if (meta == null) {
            return new ArrayList<>();
        }
        return loadTrades(meta.account, meta.server);
    }

    // 按账号身份读取交易历史。
    public List<TradeRecordItem> loadTrades(String account, String server) {
        List<TradeRecordItem> result = new ArrayList<>();
        if (tradeHistoryDao == null) {
            return result;
        }
        List<TradeHistoryEntity> entities = tradeHistoryDao.loadAll(buildIdentityPrefix(account, server));
        if (entities == null) {
            return result;
        }
        for (TradeHistoryEntity entity : entities) {
            result.add(toTradeModel(entity));
        }
        return result;
    }

    // 清空全部历史交易数据。
    public int clearTradeHistory() {
        if (tradeHistoryDao == null) {
            return 0;
        }
        return tradeHistoryDao.clearAll();
    }

    // 清空某个身份分区下的历史交易数据。
    public int clearTradeHistory(String account, String server) {
        if (tradeHistoryDao == null) {
            return 0;
        }
        return tradeHistoryDao.clearAll(buildIdentityPrefix(account, server));
    }

    // 清空运行时账户快照。
    public void clearRuntimeSnapshot() {
        if (accountSnapshotDao == null) {
            return;
        }
        accountSnapshotDao.clearRuntime();
    }

    // 清空某个身份分区下的运行时账户快照。
    public void clearRuntimeSnapshot(String account, String server) {
        if (accountSnapshotDao == null) {
            return;
        }
        accountSnapshotDao.clearRuntime(safe(account), safe(server), buildIdentityPrefix(account, server));
    }

    // 合并旧交易和新交易，并按稳定键去重。
    public static List<TradeRecordItem> mergeTrades(List<TradeRecordItem> existing,
                                                    List<TradeRecordItem> incoming) {
        Map<String, TradeRecordItem> merged = new LinkedHashMap<>();
        if (existing != null) {
            for (TradeRecordItem item : existing) {
                if (item != null) {
                    String tradeKey = buildTradeKey(item);
                    if (!tradeKey.isEmpty()) {
                        merged.put(tradeKey, item);
                    }
                }
            }
        }
        if (incoming != null) {
            for (TradeRecordItem item : incoming) {
                if (item != null) {
                    String tradeKey = buildTradeKey(item);
                    if (!tradeKey.isEmpty()) {
                        merged.put(tradeKey, item);
                    }
                }
            }
        }
        List<TradeRecordItem> result = new ArrayList<>(merged.values());
        result.sort((left, right) -> Long.compare(right.getCloseTime(), left.getCloseTime()));
        return result;
    }

    // 生成交易稳定键；没有稳定身份的记录不进入主链。
    public static String buildTradeKey(TradeRecordItem item) {
        if (item == null) {
            return "";
        }
        if (item.getDealTicket() > 0L) {
            return "deal|" + item.getDealTicket();
        }
        if (item.getOrderId() > 0L || item.getPositionId() > 0L) {
            return "trade|"
                    + item.getOrderId() + "|"
                    + item.getPositionId() + "|"
                    + item.getEntryType() + "|"
                    + item.getOpenTime() + "|"
                    + item.getCloseTime() + "|"
                    + Math.round(Math.abs(item.getQuantity()) * 10_000d);
        }
        return "";
    }

    private List<TradeHistoryEntity> toTradeEntities(List<TradeRecordItem> trades, String identityPrefix) {
        List<TradeHistoryEntity> result = new ArrayList<>();
        if (trades == null) {
            return result;
        }
        for (TradeRecordItem item : trades) {
            if (item == null) {
                continue;
            }
            String tradeKey = buildTradeKey(item);
            if (tradeKey.isEmpty()) {
                continue;
            }
            TradeHistoryEntity entity = new TradeHistoryEntity();
            entity.tradeKey = buildScopedStorageKey(identityPrefix, tradeKey);
            entity.timestamp = item.getTimestamp();
            entity.productName = safe(item.getProductName());
            entity.code = safe(item.getCode());
            entity.side = safe(item.getSide());
            entity.price = item.getPrice();
            entity.quantity = item.getQuantity();
            entity.amount = item.getAmount();
            entity.fee = item.getFee();
            entity.remark = safe(item.getRemark());
            entity.profit = item.getProfit();
            entity.openTime = item.getOpenTime();
            entity.closeTime = item.getCloseTime();
            entity.storageFee = item.getStorageFee();
            entity.openPrice = item.getOpenPrice();
            entity.closePrice = item.getClosePrice();
            entity.dealTicket = item.getDealTicket();
            entity.orderId = item.getOrderId();
            entity.positionId = item.getPositionId();
            entity.entryType = item.getEntryType();
            result.add(entity);
        }
        return result;
    }

    private List<PositionSnapshotEntity> toPositionEntities(List<PositionItem> positions, String identityPrefix) {
        List<PositionSnapshotEntity> result = new ArrayList<>();
        if (positions == null) {
            return result;
        }
        Map<String, Integer> keyOccurrences = new LinkedHashMap<>();
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            PositionSnapshotEntity entity = new PositionSnapshotEntity();
            fillPositionEntity(entity, item, buildUniqueSnapshotKey(
                    buildScopedStorageKey(identityPrefix, buildPositionKey(item)),
                    keyOccurrences
            ));
            result.add(entity);
        }
        return result;
    }

    private List<PendingOrderSnapshotEntity> toPendingEntities(List<PositionItem> pendingOrders, String identityPrefix) {
        List<PendingOrderSnapshotEntity> result = new ArrayList<>();
        if (pendingOrders == null) {
            return result;
        }
        Map<String, Integer> keyOccurrences = new LinkedHashMap<>();
        for (PositionItem item : pendingOrders) {
            if (item == null) {
                continue;
            }
            PendingOrderSnapshotEntity entity = new PendingOrderSnapshotEntity();
            fillPendingEntity(entity, item, buildUniqueSnapshotKey(
                    buildScopedStorageKey(identityPrefix, buildPendingKey(item)),
                    keyOccurrences
            ));
            result.add(entity);
        }
        return result;
    }

    private void fillPositionEntity(PositionSnapshotEntity entity, PositionItem item, String key) {
        entity.snapshotKey = key;
        entity.productName = safe(item.getProductName());
        entity.code = safe(item.getCode());
        entity.side = safe(item.getSide());
        entity.positionTicket = item.getPositionTicket();
        entity.orderId = item.getOrderId();
        entity.openTime = item.getOpenTime();
        entity.quantity = item.getQuantity();
        entity.sellableQuantity = item.getSellableQuantity();
        entity.costPrice = item.getCostPrice();
        entity.latestPrice = item.getLatestPrice();
        entity.marketValue = item.getMarketValue();
        entity.positionRatio = item.getPositionRatio();
        entity.dayPnL = item.getDayPnL();
        entity.totalPnL = item.getTotalPnL();
        entity.returnRate = item.getReturnRate();
        entity.pendingLots = item.getPendingLots();
        entity.pendingCount = item.getPendingCount();
        entity.pendingPrice = item.getPendingPrice();
        entity.takeProfit = item.getTakeProfit();
        entity.stopLoss = item.getStopLoss();
        entity.storageFee = item.getStorageFee();
    }

    private void fillPendingEntity(PendingOrderSnapshotEntity entity, PositionItem item, String key) {
        entity.snapshotKey = key;
        entity.productName = safe(item.getProductName());
        entity.code = safe(item.getCode());
        entity.side = safe(item.getSide());
        entity.positionTicket = item.getPositionTicket();
        entity.orderId = item.getOrderId();
        entity.openTime = item.getOpenTime();
        entity.quantity = item.getQuantity();
        entity.sellableQuantity = item.getSellableQuantity();
        entity.costPrice = item.getCostPrice();
        entity.latestPrice = item.getLatestPrice();
        entity.marketValue = item.getMarketValue();
        entity.positionRatio = item.getPositionRatio();
        entity.dayPnL = item.getDayPnL();
        entity.totalPnL = item.getTotalPnL();
        entity.returnRate = item.getReturnRate();
        entity.pendingLots = item.getPendingLots();
        entity.pendingCount = item.getPendingCount();
        entity.pendingPrice = item.getPendingPrice();
        entity.takeProfit = item.getTakeProfit();
        entity.stopLoss = item.getStopLoss();
        entity.storageFee = item.getStorageFee();
    }

    private PositionItem toPositionModel(PositionSnapshotEntity entity) {
        return new PositionItem(
                entity.productName,
                entity.code,
                entity.side,
                entity.positionTicket,
                entity.orderId,
                entity.openTime,
                entity.quantity,
                entity.sellableQuantity,
                entity.costPrice,
                entity.latestPrice,
                entity.marketValue,
                entity.positionRatio,
                entity.dayPnL,
                entity.totalPnL,
                entity.returnRate,
                entity.pendingLots,
                entity.pendingCount,
                entity.pendingPrice,
                entity.takeProfit,
                entity.stopLoss,
                entity.storageFee
        );
    }

    private PositionItem toPendingModel(PendingOrderSnapshotEntity entity) {
        return new PositionItem(
                entity.productName,
                entity.code,
                entity.side,
                entity.positionTicket,
                entity.orderId,
                entity.openTime,
                entity.quantity,
                entity.sellableQuantity,
                entity.costPrice,
                entity.latestPrice,
                entity.marketValue,
                entity.positionRatio,
                entity.dayPnL,
                entity.totalPnL,
                entity.returnRate,
                entity.pendingLots,
                entity.pendingCount,
                entity.pendingPrice,
                entity.takeProfit,
                entity.stopLoss,
                entity.storageFee
        );
    }

    private TradeRecordItem toTradeModel(TradeHistoryEntity entity) {
        return new TradeRecordItem(
                entity.timestamp,
                entity.productName,
                entity.code,
                entity.side,
                entity.price,
                entity.quantity,
                entity.amount,
                entity.fee,
                entity.remark,
                entity.profit,
                entity.openTime,
                entity.closeTime,
                entity.storageFee,
                entity.openPrice,
                entity.closePrice,
                entity.dealTicket,
                entity.orderId,
                entity.positionId,
                entity.entryType
        );
    }

    private String metricsToJsonString(List<AccountMetric> metrics) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        if (metrics != null) {
            boolean first = true;
            for (AccountMetric metric : metrics) {
                if (metric == null) {
                    continue;
                }
                if (!first) {
                    builder.append(",");
                }
                builder.append("{\"name\":\"")
                        .append(escapeJson(safe(metric.getName())))
                        .append("\",\"value\":\"")
                        .append(escapeJson(safe(metric.getValue())))
                        .append("\"}");
                first = false;
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private List<AccountMetric> jsonToMetrics(String raw) {
        List<AccountMetric> result = new ArrayList<>();
        if (isBlank(raw)) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    result.add(new AccountMetric(item.optString("name", ""), item.optString("value", "")));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private String curvePointsToJsonString(List<CurvePoint> curvePoints) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        if (curvePoints != null) {
            boolean first = true;
            for (CurvePoint point : curvePoints) {
                if (point == null) {
                    continue;
                }
                if (!first) {
                    builder.append(",");
                }
                builder.append("{\"timestamp\":")
                        .append(point.getTimestamp())
                        .append(",\"equity\":")
                        .append(point.getEquity())
                        .append(",\"balance\":")
                        .append(point.getBalance())
                        .append(",\"positionRatio\":")
                        .append(point.getPositionRatio())
                        .append("}");
                first = false;
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private List<CurvePoint> jsonToCurvePoints(String raw) {
        List<CurvePoint> result = new ArrayList<>();
        if (isBlank(raw)) {
            return result;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    result.add(new CurvePoint(
                            item.optLong("timestamp", 0L),
                            item.optDouble("equity", 0d),
                            item.optDouble("balance", 0d),
                            item.optDouble("positionRatio", 0d)
                    ));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private String buildPositionKey(PositionItem item) {
        if (item == null) {
            return "";
        }
        if (item.getPositionTicket() > 0L) {
            if (item.getOrderId() > 0L) {
                return "position|" + item.getPositionTicket() + "|" + item.getOrderId();
            }
            return "position|" + item.getPositionTicket();
        }
        if (item.getOrderId() > 0L) {
            return "position|order|" + item.getOrderId();
        }
        return "position|" + safe(item.getCode()) + "|" + safe(item.getSide()) + "|"
                + Math.round(Math.abs(item.getQuantity()) * 10_000d) + "|" + Math.round(item.getCostPrice() * 100d);
    }

    private String buildPendingKey(PositionItem item) {
        if (item == null) {
            return "";
        }
        if (item.getOrderId() > 0L) {
            return "pending|" + item.getOrderId();
        }
        return "pending|" + safe(item.getCode()) + "|" + safe(item.getSide()) + "|"
                + Math.round(Math.abs(item.getPendingLots()) * 10_000d) + "|" + Math.round(item.getPendingPrice() * 100d);
    }

    private String buildUniqueSnapshotKey(String baseKey, Map<String, Integer> occurrences) {
        String safeBaseKey = isBlank(baseKey) ? "snapshot" : baseKey;
        int index = occurrences.containsKey(safeBaseKey) ? occurrences.get(safeBaseKey) : 0;
        occurrences.put(safeBaseKey, index + 1);
        if (index <= 0) {
            return safeBaseKey;
        }
        return safeBaseKey + "#" + index;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    // 轻量运行态如果换了账号身份，旧账户的历史侧缓存必须整体失效。
    private boolean isSnapshotIdentityChanged(AccountSnapshotMetaEntity existingMeta, StoredSnapshot snapshot) {
        if (existingMeta == null || snapshot == null) {
            return false;
        }
        return !safe(existingMeta.account).trim().equalsIgnoreCase(safe(snapshot.getAccount()).trim())
                || !safe(existingMeta.server).trim().equalsIgnoreCase(safe(snapshot.getServer()).trim());
    }

    // historyRevision 在同账户的运行态更新里可以沿用旧值；换账户后必须切到新值或清空。
    private String resolveIncrementalHistoryRevision(AccountSnapshotMetaEntity existingMeta,
                                                     StoredSnapshot snapshot,
                                                     boolean identityChanged) {
        String incomingRevision = safe(snapshot == null ? "" : snapshot.getHistoryRevision()).trim();
        if (!incomingRevision.isEmpty()) {
            return incomingRevision;
        }
        if (identityChanged || existingMeta == null) {
            return "";
        }
        return safe(existingMeta.historyRevision);
    }

    // 全量或摘要写入若未显式给出版本号，则沿用已有 revision，避免把已知历史版本静默覆盖为空。
    private String resolveStoredHistoryRevision(AccountSnapshotMetaEntity existingMeta,
                                                StoredSnapshot snapshot) {
        String incomingRevision = safe(snapshot == null ? "" : snapshot.getHistoryRevision()).trim();
        if (!incomingRevision.isEmpty()) {
            return incomingRevision;
        }
        return existingMeta == null ? "" : safe(existingMeta.historyRevision);
    }

    // 轻量运行态在同账户下允许沿用旧历史侧指标；换账户后不能继承旧值。
    private String resolveIncrementalMetricsJson(List<AccountMetric> metrics,
                                                 String existingJson,
                                                 boolean identityChanged) {
        if (metrics != null && !metrics.isEmpty()) {
            return metricsToJsonString(metrics);
        }
        return identityChanged ? "[]" : safe(existingJson);
    }

    // 轻量运行态在同账户下允许沿用旧曲线；换账户后不能继承旧曲线。
    private String resolveIncrementalCurvePointsJson(List<CurvePoint> curvePoints,
                                                     String existingJson,
                                                     boolean identityChanged) {
        if (curvePoints != null && !curvePoints.isEmpty()) {
            return curvePointsToJsonString(curvePoints);
        }
        return identityChanged ? "[]" : safe(existingJson);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private AccountSnapshotMetaEntity loadMetaByIdentity(String account, String server) {
        if (accountSnapshotDao == null) {
            return null;
        }
        return accountSnapshotDao.loadMeta(safe(account), safe(server));
    }

    private int resolveMetaId(AccountSnapshotMetaEntity existingMeta) {
        if (existingMeta != null && existingMeta.id > 0) {
            return existingMeta.id;
        }
        if (accountSnapshotDao == null) {
            return 1;
        }
        return Math.max(1, accountSnapshotDao.loadMaxMetaId() + 1);
    }

    private String buildIdentityPrefix(String account, String server) {
        return buildIdentityKey(account, server) + STORAGE_KEY_SEPARATOR;
    }

    private String buildIdentityKey(String account, String server) {
        String safeAccount = safe(account).trim();
        String safeServer = safe(server).trim().toLowerCase(Locale.ROOT);
        return safeAccount.length() + IDENTITY_SEPARATOR + safeAccount
                + IDENTITY_SEPARATOR
                + safeServer.length() + IDENTITY_SEPARATOR + safeServer;
    }

    private String buildScopedStorageKey(String identityPrefix, String baseKey) {
        String safeBaseKey = isBlank(baseKey) ? "snapshot" : baseKey;
        return identityPrefix + safeBaseKey;
    }

    private static String escapeJson(String value) {
        return safe(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static class StoredSnapshot {
        private final boolean connected;
        private final String account;
        private final String server;
        private final String source;
        private final String gateway;
        private final long updatedAt;
        private final String error;
        private final long fetchedAt;
        private final String historyRevision;
        private final List<AccountMetric> overviewMetrics;
        private final List<CurvePoint> curvePoints;
        private final List<AccountMetric> curveIndicators;
        private final List<PositionItem> positions;
        private final List<PositionItem> pendingOrders;
        private final List<TradeRecordItem> trades;
        private final List<AccountMetric> statsMetrics;

        public StoredSnapshot(boolean connected,
                              String account,
                              String server,
                              String source,
                              String gateway,
                              long updatedAt,
                              String error,
                              long fetchedAt,
                              List<AccountMetric> overviewMetrics,
                              List<CurvePoint> curvePoints,
                              List<AccountMetric> curveIndicators,
                              List<PositionItem> positions,
                              List<PositionItem> pendingOrders,
                              List<TradeRecordItem> trades,
                              List<AccountMetric> statsMetrics) {
            this(
                    connected,
                    account,
                    server,
                    source,
                    gateway,
                    updatedAt,
                    error,
                    fetchedAt,
                    "",
                    overviewMetrics,
                    curvePoints,
                    curveIndicators,
                    positions,
                    pendingOrders,
                    trades,
                    statsMetrics
            );
        }

        public StoredSnapshot(boolean connected,
                              String account,
                              String server,
                              String source,
                              String gateway,
                              long updatedAt,
                              String error,
                              long fetchedAt,
                              String historyRevision,
                              List<AccountMetric> overviewMetrics,
                              List<CurvePoint> curvePoints,
                              List<AccountMetric> curveIndicators,
                              List<PositionItem> positions,
                              List<PositionItem> pendingOrders,
                              List<TradeRecordItem> trades,
                              List<AccountMetric> statsMetrics) {
            this.connected = connected;
            this.account = safe(account);
            this.server = safe(server);
            this.source = safe(source);
            this.gateway = safe(gateway);
            this.updatedAt = updatedAt;
            this.error = safe(error);
            this.fetchedAt = fetchedAt;
            this.historyRevision = safe(historyRevision);
            this.overviewMetrics = overviewMetrics == null ? new ArrayList<>() : new ArrayList<>(overviewMetrics);
            this.curvePoints = curvePoints == null ? new ArrayList<>() : new ArrayList<>(curvePoints);
            this.curveIndicators = curveIndicators == null ? new ArrayList<>() : new ArrayList<>(curveIndicators);
            this.positions = positions == null ? new ArrayList<>() : new ArrayList<>(positions);
            this.pendingOrders = pendingOrders == null ? new ArrayList<>() : new ArrayList<>(pendingOrders);
            this.trades = trades == null ? new ArrayList<>() : new ArrayList<>(trades);
            this.statsMetrics = statsMetrics == null ? new ArrayList<>() : new ArrayList<>(statsMetrics);
        }

        public boolean isConnected() { return connected; }
        public String getAccount() { return account; }
        public String getServer() { return server; }
        public String getSource() { return source; }
        public String getGateway() { return gateway; }
        public long getUpdatedAt() { return updatedAt; }
        public String getError() { return error; }
        public long getFetchedAt() { return fetchedAt; }
        public String getHistoryRevision() { return historyRevision; }
        public List<AccountMetric> getOverviewMetrics() { return new ArrayList<>(overviewMetrics); }
        public List<CurvePoint> getCurvePoints() { return new ArrayList<>(curvePoints); }
        public List<AccountMetric> getCurveIndicators() { return new ArrayList<>(curveIndicators); }
        public List<PositionItem> getPositions() { return new ArrayList<>(positions); }
        public List<PositionItem> getPendingOrders() { return new ArrayList<>(pendingOrders); }
        public List<TradeRecordItem> getTrades() { return new ArrayList<>(trades); }
        public List<AccountMetric> getStatsMetrics() { return new ArrayList<>(statsMetrics); }
    }
}
