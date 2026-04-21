/*
 * 统一 revision 中心，负责收口不同真值域的单调递增版本号。
 */
package com.binance.monitor.runtime.revision;

import java.util.EnumMap;
import java.util.Map;

public final class RuntimeRevisionCenter {
    private final Map<RevisionType, Long> revisions = new EnumMap<>(RevisionType.class);
    private final Map<RevisionType, String> signatures = new EnumMap<>(RevisionType.class);

    public RuntimeRevisionCenter() {
        clear();
    }

    public synchronized long current(RevisionType type) {
        return revisions.get(type);
    }

    public synchronized long advance(RevisionType type) {
        long nextRevision = revisions.get(type) + 1L;
        revisions.put(type, nextRevision);
        return nextRevision;
    }

    public synchronized long advanceIfChanged(RevisionType type, String signature) {
        String safeSignature = signature == null ? "" : signature;
        if (safeSignature.equals(signatures.get(type))) {
            return revisions.get(type);
        }
        signatures.put(type, safeSignature);
        return advance(type);
    }

    public synchronized RevisionSnapshot snapshot() {
        return new RevisionSnapshot(
                revisions.get(RevisionType.MARKET_BASE),
                revisions.get(RevisionType.MARKET_WINDOW),
                revisions.get(RevisionType.ACCOUNT_RUNTIME),
                revisions.get(RevisionType.ACCOUNT_HISTORY),
                revisions.get(RevisionType.PRODUCT_RUNTIME)
        );
    }

    public synchronized void clear() {
        for (RevisionType type : RevisionType.values()) {
            revisions.put(type, 0L);
            signatures.put(type, "");
        }
    }

    public enum RevisionType {
        MARKET_BASE("marketBaseRevision"),
        MARKET_WINDOW("marketWindowRevision"),
        ACCOUNT_RUNTIME("accountRuntimeRevision"),
        ACCOUNT_HISTORY("accountHistoryRevision"),
        PRODUCT_RUNTIME("productRuntimeRevision");

        private final String fieldName;

        RevisionType(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    public static final class RevisionSnapshot {
        private final long marketBaseRevision;
        private final long marketWindowRevision;
        private final long accountRuntimeRevision;
        private final long accountHistoryRevision;
        private final long productRuntimeRevision;

        RevisionSnapshot(long marketBaseRevision,
                         long marketWindowRevision,
                         long accountRuntimeRevision,
                         long accountHistoryRevision,
                         long productRuntimeRevision) {
            this.marketBaseRevision = marketBaseRevision;
            this.marketWindowRevision = marketWindowRevision;
            this.accountRuntimeRevision = accountRuntimeRevision;
            this.accountHistoryRevision = accountHistoryRevision;
            this.productRuntimeRevision = productRuntimeRevision;
        }

        public long getMarketBaseRevision() {
            return marketBaseRevision;
        }

        public long getMarketWindowRevision() {
            return marketWindowRevision;
        }

        public long getAccountRuntimeRevision() {
            return accountRuntimeRevision;
        }

        public long getAccountHistoryRevision() {
            return accountHistoryRevision;
        }

        public long getProductRuntimeRevision() {
            return productRuntimeRevision;
        }
    }
}
