# Market Truth Center Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把行情图表、悬浮窗、悬浮提示统一切到“一个市场真值中心”，让最新价、`1m` 实时尾巴、`5m~1d` 显示序列都来自同一份正式底稿。

**Architecture:** Android 端新增 `MarketTruthCenter` 作为唯一市场真值入口，统一接收 `stream` 实时输入与 `REST` 历史修补输入；`1m` 作为唯一正式基础底稿，`5m~1d` 由系统级投影层增量生成。`MonitorRepository` 退成统一出口壳，`MonitorService`、图表页、悬浮窗都只读 `MarketTruthCenter` 发布的只读快照，不再各自保留一套真值。

**Tech Stack:** Android Java、LiveData、JUnit4、Gradle、OkHttp

---

## Planned File Structure

**Create**

- `app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java`
  统一承接 stream/REST 输入、维护单次快照发布、对外提供市场真值查询。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java`
  维护每个品种的 `1m closed minute` 与 `draft minute`。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/IntervalProjectionStore.java`
  从 `1m` 正式底稿增量投影 `5m~1d` 闭合桶。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/GapDetector.java`
  按时间连续性检测分钟断档与窗口缺口。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java`
  根据缺口范围生成 `1m` 修补窗口，统一调度历史修补。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSnapshot.java`
  对 UI 发布的不可变市场快照。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSymbolState.java`
  单个品种的正式底稿状态、投影状态、修补状态。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketDisplaySeries.java`
  图表读取的 ready-to-render 序列对象。
- `app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/MinuteBaseStoreTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/IntervalProjectionStoreTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/GapDetectorTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinatorTest.java`

**Modify**

- `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
  把现有 `selectLatestPrice / selectClosedMinute / selectDisplayKline` 改成代理新真值中心。
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
  把 `/v2/stream` 解析结果写入 `MarketTruthCenter`，不再直接作为最终 UI 真值。
- `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
  悬浮窗只读统一市场快照。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
  图表请求改为“要求某个窗口可用”，不再自己持有正式真值。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartSeriesLoaderHelper.java`
  REST 拉回结果写入 `MarketTruthCenter`，不再直接作为页面主显示结果。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java`
  去掉页面内高周期正式聚合，只保留统一快照转显示的轻量适配。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  删除 `minuteKey` 正式底稿职责，改成订阅统一读模型。
- `app/src/main/java/com/binance/monitor/runtime/market/MarketSelector.java`
  保留兼容查询入口时统一代理到新快照。
- `app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java`
- `app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelperTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`
- `README.md`
- `ARCHITECTURE.md`
- `CONTEXT.md`

### Task 1: 建立统一市场真值中心与 `1m` 基础底稿

**Files:**
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/IntervalProjectionStore.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/GapDetector.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSnapshot.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSymbolState.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketDisplaySeries.java`
- Create: `app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java`
- Create: `app/src/test/java/com/binance/monitor/runtime/market/truth/MinuteBaseStoreTest.java`

- [ ] **Step 1: 先写“最新价与 `1m` 实时尾巴同源”的失败测试**

```java
@Test
public void applyStreamDraft_shouldDriveLatestPriceAndOneMinuteTailFromSameSource() {
    MarketTruthCenter center = new MarketTruthCenter(
            new MinuteBaseStore(),
            new IntervalProjectionStore(),
            new GapDetector()
    );

    CandleEntry draft = new CandleEntry(
            "BTCUSDT",
            1713916800000L,
            1713916859999L,
            68000.0,
            68080.0,
            67980.0,
            68050.5,
            12.0,
            816000.0
    );

    center.applyStreamDraft("BTCUSDT", draft, 68050.5, 1713916840000L);
    MarketTruthSnapshot snapshot = center.getSnapshot();

    assertEquals(68050.5, snapshot.selectLatestPrice("BTCUSDT"), 0.0001d);
    MarketDisplaySeries display = snapshot.selectDisplaySeries("BTCUSDT", "1m", 120);
    assertEquals(1, display.getCandles().size());
    assertEquals(68050.5, display.getCandles().get(0).getClose(), 0.0001d);
}
```

