# Trade Top Controls Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一交易页顶部 `产品名称 / 交易 / 挂单 / 全局状态` 四个控件的背景、文字层级和选中态规则，让它们看起来属于同一组控件。

**Architecture:** 保持现有布局结构不变，只新增一层“顶部控件组”共享样式入口。布局层负责统一控件高度和标签样式，运行时层负责把产品选择器、模式按钮和状态按钮都刷到同一套背景与文字规则，避免旧独立页和主壳页继续分叉。

**Tech Stack:** Android XML、Java、JUnit4 资源/源码测试

---

### Task 1: 锁定顶部控件统一合同

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTopControlStyleSourceTest.java`

- [ ] **Step 1: 写失败测试**

```java
@Test
public void screenAndLegacyActivityShouldUseSharedTopControlStyling() throws Exception {
    String screen = readUtf8("app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
    String activity = readUtf8("app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");

    assertTrue(screen.contains("applyTopControlGroupStyles();"));
    assertTrue(screen.contains("styleTopControlButton("));
    assertTrue(screen.contains("styleTopControlLabel("));
    assertTrue(activity.contains("applyTopControlGroupStyles();"));
    assertTrue(activity.contains("styleTopControlButton("));
    assertTrue(activity.contains("styleTopControlLabel("));
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartTopControlStyleSourceTest"`
Expected: FAIL，提示找不到新的顶部控件共享样式调用

### Task 2: 实现顶部控件统一样式

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/SquareButtonStyleResourceTest.java`

- [ ] **Step 3: 写最小实现**

```java
UiPaletteManager.styleSquareTextAction(label, palette, fillColor, textColor, 13f, 8, R.dimen.control_height_lg, false);
button.setBackground(UiPaletteManager.createFilledDrawable(context, fillColor));
spinner.setBackground(UiPaletteManager.createFilledDrawable(context, palette.control));
```

- [ ] **Step 4: 扩充资源测试**

```java
assertTrue(chartLayout.contains("android:id=\"@+id/tvChartSymbolPickerLabel\""));
assertTrue(chartLayout.contains("style=\"@style/Widget.BinanceMonitor.Spinner.Label\""));
assertTrue(chartLayout.contains("android:layout_height=\"@dimen/control_height_lg\""));
```

- [ ] **Step 5: 跑定向测试确认通过**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartTopControlStyleSourceTest" --tests "com.binance.monitor.ui.theme.SquareButtonStyleResourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest"`
Expected: PASS

### Task 3: 回归验证与记录同步

**Files:**
- Modify: `CONTEXT.md`

- [ ] **Step 6: 运行构建验证**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 更新上下文记录**

```md
- 最新续做已统一交易页顶部 `产品名称 / 交易 / 挂单 / 全局状态` 控件组：背景、字体层级、选中态和旧独立页/主壳页运行时刷色已收口到同一套规则。
```
