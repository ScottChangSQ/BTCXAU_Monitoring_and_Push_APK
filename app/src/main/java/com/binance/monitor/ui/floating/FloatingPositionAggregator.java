/*
 * 悬浮窗持仓聚合器，把多笔持仓按产品汇总成一份盈亏列表。
 * 当前先提供纯聚合逻辑，后续由数据库快照结果直接喂给这里。
 */
package com.binance.monitor.ui.floating;

import com.binance.monitor.data.model.KlineData;
import com.binance.monitor.constants.AppConstants;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.state.model.FloatingCardRuntimeModel;
import com.binance.monitor.runtime.state.model.ProductRuntimeSnapshot;
import com.binance.monitor.util.ProductSymbolMapper;

import androidx.annotation.Nullable;

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
        Map<String, GroupedPositionState> grouped = new TreeMap<>(String::compareToIgnoreCase);
        if (positions == null || positions.isEmpty()) {
            return new ArrayList<>();
        }
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            String code = safeCode(item);
            if (code.isEmpty()) {
                continue;
            }
            if (!shouldIncludeCode(code, showBtc, showXau)) {
                continue;
            }
            String label = safeLabel(item, code);
            double marketPrice = resolveMarketPrice(code, latestPrices);
            boolean hasMarketPrice = !Double.isNaN(marketPrice);
            double total = resolveDisplayTotalPnl(item);
            double quantity = Math.abs(item.getQuantity());
            double signedQuantity = resolveSignedQuantity(item);
            GroupedPositionState current = grouped.get(code);
            if (current == null) {
                GroupedPositionState initial = new GroupedPositionState(label);
                initial.totalPnl = total;
                initial.totalLotsAbs = quantity;
                initial.netLots = signedQuantity;
                initial.marketPrice = marketPrice;
                initial.hasMarketPrice = hasMarketPrice;
                grouped.put(code, initial);
                continue;
            }
            current.totalPnl += total;
            current.totalLotsAbs += quantity;
            current.netLots += signedQuantity;
            if (!current.hasMarketPrice() && hasMarketPrice) {
                current.marketPrice = marketPrice;
                current.hasMarketPrice = true;
            }
        }
        List<FloatingPositionPnlItem> result = new ArrayList<>();
        for (Map.Entry<String, GroupedPositionState> entry : grouped.entrySet()) {
            String code = entry.getKey();
            GroupedPositionState state = entry.getValue();
            result.add(new FloatingPositionPnlItem(
                    code,
                    state.getLabel(),
                    state.getTotalPnl(),
                    resolveDisplayLots(state.getTotalLotsAbs(), state.getNetLots()),
                    state.getMarketPrice(),
                    state.hasMarketPrice()
            ));
        }
        return result;
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
            double totalLots = pnlItem == null ? 0d : pnlItem.getTotalLots();
            String label = resolveCardLabel(symbol, pnlItem);
            double realtimePrice = resolveMarketPrice(symbol, latestPrices);
            boolean hasPrice = !Double.isNaN(realtimePrice) || kline != null;
            double latestPrice = !Double.isNaN(realtimePrice)
                    ? realtimePrice
                    : (kline == null ? 0d : kline.getClosePrice());
            double volume = kline == null ? 0d : kline.getVolume();
            double amount = kline == null ? 0d : kline.getQuoteAssetVolume();
            long updatedAt = kline == null ? 0L : Math.max(kline.getCloseTime(), kline.getOpenTime());
            boolean hasPosition = pnlItem != null;
            result.add(new FloatingSymbolCardData(
                    symbol,
                    label,
                    totalPnl,
                    totalLots,
                    latestPrice,
                    hasPrice,
                    volume,
                    amount,
                    updatedAt,
                    hasPosition
            ));
        }
        return result;
    }

    // 统一运行态存在时，直接按产品快照构造悬浮窗卡片，避免悬浮窗再次自行聚合持仓。
    public static List<FloatingSymbolCardData> buildSymbolCardsFromRuntime(List<String> visibleSymbols,
                                                                           List<FloatingCardRuntimeModel> runtimeCards,
                                                                           Map<String, KlineData> latestKlines,
                                                                           Map<String, Double> latestPrices) {
        List<FloatingSymbolCardData> result = new ArrayList<>();
        if (visibleSymbols == null || visibleSymbols.isEmpty()) {
            return result;
        }
        for (String symbol : visibleSymbols) {
            FloatingCardRuntimeModel runtimeCard = findRuntimeCardForSymbol(symbol, runtimeCards);
            double totalPnl = runtimeCard == null ? 0d : runtimeCard.getNetPnl();
            double totalLots = runtimeCard == null ? 0d : runtimeCard.getSignedLots();
            boolean hasPosition = runtimeCard != null && runtimeCard.getPositionCount() > 0;
            KlineData kline = latestKlines == null ? null : latestKlines.get(symbol);
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
                    resolveRuntimeCardLabel(symbol, runtimeCard),
                    totalPnl,
                    totalLots,
                    latestPrice,
                    hasPrice,
                    volume,
                    amount,
                    updatedAt,
                    hasPosition
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

    // 统一取产品代码，只接受 canonical code，缺失时直接判为无效条目。
    private static String safeCode(PositionItem item) {
        String code = item.getCode() == null ? "" : item.getCode().trim();
        return code.toUpperCase(Locale.ROOT);
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
        return AppConstants.SYMBOL_BTC.equals(ProductSymbolMapper.toMarketSymbol(code));
    }

    private static boolean isXauSymbol(String code) {
        return AppConstants.SYMBOL_XAU.equals(ProductSymbolMapper.toMarketSymbol(code));
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
        String marketCode = ProductSymbolMapper.toMarketSymbol(code);
        Double direct = latestPrices.get(code);
        if (direct != null) {
            return direct;
        }
        if (!marketCode.isEmpty()) {
            Double canonical = latestPrices.get(marketCode);
            if (canonical != null) {
                return canonical;
            }
        }
        return Double.NaN;
    }

    // 悬浮窗盈亏与行情持仓页统一，直接使用持仓盈亏加库存费。
    private static double resolveDisplayTotalPnl(PositionItem item) {
        if (item == null) {
            return 0d;
        }
        return item.getTotalPnL() + item.getStorageFee();
    }

    // 把持仓方向统一收口成带正负的手数，买入为正，卖出为负。
    private static double resolveSignedQuantity(PositionItem item) {
        if (item == null) {
            return 0d;
        }
        double quantity = Math.abs(item.getQuantity());
        String side = item.getSide() == null ? "" : item.getSide().trim().toUpperCase(Locale.ROOT);
        return side.startsWith("SELL") ? -quantity : quantity;
    }

    // 展示手数大小按绝对值累计，正负按净方向决定。
    private static double resolveDisplayLots(double totalLotsAbs, double netLots) {
        if (totalLotsAbs <= 1e-9) {
            return 0d;
        }
        return netLots < 0d ? -totalLotsAbs : totalLotsAbs;
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
            if (ProductSymbolMapper.isSameProduct(symbol, code)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // 统一悬浮窗产品标题，BTC/XAU 在悬浮窗里收口成更短的资产简称。
    private static String resolveCardLabel(String symbol, FloatingPositionPnlItem item) {
        String rawLabel = item != null && item.getLabel() != null && !item.getLabel().trim().isEmpty()
                ? item.getLabel().trim()
                : ProductSymbolMapper.toTradeSymbol(symbol);
        return resolveCardLabel(symbol, rawLabel);
    }

    private static String resolveCardLabel(String symbol, String rawLabel) {
        String displayLabel = rawLabel == null || rawLabel.trim().isEmpty()
                ? ProductSymbolMapper.toTradeSymbol(symbol)
                : rawLabel.trim();
        String tradeSymbol = ProductSymbolMapper.toTradeSymbol(displayLabel);
        if (ProductSymbolMapper.TRADE_SYMBOL_BTC.equals(tradeSymbol)) {
            return "BTC";
        }
        if (ProductSymbolMapper.TRADE_SYMBOL_XAU.equals(tradeSymbol)) {
            return "XAU";
        }
        return displayLabel;
    }

    private static String resolveRuntimeCardLabel(String symbol,
                                                  @Nullable FloatingCardRuntimeModel runtimeCard) {
        if (runtimeCard == null) {
            return resolveCardLabel(symbol, (String) null);
        }
        if (runtimeCard.getCompactDisplayLabel() != null
                && !runtimeCard.getCompactDisplayLabel().trim().isEmpty()) {
            return runtimeCard.getCompactDisplayLabel().trim();
        }
        if (runtimeCard.getDisplayLabel() != null
                && !runtimeCard.getDisplayLabel().trim().isEmpty()) {
            return runtimeCard.getDisplayLabel().trim();
        }
        return resolveCardLabel(symbol, (String) null);
    }

    private static FloatingCardRuntimeModel findRuntimeCardForSymbol(String symbol,
                                                                     List<FloatingCardRuntimeModel> runtimeCards) {
        if (runtimeCards == null || runtimeCards.isEmpty()) {
            return null;
        }
        for (FloatingCardRuntimeModel runtimeCard : runtimeCards) {
            if (runtimeCard == null || runtimeCard.getProductRuntimeSnapshot() == null) {
                continue;
            }
            if (ProductSymbolMapper.isSameProduct(symbol, runtimeCard.getProductRuntimeSnapshot().getSymbol())) {
                return runtimeCard;
            }
        }
        return null;
    }

    // 聚合同一产品下的悬浮窗持仓状态。
    private static final class GroupedPositionState {
        private final String label;
        private double totalPnl;
        private double totalLotsAbs;
        private double netLots;
        private double marketPrice;
        private boolean hasMarketPrice;

        private GroupedPositionState(String label) {
            this.label = label == null ? "" : label;
        }

        private String getLabel() {
            return label;
        }

        private double getTotalPnl() {
            return totalPnl;
        }

        private double getTotalLotsAbs() {
            return totalLotsAbs;
        }

        private double getNetLots() {
            return netLots;
        }

        private double getMarketPrice() {
            return marketPrice;
        }

        private boolean hasMarketPrice() {
            return hasMarketPrice;
        }
    }
}
