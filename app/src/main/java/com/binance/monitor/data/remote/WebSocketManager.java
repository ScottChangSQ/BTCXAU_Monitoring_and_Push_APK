package com.binance.monitor.data.remote;

import android.os.Handler;
import android.os.Looper;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.KlineData;

import org.json.JSONObject;

import java.util.Collection;
import java.util.HashSet;
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
    private final Map<String, Boolean> managedSymbols = new ConcurrentHashMap<>();
    private volatile Listener listener;
    private volatile boolean running;
    private volatile WebSocket socket;
    private volatile int reconnectAttempt;
    private volatile boolean reconnectScheduled;

    public WebSocketManager() {
        client = new OkHttpClient.Builder()
                .pingInterval(AppConstants.WS_PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public synchronized void connect(Collection<String> symbols, Listener listener) {
        this.listener = listener;
        running = true;
        managedSymbols.clear();
        if (symbols != null) {
            for (String symbol : symbols) {
                if (symbol != null && !symbol.trim().isEmpty()) {
                    managedSymbols.put(symbol.trim().toUpperCase(), true);
                }
            }
        }
        reconnectAttempt = 0;
        reconnectScheduled = false;
        connectInternal(false);
    }

    public synchronized void disconnectAll() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        reconnectScheduled = false;
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.close(1000, "disconnect");
        }
        managedSymbols.clear();
        reconnectAttempt = 0;
    }

    public synchronized void forceReconnect(String reason) {
        if (!running || managedSymbols.isEmpty()) {
            return;
        }
        notifyErrorAll(reason);
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.cancel();
        }
        scheduleReconnect(true);
    }

    private synchronized void connectInternal(boolean reconnecting) {
        if (!running || managedSymbols.isEmpty() || socket != null) {
            return;
        }
        int currentAttempt = reconnectAttempt;
        notifyStateAll(false, currentAttempt, reconnecting ? "重连中" : "连接中");
        Request request = new Request.Builder()
                .url(AppConstants.buildCombinedWebSocketUrl(new HashSet<>(managedSymbols.keySet())))
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                synchronized (WebSocketManager.this) {
                    socket = webSocket;
                    reconnectAttempt = 0;
                    reconnectScheduled = false;
                }
                notifyStateAll(true, 0, "已连接");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    JSONObject root = new JSONObject(text);
                    JSONObject payload = root.optJSONObject("data");
                    JSONObject klineSource = payload == null ? root : payload;
                    JSONObject kline = klineSource.optJSONObject("k");
                    if (kline == null) {
                        return;
                    }
                    String symbol = resolveSymbol(root, payload, kline);
                    if (symbol.isEmpty() || !Boolean.TRUE.equals(managedSymbols.get(symbol))) {
                        return;
                    }
                    KlineData data = KlineData.fromSocket(symbol, kline);
                    if (listener != null) {
                        listener.onKlineUpdate(symbol, data);
                    }
                } catch (Exception exception) {
                    notifyErrorAll("消息解析失败: " + exception.getMessage());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                handleSocketTermination(webSocket, "连接关闭: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                String message = t != null ? t.getMessage() : "未知错误";
                handleSocketTermination(webSocket, "连接失败: " + message);
            }
        });
    }

    private String resolveSymbol(JSONObject root, JSONObject payload, JSONObject kline) {
        String symbol = "";
        if (payload != null) {
            symbol = payload.optString("s", "");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            symbol = kline.optString("s", "");
        }
        if (symbol == null || symbol.trim().isEmpty()) {
            String stream = root.optString("stream", "");
            int atIndex = stream.indexOf('@');
            symbol = atIndex > 0 ? stream.substring(0, atIndex) : stream;
        }
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase();
    }

    private void handleSocketTermination(WebSocket terminatedSocket, String reason) {
        synchronized (this) {
            if (socket == terminatedSocket) {
                socket = null;
            }
        }
        notifyErrorAll(reason);
        scheduleReconnect(false);
    }

    private synchronized void scheduleReconnect(boolean immediate) {
        if (!running || managedSymbols.isEmpty()) {
            return;
        }
        if (reconnectScheduled) {
            return;
        }
        reconnectAttempt++;
        final int attempt = reconnectAttempt;
        if (attempt > AppConstants.MAX_RECONNECT_ATTEMPTS) {
            notifyStateAll(false, attempt, "重连已停止");
            return;
        }
        notifyStateAll(false, attempt, "重连中(" + attempt + "/" + AppConstants.MAX_RECONNECT_ATTEMPTS + ")");
        long delay = immediate ? 300L : Math.min(30_000L, 1_500L * attempt);
        reconnectScheduled = true;
        handler.postDelayed(() -> {
            synchronized (WebSocketManager.this) {
                reconnectScheduled = false;
                if (!running || managedSymbols.isEmpty() || socket != null) {
                    return;
                }
                connectInternal(true);
            }
        }, delay);
    }

    private void notifyStateAll(boolean connected, int reconnectAttempt, String message) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        for (String symbol : managedSymbols.keySet()) {
            currentListener.onSocketStateChanged(symbol, connected, reconnectAttempt, message);
        }
    }

    private void notifyErrorAll(String message) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        for (String symbol : managedSymbols.keySet()) {
            currentListener.onSocketError(symbol, message);
        }
    }
}
