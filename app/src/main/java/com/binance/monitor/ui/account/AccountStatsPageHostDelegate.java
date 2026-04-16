/*
 * 账户统计页宿主委托，统一适配 Activity/Fragment 的页面宿主能力。
 * 当前先收口 PageController.Host 实现，后续 Fragment 真正承接业务时继续补齐 Owner 能力。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.binance.monitor.databinding.ContentAccountStatsBinding;

public final class AccountStatsPageHostDelegate implements AccountStatsPageController.Host {

    private final Owner owner;

    public AccountStatsPageHostDelegate(@NonNull Owner owner) {
        this.owner = owner;
    }

    @NonNull
    @Override
    public AppCompatActivity requireActivity() {
        return owner.requireActivity();
    }

    @Override
    public void bindPageContent(@NonNull ContentAccountStatsBinding binding) {
        owner.bindPageContent(binding);
    }

    @Override
    public void placeCurveSectionToBottom() {
        owner.placeCurveSectionToBottom();
    }

    @Override
    public void bindLocalMeta() {
        owner.bindLocalMeta();
    }

    @Override
    public void onColdStart() {
        owner.onColdStart();
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
    public void attachForegroundRefresh() {
        owner.attachForegroundRefresh();
    }

    @Override
    public void applyPagePalette() {
        owner.applyPagePalette();
    }

    @Override
    public void applyPrivacyMaskState() {
        owner.applyPrivacyMaskState();
    }

    @Override
    public void enterAccountScreen(boolean coldStart) {
        owner.enterAccountScreen(coldStart);
    }

    @Override
    public void openLoginDialogIfRequested() {
        owner.openLoginDialogIfRequested();
    }

    @Override
    public void dismissActiveLoginDialog() {
        owner.dismissActiveLoginDialog();
    }

    @Override
    public void clearTransientUiCallbacks() {
        owner.clearTransientUiCallbacks();
    }

    @Override
    public void detachForegroundRefresh() {
        owner.detachForegroundRefresh();
    }

    @Override
    public void persistUiState() {
        owner.persistUiState();
    }

    @Override
    public void clearDestroyCallbacks() {
        owner.clearDestroyCallbacks();
    }

    @Override
    public void shutdownExecutors() {
        owner.shutdownExecutors();
    }

    @Override
    public void requestScheduledSnapshot() {
        owner.requestScheduledSnapshot();
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
    public void openMarketMonitor() {
        owner.openMarketMonitor();
    }

    @Override
    public void openMarketChart() {
        owner.openMarketChart();
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

        void bindPageContent(@NonNull ContentAccountStatsBinding binding);

        void placeCurveSectionToBottom();

        void bindLocalMeta();

        void onColdStart();

        void onPageShown();

        void onPageHidden();

        void attachForegroundRefresh();

        void applyPagePalette();

        void applyPrivacyMaskState();

        void enterAccountScreen(boolean coldStart);

        void openLoginDialogIfRequested();

        void dismissActiveLoginDialog();

        void clearTransientUiCallbacks();

        void detachForegroundRefresh();

        void persistUiState();

        void clearDestroyCallbacks();

        void shutdownExecutors();

        void requestScheduledSnapshot();

        void onPageDestroyed();

        boolean isEmbeddedInHostShell();

        void openMarketMonitor();

        void openMarketChart();

        void openAccountPosition();

        void openSettings();
    }
}
