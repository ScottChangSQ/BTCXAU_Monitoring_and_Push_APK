/*
 * 账户持仓页 adapter 源码约束测试，确保 Diff 签名覆盖真实展示字段。
 */
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountPositionAdapterSourceTest {

    @Test
    public void positionAdapterContentSignatureShouldCoverDisplayedDetailFields() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");

        assertTrue(source.contains("String productName = safe(item.getProductName());"));
        assertTrue(source.contains("String side = safe(item.getSide());"));
        assertTrue(source.contains("long openTime = item.getOpenTime();"));
        assertTrue(source.contains("long costPrice = Math.round(item.getCostPrice() * 100d);"));
        assertTrue(source.contains("long takeProfit = Math.round(item.getTakeProfit() * 100d);"));
        assertTrue(source.contains("long stopLoss = Math.round(item.getStopLoss() * 100d);"));
        assertTrue(source.contains("openTime + \"|\" + costPrice + \"|\" + takeProfit + \"|\" + stopLoss"));
        assertTrue(source.contains("\"|\" + productName + \"|\" + side"));
    }

    @Test
    public void positionAdapterShouldUseSamePnlCaliberForCollapsedAndExpandedContent() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");

        assertTrue(source.contains("double displayPnl = item.getTotalPnL() + item.getStorageFee();"));
        assertTrue(source.contains("String pnlText = formatSignedSummaryPnl(displayPnl);"));
        assertTrue(source.contains("String totalPnlText = IndicatorFormatterCenter.formatMoney(displayPnl, 2, false);"));
        assertTrue(source.contains("\"持仓盈亏 %s | 收益率 %s\""));
        assertTrue(source.contains("IndicatorPresentationPolicy.applyDirectionalSpanForExactToken("));
        assertTrue(source.contains("IndicatorId.ACCOUNT_POSITION_PNL"));
        assertTrue(source.contains("IndicatorId.ACCOUNT_POSITION_PNL_RATE"));
    }

    @Test
    public void positionAdapterCollapsedSummaryShouldNotIncludeOpenPrice() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");

        assertFalse(source.contains("String openPriceText ="));
        assertFalse(source.contains("开仓 %s"));
        assertTrue(source.contains("String raw = String.format(Locale.getDefault(), \"%s|%s|%s|%s\","));
        assertTrue(source.contains("resolveSummaryProductCode(item, runtimeSnapshot), sideText, qtyText, pnlText"));
        assertTrue(source.contains("summarySideCn(item.getSide())"));
        assertTrue(source.contains("String qtyText = IndicatorFormatterCenter.formatQuantity(displayQty, 2, \"手\");"));
        assertTrue(source.contains("private static String summarySideCn(String side)"));
        assertTrue(source.contains("private static String formatSignedSummaryPnl(double value)"));
    }

    @Test
    public void positionAdapterShouldForceSingleLineSummaryAtRuntime() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");

        assertTrue(source.contains("binding.tvSummary.setIncludeFontPadding(false);"));
        assertTrue(source.contains("binding.tvSummary.setSingleLine(true);"));
        assertTrue(source.contains("binding.tvSummary.setLines(1);"));
        assertTrue(source.contains("binding.tvSummary.setMinLines(1);"));
        assertTrue(source.contains("binding.tvSummary.setMaxLines(1);"));
        assertTrue(source.contains("binding.tvSummary.setHorizontallyScrolling(true);"));
        assertTrue(source.contains("binding.tvSummary.setEllipsize(TextUtils.TruncateAt.END);"));
    }

    @Test
    public void positionAdapterShouldReadUnifiedRuntimeOnlyForSingleProductRows() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");

        assertTrue(source.contains("private final UnifiedRuntimeSnapshotStore runtimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();"));
        assertTrue(source.contains("private final Map<String, Integer> productCounts = new HashMap<>();"));
        assertTrue(source.contains("productCounts.clear();"));
        assertTrue(source.contains("productCounts.putAll(buildProductCounts(nextItems));"));
        assertTrue(source.contains("public void setRuntimeIdentity(@Nullable String account, @Nullable String server) {"));
        assertTrue(source.contains("ProductRuntimeSnapshot runtimeSnapshot = runtimeSnapshotStore.selectProduct("));
        assertTrue(source.contains("runtimeAccount,"));
        assertTrue(source.contains("runtimeServer,"));
        assertTrue(source.contains("productCount == 1 && runtimeSnapshot.getPositionCount() == 1"));
        assertTrue(source.contains("resolveSummaryProductCode(@NonNull PositionItem item,"));
        assertTrue(source.contains("runtimeSnapshot.getSymbol()"));
    }

    @Test
    public void pendingOrderAdapterContentSignatureShouldCoverDisplayedPriceFields() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java");

        assertTrue(source.contains("String productName = safe(item.getProductName());"));
        assertTrue(source.contains("String side = safe(item.getSide());"));
        assertTrue(source.contains("double displayLots = Math.abs(item.getPendingLots()) > 1e-9"));
        assertTrue(source.contains(": Math.abs(item.getQuantity());"));
        assertTrue(source.contains("long pendingPrice = Math.round(item.getPendingPrice() * 100d);"));
        assertTrue(source.contains("long quantity = Math.round(Math.abs(item.getQuantity()) * 10_000d);"));
        assertTrue(source.contains("long takeProfit = Math.round(item.getTakeProfit() * 100d);"));
        assertTrue(source.contains("long stopLoss = Math.round(item.getStopLoss() * 100d);"));
        assertTrue(source.contains("lots + \"|\" + quantity + \"|\" + latest + \"|\" + pendingPrice + \"|\""));
        assertTrue(source.contains("+ takeProfit + \"|\" + stopLoss + \"|\" + item.getPendingCount()"));
        assertTrue(source.contains("\"|\" + productName + \"|\" + side"));
    }

    @Test
    public void pendingOrderAdapterShouldExposeModifyAndDeleteActions() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java");

        assertTrue(source.contains("void onModifyRequested(PositionItem item);"));
        assertTrue(source.contains("void onDeleteRequested(PositionItem item);"));
        assertTrue(source.contains("binding.btnPositionModifyAction.setVisibility(actionEnabled ? View.VISIBLE : View.GONE);"));
        assertTrue(source.contains("binding.btnPositionDeleteAction.setVisibility(actionEnabled ? View.VISIBLE : View.GONE);"));
        assertTrue(source.contains("listener.onModifyRequested(items.get(adapterPosition));"));
        assertTrue(source.contains("listener.onDeleteRequested(items.get(adapterPosition));"));
    }

    @Test
    public void pendingOrderCollapsedSummaryShouldUseIntegerPendingPrice() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java");

        assertTrue(source.contains("String pendingPriceText = formatCollapsedPendingPrice(pendingPrice);"));
        assertTrue(source.contains("private static String formatCollapsedPendingPrice(double price)"));
        assertTrue(source.contains("String.format(Locale.getDefault(), \"%,.0f\", roundedPrice)"));
        assertTrue(source.contains("IndicatorFormatterCenter.formatQuantity(displayLots, 2, \"手\")"));
        assertTrue(source.contains("? (pendingCount + \"单\")"));
        assertTrue(source.contains("\"%s|%s|%s|%s\""));
    }

    @Test
    public void pendingOrderAdapterShouldForceSingleLineSummaryAtRuntime() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java");

        assertTrue(source.contains("binding.tvSummary.setIncludeFontPadding(false);"));
        assertTrue(source.contains("binding.tvSummary.setSingleLine(true);"));
        assertTrue(source.contains("binding.tvSummary.setLines(1);"));
        assertTrue(source.contains("binding.tvSummary.setMinLines(1);"));
        assertTrue(source.contains("binding.tvSummary.setMaxLines(1);"));
        assertTrue(source.contains("binding.tvSummary.setHorizontallyScrolling(true);"));
        assertTrue(source.contains("binding.tvSummary.setEllipsize(TextUtils.TruncateAt.END);"));
    }

    @Test
    public void pendingOrderAdapterShouldReadUnifiedRuntimeOnlyForSingleProductRows() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java");

        assertTrue(source.contains("private final UnifiedRuntimeSnapshotStore runtimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();"));
        assertTrue(source.contains("private final Map<String, Integer> productCounts = new HashMap<>();"));
        assertTrue(source.contains("productCounts.clear();"));
        assertTrue(source.contains("productCounts.putAll(buildProductCounts(nextItems));"));
        assertTrue(source.contains("public void setRuntimeIdentity(@Nullable String account, @Nullable String server) {"));
        assertTrue(source.contains("ProductRuntimeSnapshot runtimeSnapshot = runtimeSnapshotStore.selectProduct("));
        assertTrue(source.contains("runtimeAccount,"));
        assertTrue(source.contains("runtimeServer,"));
        assertTrue(source.contains("productCount == 1 && runtimeSnapshot.getPendingCount() == 1"));
        assertTrue(source.contains("runtimeSnapshot.getDisplayLabel()"));
    }

    @Test
    public void accountPositionAdaptersShouldUsePaletteDrivenRowSurfacesInsteadOfLegacyDarkDrawables() throws Exception {
        String positionAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java");
        String pendingAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java");
        String aggregateAdapter = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAggregateAdapter.java");

        assertTrue(positionAdapter.contains("UiPaletteManager.Palette palette = UiPaletteManager.resolve(binding.getRoot().getContext());"));
        assertTrue(positionAdapter.contains("binding.layoutHeader.setBackground(UiPaletteManager.createListRowBackground("));
        assertTrue(positionAdapter.contains("UiPaletteManager.styleActionButton("));
        assertFalse(positionAdapter.contains("UiPaletteManager.styleSquareTextAction("));
        assertFalse(positionAdapter.contains("binding.layoutHeader.setBackgroundResource(expanded"));
        assertFalse(positionAdapter.contains("R.drawable.bg_position_row_collapsed"));
        assertFalse(positionAdapter.contains("R.drawable.bg_position_row_expanded"));

        assertTrue(pendingAdapter.contains("UiPaletteManager.Palette palette = UiPaletteManager.resolve(binding.getRoot().getContext());"));
        assertTrue(pendingAdapter.contains("binding.layoutHeader.setBackground(UiPaletteManager.createListRowBackground("));
        assertTrue(pendingAdapter.contains("UiPaletteManager.styleActionButton("));
        assertFalse(pendingAdapter.contains("UiPaletteManager.styleSquareTextAction("));
        assertFalse(pendingAdapter.contains("binding.layoutHeader.setBackgroundResource(expanded"));
        assertFalse(pendingAdapter.contains("R.drawable.bg_position_row_collapsed"));
        assertFalse(pendingAdapter.contains("R.drawable.bg_position_row_expanded"));

        assertTrue(aggregateAdapter.contains("UiPaletteManager.Palette palette = UiPaletteManager.resolve(binding.getRoot().getContext());"));
        assertTrue(aggregateAdapter.contains("binding.layoutHeader.setBackground(UiPaletteManager.createListRowBackground("));
        assertFalse(aggregateAdapter.contains("binding.layoutHeader.setBackgroundResource(R.drawable.bg_position_row_collapsed);"));
    }

    @Test
    public void aggregateAdapterShouldRenderProductSideLotsAndUsdPnlInSingleLine() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/account/adapter/PositionAggregateAdapter.java");

        assertTrue(source.contains("double displayLots = resolveDisplayLots(item);"));
        assertTrue(source.contains("String raw = String.format(Locale.getDefault(),"));
        assertTrue(source.contains("\"%s|%s|%s|%s\""));
        assertTrue(source.contains("IndicatorFormatterCenter.formatQuantity"));
        assertTrue(source.contains("formatSignedPnlValue(item.getNetPnl())"));
        assertTrue(source.contains("resolveDirectionText(displayLots)"));
        assertTrue(source.contains("return isZero(item.getSignedLots()) ? 0d : item.getSignedLots();"));
        assertTrue(source.contains("return \"买\";"));
        assertTrue(source.contains("return \"卖\";"));
        assertFalse(source.contains("方向："));
        assertFalse(source.contains("盈亏："));
        assertFalse(source.contains("持仓："));
        assertFalse(source.contains("item.getSummaryText()"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path root = Paths.get("").toAbsolutePath();
        Path path = root.resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
