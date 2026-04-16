/*
 * 悬浮窗跳转桥接页，负责把后台悬浮窗点击稳定转成应用内页面路由。
 * 该页面本身不承载 UI，只做目标产品解析并把请求转发给主壳目标 Tab。
 */
package com.binance.monitor.ui.launch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;
import com.binance.monitor.ui.chart.MarketChartActivity;

import java.util.Locale;

public class OverlayLaunchBridgeActivity extends Activity {

    public static final String EXTRA_TARGET_DESTINATION = "extra_target_destination";
    public static final String EXTRA_TARGET_SYMBOL = "extra_target_symbol";
    public static final String TARGET_DESTINATION_CHART = "chart";
    public static final String TARGET_DESTINATION_HOME = "home";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        routeToTargetAndFinish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        routeToTargetAndFinish();
    }

    // 接到悬浮窗路由后立即转发到目标图表页，并结束自身避免停留在任务栈里。
    private void routeToTargetAndFinish() {
        String targetDestination = resolveTargetDestination(getIntent());
        String targetSymbol = resolveTargetSymbol(getIntent());
        if (TARGET_DESTINATION_HOME.equals(targetDestination)) {
            routeToHome();
        } else if (!targetSymbol.isEmpty()) {
            routeToChart(targetSymbol);
        } else {
            routeToHome();
        }
        finish();
        overridePendingTransition(0, 0);
    }

    // 跳到指定产品图表页，并复用现有顶层任务栈。
    private void routeToChart(String targetSymbol) {
        Intent intent = HostNavigationIntentFactory.forTab(this, HostTab.MARKET_CHART);
        intent.putExtra(MarketChartActivity.EXTRA_TARGET_SYMBOL, targetSymbol);
        startActivity(intent);
    }

    // 回到主页面，并保持与顶层 Tab 一致的返回栈语义。
    private void routeToHome() {
        startActivity(HostNavigationIntentFactory.forTab(this, HostTab.MARKET_MONITOR));
    }

    // 统一标准化产品代码，避免悬浮窗和图表页之间出现大小写不一致。
    private String resolveTargetSymbol(@Nullable Intent intent) {
        if (intent == null) {
            return "";
        }
        String raw = intent.getStringExtra(EXTRA_TARGET_SYMBOL);
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    // 统一解析悬浮窗目标页，未知值时默认走图表页语义。
    private String resolveTargetDestination(@Nullable Intent intent) {
        if (intent == null) {
            return TARGET_DESTINATION_CHART;
        }
        String raw = intent.getStringExtra(EXTRA_TARGET_DESTINATION);
        if (raw == null || raw.trim().isEmpty()) {
            return TARGET_DESTINATION_CHART;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
