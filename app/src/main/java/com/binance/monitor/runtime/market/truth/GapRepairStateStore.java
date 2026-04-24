/*
 * 缺口补修状态存储，负责记忆同一缺口是否已经尝试补修、是否仍然缺失，以及何时允许再次补修。
 */
package com.binance.monitor.runtime.market.truth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class GapRepairStateStore {
    private final Map<GapKey, MutableGapRecord> records = new LinkedHashMap<>();

    // 把缺口转换成稳定主键，确保同一缺口不会被重复当成新任务。
    @NonNull
    public static GapKey buildKey(@Nullable String symbol, @NonNull GapDetector.Gap gap) {
        return new GapKey(
                normalizeSymbol(symbol),
                gap.getMissingStartOpenTime(),
                gap.getMissingEndCloseTime()
        );
    }

    // 判断当前缺口在这份上游证据下是否允许再次补修。
    public synchronized boolean shouldRetry(@NonNull GapKey key,
                                            @Nullable String evidenceToken,
                                            long nowMs) {
        String normalizedEvidence = normalizeEvidenceToken(evidenceToken);
        MutableGapRecord record = records.get(key);
        if (record == null) {
            records.put(key, new MutableGapRecord(key, GapStatus.NEW_GAP, nowMs, 0L, 0L, normalizedEvidence));
            return true;
        }
        if (record.status == GapStatus.REPAIRING) {
            return false;
        }
        if (!normalizedEvidence.equals(record.lastEvidenceToken)) {
            record.status = GapStatus.RETRY_READY;
            record.lastEvidenceToken = normalizedEvidence;
            return true;
        }
        return record.status == GapStatus.NEW_GAP || record.status == GapStatus.RETRY_READY;
    }

    // 标记同一缺口已进入正式补修请求中。
    public synchronized void markRepairAttempted(@NonNull GapKey key,
                                                 @Nullable String evidenceToken,
                                                 long nowMs) {
        String normalizedEvidence = normalizeEvidenceToken(evidenceToken);
        MutableGapRecord record = records.get(key);
        if (record == null) {
            record = new MutableGapRecord(key, GapStatus.NEW_GAP, nowMs, 0L, 0L, normalizedEvidence);
            records.put(key, record);
        }
        record.status = GapStatus.REPAIRING;
        record.lastAttemptedAt = Math.max(0L, nowMs);
        record.lastEvidenceToken = normalizedEvidence;
    }

    // 标记这次补修后同一缺口依然存在，进入冻结等待新证据状态。
    public synchronized void markStillMissing(@NonNull GapKey key,
                                              @Nullable String evidenceToken,
                                              long nowMs) {
        String normalizedEvidence = normalizeEvidenceToken(evidenceToken);
        MutableGapRecord record = records.get(key);
        if (record == null) {
            record = new MutableGapRecord(key, GapStatus.NEW_GAP, nowMs, 0L, 0L, normalizedEvidence);
            records.put(key, record);
        }
        record.status = GapStatus.STALLED;
        record.lastEvidenceToken = normalizedEvidence;
    }

    // 标记这个缺口已经补平，后续若同一缺口再次出现，必须等新证据后再重试。
    public synchronized void markResolved(@NonNull GapKey key, long nowMs) {
        MutableGapRecord record = records.get(key);
        if (record == null) {
            record = new MutableGapRecord(key, GapStatus.RESOLVED, nowMs, 0L, nowMs, "");
            records.put(key, record);
            return;
        }
        record.status = GapStatus.RESOLVED;
        record.lastResolvedAt = Math.max(0L, nowMs);
    }

    // 供测试和策略层读取当前缺口状态。
    @Nullable
    public synchronized GapRecord selectRecord(@NonNull GapKey key) {
        MutableGapRecord record = records.get(key);
        if (record == null) {
            return null;
        }
        return record.toImmutable();
    }

    @NonNull
    private static String normalizeEvidenceToken(@Nullable String evidenceToken) {
        return evidenceToken == null ? "" : evidenceToken.trim();
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
    }

    public enum GapStatus {
        NEW_GAP,
        REPAIRING,
        RESOLVED,
        STALLED,
        RETRY_READY
    }

    public static final class GapKey {
        private final String symbol;
        private final long gapStartOpenTime;
        private final long gapEndCloseTime;

        public GapKey(@Nullable String symbol, long gapStartOpenTime, long gapEndCloseTime) {
            this.symbol = normalizeSymbol(symbol);
            this.gapStartOpenTime = Math.max(0L, gapStartOpenTime);
            this.gapEndCloseTime = Math.max(0L, gapEndCloseTime);
        }

        @NonNull
        public String getSymbol() {
            return symbol;
        }

        public long getGapStartOpenTime() {
            return gapStartOpenTime;
        }

        public long getGapEndCloseTime() {
            return gapEndCloseTime;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof GapKey)) {
                return false;
            }
            GapKey that = (GapKey) other;
            return gapStartOpenTime == that.gapStartOpenTime
                    && gapEndCloseTime == that.gapEndCloseTime
                    && Objects.equals(symbol, that.symbol);
        }

        @Override
        public int hashCode() {
            return Objects.hash(symbol, gapStartOpenTime, gapEndCloseTime);
        }
    }

    public static final class GapRecord {
        private final GapKey key;
        private final GapStatus status;
        private final long firstDetectedAt;
        private final long lastAttemptedAt;
        private final long lastResolvedAt;
        private final String lastEvidenceToken;

        private GapRecord(@NonNull GapKey key,
                          @NonNull GapStatus status,
                          long firstDetectedAt,
                          long lastAttemptedAt,
                          long lastResolvedAt,
                          @NonNull String lastEvidenceToken) {
            this.key = key;
            this.status = status;
            this.firstDetectedAt = Math.max(0L, firstDetectedAt);
            this.lastAttemptedAt = Math.max(0L, lastAttemptedAt);
            this.lastResolvedAt = Math.max(0L, lastResolvedAt);
            this.lastEvidenceToken = lastEvidenceToken;
        }

        @NonNull
        public GapKey getKey() {
            return key;
        }

        @NonNull
        public GapStatus getStatus() {
            return status;
        }

        public long getFirstDetectedAt() {
            return firstDetectedAt;
        }

        public long getLastAttemptedAt() {
            return lastAttemptedAt;
        }

        public long getLastResolvedAt() {
            return lastResolvedAt;
        }

        @NonNull
        public String getLastEvidenceToken() {
            return lastEvidenceToken;
        }
    }

    private static final class MutableGapRecord {
        private final GapKey key;
        private GapStatus status;
        private final long firstDetectedAt;
        private long lastAttemptedAt;
        private long lastResolvedAt;
        private String lastEvidenceToken;

        private MutableGapRecord(@NonNull GapKey key,
                                 @NonNull GapStatus status,
                                 long firstDetectedAt,
                                 long lastAttemptedAt,
                                 long lastResolvedAt,
                                 @NonNull String lastEvidenceToken) {
            this.key = key;
            this.status = status;
            this.firstDetectedAt = Math.max(0L, firstDetectedAt);
            this.lastAttemptedAt = Math.max(0L, lastAttemptedAt);
            this.lastResolvedAt = Math.max(0L, lastResolvedAt);
            this.lastEvidenceToken = lastEvidenceToken;
        }

        @NonNull
        private GapRecord toImmutable() {
            return new GapRecord(
                    key,
                    status,
                    firstDetectedAt,
                    lastAttemptedAt,
                    lastResolvedAt,
                    lastEvidenceToken
            );
        }
    }
}
