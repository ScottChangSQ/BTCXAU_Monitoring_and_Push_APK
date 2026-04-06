/*
 * 交易意图模型，封装一次点击行为对应的命令与约束快照。
 * 用于把“用户意图”与“执行回执”分离。
 */
package com.binance.monitor.data.model.v2.trade;

public class TradeIntent {
    private final TradeCommand command;
    private final BrokerConstraint constraint;
    private final long createdAt;

    // 构造交易意图对象。
    public TradeIntent(TradeCommand command, BrokerConstraint constraint, long createdAt) {
        this.command = command;
        this.constraint = constraint;
        this.createdAt = createdAt;
    }

    // 返回交易命令。
    public TradeCommand getCommand() {
        return command;
    }

    // 返回券商约束快照。
    public BrokerConstraint getConstraint() {
        return constraint;
    }

    // 返回意图生成时间。
    public long getCreatedAt() {
        return createdAt;
    }
}
