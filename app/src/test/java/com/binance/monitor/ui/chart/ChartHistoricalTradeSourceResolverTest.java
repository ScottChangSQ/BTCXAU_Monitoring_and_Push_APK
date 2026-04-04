/*
 * 图表历史成交来源解析器测试，确保图表页能在实时快照缺失时继续读取本地历史成交。
 */
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.binance.monitor.data.local.db.repository.AccountStorageRepository;
import com.binance.monitor.ui.account.model.AccountMetric;
import com.binance.monitor.ui.account.model.AccountSnapshot;
import com.binance.monitor.ui.account.model.CurvePoint;
import com.binance.monitor.ui.account.model.PositionItem;
import com.binance.monitor.ui.account.model.TradeRecordItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChartHistoricalTradeSourceResolverTest {

    @Test
    public void shouldPreferSnapshotTradesWhenCurrentSnapshotHasTrades() {
        TradeRecordItem snapshotTrade = buildTrade(101L, "BTCUSDT");
        TradeRecordItem storedTrade = buildTrade(202L, "XAUUSDT");

        List<TradeRecordItem> result = ChartHistoricalTradeSourceResolver.resolve(
                buildSnapshot(Collections.singletonList(snapshotTrade)),
                buildStoredSnapshot(Collections.singletonList(storedTrade))
        );

        assertEquals(1, result.size());
        assertEquals(101L, result.get(0).getDealTicket());
    }

    @Test
    public void shouldFallbackToStoredTradesWhenSnapshotIsMissing() {
        TradeRecordItem storedTrade = buildTrade(202L, "XAUUSDT");

        List<TradeRecordItem> result = ChartHistoricalTradeSourceResolver.resolve(
                null,
                buildStoredSnapshot(Collections.singletonList(storedTrade))
        );

        assertEquals(1, result.size());
        assertEquals(202L, result.get(0).getDealTicket());
    }

    @Test
    public void shouldFallbackToStoredTradesWhenSnapshotTradeListIsEmpty() {
        TradeRecordItem storedTrade = buildTrade(303L, "BTCUSDT");

        List<TradeRecordItem> result = ChartHistoricalTradeSourceResolver.resolve(
                buildSnapshot(new ArrayList<>()),
                buildStoredSnapshot(Collections.singletonList(storedTrade))
        );

        assertEquals(1, result.size());
        assertEquals(303L, result.get(0).getDealTicket());
    }

    @Test
    public void shouldReturnEmptyListWhenNoTradeSourceExists() {
        List<TradeRecordItem> result = ChartHistoricalTradeSourceResolver.resolve(
                buildSnapshot(new ArrayList<>()),
                buildStoredSnapshot(new ArrayList<>())
        );

        assertTrue(result.isEmpty());
    }

    @Test
    public void shouldNormalizeLifecycleTradesBeforeReturningToChart() {
        long openTime = 1_000_000L;
        long closeTime = 1_120_000L;
        List<TradeRecordItem> result = ChartHistoricalTradeSourceResolver.resolve(
                buildSnapshot(java.util.Arrays.asList(
                        new TradeRecordItem(
                                openTime,
                                "BTCUSDT",
                                "BTCUSDT",
                                "buy",
                                100d,
                                1d,
                                100d,
                                0d,
                                "",
                                0d,
                                openTime,
                                openTime,
                                0d,
                                100d,
                                100d,
                                0L,
                                9001L,
                                8001L,
                                0
                        ),
                        new TradeRecordItem(
                                closeTime,
                                "BTCUSDT",
                                "BTCUSDT",
                                "buy",
                                110d,
                                1d,
                                110d,
                                0d,
                                "",
                                15d,
                                closeTime,
                                closeTime,
                                -1d,
                                110d,
                                110d,
                                0L,
                                9001L,
                                8001L,
                                1
                        )
                )),
                null
        );

        assertEquals(1, result.size());
        assertEquals(openTime, result.get(0).getOpenTime());
        assertEquals(closeTime, result.get(0).getCloseTime());
        assertEquals(100d, result.get(0).getOpenPrice(), 0.0001d);
        assertEquals(110d, result.get(0).getClosePrice(), 0.0001d);
    }

    // 构造只关心历史成交列表的快照。
    private AccountSnapshot buildSnapshot(List<TradeRecordItem> trades) {
        return new AccountSnapshot(
                new ArrayList<AccountMetric>(),
                new ArrayList<CurvePoint>(),
                new ArrayList<AccountMetric>(),
                new ArrayList<PositionItem>(),
                new ArrayList<PositionItem>(),
                trades,
                new ArrayList<AccountMetric>()
        );
    }

    // 构造只关心历史成交列表的本地留存快照。
    private AccountStorageRepository.StoredSnapshot buildStoredSnapshot(List<TradeRecordItem> trades) {
        return new AccountStorageRepository.StoredSnapshot(
                true,
                "",
                "",
                "",
                "",
                0L,
                "",
                0L,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                trades,
                new ArrayList<>()
        );
    }

    // 构造用于区分来源的简单历史成交。
    private TradeRecordItem buildTrade(long dealTicket, String code) {
        return new TradeRecordItem(
                1_000_000L + dealTicket,
                code,
                code,
                "buy",
                100d,
                1d,
                100d,
                0d,
                "",
                5d,
                1_000_000L,
                1_060_000L,
                0d,
                100d,
                105d,
                dealTicket,
                dealTicket,
                dealTicket,
                1
        );
    }
}
