/*
 * 账户概览刷新信息辅助类，统一处理刷新间隔和剩余秒数的展示计算。
 * 供 AccountStatsBridgeActivity 复用，避免界面显示与真实调度脱节。
 */
package com.binance.monitor.ui.account;

final class AccountRefreshMetaHelper {

    private AccountRefreshMetaHelper() {
    }

    // 统一约束最短刷新间隔，避免出现 0 秒或负数调度。
    static long normalizeDelayMs(long delayMs) {
        return Math.max(1_000L, delayMs);
    }

    // 优先显示已经真正排队的刷新间隔，没有排队时再回退到动态间隔。
    static long resolveIntervalSeconds(long scheduledDelayMs, long dynamicDelayMs) {
        long intervalMs = scheduledDelayMs > 0L ? scheduledDelayMs : dynamicDelayMs;
        long normalized = normalizeDelayMs(intervalMs);
        return Math.max(1L, (normalized + 999L) / 1_000L);
    }

    // 根据已排队的下一次刷新时间，计算顶部倒计时剩余秒数。
    static long resolveRemainingSeconds(long nextRefreshAtMs,
                                        long nowMs,
                                        long intervalSeconds) {
        return resolveRemainingSeconds(nextRefreshAtMs, nowMs, intervalSeconds, false);
    }

    // 请求已发出但下一次调度尚未重新排队时，顶部应保持临近刷新状态，不能跳回整轮周期。
    static long resolveRemainingSeconds(long nextRefreshAtMs,
                                        long nowMs,
                                        long intervalSeconds,
                                        boolean loading) {
        if (intervalSeconds <= 0L) {
            return 1L;
        }
        if (nextRefreshAtMs <= 0L) {
            return loading ? 1L : intervalSeconds;
        }
        long remainMs = Math.max(0L, nextRefreshAtMs - nowMs);
        if (remainMs <= 0L) {
            return 1L;
        }
        return Math.max(1L, (remainMs + 999L) / 1_000L);
    }
}
