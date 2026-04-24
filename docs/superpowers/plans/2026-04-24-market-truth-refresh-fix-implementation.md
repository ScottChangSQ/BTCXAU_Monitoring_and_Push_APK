# Market Truth Refresh Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复这轮真实线上问题：`1m` 长时间不更新、切周期后才跳一次；悬浮窗最新价在旧闭合价和当前 `1m` 价之间闪烁；悬浮窗量额口径与当前分钟不一致。

**Architecture:** 这次不再做泛化重构，而是针对已经确认的 3 个机制点收口。第一，图表刷新新鲜度改为只看真值中心的真实推进时间，不再看页面末尾草稿的未来 `closeTime`。第二，真值中心新增“当前分钟读模型”，让图表 `1m`、悬浮窗最新价、悬浮窗本分钟量/额都读同一份状态。第三，`REST/repair` 改成“补闭合历史”和“改当前分钟状态”分离，禁止在没有 `latestPatch` 时把当前价打回旧闭合价。

## 2026-04-24 第二阶段细化

基于这轮重新排查，再把专项补成 2 条必须同时成立的正式规则：

1. **实时链**
   服务端决定哪些 `market.snapshot` 值得发布；APP 只做最小技术校验，不再对“同一分钟这次变化算不算有效推进”做业务判断。只要消息通过技术校验并且不是重复/倒序包，就直接写入统一真值中心，再统一驱动图表与悬浮窗刷新。

2. **历史补链**
   APP 只对“闭合 `1m` 底稿是否连续”做完整性判断；一旦发现缺口，可以正式补修，但必须为同一缺口记忆状态，只有在出现新的上游证据后才允许再次补，避免网络不佳或上游暂时缺数据时陷入低频无限重试。

本计划后续任务在原 4 个任务基础上继续追加，不推翻原顺序：

- Task 1 到 Task 4 先把当前已知 3 个线上问题收口。
- Task 5 再把实时链改成“新包即入真值”。
- Task 6 再把历史补链升级成“缺口状态机 + 新证据重试”。

**Tech Stack:** Android Java、LiveData、JUnit4、Gradle、OkHttp

---

## Planned File Structure

**Create**

- `app/src/main/java/com/binance/monitor/runtime/market/truth/model/CurrentMinuteSnapshot.java`
  统一承载当前分钟最新价、本分钟量、本分钟额、分钟时间桶与真实推进时间。
- `app/src/test/java/com/binance/monitor/runtime/market/truth/CurrentMinuteSnapshotSourceTest.java`
  锁定“当前分钟读模型”必须来自真值中心，而不是悬浮窗/图表各自拼装。

**Modify**

- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`
  图表自动刷新新鲜度改为基于真值真实推进时间。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
  把刷新计划输入改成真值推进时间，不再用页面可见末尾草稿时间假装新鲜。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  删除“把末尾 candle closeTime 夹到 now”的假新鲜逻辑。
- `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
  对外暴露 `CurrentMinuteSnapshot` 与真值推进时间查询。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSnapshot.java`
  增加当前分钟读模型只读入口。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSymbolState.java`
  增加 `CurrentMinuteSnapshot` 构造与查询。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java`
  统一生成当前分钟读模型，并把 repair/REST 的“历史补齐”与“当前分钟状态”拆开。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java`
  修补链不再丢掉 `latestPatch`；如果接口无 patch，只补历史，不改当前分钟。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/GapRepairStateStore.java`
  记录同一缺口的补修状态、上次请求时间、上次上游证据和冻结条件，禁止对同一缺口无条件反复补。
- `app/src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java`
  删除实时链里“同一分钟只有量额增长才算推进”的 APP 侧业务门闩。
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
  服务层实时链改成“新包即入真值”，历史补链改成“缺口状态机 + 冷却 + 新证据重试”。
- `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
  悬浮窗最新价、本分钟量、本分钟额改读 `CurrentMinuteSnapshot`。
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
  产品卡片价格、量、额优先用当前分钟读模型，不再把“上一分钟闭合值”当当前值。
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperAdditionalTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/MinuteBaseStoreTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinatorTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/GapRepairStateStoreTest.java`
- `app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java`
- `app/src/test/java/com/binance/monitor/service/MonitorServiceRealtimeIngressTest.java`
- `app/src/test/java/com/binance/monitor/service/MonitorServiceMarketRepairPolicyTest.java`
- `CONTEXT.md`

## Task 1: 修正图表“1m 假新鲜”门闩

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperAdditionalTest.java`

