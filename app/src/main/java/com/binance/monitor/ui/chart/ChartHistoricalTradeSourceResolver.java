/*
 * 图表历史成交来源解析器，负责在实时快照缺失时回退到本地留存交易。
 * 仅供 K 线图历史成交叠加层使用，避免图表页被账户会话状态整段清空。
 */
package com.binance.monitor.ui.chart;

import androidx.annotation.Nullable;

import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.ui.account.TradeLifecycleMergeHelper;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import java.util.ArrayList;
import java.util.List;

final class ChartHistoricalTradeSourceResolver {

    private ChartHistoricalTradeSourceResolver() {
    }

    // 优先使用当前快照中的历史成交，缺失时再回退到本地留存交易。
    static List<TradeRecordItem> resolve(@Nullable AccountSnapshot snapshot,
                                         @Nullable AccountStorageRepository.StoredSnapshot storedSnapshot) {
        List<TradeRecordItem> snapshotTrades = snapshot == null ? null : snapshot.getTrades();
        if (snapshotTrades != null && !snapshotTrades.isEmpty()) {
            return TradeLifecycleMergeHelper.merge(snapshotTrades);
        }
        List<TradeRecordItem> storedTrades = storedSnapshot == null ? null : storedSnapshot.getTrades();
        if (storedTrades != null && !storedTrades.isEmpty()) {
            return TradeLifecycleMergeHelper.merge(storedTrades);
        }
        return new ArrayList<>();
    }
}
