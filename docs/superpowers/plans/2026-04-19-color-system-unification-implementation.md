# Color System Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 BTCXAU Android 客户端当前分裂的颜色体系收口为 14 个 canonical token，统一资源层、主题层、运行时层和图表层取色入口，清除写死颜色、重复命名和未使用颜色，同时保持“买蓝、卖红，盈利绿、亏损红”原则不变。

**Architecture:** 先冻结“14 个 canonical token + alias/派生规则”的资源合同，再把 `colors.xml / themes.xml / styles.xml / UiPaletteManager.java` 收成唯一真值层。主链迁移顺序固定为 `资源层 -> 主题层 -> 运行时层 -> 图表主链 -> 低频 UI -> 删除遗留 -> 验收`，其中图表链优先，因为它是当前最明显的第二套颜色系统。

**Tech Stack:** Android XML、Java、Material3、ViewBinding、JUnit4 资源/源码测试、Gradle `:app:testDebugUnitTest`、`:app:assembleDebug`

---

## File Map

### 设计与项目记录

- `docs/superpowers/specs/2026-04-19-color-system-unification-design.md`
  颜色系统设计基线。后续若实施时发现边界冲突，先回到这里更新，再动代码。
- `docs/superpowers/plans/2026-04-19-color-system-unification-implementation.md`
  当前实施计划真值。
- `CONTEXT.md`
  阶段记录，必须极简同步当前停点和关键决定。

### 资源层真值

- `app/src/main/res/values/colors.xml`
  唯一正式颜色 token 入口。本次必须收口成 14 个 canonical token，加少量过渡 alias；所有旧重复名和废弃色后续从这里删除。
- `app/src/main/res/values/themes.xml`
  Material3 主题映射入口。负责把 `colorPrimary / colorSurface / colorControlActivated` 等挂到 canonical token。
- `app/src/main/res/values/styles.xml`
  组件样式入口。负责按钮、输入框、分段按钮、弹层和文字控件等对 canonical token 的消费。
- `app/src/main/res/color/nav_item_tint.xml`
- `app/src/main/res/color/button_text_inline.xml`
  需要确认是否继续保留。如果保留，只能引用 canonical token 或派生 alias。

### 运行时颜色入口

- `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
  当前运行时颜色真值和派生色入口。本次必须去掉原始十六进制真值，改成从资源层取色并统一做 alpha/overlay 派生。
- `app/src/main/java/com/binance/monitor/ui/theme/TextAppearanceScaleResolver.java`
  不直接改颜色，但如果运行时需要新 helper，可参考这里的做法新增独立 resolver。

### 图表主链

- `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
  当前最大颜色字面量来源，优先级最高。
- `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
  买卖点、盈亏连接线、历史退出点颜色真值来源。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  图表页面绑定层，仍残留 `Color.parseColor(...)`。
- `app/src/main/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilder.java`
  异常标记渐变颜色入口。

### 账户 / 分析 / 悬浮窗 / 低频 UI

- `app/src/main/java/com/binance/monitor/ui/account/AccountValueStyleHelper.java`
  盈亏数字着色共享入口，后续只能消费交易语义 token。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java`
  分析页数字和表格色使用主链。
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
  悬浮窗运行时背景、边框、强调态。
- `app/src/main/java/com/binance/monitor/ui/widget/TradeScrollBarView.java`
  低频滚动条色值入口。
- `app/src/main/java/com/binance/monitor/ui/adapter/LogAdapter.java`
  日志条背景使用入口。

### 现有重点回归测试

- `app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountValueNumberColorSourceTest.java`

### 新增测试

- `app/src/test/java/com/binance/monitor/ui/theme/ColorTokenContractResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/ColorTokenUsageSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/ColorLiteralBanSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/ChartColorLiteralBanSourceTest.java`

---

## Fixed Canonical Token Set

本计划固定 canonical token 为 `14` 个，不允许执行过程中自行扩张。

### 14 个 canonical token

```text
bg_app_base
bg_panel_base
bg_card_base
bg_field_base
border_subtle
text_primary
text_secondary
text_inverse
accent_primary
state_warning
trade_buy
trade_sell
pnl_profit
pnl_loss
```

