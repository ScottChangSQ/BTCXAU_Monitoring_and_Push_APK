/*
 * 图表历史仓库，负责把 K 线历史读写统一落到 Room。
 * 图表页通过它实现历史保留、增量合并和分项清理。
 */
package com.binance.monitor.data.local.db.repository;

import android.content.Context;

import com.binance.monitor.data.local.db.AppDatabaseProvider;
import com.binance.monitor.data.local.db.dao.KlineHistoryDao;
import com.binance.monitor.data.local.db.entity.KlineHistoryEntity;
import com.binance.monitor.data.model.CandleEntry;

import java.util.ArrayList;
import java.util.List;

public class ChartHistoryRepository {

    private final KlineHistoryDao klineHistoryDao;

    public ChartHistoryRepository(Context context) {
        this(AppDatabaseProvider.getInstance(context).klineHistoryDao());
    }

    ChartHistoryRepository(KlineHistoryDao klineHistoryDao) {
        this.klineHistoryDao = klineHistoryDao;
    }

    // 读取指定交易对周期下的全部历史 K 线。
    public List<CandleEntry> loadCandles(String seriesKey) {
        if (klineHistoryDao == null || isBlank(seriesKey)) {
            return new ArrayList<>();
        }
        List<KlineHistoryEntity> entities = klineHistoryDao.loadSeries(seriesKey);
        List<CandleEntry> result = new ArrayList<>();
        if (entities == null || entities.isEmpty()) {
            return result;
        }
        for (KlineHistoryEntity entity : entities) {
            result.add(toModel(entity));
        }
        return result;
    }

    // 清空全部历史行情数据。
    public int clearAllHistory() {
        if (klineHistoryDao == null) {
            return 0;
        }
        return klineHistoryDao.clearAll();
    }

    // 直接把上层已经整理好的 K 线窗口写入 Room，不再重复回读整段旧历史。
    public void saveCandles(String seriesKey,
                            String symbol,
                            String intervalKey,
                            String apiInterval,
                            boolean yearAggregate,
                            List<CandleEntry> candles) {
        if (klineHistoryDao == null || candles == null || candles.isEmpty()) {
            return;
        }
        klineHistoryDao.upsertAll(toEntities(seriesKey, symbol, intervalKey, apiInterval, yearAggregate, candles));
    }

    // 把 K 线模型转换为 Room 实体。
    private List<KlineHistoryEntity> toEntities(String seriesKey,
                                                String symbol,
                                                String intervalKey,
                                                String apiInterval,
                                                boolean yearAggregate,
                                                List<CandleEntry> candles) {
        List<KlineHistoryEntity> result = new ArrayList<>();
        if (candles == null || candles.isEmpty()) {
            return result;
        }
        for (CandleEntry candle : candles) {
            if (candle == null) {
                continue;
            }
            KlineHistoryEntity entity = new KlineHistoryEntity();
            entity.seriesKey = safe(seriesKey);
            entity.symbol = safe(symbol);
            entity.intervalKey = safe(intervalKey);
            entity.apiInterval = safe(apiInterval);
            entity.yearAggregate = yearAggregate;
            entity.openTime = candle.getOpenTime();
            entity.closeTime = candle.getCloseTime();
            entity.open = candle.getOpen();
            entity.high = candle.getHigh();
            entity.low = candle.getLow();
            entity.close = candle.getClose();
            entity.volume = candle.getVolume();
            entity.quoteVolume = candle.getQuoteVolume();
            result.add(entity);
        }
        return result;
    }

    // 把 Room 实体还原为图表使用的 CandleEntry。
    private CandleEntry toModel(KlineHistoryEntity entity) {
        return new CandleEntry(
                entity.symbol,
                entity.openTime,
                entity.closeTime,
                entity.open,
                entity.high,
                entity.low,
                entity.close,
                entity.volume,
                entity.quoteVolume
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
