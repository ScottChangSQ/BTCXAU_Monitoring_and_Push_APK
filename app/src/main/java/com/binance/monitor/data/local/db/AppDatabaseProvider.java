/*
 * Room 数据库单例提供者，负责按应用进程范围复用同一个数据库实例。
 * 数据库只允许后台线程访问，避免页面线程直接承担 Room I/O。
 * 同时禁止使用破坏性迁移兜底，避免版本升级时静默删库。
 */
package com.binance.monitor.data.local.db;

import android.content.Context;

import androidx.room.Room;

public final class AppDatabaseProvider {

    private static final String DATABASE_NAME = "binance_monitor.db";
    private static volatile AppDatabase instance;

    private AppDatabaseProvider() {
    }

    // 获取全局唯一数据库实例。
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabaseProvider.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME
                            )
                            .addMigrations(AppDatabase.MIGRATION_1_2)
                            .addMigrations(AppDatabase.MIGRATION_2_3)
                            .addMigrations(AppDatabase.MIGRATION_3_4)
                            .build();
                }
            }
        }
        return instance;
    }
}
