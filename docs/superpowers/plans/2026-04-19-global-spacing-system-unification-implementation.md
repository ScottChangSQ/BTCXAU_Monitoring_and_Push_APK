# Global Spacing System Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 BTCXAU Android 客户端当前分裂的页面边距、模块间距、容器内边距、控件间距、字段预留和文字行高收口成一套全局尺寸系统，禁止非零 `dp/sp` 距离继续散落在 XML 与 Java 里。

**Architecture:** 先冻结“基础尺度阶梯 + 语义尺寸 token + 行高合同 + 非零硬编码禁入”四层规则，再把 `dimens.xml / styles.xml / UiPaletteManager / 各页面布局 / 运行时 helper` 统一接回这套真值。迁移顺序固定为 `合同测试 -> 资源层 -> 运行时解析入口 -> 主页面布局 -> Java 动态布局 -> 自定义 helper -> 删除遗留 -> 文档与总验收`，避免页面边迁移边重新发明尺寸语言。

**Tech Stack:** Android XML、Java、Material3、ViewBinding、JUnit4 资源/源码测试、Gradle `:app:testDebugUnitTest`、`:app:assembleDebug`

---

## File Map

### 计划与项目记录

- `docs/superpowers/plans/2026-04-19-global-spacing-system-unification-implementation.md`
  当前实施计划真值。
- `CONTEXT.md`
  阶段停点、关键决定和执行顺序记录，实施中每轮必须同步。
- `README.md`
  需要补“全局尺寸系统”规则、运行约束和常用验证命令。
- `ARCHITECTURE.md`
  需要补“尺寸系统真值层 -> 运行时解析入口 -> 页面消费端”三层结构说明。

### 资源层真值

- `app/src/main/res/values/dimens.xml`
  尺寸 token 唯一真值入口。本次要从“散点 + 局部专用值并存”收口为“基础阶梯 + 语义 token + 少量组件几何 token”。
- `app/src/main/res/values/styles.xml`
  文字行高和标准主体内边距真值入口。本次要把 `Body / Compact / Dense / Control / ControlCompact` 的行高和 padding 统一接回语义 token。
- `app/src/main/res/values/themes.xml`
  组件默认边框与间距的主题入口，主要确认不再把旧尺寸名当正式真值。

### 运行时尺寸入口

- `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
  当前已经负责部分容器尺寸与抽屉外边距，本次继续保留为主入口之一，但不再允许直接写散落的 `dp(context, value)` 来表达语义间距。
- `app/src/main/java/com/binance/monitor/ui/theme/SpacingTokenResolver.java`
  新增文件。负责把语义 `dimen` 解析成运行时 `px`，成为 Java 动态布局唯一尺寸解析入口。

### 现有 helper，需要接回全局尺寸系统

- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowLayoutHelper.java`
  当前仍有展开宽度、内边距、最小化按钮尺寸等硬编码 `dp`。
- `app/src/main/java/com/binance/monitor/ui/account/CurvePaneLayoutHelper.java`
  当前仍有曲线绘图区左右边界与线宽硬编码。
- `app/src/main/java/com/binance/monitor/ui/account/CurvePaneSpacingHelper.java`
  当前负责子图上下留白逻辑，本次要保持逻辑不变，但默认 inset 来源改为 token。
- `app/src/main/java/com/binance/monitor/ui/chart/KlinePaneTextLayoutHelper.java`
  当前仍有指标标题/坐标文本 inset 的硬编码。

### 主页面与弹层布局

- `app/src/main/res/layout/activity_market_chart.xml`
- `app/src/main/res/layout/content_account_position.xml`
- `app/src/main/res/layout/content_account_stats.xml`
- `app/src/main/res/layout/content_settings.xml`
- `app/src/main/res/layout/dialog_global_status_sheet.xml`
- `app/src/main/res/layout/dialog_account_connection_sheet.xml`
- `app/src/main/res/layout/dialog_account_trade_history_sheet.xml`
- `app/src/main/res/layout/dialog_indicator_params.xml`
- `app/src/main/res/layout/layout_floating_window.xml`
- `app/src/main/res/layout/activity_main_host.xml`

### Java 动态布局与桥接页

- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- `app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java`

### 新增测试

