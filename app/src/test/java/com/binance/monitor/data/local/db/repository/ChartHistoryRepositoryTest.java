/*
 * 验证图表历史合并逻辑会保留旧数据，并按 openTime 去重，
 * 避免切周期或刷新后把已经拉过的历史 K 线丢掉。
 */
package com.binance.monitor.data.local.db.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import com.binance.monitor.data.local.db.dao.KlineHistoryDao;
import com.binance.monitor.data.local.db.entity.KlineHistoryEntity;
import com.binance.monitor.data.model.CandleEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChartHistoryRepositoryTest {

    // 图表页已在上层完成合并时，仓库应直接写入传入窗口，不再额外回读整段旧历史。
    @Test
    public void saveCandlesShouldUpsertIncomingWithoutReloadingExistingHistory() {
        FakeKlineHistoryDao dao = new FakeKlineHistoryDao();
        ChartHistoryRepository repository = new ChartHistoryRepository(dao);
        List<CandleEntry> incoming = Arrays.asList(
                candle(2000L, 2999L, 105d),
                candle(3000L, 3999L, 106d)
        );

        repository.saveCandles(
                "BTCUSDT_15m",
                "BTCUSDT",
                "15m",
                "15m",
                false,
                incoming
        );

        assertEquals(0, dao.loadSeriesCallCount);
        assertEquals(1, dao.upsertAllCallCount);
        assertEquals(2, dao.lastUpserted.size());
        assertEquals(2000L, dao.lastUpserted.get(0).openTime);
        assertEquals(3000L, dao.lastUpserted.get(1).openTime);
    }

    // 空输入不应触发数据库写入。
    @Test
    public void saveCandlesShouldSkipEmptyInput() {
        FakeKlineHistoryDao dao = new FakeKlineHistoryDao();
        ChartHistoryRepository repository = new ChartHistoryRepository(dao);

        repository.saveCandles("BTCUSDT_15m", "BTCUSDT", "15m", "15m", false, new ArrayList<>());

        assertEquals(0, dao.loadSeriesCallCount);
        assertEquals(0, dao.upsertAllCallCount);
        assertSame(null, dao.lastUpserted);
    }

    private CandleEntry candle(long openTime, long closeTime, double close) {
        return new CandleEntry("BTCUSDT", openTime, closeTime, close, close, close, close, 1d, 1d);
    }

    private static final class FakeKlineHistoryDao implements KlineHistoryDao {
        private int loadSeriesCallCount;
        private int upsertAllCallCount;
        private List<KlineHistoryEntity> lastUpserted;

        @Override
        public List<KlineHistoryEntity> loadSeries(String seriesKey) {
            loadSeriesCallCount++;
            return new ArrayList<>();
        }

        @Override
        public void upsertAll(List<KlineHistoryEntity> items) {
            upsertAllCallCount++;
            lastUpserted = items;
        }

        @Override
        public int clearAll() {
            return 0;
        }
    }
}
