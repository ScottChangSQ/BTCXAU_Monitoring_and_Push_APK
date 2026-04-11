/*
 * 图表页启动门控，负责把首帧前到达的实时尾部和账户叠加层延后到主图首次落稳后再释放。
 * 与 MarketChartActivity 配合，避免首帧阶段把多条非主序列更新同时压到主线程。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

final class MarketChartStartupGate {

    @NonNull
    private String activeDataKey = "";
    private boolean primaryDisplayCommitted;
    private boolean primaryDisplayDrawn;
    @Nullable
    private Runnable pendingRealtimeTask;
    @Nullable
    private Runnable pendingOverlayTask;

    // 切换到新的 symbol+interval 后，旧 key 的挂起任务必须全部失效。
    void resetForDataKey(@Nullable String dataKey) {
        activeDataKey = normalizeKey(dataKey);
        primaryDisplayCommitted = false;
        primaryDisplayDrawn = false;
        pendingRealtimeTask = null;
        pendingOverlayTask = null;
    }

    // 只有当前 key 的主序列尚未真正完成首帧绘制时，实时尾部和账户叠加层才需要先挂起。
    boolean shouldDeferUntilPrimaryDisplay(@Nullable String dataKey) {
        return matchesActiveKey(dataKey) && !(primaryDisplayCommitted && primaryDisplayDrawn);
    }

    // 实时尾部只保留最新一份，因为它语义上覆盖同一时间桶。
    void replacePendingRealtime(@Nullable String dataKey, @Nullable Runnable task) {
        if (!matchesActiveKey(dataKey) || primaryDisplayCommitted) {
            return;
        }
        pendingRealtimeTask = task;
    }

    // 账户叠加层也只保留最新一份，避免首帧前重复重建。
    void replacePendingOverlay(@Nullable String dataKey, @Nullable Runnable task) {
        if (!matchesActiveKey(dataKey) || primaryDisplayCommitted) {
            return;
        }
        pendingOverlayTask = task;
    }

    // 主序列首次落稳后，按“实时尾部 -> 账户叠加层”的顺序一次性释放挂起任务。
    @NonNull
    List<Runnable> onPrimaryDisplayCommitted(@Nullable String dataKey) {
        if (!matchesActiveKey(dataKey)) {
            return new ArrayList<>();
        }
        primaryDisplayCommitted = true;
        return drainPendingIfReady();
    }

    // 图表主视图真正完成当前 key 的首次绘制后，才允许释放启动阶段挂起任务。
    @NonNull
    List<Runnable> onPrimaryDisplayDrawn(@Nullable String dataKey) {
        if (!matchesActiveKey(dataKey)) {
            return new ArrayList<>();
        }
        primaryDisplayDrawn = true;
        return drainPendingIfReady();
    }

    @NonNull
    private List<Runnable> drainPendingIfReady() {
        List<Runnable> pending = new ArrayList<>();
        if (!primaryDisplayCommitted || !primaryDisplayDrawn) {
            return pending;
        }
        if (pendingRealtimeTask != null) {
            pending.add(pendingRealtimeTask);
        }
        if (pendingOverlayTask != null) {
            pending.add(pendingOverlayTask);
        }
        pendingRealtimeTask = null;
        pendingOverlayTask = null;
        return pending;
    }

    private boolean matchesActiveKey(@Nullable String dataKey) {
        return activeDataKey.equals(normalizeKey(dataKey));
    }

    @NonNull
    private String normalizeKey(@Nullable String dataKey) {
        return dataKey == null ? "" : dataKey.trim();
    }
}
