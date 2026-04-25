/*
 * 监控服务提醒协调器边界，先承接服务端提醒 seam，后续再迁移异常提醒编排细节。
 */
package com.binance.monitor.service;

import androidx.annotation.Nullable;

final class MonitorAlertCoordinator {

    interface Host {
        void dispatchParsedServerAlert(@Nullable Object alert);
    }

    private final Host host;

    MonitorAlertCoordinator(Host host) {
        this.host = host;
    }
}
