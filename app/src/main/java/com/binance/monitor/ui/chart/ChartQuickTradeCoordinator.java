/*
 * 图表页快捷交易协调器，负责校验输入、解析当前有效价、决定挂单类型并生成交易命令。
 * 与 MarketChartScreen、TradeCommandFactory 和既有交易执行主链协同工作。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.NonNull;

import com.binance.monitor.data.model.v2.trade.TradeCommand;
import com.binance.monitor.ui.trade.TradeCommandFactory;

import java.util.Locale;

final class ChartQuickTradeCoordinator {

    interface AccountProvider {
        @NonNull
        String getAccountId();
    }

    interface SymbolProvider {
        @NonNull
        String getTradeSymbol();
    }

    interface PriceProvider {
        double getCurrentPrice();
    }

    interface Executor {
        void execute(@NonNull TradeCommand command);
    }

    private static final double MIN_PENDING_DISTANCE = 1e-6d;

    private final AccountProvider accountProvider;
    private final SymbolProvider symbolProvider;
    private final PriceProvider priceProvider;
    private final Executor executor;

    ChartQuickTradeCoordinator(@NonNull AccountProvider accountProvider,
                               @NonNull SymbolProvider symbolProvider,
                               @NonNull PriceProvider priceProvider,
                               @NonNull Executor executor) {
        this.accountProvider = accountProvider;
        this.symbolProvider = symbolProvider;
        this.priceProvider = priceProvider;
        this.executor = executor;
    }

    // 按当前输入直接生成市价买入命令。
    void executeMarketBuy(@NonNull String volumeText) {
        executor.execute(TradeCommandFactory.openMarket(
                requireAccountId(),
                requireSymbol(),
                "buy",
                requireVolume(volumeText),
                requireCurrentPrice(),
                0d,
                0d
        ));
    }

    // 按当前输入直接生成市价卖出命令。
    void executeMarketSell(@NonNull String volumeText) {
        executor.execute(TradeCommandFactory.openMarket(
                requireAccountId(),
                requireSymbol(),
                "sell",
                requireVolume(volumeText),
                requireCurrentPrice(),
                0d,
                0d
        ));
    }

    // 根据挂单线价格直接生成挂单买入命令。
    void executePendingBuy(@NonNull String volumeText, double linePrice) {
        executePending(volumeText, linePrice, true);
    }

    // 根据挂单线价格直接生成挂单卖出命令。
    void executePendingSell(@NonNull String volumeText, double linePrice) {
        executePending(volumeText, linePrice, false);
    }

    @NonNull
    String resolvePendingOrderType(boolean buySide, double linePrice, double currentPrice) {
        requirePendingDistance(linePrice, currentPrice);
        if (buySide) {
            return linePrice < currentPrice ? "buy_limit" : "buy_stop";
        }
        return linePrice > currentPrice ? "sell_limit" : "sell_stop";
    }

    // 用统一规则生成挂单新增命令，避免页面层自己拼 orderType。
    private void executePending(@NonNull String volumeText, double linePrice, boolean buySide) {
        double currentPrice = requireCurrentPrice();
        String orderType = resolvePendingOrderType(buySide, linePrice, currentPrice);
        executor.execute(TradeCommandFactory.pendingAdd(
                requireAccountId(),
                requireSymbol(),
                orderType,
                requireVolume(volumeText),
                linePrice,
                0d,
                0d
        ));
    }

    private double requireCurrentPrice() {
        double value = priceProvider.getCurrentPrice();
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException("当前价格未就绪，暂时不能执行交易");
        }
        return value;
    }

    private void requirePendingDistance(double linePrice, double currentPrice) {
        if (!Double.isFinite(linePrice) || linePrice <= 0d) {
            throw new IllegalArgumentException("挂单价格未就绪");
        }
        if (Math.abs(linePrice - currentPrice) <= MIN_PENDING_DISTANCE) {
            throw new IllegalArgumentException("请把挂单线再上移或下移一点");
        }
    }

    private double requireVolume(@NonNull String volumeText) {
        double volume = MarketChartTradeSupport.parseOptionalDouble(volumeText, 0d);
        if (!Double.isFinite(volume) || volume <= 0d) {
            throw new IllegalArgumentException("手数必须大于 0");
        }
        return volume;
    }

    @NonNull
    private String requireAccountId() {
        String accountId = accountProvider.getAccountId().trim();
        if (accountId.isEmpty()) {
            throw new IllegalArgumentException("当前账户未连接，暂时不能交易");
        }
        return accountId;
    }

    @NonNull
    private String requireSymbol() {
        String symbol = symbolProvider.getTradeSymbol().trim().toUpperCase(Locale.ROOT);
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("当前品种未就绪");
        }
        return symbol;
    }
}
