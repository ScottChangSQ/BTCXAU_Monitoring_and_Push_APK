/*
 * 行情历史 DAO，提供 K 线查询、写入与清理能力。
 * 由 ChartHistoryRepository 包装后供图表页调用。
 */
package com.binance.monitor.data.local.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.binance.monitor.data.local.db.entity.KlineHistoryEntity;

import java.util.List;

@Dao
public interface KlineHistoryDao {

    // 读取某一组交易对和周期下的全部历史 K 线。
    @Query("SELECT * FROM kline_history WHERE seriesKey = :seriesKey ORDER BY openTime ASC")
    List<KlineHistoryEntity> loadSeries(String seriesKey);

    // 批量写入或覆盖同 openTime 的 K 线。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<KlineHistoryEntity> items);

    // 清理全部历史行情数据。
    @Query("DELETE FROM kline_history")
    int clearAll();
}