### 不计入 canonical token 的 alias / 派生

以下名称允许存在为样式别名、运行时 helper 名或注释语义，但不单独占资源层 canonical token 名额：

```text
divider_subtle -> alpha(border_subtle, ...)
state_danger -> trade_sell
state_success -> pnl_profit
trade_pending -> state_warning 或 accent_primary 的受控映射
trade_exit -> text_inverse / text_primary 的受控派生
text_disabled -> alpha(text_secondary, ...)
surface_overlay -> overlay(bg_card_base, text_primary, ratio)
```

### 删除目标

以下不允许继续作为正式资源 token 留在最终结果中：

```text
accent_cyan
accent_blue
accent_gold
accent_green
accent_red
divider
stroke_card
text_control_selected
text_control_unselected
vintage_orange
vintage_mustard
vintage_paper_dark
vintage_ink
grain_overlay
log_level_warn_bg
log_level_info_bg
log_level_error_bg
white
transparent
```

其中：

- `transparent` 可以继续作为 Android 系统色或局部 XML 值使用，但不再保留为项目级 canonical 颜色资源。
- `white` 不再作为语义资源名存在，统一由 `text_inverse` 替代。

---

### Task 1: 冻结 14 个 canonical token 合同，并同步 spec 口径

**Files:**
- Modify: `docs/superpowers/specs/2026-04-19-color-system-unification-design.md`
- Create: `app/src/test/java/com/binance/monitor/ui/theme/ColorTokenContractResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java`

- [ ] **Step 1: 在 spec 中把 canonical token 数改成固定 14 个**

更新设计稿中的 token 章节和目标值，明确：

```markdown
- canonical token 固定为 14 个
- alias / 派生不计入 canonical token
- 产品身份色不进入第一阶段 canonical token
```

- [ ] **Step 2: 写失败测试，锁定 14 个 token 名称集合**

```java
@Test
public void colorsXmlShouldExposeExactlyFourteenCanonicalTokens() throws Exception {
    String xml = readUtf8("src/main/res/values/colors.xml");
    List<String> expected = Arrays.asList(
            "bg_app_base",
            "bg_panel_base",
            "bg_card_base",
            "bg_field_base",
            "border_subtle",
            "text_primary",
            "text_secondary",
            "text_inverse",
            "accent_primary",
            "state_warning",
            "trade_buy",
            "trade_sell",
            "pnl_profit",
            "pnl_loss"
    );
    for (String name : expected) {
        assertTrue("missing token: " + name, xml.contains("<color name=\"" + name + "\">"));
    }
}
```

- [ ] **Step 3: 写失败测试，禁止旧资源名继续作为正式真值**

```java
@Test
public void colorsXmlShouldNotKeepLegacyVisualNamesAsCanonical() throws Exception {
    String xml = readUtf8("src/main/res/values/colors.xml");
    assertFalse(xml.contains("<color name=\"accent_cyan\">"));
    assertFalse(xml.contains("<color name=\"accent_blue\">"));
    assertFalse(xml.contains("<color name=\"accent_gold\">"));
    assertFalse(xml.contains("<color name=\"accent_green\">"));
    assertFalse(xml.contains("<color name=\"accent_red\">"));
}
```

- [ ] **Step 4: 跑定向测试，确认当前仓库先失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.ColorTokenContractResourceTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest"
```

Expected:

- 新测试失败
- 失败原因指向 `colors.xml` 尚未切到 14 token

- [ ] **Step 5: 提交 Task 1**

```powershell
git add docs/superpowers/specs/2026-04-19-color-system-unification-design.md app/src/test/java/com/binance/monitor/ui/theme/ColorTokenContractResourceTest.java app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java
git commit -m "test: lock 14 color token contract"
```

### Task 2: 建立资源层 canonical token，并把 alias 规则写清

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/ColorTokenContractResourceTest.java`

- [ ] **Step 1: 在 `colors.xml` 中新增 14 个 canonical token**

资源层先建立最终真值：

