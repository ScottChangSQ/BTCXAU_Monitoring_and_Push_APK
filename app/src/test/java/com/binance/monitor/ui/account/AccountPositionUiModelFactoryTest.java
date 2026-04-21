/*
 * 账户持仓页读模型工厂测试，覆盖典型输入场景与稳定性契约。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.CurvePoint;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.domain.account.model.TradeRecordItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStore;

import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public class AccountPositionUiModelFactoryTest {

    @Before
    public void setUp() {
        UnifiedRuntimeSnapshotStore.getInstance().clearAccountRuntime();
    }

    // 有持仓有挂单时，输出应稳定排序且签名稳定。
    @Test
    public void buildShouldBeStableWhenPositionsAndPendingOrdersExist() {
        AccountPositionUiModelFactory factory = new AccountPositionUiModelFactory();
        List<AccountMetric> metrics = Arrays.asList(
                new AccountMetric("B", "2"),
                new AccountMetric("A", "1")
        );
        List<PositionItem> positions = Arrays.asList(
                createPosition("ETHUSD", "Sell", 2L, 12L, 2.0),
                createPosition("BTCUSD", "Buy", 1L, 11L, 1.0)
        );
        List<PositionItem> pendingOrders = Arrays.asList(
                createPosition("XAUUSD", "Buy", 0L, 22L, 3.0),
                createPosition("EURUSD", "Sell", 0L, 21L, 4.0)
        );
        AccountStatsPreloadManager.Cache cache = createCache(metrics, positions, pendingOrders, 1710000000000L, "rev-a");

        AccountPositionUiModel first = factory.build(cache);
        AccountPositionUiModel second = factory.build(cache);

        assertTrue(first.getOverviewMetrics().size() >= 1);
        assertEquals(first.getOverviewMetrics().size(), second.getOverviewMetrics().size());
        assertEquals(first.getOverviewMetrics().get(0).getName(), second.getOverviewMetrics().get(0).getName());
        assertEquals("BTCUSD", first.getPositions().get(0).getCode());
        assertEquals("EURUSD", first.getPendingOrders().get(0).getCode());
        assertEquals("当前持仓 2 条", first.getPositionSummaryText());
        assertEquals("挂单 2 条", first.getPendingSummaryText());
        assertEquals(first.getSignature(), second.getSignature());
    }

    // 只有挂单时，持仓摘要应为 0 且挂单内容可读。
    @Test
    public void buildShouldHandleOnlyPendingOrders() {
        AccountPositionUiModelFactory factory = new AccountPositionUiModelFactory();
        AccountStatsPreloadManager.Cache cache = createCache(
                Collections.singletonList(new AccountMetric("净值", "1000")),
                Collections.emptyList(),
                Collections.singletonList(createPosition("XAUUSD", "Buy", 0L, 31L, 1.5)),
                1710000000000L,
                "rev-b"
        );

        AccountPositionUiModel model = factory.build(cache);
        assertEquals("当前持仓 0 条", model.getPositionSummaryText());
        assertEquals("挂单 1 条", model.getPendingSummaryText());
        assertEquals(0, model.getPositions().size());
        assertEquals(1, model.getPendingOrders().size());
    }

    // 概览指标应沿用统一帮助类的固定展示顺序，不能被页面工厂再次打乱。
    @Test
    public void buildShouldKeepOverviewMetricsInCanonicalDisplayOrder() {
        AccountPositionUiModelFactory factory = new AccountPositionUiModelFactory();
        List<AccountMetric> metrics = Arrays.asList(
                new AccountMetric("保证金", "$300.00"),
                new AccountMetric("总资产", "$1000.00"),
                new AccountMetric("净值", "$900.00")
        );
        List<TradeRecordItem> trades = Collections.emptyList();
        List<CurvePoint> curves = Collections.emptyList();
        AccountSnapshot snapshot = new AccountSnapshot(
                metrics,
                new ArrayList<CurvePoint>(),
                new ArrayList<AccountMetric>(),
                Collections.emptyList(),
                Collections.emptyList(),
                trades,
                new ArrayList<AccountMetric>()
        );
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                snapshot,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "https://example.test",
                1710000000000L,
                "",
                1710000000000L,
                "rev-order"
        );

        AccountPositionUiModel model = factory.build(cache);
        List<AccountMetric> expected = AccountOverviewMetricsHelper.buildOverviewMetrics(
                metrics,
                Collections.emptyList(),
                trades,
                curves,
                1710000000000L,
                TimeZone.getDefault()
        );

        assertEquals(expected.size(), model.getOverviewMetrics().size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).getName(), model.getOverviewMetrics().get(i).getName());
        }
    }

    // 账户页产品摘要必须直接复用统一运行态，不能再按“产品 + 方向”在页面侧重算一份。
    @Test
    public void buildShouldReuseUnifiedRuntimeProductSummariesForAccountAggregates() {
        AccountPositionUiModelFactory factory = new AccountPositionUiModelFactory();
        List<PositionItem> positions = Arrays.asList(
                createPosition("XAUUSD", "Buy", 1001L, 0L, 1.0, 2300d, 25d, 5d),
                createPosition("XAUUSD", "Buy", 1002L, 0L, 2.0, 2400d, 50d, -2d),
                createPosition("XAUUSD", "Sell", 1003L, 0L, 0.5, 2350d, -10d, 0d)
        );
        AccountStatsPreloadManager.Cache cache = createCache(
                Collections.singletonList(new AccountMetric("总资产", "$1000.00")),
                positions,
                Collections.singletonList(createPosition("XAUUSD", "Buy", 0L, 2001L, 0.0, 2360d, 0d, 0d)),
                1710000000000L,
                "rev-aggregate"
        );
        UnifiedRuntimeSnapshotStore.getInstance().applyAccountCache(cache);

        AccountPositionUiModel model = factory.build(cache);

        assertEquals(1, model.getPositionAggregates().size());
        Object aggregate = model.getPositionAggregates().get(0);
        assertEquals("XAUUSD", invokeStringGetter(aggregate, "getDisplayLabel"));
        assertEquals("XAU", invokeStringGetter(aggregate, "getCompactDisplayLabel"));
        assertEquals(3, invokeIntGetter(aggregate, "getPositionCount"));
        assertEquals(1, invokeIntGetter(aggregate, "getPendingCount"));
        assertEquals(3.50d, invokeDoubleGetter(aggregate, "getTotalLots"), 0.0001d);
        assertEquals(2.50d, invokeDoubleGetter(aggregate, "getSignedLots"), 0.0001d);
        assertEquals(68d, invokeDoubleGetter(aggregate, "getNetPnl"), 0.0001d);
        assertEquals("盈亏：+68.00 | 持仓：3.50手", invokeStringGetter(aggregate, "getSummaryText"));
    }

    // 空快照输入时，应输出空集合与占位更新时间并保持签名可用。
    @Test
    public void buildShouldHandleEmptySnapshot() {
        AccountPositionUiModelFactory factory = new AccountPositionUiModelFactory();
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                false,
                null,
                "",
                "",
                "",
                "",
                0L,
                "",
                0L,
                ""
        );

        AccountPositionUiModel model = factory.build(cache);
        assertEquals(0, model.getOverviewMetrics().size());
        assertEquals(0, model.getPositions().size());
        assertEquals(0, model.getPendingOrders().size());
        assertEquals("--", model.getUpdatedAtText());
        assertFalse(model.getSignature().isEmpty());
    }

    // 当缓存还没有业务更新时间时，读模型版本应回退到拉取时间，避免旧恢复结果覆盖新界面。
    @Test
    public void buildShouldUseFetchedAtAsSnapshotVersionWhenUpdatedAtMissing() {
        AccountPositionUiModelFactory factory = new AccountPositionUiModelFactory();
        AccountStatsPreloadManager.Cache cache = new AccountStatsPreloadManager.Cache(
                true,
                new AccountSnapshot(
                        Collections.singletonList(new AccountMetric("总资产", "$1000.00")),
                        new ArrayList<CurvePoint>(),
                        new ArrayList<AccountMetric>(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        new ArrayList<TradeRecordItem>(),
                        new ArrayList<AccountMetric>()
                ),
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "https://example.test",
                0L,
                "",
                1710001234567L,
                "rev-version"
        );

        AccountPositionUiModel model = factory.build(cache);

        assertEquals(1710001234567L, model.getSnapshotVersionMs());
    }

    // 账户持仓页读模型必须只受运行态驱动，历史修订号、成交列表和净值曲线变化都不应打脏当前页。
    @Test
    public void buildShouldIgnoreHistoryRevisionTradesAndCurvePoints() {
        AccountPositionUiModelFactory factory = new AccountPositionUiModelFactory();
        List<AccountMetric> metrics = Arrays.asList(
                new AccountMetric("总资产", "$1000.00"),
                new AccountMetric("净值", "$900.00")
        );
        List<PositionItem> positions = Collections.singletonList(createPosition("BTCUSD", "Buy", 1L, 11L, 1.0));
        List<PositionItem> pendingOrders = Collections.singletonList(createPosition("XAUUSD", "Sell", 0L, 21L, 2.0));

        AccountStatsPreloadManager.Cache firstCache = createCache(
                metrics,
                positions,
                pendingOrders,
                1710000000000L,
                "rev-a",
                Collections.singletonList(new TradeRecordItem(1L, "BTCUSD", "BTCUSD", "Buy", 1d, 101d, 100d, 10d, "")),
                Collections.singletonList(new CurvePoint(1L, 900d, 880d, 0.1d))
        );
        AccountStatsPreloadManager.Cache secondCache = createCache(
                metrics,
                positions,
                pendingOrders,
                1710000000000L,
                "rev-b",
                Collections.singletonList(new TradeRecordItem(2L, "ETHUSD", "ETHUSD", "Sell", 2d, 202d, 200d, -30d, "")),
                Collections.singletonList(new CurvePoint(2L, 1200d, 1100d, 0.2d))
        );

        AccountPositionUiModel first = factory.build(firstCache);
        AccountPositionUiModel second = factory.build(secondCache);

        assertEquals(first.getOverviewMetrics().size(), second.getOverviewMetrics().size());
        for (int i = 0; i < first.getOverviewMetrics().size(); i++) {
            assertEquals(first.getOverviewMetrics().get(i).getName(), second.getOverviewMetrics().get(i).getName());
            assertEquals(first.getOverviewMetrics().get(i).getValue(), second.getOverviewMetrics().get(i).getValue());
        }
        assertEquals(first.getSignature(), second.getSignature());
    }

    // 组装缓存测试数据。
    private static AccountStatsPreloadManager.Cache createCache(List<AccountMetric> metrics,
                                                                List<PositionItem> positions,
                                                                List<PositionItem> pendingOrders,
                                                                long updatedAt,
                                                                String historyRevision) {
        return createCache(metrics, positions, pendingOrders, updatedAt, historyRevision,
                new ArrayList<>(), new ArrayList<>());
    }

    private static AccountStatsPreloadManager.Cache createCache(List<AccountMetric> metrics,
                                                                List<PositionItem> positions,
                                                                List<PositionItem> pendingOrders,
                                                                long updatedAt,
                                                                String historyRevision,
                                                                List<TradeRecordItem> trades,
                                                                List<CurvePoint> curvePoints) {
        AccountSnapshot snapshot = new AccountSnapshot(
                metrics == null ? new ArrayList<>() : metrics,
                curvePoints == null ? new ArrayList<>() : curvePoints,
                new ArrayList<AccountMetric>(),
                positions == null ? new ArrayList<>() : positions,
                pendingOrders == null ? new ArrayList<>() : pendingOrders,
                trades == null ? new ArrayList<>() : trades,
                new ArrayList<AccountMetric>()
        );
        return new AccountStatsPreloadManager.Cache(
                true,
                snapshot,
                "7400048",
                "ICMarketsSC-MT5-6",
                "V2网关",
                "https://example.test",
                updatedAt,
                "",
                updatedAt,
                historyRevision
        );
    }

    // 组装最小持仓/挂单测试数据。
    private static PositionItem createPosition(String code,
                                               String side,
                                               long positionTicket,
                                               long orderId,
                                               double quantity) {
        return createPosition(code, side, positionTicket, orderId, quantity, 100d, 20d, 0d);
    }

    private static PositionItem createPosition(String code,
                                               String side,
                                               long positionTicket,
                                               long orderId,
                                               double quantity,
                                               double costPrice,
                                               double totalPnl,
                                               double storageFee) {
        return new PositionItem(
                code,
                code,
                side,
                positionTicket,
                orderId,
                1710000000000L,
                quantity,
                quantity,
                costPrice,
                101d,
                1000d,
                0.1d,
                10d,
                totalPnl,
                0.02d,
                0d,
                0,
                0d,
                0d,
                0d,
                storageFee
        );
    }

    private static String invokeStringGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException exception) {
            fail("缺少账户页产品摘要方法: " + methodName);
            return "";
        }
    }

    private static int invokeIntGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (ReflectiveOperationException exception) {
            fail("缺少账户页产品摘要方法: " + methodName);
            return 0;
        }
    }

    private static double invokeDoubleGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number ? ((Number) value).doubleValue() : 0d;
        } catch (ReflectiveOperationException exception) {
            fail("缺少账户页产品摘要方法: " + methodName);
            return 0d;
        }
    }
}
