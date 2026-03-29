package com.binance.monitor.data.local;

import android.content.Context;

import com.binance.monitor.data.model.CandleEntry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KlineCacheStore {

    private final File rootDir;

    public KlineCacheStore(Context context) {
        File base = context.getApplicationContext().getFilesDir();
        rootDir = new File(base, "kline_cache");
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
    }

    public synchronized List<CandleEntry> read(String key, int maxCount) {
        File file = resolveFile(key);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            if (builder.length() == 0) {
                return new ArrayList<>();
            }
            JSONArray array = new JSONArray(builder.toString());
            List<CandleEntry> out = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                out.add(CandleEntry.fromJson(item));
            }
            return normalize(out, maxCount);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public synchronized void write(String key, List<CandleEntry> candles, int maxCount) {
        if (candles == null || candles.isEmpty()) {
            return;
        }
        List<CandleEntry> normalized = normalize(candles, maxCount);
        JSONArray array = new JSONArray();
        for (CandleEntry entry : normalized) {
            try {
                array.put(entry.toJson());
            } catch (Exception ignored) {
            }
        }
        File file = resolveFile(key);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            writer.write(array.toString());
        } catch (Exception ignored) {
        }
    }

    private List<CandleEntry> normalize(List<CandleEntry> source, int maxCount) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, CandleEntry> dedup = new LinkedHashMap<>();
        List<CandleEntry> sorted = new ArrayList<>(source);
        Collections.sort(sorted, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        for (CandleEntry item : sorted) {
            dedup.put(item.getOpenTime(), item);
        }
        List<CandleEntry> out = new ArrayList<>(dedup.values());
        int limit = Math.max(1, maxCount);
        if (out.size() > limit) {
            return new ArrayList<>(out.subList(out.size() - limit, out.size()));
        }
        return out;
    }

    private File resolveFile(String key) {
        String safe = key == null ? "default" : key.replaceAll("[^a-zA-Z0-9._-]", "_");
        return new File(rootDir, safe + ".json");
    }
}
