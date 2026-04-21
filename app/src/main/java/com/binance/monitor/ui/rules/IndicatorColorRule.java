/*
 * 指标颜色规则，统一约束红绿和中性表现，不再让页面自行猜测。
 */
package com.binance.monitor.ui.rules;

public enum IndicatorColorRule {
    NEUTRAL,
    PROFIT_UP_LOSS_DOWN,
    LOSS_UP_PROFIT_DOWN
}
