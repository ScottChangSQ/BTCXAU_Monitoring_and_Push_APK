/*
 * 旧行情监控入口桥接页，只负责把历史入口收口到主壳行情监控 Tab。
 */
package com.binance.monitor.ui.main;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(HostNavigationIntentFactory.forTab(this, HostTab.MARKET_MONITOR));
        overridePendingTransition(0, 0);
        finish();
    }
}
