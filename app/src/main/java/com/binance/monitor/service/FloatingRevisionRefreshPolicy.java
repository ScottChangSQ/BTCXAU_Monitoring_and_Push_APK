/*
 * 悬浮窗 revision 刷新策略，负责去重同一组产品 revision 的重复刷新。
 */
package com.binance.monitor.service;

import androidx.annotation.NonNull;

import java.util.List;

final class FloatingRevisionRefreshPolicy {
    private String lastAppliedSignature = "";

    // 只有当前可见产品 revision 签名变化后，非强制刷新才需要真正重绘悬浮窗。
    boolean shouldRefresh(@NonNull List<Long> productRevisions,
                          @NonNull List<String> marketSignatures) {
        return !buildSignature(productRevisions, marketSignatures).equals(lastAppliedSignature);
    }

    // 本轮刷新已经真正上屏后，记录当前 revision 签名。
    void markApplied(@NonNull List<Long> productRevisions,
                     @NonNull List<String> marketSignatures) {
        lastAppliedSignature = buildSignature(productRevisions, marketSignatures);
    }

    @NonNull
    private String buildSignature(@NonNull List<Long> productRevisions,
                                  @NonNull List<String> marketSignatures) {
        if (productRevisions.isEmpty()) {
            return "empty|market=" + buildMarketSignature(marketSignatures);
        }
        StringBuilder builder = new StringBuilder();
        for (Long revision : productRevisions) {
            builder.append(revision == null ? 0L : revision).append('|');
        }
        builder.append("market=").append(buildMarketSignature(marketSignatures));
        return builder.toString();
    }

    @NonNull
    private String buildMarketSignature(@NonNull List<String> marketSignatures) {
        if (marketSignatures.isEmpty()) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder();
        for (String signature : marketSignatures) {
            builder.append(signature == null ? "" : signature).append('|');
        }
        return builder.toString();
    }
}
