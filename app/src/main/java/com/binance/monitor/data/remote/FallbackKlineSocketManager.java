package com.binance.monitor.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.model.KlineData;

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

/*
 * Binance 1m K 线回退流管理器，只在 v2 stream 不健康时承担行情回退输入。
 * 它不再承担统一同步主链职责，主要给监控页和悬浮窗补最新展示快照。
 */
public class FallbackKlineSocketManager {

    public interface Listener {
        void onFallbackStreamStateChanged(String symbol, boolean connected, int reconnectAttempt, String message);
        void onFallbackKlineUpdate(String symbol, KlineData data);
        void onFallbackStreamError(String symbol, String message);
    }

    private final OkHttpClient client;
    private final ConfigManager configManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, Boolean> managedSymbols = new ConcurrentHashMap<>();
    private volatile Listener listener;
    private volatile boolean running;
    private volatile WebSocket socket;
    private volatile int reconnectAttempt;
    private volatile boolean reconnectScheduled;

    public FallbackKlineSocketManager() {
        this(null);
    }

    public FallbackKlineSocketManager(Context context) {
        configManager = context == null ? null : ConfigManager.getInstance(context.getApplicationContext());
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
                .url(AppConstants.buildCombinedWebSocketUrl(getConfiguredWebSocketBaseUrl(), new HashSet<>(managedSymbols.keySet())))
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                synchronized (FallbackKlineSocketManager.this) {
                    socket = webSocket;
                    reconnectAttempt = 0;
                    reconnectScheduled = false;
                }
                notifyStateAll(true, 0, "已连接");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    KlineStreamMessageParser.ParsedKline parsed = KlineStreamMessageParser.parse(text);
                    if (parsed == null || parsed.data == null || parsed.symbol == null || parsed.symbol.trim().isEmpty()) {
                        return;
                    }
                    if (!Boolean.TRUE.equals(managedSymbols.get(parsed.symbol))) {
                        return;
                    }
                    if (listener != null) {
                        listener.onFallbackKlineUpdate(parsed.symbol, parsed.data);
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
            synchronized (FallbackKlineSocketManager.this) {
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
            currentListener.onFallbackStreamStateChanged(symbol, connected, reconnectAttempt, message);
        }
    }

    private void notifyErrorAll(String message) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        for (String symbol : managedSymbols.keySet()) {
            currentListener.onFallbackStreamError(symbol, message);
        }
    }

    private String getConfiguredWebSocketBaseUrl() {
        if (configManager == null) {
            return AppConstants.BASE_WS_URL;
        }
        return configManager.getBinanceWebSocketBaseUrl();
    }
}
