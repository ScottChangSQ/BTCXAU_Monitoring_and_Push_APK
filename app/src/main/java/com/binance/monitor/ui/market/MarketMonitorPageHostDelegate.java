/*
 * 行情监控页宿主委托，统一适配 Activity/Fragment 的页面宿主能力。
 */
package com.binance.monitor.ui.market;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ActivityMainBinding;

public final class MarketMonitorPageHostDelegate implements MarketMonitorPageController.Host {

    private final Owner owner;

    public MarketMonitorPageHostDelegate(@NonNull Owner owner) {
        this.owner = owner;
    }

    @NonNull
    @Override
    public AppCompatActivity requireActivity() {
        return owner.requireActivity();
    }

    @Override
    public void bindPageContent(@NonNull ActivityMainBinding binding) {
        owner.bindPageContent(binding);
    }

    @Override
    public void onPageShown() {
        owner.onPageShown();
    }

    @Override
    public void onPageHidden() {
        owner.onPageHidden();
    }

    @Override
    public void onPageDestroyed() {
        owner.onPageDestroyed();
    }

    @Override
    public boolean isEmbeddedInHostShell() {
        return owner.isEmbeddedInHostShell();
    }

    @Override
    public void openMarketChart() {
        owner.openMarketChart();
    }

    @Override
    public void openAccountStats() {
        owner.openAccountStats();
    }

    @Override
    public void openAccountPosition() {
        owner.openAccountPosition();
    }

    @Override
    public void openSettings() {
        owner.openSettings();
    }

    public interface Owner {
        @NonNull
        AppCompatActivity requireActivity();

        void bindPageContent(@NonNull ActivityMainBinding binding);

        void onPageShown();

        void onPageHidden();

        void onPageDestroyed();

        boolean isEmbeddedInHostShell();

        void openMarketChart();

        void openAccountStats();

        void openAccountPosition();

        void openSettings();
    }
}
