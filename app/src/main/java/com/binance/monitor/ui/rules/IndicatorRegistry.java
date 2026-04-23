/*
 * 全局指标注册表，统一维护正式指标定义与历史名称映射。
 */
package com.binance.monitor.ui.rules;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.runtime.account.MetricNameTranslator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class IndicatorRegistry {
    private static final Map<IndicatorId, IndicatorDefinition> DEFINITIONS =
            new EnumMap<>(IndicatorId.class);
    private static final Map<String, IndicatorId> LABEL_INDEX = new HashMap<>();

    static {
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_TOTAL_ASSET,
                "总资产",
                "总资产",
                IndicatorCategory.ASSET,
                IndicatorValueType.MONEY,
                "$",
                2,
                IndicatorColorRule.NEUTRAL,
                "Total Asset",
                "Total Assets"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_NET_ASSET,
                "净资产",
                "净资产",
                IndicatorCategory.ASSET,
                IndicatorValueType.MONEY,
                "$",
                2,
                IndicatorColorRule.NEUTRAL,
                "净值",
                "当前净值",
                "Net Asset",
                "Current Equity"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_AVAILABLE_FUNDS,
                "可用预付款",
                "可用预付款",
                IndicatorCategory.ASSET,
                IndicatorValueType.MONEY,
                "$",
                2,
                IndicatorColorRule.NEUTRAL,
                "可用资金",
                "可用保证金",
                "Available",
                "Available Funds",
                "Free Fund"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_MARGIN,
                "保证金",
                "保证金",
                IndicatorCategory.ASSET,
                IndicatorValueType.MONEY,
                "$",
                2,
                IndicatorColorRule.NEUTRAL,
                "保证金金额",
                "预付款",
                "Margin",
                "Margin Amount"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_POSITION_PNL,
                "持仓盈亏",
                "持仓盈亏",
                IndicatorCategory.POSITION,
                IndicatorValueType.MONEY,
                "$",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN,
                "Position PnL"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_POSITION_PNL_RATE,
                "持仓收益率",
                "持仓收益率",
                IndicatorCategory.POSITION,
                IndicatorValueType.PERCENT,
                "%",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN,
                "Position Return"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_TOTAL_RETURN_AMOUNT,
                "累计收益额",
                "累计收益额",
                IndicatorCategory.RETURN,
                IndicatorValueType.MONEY,
                "$",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN,
                "累计盈亏",
                "Cumulative Profit",
                "Cumulative PnL"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_TOTAL_RETURN_RATE,
                "累计收益率",
                "累计收益率",
                IndicatorCategory.RETURN,
                IndicatorValueType.PERCENT,
                "%",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN,
                "Total Return",
                "Cumulative Return"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_MAX_DRAWDOWN,
                "最大回撤",
                "最大回撤",
                IndicatorCategory.RISK,
                IndicatorValueType.PERCENT,
                "%",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN,
                "Max Drawdown"
        ));
        register(new IndicatorDefinition(
                IndicatorId.ACCOUNT_SHARPE_RATIO,
                "夏普比率",
                "夏普比率",
                IndicatorCategory.RISK,
                IndicatorValueType.TEXT,
                "",
                2,
                IndicatorColorRule.NEUTRAL,
                "Sharpe",
                "Sharpe Ratio"
        ));
        register(new IndicatorDefinition(
                IndicatorId.TRADE_TOTAL_COUNT,
                "总交易次数",
                "总交易次数",
                IndicatorCategory.TRADE,
                IndicatorValueType.COUNT,
                "笔",
                0,
                IndicatorColorRule.NEUTRAL,
                "交易次数",
                "Total Trades"
        ));
        register(new IndicatorDefinition(
                IndicatorId.TRADE_WIN_RATE,
                "胜率",
                "胜率",
                IndicatorCategory.TRADE,
                IndicatorValueType.PERCENT,
                "%",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN,
                "Win Rate"
        ));
        register(new IndicatorDefinition(
                IndicatorId.TRADE_AVG_PROFIT,
                "平均每笔盈利",
                "平均每笔盈利",
                IndicatorCategory.TRADE,
                IndicatorValueType.MONEY,
                "$",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN,
                "Avg Profit/Trade"
        ));
        register(new IndicatorDefinition(
                IndicatorId.TRADE_AVG_LOSS,
                "平均每笔亏损",
                "平均每笔亏损",
                IndicatorCategory.TRADE,
                IndicatorValueType.MONEY,
                "$",
                2,
                IndicatorColorRule.PROFIT_UP_LOSS_DOWN,
                "Avg Loss/Trade"
        ));
        register(new IndicatorDefinition(
                IndicatorId.TRADE_WIN_LOSS_COUNT,
                "盈利交易数/亏损交易数",
                "盈利交易数/亏损交易数",
                IndicatorCategory.TRADE,
                IndicatorValueType.TEXT,
                "",
                0,
                IndicatorColorRule.NEUTRAL,
                "Win/Loss Trades"
        ));
        register(new IndicatorDefinition(
                IndicatorId.TRADE_GROSS_PROFIT_LOSS,
                "毛利/毛损",
                "毛利/毛损",
                IndicatorCategory.TRADE,
                IndicatorValueType.TEXT,
                "",
                0,
                IndicatorColorRule.NEUTRAL
        ));
    }

    private IndicatorRegistry() {
    }

    // 返回指定指标的正式定义，缺失即视为实现错误。
    @NonNull
    public static IndicatorDefinition require(@NonNull IndicatorId id) {
        IndicatorDefinition definition = DEFINITIONS.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Missing indicator definition for " + id);
        }
        return definition;
    }

    // 按历史或正式显示名查找定义，供迁移期兼容旧页面调用。
    @Nullable
    public static IndicatorDefinition findByDisplayName(@Nullable String rawName) {
        String normalized = normalizeLabel(rawName);
        if (normalized.isEmpty()) {
            return null;
        }
        IndicatorId id = LABEL_INDEX.get(normalized);
        return id == null ? null : DEFINITIONS.get(id);
    }

    // 暴露只读定义表，供测试和规则检查使用。
    @NonNull
    public static Map<IndicatorId, IndicatorDefinition> definitions() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }

    // 统一注册正式定义及其别名索引。
    private static void register(@NonNull IndicatorDefinition definition) {
        DEFINITIONS.put(definition.getId(), definition);
        indexLabel(definition.getDisplayName(), definition.getId());
        indexLabel(definition.getShortName(), definition.getId());
        for (String alias : definition.getAliases()) {
            indexLabel(alias, definition.getId());
        }
    }

    // 统一给一个标签建立索引。
    private static void indexLabel(@Nullable String rawLabel, @NonNull IndicatorId id) {
        String normalized = normalizeLabel(rawLabel);
        if (!normalized.isEmpty()) {
            LABEL_INDEX.put(normalized, id);
        }
    }

    // 统一规整标签，兼容英文名、空格和大小写差异。
    @NonNull
    public static String normalizeLabel(@Nullable String rawLabel) {
        if (rawLabel == null) {
            return "";
        }
        String translated = MetricNameTranslator.toChinese(rawLabel);
        return translated
                .trim()
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }
}
