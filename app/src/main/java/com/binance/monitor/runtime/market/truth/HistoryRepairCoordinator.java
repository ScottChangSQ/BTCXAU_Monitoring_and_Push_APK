/*
 * 历史修补协调器，负责按目标周期补最近一段正式 1m 底稿，供高周期尾部统一重算。
 */
package com.binance.monitor.runtime.market.truth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.CandleEntry;
import com.binance.monitor.data.model.v2.MarketSeriesPayload;
import com.binance.monitor.data.remote.v2.GatewayV2Client;
import com.binance.monitor.data.repository.MonitorRepository;

import java.util.List;

public final class HistoryRepairCoordinator {
    private final MonitorRepository repository;
    private final GatewayV2Client gatewayV2Client;

    public HistoryRepairCoordinator(@NonNull MonitorRepository repository,
                                    @NonNull GatewayV2Client gatewayV2Client) {
        this.repository = repository;
        this.gatewayV2Client = gatewayV2Client;
    }

    // 为当前周期补最近一段 1m 正式历史，只覆盖最新尾部需要的分钟窗口。
    public void repairRecentMinuteTail(@NonNull String symbol,
                                       @Nullable String intervalKey) throws Exception {
        long requestedAt = System.currentTimeMillis();
        GapDetector.Gap gapBefore = repository.selectMinuteGap(symbol);
        String evidenceToken = gapBefore == null ? "" : repository.buildMinuteGapEvidenceToken(symbol);
        if (gapBefore != null) {
            if (!repository.shouldRetryMinuteGapRepair(symbol, gapBefore, evidenceToken, requestedAt)) {
                return;
            }
            repository.markMinuteGapRepairAttempted(symbol, gapBefore, evidenceToken, requestedAt);
        }
        int minuteLimit = resolveMinuteRepairLimit(intervalKey);
        try {
            MarketSeriesPayload minutePayload = gatewayV2Client.fetchMarketSeries(symbol, "1m", minuteLimit);
            List<CandleEntry> minuteCandles = minutePayload == null ? null : minutePayload.getCandles();
            repository.applyRepairedMinuteWindow(
                    symbol,
                    minuteCandles,
                    minutePayload == null ? null : minutePayload.getLatestPatch(),
                    minutePayload == null ? System.currentTimeMillis() : minutePayload.getServerTime()
            );
            settleGapRepairState(symbol, gapBefore, evidenceToken, System.currentTimeMillis());
        } catch (Exception exception) {
            if (gapBefore != null) {
                repository.markMinuteGapRepairStillMissing(symbol, gapBefore, evidenceToken, System.currentTimeMillis());
            }
            throw exception;
        }
    }

    // 根据目标周期决定最近分钟尾部补档窗口。
    static int resolveMinuteRepairLimit(@Nullable String intervalKey) {
        String normalizedInterval = MarketTruthAggregationHelper.normalizeIntervalKey(intervalKey);
        if ("1m".equals(normalizedInterval)) {
            return 180;
        }
        long intervalMs = MarketTruthAggregationHelper.resolveIntervalMs(normalizedInterval);
        if (intervalMs <= 0L) {
            return 180;
        }
        int intervalMinutes = (int) Math.max(1L, intervalMs / 60_000L);
        return Math.max(60, Math.min(1_500, intervalMinutes + 5));
    }

    // 根据补修后的最新底稿状态，收口同一缺口的后续重试资格。
    private void settleGapRepairState(@NonNull String symbol,
                                      @Nullable GapDetector.Gap gapBefore,
                                      @Nullable String evidenceToken,
                                      long settledAt) {
        if (gapBefore == null) {
            return;
        }
        GapDetector.Gap gapAfter = repository.selectMinuteGap(symbol);
        if (gapAfter == null) {
            repository.markMinuteGapRepairResolved(symbol, gapBefore, settledAt);
            return;
        }
        if (isSameGap(gapBefore, gapAfter)) {
            repository.markMinuteGapRepairStillMissing(symbol, gapBefore, evidenceToken, settledAt);
            return;
        }
        repository.markMinuteGapRepairResolved(symbol, gapBefore, settledAt);
    }

    // 只把“起止完全相同”的缺口视为同一缺口，避免部分补齐后继续冻结新范围。
    private static boolean isSameGap(@NonNull GapDetector.Gap left, @NonNull GapDetector.Gap right) {
        return left.getMissingStartOpenTime() == right.getMissingStartOpenTime()
                && left.getMissingEndCloseTime() == right.getMissingEndCloseTime();
    }
}
