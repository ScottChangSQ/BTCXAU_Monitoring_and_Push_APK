package com.binance.monitor.data.local;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class V2SnapshotStoreTest {

    @Test
    public void saveAndReadMarketSnapshotShouldRoundTrip() {
        V2SnapshotStore store = new V2SnapshotStore(new FakeStore());

        store.writeMarketSnapshot("{\"syncToken\":\"abc\"}");

        assertEquals("{\"syncToken\":\"abc\"}", store.readMarketSnapshot());
    }

    private static final class FakeStore implements V2SnapshotStore.KeyValueStore {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key) {
            return values.getOrDefault(key, "");
        }

        @Override
        public void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public void clear() {
            values.clear();
        }
    }
}
