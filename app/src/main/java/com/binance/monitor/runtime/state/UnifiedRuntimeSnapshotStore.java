/*
 * 统一运行态内存真值中心，负责收口账户级与产品级派生快照。
 */
package com.binance.monitor.runtime.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.state.model.AccountRuntimeSnapshot;
import com.binance.monitor.runtime.state.model.ChartProductRuntimeModel;
import com.binance.monitor.runtime.state.model.FloatingCardRuntimeModel;
import com.binance.monitor.runtime.state.model.ProductRuntimeSnapshot;
import com.binance.monitor.util.ProductSymbolMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UnifiedRuntimeSnapshotStore {
    private static final ProductRuntimeSnapshot EMPTY_PRODUCT =
            new ProductRuntimeSnapshot("", 0L, 0, 0, 0d, 0d, 0d, "", "", "", "");
    private static final UnifiedRuntimeSnapshotStore INSTANCE = new UnifiedRuntimeSnapshotStore();

    private final Object lock = new Object();
    private AccountRuntimeSnapshot accountRuntimeSnapshot;
    private String lastAccountSignature = "";
    private long nextAccountRevision;
    private final Map<String, ProductRuntimeSnapshot> productSnapshots = new LinkedHashMap<>();
    private final Map<String, String> productSignatures = new LinkedHashMap<>();
    private final Map<String, Long> productRevisions = new LinkedHashMap<>();

    private UnifiedRuntimeSnapshotStore() {
    }

    @NonNull
    public static UnifiedRuntimeSnapshotStore getInstance() {
        return INSTANCE;
    }

    public void applyAccountCache(@Nullable AccountStatsPreloadManager.Cache cache) {
        synchronized (lock) {
            if (cache == null) {
                clearAccountRuntimeLocked();
                return;
            }
            String accountSignature = buildAccountSignature(cache);
            if (!accountSignature.equals(lastAccountSignature)) {
                nextAccountRevision++;
                lastAccountSignature = accountSignature;
            }
            String accountKey = buildAccountKey(cache.getAccount(), cache.getServer());
            accountRuntimeSnapshot = new AccountRuntimeSnapshot(
                    nextAccountRevision,
                    cache.isConnected(),
                    accountKey,
                    cache.getAccount(),
                    cache.getServer(),
                    cache.getSource(),
                    cache.getGateway(),
                    cache.getHistoryRevision(),
                    cache.getUpdatedAt(),
                    cache.getSnapshot()
            );
            rebuildProductsLocked(cache.getSnapshot());
        }
    }

    public void clearAccountRuntime() {
        synchronized (lock) {
            clearAccountRuntimeLocked();
        }
    }

    @Nullable
    public AccountRuntimeSnapshot getAccountRuntimeSnapshot() {
        synchronized (lock) {
            return accountRuntimeSnapshot;
        }
    }

    @NonNull
    public ProductRuntimeSnapshot selectProduct(@Nullable String symbol) {
        synchronized (lock) {
            ProductRuntimeSnapshot snapshot = productSnapshots.get(normalizeSymbol(symbol));
            return snapshot == null ? EMPTY_PRODUCT : snapshot;
        }
    }

    @NonNull
    public ChartProductRuntimeModel selectChartProductRuntime(@Nullable String symbol) {
        return new ChartProductRuntimeModel(selectProductForUiSurface(symbol));
    }

    @NonNull
    public FloatingCardRuntimeModel selectFloatingCard(@Nullable String symbol) {
        return new FloatingCardRuntimeModel(selectProductForUiSurface(symbol));
    }

    @NonNull
    private ProductRuntimeSnapshot selectProductForUiSurface(@Nullable String symbol) {
        synchronized (lock) {
            ProductRuntimeSnapshot direct = productSnapshots.get(normalizeSymbol(symbol));
            if (direct != null) {
                return direct;
            }
            for (ProductRuntimeSnapshot snapshot : productSnapshots.values()) {
                if (snapshot != null && ProductSymbolMapper.isSameProduct(symbol, snapshot.getSymbol())) {
                    return snapshot;
                }
            }
            return EMPTY_PRODUCT;
        }
    }

    private void clearAccountRuntimeLocked() {
        accountRuntimeSnapshot = null;
        lastAccountSignature = "";
        nextAccountRevision = 0L;
        productSnapshots.clear();
        productSignatures.clear();
        productRevisions.clear();
    }

    private void rebuildProductsLocked(@Nullable AccountSnapshot snapshot) {
        Map<String, ProductAggregate> aggregates = new LinkedHashMap<>();
        if (snapshot != null) {
            appendPositions(aggregates, snapshot.getPositions(), false);
            appendPositions(aggregates, snapshot.getPendingOrders(), true);
        }
        Map<String, ProductRuntimeSnapshot> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, ProductAggregate> entry : aggregates.entrySet()) {
            String symbol = entry.getKey();
            ProductAggregate aggregate = entry.getValue();
            String signature = aggregate.buildSignature();
            long nextRevision = productRevisions.containsKey(symbol) && signature.equals(productSignatures.get(symbol))
                    ? productRevisions.get(symbol)
                    : productRevisions.getOrDefault(symbol, 0L) + 1L;
            productRevisions.put(symbol, nextRevision);
            productSignatures.put(symbol, signature);
            rebuilt.put(symbol, aggregate.toSnapshot(symbol, nextRevision));
        }
        List<String> removedSymbols = new ArrayList<>(productSnapshots.keySet());
        removedSymbols.removeAll(rebuilt.keySet());
        for (String removedSymbol : removedSymbols) {
            productSignatures.remove(removedSymbol);
            productRevisions.remove(removedSymbol);
        }
        productSnapshots.clear();
        productSnapshots.putAll(rebuilt);
    }

    private void appendPositions(@NonNull Map<String, ProductAggregate> aggregates,
                                 @Nullable List<PositionItem> positions,
                                 boolean pending) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            String symbol = normalizeSymbol(resolveSymbol(item));
            if (symbol.isEmpty()) {
                continue;
            }
            ProductAggregate aggregate = aggregates.get(symbol);
            if (aggregate == null) {
                aggregate = new ProductAggregate();
                aggregates.put(symbol, aggregate);
            }
            aggregate.add(item, pending);
        }
    }

    @NonNull
    private static String buildAccountSignature(@NonNull AccountStatsPreloadManager.Cache cache) {
        return safe(cache.getAccount()) + '|'
                + safe(cache.getServer()) + '|'
                + safe(cache.getHistoryRevision()) + '|'
                + cache.isConnected() + '|'
                + buildSnapshotSignature(cache.getSnapshot());
    }

    @NonNull
    private static String buildSnapshotSignature(@Nullable AccountSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendPositionSignature(builder, snapshot.getPositions());
        builder.append('#');
        appendPositionSignature(builder, snapshot.getPendingOrders());
        return builder.toString();
    }

    private static void appendPositionSignature(@NonNull StringBuilder builder,
                                                @Nullable List<PositionItem> positions) {
        if (positions == null || positions.isEmpty()) {
            builder.append("0");
            return;
        }
        builder.append(positions.size()).append(':');
        for (PositionItem item : positions) {
            if (item == null) {
                continue;
            }
            builder.append(safe(resolveSymbol(item))).append('|')
                    .append(safe(item.getSide())).append('|')
                    .append(item.getPositionTicket()).append('|')
                    .append(item.getOrderId()).append('|')
                    .append(item.getQuantity()).append('|')
                    .append(item.getPendingPrice()).append('|')
                    .append(item.getTotalPnL()).append(';');
        }
    }

    @NonNull
    private static String buildAccountKey(@Nullable String account, @Nullable String server) {
        if (safe(account).isEmpty() && safe(server).isEmpty()) {
            return "";
        }
        return safe(account) + '@' + safe(server);
    }

    @NonNull
    private static String resolveSymbol(@NonNull PositionItem item) {
        if (item.getCode() != null && !item.getCode().trim().isEmpty()) {
            return item.getCode().trim();
        }
        return safe(item.getProductName());
    }

    @NonNull
    private static String normalizeSymbol(@Nullable String symbol) {
        return safe(symbol).trim().toUpperCase(Locale.US);
    }

    @NonNull
    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static final class ProductAggregate {
        private int positionCount;
        private int pendingCount;
        private double totalLots;
        private double signedLots;
        private double netPnl;
        private String displayLabel = "";
        private final List<String> parts = new ArrayList<>();

        void add(@NonNull PositionItem item, boolean pending) {
            double lots = Math.abs(item.getQuantity()) > 1e-9 ? Math.abs(item.getQuantity()) : Math.abs(item.getPendingLots());
            captureDisplayLabel(item);
            if (pending) {
                pendingCount++;
            } else {
                positionCount++;
                totalLots += lots;
                signedLots += resolveSignedLots(item, lots);
                netPnl += item.getTotalPnL() + item.getStorageFee();
            }
            parts.add(safe(item.getSide()) + '|'
                    + item.getPositionTicket() + '|'
                    + item.getOrderId() + '|'
                    + item.getQuantity() + '|'
                    + item.getPendingLots() + '|'
                    + item.getPendingPrice() + '|'
                    + item.getTotalPnL() + '|'
                    + safe(item.getProductName()));
        }

        @NonNull
        String buildSignature() {
            Collections.sort(parts);
            return positionCount + "|" + pendingCount + "|" + totalLots + "|" + signedLots + "|" + netPnl + "|" + displayLabel + "|" + parts;
        }

        @NonNull
        ProductRuntimeSnapshot toSnapshot(@NonNull String symbol, long productRevision) {
            double displayLots = resolveDisplayLots(totalLots, signedLots);
            String resolvedDisplayLabel = displayLabel.isEmpty() ? symbol : displayLabel;
            String positionSummary = positionCount <= 0
                    ? "盈亏：-- | 持仓：--"
                    : "盈亏：" + formatSigned(netPnl) + " | 持仓：" + formatSummaryLots(displayLots) + "手";
            String pendingSummary = pendingCount <= 0
                    ? "挂单：--"
                    : "挂单：" + pendingCount + "笔";
            return new ProductRuntimeSnapshot(
                    symbol,
                    productRevision,
                    positionCount,
                    pendingCount,
                    totalLots,
                    displayLots,
                    netPnl,
                    resolvedDisplayLabel,
                    resolveCompactDisplayLabel(resolvedDisplayLabel, symbol),
                    positionSummary,
                    pendingSummary
            );
        }

        private void captureDisplayLabel(@NonNull PositionItem item) {
            String productName = safe(item.getProductName()).trim();
            if (!productName.isEmpty()) {
                displayLabel = productName;
            }
        }

        @NonNull
        private static String formatSigned(double value) {
            return String.format(Locale.US, "%+.2f", value);
        }

        @NonNull
        private static String formatLots(double value) {
            return String.format(Locale.US, "%.2f", value);
        }

        @NonNull
        private static String formatSummaryLots(double value) {
            if (Math.abs(value) <= 1e-9) {
                return "0.00";
            }
            return value < 0d
                    ? String.format(Locale.US, "-%.2f", Math.abs(value))
                    : String.format(Locale.US, "%.2f", Math.abs(value));
        }

        private static double resolveSignedLots(@NonNull PositionItem item, double lots) {
            String side = safe(item.getSide()).trim().toUpperCase(Locale.US);
            return side.startsWith("SELL") ? -lots : lots;
        }

        private static double resolveDisplayLots(double totalLots, double signedLots) {
            if (totalLots <= 1e-9) {
                return 0d;
            }
            return signedLots < 0d ? -totalLots : totalLots;
        }

        @NonNull
        private static String resolveCompactDisplayLabel(@NonNull String displayLabel,
                                                         @NonNull String symbol) {
            String tradeSymbol = ProductSymbolMapper.toTradeSymbol(displayLabel);
            if (tradeSymbol.isEmpty() || !ProductSymbolMapper.isSupportedProduct(tradeSymbol)) {
                tradeSymbol = ProductSymbolMapper.toTradeSymbol(symbol);
            }
            if (ProductSymbolMapper.TRADE_SYMBOL_BTC.equals(tradeSymbol)) {
                return "BTC";
            }
            if (ProductSymbolMapper.TRADE_SYMBOL_XAU.equals(tradeSymbol)) {
                return "XAU";
            }
            return displayLabel.isEmpty() ? symbol : displayLabel;
        }
    }
}