- [ ] **Step 1: 写失败测试，锁定“冻结草稿不能再被判成 fresh”**

```java
@Test
public void resolvePlan_shouldNotSkipWhenTruthProgressIsStaleEvenIfVisibleDraftCloseTimeLooksFresh() {
    List<CandleEntry> local = createSeries(1500, NOW - MINUTE, MINUTE);

    MarketChartRefreshHelper.SyncPlan plan = MarketChartRefreshHelper.resolvePlan(
            local,
            1500,
            1500,
            NOW,
            NOW - 180_000L,
            MINUTE,
            false,
            true,
            MarketChartRefreshHelper.RequestReason.AUTO_REFRESH
    );

    assertEquals(MarketChartRefreshHelper.SyncMode.INCREMENTAL, plan.mode);
}
```

- [ ] **Step 2: 运行刷新策略测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest"`

Expected: FAIL，原因是当前逻辑仍把页面最后一根可见 K 线的 `closeTime` 夹到 `now`，导致冻结草稿会被误判成 fresh。

- [ ] **Step 3: 写最小实现，把新鲜度判断改成真值推进时间**

```java
// MonitorRepository.java
public synchronized long selectTruthProgressAt(@Nullable String symbol) {
    MarketTruthSymbolState state = marketTruthCenter.getSnapshot().getSymbolState(symbol);
    return state == null ? 0L : Math.max(0L, state.getLastTruthUpdateAt());
}

// MarketChartDataCoordinator.java
long truthProgressAt = repository.selectTruthProgressAt(traceSymbol);
MarketChartRefreshHelper.SyncPlan refreshPlan = MarketChartRefreshHelper.resolvePlan(
        localForPlan,
        reqInterval.getLimit(),
        host.getRestoreWindowLimit(),
        System.currentTimeMillis(),
        truthProgressAt,
        host.intervalToMs(reqInterval.getKey()),
        reqInterval.isYearAggregate(),
        host.hasRealtimeTailSourceForChart(),
        effectiveRequestReason
);

// MarketChartScreen.java
private long resolveLatestVisibleCandleTime(@Nullable List<CandleEntry> visible) {
    if (visible == null || visible.isEmpty()) {
        return 0L;
    }
    CandleEntry latest = visible.get(visible.size() - 1);
    return latest == null ? 0L : Math.max(0L, latest.getOpenTime());
}
```

- [ ] **Step 4: 重新运行刷新策略测试，确认 1m 停住后不再长期 SKIP**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperAdditionalTest.java
git commit -m "fix: use truth progress for chart freshness gating"
```

## Task 2: 建立统一“当前分钟读模型”

**Files:**
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/model/CurrentMinuteSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSymbolState.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java`
- Create: `app/src/test/java/com/binance/monitor/runtime/market/truth/CurrentMinuteSnapshotSourceTest.java`

- [ ] **Step 1: 写失败测试，锁定图表 1m 与悬浮窗必须读同一份当前分钟状态**

```java
@Test
public void currentMinuteSnapshot_shouldShareLatestPriceVolumeAndAmountWithOneMinuteDraft() {
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
    CurrentMinuteSnapshot current = center.getSnapshot().selectCurrentMinute("BTCUSDT");

    assertEquals(68050.5, current.getLatestPrice(), 0.0001d);
    assertEquals(12.0, current.getVolume(), 0.0001d);
    assertEquals(816000.0, current.getAmount(), 0.0001d);
    assertEquals(1713916800000L, current.getOpenTime());
}
```

