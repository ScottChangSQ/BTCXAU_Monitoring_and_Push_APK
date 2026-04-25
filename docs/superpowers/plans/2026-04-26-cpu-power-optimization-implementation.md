# CPU Power Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变核心功能边界的前提下，优先压低前台 CPU 与电量消耗，并把后台亮屏/息屏场景收敛为最小运行成本。

**Architecture:** 本次不再把“资源优化”当作单一参数调节问题，而是拆成三条独立链路处理：图表页实时尾部链、悬浮窗刷新链、分析页次级统计链。前台优先减少高频 UI 落图与重绑定；后台亮屏只保留悬浮窗最小字段；息屏退化为 alert-only，并在回前台时走一次全量补齐。

**Tech Stack:** Android Java、Handler/Looper、LiveData、OkHttp WebSocket、FastAPI/Python、Gradle、JUnit、adb batterystats/top/dumpsys。

---

## 一、目标边界

### 这次要解决的问题

当前真机复测里，后台熄屏 CPU 已经明显低于前台，但前台 CPU 与电量仍偏高。代码链路显示，主耗点不是单一网络心跳，而是：

- 图表页 `marketTick -> realtime tail -> K 线尾部 UI 落图`
- 前台悬浮窗 `500ms` 级刷新
- 图表页账户叠加层频繁重绑
- 分析页长页面内多个次级区块仍可能在同一轮快照里一起刷新

### 这次不做的事

- 不改交易语义
- 不改账户正确性真值来源
- 不做与 CPU/电量无直接关系的样式重构
- 不把后台网络流量当主目标单独优化，但允许顺带下降

### 成功标准

1. 前台图表页 `15min` CPU 与 `batterystats` mAh 明显下降。
2. 前台分析页 `15min` CPU 明显下降。
3. 前台悬浮窗场景 CPU 有可复测的下降。
4. 后台亮屏与息屏场景不引入功能回退：悬浮窗、异常提醒、回前台补齐数据都正常。

## 二、文件边界

### 本次主要会改的文件

- `app/src/main/java/com/binance/monitor/constants/AppConstants.java`
  - 刷新节奏常量。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  - 图表页 realtime tail 落图、叠加层刷新、状态刷新预算。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
  - 图表页实时行情观察与主动请求编排。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
  - 图表页自动刷新、叠加层 debounce、页面可见态编排。
- `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
  - 图表页高频刷新合并调度。
- `app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java`
  - 悬浮窗与前后台刷新节奏策略。
- `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
  - 悬浮窗节流、最小字段拼装、亮屏/熄屏资格控制。
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
  - 悬浮窗卡片绑定与最小化展示。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
  - 分析页可见性门控、次级区块延后刷新。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
  - 分析页旧 Activity 入口保持一致行为。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java`
  - 分析页曲线、收益统计、交易统计、交易记录刷新编排。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java`
  - 分析页页面可见态循环编排。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
  - 轻量快照与全量快照的分层、后台场景全量数据暂停。
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
  - 后台亮屏最小字段模式、息屏 alert-only 模式。
- `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java`
  - 息屏场景 stream 行为收敛。
- `bridge/mt5_gateway/server_v2.py`
  - 如需分离 alert-only / full-stream 消息语义，在这里落入口。

### 本次验证相关文件

- `app/src/test/java/com/binance/monitor/...`
  - 新增或补充 source/unit test，锁定节流与页面可见态行为。
- `.tmp/measure_android_resources.py`
  - 继续复用现有真机资源测量脚本。
- `CONTEXT.md`
  - 记录实施阶段和最新决策。

## 三、实施顺序

### 批次 A：前台主收益

1. 图表页 realtime tail 改成 `1s` 合并落图
2. 前台悬浮窗有持仓从 `500ms` 提到 `1s`
3. 图表页账户叠加层 debounce 放宽
4. 分析页只刷新当前可见区块

### 批次 B：后台 CPU/电量收缩

5. 后台亮屏只保留悬浮窗最小字段
6. 息屏退化为 alert-only

### 批次 C：回归和资源复测

7. 四组真机场景复测
8. 根据复测结果决定是否继续压缩分析页和悬浮窗节奏

