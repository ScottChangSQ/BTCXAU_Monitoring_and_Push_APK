/*
 * 账户曲线重建辅助，负责基于历史交易重算“结余 + 历史持仓盈亏”的净值曲线。
 * 供 AccountStatsBridgeActivity 在展示历史区间净值时统一复用。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AccountCurveRebuildHelper {

    private static final double VALUE_EPSILON = 0.01d;
    private static final double SOURCE_SPREAD_MIN_RATIO = 0.35d;
    private static final double SOURCE_SPREAD_DEVIATION_MIN = 8d;
    private static final double SOURCE_SPREAD_DEVIATION_RATIO = 2d;
    private static final double SOURCE_SPREAD_OUTLIER_BASE = 100d;
    private static final double SOURCE_SPREAD_OUTLIER_RATIO = 8d;
    private static final double SOURCE_SPREAD_OUTLIER_PADDING = 20d;

    private AccountCurveRebuildHelper() {
    }

    // 按历史交易重建曲线：结余只在平仓时变化，净值统一按“结余 + 持仓盈亏”计算。
    static List<CurvePoint> rebuild(@Nullable List<CurvePoint> source,
                                    @Nullable List<TradeRecordItem> trades,
                                    double initialBalance) {
        List<CurvePoint> normalized = AccountCurvePointNormalizer.normalize(source, initialBalance);
        if (trades == null || trades.isEmpty() || normalized.isEmpty()) {
            return normalized;
        }

        List<TradeRecordItem> sortedTrades = new ArrayList<>();
        for (TradeRecordItem trade : trades) {
            if (trade != null) {
                sortedTrades.add(trade);
            }
        }
        if (sortedTrades.isEmpty()) {
            return normalized;
        }
        sortedTrades.sort(Comparator.comparingLong(AccountCurveRebuildHelper::resolveTradeSortTimestamp));

        long firstTimestamp = normalized.get(0).getTimestamp();
        double startingBalance = normalized.get(0).getBalance() > 0d
                ? normalized.get(0).getBalance()
                : initialBalance;
        List<CurvePoint> rebuilt = new ArrayList<>(normalized.size());
        for (int index = 0; index < normalized.size(); index++) {
            CurvePoint point = normalized.get(index);
            long timestamp = point.getTimestamp();
            double rebuiltBalance = startingBalance + sumClosedTradeDelta(sortedTrades, firstTimestamp, timestamp);
            FloatingState floatingState = resolveFloatingState(sortedTrades, timestamp);
            double sourceEquity = resolveSourceEquity(normalized, index, point, rebuiltBalance, floatingState);
            rebuilt.add(new CurvePoint(
                    timestamp,
                    sourceEquity,
                    rebuiltBalance,
                    point.getPositionRatio()
            ));
        }
        return rebuilt;
    }

    // 汇总到当前时刻之前已平仓成交的已实现盈亏。
    private static double sumClosedTradeDelta(List<TradeRecordItem> trades, long firstTimestamp, long timestamp) {
        double delta = 0d;
        for (TradeRecordItem trade : trades) {
            long closeTime = resolveCloseTime(trade);
            if (closeTime <= firstTimestamp || closeTime > timestamp) {
                continue;
            }
            delta += resolveTradeDelta(trade);
        }
        return delta;
    }

    // 汇总当前时刻仍在持仓中的每一单历史盈亏，净值曲线按“结余 + 持仓盈亏”构建。
    private static FloatingState resolveFloatingState(List<TradeRecordItem> trades, long timestamp) {
        double total = 0d;
        int activeCount = 0;
        for (TradeRecordItem trade : trades) {
            long openTime = resolveOpenTime(trade);
            long closeTime = resolveCloseTime(trade);
            if (openTime <= 0L || closeTime <= openTime) {
                continue;
            }
            if (timestamp > openTime && timestamp < closeTime) {
                double quantity = Math.abs(trade.getQuantity());
                double price = resolveHistoricalPrice(trade, timestamp, openTime, closeTime);
                total += resolveDirectionalFloatingPnl(trade, quantity, price);
                activeCount++;
            }
        }
        return new FloatingState(total, activeCount);
    }

    // 当服务端原始净值曲线已经是平滑且可信的真实轨迹时，优先保留它，避免客户端线性重放把回撤抹平。
    private static double resolveSourceEquity(List<CurvePoint> sourcePoints,
                                              int sourceIndex,
                                              CurvePoint sourcePoint,
                                              double rebuiltBalance,
                                              FloatingState floatingState) {
        if (sourcePoint == null) {
            return rebuiltBalance + floatingState.floatingPnl;
        }
        if (floatingState.activeCount <= 0) {
            return rebuiltBalance;
        }
        double sourceSpread = sourcePoint.getEquity() - sourcePoint.getBalance();
        double simulatedSpread = floatingState.floatingPnl;
        if (!isReasonableSourceSpread(sourcePoints, sourceIndex, sourceSpread, simulatedSpread)) {
            return rebuiltBalance + simulatedSpread;
        }
        return rebuiltBalance + sourceSpread;
    }

    // 原始净值差值为 0 或严重偏离时，认为它是不完整或异常数据，回退到客户端重放值。
    private static boolean isReasonableSourceSpread(List<CurvePoint> sourcePoints,
                                                    int sourceIndex,
                                                    double sourceSpread,
                                                    double simulatedSpread) {
        double absSource = Math.abs(sourceSpread);
        double absSimulated = Math.abs(simulatedSpread);
        if (absSource <= VALUE_EPSILON) {
            return absSimulated <= VALUE_EPSILON || hasMeaningfulNeighborSpread(sourcePoints, sourceIndex);
        }
        if (absSimulated > VALUE_EPSILON && absSource < absSimulated * SOURCE_SPREAD_MIN_RATIO) {
            return false;
        }
        double maxAllowedDeviation = Math.max(
                SOURCE_SPREAD_DEVIATION_MIN,
                absSimulated * SOURCE_SPREAD_DEVIATION_RATIO
        );
        if (Math.abs(sourceSpread - simulatedSpread) > maxAllowedDeviation) {
            return false;
        }
        double maxAllowedAbsolute = Math.max(
                SOURCE_SPREAD_OUTLIER_BASE,
                absSimulated * SOURCE_SPREAD_OUTLIER_RATIO + SOURCE_SPREAD_OUTLIER_PADDING
        );
        return absSource <= maxAllowedAbsolute;
    }

    // 当前点若刚好落在真实浮盈回到 0 的位置，只要前后源曲线仍有明显浮动，就继续信任服务端原始曲线。
    private static boolean hasMeaningfulNeighborSpread(List<CurvePoint> sourcePoints, int sourceIndex) {
        if (sourcePoints == null || sourceIndex < 0 || sourceIndex >= sourcePoints.size()) {
            return false;
        }
        if (sourceIndex > 0) {
            CurvePoint previous = sourcePoints.get(sourceIndex - 1);
            if (previous != null && Math.abs(previous.getEquity() - previous.getBalance()) > VALUE_EPSILON) {
                return true;
            }
        }
        if (sourceIndex + 1 < sourcePoints.size()) {
            CurvePoint next = sourcePoints.get(sourceIndex + 1);
            if (next != null && Math.abs(next.getEquity() - next.getBalance()) > VALUE_EPSILON) {
                return true;
            }
        }
        return false;
    }

    // 统一解析单笔成交对应的已实现盈亏。
    private static double resolveTradeDelta(TradeRecordItem trade) {
        if (trade == null) {
            return 0d;
        }
        return trade.getProfit() + trade.getStorageFee();
    }

    // 统一解析开仓时间，缺失时回退到成交时间。
    private static long resolveOpenTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        return trade.getOpenTime() > 0L ? trade.getOpenTime() : trade.getTimestamp();
    }

    // 统一解析平仓时间，平仓成交缺 closeTime 时回退到成交时间。
    private static long resolveCloseTime(TradeRecordItem trade) {
        if (trade == null) {
            return 0L;
        }
        if (trade.getCloseTime() > 0L) {
            return trade.getCloseTime();
        }
        return trade.getTimestamp();
    }

    // 历史成交排序优先按平仓时间，其次按开仓时间。
    private static long resolveTradeSortTimestamp(TradeRecordItem trade) {
        long closeTime = resolveCloseTime(trade);
        return closeTime > 0L ? closeTime : resolveOpenTime(trade);
    }

    // 历史时点价格优先按开平仓价做线性插值，拿不到时回退到成交价格。
    private static double resolveHistoricalPrice(TradeRecordItem trade,
                                                 long timestamp,
                                                 long openTime,
                                                 long closeTime) {
        if (trade == null) {
            return 0d;
        }
        double openPrice = trade.getOpenPrice() > 0d ? trade.getOpenPrice() : trade.getPrice();
        double closePrice = trade.getClosePrice() > 0d ? trade.getClosePrice() : trade.getPrice();
        if (openPrice <= 0d && closePrice <= 0d) {
            return 0d;
        }
        if (openPrice <= 0d) {
            return closePrice;
        }
        if (closePrice <= 0d || closeTime <= openTime) {
            return openPrice;
        }
        double progress = (double) (timestamp - openTime) / (double) (closeTime - openTime);
        double clamped = Math.max(0d, Math.min(1d, progress));
        return openPrice + (closePrice - openPrice) * clamped;
    }

    // 历史持仓盈亏按方向区分：Buy 看“现价 - 开仓价”，Sell 看“开仓价 - 现价”。
    private static double resolveDirectionalFloatingPnl(TradeRecordItem trade,
                                                        double quantity,
                                                        double historicalPrice) {
        if (trade == null || quantity <= 0d) {
            return 0d;
        }
        double openPrice = trade.getOpenPrice() > 0d ? trade.getOpenPrice() : trade.getPrice();
        double multiplier = resolveProductMultiplier(trade);
        if (openPrice <= 0d || multiplier <= 0d) {
            return 0d;
        }
        if (isSellTrade(trade)) {
            return quantity * (openPrice - historicalPrice) * multiplier;
        }
        return quantity * (historicalPrice - openPrice) * multiplier;
    }

    // 产品倍数目前按 BTC=1、XAU=100 估算，其它品种默认 1。
    private static double resolveProductMultiplier(TradeRecordItem trade) {
        String symbol = resolveSymbol(trade).toUpperCase();
        if (symbol.startsWith("XAU")) {
            return 100d;
        }
        return 1d;
    }

    // 统一读取产品编码，优先 code，缺失时回退 productName。
    private static String resolveSymbol(TradeRecordItem trade) {
        if (trade == null) {
            return "";
        }
        String code = trade.getCode();
        if (code != null && !code.trim().isEmpty()) {
            return code.trim();
        }
        String productName = trade.getProductName();
        return productName == null ? "" : productName.trim();
    }

    // 方向识别统一兼容中英文卖出标记，缺省按买入处理。
    private static boolean isSellTrade(TradeRecordItem trade) {
        if (trade == null || trade.getSide() == null) {
            return false;
        }
        String side = trade.getSide().trim().toLowerCase();
        return side.contains("sell") || side.contains("short") || side.contains("卖");
    }

    private static final class FloatingState {
        private final double floatingPnl;
        private final int activeCount;

        private FloatingState(double floatingPnl, int activeCount) {
            this.floatingPnl = floatingPnl;
            this.activeCount = activeCount;
        }
    }
}
