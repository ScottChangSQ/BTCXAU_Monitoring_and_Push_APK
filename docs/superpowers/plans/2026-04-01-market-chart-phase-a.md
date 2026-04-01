# Market Chart Phase A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修正行情持仓页的 K 线显示、隐私隐藏、默认视口、缩放手势和右上角延迟显示，使其符合第 1 批确认方案。

**Architecture:** 继续复用 `MarketChartActivity` 与 `KlineChartView` 这套现有结构，只新增一层轻量显示规则来统一控制隐私关闭时的图层可见性。交互层面保持现有视口模型，但统一默认停靠位、右侧留白与越界判断，并在 `KlineChartView` 内补上斜向整体缩放和新的图层绘制规则。

**Tech Stack:** Java、XML、ViewBinding、JUnit4、Android View 自绘、Gradle

---

### Task 1: 行情持仓隐私规则与整页遮罩退场

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/java/com/binance/monitor/util/SensitiveDisplayMasker.java`

- [ ] **Step 1: 补页面级失败验证思路并锁定行为**

把下面这组行为写入实现注释和测试目标，作为后续实现基线：

```text
小眼睛开启：K 线叠加层正常显示，当前持仓模块正常显示。
小眼睛关闭：K 线本体继续显示；持仓/挂单/止盈止损/成本线隐藏；
异常点继续显示；当前持仓模块的账户号、数量、价格、盈亏、收益率改成 *。
```

- [ ] **Step 2: 让 `SensitiveDisplayMasker` 补齐当前持仓模块所需的打码入口**

在 `SensitiveDisplayMasker` 中补齐统一方法，避免 `MarketChartActivity` 自己拼 `*`：

```java
public static String maskAccountId(String text, boolean masked) {
    return masked ? MASK_TEXT : safeText(text);
}

public static String maskSignedValue(String text, boolean masked) {
    return masked ? MASK_TEXT : safeText(text);
}
```

- [ ] **Step 3: 让 `KlineChartView` 接受一组轻量显示开关**

在 `KlineChartView` 新增集中入口，而不是让 Activity 到处传空列表：

```java
public void setOverlayVisibility(boolean showPositionAnnotations,
                                 boolean showPendingAnnotations,
                                 boolean showAggregateCostAnnotation) {
    this.showPositionAnnotations = showPositionAnnotations;
    this.showPendingAnnotations = showPendingAnnotations;
    this.showAggregateCostAnnotation = showAggregateCostAnnotation;
    invalidate();
}
```

- [ ] **Step 4: 把 `MarketChartActivity` 的整页遮罩改成按区域隐藏**

删除 `applyPrivacyMaskState()` 对整页遮罩的依赖，改为只控制 K 线叠加层和当前持仓模块：

```java
private void applyPrivacyMaskState() {
    boolean masked = SensitiveDisplayMasker.isEnabled(this);
    boolean showSensitiveOverlay = !masked;
    binding.klineChartView.setOverlayVisibility(
            showSensitiveOverlay,
            showSensitiveOverlay,
            showSensitiveOverlay
    );
    updateChartPositionPanelMasked(masked);
}
```

- [ ] **Step 5: 清理布局中的整页隐私遮罩**

从 `activity_market_chart.xml` 移除 `layoutChartPrivacyMask`，保留原有 K 线与当前持仓结构，避免后续误用。

- [ ] **Step 6: 运行受影响单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.*"`

Expected: 现有图表相关单测通过，没有因为新增显示开关导致编译失败。

### Task 2: 默认视口、右滑边界与斜向整体缩放

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartViewportMath.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/ChartViewportMathTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/KlineViewportHelperTest.java`

- [ ] **Step 1: 先补失败测试，锁定“右滑不过早隐藏”和“默认停靠位”**

在 `ChartViewportMathTest` 里补两组断言：

```java
@Test
public void latestCandleRemainsVisibleUntilCrossingPriceAxis() {
    float x = ChartViewportMath.projectCandleCenterX(700f, 10f, 111f, 99f);
    assertFalse(ChartViewportMath.isOutOfBounds(x, 700f, 0.2f));
}

@Test
public void defaultDockingLeavesReferenceBlankOnInitialViewport() {
    assertEquals(12.5f, ChartViewportMath.resolveDefaultRightBlankSlots(), 0.0001f);
}
```

- [ ] **Step 2: 运行测试确认先失败或不完整**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartViewportMathTest" --tests "com.binance.monitor.ui.chart.KlineViewportHelperTest"`

Expected: 至少有一条新增断言失败，说明当前逻辑还未满足目标。

- [ ] **Step 3: 统一默认停靠位和越界判断**

在 `ChartViewportMath` 中补统一常量与方法，让默认停靠和越界共用一套槽位定义：

```java
public static float resolveDefaultRightBlankSlots() {
    return DEFAULT_DOCKING_SLOTS;
}

public static boolean isOutOfBounds(float candleCenterX, float priceRectRight, float tolerancePx) {
    return candleCenterX > priceRectRight + tolerancePx;
}
```

- [ ] **Step 4: 让 `KlineChartView` 初始化到参考视口**

在首次 `setCandles()` 时设置默认 `candleWidth` 与 `offsetCandles`，保证单屏密度更接近参考图：

```java
private void resetViewportToDefault() {
    candleWidth = dp(1.15f);
    verticalScale = 1f;
    offsetCandles = 0f;
    clampOffset();
}
```

