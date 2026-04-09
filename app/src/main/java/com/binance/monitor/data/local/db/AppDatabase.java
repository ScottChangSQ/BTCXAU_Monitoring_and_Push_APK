/*
 * Room 数据库入口，统一管理行情历史和 MT5 账户快照相关表。
 * 供图表页、账户页、悬浮窗和设置页通过仓库层访问。
 */
package com.binance.monitor.data.local.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.binance.monitor.data.local.db.dao.AccountSnapshotDao;
import com.binance.monitor.data.local.db.dao.KlineHistoryDao;
import com.binance.monitor.data.local.db.dao.TradeHistoryDao;
import com.binance.monitor.data.local.db.entity.AccountSnapshotMetaEntity;
import com.binance.monitor.data.local.db.entity.KlineHistoryEntity;
import com.binance.monitor.data.local.db.entity.PendingOrderSnapshotEntity;
import com.binance.monitor.data.local.db.entity.PositionSnapshotEntity;
import com.binance.monitor.data.local.db.entity.TradeHistoryEntity;

@Database(
        entities = {
                KlineHistoryEntity.class,
                TradeHistoryEntity.class,
                PositionSnapshotEntity.class,
                PendingOrderSnapshotEntity.class,
                AccountSnapshotMetaEntity.class
        },
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE account_snapshot_meta ADD COLUMN historyRevision TEXT NOT NULL DEFAULT ''");
        }
    };

    // 提供行情历史访问入口。
    public abstract KlineHistoryDao klineHistoryDao();

    // 提供历史交易访问入口。
    public abstract TradeHistoryDao tradeHistoryDao();

    // 提供账户快照访问入口。
    public abstract AccountSnapshotDao accountSnapshotDao();
}