- `app/src/test/java/com/binance/monitor/ui/theme/SpacingTokenContractResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/SpacingHardcodeBanSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/LineHeightContractResourceTest.java`

### 现有重点回归测试

- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTopControlStyleSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartOverlayToggleLayoutSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountPositionLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeLayoutCompatibilitySourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowManagerSourceTest.java`

---

## Fixed Global Rules

### 基础尺度阶梯

非零距离只允许从下面这组基础值派生：

```text
2dp
4dp
8dp
12dp
16dp
24dp
```

### 语义尺寸 token

实施结束后，页面和运行时代码优先只消费下面这组语义名：

```text
screen_edge_padding
section_gap
container_padding
container_padding_compact
sheet_content_padding
dialog_content_padding
row_gap
row_gap_compact
inline_gap
inline_gap_compact
field_padding_x
field_padding_x_compact
field_trailing_reserve
field_trailing_reserve_compact
icon_text_gap
list_item_padding_x
list_item_padding_y
```

### 行高合同

文字行距统一到文字体系，不允许在布局里继续零散设置：

```text
PageHero lineHeight = 28sp
ValueHero lineHeight = 24sp
Section lineHeight = 22sp
Body lineHeight = 20sp
Compact lineHeight = 16sp
Dense lineHeight = 14sp
```

### 硬编码禁令

- 允许：`0dp`、布局行为所需 `0dp width/height`、`1dp` 描边线宽、图表绘制中与数据几何直接绑定的非间距数值。
- 禁止：非零 `padding/margin/gap/reserve/lineHeight` 继续直接写在 XML 或 Java。
- Java 动态布局一律通过 `SpacingTokenResolver` 或已接入它的 helper 取值。

---

### Task 1: 冻结全局尺寸合同，先把硬编码和行高边界锁住

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/theme/SpacingTokenContractResourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/theme/SpacingHardcodeBanSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/theme/LineHeightContractResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`

- [ ] **Step 1: 写失败测试，锁定语义尺寸 token 集合**

```java
@Test
public void dimensShouldExposeCanonicalSpacingTokens() throws Exception {
    String xml = readUtf8("src/main/res/values/dimens.xml");

    assertTrue(xml.contains("<dimen name=\"screen_edge_padding\">8dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"section_gap\">8dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"container_padding\">8dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"container_padding_compact\">4dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"sheet_content_padding\">8dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"dialog_content_padding\">12dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"row_gap\">8dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"row_gap_compact\">4dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"inline_gap\">4dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"inline_gap_compact\">2dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"field_padding_x\">12dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"field_padding_x_compact\">8dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"field_trailing_reserve\">16dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"field_trailing_reserve_compact\">12dp</dimen>"));
    assertTrue(xml.contains("<dimen name=\"icon_text_gap\">4dp</dimen>"));
}
```

- [ ] **Step 2: 写失败测试，锁定文字行高合同**

```java
@Test
public void textAppearancesShouldExposeGlobalLineHeightContract() throws Exception {
    String styles = readUtf8("src/main/res/values/styles.xml");

    assertTrue(styles.contains("TextAppearance.BinanceMonitor.Scale.PageHero"));
    assertTrue(styles.contains("<item name=\"android:lineHeight\">28sp</item>"));
    assertTrue(styles.contains("TextAppearance.BinanceMonitor.Scale.ValueHero"));
    assertTrue(styles.contains("<item name=\"android:lineHeight\">24sp</item>"));
    assertTrue(styles.contains("TextAppearance.BinanceMonitor.Scale.Body"));
    assertTrue(styles.contains("<item name=\"android:lineHeight\">20sp</item>"));
    assertTrue(styles.contains("TextAppearance.BinanceMonitor.Scale.Compact"));
    assertTrue(styles.contains("<item name=\"android:lineHeight\">16sp</item>"));
    assertTrue(styles.contains("TextAppearance.BinanceMonitor.Scale.Dense"));
    assertTrue(styles.contains("<item name=\"android:lineHeight\">14sp</item>"));
}
```

- [ ] **Step 3: 写失败测试，禁止非零 spacing literal 继续散落在 XML/Java**