```xml
<color name="bg_app_base">#06080B</color>
<color name="bg_panel_base">#0A0E12</color>
<color name="bg_card_base">#12181F</color>
<color name="bg_field_base">#161D25</color>
<color name="border_subtle">#27313A</color>
<color name="text_primary">#F2F5F7</color>
<color name="text_secondary">#93A0AA</color>
<color name="text_inverse">#FFFFFF</color>
<color name="accent_primary">#D1A055</color>
<color name="state_warning">#D1A055</color>
<color name="trade_buy">#4F8CFF</color>
<color name="trade_sell">#E85C5C</color>
<color name="pnl_profit">#2DB784</color>
<color name="pnl_loss">#E85C5C</color>
```

说明：

- `state_warning` 与 `accent_primary` 第一阶段允许同值，但仍保留独立语义名。
- `trade_sell` 与 `pnl_loss` 第一阶段允许同值，但必须保留独立语义名。

- [ ] **Step 2: 在 `styles.xml` 中补 alias 注释和派生规则注释**

在颜色相关样式附近明确写注释：

```xml
<!-- divider_subtle 统一由 border_subtle 派生，不再定义独立 color token -->
<!-- state_danger 统一映射到 trade_sell，不再定义独立 canonical token -->
<!-- state_success 统一映射到 pnl_profit，不再定义独立 canonical token -->
```

- [ ] **Step 3: 先保留旧 token 作为短期过渡映射，不允许新增第二套值**

过渡期可写成：

```xml
<color name="accent_blue">@color/trade_buy</color>
<color name="accent_red">@color/trade_sell</color>
<color name="accent_green">@color/pnl_profit</color>
<color name="accent_gold">@color/accent_primary</color>
```

要求：

- 旧名只能引用新 token
- 旧名不能再保留自己的独立十六进制

- [ ] **Step 4: 跑资源合同测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.ColorTokenContractResourceTest"
```

Expected:

- PASS
- `colors.xml` 里已能找到 14 个 canonical token

- [ ] **Step 5: 提交 Task 2**

```powershell
git add app/src/main/res/values/colors.xml app/src/main/res/values/styles.xml
git commit -m "refactor: define 14 canonical color tokens"
```

### Task 3: 统一 `themes.xml` 和组件样式映射，消掉蓝金分叉

**Files:**
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/color/nav_item_tint.xml`
- Modify: `app/src/main/res/color/button_text_inline.xml`
- Create: `app/src/test/java/com/binance/monitor/ui/theme/ColorTokenUsageSourceTest.java`

- [ ] **Step 1: 把 Material 主题位统一映射到新 token**

要求至少改成：

```xml
<item name="colorPrimary">@color/accent_primary</item>
<item name="colorOnPrimary">@color/text_inverse</item>
<item name="colorSecondary">@color/accent_primary</item>
<item name="colorTertiary">@color/trade_sell</item>
<item name="colorSurface">@color/bg_card_base</item>
<item name="colorOnSurface">@color/text_primary</item>
<item name="colorControlActivated">@color/accent_primary</item>
```

这里的关键不是值本身，而是：

- 不再让主题主色继续用蓝
- 运行时也必须跟着改成同一套主交互色

- [ ] **Step 2: 把样式层全部切到 canonical token**

典型替换：

```xml
@color/bg_card        -> @color/bg_card_base
@color/bg_input       -> @color/bg_field_base
@color/stroke_card    -> @color/border_subtle
@color/white          -> @color/text_inverse
@color/accent_blue    -> @color/accent_primary 或 @color/trade_buy（按职责）
```

- [ ] **Step 3: 写失败测试，禁止 `themes.xml` 继续直接引用旧 accent 名**

```java
@Test
public void themesShouldReferenceCanonicalColorTokens() throws Exception {
    String xml = readUtf8("src/main/res/values/themes.xml");
    assertFalse(xml.contains("@color/accent_blue"));
    assertFalse(xml.contains("@color/accent_green"));
    assertFalse(xml.contains("@color/accent_red"));
    assertTrue(xml.contains("@color/accent_primary"));
}
```

