/*
 * 行情监控主壳页，承接行情监控共享页面控制器。
 */
package com.binance.monitor.ui.market;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ActivityMainBinding;
import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;
import com.binance.monitor.ui.host.HostTabPage;

public class MarketMonitorFragment extends Fragment implements HostTabPage {

    private MarketMonitorPageController pageController;
    private MarketMonitorPageRuntime pageRuntime;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_market_monitor, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View monitorContentView = ((ViewGroup) view).getChildAt(0);
        ActivityMainBinding monitorBinding = ActivityMainBinding.bind(monitorContentView);
        pageRuntime = new MarketMonitorPageRuntime(new MarketMonitorPageRuntime.Host() {
            @NonNull
            @Override
            public AppCompatActivity requireActivity() {
                return (AppCompatActivity) MarketMonitorFragment.this.requireActivity();
            }

            @NonNull
            @Override
            public androidx.lifecycle.LifecycleOwner getLifecycleOwner() {
                return getViewLifecycleOwner();
            }

            @NonNull
            @Override
            public androidx.lifecycle.ViewModelStoreOwner getViewModelStoreOwner() {
                return MarketMonitorFragment.this;
            }

            @Override
            public boolean isEmbeddedInHostShell() {
                return true;
            }

            @Override
            public void openMarketChart() {
                startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.MARKET_CHART));
            }

            @Override
            public void openAccountStats() {
                startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.ACCOUNT_STATS));
            }

            @Override
            public void openAccountPosition() {
                startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.ACCOUNT_POSITION));
            }

            @Override
            public void openSettings() {
                startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.SETTINGS));
            }
        }, monitorBinding, savedInstanceState);
        pageController = new MarketMonitorPageController(
                new MarketMonitorPageHostDelegate(pageRuntime),
                monitorBinding,
                new MarketMonitorPageController.BottomNavBinding(
                        monitorBinding.tabBar,
                        monitorBinding.tabMarketMonitor,
                        monitorBinding.tabMarketChart,
                        monitorBinding.tabAccountPosition,
                        monitorBinding.tabAccountStats,
                        monitorBinding.tabSettings
                )
        );
        pageController.bind();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pageRuntime != null) {
            pageRuntime.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onHostPageShown() {
        if (pageController != null) {
            pageController.onPageShown();
        }
    }

    @Override
    public void onHostPageHidden() {
        if (pageController != null) {
            pageController.onPageHidden();
        }
    }

    @Override
    public void onDestroyView() {
        if (pageController != null) {
            pageController.onDestroy();
            pageController = null;
        }
        pageRuntime = null;
        super.onDestroyView();
    }
}
