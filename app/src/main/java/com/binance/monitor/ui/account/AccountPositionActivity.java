/*
 * 账户持仓旧 Activity 桥接页，统一把旧入口收口到主壳账户持仓 Tab。
 */
package com.binance.monitor.ui.account;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;

public class AccountPositionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(HostNavigationIntentFactory.forTab(this, HostTab.ACCOUNT_POSITION));
        overridePendingTransition(0, 0);
        finish();
    }
}