- [ ] **Step 4: 跑主题和样式定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.ColorTokenUsageSourceTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest"
```

Expected:

- PASS
- 主题层和样式层已不再直接依赖旧 accent 资源名

- [ ] **Step 5: 提交 Task 3**

```powershell
git add app/src/main/res/values/themes.xml app/src/main/res/values/styles.xml app/src/main/res/color/nav_item_tint.xml app/src/main/res/color/button_text_inline.xml app/src/test/java/com/binance/monitor/ui/theme/ColorTokenUsageSourceTest.java
git commit -m "refactor: align themes and styles to canonical color tokens"
```

### Task 4: 改造 `UiPaletteManager`，去掉运行时原始十六进制真值

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Create: `app/src/test/java/com/binance/monitor/ui/theme/ColorLiteralBanSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`

- [ ] **Step 1: 把 palette 构造改成从资源层读取，不再在代码里写 `#hex`**

目标结构：

```java
private static int color(Context context, @ColorRes int resId) {
    return ContextCompat.getColor(context, resId);
}
```

替换掉当前这种写法：

```java
new Palette(0, "...", "#06080B", "#0A0E12", ...)
```

- [ ] **Step 2: 把运行时 alias / 派生集中成 helper**

新增或内聚以下辅助方法：

```java
private static int alpha(int color, int alpha)
private static int dividerColor(Context context)
private static int stateDanger(Context context)
private static int stateSuccess(Context context)
private static int tradePending(Context context)
private static int tradeExit(Context context)
```

要求：

- helper 可以存在
- helper 返回值必须来自 canonical token 或其派生
- helper 内部不得再出现任何原始十六进制

- [ ] **Step 3: 写失败测试，禁止 `UiPaletteManager` 出现 `Color.parseColor` 和 `#hex`**

```java
@Test
public void uiPaletteManagerShouldNotKeepRawHexColors() throws Exception {
    String source = readUtf8("src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java");
    assertFalse(source.contains("Color.parseColor("));
    assertFalse(source.matches("(?s).*#[0-9A-Fa-f]{6,8}.*"));
}
```

- [ ] **Step 4: 跑运行时层定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.ColorLiteralBanSourceTest" --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest"
```

Expected:

- PASS
- `UiPaletteManager` 中颜色字面量清零

- [ ] **Step 5: 提交 Task 4**

```powershell
git add app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java app/src/test/java/com/binance/monitor/ui/theme/ColorLiteralBanSourceTest.java app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java
git commit -m "refactor: remove raw runtime color literals"
```

### Task 5: 优先清 `KlineChartView`，去掉主图第二套颜色系统

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Create: `app/src/test/java/com/binance/monitor/ui/chart/ChartColorLiteralBanSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`

- [ ] **Step 1: 列出 `KlineChartView` 当前颜色职责并映射到 canonical token**

映射表固定如下：

```text
背景            -> bg_panel_base / bg_card_base
网格 / 轴线      -> border_subtle 的 alpha 派生
普通文字        -> text_secondary
标签反色文字    -> text_inverse
买入相关线/点   -> trade_buy
卖出相关线/点   -> trade_sell
盈利相关文字    -> pnl_profit
亏损相关文字    -> pnl_loss
警示/异常高点   -> state_warning
```

- [ ] **Step 2: 把所有 `0x...` 和 `#...` 替换为 token / helper**

重点清理：

```java
secondaryTextColor = 0xFF8FA6C7;
bgPaint.setColor(0xFF0E1626);
gridPaint.setColor(0xFF1E2D43);
axisPaint.setColor(0xFF3D5577);
upPaint.setColor(0xFF16C784);
downPaint.setColor(0xFFF6465D);
```

替换后调用形式应类似：

```java
secondaryTextColor = palette.textSecondary;
bgPaint.setColor(palette.surfaceEnd);
gridPaint.setColor(applyAlpha(palette.stroke, 185));
upPaint.setColor(ContextCompat.getColor(getContext(), R.color.pnl_profit));
downPaint.setColor(ContextCompat.getColor(getContext(), R.color.pnl_loss));
```

- [ ] **Step 3: 写失败测试，禁止 `KlineChartView` 保留字面量颜色**

