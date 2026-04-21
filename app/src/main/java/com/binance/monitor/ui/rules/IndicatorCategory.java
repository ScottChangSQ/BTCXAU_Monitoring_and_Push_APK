/*
 * 全局指标分类，用于限制不同页面可消费的指标语义范围。
 */
package com.binance.monitor.ui.rules;

public enum IndicatorCategory {
    ASSET,
    RETURN,
    TRADE,
    POSITION,
    RISK,
    MONITOR
}
