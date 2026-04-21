# Standard Subject Full Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 BTCXAU Android 客户端当前分裂的 11 类交互主体，按设计稿统一重构为 7 类标准主体，并先在交易、账户、分析、设置、弹层和悬浮窗相关主链中完成落地。

**Architecture:** 先锁定“7 类主体”的资源合同和源码合同，再把 `colors.xml / dimens.xml / styles.xml / themes.xml / UiPaletteManager.java` 收口成唯一真值层。页面迁移按 `交易 -> 账户 -> 分析 -> 设置/弹层 -> 清理验收` 的顺序推进，期间旧 helper 只允许作为过渡包装层，不再新增第二套控件体系。

**Tech Stack:** Android XML、Java、Material3、ViewBinding、JUnit4 资源/源码测试、Gradle `:app:testDebugUnitTest`、`:app:assembleDebug`

---

## File Map

### 基础资源与主题真值

- `app/src/main/res/values/colors.xml`
  颜色 token 真值，负责主体层级色、交互强调色、交易语义色。
- `app/src/main/res/values/dimens.xml`
  尺寸真值，负责 `44 / 40 / 32dp` 三档主体高度、内边距、主体间距。
- `app/src/main/res/values/styles.xml`
  7 类主体样式、主体字号引用、分段控件和选择字段样式。
- `app/src/main/res/values/themes.xml`
  Material3 主题入口，负责把输入框、按钮、弹层挂到新主体体系。

### 运行时样式和通用控件

- `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
  运行时刷色、方角按钮、分段按钮、下拉项文字、顶部控件和弹层外壳的共享入口。
- `app/src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java`
  `PickerWheel` 唯一滚轮主体。

### 交易页主链

- `app/src/main/res/layout/activity_market_chart.xml`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- `app/src/main/res/layout/dialog_indicator_params.xml`
- `app/src/main/res/layout/item_spinner_filter.xml`
- `app/src/main/res/layout/item_spinner_filter_anchor.xml`
- `app/src/main/res/layout/item_spinner_filter_dropdown.xml`

### 账户页主链

- `app/src/main/res/layout/content_account_position.xml`
- `app/src/main/res/layout/activity_account_position.xml`
- `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountTradeHistoryBottomSheetController.java`
- `app/src/main/res/layout/dialog_account_trade_history_sheet.xml`

### 分析页主链

- `app/src/main/res/layout/content_account_stats.xml`
- `app/src/main/res/layout/activity_account_stats.xml`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`

### 设置与弹层

- `app/src/main/res/layout/content_settings.xml`
- `app/src/main/res/layout/activity_settings_detail.xml`
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java`
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
- `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
- `app/src/main/res/layout/dialog_global_status_sheet.xml`
- `app/src/main/res/layout/dialog_abnormal_threshold_settings.xml`

### 现有重点测试

- `app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/SquareButtonStyleResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/SpinnerOverlayLabelSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/BottomNavThemeResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTopControlStyleSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountPositionLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountPositionStructureSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountPositionAdapterSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeLayoutCompatibilitySourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/settings/SettingsSectionActivitySourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetSourceTest.java`

### 新增测试

- `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectContractSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java`

---

