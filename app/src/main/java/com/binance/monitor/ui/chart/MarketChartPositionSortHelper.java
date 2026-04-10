/*
 * 行情持仓明细排序辅助，只负责把持仓列表按既定规则输出成稳定顺序。
 * 与 MarketChartActivity、PositionAdapterV2 和 PositionItem 协同工作。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.PositionItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class MarketChartPositionSortHelper {

    enum SortOption {
        OPEN_TIME_DESC("开仓时间（倒序）"),
        PRODUCT_ASC("产品"),
        PNL_ASC("盈亏（正序）"),
        PNL_DESC("盈亏（倒序）");

        private final String label;

        SortOption(String label) {
            this.label = label;
        }

        String getLabel() {
            return label;
        }
    }

    private MarketChartPositionSortHelper() {
    }

    // 返回排序入口需要展示的全部文案，供 Spinner 和右侧标签统一复用。
    static String[] buildOptionLabels() {
        SortOption[] values = SortOption.values();
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = values[i].getLabel();
        }
        return labels;
    }

    // 根据持久化值恢复排序选项，未知值统一回退到默认开仓时间倒序。
    static SortOption fromStoredValue(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) {
            return SortOption.OPEN_TIME_DESC;
        }
        String normalized = value.trim();
        for (SortOption option : SortOption.values()) {
            if (option.name().equalsIgnoreCase(normalized)) {
                return option;
            }
        }
        return SortOption.OPEN_TIME_DESC;
    }

    // 根据界面展示文案解析排序方式，避免 Activity 自己维护第二份映射。
    static SortOption fromLabel(@Nullable String label) {
        if (label == null || label.trim().isEmpty()) {
            return SortOption.OPEN_TIME_DESC;
        }
        String normalized = label.trim();
        for (SortOption option : SortOption.values()) {
            if (option.getLabel().equals(normalized)) {
                return option;
            }
        }
        return SortOption.OPEN_TIME_DESC;
    }

    // 按选中的排序方式返回一份新的稳定列表，避免直接改动上游快照数据。
    static List<PositionItem> sortPositions(@Nullable List<PositionItem> source,
                                            @Nullable SortOption option) {
        List<PositionItem> result = source == null ? new ArrayList<>() : new ArrayList<>(source);
        result.sort(resolveComparator(option));
        return result;
    }

    // 为每种排序方式提供明确比较器，并统一追加稳定兜底键。
    private static Comparator<PositionItem> resolveComparator(@Nullable SortOption option) {
        SortOption safeOption = option == null ? SortOption.OPEN_TIME_DESC : option;
        if (safeOption == SortOption.PRODUCT_ASC) {
            return (left, right) -> {
                int productCompare = normalizeProduct(left).compareTo(normalizeProduct(right));
                if (productCompare != 0) {
                    return productCompare;
                }
                int codeCompare = normalizeCode(left).compareTo(normalizeCode(right));
                if (codeCompare != 0) {
                    return codeCompare;
                }
                int openTimeCompare = Long.compare(resolveOpenTime(right), resolveOpenTime(left));
                if (openTimeCompare != 0) {
                    return openTimeCompare;
                }
                return Long.compare(resolveStableIdentity(left), resolveStableIdentity(right));
            };
        }
        if (safeOption == SortOption.PNL_ASC) {
            return (left, right) -> {
                int pnlCompare = Double.compare(resolveDisplayedPnl(left), resolveDisplayedPnl(right));
                if (pnlCompare != 0) {
                    return pnlCompare;
                }
                int openTimeCompare = Long.compare(resolveOpenTime(right), resolveOpenTime(left));
                if (openTimeCompare != 0) {
                    return openTimeCompare;
                }
                int productCompare = normalizeProduct(left).compareTo(normalizeProduct(right));
                if (productCompare != 0) {
                    return productCompare;
                }
                return Long.compare(resolveStableIdentity(left), resolveStableIdentity(right));
            };
        }
        if (safeOption == SortOption.PNL_DESC) {
            return (left, right) -> {
                int pnlCompare = Double.compare(resolveDisplayedPnl(right), resolveDisplayedPnl(left));
                if (pnlCompare != 0) {
                    return pnlCompare;
                }
                int openTimeCompare = Long.compare(resolveOpenTime(right), resolveOpenTime(left));
                if (openTimeCompare != 0) {
                    return openTimeCompare;
                }
                int productCompare = normalizeProduct(left).compareTo(normalizeProduct(right));
                if (productCompare != 0) {
                    return productCompare;
                }
                return Long.compare(resolveStableIdentity(left), resolveStableIdentity(right));
            };
        }
        return (left, right) -> {
            int openTimeCompare = Long.compare(resolveOpenTime(right), resolveOpenTime(left));
            if (openTimeCompare != 0) {
                return openTimeCompare;
            }
            int productCompare = normalizeProduct(left).compareTo(normalizeProduct(right));
            if (productCompare != 0) {
                return productCompare;
            }
            int codeCompare = normalizeCode(left).compareTo(normalizeCode(right));
            if (codeCompare != 0) {
                return codeCompare;
            }
            return Long.compare(resolveStableIdentity(left), resolveStableIdentity(right));
        };
    }

    // 持仓明细展示的盈亏口径是“持仓盈亏 + 库存费”，排序也保持同一套口径。
    private static double resolveDisplayedPnl(@Nullable PositionItem item) {
        if (item == null) {
            return 0d;
        }
        return item.getTotalPnL() + item.getStorageFee();
    }

    // 开仓时间无效时回退到最小值，保证无时间数据的条目排在最后。
    private static long resolveOpenTime(@Nullable PositionItem item) {
        if (item == null) {
            return Long.MIN_VALUE;
        }
        return item.getOpenTime();
    }

    // 用持仓票据或挂单票据做最终稳定键，避免同值条目在刷新中来回换位。
    private static long resolveStableIdentity(@Nullable PositionItem item) {
        if (item == null) {
            return Long.MIN_VALUE;
        }
        if (item.getPositionTicket() > 0L) {
            return item.getPositionTicket();
        }
        if (item.getOrderId() > 0L) {
            return item.getOrderId();
        }
        return resolveOpenTime(item);
    }

    // 产品排序优先使用产品名，避免代码和中文名称混排时顺序不稳定。
    private static String normalizeProduct(@Nullable PositionItem item) {
        if (item == null || item.getProductName() == null) {
            return "";
        }
        return item.getProductName().trim().toUpperCase(Locale.ROOT);
    }

    // 产品名相同或为空时，继续按代码补一个稳定次序。
    private static String normalizeCode(@Nullable PositionItem item) {
        if (item == null || item.getCode() == null) {
            return "";
        }
        return item.getCode().trim().toUpperCase(Locale.ROOT);
    }
}
