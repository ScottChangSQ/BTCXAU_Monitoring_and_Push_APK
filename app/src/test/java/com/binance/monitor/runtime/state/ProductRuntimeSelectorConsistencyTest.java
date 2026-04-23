package com.binance.monitor.runtime.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.state.model.ChartProductRuntimeModel;
import com.binance.monitor.runtime.state.model.FloatingCardRuntimeModel;
import com.binance.monitor.runtime.state.model.ProductRuntimeSnapshot;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class ProductRuntimeSelectorConsistencyTest {

    private UnifiedRuntimeSnapshotStore store;

    @Before
    public void setUp() {
        store = UnifiedRuntimeSnapshotStore.getInstance();
        store.clearAccountRuntime();
    }

    @Test
    public void chartFloatingAndAccountSelectorsShouldShareSameRuntimeFields() {
        store.applyAccountCache(new AccountStatsPreloadManager.Cache(
                true,
                new AccountSnapshot(
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.singletonList(new PositionItem(
                                "比特币",
                                "BTCUSD",
                                "Buy",
                                1L,
                                10L,
                                0L,
                                0.10d,
                                0.10d,
                                100d,
                                110d,
                                0d,
                                0.1d,
                                0d,
                                12.3d,
                                0.1d,
                                0d,
                                0,
                                0d,
                                0d,
                                0d,
                                0d
                        )),
                        Collections.singletonList(new PositionItem(
                                "比特币",
                                "BTCUSD",
                                "Buy",
                                0L,
                                20L,
                                0L,
                                0d,
                                0d,
                                100d,
                                100d,
                                0d,
                                0d,
                                0d,
                                0d,
                                0d,
                                0.20d,
                                1,
                                100d,
                                0d,
                                0d,
                                0d
                        )),
                        Collections.emptyList(),
                        Collections.emptyList()
                ),
                "7400048",
                "ICMarketsSC-MT5-6",
                "stream",
                "gateway",
                1000L,
                "",
                1001L,
                "history-1"
        ));

        List<ProductRuntimeSnapshot> accountProducts = store.selectAllProducts("7400048", "ICMarketsSC-MT5-6");
        assertEquals(1, accountProducts.size());

        ProductRuntimeSnapshot accountRuntime = accountProducts.get(0);
        ChartProductRuntimeModel chartRuntime = store.selectChartProductRuntime("7400048", "ICMarketsSC-MT5-6", "BTC");
        FloatingCardRuntimeModel floatingRuntime = store.selectFloatingCard("7400048", "ICMarketsSC-MT5-6", "BTCUSDT");

        assertEquals(accountRuntime.getSymbol(), chartRuntime.getProductRuntimeSnapshot().getSymbol());
        assertEquals(accountRuntime.getSymbol(), floatingRuntime.getProductRuntimeSnapshot().getSymbol());
        assertEquals(accountRuntime.getDisplayLabel(), invokeString(chartRuntime, "getDisplayLabel"));
        assertEquals(accountRuntime.getDisplayLabel(), invokeString(floatingRuntime, "getDisplayLabel"));
        assertEquals(accountRuntime.getCompactDisplayLabel(), invokeString(chartRuntime, "getCompactDisplayLabel"));
        assertEquals(accountRuntime.getCompactDisplayLabel(), invokeString(floatingRuntime, "getCompactDisplayLabel"));
        assertEquals(accountRuntime.getPositionCount(), invokeInt(chartRuntime, "getPositionCount"));
        assertEquals(accountRuntime.getPositionCount(), invokeInt(floatingRuntime, "getPositionCount"));
        assertEquals(accountRuntime.getPendingCount(), invokeInt(chartRuntime, "getPendingCount"));
        assertEquals(accountRuntime.getPendingCount(), invokeInt(floatingRuntime, "getPendingCount"));
        assertEquals(accountRuntime.getSignedLots(), invokeDouble(chartRuntime, "getSignedLots"), 0.0001d);
        assertEquals(accountRuntime.getSignedLots(), invokeDouble(floatingRuntime, "getSignedLots"), 0.0001d);
        assertEquals(accountRuntime.getNetPnl(), invokeDouble(chartRuntime, "getNetPnl"), 0.0001d);
        assertEquals(accountRuntime.getNetPnl(), invokeDouble(floatingRuntime, "getNetPnl"), 0.0001d);
        assertEquals(invokeString(accountRuntime, "getCrossPageSummaryText"),
                invokeString(chartRuntime, "getCrossPageSummaryText"));
        assertEquals(invokeString(accountRuntime, "getCrossPageSummaryText"),
                invokeString(floatingRuntime, "getCrossPageSummaryText"));
    }

    private static String invokeString(Object target, String methodName) {
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value == null ? "" : value.toString();
        } catch (ReflectiveOperationException exception) {
            fail("缺少统一产品运行态方法: " + target.getClass().getSimpleName() + "." + methodName);
            return "";
        }
    }

    private static int invokeInt(Object target, String methodName) {
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (ReflectiveOperationException exception) {
            fail("缺少统一产品运行态方法: " + target.getClass().getSimpleName() + "." + methodName);
            return 0;
        }
    }

    private static double invokeDouble(Object target, String methodName) {
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Number ? ((Number) value).doubleValue() : 0d;
        } catch (ReflectiveOperationException exception) {
            fail("缺少统一产品运行态方法: " + target.getClass().getSimpleName() + "." + methodName);
            return 0d;
        }
    }
}
