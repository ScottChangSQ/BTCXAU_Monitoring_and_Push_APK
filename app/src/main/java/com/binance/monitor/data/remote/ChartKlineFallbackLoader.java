/*
 * 负责图表 K 线在 REST 全部失败时的安全回退，
 * 供 BinanceApiClient 复用，避免图表因单一路径不可用而彻底空白。
 */
package com.binance.monitor.data.remote;

import com.binance.monitor.data.model.CandleEntry;

import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class ChartKlineFallbackLoader {

    interface RestArrayFetcher {
        // 读取指定 URL 的 K 线数组数据。
        JSONArray fetch(String url) throws Exception;
    }

    interface HistoryFallbackFetcher {
        // 在 REST 不可用时提供历史回退数据。
        List<CandleEntry> fetch() throws Exception;
    }

    // 先走图表 REST，再按需回退到通用历史数据。
    List<CandleEntry> load(Collection<String> candidateUrls,
                          String symbol,
                          Long endTimeInclusive,
                          RestArrayFetcher restArrayFetcher,
                          HistoryFallbackFetcher historyFallbackFetcher) throws Exception {
        Exception lastRestError = null;
        if (candidateUrls != null) {
            for (String url : candidateUrls) {
                try {
                    JSONArray array = restArrayFetcher.fetch(url);
                    if (array == null || array.length() == 0) {
                        lastRestError = new IOException("返回空数据: " + url);
                        continue;
                    }
                    List<CandleEntry> parsed = new ArrayList<>(array.length());
                    for (int i = 0; i < array.length(); i++) {
                        CandleEntry item = CandleEntry.fromRest(symbol, array.getJSONArray(i));
                        if (endTimeInclusive == null || item.getOpenTime() <= endTimeInclusive) {
                            parsed.add(item);
                        }
                    }
                    List<CandleEntry> normalized = normalizeCandles(parsed);
                    if (!normalized.isEmpty()) {
                        return normalized;
                    }
                    lastRestError = new IOException("解析后为空: " + url);
                } catch (Exception e) {
                    lastRestError = new IOException("请求失败(" + url + "): " + e.getMessage(), e);
                }
            }
        }

        Exception fallbackError = null;
        if (historyFallbackFetcher != null) {
            try {
                List<CandleEntry> fallback = normalizeCandles(historyFallbackFetcher.fetch());
                if (!fallback.isEmpty()) {
                    return fallback;
                }
            } catch (Exception e) {
                fallbackError = e;
            }
        }

        if (fallbackError != null && lastRestError != null) {
            throw new IOException(
                    "图表K线REST请求失败: "
                            + lastRestError.getMessage()
                            + "；历史回退失败: "
                            + fallbackError.getMessage(),
                    fallbackError
            );
        }
        if (lastRestError != null) {
            throw new IOException("图表K线REST请求失败: " + lastRestError.getMessage(), lastRestError);
        }
        if (fallbackError != null) {
            throw fallbackError;
        }
        throw new IOException("图表K线REST请求失败");
    }

    // 统一按开盘时间排序并去重，保证图表消费的数据稳定。
    private List<CandleEntry> normalizeCandles(List<CandleEntry> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<CandleEntry> sorted = new ArrayList<>(source);
        Collections.sort(sorted, (left, right) -> Long.compare(left.getOpenTime(), right.getOpenTime()));
        List<CandleEntry> out = new ArrayList<>();
        Long lastOpenTime = null;
        for (CandleEntry item : sorted) {
            if (item == null) {
                continue;
            }
            if (lastOpenTime != null && item.getOpenTime() == lastOpenTime) {
                if (!out.isEmpty()) {
                    out.set(out.size() - 1, item);
                }
                continue;
            }
            out.add(item);
            lastOpenTime = item.getOpenTime();
        }
        return out;
    }
}
