package com.binance.monitor.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.binance.monitor.data.local.AbnormalRecordManager;
import com.binance.monitor.data.local.ConfigManager;
import com.binance.monitor.data.local.LogManager;
import com.binance.monitor.data.model.AbnormalRecord;
import com.binance.monitor.data.model.AppLogEntry;
import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.data.model.v2.MarketSeriesPayload;
import com.binance.monitor.runtime.market.MarketRuntimeStore;
import com.binance.monitor.runtime.market.model.MarketRuntimeSnapshot;
import com.binance.monitor.runtime.market.model.SymbolMarketWindow;
import com.binance.monitor.runtime.market.truth.GapDetector;
import com.binance.monitor.runtime.market.truth.GapRepairStateStore;
import com.binance.monitor.runtime.market.truth.IntervalProjectionStore;
import com.binance.monitor.runtime.market.truth.MarketTruthCenter;
import com.binance.monitor.runtime.market.truth.MinuteBaseStore;
import com.binance.monitor.runtime.market.truth.model.CurrentMinuteSnapshot;
import com.binance.monitor.runtime.market.truth.model.MarketDisplaySeries;
import com.binance.monitor.runtime.market.truth.model.MarketTruthSnapshot;
import com.binance.monitor.runtime.market.truth.model.MarketTruthSymbolState;

import java.util.Collections;
import java.util.List;

public class MonitorRepository {

    private static volatile MonitorRepository instance;

    private final ConfigManager configManager;
    private final LogManager logManager;
    private final AbnormalRecordManager recordManager;
    private final MutableLiveData<String> connectionStatus = new MutableLiveData<>("连接中");
    private final MutableLiveData<Boolean> monitoringEnabled;
    private final MutableLiveData<Long> lastUpdateTime = new MutableLiveData<>(0L);
    private final MutableLiveData<MarketRuntimeSnapshot> marketRuntimeSnapshotLiveData =
            new MutableLiveData<>(MarketRuntimeSnapshot.empty());
    private final MutableLiveData<MarketTruthSnapshot> marketTruthSnapshotLiveData =
            new MutableLiveData<>(MarketTruthSnapshot.empty());
    private final MarketRuntimeStore marketRuntimeStore = new MarketRuntimeStore();
    private final GapDetector gapDetector = new GapDetector();
    private final GapRepairStateStore gapRepairStateStore = new GapRepairStateStore();
    private final MarketTruthCenter marketTruthCenter = new MarketTruthCenter(
            new MinuteBaseStore(),
            new IntervalProjectionStore(),
            gapDetector
    );

    private MonitorRepository(Context context) {
        Context appContext = context.getApplicationContext();
        configManager = ConfigManager.getInstance(appContext);
        logManager = LogManager.getInstance(appContext);
        recordManager = AbnormalRecordManager.getInstance(appContext);
        monitoringEnabled = new MutableLiveData<>(configManager.isMonitoringEnabled());
    }