- [ ] **Step 2: 运行新建真值中心测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.MinuteBaseStoreTest"`

Expected: FAIL，原因是 `MarketTruthCenter/MinuteBaseStore` 与 `selectDisplaySeries()` 还不存在。

- [ ] **Step 3: 写最小实现，让 `stream draft -> latest price -> 1m display` 先闭环**

```java
public final class MarketTruthCenter {
    private final MinuteBaseStore minuteBaseStore;
    private final IntervalProjectionStore intervalProjectionStore;
    private final GapDetector gapDetector;
    private MarketTruthSnapshot snapshot = MarketTruthSnapshot.empty();

    public void applyStreamDraft(@NonNull String symbol,
                                 @NonNull CandleEntry draftMinute,
                                 double latestPrice,
                                 long updatedAt) {
        MinuteBaseStore.ApplyResult result = minuteBaseStore.applyDraft(symbol, draftMinute, latestPrice, updatedAt);
        snapshot = snapshot.withSymbolState(
                symbol,
                MarketTruthSymbolState.fromMinuteResult(result)
        );
    }

    @NonNull
    public MarketTruthSnapshot getSnapshot() {
        return snapshot;
    }
}
```

- [ ] **Step 4: 重新运行真值中心测试，确认 `1m` 同源断言通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.MinuteBaseStoreTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/runtime/market/truth app/src/test/java/com/binance/monitor/runtime/market/truth
git commit -m "feat: add market truth center minute base"
```

### Task 2: 建立高周期增量投影与断档检测

**Files:**
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/IntervalProjectionStore.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/GapDetector.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/model/ProjectionBucket.java`
- Create: `app/src/test/java/com/binance/monitor/runtime/market/truth/IntervalProjectionStoreTest.java`
- Create: `app/src/test/java/com/binance/monitor/runtime/market/truth/GapDetectorTest.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java`

- [ ] **Step 1: 先写“分钟闭合后只更新受影响高周期桶”和“缺分钟时能识别断档”的失败测试**

```java
@Test
public void closeMinute_shouldOnlyAdvanceAffectedProjectionBuckets() {
    IntervalProjectionStore store = new IntervalProjectionStore();

    store.onMinuteClosed("BTCUSDT", candle(1713916800000L, 68000.0, 68020.0));
    store.onMinuteClosed("BTCUSDT", candle(1713916860000L, 68020.0, 68010.0));

    List<CandleEntry> fiveMinute = store.selectClosedSeries("BTCUSDT", "5m", 50);
    assertEquals(1, fiveMinute.size());
    assertEquals(68010.0, fiveMinute.get(0).getClose(), 0.0001d);
}

@Test
public void detectMinuteGap_shouldMarkMissingWindowByTimeContinuity() {
    GapDetector detector = new GapDetector();
    List<CandleEntry> minutes = Arrays.asList(
            candle(1713916800000L, 68000.0, 68010.0),
            candle(1713916920000L, 68010.0, 68030.0)
    );

    GapDetector.Gap gap = detector.findMinuteGap(minutes, 60_000L);
    assertEquals(1713916860000L, gap.getMissingStartOpenTime());
    assertEquals(1713916919999L, gap.getMissingEndCloseTime());
}
```

- [ ] **Step 2: 运行投影与断档测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.IntervalProjectionStoreTest" --tests "com.binance.monitor.runtime.market.truth.GapDetectorTest"`

Expected: FAIL，原因是 `onMinuteClosed()`、`selectClosedSeries()`、`findMinuteGap()` 尚不存在。

- [ ] **Step 3: 写最小实现，把高周期正式历史和缺口状态下沉到真值中心**