- [ ] **Step 2: 运行真值中心测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.CurrentMinuteSnapshotSourceTest"`

Expected: FAIL，原因是 `CurrentMinuteSnapshot` 与 `selectCurrentMinute(...)` 尚不存在。

- [ ] **Step 3: 写最小实现，统一产出当前分钟快照**

```java
public final class CurrentMinuteSnapshot {
    private final String symbol;
    private final double latestPrice;
    private final double volume;
    private final double amount;
    private final long openTime;
    private final long closeTime;
    private final long updatedAt;
}

// MarketTruthSymbolState.java
@NonNull
public CurrentMinuteSnapshot selectCurrentMinute() {
    CandleEntry source = draftMinute != null ? draftMinute : latestClosedMinute;
    if (source == null) {
        return CurrentMinuteSnapshot.empty(symbol);
    }
    return new CurrentMinuteSnapshot(
            symbol,
            latestPrice,
            source.getVolume(),
            source.getQuoteVolume(),
            source.getOpenTime(),
            source.getCloseTime(),
            lastTruthUpdateAt
    );
}
```

- [ ] **Step 4: 重新运行真值测试，确认“最新价/本分钟量/本分钟额”已经同源**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.CurrentMinuteSnapshotSourceTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/runtime/market/truth/model/CurrentMinuteSnapshot.java app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSymbolState.java app/src/main/java/com/binance/monitor/runtime/market/truth/model/MarketTruthSnapshot.java app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java app/src/test/java/com/binance/monitor/runtime/market/truth/CurrentMinuteSnapshotSourceTest.java
git commit -m "feat: expose unified current minute snapshot"
```

## Task 3: 禁止 REST/repair 在无 patch 时回滚当前价

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinatorTest.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/market/truth/MinuteBaseStoreTest.java`

- [ ] **Step 1: 写失败测试，锁定“repair 只能补历史，不能把当前价打回旧闭合价”**

```java
@Test
public void repairedMinuteHistory_withoutLatestPatch_shouldNotRollbackCurrentMinutePrice() {
    MarketTruthCenter center = new MarketTruthCenter(
            new MinuteBaseStore(),
            new IntervalProjectionStore(),
            new GapDetector()
    );
    CandleEntry draft = minute(1713916980000L, 68025.0, 68050.0);
    center.applyStreamDraft("BTCUSDT", draft, 68050.0, 1713917000000L);

    center.applyRepairedMinuteWindow(
            "BTCUSDT",
            Arrays.asList(
                    minute(1713916800000L, 68000.0, 68010.0),
                    minute(1713916860000L, 68010.0, 68020.0)
            ),
            null,
            1713917010000L
    );

    assertEquals(68050.0, center.getSnapshot().selectLatestPrice("BTCUSDT"), 0.0001d);
    assertEquals(68050.0, center.getSnapshot().selectCurrentMinute("BTCUSDT").getLatestPrice(), 0.0001d);
}
```

- [ ] **Step 2: 运行 repair 相关测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.HistoryRepairCoordinatorTest" --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.MinuteBaseStoreTest"`

Expected: FAIL，原因是当前 `applyRepairedMinuteHistory(...)` 仍会在无 patch 时退回最后闭合分钟收盘价。

- [ ] **Step 3: 写最小实现，把“补闭合历史”和“改当前分钟状态”拆开**

```java
// HistoryRepairCoordinator.java
public void repairRecentMinuteTail(@NonNull String symbol,
                                   @Nullable String intervalKey) throws Exception {
    int minuteLimit = resolveMinuteRepairLimit(intervalKey);
    MarketSeriesPayload minutePayload = gatewayV2Client.fetchMarketSeries(symbol, "1m", minuteLimit);
    repository.applyRepairedMinuteWindow(
            symbol,
            minutePayload == null ? null : minutePayload.getCandles(),
            minutePayload == null ? null : minutePayload.getLatestPatch(),
            minutePayload == null ? System.currentTimeMillis() : minutePayload.getServerTime()
    );
}

