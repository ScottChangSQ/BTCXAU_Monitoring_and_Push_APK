/*
 * 账户曲线归一化工具，只负责做严格清洗：过滤无效点、排序、按时间戳去重。
 * 不再本地补点、补净值或补结余，避免展示链继续造数。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AccountCurvePointNormalizer {

    private AccountCurvePointNormalizer() {
    }

    // 归一化账户曲线点，只保留服务端给出的有效采样点。
    static List<CurvePoint> normalize(@Nullable List<CurvePoint> source, double initialBalance) {
        return normalize(source, initialBalance, System.currentTimeMillis());
    }

    // 为测试提供可控时间戳版本；当前不会再用它补默认点，仅保留签名兼容。
    static List<CurvePoint> normalize(@Nullable List<CurvePoint> source,
                                      double initialBalance,
                                      long nowTimestamp) {
        List<CurvePoint> normalized = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return normalized;
        }

        List<CurvePoint> sorted = new ArrayList<>();
        for (CurvePoint point : source) {
            if (point != null
                    && point.getTimestamp() > 0L
                    && point.getEquity() > 0d
                    && point.getBalance() > 0d) {
                sorted.add(point);
            }
        }
        if (sorted.isEmpty()) {
            return normalized;
        }
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));

        Map<Long, CurvePoint> deduplicated = new LinkedHashMap<>();
        for (CurvePoint point : sorted) {
            // 同一时间戳只保留最后一条，避免图表在同一个横坐标上画出竖直毛刺。
            deduplicated.put(point.getTimestamp(), point);
        }
        normalized.addAll(deduplicated.values());
        return normalized;
    }
}
