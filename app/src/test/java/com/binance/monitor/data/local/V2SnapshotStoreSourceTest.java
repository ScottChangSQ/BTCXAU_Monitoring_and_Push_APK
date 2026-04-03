package com.binance.monitor.data.local;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class V2SnapshotStoreSourceTest {

    @Test
    public void storeShouldNotCarryChartSeriesSnapshotApis() throws Exception {
        Path file = Paths.get("src/main/java/com/binance/monitor/data/local/V2SnapshotStore.java");
        String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

        assertFalse(source.contains("writeSeriesSnapshot("));
        assertFalse(source.contains("readSeriesSnapshot("));
    }
}
