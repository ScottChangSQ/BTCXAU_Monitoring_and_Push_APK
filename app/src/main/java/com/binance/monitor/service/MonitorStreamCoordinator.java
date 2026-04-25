/*
 * 监控服务行情流协调器边界，先承接流处理 seam，后续再逐步迁入具体消息编排。
 */
package com.binance.monitor.service;

import androidx.annotation.Nullable;

final class MonitorStreamCoordinator {

    interface Host {
        void updateConnectionStatus();

        void applyRealtimeMessage(@Nullable Object message);
    }

    private final Host host;

    MonitorStreamCoordinator(Host host) {
        this.host = host;
    }
}
