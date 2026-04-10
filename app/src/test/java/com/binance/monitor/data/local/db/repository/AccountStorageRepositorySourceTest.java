package com.binance.monitor.data.local.db.repository;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountStorageRepositorySourceTest {

    @Test
    public void fullSnapshotWritesShouldRunInsideSingleDatabaseTransaction() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get("src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java")),
                StandardCharsets.UTF_8
        ).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(source.contains("database.runInTransaction(() -> {"));
        assertTrue(source.contains("public void persistSnapshot(StoredSnapshot snapshot) {"));
        assertTrue(source.contains("public void persistV2Snapshot(StoredSnapshot snapshot) {"));
    }
}
