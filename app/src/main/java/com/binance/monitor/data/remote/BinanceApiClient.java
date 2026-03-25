package com.binance.monitor.data.remote;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.KlineData;

import org.json.JSONArray;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class BinanceApiClient {

    private final OkHttpClient client;

    public BinanceApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public KlineData fetchLatestClosedKline(String symbol) throws Exception {
        Request request = new Request.Builder()
                .url(AppConstants.buildRestUrl(symbol))
                .get()
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("REST 请求失败: " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("REST 响应为空");
            }
            JSONArray array = new JSONArray(body.string());
            if (array.length() == 0) {
                throw new IOException("K线数据为空");
            }
            long now = System.currentTimeMillis();
            KlineData fallback = null;
            for (int i = array.length() - 1; i >= 0; i--) {
                KlineData item = KlineData.fromRest(symbol, array.getJSONArray(i));
                if (fallback == null) {
                    fallback = item;
                }
                if (item.getCloseTime() < now - 1000L) {
                    return item;
                }
            }
            return fallback;
        }
    }
}
