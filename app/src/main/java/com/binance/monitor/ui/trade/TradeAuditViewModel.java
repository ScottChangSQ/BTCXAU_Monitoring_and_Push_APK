/*
 * 交易追踪页 ViewModel，负责聚合本地审计与网关审计并输出回放内容。
 * 与 TradeAuditStore、GatewayV2TradeClient、TradeReplayFormatter 协同工作。
 */
package com.binance.monitor.ui.trade;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.binance.monitor.data.remote.v2.GatewayV2TradeClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TradeAuditViewModel extends AndroidViewModel {
    private static final int RECENT_LIMIT = 20;

    private final TradeAuditStore auditStore;
    private final GatewayV2TradeClient gatewayV2TradeClient;
    private final ExecutorService executorService;
    private final MutableLiveData<List<TradeAuditEntry>> recentEntries = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<TradeReplayFormatter.ReplayContent> replayContent = new MutableLiveData<>();
    private final MutableLiveData<String> currentTraceId = new MutableLiveData<>("");

    public TradeAuditViewModel(@NonNull Application application) {
        super(application);
        this.auditStore = new TradeAuditStore(application.getApplicationContext());
        this.gatewayV2TradeClient = new GatewayV2TradeClient(application.getApplicationContext());
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @NonNull
    public LiveData<List<TradeAuditEntry>> getRecentEntries() {
        return recentEntries;
    }

    @NonNull
    public LiveData<TradeReplayFormatter.ReplayContent> getReplayContent() {
        return replayContent;
    }

    @NonNull
    public LiveData<String> getCurrentTraceId() {
        return currentTraceId;
    }

    public void refreshRecent() {
        executorService.execute(() -> {
            List<TradeAuditEntry> localRecent = auditStore.getRecent(RECENT_LIMIT);
            List<TradeAuditEntry> gatewayRecent = loadRemoteRecent();
            recentEntries.postValue(mergeRecentEntries(localRecent, gatewayRecent));
        });
    }

    public void lookup(@Nullable String traceId) {
        String safeTraceId = traceId == null ? "" : traceId.trim();
        currentTraceId.postValue(safeTraceId);
        executorService.execute(() -> {
            List<TradeAuditEntry> localEntries = auditStore.lookup(safeTraceId);
            List<TradeAuditEntry> gatewayEntries = loadRemoteLookup(safeTraceId);
            replayContent.postValue(TradeReplayFormatter.buildReplay(safeTraceId, localEntries, gatewayEntries));
        });
    }

    @NonNull
    private List<TradeAuditEntry> loadRemoteRecent() {
        try {
            return gatewayV2TradeClient.auditRecent(RECENT_LIMIT);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    @NonNull
    private List<TradeAuditEntry> loadRemoteLookup(@NonNull String traceId) {
        if (traceId.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return gatewayV2TradeClient.auditLookup(traceId);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    @NonNull
    private List<TradeAuditEntry> mergeRecentEntries(@NonNull List<TradeAuditEntry> localEntries,
                                                     @NonNull List<TradeAuditEntry> gatewayEntries) {
        Map<String, TradeAuditEntry> merged = new LinkedHashMap<>();
        appendLatestTraceEntry(merged, localEntries);
        appendLatestTraceEntry(merged, gatewayEntries);
        return new ArrayList<>(merged.values());
    }

    private void appendLatestTraceEntry(@NonNull Map<String, TradeAuditEntry> merged,
                                        @Nullable List<TradeAuditEntry> entries) {
        if (entries == null) {
            return;
        }
        for (TradeAuditEntry entry : entries) {
            if (entry == null || entry.getTraceId().isEmpty()) {
                continue;
            }
            if (!merged.containsKey(entry.getTraceId())) {
                merged.put(entry.getTraceId(), entry);
            }
        }
    }

    @Override
    protected void onCleared() {
        executorService.shutdownNow();
        super.onCleared();
    }
}
