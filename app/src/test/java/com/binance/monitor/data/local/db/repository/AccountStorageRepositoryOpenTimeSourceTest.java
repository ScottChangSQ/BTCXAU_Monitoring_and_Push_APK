package com.binance.monitor.data.local.db.repository;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStorageRepositoryOpenTimeSourceTest {

    @Test
    public void roomSnapshotPersistenceShouldPreservePositionAndPendingOpenTime() throws Exception {
        String repositorySource = readUtf8("src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        String positionEntitySource = readUtf8("src/main/java/com/binance/monitor/data/local/db/entity/PositionSnapshotEntity.java");
        String pendingEntitySource = readUtf8("src/main/java/com/binance/monitor/data/local/db/entity/PendingOrderSnapshotEntity.java");

        assertTrue(positionEntitySource.contains("public long openTime;"));
        assertTrue(pendingEntitySource.contains("public long openTime;"));
        assertTrue(repositorySource.contains("entity.openTime = item.getOpenTime();"));
        assertTrue(repositorySource.contains("entity.orderId,\n                entity.openTime,\n                entity.quantity,"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
