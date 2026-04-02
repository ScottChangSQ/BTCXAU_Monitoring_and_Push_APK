/*
 * 账户曲线归一化工具，负责修正空值、补齐最少两点，并保留仓位比例。
 * 供 AccountStatsBridgeActivity 和曲线相关测试复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.account.model.CurvePoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AccountCurvePointNormalizer {

    private AccountCurvePointNormalizer() {
    }

    // 归一化账户曲线点，确保净值/结余有效且历史仓位比例不会丢失。
    static List<CurvePoint> normalize(@Nullable List<CurvePoint> source, double initialBalance) {
        return normalize(source, initialBalance, System.currentTimeMillis());
    }

    // 为测试提供可控时间戳版本，便于校验空态补点结果。
    static List<CurvePoint> normalize(@Nullable List<CurvePoint> source,
                                      double initialBalance,
                                      long nowTimestamp) {
        List<CurvePoint> normalized = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            normalized.add(new CurvePoint(
                    nowTimestamp - 60_000L,
                    initialBalance,
                    initialBalance,
                    0d
            ));
            normalized.add(new CurvePoint(
                    nowTimestamp,
                    initialBalance,
                    initialBalance,
                    0d
            ));
            return normalized;
        }

        List<CurvePoint> sorted = new ArrayList<>();
        for (CurvePoint point : source) {
            if (point != null) {
                sorted.add(point);
            }
        }
        if (sorted.isEmpty()) {
            return normalize(null, initialBalance, nowTimestamp);
        }
        sorted.sort(Comparator.comparingLong(CurvePoint::getTimestamp));

        double lastEquity = initialBalance;
        double lastBalance = initialBalance;
        boolean first = true;
        for (CurvePoint point : sorted) {
            long timestamp = point.getTimestamp() > 0L ? point.getTimestamp() : nowTimestamp;
            double equity = point.getEquity();
            double balance = point.getBalance();
            double positionRatio = point.getPositionRatio();

            if (first) {
                if (equity <= 0d && balance <= 0d) {
                    equity = initialBalance;
                    balance = initialBalance;
                } else {
                    if (equity <= 0d) {
                        equity = balance;
                    }
                    if (balance <= 0d) {
                        balance = equity;
                    }
                }
                first = false;
            } else {
                if (equity <= 0d) {
                    equity = lastEquity;
                }
                if (balance <= 0d) {
                    balance = lastBalance;
                }
            }

            if (equity <= 0d) {
                equity = initialBalance;
            }
            if (balance <= 0d) {
                balance = initialBalance;
            }
            lastEquity = equity;
            lastBalance = balance;
            normalized.add(new CurvePoint(timestamp, equity, balance, positionRatio));
        }

        if (normalized.size() == 1) {
            CurvePoint only = normalized.get(0);
            normalized.add(new CurvePoint(
                    only.getTimestamp() + 60_000L,
                    only.getEquity(),
                    only.getBalance(),
                    only.getPositionRatio()
            ));
        }
        return normalized;
    }
}
