/*
 * 主壳 Tab 导航器，负责首次创建、show/hide 和目标页切换。
 */
package com.binance.monitor.ui.host;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.binance.monitor.ui.account.AccountPositionFragment;
import com.binance.monitor.ui.account.AccountStatsFragment;
import com.binance.monitor.ui.chart.MarketChartFragment;
import com.binance.monitor.ui.market.MarketMonitorFragment;
import com.binance.monitor.ui.settings.SettingsFragment;

public final class HostTabNavigator {

    public void show(@NonNull FragmentManager fragmentManager,
                     @IdRes int containerId,
                     @NonNull HostTab targetTab) {
        FragmentTransaction transaction = fragmentManager.beginTransaction().setReorderingAllowed(true);
        Fragment targetFragment = fragmentManager.findFragmentByTag(targetTab.getKey());
        if (targetFragment == null) {
            Fragment fragment = createFragment(targetTab);
            targetFragment = fragment;
            transaction.add(containerId, fragment, targetTab.getKey());
        }
        for (HostTab tab : HostTab.values()) {
            Fragment fragment = tab == targetTab ? targetFragment : fragmentManager.findFragmentByTag(tab.getKey());
            if (fragment == null) {
                continue;
            }
            if (tab == targetTab) {
                transaction.show(fragment);
                notifyPageShown(fragment);
            } else {
                transaction.hide(fragment);
                notifyPageHidden(fragment);
            }
        }
        transaction.commitNowAllowingStateLoss();
    }

    @NonNull
    public static Fragment fragmentClassOf(@NonNull HostTab tab) {
        return createFragment(tab);
    }

    @NonNull
    private static Fragment createFragment(@NonNull HostTab tab) {
        switch (tab) {
            case MARKET_MONITOR:
                return new MarketMonitorFragment();
            case MARKET_CHART:
                return new MarketChartFragment();
            case ACCOUNT_STATS:
                return new AccountStatsFragment();
            case ACCOUNT_POSITION:
                return new AccountPositionFragment();
            case SETTINGS:
            default:
                return new SettingsFragment();
        }
    }

    private void notifyPageShown(@NonNull Fragment fragment) {
        if (fragment instanceof HostTabPage) {
            ((HostTabPage) fragment).onHostPageShown();
        }
    }

    private void notifyPageHidden(@NonNull Fragment fragment) {
        if (fragment instanceof HostTabPage) {
            ((HostTabPage) fragment).onHostPageHidden();
        }
    }
}
