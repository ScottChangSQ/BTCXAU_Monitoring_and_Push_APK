/*
 * 批量交易结果格式化器，负责把整体状态和单项结果转成用户可读文案。
 * 供图表页结果弹窗和后续批量交易入口复用。
 */
package com.binance.monitor.ui.trade;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.data.model.v2.trade.BatchTradeItemResult;
import com.binance.monitor.data.model.v2.trade.BatchTradeReceipt;
import com.binance.monitor.data.model.v2.trade.ExecutionError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BatchTradeResultFormatter {

    private BatchTradeResultFormatter() {
    }

    // 生成批量交易总览文案。
    @NonNull
    public static String buildSummary(@Nullable BatchTradeReceipt receipt) {
        if (receipt == null) {
            return "批量结果未确认";
        }
        int successCount = 0;
        int totalCount = receipt.getItems().size();
        for (BatchTradeItemResult item : receipt.getItems()) {
            if (item != null && item.isAccepted()) {
                successCount++;
            }
        }
        if (receipt.isAccepted()) {
            return "批量交易成功（" + successCount + "/" + totalCount + "）";
        }
        if (receipt.isPartial()) {
            return "批量交易部分成功（" + successCount + "/" + totalCount + "）";
        }
        return "批量交易失败（" + successCount + "/" + totalCount + "）";
    }

    // 生成每一项的清单文案。
    @NonNull
    public static List<String> buildItemLines(@Nullable BatchTradeReceipt receipt) {
        if (receipt == null || receipt.getItems().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        for (BatchTradeItemResult item : receipt.getItems()) {
            if (item == null) {
                continue;
            }
            String prefix = item.isAccepted() ? "成功" : "失败";
            String label = safeLabel(item);
            String suffix = item.isAccepted() ? "" : buildErrorSuffix(item.getError());
            lines.add(prefix + "：" + label + suffix);
        }
        return lines;
    }

    // 统一获取显示文案，缺失时回退到动作和 itemId。
    @NonNull
    private static String safeLabel(@NonNull BatchTradeItemResult item) {
        if (item.getDisplayLabel() != null && !item.getDisplayLabel().trim().isEmpty()) {
            return item.getDisplayLabel().trim();
        }
        String action = item.getAction() == null ? "" : item.getAction().trim();
        String itemId = item.getItemId() == null ? "" : item.getItemId().trim();
        if (action.isEmpty()) {
            return itemId;
        }
        if (itemId.isEmpty()) {
            return action;
        }
        return action + " " + itemId;
    }

    // 统一拼接失败原因。
    @NonNull
    private static String buildErrorSuffix(@Nullable ExecutionError error) {
        if (error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()) {
            return "";
        }
        return " - " + error.getMessage().trim();
    }
}
