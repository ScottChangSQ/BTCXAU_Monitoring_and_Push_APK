# Analysis Tab Scroll Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 降低分析 tab 上下滑动时的主线程绘制、测量和布局成本，让滚动期间不再被多张图表全量 `onDraw()` 和长页面结构拖慢。

**Architecture:** 先用可验证的源码/布局测试锁住性能目标，再做两层收口：第一层把图表重计算从 `onDraw()` 前移到数据/尺寸变化时；第二层把长页面从一次性全量挂载改成按 section 延迟挂载。保持现有 XML/View 体系，不引入 Compose 或第三方图表库。

**Tech Stack:** Android Java、View 自绘、ViewBinding、RecyclerView、JUnit 源码/布局测试、Gradle。

---

## 当前判断

输入：分析 tab 已完成自动重绘门控，普通运行态/连接态轮询不再触发整页 `applySnapshot`。

处理流程：用户上下滑动 `NestedScrollView`，系统会持续绘制当前可见区域；可见区域内的自绘图表在 `onDraw()` 中重新遍历曲线/交易点并重建 `Path`、`RectF`、标签文本。

状态变化：滚动本身没有业务数据变化，但图表每帧重复做数据计算，长页面还会承受多张图表和多个 `RecyclerView wrap_content` 的测量成本。

输出：滑动掉帧、触摸跟手性差。

上下游影响：优化不能改变分析页展示口径、收益统计口径、交易统计口径，也不能重新打开之前已经收口的自动整页重绘。

## 优化顺序

1. 先做证据和保护测试，避免后续优化把刷新门控、模块顺序或图表数据口径改坏。
2. 再做图表 `RenderModel` 缓存，这是最小完整优化，风险小，收益直接。
3. 再合并图表 setter，减少一次数据绑定触发多次 `invalidate()`。
4. 再优化共享十字光标查找，把 O(n) 改成二分。
5. 最后处理长页面结构：先做次屏延迟挂载；如果仍卡，再把分析页迁移到 section RecyclerView。

## 不做的事

- 不引入降级采样来“看起来更流畅”，除非先证明全量点绘制在目标设备上仍超预算。
- 不把图表替换成第三方库。
- 不改收益、交易、历史修订号的业务口径。
- 不一次性重写 `AccountStatsScreen` / `AccountStatsBridgeActivity`，避免扩大风险。

---

### Task 1: 增加性能口径保护测试

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScrollPerformanceSourceTest.java`
- Modify: none
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScrollPerformanceSourceTest.java`

- [ ] **Step 1: 写源码测试，锁住图表不能在 onDraw 中做全量准备**

```java
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountStatsScrollPerformanceSourceTest {

    @Test
    public void analysisCurveChartsShouldUsePreparedRenderModelsOutsideOnDraw() throws Exception {
        assertPreparedRenderModel("EquityCurveView.java", "EquityCurveRenderModel");
        assertPreparedRenderModel("PositionRatioChartView.java", "PositionRatioRenderModel");
        assertPreparedRenderModel("DrawdownChartView.java", "DrawdownRenderModel");
        assertPreparedRenderModel("DailyReturnChartView.java", "DailyReturnRenderModel");
    }

    @Test
    public void analysisCurveChartsShouldNotInvalidateMultipleTimesPerProjectionBind() throws Exception {
        String helper = readUtf8("app/src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java",
                "src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java");

        assertTrue(helper.contains("binding.equityCurveView.setRenderData("));
        assertTrue(helper.contains("binding.positionRatioChartView.setRenderData("));
        assertTrue(helper.contains("binding.drawdownChartView.setRenderData("));
        assertTrue(helper.contains("binding.dailyReturnChartView.setRenderData("));
        assertFalse(helper.contains("binding.equityCurveView.setViewport("));
        assertFalse(helper.contains("binding.positionRatioChartView.setViewport("));
        assertFalse(helper.contains("binding.drawdownChartView.setViewport("));
        assertFalse(helper.contains("binding.dailyReturnChartView.setViewport("));
    }

    @Test
    public void secondaryCurveHighlightLookupShouldUseBinarySearch() throws Exception {
        assertTrue(readUtf8("app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java",
                "src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java")
                .contains("CurvePointBinarySearch.nearestCurvePointIndex("));
        assertTrue(readUtf8("app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java",
                "src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java")
                .contains("CurvePointBinarySearch.nearestDrawdownPointIndex("));
        assertTrue(readUtf8("app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java",
                "src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java")
                .contains("CurvePointBinarySearch.nearestDailyReturnPointIndex("));
    }

    private static void assertPreparedRenderModel(String fileName, String modelName) throws Exception {
        String source = readUtf8("app/src/main/java/com/binance/monitor/ui/account/" + fileName,
                "src/main/java/com/binance/monitor/ui/account/" + fileName);
        String onDraw = source.substring(source.indexOf("protected void onDraw"));
        assertTrue(fileName + " 必须保留预计算渲染模型", source.contains("private " + modelName + " renderModel"));
        assertFalse(fileName + " 的 onDraw 不应再全量遍历原始 points", onDraw.contains("for (int i = 0; i < points.size(); i++)"));
        assertFalse(fileName + " 的 onDraw 不应再直接遍历原始 points", onDraw.contains("for (CurvePoint point : points)"));
    }

    private static String readUtf8(String... candidates) throws Exception {
        Path workingDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String candidate : candidates) {
            Path path = workingDir.resolve(candidate).normalize();
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
            }
        }
        throw new IllegalStateException("找不到源码文件");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsScrollPerformanceSourceTest"
```