// MarketTruthCenter.java
public synchronized void applyRepairedMinuteWindow(@NonNull String symbol,
                                                   @Nullable List<CandleEntry> minuteCandles,
                                                   @Nullable CandleEntry latestPatch,
                                                   long updatedAt) {
    minuteBaseStore.applyClosedMinutes(symbol, minuteCandles, snapshot.selectLatestPrice(symbol), updatedAt);
    if (latestPatch != null) {
        minuteBaseStore.applyDraft(symbol, latestPatch, latestPatch.getClose(), updatedAt);
    }
    rebuildSymbolState(symbol, updatedAt);
}
```

- [ ] **Step 4: 重新运行 repair 测试，确认旧闭合价不再回滚当前价**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.HistoryRepairCoordinatorTest" --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.MinuteBaseStoreTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java app/src/test/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinatorTest.java app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java app/src/test/java/com/binance/monitor/runtime/market/truth/MinuteBaseStoreTest.java
git commit -m "fix: prevent repair from rolling back current minute price"
```

## Task 4: 悬浮窗切到当前分钟口径，并完成整体验证

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 写失败测试，锁定悬浮窗必须展示“本分钟”而不是“上一分钟”**

```java
@Test
public void floatingCoordinator_shouldBuildVisibleSnapshotFromCurrentMinuteSource() throws Exception {
    String source = readUtf8(
            "app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java",
            "src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java"
    ).replace("\r\n", "\n").replace('\r', '\n');

    assertTrue(source.contains("repository.selectCurrentMinuteSnapshot(symbol)"));
    assertFalse(source.contains("repository.selectClosedMinute(symbol)"));
}
```

- [ ] **Step 2: 运行悬浮窗源码测试，确认先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest"`

Expected: FAIL，原因是悬浮窗当前仍拆成“闭合分钟 + latestPrice”两份读口。

- [ ] **Step 3: 写最小实现，让悬浮窗最新价、本分钟量、本分钟额都从当前分钟读模型来**

```java
// MonitorFloatingCoordinator.java
private java.util.Map<String, CurrentMinuteSnapshot> buildVisibleCurrentMinuteSnapshot(@NonNull List<String> visibleSymbols) {
    java.util.Map<String, CurrentMinuteSnapshot> snapshot = new LinkedHashMap<>();
    if (repository == null) {
        return snapshot;
    }
    for (String symbol : visibleSymbols) {
        snapshot.put(symbol, repository.selectCurrentMinuteSnapshot(symbol));
    }
    return snapshot;
}

// FloatingPositionAggregator.java
CurrentMinuteSnapshot minute = currentMinutes == null ? null : currentMinutes.get(symbol);
double latestPrice = minute == null ? 0d : minute.getLatestPrice();
double volume = minute == null ? 0d : minute.getVolume();
double amount = minute == null ? 0d : minute.getAmount();
```

- [ ] **Step 4: 运行最终定向回归，并同步阶段记录**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest" --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.MinuteBaseStoreTest" --tests "com.binance.monitor.runtime.market.truth.HistoryRepairCoordinatorTest" --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest"`

Expected: PASS

同步 `CONTEXT.md` 时写入：

```md
- 2026-04-24 已生成“market truth refresh fix”专项实施计划，顺序固定为：先修 1m 假新鲜门闩，再建立当前分钟读模型，再禁止 repair 回滚当前价，最后把悬浮窗量价额切到当前分钟口径。
```

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java CONTEXT.md
git commit -m "fix: align floating snapshot with current minute truth"
```

## Task 5: 实时链改成“新包即入真值”，删除 APP 侧业务判定

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/market/truth/MinuteBaseStoreTest.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java`
- Create: `app/src/test/java/com/binance/monitor/service/MonitorServiceRealtimeIngressTest.java`

- [ ] **Step 1: 写失败测试，锁定“同一分钟仅价格变化也必须更新真值”**

