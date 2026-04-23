/*
 * 账户持仓页分段差异比较测试，确保三段变化判断稳定。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class AccountPositionSectionDiffTest {

    // 仅概览变更时，应只标记概览变化。
    @Test
    public void diffShouldDetectOverviewChangeOnly() {
        AccountPositionUiModel previous = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-1"
        );
        AccountPositionUiModel current = createModel(
                Collections.singletonList(new AccountMetric("净值", "200")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-2"
        );

        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(previous, current);
        assertTrue(diff.isOverviewChanged());
        assertFalse(diff.isPositionsChanged());
        assertFalse(diff.isPendingChanged());
    }

    // 仅持仓变更时，应只标记持仓变化。
    @Test
    public void diffShouldDetectPositionChangeOnly() {
        AccountPositionUiModel previous = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-1"
        );
        AccountPositionUiModel current = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 2 条",
                Collections.singletonList(createPosition("ETHUSD", 12L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-2"
        );

        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(previous, current);
        assertFalse(diff.isOverviewChanged());
        assertTrue(diff.isPositionsChanged());
        assertFalse(diff.isPendingChanged());
    }

    // 仅挂单变更时，应只标记挂单变化。
    @Test
    public void diffShouldDetectPendingChangeOnly() {
        AccountPositionUiModel previous = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-1"
        );
        AccountPositionUiModel current = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 2 条",
                Collections.singletonList(createPosition("EURUSD", 22L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-2"
        );

        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(previous, current);
        assertFalse(diff.isOverviewChanged());
        assertFalse(diff.isPositionsChanged());
        assertTrue(diff.isPendingChanged());
    }

    // 完全相同时，不应误报变化。
    @Test
    public void diffShouldRemainFalseWhenNothingChanged() {
        AccountPositionUiModel previous = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-1"
        );
        AccountPositionUiModel current = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-9"
        );

        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(previous, current);
        assertFalse(diff.isOverviewChanged());
        assertFalse(diff.isPositionsChanged());
        assertFalse(diff.isPendingChanged());
        assertFalse(diff.hasAnyChange());
    }

    // 持仓展示字段变化时，即使摘要不变，也必须触发持仓段刷新。
    @Test
    public void diffShouldDetectPositionDisplayFieldChange() {
        AccountPositionUiModel previous = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L, 10d, 20d, 0.02d, 104d)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L, 10d, 20d, 0.02d, 104d)),
                "更新时间 2026-01-01 10:00:00",
                "sig-1"
        );
        AccountPositionUiModel current = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L, 12d, 24d, 0.05d, 104d)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L, 10d, 20d, 0.02d, 104d)),
                "更新时间 2026-01-01 10:00:00",
                "sig-2"
        );

        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(previous, current);
        assertFalse(diff.isOverviewChanged());
        assertTrue(diff.isPositionsChanged());
        assertFalse(diff.isPendingChanged());
    }

    // 挂单展示字段变化时，即使摘要不变，也必须触发挂单段刷新。
    @Test
    public void diffShouldDetectPendingDisplayFieldChange() {
        AccountPositionUiModel previous = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L, 10d, 20d, 0.02d, 104d)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L, 10d, 20d, 0.02d, 104d)),
                "更新时间 2026-01-01 10:00:00",
                "sig-1"
        );
        AccountPositionUiModel current = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L, 10d, 20d, 0.02d, 104d)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L, 10d, 20d, 0.02d, 108d)),
                "更新时间 2026-01-01 10:00:00",
                "sig-2"
        );

        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(previous, current);
        assertFalse(diff.isOverviewChanged());
        assertFalse(diff.isPositionsChanged());
        assertTrue(diff.isPendingChanged());
    }

    // 仅历史成交变化时，应只标记历史段变化。
    @Test
    public void diffShouldDetectHistoryChangeOnly() {
        AccountPositionUiModel previous = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-1"
        );
        AccountPositionUiModel current = createModel(
                Collections.singletonList(new AccountMetric("净值", "100")),
                "当前持仓 1 条",
                Collections.singletonList(createPosition("BTCUSD", 11L)),
                "挂单 1 条",
                Collections.singletonList(createPosition("XAUUSD", 21L)),
                "更新时间 2026-01-01 10:00:00",
                "sig-2"
        );

        AccountPositionSectionDiff.Result diff = AccountPositionSectionDiff.diff(
                previous,
                current,
                Collections.singletonList(createTrade("BTCUSD", 101L)),
                Collections.singletonList(createTrade("BTCUSD", 102L))
        );
        assertFalse(diff.isOverviewChanged());
        assertFalse(diff.isPositionsChanged());
        assertFalse(diff.isPendingChanged());
        assertTrue(diff.isHistoryChanged());
        assertTrue(diff.hasAnyChange());
    }

    // 组装测试模型。
    private static AccountPositionUiModel createModel(List<AccountMetric> metrics,
                                                      String positionSummary,
                                                      List<PositionItem> positions,
                                                      String pendingSummary,
                                                      List<PositionItem> pendingOrders,
                                                      String updatedAtText,
                                                      String signature) {
        return new AccountPositionUiModel(
                metrics,
                "",
                "",
                "已连接",
                positionSummary,
                pendingSummary,
                Collections.emptyList(),
                positions,
                pendingOrders,
                updatedAtText,
                signature,
                1710000000000L
        );
    }

    // 组装最小持仓数据。
    private static PositionItem createPosition(String code, long orderId) {
        return createPosition(code, orderId, 10d, 20d, 0.02d, 104d);
    }

    private static PositionItem createPosition(String code,
                                               long orderId,
                                               double takeProfit,
                                               double stopLoss,
                                               double storageFee,
                                               double pendingPrice) {
        return new PositionItem(
                code,
                code,
                "Buy",
                0L,
                orderId,
                0L,
                1d,
                1d,
                100d,
                101d,
                1000d,
                0.1d,
                10d,
                20d,
                0.02d,
                0d,
                0,
                pendingPrice,
                takeProfit,
                stopLoss,
                storageFee
        );
    }

    // 组装最小历史成交数据。
    private static TradeRecordItem createTrade(String code, long dealTicket) {
        return new TradeRecordItem(
                1710000000000L + dealTicket,
                code,
                code,
                "Buy",
                100d,
                1d,
                100d,
                1d,
                "remark",
                10d,
                1710000000000L,
                1710003600000L + dealTicket,
                0d,
                99d,
                101d,
                dealTicket,
                2000L + dealTicket,
                3000L + dealTicket,
                1
        );
    }
}