```java
@Test
public void sourceShouldNotKeepNonZeroSpacingDpLiteralsOutsideAllowedFiles() throws Exception {
    Pattern dpLiteral = Pattern.compile("(?<!0)(\\d+(\\.\\d+)?)dp");
    List<Path> files = collectProjectFiles();
    for (Path file : files) {
        String normalized = file.toString().replace('\\', '/');
        if (normalized.endsWith("/res/values/dimens.xml")) {
            continue;
        }
        if (normalized.endsWith("/res/values/styles.xml")) {
            continue;
        }
        String source = Files.readString(file, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        Matcher matcher = dpLiteral.matcher(source);
        assertFalse("非零 dp 间距仍硬编码在: " + normalized, matcher.find());
    }
}
```

- [ ] **Step 4: 跑定向测试，确认当前仓库先失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.SpacingTokenContractResourceTest" --tests "com.binance.monitor.ui.theme.LineHeightContractResourceTest" --tests "com.binance.monitor.ui.theme.SpacingHardcodeBanSourceTest"
```

Expected:

- 新测试断言失败
- 当前仓库明确暴露“token 未定义 / 行高未统一 / 非零 dp 仍硬编码”

- [ ] **Step 5: 提交 Task 1**

```powershell
git add app/src/test/java/com/binance/monitor/ui/theme/SpacingTokenContractResourceTest.java app/src/test/java/com/binance/monitor/ui/theme/LineHeightContractResourceTest.java app/src/test/java/com/binance/monitor/ui/theme/SpacingHardcodeBanSourceTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java
git commit -m "test: lock global spacing contracts"
```

### Task 2: 重建资源层，固定基础阶梯、语义 token 和文字行高

**Files:**
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/SpacingTokenContractResourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/LineHeightContractResourceTest.java`

- [ ] **Step 1: 在 `dimens.xml` 中建立基础阶梯和语义 token**

```xml
<dimen name="space_2">2dp</dimen>
<dimen name="space_4">4dp</dimen>
<dimen name="space_8">8dp</dimen>
<dimen name="space_12">12dp</dimen>
<dimen name="space_16">16dp</dimen>
<dimen name="space_24">24dp</dimen>

<dimen name="screen_edge_padding">8dp</dimen>
<dimen name="section_gap">8dp</dimen>
<dimen name="container_padding">8dp</dimen>
<dimen name="container_padding_compact">4dp</dimen>
<dimen name="sheet_content_padding">8dp</dimen>
<dimen name="dialog_content_padding">12dp</dimen>
<dimen name="row_gap">8dp</dimen>
<dimen name="row_gap_compact">4dp</dimen>
<dimen name="inline_gap">4dp</dimen>
<dimen name="inline_gap_compact">2dp</dimen>
<dimen name="field_padding_x">12dp</dimen>
<dimen name="field_padding_x_compact">8dp</dimen>
<dimen name="field_trailing_reserve">16dp</dimen>
<dimen name="field_trailing_reserve_compact">12dp</dimen>
<dimen name="icon_text_gap">4dp</dimen>
<dimen name="list_item_padding_x">12dp</dimen>
<dimen name="list_item_padding_y">8dp</dimen>
```

- [ ] **Step 2: 在 `styles.xml` 中把行高统一接回文字体系**

```xml
<style name="TextAppearance.BinanceMonitor.Scale.PageHero" parent="TextAppearance.Material3.HeadlineSmall">
    <item name="android:textSize">22sp</item>
    <item name="android:lineHeight">28sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.ValueHero" parent="TextAppearance.Material3.TitleLarge">
    <item name="android:textSize">18sp</item>
    <item name="android:lineHeight">24sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.Body" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:textSize">14sp</item>
    <item name="android:lineHeight">20sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.Compact" parent="TextAppearance.Material3.BodySmall">
    <item name="android:textSize">12sp</item>
    <item name="android:lineHeight">16sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.Dense" parent="TextAppearance.Material3.BodySmall">
    <item name="android:textSize">10sp</item>
    <item name="android:lineHeight">14sp</item>
</style>
```

- [ ] **Step 3: 把标准主体 padding 改成只吃语义 token**

