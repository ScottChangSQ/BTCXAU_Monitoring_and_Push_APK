/*
 * v2 同步流客户端，负责连接网关 /v2/stream 并解析统一同步消息。
 * MonitorService 通过它接收服务端已发布的 stream 事件，再决定是否刷新本地展示。
 */
package com.binance.monitor.data.remote.v2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.runtime.ConnectionStage;
import com.binance.monitor.util.GatewayUrlResolver;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class GatewayV2StreamClient {

    public interface Listener {
        void onStateChanged(ConnectionEvent event);
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
    private volatile long connectionId;

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
        connectionId++;
        handler.removeCallbacksAndMessages(null);
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.cancel();
        }
    }

    // 在前后台切换或 watchdog 判定失活时，显式重建统一 stream 连接。
    public synchronized void restart(String reason) {
        if (!running) {
            return;
        }
        reconnectScheduled = false;
        reconnectAttempt = 0;
        connectionId++;
        handler.removeCallbacksAndMessages(null);
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.cancel();
        }
        connectInternal(true);
    }

    private synchronized void connectInternal(boolean reconnecting) {
        if (!running || socket != null) {
            return;
        }
        final long activeConnectionId = ++connectionId;
        notifyState(reconnecting ? ConnectionStage.RECONNECTING : ConnectionStage.CONNECTING,
                reconnecting ? "重连中" : "连接中");
        Request request = new Request.Builder()
                .url(resolveStreamUrl())
                .build();
        socket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                synchronized (GatewayV2StreamClient.this) {
                    if (activeConnectionId != connectionId) {
                        webSocket.cancel();
                        return;
                    }
                    socket = webSocket;
                    reconnectAttempt = 0;
                    reconnectScheduled = false;
                }
                notifyState(ConnectionStage.CONNECTED, "已连接");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                if (activeConnectionId != connectionId) {
                    return;
                }
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
                handleTermination(webSocket, activeConnectionId, "连接关闭: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                handleTermination(webSocket, activeConnectionId,
                        "连接失败: " + (t == null ? "未知错误" : t.getMessage()));
            }
        });
    }

    private void handleTermination(WebSocket terminatedSocket, long activeConnectionId, String reason) {
        boolean shouldReconnect;
        synchronized (this) {
            if (socket != terminatedSocket || activeConnectionId != connectionId) {
                return;
            }
            socket = null;
            shouldReconnect = running;
        }
        notifyState(
                shouldReconnect ? ConnectionStage.RECONNECTING : ConnectionStage.DISCONNECTED,
                shouldReconnect ? "重连中" : reason
        );
        notifyError(reason);
        if (shouldReconnect) {
            scheduleReconnect();
        }
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

    // 解析服务端 v2 stream 消息，严格按新根级事件字段读取。
    public static StreamMessage parseMessage(String body) throws Exception {
        JSONObject json = new JSONObject(body == null ? "{}" : body);
        JSONObject revisions = json.optJSONObject("revisions");
        if (revisions == null) {
            revisions = new JSONObject();
        }
        JSONObject changes = json.optJSONObject("changes");
        if (changes == null) {
            changes = new JSONObject();
        }
        return new StreamMessage(
                json.optString("type", ""),
                json.optLong("busSeq", 0L),
                json.optLong("publishedAt", 0L),
                revisions,
                changes,
                json.optLong("serverTime", 0L),
                buildPlannerEnvelope(revisions, changes)
        );
    }

    // 保留原 getPayload() 调用口，但内容改为新协议下的 revisions+changes 封套。
    private static JSONObject buildPlannerEnvelope(JSONObject revisions, JSONObject changes) throws Exception {
        return new JSONObject()
                .put("revisions", revisions == null ? new JSONObject() : revisions)
                .put("changes", changes == null ? new JSONObject() : changes);
    }

    private void notifyState(ConnectionStage stage, String message) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        currentListener.onStateChanged(new ConnectionEvent(stage, message == null ? "" : message));
    }

    private void notifyError(String message) {
        Listener currentListener = listener;
        if (currentListener == null) {
            return;
        }
        currentListener.onError(message == null ? "" : message);
    }

    private String resolveStreamUrl() {
        String baseUrl = configManager == null
                ? AppConstants.MT5_GATEWAY_BASE_URL
                : configManager.getMt5GatewayBaseUrl();
        String gatewayRoot = GatewayUrlResolver.resolveGatewayRootBaseUrl(
                baseUrl,
                AppConstants.MT5_GATEWAY_BASE_URL
        );
        if (gatewayRoot.endsWith("/")) {
            gatewayRoot = gatewayRoot.substring(0, gatewayRoot.length() - 1);
        }
        if (gatewayRoot.startsWith("https://")) {
            return "wss://" + gatewayRoot.substring("https://".length()) + "/v2/stream";
        }
        if (gatewayRoot.startsWith("http://")) {
            return "ws://" + gatewayRoot.substring("http://".length()) + "/v2/stream";
        }
        return gatewayRoot + "/v2/stream";
    }

    public static class StreamMessage {
        private final String type;
        private final long busSeq;
        private final long publishedAt;
        private final JSONObject revisions;
        private final JSONObject changes;
        private final long serverTime;
        private final JSONObject payload;

        public StreamMessage(String type,
                             long busSeq,
                             long publishedAt,
                             JSONObject revisions,
                             JSONObject changes,
                             long serverTime,
                             JSONObject payload) {
            this.type = type == null ? "" : type;
            this.busSeq = busSeq;
            this.publishedAt = publishedAt;
            this.revisions = revisions == null ? new JSONObject() : revisions;
            this.changes = changes == null ? new JSONObject() : changes;
            this.serverTime = serverTime;
            this.payload = payload == null ? new JSONObject() : payload;
        }

        public String getType() {
            return type;
        }

        public long getBusSeq() {
            return busSeq;
        }

        public long getPublishedAt() {
            return publishedAt;
        }

        public JSONObject getRevisions() {
            return revisions;
        }

        public JSONObject getChanges() {
            return changes;
        }

        public JSONObject getPayload() {
            return payload;
        }

        public long getServerTime() {
            return serverTime;
        }
    }

    public static class ConnectionEvent {
        private final ConnectionStage stage;
        private final String message;

        public ConnectionEvent(@Nullable ConnectionStage stage, @Nullable String message) {
            this.stage = stage == null ? ConnectionStage.CONNECTING : stage;
            this.message = message == null ? "" : message;
        }

        public ConnectionStage getStage() {
            return stage;
        }

        public String getMessage() {
            return message;
        }

        public boolean isConnected() {
            return stage == ConnectionStage.CONNECTED;
        }
    }
}