```java
@Test
public void applyDraft_shouldAcceptSameMinutePriceOnlyChangeFromStream() {
    MinuteBaseStore store = new MinuteBaseStore();
    CandleEntry first = minuteDraft(1713916800000L, 68000.0, 10.0, 680000.0);
    CandleEntry second = minuteDraft(1713916800000L, 68020.0, 10.0, 680000.0);

    store.applyDraft("BTCUSDT", first, 68000.0, 1713916810000L);
    MinuteBaseStore.ApplyResult result = store.applyDraft("BTCUSDT", second, 68020.0, 1713916812000L);

    assertEquals(68020.0, result.getLatestPrice(), 0.0001d);
    assertEquals(68020.0, result.getDraftMinute().getClose(), 0.0001d);
    assertEquals(1713916812000L, result.getUpdatedAt());
}
```

- [ ] **Step 2: 运行实时链相关测试，确认当前逻辑先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.MinuteBaseStoreTest" --tests "com.binance.monitor.service.MonitorServiceRealtimeIngressTest"`

Expected: FAIL，原因是当前 `MinuteBaseStore.isDraftProgressing(...)` 仍会把“同一分钟只有价格变化”的新消息忽略掉。

- [ ] **Step 3: 写最小实现，把实时链改成“服务端已发布新消息即入真值”**

实现要求：

- `MinuteBaseStore.applyDraft(...)` 不再按价格/量额/closeTime 判断这次流消息值不值得推进。
- 只要消息通过 `MonitorService.handleV2StreamMessage(...)` 的技术校验并被接受，就直接覆盖同一分钟草稿。
- `updatedAt` 跟真实消息应用成功绑定，不再依赖本地业务性字段比较。
- 仍保留技术性过滤：空消息、解析失败、重复包、倒序包。

- [ ] **Step 4: 重新运行实时链测试，确认真值、图表和悬浮窗都能吃到同一分钟价格更新**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.MinuteBaseStoreTest" --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.service.MonitorServiceRealtimeIngressTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/test/java/com/binance/monitor/runtime/market/truth/MinuteBaseStoreTest.java app/src/test/java/com/binance/monitor/runtime/market/truth/MarketTruthCenterTest.java app/src/test/java/com/binance/monitor/service/MonitorServiceRealtimeIngressTest.java
git commit -m "fix: apply every accepted stream tick into market truth"
```

## Task 6: 历史补链引入“缺口状态机”，禁止同一缺口无条件反复补

**Files:**
- Create: `app/src/main/java/com/binance/monitor/runtime/market/truth/GapRepairStateStore.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/GapDetector.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java`
- Create: `app/src/test/java/com/binance/monitor/runtime/market/truth/GapRepairStateStoreTest.java`
- Create: `app/src/test/java/com/binance/monitor/service/MonitorServiceMarketRepairPolicyTest.java`

- [ ] **Step 1: 写失败测试，锁定“同一缺口在无新证据时不得重复补修”**

```java
@Test
public void shouldNotRetrySameGapWithoutNewEvidence() {
    GapRepairStateStore store = new GapRepairStateStore();
    GapKey key = new GapKey("BTCUSDT", 1713913200000L, 1713913379999L);

    store.markRepairAttempted(key, "rev-1", 1713917000000L);
    store.markStillMissing(key, "rev-1", 1713917005000L);

    assertFalse(store.shouldRetry(key, "rev-1", 1713917010000L));
    assertTrue(store.shouldRetry(key, "rev-2", 1713917010000L));
}
```

- [ ] **Step 2: 运行补修策略测试，确认当前逻辑先失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.GapRepairStateStoreTest" --tests "com.binance.monitor.service.MonitorServiceMarketRepairPolicyTest"`

Expected: FAIL，原因是当前只有全局 `cooldown`，还没有“同一缺口状态记忆 + 新证据重试”机制。

- [ ] **Step 3: 写最小实现，建立正式缺口状态机**

状态固定为：

- `NEW_GAP`
- `REPAIRING`
- `RESOLVED`
- `STALLED`
- `RETRY_READY`

实现要求：