Expected: FAIL，提示缺少 `RenderModel`、仍在使用 `setViewport` 或缺少二分查找。

---

### Task 2: 建立曲线点二分查找工具

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/CurvePointBinarySearch.java`
- Create: `app/src/test/java/com/binance/monitor/ui/account/CurvePointBinarySearchTest.java`

- [ ] **Step 1: 写单元测试**

```java
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CurvePointBinarySearchTest {

    @Test
    public void nearestCurvePointIndexShouldReturnClosestTimestamp() {
        List<CurvePoint> points = Arrays.asList(
                point(1000L),
                point(2000L),
                point(4000L)
        );

        assertEquals(0, CurvePointBinarySearch.nearestCurvePointIndex(points, 1200L));
        assertEquals(1, CurvePointBinarySearch.nearestCurvePointIndex(points, 2600L));
        assertEquals(2, CurvePointBinarySearch.nearestCurvePointIndex(points, 3900L));
        assertEquals(0, CurvePointBinarySearch.nearestCurvePointIndex(points, 100L));
        assertEquals(2, CurvePointBinarySearch.nearestCurvePointIndex(points, 8000L));
    }

    @Test
    public void nearestCurvePointIndexShouldHandleEmptyInput() {
        assertEquals(-1, CurvePointBinarySearch.nearestCurvePointIndex(Collections.emptyList(), 1000L));
    }

    private static CurvePoint point(long timestamp) {
        return new CurvePoint(timestamp, 100d, 100d, 0d);
    }
}
```

- [ ] **Step 2: 实现工具类**

```java
package com.binance.monitor.ui.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

final class CurvePointBinarySearch {

    private CurvePointBinarySearch() {
    }

    static int nearestCurvePointIndex(@Nullable List<CurvePoint> points, long timestamp) {
        return nearestIndex(points == null ? 0 : points.size(), timestamp,
                index -> points.get(index).getTimestamp());
    }

    static int nearestDrawdownPointIndex(@Nullable List<CurveAnalyticsHelper.DrawdownPoint> points, long timestamp) {
        return nearestIndex(points == null ? 0 : points.size(), timestamp,
                index -> points.get(index).getTimestamp());
    }

    static int nearestDailyReturnPointIndex(@Nullable List<CurveAnalyticsHelper.DailyReturnPoint> points, long timestamp) {
        return nearestIndex(points == null ? 0 : points.size(), timestamp,
                index -> points.get(index).getTimestamp());
    }

    private static int nearestIndex(int size, long timestamp, @NonNull TimestampReader reader) {
        if (size <= 0) {
            return -1;
        }
        int left = 0;
        int right = size - 1;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            long midTs = reader.timestampAt(mid);
            if (midTs < timestamp) {
                left = mid + 1;
            } else if (midTs > timestamp) {
                right = mid - 1;
            } else {
                return mid;
            }
        }
        if (left >= size) {
            return size - 1;
        }
        if (right < 0) {
            return 0;
        }
        long leftDiff = Math.abs(reader.timestampAt(left) - timestamp);
        long rightDiff = Math.abs(reader.timestampAt(right) - timestamp);
        return leftDiff < rightDiff ? left : right;
    }

    private interface TimestampReader {
        long timestampAt(int index);
    }
}
```

- [ ] **Step 3: 运行工具测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.CurvePointBinarySearchTest"
```

Expected: PASS。

---

