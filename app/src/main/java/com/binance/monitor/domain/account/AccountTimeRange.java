/*
 * 账户历史时间范围枚举，供网关、运行时缓存和页面共同使用。
 */
package com.binance.monitor.domain.account;

public enum AccountTimeRange {
    D1,
    D7,
    M1,
    M3,
    Y1,
    ALL
}
