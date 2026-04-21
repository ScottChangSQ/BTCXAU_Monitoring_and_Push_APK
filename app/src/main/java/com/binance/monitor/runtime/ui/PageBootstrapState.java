/*
 * 页面首帧启动状态定义，供图表页、账户页和分析页共享。
 */
package com.binance.monitor.runtime.ui;

public enum PageBootstrapState {
    MEMORY_READY,
    STORAGE_RESTORING,
    LOCAL_READY_REMOTE_SYNCING,
    REMOTE_READY,
    TRUE_EMPTY
}
