/*
 * v2 同步流客户端，负责连接网关 /v2/stream 并解析统一同步消息。
 * MonitorService 通过它接收账户/市场同步信号，再决定是否刷新本地展示。
 */
package com.binance.monitor.data.remote.v2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.binance.monitor.data.local.ConfigManager;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class GatewayV2StreamClient {

    public interface Listener {
        void onStateChanged(boolean connected, String message);
        void onMessage(StreamMessage message);
        void onError(String message);
    }

    private final OkHttpClient client;
    @Nullable
    private final ConfigManager configManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile Listener listener;
    private volatile boolean running;
    private volatile WebSocket socket;
    private volatile boolean reconnectScheduled;
    private volatile int reconnectAttempt;

    public GatewayV2StreamClient(@Nullable Context context) {
        client = new OkHttpClient.Builder()
                .pingInterval(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        configManager = context == null
                ? null
                : ConfigManager.getInstance(context.getApplicationContext());
    }

    // 启动 v2 stream 连接，主线程服务只保留一条统一同步链路。
    public synchronized void connect(@Nullable Listener listener) {
        this.listener = listener;
        running = true;
        reconnectScheduled = false;
        reconnectAttempt = 0;
        connectInternal(false);
    }

    // 关闭当前 v2 stream 连接并取消后续重连。
    public synchronized void disconnect() {
        running = false;
        reconnectScheduled = false;
        reconnectAttempt = 0;
        handler.removeCallbacksAndMessages(null);
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.close(1000, "disconnect");
        }
    }

    private synchronized void connectInternal(boolean reconnecting) {
        if (!running || socket != null) {
            return;
        }
        notifyState(false, reconnecting ? "重连中" : "连接中");
        Request request = new Request.Builder()
                .url(resolveStreamUrl())
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                synchronized (GatewayV2StreamClient.this) {
                    socket = webSocket;
                    reconnectAttempt = 0;
                    reconnectScheduled = false;
                }
                notifyState(true, "已连接");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                try {
                    StreamMessage message = parseMessage(text);
                    Listener currentListener = listener;
                    if (currentListener != null) {
                        currentListener.onMessage(message);
                    }
                } catch (Exception exception) {
                    notifyError("v2 stream 消息解析失败: " + exception.getMessage());
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                handleTermination(webSocket, "连接关闭: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                handleTermination(webSocket, "连接失败: " + (t == null ? "未知错误" : t.getMessage()));
            }
        });
    }

    private void handleTermination(WebSocket terminatedSocket, String reason) {
        synchronized (this) {
            if (socket == terminatedSocket) {
                socket = null;
            }
        }
        notifyError(reason);
        scheduleReconnect();
    }

    private synchronized void scheduleReconnect() {
        if (!running || reconnectScheduled) {
            return;
        }
        reconnectScheduled = true;
        reconnectAttempt++;
        long delayMs = Math.min(30_000L, 1_500L * Math.max(1, reconnectAttempt));
        handler.postDelayed(() -> {
            synchronized (GatewayV2StreamClient.this) {
                reconnectScheduled = false;
                if (!running || socket != null) {
                    return;
                }
                connectInternal(true);
            }
        }, delayMs);
    }

    // 解析服务端 v2 stream 消息，统一保留类型、payload 和同步 token。
    public static StreamMessage parseMessage(String body) throws Exception {
        JSONObject json = new JSONObject(body == null ? "{}" : body);
        JSONObject payload = json.optJSONObject("payload");
        if (payload == null) {
            payload = new JSONObject();
        }
        return new StreamMessage(
                json.optString("type", ""),
                payload,
                json.optLong("serverTime", 0L),
                json.optString("syncToken", "")
        );
    }

    private void notifyState(boolean connected, String message) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        currentListener.onStateChanged(connected, message == null ? "" : message);
    }

    private void notifyError(String message) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        currentListener.onError(message == null ? "" : message);
    }

    private String resolveStreamUrl() {
        String baseUrl = configManager == null ? "" : configManager.getMt5GatewayBaseUrl();
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.startsWith("https://")) {
            return "wss://" + normalized.substring("https://".length()) + "/v2/stream";
        }
        if (normalized.startsWith("http://")) {
            return "ws://" + normalized.substring("http://".length()) + "/v2/stream";
        }
        if (normalized.startsWith("wss://") || normalized.startsWith("ws://")) {
            return normalized + "/v2/stream";
        }
        return "ws://127.0.0.1:8787/v2/stream";
    }

    public static class StreamMessage {
        private final String type;
        private final JSONObject payload;
        private final long serverTime;
        private final String syncToken;

        public StreamMessage(String type, JSONObject payload, long serverTime, String syncToken) {
            this.type = type == null ? "" : type;
            this.payload = payload == null ? new JSONObject() : payload;
            this.serverTime = serverTime;
            this.syncToken = syncToken == null ? "" : syncToken;
        }

        public String getType() {
            return type;
        }

        public JSONObject getPayload() {
            return payload;
        }

        public long getServerTime() {
            return serverTime;
        }

        public String getSyncToken() {
            return syncToken;
        }
    }
}
