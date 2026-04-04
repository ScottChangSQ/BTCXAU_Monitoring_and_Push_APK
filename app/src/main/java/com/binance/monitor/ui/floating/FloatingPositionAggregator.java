/*
 * 悬浮窗持仓聚合器，把多笔持仓按产品汇总成一份盈亏列表。
 * 当前先提供纯聚合逻辑，后续由数据库快照结果直接喂给这里。
 */
package com.binance.monitor.ui.floating;

import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.ui.account.model.PositionItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class FloatingPositionAggregator {

    // 把当前持仓按产品代码聚合，并汇总总盈亏。
    public static List<FloatingPositionPnlItem> aggregate(List<PositionItem> positions) {
        return aggregate(positions, null, true, true);
    }

    // 把当前持仓按产品代码聚合，并补充可展示的实时行情价格。
    public static List<FloatingPositionPnlItem> aggregate(List<PositionItem> positions,
                                                          Map<String, Double> latestPrices,
                                                          boolean showBtc,
                                                          boolean showXau) {
        Map<String, FloatingPositionPnlItem> grouped = new TreeMap<>(String::compareToIgnoreCase);
        if (positions == null || positions.isEmpty()) {
            return new ArrayList<>();
        }
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            String code = safeCode(item);
            if (!shouldIncludeCode(code, showBtc, showXau)) {
                continue;
            }
            String label = safeLabel(item, code);
            double marketPrice = resolveMarketPrice(code, latestPrices);
            boolean hasMarketPrice = !Double.isNaN(marketPrice);
            double total = resolveDisplayTotalPnl(item);
            FloatingPositionPnlItem current = grouped.get(code);
            if (current == null) {
                grouped.put(code, new FloatingPositionPnlItem(code, label, total, marketPrice, hasMarketPrice));
                continue;
            }
            grouped.put(code, new FloatingPositionPnlItem(
                    code,
                    current.getLabel(),
                    current.getTotalPnl() + total,
                    current.hasMarketPrice() ? current.getMarketPrice() : marketPrice,
                    current.hasMarketPrice() || hasMarketPrice
            ));
        }
        return new ArrayList<>(grouped.values());
    }

    // 生成悬浮窗产品卡片，确保已启用的产品即使当前无持仓也会展示行情。
    public static List<FloatingSymbolCardData> buildSymbolCards(List<PositionItem> positions,
                                                                Map<String, KlineData> latestKlines,
                                                                Map<String, Double> latestPrices,
                                                                boolean showBtc,
                                                                boolean showXau) {
        Map<String, FloatingPositionPnlItem> grouped = new LinkedHashMap<>();
        if (positions != null) {
            for (FloatingPositionPnlItem item : aggregate(positions, latestPrices, showBtc, showXau)) {
                if (item != null) {
                    grouped.put(item.getCode(), item);
                }
            }
        }

        List<String> visibleSymbols = filterMarketSymbols(
                java.util.Arrays.asList(AppConstants.SYMBOL_BTC, AppConstants.SYMBOL_XAU),
                showBtc,
                showXau
        );
        List<FloatingSymbolCardData> result = new ArrayList<>();
        for (String symbol : visibleSymbols) {
            FloatingPositionPnlItem pnlItem = findPnlItemForSymbol(symbol, grouped);
            KlineData kline = latestKlines == null ? null : latestKlines.get(symbol);
            double totalPnl = pnlItem == null ? 0d : pnlItem.getTotalPnl();
            String label = resolveCardLabel(symbol, pnlItem);
            double realtimePrice = resolveMarketPrice(symbol, latestPrices);
            boolean hasPrice = !Double.isNaN(realtimePrice) || kline != null;
            double latestPrice = !Double.isNaN(realtimePrice)
                    ? realtimePrice
                    : (kline == null ? 0d : kline.getClosePrice());
            double volume = kline == null ? 0d : kline.getVolume();
            double amount = kline == null ? 0d : kline.getQuoteAssetVolume();
            long updatedAt = kline == null ? 0L : Math.max(kline.getCloseTime(), kline.getOpenTime());
            result.add(new FloatingSymbolCardData(
                    symbol,
                    label,
                    totalPnl,
                    latestPrice,
                    hasPrice,
                    volume,
                    amount,
                    updatedAt
            ));
        }
        return result;
    }

    // 计算悬浮窗当前可见持仓的总盈亏。
    public static double sumTotalPnl(List<FloatingPositionPnlItem> items) {
        double total = 0d;
        if (items == null || items.isEmpty()) {
            return total;
        }
        for (FloatingPositionPnlItem item : items) {
            if (item != null) {
                total += item.getTotalPnl();
            }
        }
        return total;
    }

    // 按设置中的 BTC/XAU 开关筛选行情展示代码，其余产品默认保留。
    public static List<String> filterMarketSymbols(List<String> codes, boolean showBtc, boolean showXau) {
        LinkedHashSet<String> filtered = new LinkedHashSet<>();
        if (codes == null || codes.isEmpty()) {
            return new ArrayList<>();
        }
        for (String code : codes) {
            String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (!showBtc && isBtcSymbol(normalized)) {
                continue;
            }
            if (!showXau && isXauSymbol(normalized)) {
                continue;
            }
            filtered.add(normalized);
        }
        return new ArrayList<>(filtered);
    }

    // 统一取产品代码，优先 code，缺失时回退 productName。
    private static String safeCode(PositionItem item) {
        String code = item.getCode() == null ? "" : item.getCode().trim();
        if (!code.isEmpty()) {
            return code.toUpperCase(Locale.ROOT);
        }
        String productName = item.getProductName() == null ? "" : item.getProductName().trim();
        return productName.toUpperCase(Locale.ROOT);
    }

    // 统一生成悬浮窗展示标签。
    private static String safeLabel(PositionItem item, String fallback) {
        String productName = item.getProductName() == null ? "" : item.getProductName().trim();
        if (!productName.isEmpty()) {
            return productName;
        }
        return fallback;
    }

    private static boolean isBtcSymbol(String code) {
        if (code == null) {
            return false;
        }
        return AppConstants.SYMBOL_BTC.equalsIgnoreCase(code)
                || code.startsWith("BTC");
    }

    private static boolean isXauSymbol(String code) {
        if (code == null) {
            return false;
        }
        return AppConstants.SYMBOL_XAU.equalsIgnoreCase(code)
                || code.startsWith("XAU");
    }

    private static boolean shouldIncludeCode(String code, boolean showBtc, boolean showXau) {
        if (!showBtc && isBtcSymbol(code)) {
            return false;
        }
        if (!showXau && isXauSymbol(code)) {
            return false;
        }
        return true;
    }

    private static double resolveMarketPrice(String code, Map<String, Double> latestPrices) {
        if (latestPrices == null || latestPrices.isEmpty()) {
            return Double.NaN;
        }
        Double direct = latestPrices.get(code);
        if (direct != null) {
            return direct;
        }
        if (isBtcSymbol(code)) {
            Double btc = latestPrices.get(AppConstants.SYMBOL_BTC);
            return btc == null ? Double.NaN : btc;
        }
        if (isXauSymbol(code)) {
            Double xau = latestPrices.get(AppConstants.SYMBOL_XAU);
            return xau == null ? Double.NaN : xau;
        }
        return Double.NaN;
    }

    // 悬浮窗盈亏直接复用当前行情页里的持仓盈亏数字，不再额外叠加库存费。
    private static double resolveDisplayTotalPnl(PositionItem item) {
        if (item == null) {
            return 0d;
        }
        return item.getTotalPnL();
    }

    // 兼容 MT5 持仓代码和 Binance 行情代码不一致的场景，保证悬浮窗仍能显示对应盈亏。
    private static FloatingPositionPnlItem findPnlItemForSymbol(String symbol,
                                                                Map<String, FloatingPositionPnlItem> grouped) {
        if (grouped == null || grouped.isEmpty()) {
            return null;
        }
        FloatingPositionPnlItem direct = grouped.get(symbol);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, FloatingPositionPnlItem> entry : grouped.entrySet()) {
            if (entry == null || entry.getValue() == null) {
                continue;
            }
            String code = entry.getKey();
            if ((isBtcSymbol(symbol) && isBtcSymbol(code))
                    || (isXauSymbol(symbol) && isXauSymbol(code))) {
                return entry.getValue();
            }
        }
        return null;
    }

    // 统一悬浮窗产品标题，优先使用已有持仓标签，否则回退资产简称。
    private static String resolveCardLabel(String symbol, FloatingPositionPnlItem item) {
        return AppConstants.symbolToAsset(symbol);
    }
}