### Task 3: 主曲线图改为预计算 RenderModel

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScrollPerformanceSourceTest.java`

- [ ] **Step 1: 在 `EquityCurveView` 增加一次性绑定入口**

新增字段和入口：

```java
private EquityCurveRenderModel renderModel = EquityCurveRenderModel.empty();

public void setRenderData(@Nullable List<CurvePoint> data,
                          long startTs,
                          long endTs,
                          double baseBalanceValue,
                          long drawdownStart,
                          long drawdownEnd,
                          double peakBalance,
                          double valleyBalance) {
    points.clear();
    if (data != null) {
        points.addAll(data);
        points.sort((left, right) -> Long.compare(left.getTimestamp(), right.getTimestamp()));
    }
    viewportStartTs = Math.max(0L, startTs);
    viewportEndTs = Math.max(viewportStartTs + 1L, endTs);
    baseBalance = Math.max(1e-9, baseBalanceValue);
    drawdownStartTs = Math.max(0L, drawdownStart);
    drawdownEndTs = Math.max(0L, drawdownEnd);
    drawdownPeakBalance = peakBalance;
    drawdownValleyBalance = valleyBalance;
    tooltipExtraLines.clear();
    tooltipPointOverride = null;
    highlightedIndex = -1;
    highlightedXRatio = -1f;
    longPressing = false;
    rebuildRenderModel();
    dispatchHighlightedPoint();
    invalidate();
}
```

- [ ] **Step 2: 在尺寸变化时重建模型**

```java
@Override
protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    rebuildRenderModel();
}
```

- [ ] **Step 3: 把 onDraw 全量计算迁移到 `rebuildRenderModel()`**

实现要求：

```java
private void rebuildRenderModel() {
    int width = getWidth();
    int height = getHeight();
    if (width <= 0 || height <= 0 || points.size() < 2 || masked) {
        renderModel = EquityCurveRenderModel.empty();
        return;
    }
    // 在这里计算 chartLeft/chartTop/chartRight/chartBottom、chartMin/chartMax、
    // chartStartTs/chartEndTs、equityPath、balancePath、回撤高亮区域和标签文本。
    // onDraw 只能读取 renderModel，不再遍历原始 points 重建曲线。
}
```

`onDraw()` 保留：

```java
drawGrid(canvas, renderModel.chartLeft, renderModel.chartTop, renderModel.chartRight, renderModel.chartBottom);
drawAxes(canvas, renderModel.chartLeft, renderModel.chartTop, renderModel.chartRight, renderModel.chartBottom);
canvas.drawPath(renderModel.balancePath, balancePaint);
canvas.drawPath(renderModel.equityPath, equityPaint);
```

- [ ] **Step 4: 旧 setter 改成兼容入口**

`setPoints`、`setViewport`、`setBaseBalance`、`setDrawdownHighlight` 保留，但内部调用 `rebuildRenderModel()` 后只在必要时 `invalidate()`，后续统一由 Task 7 改到 `setRenderData()`。

- [ ] **Step 5: 运行测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsScrollPerformanceSourceTest"
```

Expected: 仍可能失败，因为其他图表还没改；但 `EquityCurveView` 对应断言应通过。

---

### Task 4: 三张附图改为预计算 RenderModel

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScrollPerformanceSourceTest.java`

- [ ] **Step 1: `PositionRatioChartView` 增加 `PositionRatioRenderModel`**

要求：

```java
private PositionRatioRenderModel renderModel = PositionRatioRenderModel.empty();

public void setRenderData(@Nullable List<CurvePoint> source, long startTs, long endTs) {
    points.clear();
    if (source != null) {
        points.addAll(source);
        points.sort((left, right) -> Long.compare(left.getTimestamp(), right.getTimestamp()));
    }
    viewportStartTs = Math.max(0L, startTs);
    viewportEndTs = Math.max(viewportStartTs + 1L, endTs);
    highlightedIndex = -1;
    highlightedXRatio = -1f;
    longPressing = false;
    rebuildRenderModel();
    invalidate();
}
```

`rebuildRenderModel()` 预生成 `linePath`、`fillPath`、`zeroY`、`peakX`、`peakY`、标签文本。

- [ ] **Step 2: `DrawdownChartView` 增加 `DrawdownRenderModel`**

同样把 `minDrawdown`、`linePath`、`fillPath`、`zeroY`、`valleyX`、`valleyY`、标签文本移出 `onDraw()`。

- [ ] **Step 3: `DailyReturnChartView` 增加 `DailyReturnRenderModel`**

将每根柱子的 `RectF`、颜色方向、`maxAbs`、`zeroY`、X 轴标签文本提前计算。`onDraw()` 只遍历 `renderModel.bars`，不遍历原始 `points`。

- [ ] **Step 4: 三张附图的最近点查找改为二分**

替换：

```java
private int findNearestIndexByTimestamp(long timestamp) {
    return CurvePointBinarySearch.nearestCurvePointIndex(points, timestamp);
}
```

`DrawdownChartView` 使用 `nearestDrawdownPointIndex`，`DailyReturnChartView` 使用 `nearestDailyReturnPointIndex`。

- [ ] **Step 5: 运行测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.CurvePointBinarySearchTest" --tests "com.binance.monitor.ui.account.AccountStatsScrollPerformanceSourceTest"
```

