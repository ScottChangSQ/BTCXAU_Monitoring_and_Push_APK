/*
 * 账户持仓页读模型工厂，只基于 AccountStatsPreloadManager.Cache 做稳定转换。
 * 该工厂负责稳定排序、摘要文本与签名生成，不引入第二数据源。
 */
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.AccountSnapshot;
import com.binance.monitor.domain.account.model.PositionItem;
import com.binance.monitor.runtime.account.AccountStatsPreloadManager;
import com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStore;
import com.binance.monitor.runtime.state.model.ProductRuntimeSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class AccountPositionUiModelFactory {
    private final UnifiedRuntimeSnapshotStore runtimeSnapshotStore = UnifiedRuntimeSnapshotStore.getInstance();

    // 将预加载缓存转换为账户持仓页只读模型。
    @NonNull
    public AccountPositionUiModel build(@Nullable AccountStatsPreloadManager.Cache cache) {
        AccountSnapshot snapshot = cache == null ? null : cache.getSnapshot();
        long updatedAt = cache == null ? 0L : cache.getUpdatedAt();
        List<AccountMetric> overviewMetrics = buildOverviewMetrics(snapshot, updatedAt);
        List<PositionItem> positions = sortPositionItems(snapshot == null ? null : snapshot.getPositions());
        List<PositionAggregateItem> positionAggregates = buildPositionAggregates(cache);
        List<PositionItem> pendingOrders = sortPositionItems(snapshot == null ? null : snapshot.getPendingOrders());

        String connectionStatusText = resolveConnectionStatusText(cache);
        String positionSummary = buildSummaryText("当前持仓", positions.size());
        String pendingSummary = buildSummaryText("挂单", pendingOrders.size());
        long fetchedAt = cache == null ? 0L : cache.getFetchedAt();
        String updatedAtText = formatUpdatedAt(updatedAt);
        long snapshotVersionMs = resolveSnapshotVersionMs(updatedAt, fetchedAt);
        String signature = buildSignature(cache,
                overviewMetrics,
                positionAggregates,
                positions,
                pendingOrders,
                connectionStatusText,
                updatedAtText);
        return new AccountPositionUiModel(
                overviewMetrics,
                cache == null ? "" : safeText(cache.getAccount()),
                cache == null ? "" : safeText(cache.getServer()),
                connectionStatusText,
                positionSummary,
                pendingSummary,
                positionAggregates,
                positions,
                pendingOrders,
                updatedAtText,
                signature,
                snapshotVersionMs
        );
    }

    // 账户概览沿用统一帮助类，在后台补齐固定顺序和累计指标后再给 UI。
    @NonNull
    private List<AccountMetric> buildOverviewMetrics(@Nullable AccountSnapshot snapshot, long updatedAt) {
        if (snapshot == null || snapshot.getOverviewMetrics() == null || snapshot.getOverviewMetrics().isEmpty()) {
            return Collections.emptyList();
        }
        List<AccountMetric> overview = AccountOverviewMetricsHelper.buildOverviewMetrics(
                snapshot.getOverviewMetrics(),
                snapshot.getPositions(),
                Collections.emptyList(),
                Collections.emptyList(),
                updatedAt,
                TimeZone.getDefault()
        );
        return overview.isEmpty() ? Collections.emptyList() : overview;
    }

    // 账户页产品摘要直接复用统一运行态，避免页面侧再重算第二份产品聚合。
    @NonNull
    private List<PositionAggregateItem> buildPositionAggregates(@Nullable AccountStatsPreloadManager.Cache cache) {
        List<ProductRuntimeSnapshot> productRuntimes = runtimeSnapshotStore.selectAllProducts(
                cache == null ? null : cache.getAccount(),
                cache == null ? null : cache.getServer()
        );
        if (productRuntimes.isEmpty()) {
            return Collections.emptyList();
        }
        List<PositionAggregateItem> result = new ArrayList<>();
        for (ProductRuntimeSnapshot snapshot : productRuntimes) {
            if (snapshot == null) {
                continue;
            }
            if (snapshot.getPositionCount() <= 0 && snapshot.getPendingCount() <= 0) {
                continue;
            }
            result.add(new PositionAggregateItem(
                    snapshot.getDisplayLabel(),
                    snapshot.getCompactDisplayLabel(),
                    snapshot.getPositionCount(),
                    snapshot.getPendingCount(),
                    snapshot.getTotalLots(),
                    snapshot.getSignedLots(),
                    snapshot.getNetPnl(),
                    snapshot.getCrossPageSummaryText()
            ));
        }
        return result;
    }

    // 稳定排序持仓与挂单明细，保证展示与签名可重复。
    @NonNull
    private List<PositionItem> sortPositionItems(@Nullable List<PositionItem> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<PositionItem> sorted = new ArrayList<>(source);
        sorted.sort(Comparator
                .comparing((PositionItem item) -> safeText(item == null ? null : item.getCode()))
                .thenComparing(item -> safeText(item == null ? null : item.getSide()))
                .thenComparingLong(item -> item == null ? 0L : item.getPositionTicket())
                .thenComparingLong(item -> item == null ? 0L : item.getOrderId())
                .thenComparingLong(item -> item == null ? 0L : item.getOpenTime())
                .thenComparingDouble(item -> item == null ? 0d : item.getQuantity())
                .thenComparingDouble(item -> item == null ? 0d : item.getLatestPrice()));
        return sorted;
    }

    // 按统一格式生成摘要文本。
    @NonNull
    private String buildSummaryText(@NonNull String title, int count) {
        return title + " " + count + " 条";
    }

    // 将更新时间统一为固定格式，空时间返回占位。
    @NonNull
    private String formatUpdatedAt(long updatedAt) {
        if (updatedAt <= 0L) {
            return "--";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        return "更新时间 " + format.format(new Date(updatedAt));
    }

    // 顶部状态按钮文案统一由缓存推导，避免页面层再次拼接。
    @NonNull
    private String resolveConnectionStatusText(@Nullable AccountStatsPreloadManager.Cache cache) {
        boolean connected = cache != null && cache.isConnected();
        String account = safeText(cache == null ? null : cache.getAccount());
        if (!connected) {
            return account.isEmpty() ? "未连接账户 | 未登录" : "已登录账户 | 待同步";
        }
        String accountSummary = account.isEmpty() ? "已连接账户" : account;
        return accountSummary + " | 实时同步中";
    }

    // 用更新时间优先、拉取时间兜底的方式生成单调版本，避免旧缓存异步回盖新界面。
    private long resolveSnapshotVersionMs(long updatedAt, long fetchedAt) {
        return Math.max(updatedAt, fetchedAt);
    }

    // 组合缓存核心字段与已排序内容，生成稳定签名。
    private String buildSignature(@Nullable AccountStatsPreloadManager.Cache cache,
                                  @NonNull List<AccountMetric> overviewMetrics,
                                  @NonNull List<PositionAggregateItem> positionAggregates,
                                  @NonNull List<PositionItem> positions,
                                  @NonNull List<PositionItem> pendingOrders,
                                  @NonNull String connectionStatusText,
                                  @NonNull String updatedAtText) {
        StringBuilder builder = new StringBuilder();
        builder.append("connected=").append(cache != null && cache.isConnected()).append('\n');
        builder.append("account=").append(safeText(cache == null ? null : cache.getAccount())).append('\n');
        builder.append("server=").append(safeText(cache == null ? null : cache.getServer())).append('\n');
        builder.append("updatedAt=").append(cache == null ? 0L : cache.getUpdatedAt()).append('\n');
        builder.append("fetchedAt=").append(cache == null ? 0L : cache.getFetchedAt()).append('\n');
        builder.append("overview=").append(serializeMetrics(overviewMetrics)).append('\n');
        builder.append("aggregates=").append(serializePositionAggregates(positionAggregates)).append('\n');
        builder.append("positions=").append(serializePositions(positions)).append('\n');
        builder.append("pending=").append(serializePositions(pendingOrders)).append('\n');
        builder.append("connectionStatusText=").append(connectionStatusText).append('\n');
        builder.append("updatedAtText=").append(updatedAtText);
        return sha256(builder.toString());
    }

    // 序列化指标列表用于签名比较。
    @NonNull
    private String serializeMetrics(@NonNull List<AccountMetric> metrics) {
        StringBuilder builder = new StringBuilder();
        for (AccountMetric metric : metrics) {
            builder.append(safeText(metric == null ? null : metric.getName()))
                    .append('=')
                    .append(safeText(metric == null ? null : metric.getValue()))
                    .append(';');
        }
        return builder.toString();
    }

    // 序列化产品聚合列表用于签名比较。
    @NonNull
    private String serializePositionAggregates(@NonNull List<PositionAggregateItem> items) {
        StringBuilder builder = new StringBuilder();
        for (PositionAggregateItem item : items) {
            builder.append(safeText(item == null ? null : item.getDisplayLabel())).append('|')
                    .append(safeText(item == null ? null : item.getCompactDisplayLabel())).append('|')
                    .append(item == null ? 0 : item.getPositionCount()).append('|')
                    .append(item == null ? 0 : item.getPendingCount()).append('|')
                    .append(item == null ? 0d : item.getTotalLots()).append('|')
                    .append(item == null ? 0d : item.getSignedLots()).append('|')
                    .append(item == null ? 0d : item.getNetPnl()).append('|')
                    .append(safeText(item == null ? null : item.getSummaryText()))
                    .append(';');
        }
        return builder.toString();
    }

    // 序列化持仓/挂单列表用于签名比较。
    @NonNull
    private String serializePositions(@NonNull List<PositionItem> positions) {
        StringBuilder builder = new StringBuilder();
        for (PositionItem item : positions) {
            if (item == null) {
                builder.append("null;");
                continue;
            }
            builder.append(safeText(item.getCode())).append('|')
                    .append(safeText(item.getSide())).append('|')
                    .append(item.getPositionTicket()).append('|')
                    .append(item.getOrderId()).append('|')
                    .append(item.getOpenTime()).append('|')
                    .append(item.getQuantity()).append('|')
                    .append(item.getSellableQuantity()).append('|')
                    .append(item.getCostPrice()).append('|')
                    .append(item.getLatestPrice()).append('|')
                    .append(item.getMarketValue()).append('|')
                    .append(item.getPositionRatio()).append('|')
                    .append(item.getDayPnL()).append('|')
                    .append(item.getTotalPnL()).append('|')
                    .append(item.getReturnRate()).append('|')
                    .append(item.getPendingLots()).append('|')
                    .append(item.getPendingCount()).append('|')
                    .append(item.getPendingPrice()).append('|')
                    .append(item.getTakeProfit()).append('|')
                    .append(item.getStopLoss()).append('|')
                    .append(item.getStorageFee())
                    .append(';');
        }
        return builder.toString();
    }

    // 生成 SHA-256 文本签名，确保跨次构建一致。
    @NonNull
    private String sha256(@NonNull String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("生成账户持仓签名失败", exception);
        }
    }

    // 统一处理空文本，避免参与签名时出现 null。
    @NonNull
    private String safeText(@Nullable String value) {
        return value == null ? "" : value;
    }
}
