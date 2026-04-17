/*
 * 旧交易页 Activity 桥接入口，只负责把历史入口收口到主壳交易 Tab。
 * 真正的交易页实现统一落在 MarketChartFragment / MarketChartScreen。
 */
package com.binance.monitor.ui.chart;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;

public class MarketChartActivity extends AppCompatActivity {

    public static final String EXTRA_TARGET_SYMBOL = "extra_target_symbol";
    public static final String EXTRA_TRADE_ACTION = "extra_trade_action";
    public static final String EXTRA_TRADE_POSITION_TICKET = "extra_trade_position_ticket";
    public static final String EXTRA_TRADE_ORDER_TICKET = "extra_trade_order_ticket";
    public static final String EXTRA_TRADE_ACTION_CLOSE_POSITION = "close_position";
    public static final String EXTRA_TRADE_ACTION_MODIFY_POSITION = "modify_position";
    public static final String EXTRA_TRADE_ACTION_MODIFY_PENDING = "modify_pending";
    public static final String EXTRA_TRADE_ACTION_CANCEL_PENDING = "cancel_pending";
    public static final String PREF_RUNTIME_NAME = "market_chart_runtime";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bridgeLegacyEntryToMainHost(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bridgeLegacyEntryToMainHost(intent);
    }

    // 旧入口统一透传原始参数后跳到主壳交易 Tab，避免继续维护第二套页面实现。
    private void bridgeLegacyEntryToMainHost(@Nullable Intent sourceIntent) {
        Intent bridgeIntent = HostNavigationIntentFactory.forTab(this, HostTab.MARKET_CHART);
        Bundle sourceExtras = sourceIntent == null ? null : sourceIntent.getExtras();
        if (sourceExtras != null) {
            bridgeIntent.putExtras(sourceExtras);
        }
        startActivity(bridgeIntent);
        overridePendingTransition(0, 0);
        finish();
    }
}
