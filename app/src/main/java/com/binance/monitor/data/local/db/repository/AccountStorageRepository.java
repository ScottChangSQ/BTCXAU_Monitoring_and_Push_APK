/*
 * 账户持久层仓库，统一管理历史交易、当前持仓、当前挂单和账户摘要。
 * 账户页、悬浮窗、设置页都通过这里访问 Room 数据。
 */
package com.binance.monitor.data.local.db.repository;

import android.content.Context;

import com.binance.monitor.data.local.db.AppDatabaseProvider;
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
import com.binance.monitor.ui.floating.FloatingPositionAggregator;
import com.binance.monitor.ui.floating.FloatingPositionPnlItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AccountStorageRepository {

    private final TradeHistoryDao tradeHistoryDao;
    private final AccountSnapshotDao accountSnapshotDao;

    public AccountStorageRepository(Context context) {
        this(
                AppDatabaseProvider.getInstance(context).tradeHistoryDao(),
                AppDatabaseProvider.getInstance(context).accountSnapshotDao()
        );
    }

    AccountStorageRepository(TradeHistoryDao tradeHistoryDao,
                             AccountSnapshotDao accountSnapshotDao) {
        this.tradeHistoryDao = tradeHistoryDao;
        this.accountSnapshotDao = accountSnapshotDao;
    }

    // 保存最新账户快照；全量快照直接替换历史交易，避免旧错记录残留。
    public void persistSnapshot(StoredSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (tradeHistoryDao != null) {
            List<TradeHistoryEntity> trades = toTradeEntities(snapshot.getTrades());
            tradeHistoryDao.clearAll();
            if (!trades.isEmpty()) {
                tradeHistoryDao.upsertAll(trades);
            }
        }
        if (accountSnapshotDao == null) {
            return;
        }
        accountSnapshotDao.replacePositions(toPositionEntities(snapshot.getPositions()));
        accountSnapshotDao.replacePendingOrders(toPendingEntities(snapshot.getPendingOrders()));
        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = 1;
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = safe(snapshot.getAccount());
        metaEntity.server = safe(snapshot.getServer());
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
        if (tradeHistoryDao != null) {
            List<TradeHistoryEntity> trades = toTradeEntities(snapshot.getTrades());
            tradeHistoryDao.clearAll();
            if (!trades.isEmpty()) {
                tradeHistoryDao.upsertAll(trades);
            }
        }
        if (accountSnapshotDao == null) {
            return;
        }
        accountSnapshotDao.replacePositions(toPositionEntities(snapshot.getPositions()));
        accountSnapshotDao.replacePendingOrders(toPendingEntities(snapshot.getPendingOrders()));
        AccountSnapshotMetaEntity existingMeta = accountSnapshotDao.loadMeta();

        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = 1;
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = safe(snapshot.getAccount());
        metaEntity.server = safe(snapshot.getServer());
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
        AccountSnapshotMetaEntity existingMeta = accountSnapshotDao.loadMeta();
        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = 1;
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = safe(snapshot.getAccount());
        metaEntity.server = safe(snapshot.getServer());
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
        accountSnapshotDao.replacePositions(toPositionEntities(snapshot.getPositions()));
        AccountSnapshotMetaEntity existingMeta = accountSnapshotDao.loadMeta();

        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = 1;
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = safe(snapshot.getAccount());
        metaEntity.server = safe(snapshot.getServer());
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
        accountSnapshotDao.replacePositions(toPositionEntities(snapshot.getPositions()));
        accountSnapshotDao.replacePendingOrders(toPendingEntities(snapshot.getPendingOrders()));
        AccountSnapshotMetaEntity existingMeta = accountSnapshotDao.loadMeta();
        boolean identityChanged = isSnapshotIdentityChanged(existingMeta, snapshot);
        if (identityChanged && tradeHistoryDao != null) {
            tradeHistoryDao.clearAll();
        }

        AccountSnapshotMetaEntity metaEntity = new AccountSnapshotMetaEntity();
        metaEntity.id = 1;
        metaEntity.connected = snapshot.isConnected();
        metaEntity.updatedAt = snapshot.getUpdatedAt();
        metaEntity.fetchedAt = snapshot.getFetchedAt();
        metaEntity.account = safe(snapshot.getAccount());
        metaEntity.server = safe(snapshot.getServer());
        metaEntity.source = safe(snapshot.getSource());
        metaEntity.gateway = safe(snapshot.getGateway());
        metaEntity.error = safe(snapshot.getError());
        metaEntity.historyRevision = resolveIncrementalHistoryRevision(existingMeta, snapshot, identityChanged);
        metaEntity.overviewMetricsJson = metricsToJsonString(snapshot.getOverviewMetrics());
        metaEntity.curveIndicatorsJson = resolveIncrementalMetricsJson(
                snapshot.getCurveIndicators(),
                existingMeta == null ? "[]" : safe(existingMeta.curveIndicatorsJson),
                identityChanged
        );
        metaEntity.statsMetricsJson = resolveIncrementalMetricsJson(
                snapshot.getStatsMetrics(),
                existingMeta == null ? "[]" : safe(existingMeta.statsMetricsJson),
                identityChanged
        );
        metaEntity.curvePointsJson = resolveIncrementalCurvePointsJson(
                snapshot.getCurvePoints(),
                existingMeta == null ? "[]" : safe(existingMeta.curvePointsJson),
                identityChanged
        );
        accountSnapshotDao.upsertMeta(metaEntity);
    }

    // 读取当前数据库中保存的账户快照。
    public StoredSnapshot loadStoredSnapshot() {
        List<TradeRecordItem> trades = loadTrades();
        List<PositionItem> positions = loadPositions();
        List<PositionItem> pendingOrders = loadPendingOrders();
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
                    positions,
                    pendingOrders,
                    trades,
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
        List<PositionItem> result = new ArrayList<>();
        if (accountSnapshotDao == null) {
            return result;
        }
        List<PositionSnapshotEntity> entities = accountSnapshotDao.loadPositions();
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
        List<PositionItem> result = new ArrayList<>();
        if (accountSnapshotDao == null) {
            return result;
        }
        List<PendingOrderSnapshotEntity> entities = accountSnapshotDao.loadPendingOrders();
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
        List<TradeRecordItem> result = new ArrayList<>();
        if (tradeHistoryDao == null) {
            return result;
        }
        List<TradeHistoryEntity> entities = tradeHistoryDao.loadAll();
        if (entities == null) {
            return result;
        }
        for (TradeHistoryEntity entity : entities) {
            result.add(toTradeModel(entity));
        }
        return result;
    }

    // 读取悬浮窗需要的按产品盈亏列表。
    public List<FloatingPositionPnlItem> loadFloatingPositionItems() {
        return FloatingPositionAggregator.aggregate(loadPositions());
    }

    // 清空全部历史交易数据。
    public int clearTradeHistory() {
        if (tradeHistoryDao == null) {
            return 0;
        }
        return tradeHistoryDao.clearAll();
    }

    // 清空运行时账户快照。
    public void clearRuntimeSnapshot() {
        if (accountSnapshotDao == null) {
            return;
        }
        accountSnapshotDao.clearRuntime();
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

    private List<TradeHistoryEntity> toTradeEntities(List<TradeRecordItem> trades) {
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
            entity.tradeKey = tradeKey;
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

    private List<PositionSnapshotEntity> toPositionEntities(List<PositionItem> positions) {
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
                    buildPositionKey(item),
                    keyOccurrences
            ));
            result.add(entity);
        }
        return result;
    }

    private List<PendingOrderSnapshotEntity> toPendingEntities(List<PositionItem> pendingOrders) {
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
                    buildPendingKey(item),
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
