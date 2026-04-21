/*
 * 账户持仓页增强源码约束测试，锁定产品聚合、持仓摘要和展开态展示契约。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountPositionEnhancementSourceTest {

    @Test
    public void activityShouldBindPositionAggregateSection() throws Exception {
        String activitySource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");
        String layoutSource = readUtf8("src/main/res/layout/activity_account_position.xml");

        assertTrue(activitySource.contains("private PositionAggregateAdapter positionAggregateAdapter;"));
        assertTrue(activitySource.contains("binding.recyclerPositionAggregates.setLayoutManager(new LinearLayoutManager(host.requireActivity()));"));
        assertTrue(activitySource.contains("binding.recyclerPositionAggregates.setAdapter(positionAggregateAdapter);"));
        assertTrue(activitySource.contains("positionAggregateAdapter.submitList(aggregates);")
                || activitySource.contains("positionAggregateAdapter.submitList(model.getPositionAggregates());"));

        assertTrue(layoutSource.contains("android:id=\"@+id/recyclerPositionAggregates\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/tvPositionAggregateTitle\""));
    }

    @Test
    public void positionAdapterShouldHideOpenPriceInCollapsedSummaryButKeepExpandedDetails() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");
        String layoutSource = readUtf8("src/main/res/layout/item_position.xml");

        assertFalse(source.contains("String openPriceText ="));
        assertFalse(source.contains("开仓 %s"));
        assertTrue(source.contains("String raw = String.format(Locale.getDefault(), \"%s | %s | %s | %s\","));
        assertTrue(source.contains("开仓时间 %s"));
        assertTrue(source.contains("formatOpenTime(item.getOpenTime())"));
        assertTrue(source.contains("applyRowPalette(palette, expanded);"));
        assertTrue(source.contains("UiPaletteManager.createListRowBackground("));

        assertTrue(layoutSource.contains("android:id=\"@+id/btnPositionCloseAction\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/btnPositionModifyAction\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/btnPositionDeleteAction\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutSummaryColumn\""));
        assertTrue(layoutSource.contains("android:id=\"@+id/layoutActionButtons\""));
        assertTrue(layoutSource.contains("@dimen/list_item_padding_x"));
        assertTrue(layoutSource.contains("@dimen/list_item_padding_y"));
        assertTrue(layoutSource.contains("@dimen/field_padding_x_compact"));
        assertTrue(layoutSource.contains("@dimen/inline_gap"));
        assertTrue(layoutSource.contains("android:gravity=\"center_vertical\""));
        assertTrue(layoutSource.contains("android:maxLines=\"1\""));
        assertTrue(layoutSource.contains("android:ellipsize=\"end\""));
        assertFalse(layoutSource.contains("@dimen/position_row_header_padding_horizontal"));
        assertFalse(layoutSource.contains("@dimen/position_row_header_padding_vertical"));
        assertFalse(layoutSource.contains("@dimen/subject_padding_x_compact"));
        assertFalse(layoutSource.contains("@dimen/control_group_gap"));
        assertFalse(layoutSource.contains("@dimen/page_horizontal_padding"));
    }

    @Test
    public void accountProductSummaryShouldReadUnifiedRuntimeInsteadOfRebuildingSideAndCost() throws Exception {
        String factorySource = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionUiModelFactory.java");
        String adapterSource = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAggregateAdapter.java");

        assertTrue(factorySource.contains("private final UnifiedRuntimeSnapshotStore runtimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();"));
        assertTrue(factorySource.contains("runtimeSnapshotStore.selectAllProducts()"));
        assertFalse(factorySource.contains("AggregateAccumulator"));
        assertFalse(factorySource.contains("averageCostPrice"));
        assertFalse(factorySource.contains("productName + \"|\" + side.toUpperCase(Locale.ROOT)"));

        assertTrue(adapterSource.contains("String lotsText ="));
        assertTrue(adapterSource.contains("String pnlText ="));
        assertTrue(adapterSource.contains("String raw = String.format(Locale.getDefault(),"));
        assertTrue(adapterSource.contains("\"%s | %s | %s | %s\""));
        assertTrue(adapterSource.contains("resolveDirectionText(item.getSignedLots())"));
        assertTrue(adapterSource.contains("买入"));
        assertTrue(adapterSource.contains("卖出"));
        assertFalse(adapterSource.contains("item.getSummaryText()"));
        assertFalse(adapterSource.contains("方向"));
        assertFalse(adapterSource.contains("盈亏："));
        assertFalse(adapterSource.contains("持仓："));
        assertFalse(adapterSource.contains("%s | %s | %s | 成本 %s | %s"));
        assertFalse(adapterSource.contains("sideCn(item.getSide())"));
    }

    @Test
    public void activityShouldRoutePositionAndPendingActionsIntoChartTradeFlow() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java");

        assertTrue(source.contains("positionAdapter.setActionListener(new PositionAdapterV2.ActionListener()"));
        assertTrue(source.contains("pendingOrderAdapter.setActionListener(new PendingOrderAdapter.ActionListener()"));
        assertTrue(source.contains("host.openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_CLOSE_POSITION);"));
        assertTrue(source.contains("host.openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_MODIFY_POSITION);"));
        assertTrue(source.contains("host.openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_MODIFY_PENDING);"));
        assertTrue(source.contains("host.openChartTradeAction(item, MarketChartActivity.EXTRA_TRADE_ACTION_CANCEL_PENDING);"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
