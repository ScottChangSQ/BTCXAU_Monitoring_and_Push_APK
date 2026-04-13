/*
 * 账户持仓页增强源码约束测试，锁定产品聚合、持仓摘要和展开态展示契约。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountPositionEnhancementSourceTest {

    @Test
    public void activityShouldBindPositionAggregateSection() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java");
        String layoutSource = readUtf8("src/main/res/layout/activity_account_position.xml");

        assertTrue(activitySource.contains("private PositionAggregateAdapter positionAggregateAdapter;"));
        assertTrue(activitySource.contains("binding.recyclerPositionAggregates.setLayoutManager(new LinearLayoutManager(this));"));
        assertTrue(activitySource.contains("binding.recyclerPositionAggregates.setAdapter(positionAggregateAdapter);"));
        assertTrue(activitySource.contains("positionAggregateAdapter.submitList(model.getPositionAggregates());"));

        assertTrue(layoutSource.contains("android:id=\"@+id/recyclerPositionAggregates\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/tvPositionAggregateTitle\""));
    }

    @Test
    public void positionAdapterShouldShowOpenPriceOpenTimeAndExpandedBackgroundState() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");
        String layoutSource = readUtf8("src/main/res/layout/item_position.xml");

        assertTrue(source.contains("开仓 %s"));
        assertTrue(source.contains("开仓时间 %s"));
        assertTrue(source.contains("formatOpenTime(item.getOpenTime())"));
        assertTrue(source.contains("R.drawable.bg_position_row_expanded"));
        assertTrue(source.contains("R.drawable.bg_position_row_collapsed"));

        assertTrue(layoutSource.contains("android:id=\"@+id/btnPositionCloseAction\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/btnPositionModifyAction\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/btnPositionDeleteAction\""));
    }

    @Test
    public void positionAggregateAdapterShouldShowGroupedSideLotsCostAndPnl() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAggregateAdapter.java");

        assertTrue(source.contains("%s | %s | %s | 成本 %s | %s"));
    }

    @Test
    public void activityShouldRoutePositionAndPendingActionsIntoChartTradeFlow() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java");

        assertTrue(source.contains("positionAdapter.setActionListener(new PositionAdapterV2.ActionListener()"));
        assertTrue(source.contains("pendingOrderAdapter.setActionListener(new PendingOrderAdapter.ActionListener()"));
        assertTrue(source.contains("openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_CLOSE_POSITION);"));
        assertTrue(source.contains("openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_MODIFY_POSITION);"));
        assertTrue(source.contains("openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_MODIFY_PENDING);"));
        assertTrue(source.contains("openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_CANCEL_PENDING);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