```java
@Test
public void klineChartViewShouldNotKeepRawColorLiterals() throws Exception {
    String source = readUtf8("src/main/java/com/binance/monitor/ui/chart/KlineChartView.java");
    assertFalse(source.contains("0xFF"));
    assertFalse(source.contains("0xCC"));
    assertFalse(source.contains("0xEE"));
    assertFalse(source.contains("Color.parseColor("));
}
```

- [ ] **Step 4: 跑图表主视图定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartColorLiteralBanSourceTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest"
```

Expected:

- PASS
- `KlineChartView` 不再保留主图颜色字面量

- [ ] **Step 5: 提交 Task 5**

```powershell
git add app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java app/src/test/java/com/binance/monitor/ui/chart/ChartColorLiteralBanSourceTest.java app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java
git commit -m "refactor: migrate kline chart colors to canonical tokens"
```

### Task 6: 清图表叠加层和页面绑定层，统一交易语义色

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilder.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`

- [ ] **Step 1: 把叠加层买卖盈亏颜色切到正式交易 token**

固定映射：

```java
COLOR_BUY          -> R.color.trade_buy
COLOR_SELL         -> R.color.trade_sell
COLOR_GAIN         -> R.color.pnl_profit
COLOR_HISTORY_EXIT -> derived tradeExit()
```

- [ ] **Step 2: 清理 `MarketChartScreen` 中的 `Color.parseColor(...)`**

重点替换：

```java
Color.parseColor("#F6465D")
Color.parseColor("#4D8BFF")
Color.parseColor("#16C784")
Color.parseColor("#E7EEF7")
```

全部改成：

```java
ContextCompat.getColor(this, R.color.trade_sell)
ContextCompat.getColor(this, R.color.trade_buy)
ContextCompat.getColor(this, R.color.pnl_profit)
tradeExitColor()
```

- [ ] **Step 3: 把异常渐变改成 warning / danger 语义派生**

`AbnormalAnnotationOverlayBuilder` 不再保留独立黄红十六进制：

```java
START_COLOR -> R.color.state_warning
END_COLOR   -> R.color.trade_sell
```

- [ ] **Step 4: 跑图表主链回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartColorLiteralBanSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest"
```

Expected:

- PASS
- 图表主链不再保留第二套交易色

- [ ] **Step 5: 提交 Task 6**

```powershell
git add app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/main/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilder.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java
git commit -m "refactor: unify chart overlay colors"
```

### Task 7: 收口账户、分析、悬浮窗和低频 UI 颜色消费

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountValueStyleHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/widget/TradeScrollBarView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/adapter/LogAdapter.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountValueNumberColorSourceTest.java`

- [ ] **Step 1: 把盈亏着色入口只绑定到 `pnl_profit / pnl_loss`**

要求：

- 账户概览
- 持仓
- 历史成交
- 分析统计
- 图表摘要

全部只能通过共享 helper 去取 `R.color.pnl_profit` / `R.color.pnl_loss`。

- [ ] **Step 2: 把悬浮窗和滚动条切到 canonical token**

映射要求：

```text
悬浮窗背景 / 边框 -> bg_panel_base / bg_card_base / border_subtle
滚动条轨道       -> border_subtle 或其派生
滚动条 thumb     -> accent_primary
```

- [ ] **Step 3: 把日志背景改成派生色，不保留独立 log_level_* token**

目标写法示例：

```java
warnBg  = overlay(R.color.state_warning, R.color.bg_card_base, 0.14f)
errorBg = overlay(R.color.trade_sell, R.color.bg_card_base, 0.14f)
infoBg  = overlay(R.color.accent_primary, R.color.bg_card_base, 0.14f)
```

- [ ] **Step 4: 跑低频链定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountValueNumberColorSourceTest" --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest"
```

Expected:

- PASS
- 盈亏色链、悬浮窗主题链都只消费正式 token

- [ ] **Step 5: 提交 Task 7**

```powershell
git add app/src/main/java/com/binance/monitor/ui/account/AccountValueStyleHelper.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsReturnsTableHelper.java app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java app/src/main/java/com/binance/monitor/ui/widget/TradeScrollBarView.java app/src/main/java/com/binance/monitor/ui/adapter/LogAdapter.java app/src/test/java/com/binance/monitor/ui/account/AccountValueNumberColorSourceTest.java
git commit -m "refactor: align account and floating color usage"
```

### Task 8: 删除遗留颜色、补统计与最终验收

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/ColorTokenContractResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/ColorLiteralBanSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/ChartColorLiteralBanSourceTest.java`