```xml
<style name="Widget.BinanceMonitor.Subject.ActionButton.Base" parent="Widget.Material3.Button">
    <item name="android:minHeight">@dimen/subject_height_md</item>
    <item name="android:paddingStart">@dimen/field_padding_x</item>
    <item name="android:paddingEnd">@dimen/field_padding_x</item>
    <item name="android:paddingTop">0dp</item>
    <item name="android:paddingBottom">0dp</item>
</style>

<style name="Widget.BinanceMonitor.Subject.TextTrigger.Compact" parent="Widget.BinanceMonitor.Subject.TextTrigger">
    <item name="android:minHeight">@dimen/subject_height_compact</item>
    <item name="android:paddingStart">@dimen/field_padding_x_compact</item>
    <item name="android:paddingEnd">@dimen/field_padding_x_compact</item>
</style>

<style name="Widget.BinanceMonitor.Subject.SelectField.Label" parent="">
    <item name="android:paddingStart">@dimen/field_padding_x</item>
    <item name="android:paddingEnd">@dimen/field_trailing_reserve</item>
</style>
```

- [ ] **Step 4: 跑资源合同测试，确认真值层成形**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.SpacingTokenContractResourceTest" --tests "com.binance.monitor.ui.theme.LineHeightContractResourceTest"
```

Expected:

- `BUILD SUCCESSFUL`
- token 与行高合同全部转绿

- [ ] **Step 5: 提交 Task 2**

```powershell
git add app/src/main/res/values/dimens.xml app/src/main/res/values/styles.xml app/src/main/res/values/themes.xml
git commit -m "refactor: define canonical spacing tokens"
```

### Task 3: 新建运行时尺寸解析入口，并把 `UiPaletteManager` 接回语义 token

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/theme/SpacingTokenResolver.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/SpacingHardcodeBanSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`

- [ ] **Step 1: 新建 `SpacingTokenResolver`，提供 Java 动态布局统一入口**

```java
package com.binance.monitor.ui.theme;

import android.content.Context;
import androidx.annotation.DimenRes;
import com.binance.monitor.R;

public final class SpacingTokenResolver {

    private SpacingTokenResolver() {
    }

    public static int px(Context context, @DimenRes int dimenResId) {
        return context.getResources().getDimensionPixelSize(dimenResId);
    }

    public static int screenEdgePx(Context context) {
        return px(context, R.dimen.screen_edge_padding);
    }

    public static int sectionGapPx(Context context) {
        return px(context, R.dimen.section_gap);
    }

    public static int containerPaddingPx(Context context) {
        return px(context, R.dimen.container_padding);
    }

    public static int dialogContentPaddingPx(Context context) {
        return px(context, R.dimen.dialog_content_padding);
    }

    public static int rowGapPx(Context context) {
        return px(context, R.dimen.row_gap);
    }

    public static int rowGapCompactPx(Context context) {
        return px(context, R.dimen.row_gap_compact);
    }

    public static int inlineGapPx(Context context) {
        return px(context, R.dimen.inline_gap);
    }

    public static int inlineGapCompactPx(Context context) {
        return px(context, R.dimen.inline_gap_compact);
    }

    public static float dpFloat(Context context, @DimenRes int dimenResId) {
        return context.getResources().getDimension(dimenResId);
    }
}
```

- [ ] **Step 2: 把 `UiPaletteManager` 抽屉外边距和主体 padding 改成通过 resolver 取值**

```java
int horizontalMargin = SpacingTokenResolver.screenEdgePx(dialog.getContext());
marginParams.leftMargin = horizontalMargin;
marginParams.rightMargin = horizontalMargin;

int horizontalPaddingPx = SpacingTokenResolver.px(context, R.dimen.field_padding_x);
button.setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0);
```

- [ ] **Step 3: 清理 `UiPaletteManager` 里表达语义间距的直接 `dp(context, value)`**

```java
label.setPadding(
        SpacingTokenResolver.px(label.getContext(), R.dimen.field_padding_x),
        0,
        SpacingTokenResolver.px(label.getContext(), R.dimen.field_trailing_reserve),
        0
);
```

