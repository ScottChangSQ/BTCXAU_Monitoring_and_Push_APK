/*
 * 账户快照 DAO，管理当前持仓、当前挂单和账户摘要。
 * 用于账户页恢复、悬浮窗显示以及运行时缓存清理。
 */
package com.binance.monitor.data.local.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.binance.monitor.data.local.db.entity.AccountSnapshotMetaEntity;
import com.binance.monitor.data.local.db.entity.PendingOrderSnapshotEntity;
import com.binance.monitor.data.local.db.entity.PositionSnapshotEntity;

import java.util.List;

@Dao
public interface AccountSnapshotDao {

    // 读取最近一次账户摘要。
    @Query("SELECT * FROM account_snapshot_meta WHERE id = 1 LIMIT 1")
    AccountSnapshotMetaEntity loadMeta();

    // 覆盖保存最近一次账户摘要。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertMeta(AccountSnapshotMetaEntity entity);

    // 读取当前持仓快照。
    @Query("SELECT * FROM position_snapshot ORDER BY ABS(quantity) DESC, code ASC")
    List<PositionSnapshotEntity> loadPositions();

    // 读取当前挂单快照。
    @Query("SELECT * FROM pending_order_snapshot ORDER BY pendingLots DESC, code ASC")
    List<PendingOrderSnapshotEntity> loadPendingOrders();

    // 清空当前持仓快照。
    @Query("DELETE FROM position_snapshot")
    int clearPositions();

    // 清空当前挂单快照。
    @Query("DELETE FROM pending_order_snapshot")
    int clearPendingOrders();

    // 清空账户摘要。
    @Query("DELETE FROM account_snapshot_meta")
    int clearMeta();

    // 批量写入当前持仓。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPositions(List<PositionSnapshotEntity> items);

    // 批量写入当前挂单。
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPendingOrders(List<PendingOrderSnapshotEntity> items);

    // 用新快照整体替换当前持仓。
    @Transaction
    default void replacePositions(List<PositionSnapshotEntity> items) {
        clearPositions();
        if (items != null && !items.isEmpty()) {
            insertPositions(items);
        }
    }

    // 用新快照整体替换当前挂单。
    @Transaction
    default void replacePendingOrders(List<PendingOrderSnapshotEntity> items) {
        clearPendingOrders();
        if (items != null && !items.isEmpty()) {
            insertPendingOrders(items);
        }
    }

    // 清空所有运行时账户快照。
    @Transaction
    default void clearRuntime() {
        clearPositions();
        clearPendingOrders();
        clearMeta();
    }
}