```java
public final class IntervalProjectionStore {
    private final Map<String, Map<String, List<CandleEntry>>> closedBuckets = new LinkedHashMap<>();

    public void onMinuteClosed(@NonNull String symbol, @NonNull CandleEntry minute) {
        mergeClosedBucket(symbol, "5m", minute);
        mergeClosedBucket(symbol, "15m", minute);
        mergeClosedBucket(symbol, "30m", minute);
        mergeClosedBucket(symbol, "1h", minute);
        mergeClosedBucket(symbol, "4h", minute);
        mergeClosedBucket(symbol, "1d", minute);
    }
}

public final class GapDetector {
    @Nullable
    public Gap findMinuteGap(@Nullable List<CandleEntry> candles, long stepMs) {
        if (candles == null || candles.size() < 2) {
            return null;
        }
        for (int index = 1; index < candles.size(); index++) {
            long expectedOpen = candles.get(index - 1).getOpenTime() + stepMs;
            long actualOpen = candles.get(index).getOpenTime();
            if (actualOpen > expectedOpen) {
                return new Gap(expectedOpen, actualOpen - 1L);
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: 重新运行投影与断档测试，确认增量投影成立**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.IntervalProjectionStoreTest" --tests "com.binance.monitor.runtime.market.truth.GapDetectorTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/runtime/market/truth app/src/test/java/com/binance/monitor/runtime/market/truth
git commit -m "feat: add interval projection and gap detection"
```

### Task 3: 让 `MonitorRepository` 和 `MonitorService` 改写统一真值中心

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/MarketSelector.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/data/repository/MonitorRepositoryTruthCenterTest.java`

- [ ] **Step 1: 先写“stream 进入 repository 后，最新价和 display kline 来自同一快照”的失败测试**

```java
@Test
public void applyStreamSnapshot_shouldExposeLatestPriceAndDisplayKlineFromTruthCenter() {
    MonitorRepository repository = MonitorRepository.createForTest();

    repository.applyStreamDraft(
            "BTCUSDT",
            new CandleEntry("BTCUSDT", 1713916800000L, 1713916859999L, 68000.0, 68080.0, 67980.0, 68040.0, 10.0, 680000.0),
            68040.0,
            1713916840000L
    );

    assertEquals(68040.0, repository.selectLatestPrice("BTCUSDT"), 0.0001d);
    assertEquals(68040.0, repository.selectDisplaySeries("BTCUSDT", "1m", 120).getCandles().get(0).getClose(), 0.0001d);
}
```

- [ ] **Step 2: 运行 repository / service 测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.repository.MonitorRepositoryTruthCenterTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest"`

Expected: FAIL，原因是 `applyStreamDraft()` 与 `selectDisplaySeries()` 还未在 repository 暴露。

- [ ] **Step 3: 把 repository 变成真值中心出口壳，并让 service 写入新入口**

```java
public class MonitorRepository {
    private final MarketTruthCenter marketTruthCenter = new MarketTruthCenter(
            new MinuteBaseStore(),
            new IntervalProjectionStore(),
            new GapDetector()
    );

    public synchronized void applyStreamDraft(@NonNull String symbol,
                                              @NonNull CandleEntry draftMinute,
                                              double latestPrice,
                                              long updatedAt) {
        marketTruthCenter.applyStreamDraft(symbol, draftMinute, latestPrice, updatedAt);
        marketRuntimeSnapshotLiveData.postValue(MarketRuntimeSnapshot.empty());
        lastUpdateTime.postValue(updatedAt);
    }

    @NonNull
    public synchronized MarketDisplaySeries selectDisplaySeries(@NonNull String symbol,
                                                                @NonNull String intervalKey,
                                                                int limit) {
        return marketTruthCenter.getSnapshot().selectDisplaySeries(symbol, intervalKey, limit);
    }
}
```

- [ ] **Step 4: 重新运行 repository / service 测试，确认 stream 已统一落到真值中心**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.repository.MonitorRepositoryTruthCenterTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/runtime/market/MarketSelector.java app/src/test/java/com/binance/monitor/data/repository/MonitorRepositoryTruthCenterTest.java app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java
git commit -m "feat: route stream market state through truth center"
```

### Task 4: 建立 `1m` 历史修补链，让 REST 先写真值中心再出显示

**Files:**
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java`
- Create: `app/src/test/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinatorTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartSeriesLoaderHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`