- [ ] **Step 4: 跑运行时入口相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest" --tests "com.binance.monitor.ui.theme.SpacingHardcodeBanSourceTest"
```

Expected:

- `UiPaletteManager` 不再被新测试抓出语义间距硬编码

- [ ] **Step 5: 提交 Task 3**

```powershell
git add app/src/main/java/com/binance/monitor/ui/theme/SpacingTokenResolver.java app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java
git commit -m "refactor: add runtime spacing token resolver"
```

### Task 4: 把主页面和抽屉 XML 统一到语义 token

**Files:**
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/res/layout/content_account_position.xml`
- Modify: `app/src/main/res/layout/content_account_stats.xml`
- Modify: `app/src/main/res/layout/content_settings.xml`
- Modify: `app/src/main/res/layout/dialog_global_status_sheet.xml`
- Modify: `app/src/main/res/layout/dialog_account_connection_sheet.xml`
- Modify: `app/src/main/res/layout/dialog_account_trade_history_sheet.xml`
- Modify: `app/src/main/res/layout/dialog_indicator_params.xml`
- Modify: `app/src/main/res/layout/activity_main_host.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionLayoutResourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`

- [ ] **Step 1: 页面根容器统一接到 `screen_edge_padding / section_gap`**

```xml
android:paddingStart="@dimen/screen_edge_padding"
android:paddingEnd="@dimen/screen_edge_padding"
android:paddingTop="@dimen/screen_edge_padding"
android:paddingBottom="@dimen/screen_edge_padding"

android:layout_marginTop="@dimen/section_gap"
```

- [ ] **Step 2: 抽屉和弹窗内容区统一接到 `sheet_content_padding / dialog_content_padding`**

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="@dimen/sheet_content_padding">
```

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="@dimen/dialog_content_padding">
```

- [ ] **Step 3: 行间距、按钮组间距和字段内边距统一接到 `row_gap / inline_gap / field_padding_x*`**

```xml
android:layout_marginTop="@dimen/row_gap"
android:layout_marginStart="@dimen/inline_gap"
android:layout_marginEnd="@dimen/inline_gap_compact"
android:paddingStart="@dimen/field_padding_x_compact"
android:paddingEnd="@dimen/field_trailing_reserve_compact"
android:drawablePadding="@dimen/icon_text_gap"
```

- [ ] **Step 4: 跑布局资源回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeLayoutCompatibilitySourceTest"
```

Expected:

- 页面根边距、模块间距、抽屉内边距和按钮组间距全部改用新 token

- [ ] **Step 5: 提交 Task 4**

```powershell
git add app/src/main/res/layout/activity_market_chart.xml app/src/main/res/layout/content_account_position.xml app/src/main/res/layout/content_account_stats.xml app/src/main/res/layout/content_settings.xml app/src/main/res/layout/dialog_global_status_sheet.xml app/src/main/res/layout/dialog_account_connection_sheet.xml app/src/main/res/layout/dialog_account_trade_history_sheet.xml app/src/main/res/layout/dialog_indicator_params.xml app/src/main/res/layout/activity_main_host.xml
git commit -m "refactor: migrate layouts to spacing tokens"
```

### Task 5: 把 Java 动态布局和桥接页 padding/margin 改成 resolver，不再自己 `dpToPx(...)`

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTopControlStyleSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowManagerSourceTest.java`

- [ ] **Step 1: 把字段 trailing reserve、箭头间距、按钮 padding 统一改成 resolver**

```java
binding.tvChartSymbolPickerLabel.setCompoundDrawablePadding(
        SpacingTokenResolver.inlineGapPx(this)
);
label.setPadding(
        SpacingTokenResolver.px(this, R.dimen.field_padding_x_compact),
        0,
        SpacingTokenResolver.px(this, R.dimen.field_trailing_reserve_compact),
        0
);
```

- [ ] **Step 2: 把 Java 构造的弹窗容器 padding 与行间距统一改成 resolver**

```java
int horizontal = SpacingTokenResolver.px(this, R.dimen.dialog_content_padding);
int top = SpacingTokenResolver.px(this, R.dimen.row_gap);
int bottom = SpacingTokenResolver.px(this, R.dimen.row_gap_compact);
container.setPadding(horizontal, top, horizontal, bottom);

rowParams.topMargin = SpacingTokenResolver.rowGapPx(this);
continueParams.leftMargin = SpacingTokenResolver.inlineGapPx(this);
```

- [ ] **Step 3: 把运行时创建的列表行和信息卡 padding 统一改成 `list_item_padding_x / list_item_padding_y`**

