/*
 * 主界面异常记录展示辅助，负责把原始异常记录整理成“最近 10 条摘要”。
 * 当前主卡片不再额外做时间过滤或同时刻合并，直接展示最新的原始 10 条。
 */
package com.binance.monitor.ui.main;

import androidx.annotation.NonNull;

import com.binance.monitor.data.model.AbnormalRecord;

import java.util.ArrayList;
import java.util.List;

public final class RecentAbnormalRecordHelper {

    private RecentAbnormalRecordHelper() {
    }

    // 直接截取最新原始记录，避免时间过滤把真正最近的异常挡掉。
    @NonNull
    public static List<AbnormalRecord> buildRecentDisplay(List<AbnormalRecord> source, long nowMs, int limit) {
        List<AbnormalRecord> output = new ArrayList<>();
        if (source == null || source.isEmpty() || limit <= 0) {
            return output;
        }
        for (AbnormalRecord item : source) {
            if (item == null) {
                continue;
            }
            output.add(item);
            if (output.size() >= limit) {
                break;
            }
        }
        return output;
    }
}