- 缺口唯一键固定为 `symbol + gapStart + gapEnd`
- 同一缺口第一次发现时允许补一次
- 若补后仍是同一缺口，标记为 `STALLED`
- `STALLED` 状态下，只有出现新证据时才进入 `RETRY_READY`
- 新证据至少包括：
  - 上游历史版本变化
  - 当前分钟前沿继续向后推进
  - 冷启动重新校验
  - 用户主动正式刷新
- `HistoryRepairCoordinator` 只执行补修，不决定是否无限重试
- `MonitorService` 负责全局补修冷却，`GapRepairStateStore` 负责同一缺口的重复抑制

- [ ] **Step 4: 接到现有补修链**

接线要求：

- `GapDetector` 发现闭合 `1m` 缺口后，登记到 `GapRepairStateStore`
- `MonitorService.requestMarketTruthRepair(...)` 继续负责“真值长期不前进”的全局补修
- 图表页 `HistoryRepairCoordinator.repairRecentMinuteTail(...)` 继续负责“当前周期依赖的分钟尾部补修”
- 两者都要先问缺口状态机：当前这次是不是允许补

- [ ] **Step 5: 重新运行补修链测试，确认不会进入低频无限循环**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.GapRepairStateStoreTest" --tests "com.binance.monitor.runtime.market.truth.HistoryRepairCoordinatorTest" --tests "com.binance.monitor.service.MonitorServiceMarketRepairPolicyTest"`

Expected: PASS

- [ ] **Step 6: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/runtime/market/truth/GapRepairStateStore.java app/src/main/java/com/binance/monitor/runtime/market/truth/GapDetector.java app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java app/src/test/java/com/binance/monitor/runtime/market/truth/GapRepairStateStoreTest.java app/src/test/java/com/binance/monitor/service/MonitorServiceMarketRepairPolicyTest.java
git commit -m "feat: dedupe historical repair by gap state"
```

## 联调验收顺序

- [ ] **Step 1: 单测全绿**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.market.truth.MinuteBaseStoreTest" --tests "com.binance.monitor.runtime.market.truth.MarketTruthCenterTest" --tests "com.binance.monitor.runtime.market.truth.HistoryRepairCoordinatorTest" --tests "com.binance.monitor.runtime.market.truth.GapRepairStateStoreTest" --tests "com.binance.monitor.service.MonitorServiceRealtimeIngressTest" --tests "com.binance.monitor.service.MonitorServiceMarketRepairPolicyTest" --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest"
```

- [ ] **Step 2: 编译并安装最新 APK**

Run:

```bash
./gradlew.bat :app:assembleDebug
adb -s 7fab54c4 install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: 真机验收口径**

依次验证：

1. 停留在 `1m` 页面不切周期时，只要服务器继续推新价，`1m` 最新一根就要持续前进。
2. 悬浮窗最新价必须跟 `1m` 当前分钟同向同步，不再长时间停住或在旧价间闪烁。
3. 网络暂时不好、上游暂时缺数据时，APP 最多看到“缺口仍存在”，但不能持续对同一缺口反复补修。
4. `5m~1d` 切换后，最新显示必须来自同一份真值中心，不再依赖用户反复切周期自愈。

- [ ] **Step 4: 更新上下文记录**

完成阶段性实现后，更新 `CONTEXT.md`，只写：

- 当前做到哪一条任务
- 上次停在哪
- 为什么这样拆

## Self-Review Checklist

- 需求覆盖检查：
  - “1m 长时间不更新”由 Task 1 修正。
  - “悬浮窗与 1m 最新价不同源”由 Task 2 修正。
  - “REST/repair 把当前价打回旧闭合价”由 Task 3 修正。
  - “悬浮窗量额要改成本分钟口径”由 Task 4 修正。
- 占位符检查：
  - 本计划没有 `TBD/TODO/后续补充/类似前一任务` 这类占位语。
- 类型一致性检查：
  - 统一使用 `CurrentMinuteSnapshot` 表示当前分钟读模型。
  - `latestPrice/volume/amount/openTime/closeTime/updatedAt` 这一组字段前后一致。