```java
row.setPadding(
        SpacingTokenResolver.px(this, R.dimen.list_item_padding_x),
        SpacingTokenResolver.px(this, R.dimen.list_item_padding_y),
        SpacingTokenResolver.px(this, R.dimen.list_item_padding_x),
        SpacingTokenResolver.px(this, R.dimen.list_item_padding_y)
);
```

- [ ] **Step 4: 跑源码回归，确认动态布局主链不再自己发明间距**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartTopControlStyleSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest" --tests "com.binance.monitor.ui.theme.SpacingHardcodeBanSourceTest"
```

Expected:

- 这些动态布局主链里的非零语义间距不再以 `dpToPx(6)`、`dp(10)` 方式存在

- [ ] **Step 5: 提交 Task 5**

```powershell
git add app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java
git commit -m "refactor: route runtime spacing through resolver"
```

### Task 6: 把自定义 helper 和几何留白抽成 token，清掉 helper 内硬编码 dp

**Files:**
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowLayoutHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/CurvePaneLayoutHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlinePaneTextLayoutHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/widget/TradeScrollBarView.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/SpacingHardcodeBanSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowManagerSourceTest.java`

- [ ] **Step 1: 在 `dimens.xml` 中为组件几何尺寸建立专用 token**

```xml
<dimen name="floating_window_expanded_width">90dp</dimen>
<dimen name="floating_window_padding_x">4dp</dimen>
<dimen name="floating_window_mini_size">14dp</dimen>
<dimen name="curve_chart_left_inset">34dp</dimen>
<dimen name="curve_chart_right_inset">28dp</dimen>
<dimen name="curve_axis_stroke">1dp</dimen>
<dimen name="kline_indicator_plot_top_inset">12dp</dimen>
<dimen name="kline_indicator_plot_bottom_inset">10dp</dimen>
<dimen name="trade_scrollbar_track_width">4dp</dimen>
<dimen name="trade_scrollbar_thumb_width">10dp</dimen>
```

- [ ] **Step 2: 把 helper 从“返回裸 dp 数值”改成“返回 dimen 资源 id 或通过 resolver 取值”**

```java
static @DimenRes int resolveExpandedWidthRes() {
    return R.dimen.floating_window_expanded_width;
}

static @DimenRes int resolveChartLeftInsetRes() {
    return R.dimen.curve_chart_left_inset;
}
```

- [ ] **Step 3: 把使用端改成统一从 token 取值**

```java
expandedParams.width = SpacingTokenResolver.px(this, FloatingWindowLayoutHelper.resolveExpandedWidthRes());
chartLeft = SpacingTokenResolver.dpFloat(getContext(), CurvePaneLayoutHelper.resolveChartLeftInsetRes());
```

- [ ] **Step 4: 跑 helper 与悬浮窗相关回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest" --tests "com.binance.monitor.ui.theme.SpacingHardcodeBanSourceTest"
```

Expected:

- helper 文件不再持有语义间距硬编码

- [ ] **Step 5: 提交 Task 6**

```powershell
git add app/src/main/res/values/dimens.xml app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowLayoutHelper.java app/src/main/java/com/binance/monitor/ui/account/CurvePaneLayoutHelper.java app/src/main/java/com/binance/monitor/ui/chart/KlinePaneTextLayoutHelper.java app/src/main/java/com/binance/monitor/ui/widget/TradeScrollBarView.java
git commit -m "refactor: extract helper spacing metrics into tokens"
```

### Task 7: 删除旧尺寸名与遗留硬编码，保证只有全局尺寸系统在工作

**Files:**
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/SpacingHardcodeBanSourceTest.java`
- Modify: 所有仍引用旧尺寸名的主链文件

- [ ] **Step 1: 删除或停用旧语义尺寸名**

删除目标至少包括：

```text
page_horizontal_padding
page_section_gap
card_content_padding
control_group_gap
global_status_sheet_row_gap
chart_overlay_toggle_gap
chart_indicator_option_gap
chart_indicator_option_padding_x
chart_top_mode_button_padding_x
chart_symbol_select_field_trailing_reserve
subject_padding_x_md
subject_padding_x_compact
subject_select_field_trailing_reserve
subject_group_gap_md
position_row_header_padding_horizontal
position_row_header_padding_vertical
```

- [ ] **Step 2: 把仍依赖旧名的页面全部切到新 token**