- [ ] **Step 1: 先写“切到 `5m` 时也先补 `1m`，再由投影层生成显示”的失败测试**

```java
@Test
public void requestFiveMinuteWindow_shouldRepairMinuteBaseBeforeBuildingProjection() throws Exception {
    FakeGateway gateway = new FakeGateway()
            .withMinuteSeries("BTCUSDT", 120, buildMinuteWindow());

    MonitorRepository repository = MonitorRepository.createForTest();
    HistoryRepairCoordinator coordinator = new HistoryRepairCoordinator(repository, gateway::fetchMinuteSeries);

    coordinator.ensureWindow("BTCUSDT", "5m", 1713916800000L, 1713924000000L, 120);

    MarketDisplaySeries display = repository.selectDisplaySeries("BTCUSDT", "5m", 120);
    assertFalse(display.getCandles().isEmpty());
    assertEquals("1m", gateway.getLastRequestedInterval());
}
```

- [ ] **Step 2: 运行历史修补与图表刷新测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.HistoryRepairCoordinatorTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest"`

Expected: FAIL，原因是图表刷新仍直接按当前周期抓 REST，修补协调器还不存在。

- [ ] **Step 3: 写最小实现，让 REST 拉回结果先补 `1m` 正式底稿**

```java
public final class HistoryRepairCoordinator {
    public interface MinuteHistoryGateway {
        @NonNull
        List<CandleEntry> fetchMinuteSeries(@NonNull String symbol,
                                            long startTimeInclusive,
                                            long endTimeInclusive,
                                            int limit) throws Exception;
    }

    public void ensureWindow(@NonNull String symbol,
                             @NonNull String intervalKey,
                             long startTimeInclusive,
                             long endTimeInclusive,
                             int limit) throws Exception {
        List<CandleEntry> repairedMinutes = minuteHistoryGateway.fetchMinuteSeries(
                symbol,
                startTimeInclusive,
                endTimeInclusive,
                limit
        );
        repository.applyRepairedMinuteHistory(symbol, repairedMinutes, System.currentTimeMillis());
    }
}
```

- [ ] **Step 4: 重新运行历史修补测试，确认 `REST -> 1m 底稿 -> 高周期显示` 链成立**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.HistoryRepairCoordinatorTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartSeriesLoaderHelper.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java app/src/test/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinatorTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java
git commit -m "feat: repair minute history through truth center"
```

### Task 5: 迁移悬浮窗与图表到统一读模型，删除页面级正式聚合

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`

- [ ] **Step 1: 先写“悬浮窗最新价与图表 `1m` 尾巴同源，`5m` 不再读页面 minuteKey”的失败测试**

```java
@Test
public void floatingAndChart_shouldReadSameLatestPriceSnapshot() {
    MonitorRepository repository = MonitorRepository.createForTest();
    repository.applyStreamDraft("BTCUSDT", draftMinute(68025.0), 68025.0, 1713916840000L);

    double floatingPrice = repository.selectLatestPrice("BTCUSDT");
    double chartPrice = repository.selectDisplaySeries("BTCUSDT", "1m", 120)
            .getCandles()
            .get(0)
            .getClose();

    assertEquals(chartPrice, floatingPrice, 0.0001d);
}

@Test
public void fiveMinuteRealtimeTail_shouldBuildFromTruthCenterInsteadOfMinuteCache() {
    MarketDisplaySeries display = repository.selectDisplaySeries("BTCUSDT", "5m", 120);
    assertFalse(display.getCandles().isEmpty());
}
```

- [ ] **Step 2: 运行悬浮窗与图表实时尾巴测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartRealtimeTailHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest"`

Expected: FAIL，原因是悬浮窗仍读旧 repository 价格，图表高周期仍走页面聚合。

- [ ] **Step 3: 写最小实现，把显示端统一切到 `MarketDisplaySeries`**

