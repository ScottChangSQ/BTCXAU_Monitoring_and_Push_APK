/*
 * 悬浮窗跳转桥接页，负责把后台悬浮窗点击稳定转成应用内页面路由。
 * 该页面本身不承载 UI，只做目标产品解析并把请求转发给 MarketChartActivity。
 */
package com.binance.monitor.ui.launch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.binance.monitor.ui.chart.MarketChartActivity;

import java.util.Locale;

public class OverlayLaunchBridgeActivity extends Activity {

    public static final String EXTRA_TARGET_SYMBOL = "extra_target_symbol";

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
        String targetSymbol = resolveTargetSymbol(getIntent());
        if (!targetSymbol.isEmpty()) {
            Intent intent = new Intent(this, MarketChartActivity.class);
            intent.putExtra(MarketChartActivity.EXTRA_TARGET_SYMBOL, targetSymbol);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
        finish();
        overridePendingTransition(0, 0);
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
}
