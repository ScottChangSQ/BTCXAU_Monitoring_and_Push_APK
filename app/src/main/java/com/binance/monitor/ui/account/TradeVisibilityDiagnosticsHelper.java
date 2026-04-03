/*
 * 交易可见性诊断辅助类，用于把历史交易列表压缩成稳定的日志摘要。
 * 与 AccountStatsBridgeActivity 的交易入页、筛选显示排查链路配合使用。
 */
package com.binance.monitor.ui.account;

import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class TradeVisibilityDiagnosticsHelper {
    private static final double SMALL_LOT_THRESHOLD = 0.011d;
    private static final int SAMPLE_LIMIT = 3;

    private TradeVisibilityDiagnosticsHelper() {
    }

    // 生成交易列表的紧凑诊断摘要，便于运行日志快速判断是否有小手数记录被漏掉。
    static String buildTradeSignature(List<TradeRecordItem> trades) {
        if (trades == null || trades.isEmpty()) {
            return "count=0, smallLots=none, samples=none";
        }
        Map<String, Integer> smallLotCounts = new LinkedHashMap<>();
        List<String> samples = new ArrayList<>();
        for (TradeRecordItem item : trades) {
            if (item == null) {
                continue;
            }
            if (!isSmallLot(item)) {
                continue;
            }
            String code = safeCode(item);
            smallLotCounts.put(code, smallLotCounts.containsKey(code) ? smallLotCounts.get(code) + 1 : 1);
            if (samples.size() < SAMPLE_LIMIT) {
                samples.add(buildTradeSample(item));
            }
        }
        return "count=" + trades.size()
                + ", smallLots=" + buildSmallLotSummary(smallLotCounts)
                + ", samples=" + buildSampleSummary(samples);
    }

    // 生成筛选态的诊断摘要，帮助判断交易是在基表缺失还是被 UI 条件隐藏。
    static String buildFilterSignature(String product,
                                       String side,
                                       String sort,
                                       List<TradeRecordItem> trades) {
        return "product=" + safeText(product)
                + ", side=" + safeText(side)
                + ", sort=" + safeText(sort)
                + ", " + buildTradeSignature(trades);
    }

    // 判断是否为用户当前漏单问题里最敏感的小手数成交。
    private static boolean isSmallLot(TradeRecordItem item) {
        return item != null
                && Math.abs(item.getQuantity()) > 0d
                && Math.abs(item.getQuantity()) <= SMALL_LOT_THRESHOLD;
    }

    // 构建单条样本摘要，保留代码、方向、手数、时间和成交主键。
    private static String buildTradeSample(TradeRecordItem item) {
        return safeCode(item)
                + "/" + safeText(item.getSide())
                + "/" + formatQuantity(item.getQuantity())
                + "@" + resolveCloseTime(item)
                + "#" + item.getDealTicket();
    }

    // 输出小手数按产品分组的计数摘要。
    private static String buildSmallLotSummary(Map<String, Integer> smallLotCounts) {
        if (smallLotCounts == null || smallLotCounts.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : smallLotCounts.entrySet()) {
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return builder.toString();
    }

    // 输出样本列表，避免日志正文过长。
    private static String buildSampleSummary(List<String> samples) {
        if (samples == null || samples.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (String sample : samples) {
            if (sample == null || sample.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(sample);
        }
        return builder.length() == 0 ? "none" : builder.toString();
    }

    // 统一处理交易代码为空时的展示。
    private static String safeCode(TradeRecordItem item) {
        if (item == null) {
            return "--";
        }
        String code = safeText(item.getCode());
        if (!"--".equals(code)) {
            return code;
        }
        return safeText(item.getProductName());
    }

    // 统一输出筛选条件和方向文本。
    private static String safeText(String value) {
        return value == null || value.trim().isEmpty() ? "--" : value.trim();
    }

    // 压缩手数字符串，保证日志稳定。
    private static String formatQuantity(double quantity) {
        return String.format(Locale.US, "%.2f", Math.abs(quantity));
    }

    // 统一读取平仓时间，没有时回退到原始时间戳。
    private static long resolveCloseTime(TradeRecordItem item) {
        if (item == null) {
            return 0L;
        }
        return item.getCloseTime() > 0L ? item.getCloseTime() : item.getTimestamp();
    }
}
