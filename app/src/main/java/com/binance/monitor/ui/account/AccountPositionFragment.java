/*
 * 账户持仓主壳页，承接账户持仓共享页面控制器。
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
import com.binance.monitor.databinding.ContentAccountPositionBinding;
import com.binance.monitor.ui.host.HostNavigationIntentFactory;
import com.binance.monitor.ui.host.HostTab;
import com.binance.monitor.ui.host.HostTabPage;

public class AccountPositionFragment extends Fragment implements HostTabPage {
    private AccountPositionPageController pageController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account_position, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pageController = new AccountPositionPageController(
                new AccountPositionPageController.Host() {
                    @NonNull
                    @Override
                    public AppCompatActivity requireActivity() {
                        return (AppCompatActivity) AccountPositionFragment.this.requireActivity();
                    }

                    @NonNull
                    @Override
                    public android.content.Context getApplicationContext() {
                        return AccountPositionFragment.this.requireContext().getApplicationContext();
                    }

                    @Override
                    public boolean isEmbeddedInHostShell() {
                        return true;
                    }

                    @Override
                    public boolean isPageReady() {
                        return isAdded()
                                && getView() != null
                                && !requireActivity().isFinishing()
                                && !requireActivity().isDestroyed();
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
                    public void openAccountStats() {
                        startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.ACCOUNT_STATS));
                    }

                    @Override
                    public void openSettings() {
                        startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.SETTINGS));
                    }

                    @Override
                    public void openChartTradeAction(@Nullable com.binance.monitor.domain.account.model.PositionItem item,
                                                     @NonNull String tradeAction) {
                        Intent intent = HostNavigationIntentFactory.forTab(requireContext(), HostTab.MARKET_CHART);
                        String symbol = item == null ? "" : item.getCode();
                        if (symbol == null || symbol.trim().isEmpty()) {
                            symbol = item == null ? "" : item.getProductName();
                        }
                        symbol = com.binance.monitor.util.ProductSymbolMapper.toMarketSymbol(symbol);
                        intent.putExtra(com.binance.monitor.ui.chart.MarketChartActivity.EXTRA_TARGET_SYMBOL, symbol);
                        intent.putExtra(com.binance.monitor.ui.chart.MarketChartActivity.EXTRA_TRADE_ACTION, tradeAction);
                        intent.putExtra(com.binance.monitor.ui.chart.MarketChartActivity.EXTRA_TRADE_POSITION_TICKET,
                                item == null ? 0L : item.getPositionTicket());
                        intent.putExtra(com.binance.monitor.ui.chart.MarketChartActivity.EXTRA_TRADE_ORDER_TICKET,
                                item == null ? 0L : item.getOrderId());
                        startActivity(intent);
                        requireActivity().overridePendingTransition(0, 0);
                    }
                },
                ContentAccountPositionBinding.bind(view),
                null
        );
        pageController.bind();
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
        super.onDestroyView();
    }
}