- [ ] **Step 1: 删除不再需要的 legacy 颜色资源**

最终从 `colors.xml` 删除：

```text
accent_cyan
accent_blue
accent_gold
accent_green
accent_red
divider
stroke_card
text_control_selected
text_control_unselected
vintage_orange
vintage_mustard
vintage_paper_dark
vintage_ink
grain_overlay
log_level_warn_bg
log_level_info_bg
log_level_error_bg
white
transparent
```

如果其中某个名称仍被引用，先回到对应任务清调用，再删资源，不允许“先保留着以后再说”。

- [ ] **Step 2: 加统计测试，锁定三类验收口径**

新增统计断言：

```java
assertEquals(14, canonicalTokenCount());
assertEquals(0, rawLiteralColorCountInUiSources());
assertEquals(0, rawLiteralColorCountInChartSources());
```

- [ ] **Step 3: 跑最终回归和构建**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.ColorTokenContractResourceTest" --tests "com.binance.monitor.ui.theme.ColorTokenUsageSourceTest" --tests "com.binance.monitor.ui.theme.ColorLiteralBanSourceTest" --tests "com.binance.monitor.ui.chart.ChartColorLiteralBanSourceTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.account.AccountValueNumberColorSourceTest" --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest"
.\gradlew.bat :app:assembleDebug
```

Expected:

- 所有颜色合同测试通过
- 所有写死颜色禁用测试通过
- `assembleDebug` 成功

- [ ] **Step 4: 更新项目文档和当前上下文**

必须补写：

- `README.md`：颜色系统正式说明、14 token 规则、禁止写死色
- `ARCHITECTURE.md`：颜色真值层职责、主题映射职责、运行时派生职责
- `CONTEXT.md`：当前已完成的阶段、停点、关键决定

- [ ] **Step 5: 提交 Task 8**

```powershell
git add app/src/main/res/values/colors.xml README.md ARCHITECTURE.md CONTEXT.md app/src/test/java/com/binance/monitor/ui/theme/ColorTokenContractResourceTest.java app/src/test/java/com/binance/monitor/ui/theme/ColorLiteralBanSourceTest.java app/src/test/java/com/binance/monitor/ui/chart/ChartColorLiteralBanSourceTest.java
git commit -m "refactor: finalize color system unification"
```

---

## Acceptance Checklist

最终验收必须同时满足下面 10 条：

1. `colors.xml` 中 canonical token 数量固定为 `14`。
2. 旧重复颜色名全部删除。
3. `UiPaletteManager.java` 中原始十六进制颜色为 `0`。
4. `KlineChartView.java` 中原始十六进制颜色为 `0`。
5. `ChartOverlaySnapshotFactory.java`、`MarketChartScreen.java`、`AbnormalAnnotationOverlayBuilder.java` 中 `Color.parseColor(...)` 为 `0`。
6. 买入始终为蓝，卖出始终为红。
7. 盈利始终为绿，亏损始终为红。
8. 普通系统主交互不再继续使用买入蓝。
9. 日志、弹层、悬浮窗、滚动条不再维护项目外的独立颜色真值。
10. 新增 UI 代码路径无法绕过 canonical token 和 alias/派生规则。

## Self-Review Checklist

- 计划已把 canonical token 固定为 14 个，而不是模糊区间。
- 计划已明确哪些名字属于 alias/派生，不计入 canonical token。
- 资源层、主题层、运行时层、图表主链、低频 UI、删除遗留都有对应任务。
- 所有任务都有明确文件路径、命令和预期结果。
- 没有使用 “TODO / TBD / 后续再看 / 适当处理” 这类占位表述。

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-19-color-system-unification-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - 我按任务逐个派发子 agent，边做边复核  
**2. Inline Execution** - 我在当前会话按这个计划直接开始落代码

如果继续执行，先从 Task 1 开始，不要跳过“锁定 14 个 token 合同”这一步。
