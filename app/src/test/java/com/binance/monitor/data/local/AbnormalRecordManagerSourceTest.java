package com.binance.monitor.data.local;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AbnormalRecordManagerSourceTest {

    @Test
    public void abnormalRecordManagerShouldBatchDiskPersistAndDeleteFileOnlyOnEmptySnapshot() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/data/local/AbnormalRecordManager.java");

        assertTrue(source.contains("private final ScheduledExecutorService persistExecutor = Executors.newSingleThreadScheduledExecutor();"));
        assertTrue(source.contains("private static final long PERSIST_DELAY_MS = 1_200L;"));
        assertTrue(source.contains("private void schedulePersistLocked(boolean forceImmediate) {"));
        assertTrue(source.contains("pendingPersistFuture = persistExecutor.schedule(() -> persistSnapshot(snapshot, generation), delayMs, TimeUnit.MILLISECONDS);"));
        assertTrue(source.contains("if (snapshot.isEmpty()) {\n            deleteStoreFile();"));
        assertTrue(source.contains("private void deleteStoreFile() {"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
