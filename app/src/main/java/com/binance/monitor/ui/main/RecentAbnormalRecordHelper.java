/*
 * 主界面异常记录展示辅助，负责把原始异常记录整理成“最近 10 条摘要”。
 * 最近记录卡片只展示近 1 小时摘要；详细记录弹窗则直接查看完整原始列表。
 */
package com.binance.monitor.ui.main;

import androidx.annotation.NonNull;

import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.data.model.AbnormalRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RecentAbnormalRecordHelper {

    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;

    private RecentAbnormalRecordHelper() {
    }

    // 把最近一小时的原始异常记录合并成主界面摘要，并按需要限制条数。
    @NonNull
    static List<AbnormalRecord> buildRecentDisplay(List<AbnormalRecord> source, long nowMs, int limit) {
        List<AbnormalRecord> output = new ArrayList<>();
        if (source == null || source.isEmpty() || limit <= 0) {
            return output;
        }
        long cutoff = nowMs - ONE_HOUR_MS;
        Map<Long, Map<String, AbnormalRecord>> grouped = new LinkedHashMap<>();
        for (AbnormalRecord item : source) {
            if (item == null || item.getCloseTime() < cutoff) {
                continue;
            }
            Map<String, AbnormalRecord> bySymbol = grouped.get(item.getCloseTime());
            if (bySymbol == null) {
                bySymbol = new LinkedHashMap<>();
                grouped.put(item.getCloseTime(), bySymbol);
            }
            bySymbol.put(item.getSymbol(), item);
        }
        for (Map<String, AbnormalRecord> bySymbol : grouped.values()) {
            AbnormalRecord btc = bySymbol.get(AppConstants.SYMBOL_BTC);
            AbnormalRecord xau = bySymbol.get(AppConstants.SYMBOL_XAU);
            if (btc != null && xau != null) {
                output.add(new AbnormalRecord(
                        UUID.randomUUID().toString(),
                        "BOTH",
                        Math.max(btc.getCloseTime(), xau.getCloseTime()),
                        btc.getCloseTime(),
                        btc.getOpenPrice(),
                        btc.getClosePrice(),
                        btc.getVolume(),
                        btc.getAmount(),
                        btc.getPriceChange(),
                        btc.getPercentChange(),
                        "BTC: " + btc.getTriggerSummary() + " / XAU: " + xau.getTriggerSummary()
                ));
            } else if (btc != null) {
                output.add(copyWithDisplaySymbol(btc, "BTC", "BTC: " + btc.getTriggerSummary()));
            } else if (xau != null) {
                output.add(copyWithDisplaySymbol(xau, "XAU", "XAU: " + xau.getTriggerSummary()));
            }
            if (output.size() >= limit) {
                break;
            }
        }
        return output;
    }

    private static AbnormalRecord copyWithDisplaySymbol(AbnormalRecord source, String displaySymbol, String summary) {
        return new AbnormalRecord(
                source.getId(),
                displaySymbol,
                source.getCloseTime(),
                source.getCloseTime(),
                source.getOpenPrice(),
                source.getClosePrice(),
                source.getVolume(),
                source.getAmount(),
                source.getPriceChange(),
                source.getPercentChange(),
                summary
        );
    }
}
