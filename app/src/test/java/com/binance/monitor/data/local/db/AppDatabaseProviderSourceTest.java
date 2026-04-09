package com.binance.monitor.data.local.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppDatabaseProviderSourceTest {

    @Test
    public void databaseProviderShouldNotUseDestructiveMigrationFallback() throws Exception {
        String source = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/db/AppDatabaseProvider.java",
                "src/main/java/com/binance/monitor/data/local/db/AppDatabaseProvider.java"
        );

        assertTrue(source.contains("Room.databaseBuilder("));
        assertFalse(source.contains("fallbackToDestructiveMigration()"));
    }

    @Test
    public void accountSnapshotMetaHistoryRevisionShouldMatchMigrationContract() throws Exception {
        String entitySource = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/db/entity/AccountSnapshotMetaEntity.java",
                "src/main/java/com/binance/monitor/data/local/db/entity/AccountSnapshotMetaEntity.java"
        );
        String databaseSource = readUtf8(
                "app/src/main/java/com/binance/monitor/data/local/db/AppDatabase.java",
                "src/main/java/com/binance/monitor/data/local/db/AppDatabase.java"
        );

        assertTrue("historyRevision 字段应显式声明为非空，和迁移后的表结构保持一致",
                entitySource.contains("@NonNull") && entitySource.contains("public String historyRevision = \"\";"));
        assertTrue("historyRevision 字段应显式声明默认值，和迁移 SQL 的 DEFAULT '' 保持一致",
                entitySource.contains("@ColumnInfo(defaultValue = \"''\")"));
        assertTrue("1->2 迁移应把 historyRevision 建成 NOT NULL DEFAULT ''",
                databaseSource.contains("ALTER TABLE account_snapshot_meta ADD COLUMN historyRevision TEXT NOT NULL DEFAULT ''"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir"));
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("找不到 AppDatabaseProvider.java");
    }
}