## 四、任务清单

### Task 1: 图表页 realtime tail 改为 1 秒合并落图

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailSourceTest.java`

**目标：**
- 保留实时真值接收
- 把 UI 落图从“每个 `marketTick` 都推进”改成“最多每 `1s` 推进一次”

- [ ] **Step 1: 新增 realtime tail 合并窗口常量**

```java
// AppConstants.java
public static final long CHART_REALTIME_TAIL_UI_WINDOW_MS = 1_000L;
```

- [ ] **Step 2: 在图表页保存最后一次 realtime tail UI 落图时间**

```java
// MarketChartScreen.java
private long lastRealtimeTailUiAppliedAt;
private boolean realtimeTailWindowScheduled;
```

- [ ] **Step 3: 把现有立即 `post` 的落图逻辑改成窗口内只排一次**

```java
private void scheduleRealtimeTailRefresh(@Nullable KlineData latestKline) {
    if (latestKline == null || binding == null || !hasRealtimeTailSourceForChart()) {
        return;
    }
    pendingRealtimeTailKline = latestKline;
    long now = System.currentTimeMillis();
    long elapsed = now - lastRealtimeTailUiAppliedAt;
    if (elapsed >= AppConstants.CHART_REALTIME_TAIL_UI_WINDOW_MS) {
        realtimeTailDrainScheduled = false;
        mainHandler.post(realtimeTailDrainRunnable);
        return;
    }
    if (realtimeTailWindowScheduled) {
        return;
    }
    realtimeTailWindowScheduled = true;
    mainHandler.postDelayed(() -> {
        realtimeTailWindowScheduled = false;
        mainHandler.post(realtimeTailDrainRunnable);
    }, AppConstants.CHART_REALTIME_TAIL_UI_WINDOW_MS - elapsed);
}
```

- [ ] **Step 4: 在真正落图处更新最后一次 UI 应用时间**

```java
private void applyRealtimeChartTailNow(@NonNull KlineData latestKline) {
    lastRealtimeTailUiAppliedAt = System.currentTimeMillis();
    // 保持现有 applyDisplayCandles / renderInfoWithLatest / updateStateCount 不变
}
```

- [ ] **Step 5: 写 source test 锁定窗口期节流**

```java
@Test
public void realtimeTailShouldUseOneSecondUiWindow() {
    String source = read("app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
    assertTrue(source.contains("CHART_REALTIME_TAIL_UI_WINDOW_MS"));
    assertTrue(source.contains("lastRealtimeTailUiAppliedAt"));
}
```

- [ ] **Step 6: 运行最小验证**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRealtimeTailSourceTest"
.\gradlew.bat :app:assembleDebug
```

Expected:

- unit test PASS
- Debug APK 构建成功

**风险：**
- 图表页最新价、未闭合尾 K 最多多出约 `0.5s` 额外显示延迟。

**预期收益：**
- 前台图表页 CPU 与电量收益最高，是第一优先级。

**验证方法：**
- 真机图表页停留 `15min`
- 记录 `top` CPU 均值/峰值、`batterystats` UID mAh
- 肉眼检查尾 K、最新价、视口跟随、十字线不异常

### Task 2: 前台悬浮窗刷新从 500ms 提高到 1s

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/constants/AppConstants.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorRuntimePolicyHelperTest.java`

**目标：**
- 前台有持仓刷新 `500ms -> 1000ms`
- 前台无持仓进一步放慢到 `2000ms`

- [ ] **Step 1: 调整悬浮窗前台常量**

```java
// AppConstants.java
public static final long FLOATING_UPDATE_THROTTLE_MS = 1_000L;
public static final long FLOATING_UPDATE_IDLE_THROTTLE_MS = 2_000L;
```

- [ ] **Step 2: 保持后台亮屏/最小化节奏先不变**

```java
// 本批次只动前台节奏
public static final long FLOATING_UPDATE_BACKGROUND_THROTTLE_MS = 500L;
public static final long FLOATING_UPDATE_BACKGROUND_IDLE_THROTTLE_MS = 1_500L;
```

- [ ] **Step 3: 补策略测试**

```java
@Test
public void foregroundFloatingShouldUseOneSecondWhenHasPositions() {
    long delay = MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(true, true, false);
    assertEquals(1_000L, delay);
}

@Test
public void foregroundFloatingShouldUseTwoSecondsWhenIdle() {
    long delay = MonitorRuntimePolicyHelper.resolveFloatingRefreshThrottleMs(true, false, false);
    assertEquals(2_000L, delay);
}
```

- [ ] **Step 4: 运行最小验证**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorRuntimePolicyHelperTest"
.\gradlew.bat :app:assembleDebug
```

Expected:

- unit test PASS
- Debug APK 构建成功

**风险：**
- 悬浮窗价格和盈亏跳动变慢，体感从“更紧”变成“更稳”。

**预期收益：**
- 前台悬浮窗主线程压力接近减半，属于高收益低风险项。

**验证方法：**
- 前台悬浮窗停留 `15min`
- 检查价格、盈亏、最小化、还原、异常闪烁
- 记录 CPU 与 `batterystats`

### Task 3: 图表页账户叠加层 debounce 放宽

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartOverlayRefreshSourceTest.java`

**目标：**
- 把图表页账户叠加层 debounce 从 `120ms` 调到 `300ms`
- 减少持仓运行态触发的叠加层重算与重绑

- [ ] **Step 1: 调整 debounce 返回值**

```java
// MarketChartFragment.java
@Override
public long getChartOverlayRefreshDebounceMs() {
    return 300L;
}
```

- [ ] **Step 2: 补 source test 锁定节奏**

```java
@Test
public void chartOverlayDebounceShouldBeThreeHundredMs() {
    String source = read("app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java");
    assertTrue(source.contains("return 300L;"));
}
```

- [ ] **Step 3: 运行最小验证**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartOverlayRefreshSourceTest"
.\gradlew.bat :app:assembleDebug
```

Expected:

- unit test PASS
- Debug APK 构建成功

**风险：**
- 图上持仓线、挂单线、顶部持仓摘要最多延后 `180ms` 左右。

**预期收益：**
- 图表页 CPU 中高收益，通常次于 realtime tail 合并。

**验证方法：**
- 持仓变动、挂单变动、平仓后标注更新回归
- 图表页 `15min` CPU 对比

### Task 4: 分析页改为当前可见区块优先刷新

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsViewportRefreshSourceTest.java`

**目标：**
- 分析页只在区块进入视口后再刷新收益表、交易统计、交易记录
- 离屏区块只标记 dirty，不立即重算

- [ ] **Step 1: 在 Screen 和 BridgeActivity 中增加区块可见性判断方法**

```java
private boolean isViewMostlyVisible(@Nullable View target) {
    if (target == null || !target.isShown()) {
        return false;
    }
    Rect rect = new Rect();
    boolean visible = target.getGlobalVisibleRect(rect);
    if (!visible) {
        return false;
    }
    return rect.height() >= target.getHeight() / 3;
}
```

- [ ] **Step 2: 在 Host 中暴露区块可见性接口**

```java
boolean isReturnSectionVisible();
boolean isTradeStatsSectionVisible();
boolean isTradeRecordsSectionVisible();
```

- [ ] **Step 3: 在 RenderCoordinator 里只对可见区块立即执行刷新**

```java
if (sectionDiff.refreshReturnSection && host.isReturnSectionVisible()) {
    host.renderReturnStatsTable(host.getAllCurvePoints());
}
if (sectionDiff.refreshTradeStatsSection && host.isTradeStatsSectionVisible()) {
    host.bindTradeAnalytics(...);
}
if (sectionDiff.refreshTradeRecordsSection && host.isTradeRecordsSectionVisible()) {
    host.bindFilteredTrades(...);
}
```

- [ ] **Step 4: 为离屏区块保留 pending 状态，滚到可见时补渲染**

```java
// Screen.java onScrollChanged
if (deferredSecondaryRenderPending && renderCoordinator != null) {
    renderCoordinator.renderDeferredSnapshotSections();
}
```

- [ ] **Step 5: 补 source test**

```java
@Test
public void accountStatsShouldCheckSectionVisibilityBeforeRenderingHeavyBlocks() {
    String source = read("app/src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java");
    assertTrue(source.contains("isReturnSectionVisible"));
    assertTrue(source.contains("isTradeStatsSectionVisible"));
    assertTrue(source.contains("isTradeRecordsSectionVisible"));
}
```

- [ ] **Step 6: 运行最小验证**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsViewportRefreshSourceTest"
.\gradlew.bat :app:assembleDebug
```

Expected:

- unit test PASS
- Debug APK 构建成功

**风险：**
- 首次滚动到某个统计区块时会有一次延迟渲染。

**预期收益：**
- 分析页前台 CPU 明显下降，是前台第二批主收益项。

**验证方法：**
- 分析页 `15min` 复测
- 滚动到收益表、交易统计、交易记录时检查内容能即时补齐

### Task 5: 后台亮屏只保留悬浮窗最小字段

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceRuntimePolicySourceTest.java`

**目标：**
- 手机在桌面或其它界面、但仍亮屏显示悬浮窗时，只更新悬浮窗最小字段
- 暂停分析页和页面级重统计

- [ ] **Step 1: 在 MonitorService 增加后台亮屏最小字段模式判定**

```java
private boolean shouldUseMinimalFloatingMode() {
    return !AppForegroundTracker.getInstance().isForeground() && screenInteractive;
}
```

- [ ] **Step 2: 在 AccountStatsPreloadManager 中暂停后台亮屏全量抓取**

```java
if (!AppForegroundTracker.getInstance().isForeground() && !liveScreenActive) {
    fullSnapshotActive = false;
}
```

- [ ] **Step 3: 保证悬浮窗只从当前分钟快照和产品运行态拼数据**

```java
// MonitorFloatingCoordinator.java
// 保留 price / volume / amount / positionCount / signedLots / netPnl
```

- [ ] **Step 4: 补 source test**

```java
@Test
public void serviceShouldUseMinimalFloatingModeWhenBackgroundAndInteractive() {
    String source = read("app/src/main/java/com/binance/monitor/service/MonitorService.java");
    assertTrue(source.contains("shouldUseMinimalFloatingMode"));
}
```

- [ ] **Step 5: 运行最小验证**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceRuntimePolicySourceTest"
.\gradlew.bat :app:assembleDebug
```

Expected:

- unit test PASS
- Debug APK 构建成功

**风险：**
- 后台亮屏时不再维持分析页和账户页完整统计真值，回前台需要补齐。

**预期收益：**
- 后台亮屏 CPU/电量中高收益。

**验证方法：**
- 桌面悬浮窗 `15min` 复测
- 回前台后确认账户页、分析页能一次性补齐

### Task 6: 息屏退化为 alert-only

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Test: `app/src/test/java/com/binance/monitor/service/ScreenOffAlertOnlySourceTest.java`

**目标：**
- 熄屏后停止悬浮窗刷新和行情/账户最小字段更新
- 只保留异常提醒链

- [ ] **Step 1: 在熄屏回调中切断悬浮窗刷新资格**

```java
// MonitorFloatingCoordinator.java
void setScreenInteractive(boolean interactive) {
    if (!interactive) {
        mainHandler.removeCallbacks(floatingRefreshRunnable);
        floatingRefreshScheduled = false;
        return;
    }
    requestRefresh(true);
}
```

- [ ] **Step 2: 在 MonitorService 增加 screen-off alert-only 模式**

```java
private boolean shouldUseScreenOffAlertOnlyMode() {
    return !screenInteractive;
}
```

- [ ] **Step 3: 熄屏时不再请求市场最小字段和账户最小字段刷新**

```java
if (shouldUseScreenOffAlertOnlyMode()) {
    return;
}
```

- [ ] **Step 4: 服务端为后续 alert-only 保留单独消息语义入口**

```python
# server_v2.py
# 保留 abnormal diff / alerts 推送，不要求 marketTick 在 screen-off 客户端继续消费
```

- [ ] **Step 5: 补 source test**

```java
@Test
public void screenOffShouldStopFloatingRefreshAndKeepAlertOnly() {
    String source = read("app/src/main/java/com/binance/monitor/service/MonitorService.java");
    assertTrue(source.contains("shouldUseScreenOffAlertOnlyMode"));
}
```

- [ ] **Step 6: 运行最小验证**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.ScreenOffAlertOnlySourceTest"
.\gradlew.bat :app:assembleDebug
python -m pytest bridge/mt5_gateway/tests -q
```

Expected:

- Android source test PASS
- Debug APK 构建成功
- Bridge pytest PASS

**风险：**
- 息屏期间不再维持实时价格和实时持仓显示，这是有意收缩的能力边界。

**预期收益：**
- 后台 CPU/电量收益最大。

**验证方法：**
- 息屏 `15min` 复测
- 异常提醒仍能正常到达
- 亮屏和回前台后能恢复真值

### Task 7: 真机前后台四场景回归与资源复测

**Files:**
- Modify: `CONTEXT.md`
- Use: `.tmp/measure_android_resources.py`
- Output: `.tmp-foreground-measure-v3.json`
- Output: `.tmp-analysis-measure-v3.json`
- Output: `.tmp-background-bright-measure-v3.json`
- Output: `.tmp-background-screenoff-measure-v3.json`

**目标：**
- 用同一口径复测四个核心场景
- 输出本轮实施后的资源结果

- [ ] **Step 1: 安装最新 Debug APK**

Run:

```powershell
.\gradlew.bat :app:installDebug --rerun-tasks
```

Expected:

- 设备安装成功

- [ ] **Step 2: 前台图表页 `15min` 复测**

Run:

```powershell
python .tmp/measure_android_resources.py --mode foreground-chart --minutes 15 --output .tmp-foreground-measure-v3.json
```

- [ ] **Step 3: 前台分析页 `15min` 复测**

Run:

```powershell
python .tmp/measure_android_resources.py --mode foreground-analysis --minutes 15 --output .tmp-analysis-measure-v3.json
```

- [ ] **Step 4: 后台亮屏悬浮窗 `15min` 复测**

Run:

```powershell
python .tmp/measure_android_resources.py --mode background-bright --minutes 15 --output .tmp-background-bright-measure-v3.json
```

- [ ] **Step 5: 后台息屏 `15min` 复测**

Run:

```powershell
python .tmp/measure_android_resources.py --mode background-screenoff --minutes 15 --output .tmp-background-screenoff-measure-v3.json
```

- [ ] **Step 6: 更新 CONTEXT**

```markdown
- 2026-04-26 已完成 CPU/电量优化批次 A/B 的真机四场景复测，结果写入 v3 JSON。
```

**风险：**
- 设备状态、亮度、网络抖动会对单次结果有波动，必须和上轮同口径比较。

**预期收益：**
- 输出可用于继续决策的数据，不是功能收益项。

**验证方法：**
- 对比 v2 与 v3 的 CPU、mAh、PSS

## 五、统一验证矩阵

### 代码验证

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
python -m pytest bridge/mt5_gateway/tests -q
```

### 真机验证

1. 前台图表页停留 `15min`
2. 前台分析页停留 `15min`
3. 后台亮屏悬浮窗停留 `15min`
4. 后台息屏停留 `15min`
5. 回前台后检查：
   - 图表页尾 K 正常
   - 悬浮窗数据恢复
   - 分析页统计补齐
   - 异常提醒正常

## 六、完成定义

- 图表页 realtime tail 已改成 `1s` 合并落图
- 前台悬浮窗有持仓已改成 `1s`
- 图表页叠加层 debounce 已放宽
- 分析页已做到当前可见区块优先刷新
- 后台亮屏只保留悬浮窗最小字段
- 息屏已退化为 alert-only
- 四场景真机复测完成并写回 `CONTEXT.md`

## 七、推荐提交顺序

1. `feat: throttle chart realtime tail ui updates`
2. `feat: slow foreground floating refresh cadence`
3. `feat: debounce chart overlay refresh`
4. `feat: gate analysis refresh by visible sections`
5. `feat: reduce background runtime to minimal floating fields`
6. `feat: use alert-only mode when screen off`
7. `test: rerun device resource measurement after cpu power optimization`
