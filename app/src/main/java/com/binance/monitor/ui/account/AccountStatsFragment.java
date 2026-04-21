/*
 * 账户统计主壳页骨架，承接账户统计共享页面控制器。
 */
package com.binance.monitor.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.binance.monitor.R;
import com.binance.monitor.databinding.ContentAccountStatsBinding;
import com.binance.monitor.runtime.state.model.AccountRuntimeSnapshot;
import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;
import com.binance.monitor.ui.host.HostTabPage;
import com.binance.monitor.ui.settings.SettingsActivity;

public class AccountStatsFragment extends Fragment implements HostTabPage {
    private AccountStatsPageController pageController;
    private AccountStatsPageRuntime pageRuntime;
    private AccountStatsScreen screen;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        screen = new AccountStatsScreen(
                (AppCompatActivity) requireActivity(),
                ContentAccountStatsBinding.bind(view)
        );
        screen.initialize();
        screen.onNewIntent(requireActivity().getIntent());
        pageRuntime = new AccountStatsPageRuntime(new AccountStatsPageRuntime.Host() {
                    @NonNull
                    @Override
                    public AppCompatActivity requireActivity() {
                        return (AppCompatActivity) AccountStatsFragment.this.requireActivity();
                    }

                    @Override
                    public void bindPageContent(@NonNull ContentAccountStatsBinding binding) {
                        if (screen != null) {
                            screen.bindPageContent();
                        }
                    }

                    @Override
                    public void bindLocalMeta() {
                        if (screen != null) {
                            screen.bindLocalMeta();
                        }
                    }

                    @Override
                    public void beginAccountBootstrap() {
                        if (screen != null) {
                            screen.beginAccountBootstrap();
                        }
                    }

                    @Override
                    public void attachForegroundRefresh() {
                        if (screen != null) {
                            screen.attachForegroundRefresh();
                        }
                    }

                    @Override
                    public void applyPagePalette() {
                        if (screen != null) {
                            screen.applyPagePalette();
                        }
                    }

                    @Override
                    public void applyPrivacyMaskState() {
                        if (screen != null) {
                            screen.applyPrivacyMaskState();
                        }
                    }

                    @Override
                    public void enterAccountScreen(boolean coldStart) {
                        if (screen != null) {
                            screen.enterAccountScreen(coldStart);
                        }
                    }

                    @Override
                    public void openLoginDialogIfRequested() {
                        if (screen != null) {
                            screen.openLoginDialogIfRequested();
                        }
                    }

                    @Override
                    public void dismissActiveLoginDialog() {
                        if (screen != null) {
                            screen.dismissActiveLoginDialog();
                        }
                    }

                    @Override
                    public void clearTransientUiCallbacks() {
                        if (screen != null) {
                            screen.clearTransientUiCallbacks();
                        }
                    }

                    @Override
                    public void detachForegroundRefresh() {
                        if (screen != null) {
                            screen.detachForegroundRefresh();
                        }
                    }

                    @Override
                    public void persistUiState() {
                        if (screen != null) {
                            screen.persistUiState();
                        }
                    }

                    @Override
                    public void clearDestroyCallbacks() {
                        if (screen != null) {
                            screen.clearDestroyCallbacks();
                        }
                    }

                    @Override
                    public void shutdownExecutors() {
                        if (screen != null) {
                            screen.shutdownExecutors();
                        }
                    }

                    @Override
                    public void requestScheduledSnapshot() {
                        if (screen != null) {
                            screen.requestScheduledSnapshot();
                        }
                    }

                    @Nullable
                    @Override
                    public AccountRuntimeSnapshot getCurrentAccountRuntimeSnapshot() {
                        return screen == null ? null : screen.getCurrentAccountRuntimeSnapshot();
                    }

                    @NonNull
                    @Override
                    public String getAppliedAccountHistoryRevision() {
                        return screen == null ? "" : screen.getAppliedAccountHistoryRevision();
                    }

                    @Override
                    public long getAppliedAccountUpdatedAt() {
                        return screen == null ? 0L : screen.getAppliedAccountUpdatedAt();
                    }

                    @Override
                    public long getScheduledSnapshotStaleAfterMs() {
                        return screen == null ? 0L : screen.getScheduledSnapshotStaleAfterMs();
                    }

                    @Override
                    public boolean isEmbeddedInHostShell() {
                        return true;
                    }

                    @Override
                    public void openMarketMonitor() {
                        startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.MARKET_MONITOR));
                    }

                    @Override
                    public void openMarketChart() {
                        startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.MARKET_CHART));
                    }

                    @Override
                    public void openAccountPosition() {
                        startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.ACCOUNT_POSITION));
                    }

                    @Override
                    public void openSettings() {
                        startActivity(new Intent(requireContext(), SettingsActivity.class));
                    }
                });
        screen.attachPageRuntime(pageRuntime);
        pageController = new AccountStatsPageController(new AccountStatsPageHostDelegate(
                pageRuntime),
                ContentAccountStatsBinding.bind(view),
                null);
        pageController.bind();
        pageController.onColdStart();
    }

    @Override
    public void onHostPageShown() {
        if (screen != null) {
            screen.onNewIntent(requireActivity().getIntent());
        }
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
        screen = null;
        super.onDestroyView();
    }
}