    public static MonitorRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (MonitorRepository.class) {
                if (instance == null) {
                    instance = new MonitorRepository(context);
                }
            }
        }
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public AbnormalRecordManager getRecordManager() {
        return recordManager;
    }

    public LiveData<String> getConnectionStatus() {
        return connectionStatus;
    }

    public LiveData<Boolean> getMonitoringEnabled() {
        return monitoringEnabled;
    }

    public LiveData<Long> getLastUpdateTime() {
        return lastUpdateTime;
    }

    // 返回市场统一运行态快照，供正式市场消费链直接观察。
    public LiveData<MarketRuntimeSnapshot> getMarketRuntimeSnapshotLiveData() {
        return marketRuntimeSnapshotLiveData;
    }

    // 返回统一市场真值快照，供图表、悬浮窗和其它显示链共用。
    public LiveData<MarketTruthSnapshot> getMarketTruthSnapshotLiveData() {
        return marketTruthSnapshotLiveData;
    }

    public LiveData<List<AppLogEntry>> getLogs() {
        return logManager.getLogsLiveData();
    }

    public LiveData<List<AbnormalRecord>> getRecords() {
        return recordManager.getRecordsLiveData();
    }

    public void setConnectionStatus(String status) {
        connectionStatus.postValue(status);
    }

    public void setMonitoringEnabled(boolean enabled) {
        configManager.setMonitoringEnabled(enabled);
        monitoringEnabled.postValue(enabled);
    }

    public synchronized void applyMarketRuntimeSnapshot(@Nullable MarketRuntimeSnapshot runtimeSnapshot) {
        if (runtimeSnapshot == null) {
            return;
        }
        MarketRuntimeSnapshot latestSnapshot = marketRuntimeStore.applySymbolWindows(
                new java.util.ArrayList<>(runtimeSnapshot.getSymbolWindows().values()),
                runtimeSnapshot.getUpdatedAt()
        );
        for (SymbolMarketWindow window : latestSnapshot.getSymbolWindows().values()) {
            if (window == null) {
                continue;
            }
            marketTruthCenter.applyStreamWindow(window, latestSnapshot.getUpdatedAt());
        }
        marketTruthSnapshotLiveData.postValue(marketTruthCenter.getSnapshot());
        marketRuntimeSnapshotLiveData.postValue(latestSnapshot);
        publishDisplaySnapshotsLocked(latestSnapshot);
    }

    public synchronized double selectLatestPrice(@Nullable String symbol) {
        return marketTruthCenter.getSnapshot().selectLatestPrice(symbol);
    }

    // 返回指定品种最近一次真实分钟推进时间，供图表刷新门闩判断主链是否仍在前进。
    public synchronized long selectTruthProgressAt(@Nullable String symbol) {
        MarketTruthSymbolState truthState = marketTruthCenter.getSnapshot().getSymbolState(symbol);
        return truthState == null ? 0L : Math.max(0L, truthState.getLastTruthUpdateAt());
    }

    // 返回当前闭合 1m 底稿中的首个缺口，供正式补修链判断是否需要补历史。
    @Nullable
    public synchronized GapDetector.Gap selectMinuteGap(@Nullable String symbol) {
        MarketTruthSymbolState truthState = marketTruthCenter.getSnapshot().getSymbolState(symbol);
        if (truthState == null) {
            return null;
        }
        return gapDetector.findMinuteGap(truthState.getClosedMinutes(), 60_000L);
    }

    // 把当前闭合分钟前沿编码成“是否有新证据”的稳定口径，避免同一缺口无限重试。
    @NonNull
    public synchronized String buildMinuteGapEvidenceToken(@Nullable String symbol) {
        MarketTruthSymbolState truthState = marketTruthCenter.getSnapshot().getSymbolState(symbol);
        if (truthState == null || truthState.getLatestClosedMinute() == null) {
            return "";
        }
        CandleEntry latestClosed = truthState.getLatestClosedMinute();
        return truthState.getSymbol()
                + '|'
                + latestClosed.getOpenTime()
                + '|'
                + latestClosed.getCloseTime()
                + '|'
                + latestClosed.getClose();
    }

    // 判断当前缺口在这份证据下是否允许再次补修。
    public synchronized boolean shouldRetryMinuteGapRepair(@Nullable String symbol,
                                                           @Nullable GapDetector.Gap gap,
                                                           @Nullable String evidenceToken,
                                                           long nowMs) {
        if (gap == null) {
            return false;
        }
        GapRepairStateStore.GapKey key = GapRepairStateStore.buildKey(symbol, gap);
        return gapRepairStateStore.shouldRetry(key, evidenceToken, nowMs);
    }

    // 标记当前缺口已经进入正式补修。
    public synchronized void markMinuteGapRepairAttempted(@Nullable String symbol,
                                                          @Nullable GapDetector.Gap gap,
                                                          @Nullable String evidenceToken,
                                                          long nowMs) {
        if (gap == null) {
            return;
        }
        GapRepairStateStore.GapKey key = GapRepairStateStore.buildKey(symbol, gap);
        gapRepairStateStore.markRepairAttempted(key, evidenceToken, nowMs);
    }

    // 标记这次补修后，同一缺口依然存在。
    public synchronized void markMinuteGapRepairStillMissing(@Nullable String symbol,
                                                             @Nullable GapDetector.Gap gap,
                                                             @Nullable String evidenceToken,
                                                             long nowMs) {
        if (gap == null) {
            return;
        }
        GapRepairStateStore.GapKey key = GapRepairStateStore.buildKey(symbol, gap);
        gapRepairStateStore.markStillMissing(key, evidenceToken, nowMs);
    }

    // 标记这个缺口已经补平。
    public synchronized void markMinuteGapRepairResolved(@Nullable String symbol,
                                                         @Nullable GapDetector.Gap gap,
                                                         long nowMs) {
        if (gap == null) {
            return;
        }
        GapRepairStateStore.GapKey key = GapRepairStateStore.buildKey(symbol, gap);
        gapRepairStateStore.markResolved(key, nowMs);
    }

    // 返回统一真值中心产出的当前分钟读模型，供图表和悬浮窗共用。
    @NonNull
    public synchronized CurrentMinuteSnapshot selectCurrentMinuteSnapshot(@Nullable String symbol) {
        return marketTruthCenter.getSnapshot().selectCurrentMinute(symbol);
    }

    @Nullable
    public synchronized KlineData selectClosedMinute(@Nullable String symbol) {
        CandleEntry truthClosedMinute = marketTruthCenter.getSnapshot().selectClosedMinute(symbol);
        if (truthClosedMinute != null) {
            return toKlineData(truthClosedMinute, true);
        }
        return null;
    }

    @Nullable
    public synchronized KlineData selectDisplayKline(@Nullable String symbol) {
        CandleEntry truthDisplayMinute = marketTruthCenter.getSnapshot().selectDisplayMinute(symbol);
        if (truthDisplayMinute != null) {
            return toKlineData(truthDisplayMinute, false);
        }
        return null;
    }

    // 返回统一真值中心已经整理好的目标周期显示序列。
    @NonNull
    public synchronized MarketDisplaySeries selectDisplaySeries(@Nullable String symbol,
                                                                @Nullable String intervalKey,
                                                                int limit) {
        return marketTruthCenter.getSnapshot().selectDisplaySeries(symbol, intervalKey, limit);
    }

    // 把 REST 返回的周期历史写入统一市场真值中心。
    public synchronized void applyMarketSeriesPayload(@Nullable String symbol,
                                                      @Nullable String intervalKey,
                                                      @Nullable MarketSeriesPayload payload) {
        if (symbol == null || symbol.trim().isEmpty() || intervalKey == null || intervalKey.trim().isEmpty() || payload == null) {
            return;
        }
        marketTruthCenter.applyRestSeries(
                symbol,
                intervalKey,
                payload.getCandles(),
                payload.getLatestPatch(),
                payload.getServerTime()
        );
        publishTruthSnapshotLocked();
    }

    // 把补档得到的 1m 正式历史回写统一市场真值中心。
    public synchronized void applyRepairedMinuteHistory(@Nullable String symbol,
                                                        @Nullable List<CandleEntry> minuteCandles,
                                                        long updatedAt) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return;
        }
        marketTruthCenter.applyRepairedMinuteHistory(symbol, minuteCandles, updatedAt);
        publishTruthSnapshotLocked();
    }

    // 把补档得到的 1m 闭合历史和当前 patch 一起回写统一市场真值中心。
    public synchronized void applyRepairedMinuteWindow(@Nullable String symbol,
                                                       @Nullable List<CandleEntry> minuteCandles,
                                                       @Nullable CandleEntry latestPatch,
                                                       long updatedAt) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return;
        }
        marketTruthCenter.applyRepairedMinuteWindow(symbol, minuteCandles, latestPatch, updatedAt);
        publishTruthSnapshotLocked();
    }

    @NonNull
    public synchronized String selectMarketWindowSignature(@Nullable String symbol) {
        MarketTruthSymbolState truthState = marketTruthCenter.getSnapshot().getSymbolState(symbol);
        if (truthState != null) {
            return buildTruthSignature(truthState);
        }
        SymbolMarketWindow symbolWindow = marketRuntimeStore.getSnapshot().getSymbolWindow(symbol);
        return symbolWindow == null ? "" : symbolWindow.buildSignature();
    }

    public void addRecord(AbnormalRecord record) {
        recordManager.addRecord(record);
    }

    private void publishDisplaySnapshotsLocked(@NonNull MarketRuntimeSnapshot snapshot) {
        lastUpdateTime.postValue(snapshot.getUpdatedAt());
    }

    private void publishTruthSnapshotLocked() {
        MarketTruthSnapshot snapshot = marketTruthCenter.getSnapshot();
        marketTruthSnapshotLiveData.postValue(snapshot);
        lastUpdateTime.postValue(snapshot.getUpdatedAt());
    }

    @NonNull
    private static KlineData toKlineData(@NonNull CandleEntry candle, boolean closed) {
        return new KlineData(
                candle.getSymbol(),
                candle.getOpen(),
                candle.getHigh(),
                candle.getLow(),
                candle.getClose(),
                candle.getVolume(),
                candle.getQuoteVolume(),
                candle.getOpenTime(),
                candle.getCloseTime(),
                closed
        );
    }

    @NonNull
    private static String buildTruthSignature(@NonNull MarketTruthSymbolState state) {
        CandleEntry closedMinute = state.getLatestClosedMinute();
        CandleEntry draftMinute = state.getDraftMinute();
        return state.getSymbol()
                + '|'
                + state.getLatestPrice()
                + '|'
                + buildCandleSignature(closedMinute)
                + '|'
                + buildCandleSignature(draftMinute);
    }

    @NonNull
    private static String buildCandleSignature(@Nullable CandleEntry candle) {
        if (candle == null) {
            return "";
        }
        return candle.getOpenTime()
                + ":"
                + candle.getCloseTime()
                + ":"
                + candle.getClose()
                + ":"
                + candle.getVolume();
    }
}
