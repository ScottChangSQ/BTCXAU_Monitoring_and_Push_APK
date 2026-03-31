/*
 * 历史交易 DAO，负责交易记录的长期保存和清理。
 * 由 AccountStorageRepository 包装后供账户页使用。
 */
package com.binance.monitor.data.local.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.binance.monitor.data.local.db.entity.TradeHistoryEntity;

import java.util.List;

@Dao
public interface TradeHistoryDao {

    // 按最近平仓时间倒序读取所有交易。
    @Query("SELECT * FROM trade_history ORDER BY closeTime DESC, timestamp DESC")
    List<TradeHistoryEntity> loadAll();

    // 批量写入或覆盖同稳定键的交易。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<TradeHistoryEntity> items);

    // 清空全部历史交易。
    @Query("DELETE FROM trade_history")
    int clearAll();
}
