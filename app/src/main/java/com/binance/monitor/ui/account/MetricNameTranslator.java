package com.binance.monitor.ui.account;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MetricNameTranslator {
    private static final Map<String, String> NAME_MAP = new HashMap<>();

    static {
        NAME_MAP.put("Total Asset", "总资产");
        NAME_MAP.put("Total Assets", "总资产");
        NAME_MAP.put("Net Asset", "净资产");
        NAME_MAP.put("Current Equity", "净资产");
        NAME_MAP.put("Margin", "保证金");
        NAME_MAP.put("Margin Amount", "保证金");
        NAME_MAP.put("Free Fund", "可用预付款");
        NAME_MAP.put("Available Funds", "可用预付款");
        NAME_MAP.put("Available", "可用预付款");
        NAME_MAP.put("Position Market Value", "持仓市值");
        NAME_MAP.put("Position Value", "持仓市值");
        NAME_MAP.put("Position PnL", "持仓盈亏");
        NAME_MAP.put("Daily PnL", "当日盈亏");
        NAME_MAP.put("Cumulative PnL", "累计盈亏");
        NAME_MAP.put("Daily Return", "当日收益率");
        NAME_MAP.put("Total Return", "累计收益率");
        NAME_MAP.put("Position Ratio", "仓位占比");
        NAME_MAP.put("Leverage", "杠杆");
        NAME_MAP.put("Margin Level", "保证金水平");

        NAME_MAP.put("1D Return", "近1日收益");
        NAME_MAP.put("7D Return", "近7日收益");
        NAME_MAP.put("30D Return", "近30日收益");
        NAME_MAP.put("Max Drawdown", "最大回撤");
        NAME_MAP.put("Volatility", "波动率");
        NAME_MAP.put("Sharpe", "夏普比率");
        NAME_MAP.put("Sharpe Ratio", "夏普比率");

        NAME_MAP.put("Cumulative Profit", "累计收益额");
        NAME_MAP.put("Cumulative Return", "累计收益率");
        NAME_MAP.put("Month Profit", "本月收益");
        NAME_MAP.put("YTD Profit", "年内收益");
        NAME_MAP.put("Daily Avg Profit", "日均收益");
        NAME_MAP.put("Total Trades", "总交易次数");
        NAME_MAP.put("Buy Count", "买入次数");
        NAME_MAP.put("Sell Count", "卖出次数");
        NAME_MAP.put("Win Rate", "胜率");
        NAME_MAP.put("Win/Loss Trades", "盈利交易数/亏损交易数");
        NAME_MAP.put("Avg Profit/Trade", "平均每笔盈利");
        NAME_MAP.put("Avg Loss/Trade", "平均每笔亏损");
        NAME_MAP.put("PnL Ratio", "盈亏比");
        NAME_MAP.put("Position Utilization", "仓位利用率");
        NAME_MAP.put("Single Position Max", "单一持仓最大占比");
        NAME_MAP.put("Concentration", "集中度");
        NAME_MAP.put("Consecutive Win/Loss", "连续盈利/连续亏损");
        NAME_MAP.put("Current Position Amount", "当前持仓金额");
        NAME_MAP.put("Asset Distribution", "资产分布");
        NAME_MAP.put("Top-5 Position Ratio", "前五大持仓占比");
        NAME_MAP.put("Data Source", "数据来源");
    }

    private MetricNameTranslator() {
    }

    public static String toChinese(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "--";
        }
        String direct = NAME_MAP.get(name);
        if (direct != null) {
            return direct;
        }
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : NAME_MAP.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) {
                return entry.getValue();
            }
        }
        return name;
    }
}