```xml
android:padding="@dimen/container_padding"
android:layout_marginTop="@dimen/row_gap"
android:layout_marginStart="@dimen/inline_gap"
```

- [ ] **Step 3: 把硬编码禁令测试改成最终口径**

```java
assertFalse("仍存在非零 padding dp literal: " + normalized,
        source.contains("padding=\"6dp\""));
assertFalse("仍存在非零 margin dp literal: " + normalized,
        source.contains("layout_marginTop=\"10dp\""));
```

- [ ] **Step 4: 跑最终硬编码回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.SpacingHardcodeBanSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest"
```

Expected:

- 旧尺寸名不再出现在主链
- 非零 spacing literal 不再出现在允许范围外

- [ ] **Step 5: 提交 Task 7**

```powershell
git add app/src/main/res/values/dimens.xml app/src/test/java/com/binance/monitor/ui/theme/SpacingHardcodeBanSourceTest.java
git commit -m "refactor: remove legacy spacing names"
```

### Task 8: 同步文档并做总验收

**Files:**
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 在 README 中补全局尺寸系统规则**

补充内容至少包括：

```markdown
- 基础尺度阶梯只允许 2 / 4 / 8 / 12 / 16 / 24dp
- 页面、抽屉、弹窗、容器、行、控件只允许消费语义 token
- 非零 spacing literal 禁止直接写入 XML 与 Java
- 行高只允许在文字体系中统一维护
```

- [ ] **Step 2: 在 ARCHITECTURE 中补三层真值结构**

补充内容至少包括：

```markdown
- `dimens.xml`：基础阶梯、语义 token、组件几何 token
- `styles.xml`：文字行高、标准主体 padding
- `SpacingTokenResolver`：Java 动态布局唯一尺寸解析入口
```

- [ ] **Step 3: 在 CONTEXT 中记录停点、关键决定和验证结果**

补充内容至少包括：

```markdown
- 当前正在做什么：全局尺寸系统收口
- 上次停在哪个位置：资源层、运行时、布局、helper、遗留清理分别到哪一步
- 近期关键决定和原因：为什么只保留 2/4/8/12/16/24 这组基础阶梯
```

- [ ] **Step 4: 跑总验收测试与编译**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.SpacingTokenContractResourceTest" --tests "com.binance.monitor.ui.theme.LineHeightContractResourceTest" --tests "com.binance.monitor.ui.theme.SpacingHardcodeBanSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTopControlStyleSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartOverlayToggleLayoutSourceTest" --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeLayoutCompatibilitySourceTest" --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest"
.\gradlew.bat :app:assembleDebug
```

Expected:

- 所有尺寸合同、布局回归和源码禁令测试通过
- `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交 Task 8**

```powershell
git add README.md ARCHITECTURE.md CONTEXT.md docs/superpowers/plans/2026-04-19-global-spacing-system-unification-implementation.md
git commit -m "docs: record global spacing system plan"
```

---

## Self-Review

### Spec coverage

- 页面与手机边缘距离：由 `screen_edge_padding` 和 Task 4 覆盖。
- 容器与容器之间距离：由 `section_gap / row_gap / inline_gap` 和 Task 4 覆盖。
- 容器内部元素与边界距离：由 `container_padding / field_padding_x / list_item_padding_*` 和 Task 2、Task 4、Task 5 覆盖。
- 文字元素行间距：由 Task 1、Task 2 的 `lineHeight` 合同覆盖。
- 不准写死在代码里：由 Task 1、Task 3、Task 5、Task 6、Task 7 的硬编码禁令覆盖。

### Placeholder scan

- 本计划未使用 `TODO / TBD / implement later / similar to above` 等占位描述。
- 每个改代码步骤都给了实际 XML/Java/JUnit 片段。
- 每个任务都附了明确的运行命令和期望输出。

### Type consistency

- 运行时统一入口固定为 `SpacingTokenResolver`，没有再发明第二套 resolver 名称。
- 语义 token 命名全程固定为 `screen_edge_padding / section_gap / container_padding / row_gap / inline_gap / field_padding_x / field_trailing_reserve`。
- helper 迁移统一走“返回 resId 或 resolver 取值”这条链，没有混成一半 resId、一半裸 dp。
