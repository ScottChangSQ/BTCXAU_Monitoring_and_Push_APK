# Overlay And Chart Text Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 删除悬浮窗单产品卡片里的单独盈亏金额，并删除 K 线图左上角 OHLC 文本。

**Architecture:** 这次只收口两条现有 UI 主链，不新增中间层，也不改数据模型。悬浮窗标题改成只输出“产品名 | 手数”，K 线图主图阶段停止调用 OHLC 绘制函数，同时同步删改约束测试和项目记录。

**Tech Stack:** Android Java、JUnit4、Gradle、XML/Markdown 文档

---

### Task 1: 收口悬浮窗产品卡片标题

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatterTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowManagerTitleSourceTest.java`

- [ ] **Step 1: 先写失败测试，锁定“标题只保留产品名和手数”**

```java
@Test
public void formatCardTitleShouldOnlyKeepAssetAndLots() {
    String title = FloatingWindowTextFormatter.formatCardTitle("BTC", -0.10d, -1250d, true, false);

    assertEquals("BTC | -0.10手", title);
}
```

- [ ] **Step 2: 运行定向测试，确认旧实现仍在输出第二行盈亏而失败**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingWindowTextFormatterTest"`
Expected: `formatCardTitle...` 相关断言失败，实际值仍带 `\n` 和盈亏金额

- [ ] **Step 3: 用最小实现删除第二行盈亏，并移除标题里的盈亏着色逻辑**

```java
static String formatCardTitle(String label,
                              double totalLots,
                              double totalPnl,
                              boolean hasPosition,
                              boolean masked) {
    String safeLabel = label == null ? "" : label.trim();
    return hasPosition ? safeLabel + " | " + formatLotsText(totalLots) : safeLabel;
}
```

```java
private CharSequence buildStyledCardTitle(FloatingSymbolCardData card,
                                          UiPaletteManager.Palette palette,
                                          boolean masked) {
    String label = card == null || card.getLabel() == null ? "" : card.getLabel().trim();
    double totalLots = card == null ? 0d : card.getTotalLots();
    boolean hasPosition = card != null && card.hasPosition();
    String lotsText = hasPosition ? FloatingWindowTextFormatter.formatLotsText(totalLots) : "";
    String title = FloatingWindowTextFormatter.formatCardTitle(label, totalLots, 0d, hasPosition, masked);
    SpannableStringBuilder styled = new SpannableStringBuilder(title);
    int lotsStart = hasPosition ? title.lastIndexOf(lotsText) : -1;
    int lotsEnd = lotsStart < 0 ? -1 : Math.min(title.length(), lotsStart + lotsText.length());
    if (lotsStart < lotsEnd) {
        int lotsColor = resolvePnlColor(totalLots, true);
        styled.setSpan(new ForegroundColorSpan(lotsColor),
                lotsStart,
                lotsEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    return styled;
}
```

- [ ] **Step 4: 更新源码约束测试，确认不再要求标题里保留盈亏行着色**

```java
@Test
public void titleViewShouldOnlyColorLotsInCompactTitle() throws Exception {
    assertTrue(source.contains("String lotsText = hasPosition ? FloatingWindowTextFormatter.formatLotsText(totalLots) : \"\";"));
    assertTrue(source.contains("styled.setSpan(new ForegroundColorSpan(lotsColor)"));
    assertFalse(source.contains("styled.setSpan(new ForegroundColorSpan(pnlColor)"));
}
```

- [ ] **Step 5: 重跑悬浮窗相关测试**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingWindowTextFormatterTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerTitleSourceTest"`
Expected: `BUILD SUCCESSFUL`

### Task 2: 删除 K 线图左上角 OHLC 文本

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`

- [ ] **Step 1: 先补失败测试，锁定主图不再调用 OHLC 绘制**

```java
@Test
public void pricePaneShouldNotDrawOhlcInfoText() throws Exception {
    assertFalse(source.contains("drawPriceOhlcInfo(canvas, infoIndex);"));
    assertFalse(source.contains("private void drawPriceOhlcInfo(Canvas canvas, int index) {"));
}
```

- [ ] **Step 2: 运行 K 线源码测试，确认旧实现仍然失败**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest"`
Expected: `pricePaneShouldNotDrawOhlcInfoText` 失败，因为源码仍包含 OHLC 绘制

- [ ] **Step 3: 删除主图中的 OHLC 调用和方法实现**

```java
int infoIndex = resolveInfoIndex(end);
if (showBoll) {
    drawSeries(canvas, bollMid, start, end, visiblePriceMin, visiblePriceMax, priceRect, bollMidPaint, drawStep);
```

- [ ] **Step 4: 重跑图表源码测试**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest"`
Expected: `BUILD SUCCESSFUL`

### Task 3: 更新项目记录并做最终验证

**Files:**
- Modify: `CONTEXT.md`

- [ ] **Step 1: 在 CONTEXT.md 顶部补一条极简记录**

```md
- 最新续做已按用户要求删除两处图表/悬浮窗冗余文案：悬浮窗产品卡片不再单独显示第二行盈亏金额，K 线图左上角 OHLC 文本已停止绘制；对应悬浮窗与 K 线源码约束测试、`assembleDebug` 将作为本轮验证基线。
```

- [ ] **Step 2: 跑最终验证命令**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingWindowTextFormatterTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerTitleSourceTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" compileDebugJavaWithJavac assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 如设备在线，覆盖安装调试包**

Run: `adb install -r app\\build\\outputs\\apk\\debug\\app-debug.apk`
Expected: `Success`