Expected: PASS，或只剩 Task 7 的 `setRenderData` 断言失败。

---

### Task 5: 交易统计图表做轻量缓存

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/TradeWeekdayBarChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java`
- Create: `app/src/test/java/com/binance/monitor/ui/account/TradeStatsChartRenderCacheSourceTest.java`

- [ ] **Step 1: 写源码测试**

```java
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TradeStatsChartRenderCacheSourceTest {

    @Test
    public void tradeStatsChartsShouldKeepPreparedDrawItems() throws Exception {
        assertPrepared("TradePnlBarChartView.java", "List<BarDrawItem> drawItems");
        assertPrepared("TradeWeekdayBarChartView.java", "List<BarDrawItem> drawItems");
        assertPrepared("TradeDistributionScatterView.java", "List<ScatterDrawItem> drawItems");
        assertPrepared("HoldingDurationDistributionView.java", "List<BucketDrawItem> drawItems");
    }

    private static void assertPrepared(String fileName, String marker) throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/binance/monitor/ui/account/" + fileName
        )), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String onDraw = source.substring(source.indexOf("protected void onDraw"));
        assertTrue(fileName + " 必须保留绘制缓存", source.contains(marker));
        assertFalse(fileName + " onDraw 不应每次重新计算数据范围", onDraw.contains("maxPositive"));
        assertFalse(fileName + " onDraw 不应每次重新计算数据范围", onDraw.contains("chartMin"));
    }
}
```

- [ ] **Step 2: 各图表在 `setEntries` / `setPoints` / `setBuckets` 和 `onSizeChanged` 中重建缓存**

要求：

```java
@Override
protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    rebuildDrawItems();
}
```

`onDraw()` 只消费 `drawItems`，不再重新求最大值、最小值和坐标。

- [ ] **Step 3: 运行测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.TradeStatsChartRenderCacheSourceTest"
```

Expected: PASS。

---

### Task 6: 合并曲线绑定入口，减少重复 invalidate

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsCurveRenderHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScrollPerformanceSourceTest.java`

- [ ] **Step 1: `AccountStatsCurveRenderHelper.applyPreparedProjection()` 改用一次性绑定**

替换连续 setter：

```java
binding.equityCurveView.setRenderData(
        effectivePoints,
        curveProjection.getViewportStartTs(),
        curveProjection.getViewportEndTs(),
        curveBaseBalance,
        drawdownSegment == null ? 0L : drawdownSegment.getStartTs(),
        drawdownSegment == null ? 0L : drawdownSegment.getEndTs(),
        drawdownSegment == null ? 0d : drawdownSegment.getPeakBalance(),
        drawdownSegment == null ? 0d : drawdownSegment.getValleyBalance()
);
```

三张附图：

```java
binding.positionRatioChartView.setRenderData(effectivePoints,
        curveProjection.getViewportStartTs(),
        curveProjection.getViewportEndTs());
binding.drawdownChartView.setRenderData(drawdownPoints,
        curveProjection.getViewportStartTs(),
        curveProjection.getViewportEndTs());
binding.dailyReturnChartView.setRenderData(dailyReturnPoints,
        curveProjection.getViewportStartTs(),
        curveProjection.getViewportEndTs());
```

- [ ] **Step 2: `renderImmediate()` 同步改用一次性绑定**

要求与 Step 1 相同，避免首次渲染和后台投影渲染走两套路径。

- [ ] **Step 3: 运行测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsScrollPerformanceSourceTest"
```

Expected: PASS。

---

### Task 7: 次屏模块延迟挂载策略收口

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsSecondarySectionAttachSourceTest.java`

- [ ] **Step 1: 保留首帧后挂载，但拆分为“可见前挂载”**

当前已有 `registerFirstFrameCompletionListener()` 和 `markFirstFrameCompleted()`。保留首帧保护，同时新增：

```java
private boolean tradeStatsSectionAttached = false;

