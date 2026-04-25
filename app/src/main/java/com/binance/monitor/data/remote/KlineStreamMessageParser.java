/*
 * 行情 K 线流消息解析器，负责把原始 WebSocket 文本统一解析成可直接消费的 KlineData。
 * 回退 K 线流管理器通过这里兼容 combined stream 与 direct stream 两种载荷结构。
 */
package com.binance.monitor.data.remote;

import androidx.annotation.Nullable;

import com.binance.monitor.data.model.KlineData;

import org.json.JSONObject;

import java.util.Locale;

final class KlineStreamMessageParser {

    private KlineStreamMessageParser() {
    }

    // 解析单条 K 线流消息；无法解析时返回 null，调用方可安全忽略。
    @Nullable
    static ParsedKline parse(@Nullable String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(text);
            JSONObject payload = root.optJSONObject("data");
            JSONObject source = payload == null ? root : payload;
            String symbol = resolveSymbol(root, source);
            if (symbol.isEmpty()) {
                return null;
            }
            JSONObject kline = source.optJSONObject("k");
            if (kline == null) {
                return null;
            }
            KlineData data = KlineData.parseSocketOrNull(symbol, kline);
            return data == null ? null : new ParsedKline(symbol, data);
        } catch (Exception ignored) {
            return null;
        }
    }

    // 优先读标准字段 s，缺失时再从 stream 名里回推交易对。
    private static String resolveSymbol(JSONObject root, JSONObject source) {
        String symbol = source == null ? "" : source.optString("s", "");
        if (symbol == null || symbol.trim().isEmpty()) {
            String stream = root == null ? "" : root.optString("stream", "");
            int atIndex = stream.indexOf('@');
            symbol = atIndex > 0 ? stream.substring(0, atIndex) : stream;
        }
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    static final class ParsedKline {
        final String symbol;
        final KlineData data;

        ParsedKline(String symbol, KlineData data) {
            this.symbol = symbol == null ? "" : symbol;
            this.data = data;
        }
    }
}