```java
void observeRealtimeDisplayKlines() {
    MonitorRepository repository = host.getMonitorRepository();
    if (repository == null) {
        return;
    }
    repository.getMarketTruthSnapshotLiveData().observe(host.getLifecycleOwner(), snapshot -> {
        MarketDisplaySeries display = snapshot.selectDisplaySeries(
                host.getSelectedSymbol(),
                host.getSelectedInterval().getKey(),
                host.getSelectedInterval().getLimit()
        );
        host.applyDisplayCandles(host.buildCurrentCacheKey(), display.getCandles(), true, true, false);
    });
}
```

- [ ] **Step 4: 重新运行悬浮窗与图表测试，确认 UI 已只读统一快照**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartRealtimeTailHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelperTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java
git commit -m "feat: migrate floating and chart reads to market truth center"
```

### Task 6: 删除旧鲜度判断与页面真值残留，补全回归与文档

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartSeriesLoaderHelper.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperAdditionalTest.java`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 先写“未闭合 K 线未来 closeTime 不再让页面误判新鲜”的失败测试**

```java
@Test
public void freshness_shouldUseTruthCenterWindowStateInsteadOfFutureCloseTime() {
    MarketTruthSnapshot snapshot = MarketTruthSnapshot.empty()
            .withGapState("BTCUSDT", true);

    assertFalse(MarketChartRefreshHelper.shouldSkipRefreshForTruthSnapshot(
            snapshot,
            "BTCUSDT",
            "5m",
            1713917100000L
    ));
}
```

- [ ] **Step 2: 运行图表刷新回归测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest"`

Expected: FAIL，原因是刷新策略仍用旧的可见 K 线时间与页面缓存判断。

- [ ] **Step 3: 写最小实现，移除 `minuteKey` 正式底稿职责与假新鲜判断**

```java
public static boolean shouldSkipRefreshForTruthSnapshot(@Nullable MarketTruthSnapshot snapshot,
                                                        @NonNull String symbol,
                                                        @NonNull String intervalKey,
                                                        long nowMs) {
    if (snapshot == null) {
        return false;
    }
    MarketTruthSymbolState state = snapshot.getSymbolState(symbol);
    return state != null
            && !state.hasGap()
            && state.getLastTruthUpdateAt() > 0L
            && nowMs - state.getLastTruthUpdateAt() < 2_000L;
}
```

- [ ] **Step 4: 运行完整定向回归，并同步文档**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.IntervalProjectionStoreTest" --tests "com.binance.monitor.runtime.market.truth.HistoryRepairCoordinatorTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartRealtimeTailHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest"`

Expected: PASS

同步文档时写入以下内容：

```md
- README.md：补“统一市场真值中心”模块说明与测试命令。
- ARCHITECTURE.md：补 `MarketTruthCenter -> MinuteBaseStore -> IntervalProjectionStore -> UiReadModelAdapter` 调用关系。
- CONTEXT.md：记录“图表/悬浮窗已切到统一市场真值中心”的停点。
```

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartSeriesLoaderHelper.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperAdditionalTest.java README.md ARCHITECTURE.md CONTEXT.md
git commit -m "refactor: remove page-local market truth and refresh heuristics"
```

## Self-Review Checklist

- `spec` 覆盖检查：
  - “统一真值出口”对应 Task 1、Task 3、Task 5。
  - “`1m` 是唯一基础底稿”对应 Task 1、Task 4。
  - “`5m~1d` 增量投影”对应 Task 2。
  - “断档检测与修补”对应 Task 2、Task 4、Task 6。
  - “悬浮窗与图表同源”对应 Task 3、Task 5。
  - “去掉页面假新鲜与 `minuteKey` 真值职责”对应 Task 5、Task 6。
- 占位符检查：
  - 本计划不包含“待补”“后面再说”“参考上一任务”这类空洞占位语。
- 类型一致性检查：
  - 统一使用 `MarketTruthCenter / MinuteBaseStore / IntervalProjectionStore / GapDetector / HistoryRepairCoordinator / MarketTruthSnapshot / MarketDisplaySeries` 这一组命名，不再混用第二套别名。
