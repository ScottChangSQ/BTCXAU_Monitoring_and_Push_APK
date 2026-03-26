package com.binance.monitor.ui.account;

import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;
import com.binance.monitor.util.FormatUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class AccountStatsFallbackDataSource {

    private final List<CurvePoint> allCurvePoints;
    private final List<PositionItem> basePositions;
    private final List<TradeRecordItem> baseTrades;

    public AccountStatsFallbackDataSource() {
        allCurvePoints = buildCurvePoints();
        basePositions = buildPositions();
        baseTrades = buildTrades(basePositions);
    }

    public AccountSnapshot load(AccountTimeRange range) {
        List<CurvePoint> rangePoints = filterCurve(range);
        List<PositionItem> positions = new ArrayList<>(basePositions);
        List<TradeRecordItem> trades = new ArrayList<>(baseTrades);

        double equity = rangePoints.isEmpty() ? 0d : rangePoints.get(rangePoints.size() - 1).getEquity();
        double balance = rangePoints.isEmpty() ? 0d : rangePoints.get(rangePoints.size() - 1).getBalance();
        double marketValue = sumMarketValue(positions);
        double totalPnL = sumTotalPnL(positions);
        double dayPnL = sumDayPnL(positions);
        double margin = equity * 0.38d;
        double freeFund = equity - margin;
        double totalAsset = equity + Math.max(0d, totalPnL * 0.3d);
        double dayReturn = safeDivide(dayPnL, Math.max(1d, equity - dayPnL));
        double totalReturn = safeDivide(totalPnL, Math.max(1d, balance - totalPnL));
        double positionUsage = safeDivide(marketValue, Math.max(1d, equity));

        List<AccountMetric> overview = Arrays.asList(
                new AccountMetric("Total Asset", money(totalAsset)),
                new AccountMetric("Margin", money(margin)),
                new AccountMetric("Free Fund", money(freeFund)),
                new AccountMetric("Position Market Value", money(marketValue)),
                new AccountMetric("Position PnL", signedMoney(totalPnL)),
                new AccountMetric("Daily PnL", signedMoney(dayPnL)),
                new AccountMetric("Cumulative PnL", signedMoney(totalPnL)),
                new AccountMetric("Current Equity", money(equity)),
                new AccountMetric("Daily Return", percent(dayReturn)),
                new AccountMetric("Total Return", percent(totalReturn)),
                new AccountMetric("Position Ratio", percent(positionUsage))
        );

        CurveAnalysis analysis = analyzeCurve(rangePoints);
        List<AccountMetric> curveIndicators = Arrays.asList(
                new AccountMetric("1D Return", percent(analysis.return1d)),
                new AccountMetric("7D Return", percent(analysis.return7d)),
                new AccountMetric("30D Return", percent(analysis.return30d)),
                new AccountMetric("Max Drawdown", percent(analysis.maxDrawdown)),
                new AccountMetric("Volatility", percent(analysis.volatility)),
                new AccountMetric("Sharpe", decimal(analysis.sharpe, 2))
        );

        List<AccountMetric> stats = Arrays.asList(
                new AccountMetric("Cumulative Profit", signedMoney(totalPnL)),
                new AccountMetric("Cumulative Return", percent(totalReturn)),
                new AccountMetric("Month Profit", signedMoney(totalPnL * 0.24d)),
                new AccountMetric("YTD Profit", signedMoney(totalPnL * 0.78d)),
                new AccountMetric("Daily Avg Profit", signedMoney(totalPnL / 180d)),
                new AccountMetric("Total Trades", String.valueOf(trades.size())),
                new AccountMetric("Buy Count", String.valueOf(countTradesBySide(trades, "Buy"))),
                new AccountMetric("Sell Count", String.valueOf(countTradesBySide(trades, "Sell"))),
                new AccountMetric("Win Rate", percent(0.57d)),
                new AccountMetric("Win/Loss Trades", "39 / 29"),
                new AccountMetric("Avg Profit/Trade", money(421.8)),
                new AccountMetric("Avg Loss/Trade", money(-286.4)),
                new AccountMetric("PnL Ratio", decimal(1.47d, 2)),
                new AccountMetric("Max Drawdown", percent(analysis.maxDrawdown)),
                new AccountMetric("Volatility", percent(analysis.volatility)),
                new AccountMetric("Position Utilization", percent(positionUsage)),
                new AccountMetric("Single Position Max", percent(maxPositionRatio(positions))),
                new AccountMetric("Concentration", percent(topFiveRatio(positions))),
                new AccountMetric("Consecutive Win/Loss", "5 / 3"),
                new AccountMetric("Current Position Amount", money(marketValue)),
                new AccountMetric("Asset Distribution", "黄金 / 比特币 / 外汇 / 指数"),
                new AccountMetric("Top-5 Position Ratio", percent(topFiveRatio(positions)))
        );

        return new AccountSnapshot(overview, rangePoints, curveIndicators, positions, trades, stats);
    }

    private List<CurvePoint> filterCurve(AccountTimeRange range) {
        int size = allCurvePoints.size();
        int keep;
        switch (range) {
            case D1:
                keep = Math.min(24, size);
                break;
            case D7:
                keep = Math.min(7 * 24, size);
                break;
            case M1:
                keep = Math.min(30 * 24, size);
                break;
            case M3:
                keep = Math.min(90 * 24, size);
                break;
            case Y1:
                keep = Math.min(365 * 24, size);
                break;
            case ALL:
            default:
                keep = size;
                break;
        }
        return new ArrayList<>(allCurvePoints.subList(size - keep, size));
    }

    private List<CurvePoint> buildCurvePoints() {
        List<CurvePoint> points = new ArrayList<>();
        long now = System.currentTimeMillis();
        long step = 60L * 60L * 1000L;
        int total = 365 * 24;
        double balance = 100_000d;
        double equity = 100_000d;
        Random random = new Random(7400048L);
        for (int i = total - 1; i >= 0; i--) {
            double drift = 6d + random.nextDouble() * 8d;
            double shock = (random.nextDouble() - 0.5d) * 160d;
            balance += drift + shock * 0.05d;
            equity = balance + (random.nextDouble() - 0.5d) * 1200d;
            points.add(new CurvePoint(now - i * step, Math.max(72_000d, equity), Math.max(70_000d, balance)));
        }
        return points;
    }

    private List<PositionItem> buildPositions() {
        return Arrays.asList(
                new PositionItem("黄金", "XAUUSD", "Buy", 18.5, 18.5, 2342.3, 2366.5, 43_770, 0.245, 438, 2_964, 0.0727, 2.0, 2, 2358.6),
                new PositionItem("比特币", "BTCUSD", "Buy", 1.25, 1.25, 86_240, 88_120, 110_150, 0.615, 1_190, 2_350, 0.0218, 0.5, 1, 87950.0),
                new PositionItem("纳指", "NAS100", "Sell", 7.0, 7.0, 18_922, 18_775, 12_650, 0.071, -294, -1_029, -0.0752, 1.0, 1, 18980.0),
                new PositionItem("原油", "WTI", "Buy", 22.0, 22.0, 79.11, 80.25, 17_655, 0.099, 251, 916, 0.0547, 0.0, 0, 0.0),
                new PositionItem("欧元", "EURUSD", "Buy", 45_000, 45_000, 1.0832, 1.0865, 4_889, 0.027, 132, 186, 0.0396, 10_000, 2, 1.0815),
                new PositionItem("英镑", "GBPUSD", "Sell", 30_000, 30_000, 1.2623, 1.2597, 3_780, 0.021, -78, -121, -0.0310, 0.0, 0, 0.0)
        );
    }

    private List<TradeRecordItem> buildTrades(List<PositionItem> positions) {
        List<TradeRecordItem> trades = new ArrayList<>();
        Random random = new Random(20260326L);
        long now = System.currentTimeMillis();
        String[] remarks = {"策略跟随", "分批止盈", "回撤对冲", "趋势入场", "风险对冲"};
        for (int i = 0; i < 72; i++) {
            PositionItem position = positions.get(i % positions.size());
            boolean buy = (i % 3) != 0;
            double qty = Math.max(0.1d, position.getQuantity() * (0.08d + random.nextDouble() * 0.18d));
            double price = position.getLatestPrice() * (0.98d + random.nextDouble() * 0.04d);
            double amount = qty * price;
            trades.add(new TradeRecordItem(
                    now - i * 4L * 60L * 60L * 1000L,
                    position.getProductName(),
                    position.getCode(),
                    buy ? "Buy" : "Sell",
                    price,
                    qty,
                    amount,
                    amount * 0.0006d,
                    remarks[i % remarks.length]
            ));
        }
        return trades;
    }

    private CurveAnalysis analyzeCurve(List<CurvePoint> points) {
        CurveAnalysis analysis = new CurveAnalysis();
        if (points.isEmpty()) {
            return analysis;
        }
        double peak = points.get(0).getEquity();
        double maxDd = 0d;
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            double current = points.get(i).getEquity();
            if (current > peak) {
                peak = current;
            }
            double drawdown = safeDivide(peak - current, peak);
            maxDd = Math.max(maxDd, drawdown);
            double prev = points.get(i - 1).getEquity();
            returns.add(safeDivide(current - prev, prev));
        }
        analysis.maxDrawdown = maxDd;
        analysis.return1d = returnN(points, 24);
        analysis.return7d = returnN(points, 24 * 7);
        analysis.return30d = returnN(points, 24 * 30);
        analysis.volatility = calcStd(returns) * Math.sqrt(24d * 365d);
        double avg = returns.isEmpty() ? 0d : returns.stream().mapToDouble(v -> v).average().orElse(0d);
        analysis.sharpe = analysis.volatility == 0d ? 0d : (avg * 24d * 365d) / analysis.volatility;
        return analysis;
    }

    private double calcStd(List<Double> values) {
        if (values.isEmpty()) {
            return 0d;
        }
        double avg = values.stream().mapToDouble(v -> v).average().orElse(0d);
        double sum = 0d;
        for (double value : values) {
            sum += (value - avg) * (value - avg);
        }
        return Math.sqrt(sum / values.size());
    }

    private double returnN(List<CurvePoint> points, int n) {
        if (points.size() < 2) {
            return 0d;
        }
        int from = Math.max(0, points.size() - 1 - n);
        double start = points.get(from).getEquity();
        double end = points.get(points.size() - 1).getEquity();
        return safeDivide(end - start, start);
    }

    private int countTradesBySide(List<TradeRecordItem> trades, String side) {
        int count = 0;
        for (TradeRecordItem trade : trades) {
            if (side.equalsIgnoreCase(trade.getSide())) {
                count++;
            }
        }
        return count;
    }

    private double maxPositionRatio(List<PositionItem> positions) {
        double max = 0d;
        for (PositionItem position : positions) {
            max = Math.max(max, position.getPositionRatio());
        }
        return max;
    }

    private double topFiveRatio(List<PositionItem> positions) {
        List<Double> ratios = new ArrayList<>();
        for (PositionItem position : positions) {
            ratios.add(position.getPositionRatio());
        }
        ratios.sort(Collections.reverseOrder());
        double sum = 0d;
        for (int i = 0; i < Math.min(5, ratios.size()); i++) {
            sum += ratios.get(i);
        }
        return sum;
    }

    private double sumMarketValue(List<PositionItem> positions) {
        double sum = 0d;
        for (PositionItem item : positions) {
            sum += item.getMarketValue();
        }
        return sum;
    }

    private double sumTotalPnL(List<PositionItem> positions) {
        double sum = 0d;
        for (PositionItem item : positions) {
            sum += item.getTotalPnL();
        }
        return sum;
    }

    private double sumDayPnL(List<PositionItem> positions) {
        double sum = 0d;
        for (PositionItem item : positions) {
            sum += item.getDayPnL();
        }
        return sum;
    }

    private double safeDivide(double a, double b) {
        if (b == 0d) {
            return 0d;
        }
        return a / b;
    }

    private String money(double value) {
        return "$" + FormatUtils.formatPrice(Math.abs(value));
    }

    private String signedMoney(double value) {
        String sign = value >= 0d ? "+" : "-";
        return sign + "$" + FormatUtils.formatPrice(Math.abs(value));
    }

    private String percent(double value) {
        return String.format(Locale.getDefault(), "%+.2f%%", value * 100d);
    }

    private String decimal(double value, int scale) {
        String pattern = "%." + scale + "f";
        return String.format(Locale.getDefault(), pattern, value);
    }

    private static class CurveAnalysis {
        private double return1d;
        private double return7d;
        private double return30d;
        private double maxDrawdown;
        private double volatility;
        private double sharpe;
    }
}
