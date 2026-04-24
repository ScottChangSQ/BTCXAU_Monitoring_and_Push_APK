/*
 * 批量交易入口源码测试，锁定挂单与持仓 TP/SL 修改必须先选产品，再在产品下选方向。
 */
package com.binance.monitor.ui.trade;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TradeBatchActionDialogCoordinatorSourceTest {

    @Test
    public void batchModifyActionsShouldRequireProductThenDirectionPath() throws Exception {
        String source = readUtf8("src/main/java/com/binance/monitor/ui/trade/TradeBatchActionDialogCoordinator.java");

        assertTrue(source.contains("showPendingModifyModePicker(context);"));
        assertTrue(source.contains("showPositionModifyModePicker(context);"));
        assertTrue(source.contains("private void showPendingModifyModePicker(@NonNull BatchActionContext context) {"));
        assertTrue(source.contains("private void showPositionModifyModePicker(@NonNull BatchActionContext context) {"));
        assertTrue(source.contains("showProductThenDirectionScopePicker("));
        assertTrue(source.contains("context.getPendingOrders(),"));
        assertTrue(source.contains("context.getPositions(),"));
        assertTrue(source.contains("\"选择要批量修改的挂单产品\""));
        assertTrue(source.contains("\"选择要批量修改的持仓产品\""));
        assertTrue(source.contains("\"选择要批量修改的挂单方向\""));
        assertTrue(source.contains("\"选择要批量修改的持仓方向\""));
        assertTrue(source.contains("\"买入挂单\""));
        assertTrue(source.contains("\"卖出挂单\""));
        assertTrue(source.contains("\"买入持仓\""));
        assertTrue(source.contains("\"卖出持仓\""));
        assertTrue(source.contains("private void showProductThenDirectionScopePicker(@NonNull List<PositionItem> source,"));
        assertTrue(source.contains("private void showDirectionScopePicker(@NonNull List<PositionItem> source,"));
        assertTrue(source.contains("directionTitle + \" · \" + productName"));
        assertTrue(source.contains("productName + \" · \" + buyLabel"));
        assertTrue(source.contains("productName + \" · \" + sellLabel"));
        assertTrue(source.contains("private String stripScopeCountSuffix(@NonNull String raw) {"));
        assertTrue(source.contains("private List<PositionItem> filterItemsByDirection(@Nullable List<PositionItem> source, boolean buySide) {"));
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