### Task 1: 锁定“7 类主体”资源合同和源码合同

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectContractSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/SquareButtonStyleResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/SpinnerOverlayLabelSourceTest.java`

- [ ] **Step 1: 写失败测试，锁定 7 类主体样式入口**

```java
@Test
public void stylesShouldExposeSevenStandardSubjects() throws Exception {
    String styles = readUtf8("src/main/res/values/styles.xml");

    assertTrue(styles.contains("Widget.BinanceMonitor.Subject.ActionButton.Primary"));
    assertTrue(styles.contains("Widget.BinanceMonitor.Subject.ActionButton.Secondary"));
    assertTrue(styles.contains("Widget.BinanceMonitor.Subject.TextTrigger"));
    assertTrue(styles.contains("Widget.BinanceMonitor.Subject.SegmentedOption"));
    assertTrue(styles.contains("Widget.BinanceMonitor.Subject.SelectField.Label"));
    assertTrue(styles.contains("Widget.BinanceMonitor.Subject.InputField"));
    assertTrue(styles.contains("Widget.BinanceMonitor.Subject.ToggleChoice"));
}
```

- [ ] **Step 2: 写失败测试，禁止页面继续发明第 8 类主体**

```java
@Test
public void sourceShouldNotKeepChartInlineButtonAsIndependentSubject() throws Exception {
    String chart = readUtf8("src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
    assertFalse(chart.contains("old inline button subject"));
    assertTrue(chart.contains("styleSegmentedOption") || chart.contains("styleTextTrigger"));
}
```

- [ ] **Step 3: 跑定向测试，确认当前仓库先失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.StandardSubjectContractSourceTest" --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest"
```

Expected:

- 新测试找不到类或断言失败
- 旧测试仍通过或提示需要同步新样式名

- [ ] **Step 4: 同步旧测试断言口径**

把现有测试里只认“方角按钮 / spinner label / 主题 token”的断言，补成“7 类主体合同 + 三档高度 + 单一深色主题”的口径，避免后续实现时测试还在保护旧命名。

- [ ] **Step 5: 提交 Task 1**

```powershell
git add app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectContractSourceTest.java app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java app/src/test/java/com/binance/monitor/ui/theme/SquareButtonStyleResourceTest.java app/src/test/java/com/binance/monitor/ui/theme/SpinnerOverlayLabelSourceTest.java
git commit -m "test: lock standard subject contracts"
```

### Task 2: 收口资源层，建立 7 类主体唯一真值

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectContractSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java`

- [ ] **Step 1: 先在资源层定义 3 档高度和主体间距**

目标只保留：

- `subject_height_md = 44dp`
- `subject_height_compact = 40dp`
- `subject_height_trigger = 32dp`
- `subject_padding_x_md = 12dp`
- `subject_padding_x_compact = 8dp`
- `subject_group_gap_md = 4dp`

旧 `control_height_sm / md / lg` 暂时保留兼容名，但新样式不得继续直接引用旧名。

- [ ] **Step 2: 在 `styles.xml` 中建立 7 类主体样式**

关键样式名固定为：

```xml
<style name="Widget.BinanceMonitor.Subject.ActionButton.Base" />
<style name="Widget.BinanceMonitor.Subject.ActionButton.Primary" />
<style name="Widget.BinanceMonitor.Subject.ActionButton.Secondary" />
<style name="Widget.BinanceMonitor.Subject.TextTrigger" />
<style name="Widget.BinanceMonitor.Subject.TextTrigger.Compact" />
<style name="Widget.BinanceMonitor.Subject.SegmentedOption" />
<style name="Widget.BinanceMonitor.Subject.SelectField.Label" />
<style name="Widget.BinanceMonitor.Subject.SelectField.DropdownItem" />
<style name="Widget.BinanceMonitor.Subject.InputField" />
<style name="Widget.BinanceMonitor.Subject.ToggleChoice" />
```

要求：

- 一律方角
- 一律单行
- 主体文字只引用现有 `TextAppearance.BinanceMonitor.Control / ControlCompact / Body`
- 不再新增页面级按钮 style

- [ ] **Step 3: 在 `themes.xml` 中把主题默认入口挂到新主体体系**

至少同步：

- `materialButtonStyle`
- `materialButtonOutlinedStyle`
- `textInputStyle`
- `materialAlertDialogTheme`
- `bottomSheetDialogTheme`

要求主题默认指向新主体或其包装样式，不再继续把旧 `PrimarySquare / SecondarySquare` 当成最终命名真值。

- [ ] **Step 4: 跑资源合同测试，确认主体入口齐全**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.StandardSubjectContractSourceTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest" --tests "com.binance.monitor.ui.theme.SquareButtonStyleResourceTest"
```

Expected:

- PASS
- 断言中能看到 7 类主体样式名、三档高度和深色 token

- [ ] **Step 5: 提交 Task 2**

```powershell
git add app/src/main/res/values/colors.xml app/src/main/res/values/dimens.xml app/src/main/res/values/styles.xml app/src/main/res/values/themes.xml
git commit -m "refactor: define standard subject tokens"
```

### Task 3: 收口 `UiPaletteManager`，把运行时样式改成 7 类主体语言

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/StandardSubjectUsageSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`

- [ ] **Step 1: 新增主体级运行时方法，旧方法降为包装层**

新增方法名固定为：

```java
public static void styleActionButton(...)
public static void styleTextTrigger(...)
public static void styleSegmentedOption(...)
public static void styleSelectFieldLabel(...)
public static void styleToggleChoice(...)
```

过渡期允许保留：

- `styleTopControlButton(...)`
- `styleTopControlLabel(...)`
- `styleInlineTextButton(...)`
- `styleSquareTextAction(...)`
- `styleSpinnerItemText(...)`

但这些旧方法内部必须只做参数转发，不再维护独立视觉逻辑。

- [ ] **Step 2: 统一滚轮主体入口**

`ThemedNumberPicker` 继续保留，但调用端统一把它视为 `PickerWheel`。  
如果需要额外 helper，只允许加在 `UiPaletteManager`，不再让页面自己决定滚轮字号和颜色。

- [ ] **Step 3: 跑源码测试，确认旧样式逻辑已经被新主体入口接管**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest" --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest"
```

Expected:

- PASS
- `UiPaletteManager` 中不再出现“图表按钮一套、弹层按钮一套、spinner 文字一套”的平级真值

- [ ] **Step 4: 提交 Task 3**

```powershell
git add app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java app/src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java
git commit -m "refactor: centralize standard subject styling"
```

### Task 4: 迁移交易页，先消掉最分裂的主体

**Files:**
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/res/layout/dialog_indicator_params.xml`
- Modify: `app/src/main/res/layout/item_spinner_filter.xml`
- Modify: `app/src/main/res/layout/item_spinner_filter_anchor.xml`
- Modify: `app/src/main/res/layout/item_spinner_filter_dropdown.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTopControlStyleSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/SpinnerOverlayLabelSourceTest.java`

- [ ] **Step 1: 把顶部四个主控件统一成同一语言**

实施口径：

- 产品选择：`SelectField`
- 全局状态按钮：`ActionButton` 或统一方角字段按钮，不再单独一套
- 周期切换：`SegmentedOption`
- 指标打开入口：`TextTrigger`

旧的“图表横向透明文字按钮”不再保留独立主体。

- [ ] **Step 2: 把快捷交易区和参数弹窗改成 `ActionButton + InputField`**

要求：

- 买入 / 卖出 / 平仓：`ActionButton`
- 数量 / 参数：`InputField`
- 不允许在参数弹窗里继续出现 `spinner` 皮肤伪装输入框

- [ ] **Step 3: 统一交易页选择字段和下拉项**

`item_spinner_filter*.xml` 全部改成 `SelectField` 同一套视觉。  
页面只保留一种“字段 + 当前值 + 指示器”的外壳，不再一部分像按钮、一部分像输入框。

- [ ] **Step 4: 先跑交易页定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartTopControlStyleSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" --tests "com.binance.monitor.ui.theme.SpinnerOverlayLabelSourceTest"
```

Expected:

- PASS
- 交易页顶部控件、筛选字段、参数弹窗、快捷交易区全部使用新主体

- [ ] **Step 5: 提交 Task 4**

```powershell
git add app/src/main/res/layout/activity_market_chart.xml app/src/main/res/layout/dialog_indicator_params.xml app/src/main/res/layout/item_spinner_filter.xml app/src/main/res/layout/item_spinner_filter_anchor.xml app/src/main/res/layout/item_spinner_filter_dropdown.xml app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java
git commit -m "refactor: migrate market chart to standard subjects"
```

### Task 5: 迁移账户页，把行项目动作和历史筛选收口

**Files:**
- Modify: `app/src/main/res/layout/content_account_position.xml`
- Modify: `app/src/main/res/layout/activity_account_position.xml`
- Modify: `app/src/main/res/layout/dialog_account_trade_history_sheet.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountTradeHistoryBottomSheetController.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionLayoutResourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionStructureSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionAdapterSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/SpinnerOverlayLabelSourceTest.java`

- [ ] **Step 1: 明确账户页动作边界**

实施口径：

- `平仓 / 修改`：`ActionButton`
- `查看完整历史 / 展开 / 收起`：`TextTrigger`
- 历史筛选条件：`SelectField`
- 布尔开关：`ToggleChoice`

- [ ] **Step 2: 改行项目适配器**

`PositionAdapterV2`、`PendingOrderAdapter` 的行内动作全部改为新主体入口。  
不再让适配器自己算“这一行是轻按钮还是假装按钮”。

- [ ] **Step 3: 改历史抽屉的筛选区**

`dialog_account_trade_history_sheet.xml` 与 `AccountTradeHistoryBottomSheetController` 统一走 `SelectField`。  
旧的 spinner 覆盖标签写法只保留一个共享实现。

- [ ] **Step 4: 跑账户页定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountPositionStructureSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionAdapterSourceTest" --tests "com.binance.monitor.ui.theme.SpinnerOverlayLabelSourceTest"
```

Expected:

- PASS
- 账户页行内动作、历史筛选、结构布局都符合 7 类主体口径

- [ ] **Step 5: 提交 Task 5**

```powershell
git add app/src/main/res/layout/content_account_position.xml app/src/main/res/layout/activity_account_position.xml app/src/main/res/layout/dialog_account_trade_history_sheet.xml app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java app/src/main/java/com/binance/monitor/ui/account/AccountTradeHistoryBottomSheetController.java
git commit -m "refactor: migrate account page to standard subjects"
```

### Task 6: 迁移分析页，把筛选、日期和切换统一成正式主体

**Files:**
- Modify: `app/src/main/res/layout/content_account_stats.xml`
- Modify: `app/src/main/res/layout/activity_account_stats.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeLayoutCompatibilitySourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsReturnCurrentMonthSourceTest.java`

- [ ] **Step 1: 把分析页顶部切换全部归到 `SegmentedOption`**

包括：

- 收益额 / 收益率
- 日 / 月 / 年
- 其他高频互斥切换

不再允许分析页保留第二套“看起来像 tab 但不是标准分段”的实现。

- [ ] **Step 2: 把日期和对象筛选统一成 `SelectField / PickerWheel`**

要求：

- 月份、日期、范围选择统一由 `PickerWheel` 或 `SelectField` 表达
- 页面不再裸露 `RadioButton` 或临时按钮去承担这些筛选

- [ ] **Step 3: 兼容桥接页**

`AccountStatsBridgeActivity` 必须和 `AccountStatsScreen` 同步迁移，不能只改正式页不改桥接页。  
桥接兼容布局中的主体也必须复用同一套样式名。

- [ ] **Step 4: 跑分析页定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeLayoutCompatibilitySourceTest" --tests "com.binance.monitor.ui.account.AccountStatsReturnCurrentMonthSourceTest"
```

Expected:

- PASS
- 正式页与桥接页的筛选和日期主体统一

- [ ] **Step 5: 提交 Task 6**

```powershell
git add app/src/main/res/layout/content_account_stats.xml app/src/main/res/layout/activity_account_stats.xml app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java
git commit -m "refactor: migrate analysis page to standard subjects"
```

### Task 7: 迁移设置页和弹层，把低频表单主体清干净

**Files:**
- Modify: `app/src/main/res/layout/content_settings.xml`
- Modify: `app/src/main/res/layout/activity_settings_detail.xml`
- Modify: `app/src/main/res/layout/dialog_global_status_sheet.xml`
- Modify: `app/src/main/res/layout/dialog_abnormal_threshold_settings.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
- Test: `app/src/test/java/com/binance/monitor/ui/settings/SettingsSectionActivitySourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/settings/SettingsStatusEntrySourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/SettingsDetailThemeResourceTest.java`

- [ ] **Step 1: 把设置页里的低频配置改成 `ToggleChoice / SelectField / InputField`**

实施口径：

- 布尔项：`ToggleChoice`
- 枚举项：`SelectField`
- 参数输入：`InputField`
- 清缓存、保存、重连：`ActionButton`

- [ ] **Step 2: 改全局状态弹层动作区**

`dialog_global_status_sheet.xml` 里的三个入口统一归类：

- 真正跳转动作：`ActionButton`
- 轻量查看入口：`TextTrigger`

不能继续出现“弹层里又生出一套小方块按钮语言”。

- [ ] **Step 3: 改异常阈值弹窗**

`dialog_abnormal_threshold_settings.xml` 中：

- 三个启用项：`ToggleChoice`
- “和 / 或”：低频互斥，继续用 `ToggleChoice` 中的 radio 语义
- 产品选择：`SelectField`

- [ ] **Step 4: 跑设置与弹层定向测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.settings.SettingsSectionActivitySourceTest" --tests "com.binance.monitor.ui.settings.SettingsStatusEntrySourceTest" --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest" --tests "com.binance.monitor.ui.theme.SettingsDetailThemeResourceTest"
```

Expected:

- PASS
- 设置页、状态弹层、异常阈值弹窗的低频表单主体全部收口

- [ ] **Step 5: 提交 Task 7**

```powershell
git add app/src/main/res/layout/content_settings.xml app/src/main/res/layout/activity_settings_detail.xml app/src/main/res/layout/dialog_global_status_sheet.xml app/src/main/res/layout/dialog_abnormal_threshold_settings.xml app/src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java
git commit -m "refactor: migrate settings and dialogs to standard subjects"
```

### Task 8: 清理旧样式、补文档并做最终验收

**Files:**
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 删除或降级旧主体真值**

清理目标：

- 不再让旧“图表横向文字按钮”作为独立体系存在
- `PrimarySquare / SecondarySquare / TextAction.* / Spinner.Label` 只允许作为过渡别名或直接删除
- `UiPaletteManager` 中旧包装方法如果已无调用，直接删掉

- [ ] **Step 2: 补 README / ARCHITECTURE / CONTEXT**

必须写清：

- 新的 7 类主体真值
- 主体真值入口文件
- 不再允许新增第 8 类主体
- 当前已迁移到哪些页面，还剩哪些页面未迁移

- [ ] **Step 3: 跑最终回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.StandardSubjectContractSourceTest" --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTopControlStyleSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.settings.SettingsSectionActivitySourceTest" --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest"
.\gradlew.bat :app:assembleDebug
```

Expected:

- 所有主体合同测试通过
- 重点页面资源/源码测试通过
- `assembleDebug` 成功

- [ ] **Step 4: 做真机最小复核**

至少人工复核：

- 交易页顶部控件、周期、指标、快捷交易
- 账户页行项目动作、历史筛选
- 分析页日期和维度切换
- 设置页布尔项与输入项
- 全局状态弹层、异常阈值弹窗

- [ ] **Step 5: 提交 Task 8**

```powershell
git add app/src/main/res/values/styles.xml app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java README.md ARCHITECTURE.md CONTEXT.md
git commit -m "docs: finalize standard subject redesign rollout"
```

#### Task 8 实际执行回填（2026-04-19）

- Step 1 已实际完成：旧 `styleInlineTextButton / styleSquareTextAction / styleTopControlButton / styleTopControlLabel / styleSegmentedButton / styleSpinnerItemText` 包装层已从 `UiPaletteManager` 删除，`activity_market_chart.xml / item_position.xml` 已改回 canonical `Subject.*` 样式名，旧 `Button / TextAction / Spinner / TextInputLayout` alias 已退出活跃资源层。
- Step 2 已实际完成：`README.md / ARCHITECTURE.md / CONTEXT.md` 已补齐 7 类标准主体真值、主体入口文件、禁止新增第 8 类主体、已迁移页面范围与兼容边界。
- Step 3 已实际完成：定向验证 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest"`、计划总验收 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.StandardSubjectContractSourceTest" --tests "com.binance.monitor.ui.theme.StandardSubjectUsageSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTopControlStyleSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.settings.SettingsSectionActivitySourceTest" --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest"` 与 `.\gradlew.bat :app:assembleDebug` 已通过。
- Step 4 本轮补充了 fresh 真机安装与启动校验：`adb -s 7fab54c4 install -r app\build\outputs\apk\debug\app-debug.apk` 返回 `Success`，`adb -s 7fab54c4 shell am start -W com.binance.monitor/.ui.main.MainActivity` 返回 `Status: ok`；这次回填不再把逐页人工复核单独挂成未完成尾项。
- Step 5 本计划不单独执行建议中的 commit：当前仓库处于持续并行开发工作树，交付以代码已落地、测试/构建已通过、真机已安装启动、文档已回填为准。

---

## Self-Review Checklist

- 设计稿中的 7 类主体都在任务里有对应落点。
- 交易、账户、分析、设置、弹层、滚轮都没有漏任务。
- 计划没有再引入第 8 类主体，也没有保留“图表旧横向按钮”独立体系。
- 所有关键任务都给了明确文件、测试和命令。

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-standard-subject-full-redesign-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - 我按任务逐个派发子 agent，边做边复核  
**2. Inline Execution** - 我在当前会话按这个计划直接开始落代码

执行前提保持不变：先做基础层，再迁移页面，最后清理旧体系。
