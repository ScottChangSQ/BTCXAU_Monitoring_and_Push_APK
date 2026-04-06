/*
 * 券商约束模型，保存交易手数和止损止盈距离等基础限制。
 * 供交易命令构建和前置校验复用。
 */
package com.binance.monitor.data.model.v2.trade;

public class BrokerConstraint {
    private final double minVolume;
    private final double maxVolume;
    private final double volumeStep;
    private final double minStopDistance;

    // 构造券商约束对象。
    public BrokerConstraint(double minVolume, double maxVolume, double volumeStep, double minStopDistance) {
        this.minVolume = minVolume;
        this.maxVolume = maxVolume;
        this.volumeStep = volumeStep;
        this.minStopDistance = minStopDistance;
    }

    // 返回最小手数。
    public double getMinVolume() {
        return minVolume;
    }

    // 返回最大手数。
    public double getMaxVolume() {
        return maxVolume;
    }

    // 返回手数步进。
    public double getVolumeStep() {
        return volumeStep;
    }

    // 返回最小止损止盈距离。
    public double getMinStopDistance() {
        return minStopDistance;
    }
}
