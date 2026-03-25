package com.binance.monitor.data.remote;

import android.os.Handler;
import android.os.Looper;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.KlineData;

import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketManager {

    public interface Listener {
        void onSocketStateChanged(String symbol, boolean connected, int reconnectAttempt, String message);
        void onKlineUpdate(String symbol, KlineData data);
        void onSocketError(String symbol, String message);
    }

    private final OkHttpClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, WebSocket> sockets = new ConcurrentHashMap<>();
    private final Map<String, Integer> reconnectCounts = new ConcurrentHashMap<>();
    private final Map<String, Boolean> managedSymbols = new ConcurrentHashMap<>();
    private volatile Listener listener;
    private volatile boolean running;

    public WebSocketManager() {
        client = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public synchronized void connect(Collection<String> symbols, Listener listener) {
        this.listener = listener;
        running = true;
        for (String symbol : symbols) {
            managedSymbols.put(symbol, true);
            if (!sockets.containsKey(symbol)) {
                connectSymbol(symbol, false);
            }
        }
    }

    public synchronized void disconnectAll() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        for (WebSocket socket : sockets.values()) {
            socket.close(1000, "disconnect");
        }
        sockets.clear();
        managedSymbols.clear();
    }

    private void connectSymbol(String symbol, boolean reconnecting) {
        if (!running || !Boolean.TRUE.equals(managedSymbols.get(symbol))) {
            return;
        }
        Listener currentListener = listener;
        if (currentListener != null) {
            int attempt = reconnectCounts.getOrDefault(symbol, 0);
            currentListener.onSocketStateChanged(symbol, false, attempt,
                    reconnecting ? "重连中" : "连接中");
        }
        Request request = new Request.Builder()
                .url(AppConstants.buildWebSocketUrl(symbol))
                .build();
        WebSocket socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                sockets.put(symbol, webSocket);
                reconnectCounts.put(symbol, 0);
                if (listener != null) {
                    listener.onSocketStateChanged(symbol, true, 0, "已连接");
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject root = new JSONObject(text);
                    JSONObject kline = root.getJSONObject("k");
                    KlineData data = KlineData.fromSocket(symbol, kline);
                    if (listener != null) {
                        listener.onKlineUpdate(symbol, data);
                    }
                } catch (Exception exception) {
                    if (listener != null) {
                        listener.onSocketError(symbol, "消息解析失败: " + exception.getMessage());
                    }
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                sockets.remove(symbol);
                scheduleReconnect(symbol, "连接关闭: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                sockets.remove(symbol);
                String message = t != null ? t.getMessage() : "未知错误";
                scheduleReconnect(symbol, "连接失败: " + message);
            }
        });
        sockets.put(symbol, socket);
    }

    private void scheduleReconnect(String symbol, String reason) {
        if (listener != null) {
            listener.onSocketError(symbol, reason);
        }
        if (!running || !Boolean.TRUE.equals(managedSymbols.get(symbol))) {
            return;
        }
        int nextAttempt = reconnectCounts.getOrDefault(symbol, 0) + 1;
        reconnectCounts.put(symbol, nextAttempt);
        if (nextAttempt > AppConstants.MAX_RECONNECT_ATTEMPTS) {
            if (listener != null) {
                listener.onSocketStateChanged(symbol, false, nextAttempt, "重连已停止");
            }
            return;
        }
        if (listener != null) {
            listener.onSocketStateChanged(symbol, false, nextAttempt,
                    "重连中(" + nextAttempt + "/" + AppConstants.MAX_RECONNECT_ATTEMPTS + ")");
        }
        long delay = Math.min(30000L, 2000L * nextAttempt);
        handler.postDelayed(() -> connectSymbol(symbol, true), delay);
    }
}
