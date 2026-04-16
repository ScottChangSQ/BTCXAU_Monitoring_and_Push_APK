/*
 * 主壳页可见性契约，后续主壳切页时统一通知页面前后台状态。
 */
package com.binance.monitor.ui.host;

public interface HostTabPage {
    // 页面切到前台时触发。
    void onHostPageShown();

    // 页面离开前台时触发。
    void onHostPageHidden();
}
