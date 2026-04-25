/*
 * 交易回放格式化器，负责把本地与网关审计整理成用户可读的结果清单和时间线。
 * 与 TradeAuditViewModel、TradeAuditActivity 协同工作，不直接参与交易执行。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.TradeAuditEntry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TradeReplayFormatter {

    private TradeReplayFormatter() {
    }

    @NonNull
    public static ReplayContent buildReplay(@Nullable String traceId,
                                            @Nullable List<TradeAuditEntry> localEntries,
                                            @Nullable List<TradeAuditEntry> gatewayEntries) {
        List<TradeAuditEntry> merged = mergeEntries(localEntries, gatewayEntries);
        TradeAuditEntry latest = merged.isEmpty() ? null : merged.get(merged.size() - 1);
        String safeTraceId = safe(traceId);
        if (safeTraceId.isEmpty() && latest != null) {
            safeTraceId = latest.getTraceId();
        }
        String summary = buildSummaryText(safeTraceId, latest, localEntries, gatewayEntries);
        String timeline = buildTimelineText(merged);
        String copy = summary + "\n\n" + timeline;
        String title = latest == null
                ? "交易追踪"
                : resolveTraceTypeLabel(latest) + "追踪";
        return new ReplayContent(title, summary, timeline, copy);
    }

    @NonNull
    private static List<TradeAuditEntry> mergeEntries(@Nullable List<TradeAuditEntry> localEntries,
                                                      @Nullable List<TradeAuditEntry> gatewayEntries) {
        Map<String, TradeAuditEntry> merged = new LinkedHashMap<>();
        appendEntries(merged, localEntries);
        appendEntries(merged, gatewayEntries);
        List<TradeAuditEntry> ordered = new ArrayList<>(merged.values());
        ordered.sort((left, right) -> Long.compare(resolveSortTime(left), resolveSortTime(right)));
        return ordered;
    }

    private static void appendEntries(@NonNull Map<String, TradeAuditEntry> merged,
                                      @Nullable List<TradeAuditEntry> entries) {
        if (entries == null) {
            return;
        }
        for (TradeAuditEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String key = safe(entry.getTraceId())
                    + "|"
                    + safe(entry.getStage())
                    + "|"
                    + safe(entry.getStatus())
                    + "|"
                    + resolveSortTime(entry)
                    + "|"
                    + safe(entry.getMessage());
            merged.put(key, entry);
        }
    }

    private static long resolveSortTime(@Nullable TradeAuditEntry entry) {
        if (entry == null) {
            return 0L;
        }
        if (entry.getServerTime() > 0L) {
            return entry.getServerTime();
        }
        return entry.getCreatedAt();
    }

    @NonNull
    private static String buildSummaryText(@NonNull String traceId,
                                           @Nullable TradeAuditEntry latest,
                                           @Nullable List<TradeAuditEntry> localEntries,
                                           @Nullable List<TradeAuditEntry> gatewayEntries) {
        StringBuilder builder = new StringBuilder();
        builder.append("追踪ID：").append(traceId.isEmpty() ? "--" : traceId);
        builder.append("\n当前结果：").append(resolveStatusLabel(latest));
        if (latest != null && !safe(latest.getActionSummary()).isEmpty()) {
            builder.append("\n动作摘要：").append(latest.getActionSummary());
        }
        builder.append("\n本地记录：").append(localEntries == null ? 0 : localEntries.size()).append(" 条");
        builder.append("\n网关记录：").append(gatewayEntries == null ? 0 : gatewayEntries.size()).append(" 条");
        if (latest != null && !safe(latest.getMessage()).isEmpty()) {
            builder.append("\n最新说明：").append(latest.getMessage());
        }
        return builder.toString();
    }

    @NonNull
    private static String buildTimelineText(@NonNull List<TradeAuditEntry> entries) {
        if (entries.isEmpty()) {
            return "时间线：\n暂无可回放的交易记录";
        }
        StringBuilder builder = new StringBuilder("时间线：");
        for (TradeAuditEntry entry : entries) {
            builder.append("\n");
            builder.append("- ");
            builder.append(resolveStageLabel(entry)).append(" | ");
            builder.append(entry.getStage()).append(" | ");
            builder.append(entry.getStatus());
            if (!safe(entry.getErrorCode()).isEmpty()) {
                builder.append(" | ").append(entry.getErrorCode());
            }
            if (!safe(entry.getMessage()).isEmpty()) {
                builder.append(" | ").append(entry.getMessage());
            }
        }
        return builder.toString();
    }

    @NonNull
    private static String resolveStageLabel(@Nullable TradeAuditEntry entry) {
        String stage = entry == null ? "" : safe(entry.getStage()).toLowerCase(Locale.ROOT);
        if ("check".equals(stage)) {
            return "服务器检查";
        }
        if ("submit".equals(stage)) {
            return "正式提交";
        }
        if ("result".equals(stage)) {
            return "结果回查";
        }
        if ("batch_submit".equals(stage)) {
            return "批量提交";
        }
        if ("batch_result".equals(stage)) {
            return "批量结果";
        }
        return "交易阶段";
    }

    @NonNull
    private static String resolveStatusLabel(@Nullable TradeAuditEntry latest) {
        String status = latest == null ? "" : safe(latest.getStatus()).toUpperCase(Locale.ROOT);
        if ("SETTLED".equals(status)) {
            return "已受理并收敛";
        }
        if ("PARTIAL".equals(status)) {
            return "批量部分成功";
        }
        if ("FAILED".equals(status) || "REJECTED".equals(status) || "NOT_EXECUTABLE".equals(status)) {
            return "交易被拒绝";
        }
        if ("ACCEPTED_AWAITING_SYNC".equals(status)
                || "TIMEOUT".equals(status)
                || "RESULT_UNCONFIRMED".equals(status)
                || "UNKNOWN".equals(status)) {
            return "结果仍未确认";
        }
        if ("ACCEPTED".equals(status) || "DUPLICATE".equals(status) || "EXECUTABLE".equals(status)) {
            return "交易已受理";
        }
        return "暂无结果";
    }

    @NonNull
    private static String resolveTraceTypeLabel(@Nullable TradeAuditEntry latest) {
        if (latest == null) {
            return "交易";
        }
        if ("batch".equalsIgnoreCase(latest.getTraceType())) {
            return "批量交易";
        }
        return "单笔交易";
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    public static final class ReplayContent {
        private final String displayTitle;
        private final String summaryText;
        private final String timelineText;
        private final String copyText;

        public ReplayContent(@NonNull String displayTitle,
                             @NonNull String summaryText,
                             @NonNull String timelineText,
                             @NonNull String copyText) {
            this.displayTitle = displayTitle;
            this.summaryText = summaryText;
            this.timelineText = timelineText;
            this.copyText = copyText;
        }

        @NonNull
        public String getDisplayTitle() {
            return displayTitle;
        }

        @NonNull
        public String getSummaryText() {
            return summaryText;
        }

        @NonNull
        public String getTimelineText() {
            return timelineText;
        }

        @NonNull
        public String getCopyText() {
            return copyText;
        }
    }
}