private void maybeAttachTradeStatsSectionForScroll() {
    if (tradeStatsSectionAttached || binding == null) {
        return;
    }
    int scrollY = binding.scrollAccountStats.getScrollY();
    int viewportBottom = scrollY + binding.scrollAccountStats.getHeight();
    int sectionTop = binding.cardTradeStatsSection.getTop();
    int preloadDistance = Math.round(getResources().getDisplayMetrics().density * 360f);
    if (sectionTop <= viewportBottom + preloadDistance) {
        tradeStatsSectionAttached = true;
        binding.cardTradeStatsSection.setVisibility(View.VISIBLE);
        if (renderCoordinator != null) {
            renderCoordinator.refreshTradeStats();
        }
    }
}
```

- [ ] **Step 2: 在滚动监听和首帧完成后触发**

```java
binding.scrollAccountStats.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) ->
        maybeAttachTradeStatsSectionForScroll());
```

`markFirstFrameCompleted()` 中调用一次 `maybeAttachTradeStatsSectionForScroll()`。

- [ ] **Step 3: 更新源码测试**

`AccountStatsSecondarySectionAttachSourceTest` 增加断言：

```java
assertTrue(screenSource.contains("maybeAttachTradeStatsSectionForScroll()"));
assertTrue(screenSource.contains("tradeStatsSectionAttached"));
assertTrue(bridgeSource.contains("maybeAttachTradeStatsSectionForScroll()"));
assertTrue(bridgeSource.contains("tradeStatsSectionAttached"));
```

- [ ] **Step 4: 运行测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsSecondarySectionAttachSourceTest"
```

Expected: PASS。

---

### Task 8: 真机/模拟器最小验证

**Files:**
- Modify: none

- [ ] **Step 1: 跑直接相关单元测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsScrollPerformanceSourceTest" --tests "com.binance.monitor.ui.account.CurvePointBinarySearchTest" --tests "com.binance.monitor.ui.account.TradeStatsChartRenderCacheSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsSecondarySectionAttachSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest"
```

Expected: PASS。

- [ ] **Step 2: 构建 Debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: 真机观察日志**

操作：

1. 打开 app。
2. 进入分析 tab。
3. 等待历史数据渲染完成。
4. 上下快速滑动 5 次。
5. 长按曲线并横向拖动 5 秒。

观察：

```powershell
adb logcat -d | findstr "account_render"
```

通过标准：

- 普通上下滑动期间不应出现新的 `apply_curve_range` / `refresh_trade_stats` 连续刷屏。
- 长按拖动期间不应有明显卡死。
- 分析页模块顺序仍为：核心统计 -> 收益统计 -> 净值/结余曲线 -> 交易统计。

---

## 如果 Task 1-8 后仍卡顿

进入第二阶段结构改造：将 `content_account_stats.xml` 中 `NestedScrollView + LinearLayout` 改为单个 `RecyclerView` section 列表。

建议新增：

- `app/src/main/java/com/binance/monitor/ui/account/AnalysisSectionAdapter.java`
- `app/src/main/java/com/binance/monitor/ui/account/AnalysisSectionModel.java`
- `app/src/main/res/layout/item_analysis_stats_summary.xml`
- `app/src/main/res/layout/item_analysis_return_stats.xml`
- `app/src/main/res/layout/item_analysis_curve.xml`
- `app/src/main/res/layout/item_analysis_trade_stats.xml`

迁移原则：

- 每个 section 一个 ViewHolder。
- 曲线 section 只在 ViewHolder attach 后绑定图表数据。
- 交易统计 section 只在滚到附近时绑定。
- 不再在 `RecyclerView` item 里嵌套可滚动 `RecyclerView wrap_content`；小指标优先用静态 LinearLayout/Grid 或固定高度列表。

第二阶段必须单独开计划，不和 Task 1-8 混在一次改动里。

## 验收标准

- 分析页业务数据展示不变。
- 连接态轮询仍不会触发分析页整页自动重绘。
- 图表 `onDraw()` 不再承担全量数据计算。
- 一次曲线投影绑定中，每张曲线图只触发一次主要 `invalidate()`。
- 共享十字光标最近点查找使用二分。
- 直接相关单元测试通过。
- 真机滑动日志没有重复渲染刷屏；若无法真机验证，最终说明必须标注“未验证真机帧率”。

