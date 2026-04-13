/*
 * 账户持仓页只读展示模型，统一承载概览、持仓、挂单与签名信息。
 * 该模型由 AccountPositionUiModelFactory 生成，供 AccountPositionActivity 与差异比较复用。
 */
package com.binance.monitor.ui.account;

import com.binance.monitor.domain.account.model.AccountMetric;
import com.binance.monitor.domain.account.model.PositionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AccountPositionUiModel {
    private final List<AccountMetric> overviewMetrics;
    private final String connectionStatusText;
    private final String positionSummaryText;
    private final String pendingSummaryText;
    private final List<PositionAggregateItem> positionAggregates;
    private final List<PositionItem> positions;
    private final List<PositionItem> pendingOrders;
    private final String updatedAtText;
    private final String signature;
    private final long snapshotVersionMs;

    public AccountPositionUiModel(List<AccountMetric> overviewMetrics,
                                  String connectionStatusText,
                                  String positionSummaryText,
                                  String pendingSummaryText,
                                  List<PositionAggregateItem> positionAggregates,
                                  List<PositionItem> positions,
                                  List<PositionItem> pendingOrders,
                                  String updatedAtText,
                                  String signature,
                                  long snapshotVersionMs) {
        this.overviewMetrics = immutableCopy(overviewMetrics);
        this.connectionStatusText = safeText(connectionStatusText);
        this.positionSummaryText = safeText(positionSummaryText);
        this.pendingSummaryText = safeText(pendingSummaryText);
        this.positionAggregates = immutableCopy(positionAggregates);
        this.positions = immutableCopy(positions);
        this.pendingOrders = immutableCopy(pendingOrders);
        this.updatedAtText = safeText(updatedAtText);
        this.signature = safeText(signature);
        this.snapshotVersionMs = Math.max(0L, snapshotVersionMs);
    }

    // 返回账户概览指标列表。
    public List<AccountMetric> getOverviewMetrics() {
        return overviewMetrics;
    }

    // 返回顶部账户连接状态文案。
    public String getConnectionStatusText() {
        return connectionStatusText;
    }

    // 返回持仓摘要文本。
    public String getPositionSummaryText() {
        return positionSummaryText;
    }

    // 返回挂单摘要文本。
    public String getPendingSummaryText() {
        return pendingSummaryText;
    }

    // 返回按产品和方向聚合后的当前持仓摘要列表。
    public List<PositionAggregateItem> getPositionAggregates() {
        return positionAggregates;
    }

    // 返回当前持仓明细列表。
    public List<PositionItem> getPositions() {
        return positions;
    }

    // 返回当前挂单明细列表。
    public List<PositionItem> getPendingOrders() {
        return pendingOrders;
    }

    // 返回更新时间展示文本。
    public String getUpdatedAtText() {
        return updatedAtText;
    }

    // 返回模型稳定签名。
    public String getSignature() {
        return signature;
    }

    // 返回当前读模型对应的快照版本时间，用于拒绝旧缓存回盖新界面。
    public long getSnapshotVersionMs() {
        return snapshotVersionMs;
    }

    // 构建空页面模型，供首帧或无数据场景复用。
    public static AccountPositionUiModel empty() {
        return new AccountPositionUiModel(
                Collections.emptyList(),
                "未连接账户",
                "当前持仓 0 条",
                "挂单 0 条",
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                "--",
                "",
                0L
        );
    }

    // 统一把输入列表复制为不可变列表，避免渲染阶段被外部改写。
    private static <T> List<T> immutableCopy(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    // 统一处理空文本，避免 UI 出现 null。
    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