- [ ] **Step 5: 让斜向双指触发横纵一起缩放**

把 `ScaleListener.onScale()` 改成三路分支：

```java
boolean verticalDominant = absY > absX * 1.12f;
boolean horizontalDominant = absX > absY * 1.12f;
boolean diagonal = !verticalDominant && !horizontalDominant;
```

并在 `diagonal` 分支同时更新：

```java
verticalScale = clamp(verticalScale * smoothYScale, MIN_VERTICAL_SCALE, MAX_VERTICAL_SCALE);
candleWidth = clamp(candleWidth * smoothScale, minWidth, maxWidth);
```

- [ ] **Step 6: 重新运行视口测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartViewportMathTest" --tests "com.binance.monitor.ui.chart.KlineViewportHelperTest"`

Expected: 全部 PASS。

### Task 3: 异常点形态、成本线文案和十字光标颜色

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilder.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilderTest.java`

- [ ] **Step 1: 先补异常点强度测试**

在 `AbnormalAnnotationOverlayBuilderTest` 中增加“次数更多时强度更高”的断言：

```java
assertTrue(highCount.intensity > lowCount.intensity);
assertEquals(3, highCount.count);
```

- [ ] **Step 2: 运行异常点测试确认基线**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.AbnormalAnnotationOverlayBuilderTest"`

Expected: 当前测试通过或新增断言失败，但能定位到异常点强度输出逻辑。

- [ ] **Step 3: 把异常点绘制从圆点改成竖向胶囊**

在 `KlineChartView.drawOverlayAnnotations()` 中对异常标注使用 `drawRoundRect`，高度随 `annotation.intensity` 增长：

```java
float capsuleHalfWidth = selected ? dp(2.4f) : dp(2.0f);
float capsuleHalfHeight = dp(2.4f) + dp(6.5f) * annotation.intensity;
RectF capsule = new RectF(
        x - capsuleHalfWidth,
        y - capsuleHalfHeight,
        x + capsuleHalfWidth,
        y + capsuleHalfHeight
);
canvas.drawRoundRect(capsule, capsuleHalfWidth, capsuleHalfWidth, overlayPointPaint);
```

- [ ] **Step 4: 改成本线表达**

把 `drawAggregateCostAnnotation()` 改成“更细线 + 中央文案”，移除右侧标签：

```java
String hint = "成本线：" + text;
aggregateCostHintTextPaint.setTextAlign(Paint.Align.CENTER);
canvas.drawText(hint, priceRect.centerX(), hintBaseline, aggregateCostHintTextPaint);
```

- [ ] **Step 5: 改十字光标弹窗里的涨跌金额红绿**

在 `drawCandlePopup()` 内对“价格变动”行单独挑出颜色：

```java
Paint valuePaint = i == 5 ? popupDeltaPaintFor(value) : popupTextPaint;
canvas.drawText(value, valueX, baseline, valuePaint);
```

- [ ] **Step 6: 运行图表单测**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.*"`

Expected: 图表相关单测全部 PASS。

### Task 4: 右上角延迟显示与当前持仓模块打码接线

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`

- [ ] **Step 1: 记录每次请求的起止时间**

在 `requestKlines(boolean allowCancelRunning, boolean autoRefresh)` 中记录请求开始时间：

```java
final long requestStartedAtMs = SystemClock.elapsedRealtime();
```

成功回到主线程后保存：

```java
lastSuccessfulRequestLatencyMs = Math.max(0L, SystemClock.elapsedRealtime() - requestStartedAtMs);
```

- [ ] **Step 2: 改右上角倒计时文案**

把 `updateRefreshCountdownText()` 改成：

```java
String latencyText = lastSuccessfulRequestLatencyMs >= 0L
        ? (lastSuccessfulRequestLatencyMs + "ms")
        : "--ms";
binding.tvChartRefreshCountdown.setText(remainSeconds + "秒/" + periodSeconds + "秒 " + latencyText);
```

- [ ] **Step 3: 统一当前持仓模块打码**

在更新当前持仓模块时，把账户号、数量、价格、盈亏、收益率都走 `SensitiveDisplayMasker`：

```java
String pnlText = SensitiveDisplayMasker.maskSignedValue(formatSignedUsd(totalPnl), masked);
String qtyText = SensitiveDisplayMasker.maskQuantity(formatQuantity(qty), masked);
String priceText = SensitiveDisplayMasker.maskPrice(FormatUtils.formatPrice(price), masked);
```

- [ ] **Step 4: 运行 App 单测与构建**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: PASS

Run: `.\gradlew.bat :app:assembleDebug -x lint`

Expected: BUILD SUCCESSFUL

### Task 5: 文档与上下文收口

**Files:**
- Modify: `CONTEXT.md`
- Modify: `ARCHITECTURE.md`
- Modify: `README.md`

- [ ] **Step 1: 更新 `CONTEXT.md`**

补充第 1 批已完成内容、下一批入口和关键决定。

- [ ] **Step 2: 仅在需要时更新 `ARCHITECTURE.md` 与 `README.md`**

如果新增了显示规则入口、视口逻辑职责或运行验证命令变化，再同步更新。

- [ ] **Step 3: 最终验证**

Run: `.\gradlew.bat :app:testDebugUnitTest`

Expected: PASS

Run: `.\gradlew.bat :app:assembleDebug -x lint`

Expected: BUILD SUCCESSFUL
