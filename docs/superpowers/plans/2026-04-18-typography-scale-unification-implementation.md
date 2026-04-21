# Typography Scale Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 APP 当前分散的字号定义统一收口为 `22 / 18 / 16 / 14 / 12 / 10sp` 六档，并强制所有 XML、运行时 TextView、自定义 View、图表和悬浮窗都从 `TextAppearance` 取字号。

**Architecture:** 以 `styles.xml` 中的 6 个基础字号样式为唯一真值来源。XML 统一只用 `textAppearance`，运行时 TextView 统一用共享 helper 套用 `TextAppearance`，自定义 View / Canvas 统一通过共享解析器从 `TextAppearance` 读取 `sp` 转换后的像素值，彻底删除布局直写字号、`setTextSize(float)` 和 `dp` 文字尺寸。

**Tech Stack:** Android XML、Java、Material3、TextViewCompat、JUnit4 资源/源码测试

---

### Task 1: 锁定“6 档字号 + 禁止回退”合同

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/theme/TypographyScaleContractSourceTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/theme/TypographyHardcodeBanSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java`

- [ ] **Step 1: 写失败测试，锁定正式字号表**

```java
/*
 * 锁定全局字号真值，只允许 6 档正式字号。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypographyScaleContractSourceTest {

    @Test
    public void stylesShouldCollapseTypographyIntoSixSpLevels() throws Exception {
        String styles = readUtf8("src/main/res/values/styles.xml");

        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.PageHero\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.ValueHero\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.Section\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.Body\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.Compact\""));
        assertTrue(styles.contains("<style name=\"TextAppearance.BinanceMonitor.Scale.Dense\""));

        Set<String> actual = extractSpValues(styles);
        Set<String> expected = new LinkedHashSet<>(Arrays.asList("22", "18", "16", "14", "12", "10"));
        assertEquals(expected, actual);
    }

    private static Set<String> extractSpValues(String styles) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("<item name=\"android:textSize\">([0-9.]+)sp</item>").matcher(styles);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return values;
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
```

- [ ] **Step 2: 写失败测试，禁止布局和源码继续硬编码字号**

```java
/*
 * 禁止布局、Java 和图表绘制继续定义局部字号，所有字号都必须回到 TextAppearance。
 */
package com.binance.monitor.ui.theme;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class TypographyHardcodeBanSourceTest {

    private static final List<String> XML_FILES = Arrays.asList(
            "src/main/res/layout/activity_account_position.xml",
            "src/main/res/layout/activity_account_stats.xml",
            "src/main/res/layout/activity_main.xml",
            "src/main/res/layout/activity_market_chart.xml",
            "src/main/res/layout/activity_settings_detail.xml",
            "src/main/res/layout/content_account_position.xml",
            "src/main/res/layout/content_account_stats.xml",
            "src/main/res/layout/content_settings.xml",
            "src/main/res/layout/dialog_abnormal_threshold_settings.xml",
            "src/main/res/layout/dialog_indicator_params.xml",
            "src/main/res/layout/dialog_trade_command.xml",
            "src/main/res/layout/layout_floating_window.xml"
    );

    private static final List<String> JAVA_FILES = Arrays.asList(
            "src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java",
            "src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java",
            "src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java",
            "src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java",
            "src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java",
            "src/main/java/com/binance/monitor/ui/account/EquityCurveView.java",
            "src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java",
            "src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java",
            "src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java",
            "src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java",
            "src/main/java/com/binance/monitor/ui/account/TradeWeekdayBarChartView.java",
            "src/main/java/com/binance/monitor/ui/chart/KlineChartView.java",
            "src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java",
            "src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java",
            "src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java",
            "src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java",
            "src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java",
            "src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java"
    );

    @Test
    public void layoutsShouldNotDefineTextSizeDirectly() throws Exception {
        for (String file : XML_FILES) {
            String text = readUtf8(file);
            assertFalse(file, text.contains("android:textSize=\""));
        }
    }

    @Test
    public void javaShouldNotUseLiteralTextSizeOrDpTextSize() throws Exception {
        Pattern literalSetTextSize = Pattern.compile("setTextSize\\([^\\n]*[0-9]+(?:\\.[0-9]+)?f\\)");
        Pattern dpPaintSize = Pattern.compile("setTextSize\\(dp\\([0-9]+(?:\\.[0-9]+)?f\\)\\)");

        for (String file : JAVA_FILES) {
            String text = readUtf8(file);
            assertFalse(file, literalSetTextSize.matcher(text).find());
            assertFalse(file, dpPaintSize.matcher(text).find());
        }
    }

    private static String readUtf8(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
```

- [ ] **Step 3: 扩充现有视觉 token 测试，锁定新字号集合**

```java
assertTrue(styles.contains("<item name=\"android:textSize\">22sp</item>"));
assertTrue(styles.contains("<item name=\"android:textSize\">18sp</item>"));
assertTrue(styles.contains("<item name=\"android:textSize\">16sp</item>"));
assertTrue(styles.contains("<item name=\"android:textSize\">14sp</item>"));
assertTrue(styles.contains("<item name=\"android:textSize\">12sp</item>"));
assertTrue(styles.contains("<item name=\"android:textSize\">10sp</item>"));
assertFalse(styles.contains("10.5sp"));
assertFalse(styles.contains("11sp"));
assertFalse(styles.contains("13sp"));
assertFalse(styles.contains("15sp"));
```

- [ ] **Step 4: 跑测试确认当前是红灯**

Run: `.\\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.TypographyScaleContractSourceTest" --tests "com.binance.monitor.ui.theme.TypographyHardcodeBanSourceTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest"`

Expected: FAIL，提示 `styles.xml` 仍包含旧字号，且目标布局/源码仍存在 `textSize` 硬编码。

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/binance/monitor/ui/theme/TypographyScaleContractSourceTest.java app/src/test/java/com/binance/monitor/ui/theme/TypographyHardcodeBanSourceTest.java app/src/test/java/com/binance/monitor/ui/theme/VisualDesignTokenResourceTest.java
git commit -m "test: lock six-level typography contract"
```

### Task 2: 建立唯一字号真值与样式解析入口

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/theme/TextAppearanceScaleResolver.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/dimens.xml`

- [ ] **Step 1: 写最小实现，新增 `TextAppearance` 字号解析器**

```java
/*
 * 从 TextAppearance 读取统一字号，供自定义 View 与 Paint 复用，避免局部硬编码。
 */
package com.binance.monitor.ui.theme;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.StyleRes;
import androidx.core.widget.TextViewCompat;

public final class TextAppearanceScaleResolver {

    private TextAppearanceScaleResolver() {
    }

    public static float resolveTextSizePx(Context context, @StyleRes int textAppearanceResId) {
        TypedArray typedArray = context.obtainStyledAttributes(textAppearanceResId, new int[]{android.R.attr.textSize});
        try {
            return typedArray.getDimension(0, 0f);
        } finally {
            typedArray.recycle();
        }
    }

    public static void applyTextAppearance(TextView textView, @StyleRes int textAppearanceResId) {
        TextViewCompat.setTextAppearance(textView, textAppearanceResId);
    }

    public static void applyTextSize(Paint paint, Context context, @StyleRes int textAppearanceResId) {
        paint.setTextSize(resolveTextSizePx(context, textAppearanceResId));
    }

    public static float resolveTextSizeSp(Context context, @StyleRes int textAppearanceResId) {
        float px = resolveTextSizePx(context, textAppearanceResId);
        return px / context.getResources().getDisplayMetrics().scaledDensity;
    }
}
```

- [ ] **Step 2: 把 `styles.xml` 改成 6 档基础字号 + 语义映射**

```xml
<style name="TextAppearance.BinanceMonitor.Scale.PageHero" parent="TextAppearance.Material3.HeadlineSmall">
    <item name="android:fontFamily">sans-serif-medium</item>
    <item name="android:textColor">@color/text_primary</item>
    <item name="android:textSize">22sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.ValueHero" parent="TextAppearance.Material3.TitleLarge">
    <item name="android:fontFamily">sans-serif-medium</item>
    <item name="android:textColor">@color/text_primary</item>
    <item name="android:textSize">18sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.Section" parent="TextAppearance.Material3.TitleMedium">
    <item name="android:fontFamily">sans-serif-medium</item>
    <item name="android:textColor">@color/text_primary</item>
    <item name="android:textSize">16sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.Body" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:fontFamily">sans-serif</item>
    <item name="android:textColor">@color/text_primary</item>
    <item name="android:textSize">14sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.Compact" parent="TextAppearance.Material3.BodySmall">
    <item name="android:fontFamily">sans-serif</item>
    <item name="android:textColor">@color/text_secondary</item>
    <item name="android:textSize">12sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.Scale.Dense" parent="TextAppearance.Material3.BodySmall">
    <item name="android:fontFamily">sans-serif</item>
    <item name="android:textColor">@color/text_secondary</item>
    <item name="android:textSize">10sp</item>
</style>

<style name="TextAppearance.BinanceMonitor.PageTitle" parent="@style/TextAppearance.BinanceMonitor.Scale.PageHero" />
<style name="TextAppearance.BinanceMonitor.Value" parent="@style/TextAppearance.BinanceMonitor.Scale.ValueHero" />
<style name="TextAppearance.BinanceMonitor.SectionTitle" parent="@style/TextAppearance.BinanceMonitor.Scale.Section" />
<style name="TextAppearance.BinanceMonitor.Body" parent="@style/TextAppearance.BinanceMonitor.Scale.Body" />
<style name="TextAppearance.BinanceMonitor.SectionLabel" parent="@style/TextAppearance.BinanceMonitor.Scale.Body" />
<style name="TextAppearance.BinanceMonitor.BodyCompact" parent="@style/TextAppearance.BinanceMonitor.Scale.Body" />
<style name="TextAppearance.BinanceMonitor.Caption" parent="@style/TextAppearance.BinanceMonitor.Scale.Compact" />
<style name="TextAppearance.BinanceMonitor.Meta" parent="@style/TextAppearance.BinanceMonitor.Scale.Compact" />
<style name="TextAppearance.BinanceMonitor.Control" parent="@style/TextAppearance.BinanceMonitor.Scale.Compact" />
<style name="TextAppearance.BinanceMonitor.ControlCompact" parent="@style/TextAppearance.BinanceMonitor.Scale.Compact" />
<style name="TextAppearance.BinanceMonitor.ValueCompact" parent="@style/TextAppearance.BinanceMonitor.Scale.Compact" />
<style name="TextAppearance.BinanceMonitor.Micro" parent="@style/TextAppearance.BinanceMonitor.Scale.Dense" />
<style name="TextAppearance.BinanceMonitor.Tiny" parent="@style/TextAppearance.BinanceMonitor.Scale.Dense" />
<style name="TextAppearance.BinanceMonitor.ChartDense" parent="@style/TextAppearance.BinanceMonitor.Scale.Dense" />
<style name="TextAppearance.BinanceMonitor.ChartCompact" parent="@style/TextAppearance.BinanceMonitor.Scale.Compact" />
<style name="TextAppearance.BinanceMonitor.OverlayDense" parent="@style/TextAppearance.BinanceMonitor.Scale.Dense" />
```

- [ ] **Step 3: 删除旧的文字尺寸 dimen，并让 `UiPaletteManager` 接收样式而不是 float**

```java
public static void applyTextAppearance(TextView textView, @StyleRes int textAppearanceResId) {
    TextAppearanceScaleResolver.applyTextAppearance(textView, textAppearanceResId);
}

public static void styleSquareTextAction(TextView button,
                                         Palette palette,
                                         int fillColor,
                                         int textColor,
                                         @StyleRes int textAppearanceResId,
                                         int horizontalPaddingDp,
                                         @DimenRes int minHeightResId,
                                         boolean selected) {
    button.setBackground(createFilledDrawable(button.getContext(), fillColor));
    button.setTextColor(textColor);
    applyTextAppearance(button, textAppearanceResId);
    button.setMinHeight(button.getResources().getDimensionPixelSize(minHeightResId));
    int horizontalPaddingPx = dp(button.getContext(), horizontalPaddingDp);
    button.setPadding(horizontalPaddingPx, 0, horizontalPaddingPx, 0);
    button.setSelected(selected);
}
```

- [ ] **Step 4: 跑定向测试确认样式真值转绿**

Run: `.\\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.TypographyScaleContractSourceTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/theme/TextAppearanceScaleResolver.java app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java app/src/main/res/values/styles.xml app/src/main/res/values/dimens.xml
git commit -m "refactor: centralize typography scale tokens"
```

### Task 3: 把 XML 布局全面迁回 `textAppearance`

**Files:**
- Modify: `app/src/main/res/layout/activity_account_position.xml`
- Modify: `app/src/main/res/layout/activity_account_stats.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/res/layout/activity_settings_detail.xml`
- Modify: `app/src/main/res/layout/content_account_position.xml`
- Modify: `app/src/main/res/layout/content_account_stats.xml`
- Modify: `app/src/main/res/layout/content_settings.xml`
- Modify: `app/src/main/res/layout/dialog_abnormal_threshold_settings.xml`
- Modify: `app/src/main/res/layout/dialog_indicator_params.xml`
- Modify: `app/src/main/res/layout/dialog_trade_command.xml`
- Modify: `app/src/main/res/layout/layout_floating_window.xml`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/TypographyResourceUsageTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeLayoutCompatibilitySourceTest.java`

- [ ] **Step 1: 用 `textAppearance` 替换布局直写字号**

```xml
<TextView
    android:id="@+id/tvCurrentPrice"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="14dp"
    android:maxLines="1"
    android:text="--"
    android:textAppearance="@style/TextAppearance.BinanceMonitor.PageTitle" />

<TextView
    android:id="@+id/tvOverlayStatus"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:maxLines="1"
    android:text="0.00"
    android:textAppearance="@style/TextAppearance.BinanceMonitor.Control" />

<TextView
    android:id="@+id/tvOverlayConnection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="4dp"
    android:maxLines="1"
    android:text="实时行情连接中"
    android:textAppearance="@style/TextAppearance.BinanceMonitor.OverlayDense" />
```

- [ ] **Step 2: 删除图表布局中的 `@dimen/chart_price_info_text_size` 依赖**

```xml
<TextView
    android:id="@+id/tvChartPositionSummary"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textAppearance="@style/TextAppearance.BinanceMonitor.ChartCompact" />
```

- [ ] **Step 3: 更新布局资源测试，改为检查共享样式引用**

```java
assertTrue(layout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.PageTitle\""));
assertTrue(layout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.Control\""));
assertTrue(layout.contains("android:textAppearance=\"@style/TextAppearance.BinanceMonitor.ChartCompact\""));
assertFalse(layout.contains("android:textSize=\""));
assertFalse(layout.contains("chart_price_info_text_size"));
```

- [ ] **Step 4: 跑定向测试确认布局层转绿**

Run: `.\\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.TypographyHardcodeBanSourceTest" --tests "com.binance.monitor.ui.theme.TypographyResourceUsageTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeLayoutCompatibilitySourceTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_account_position.xml app/src/main/res/layout/activity_account_stats.xml app/src/main/res/layout/activity_main.xml app/src/main/res/layout/activity_market_chart.xml app/src/main/res/layout/activity_settings_detail.xml app/src/main/res/layout/content_account_position.xml app/src/main/res/layout/content_account_stats.xml app/src/main/res/layout/content_settings.xml app/src/main/res/layout/dialog_abnormal_threshold_settings.xml app/src/main/res/layout/dialog_indicator_params.xml app/src/main/res/layout/dialog_trade_command.xml app/src/main/res/layout/layout_floating_window.xml app/src/test/java/com/binance/monitor/ui/theme/TypographyResourceUsageTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeLayoutCompatibilitySourceTest.java
git commit -m "refactor: migrate layouts to shared text appearances"
```

### Task 4: 把运行时 TextView 统一迁回 TextAppearance

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java`

- [ ] **Step 1: 替换动态创建 TextView / EditText 的 `setTextSize(float)`**

```java
TextView titleView = new TextView(context);
UiPaletteManager.applyTextAppearance(titleView, R.style.TextAppearance_BinanceMonitor_Value);

TextView subtitleView = new TextView(context);
UiPaletteManager.applyTextAppearance(subtitleView, R.style.TextAppearance_BinanceMonitor_Meta);

EditText input = new EditText(context);
UiPaletteManager.applyTextAppearance(input, R.style.TextAppearance_BinanceMonitor_Body);
```

- [ ] **Step 2: 把 `UiPaletteManager` 所有收文字样式的 API 改成接收 `@StyleRes`**

```java
UiPaletteManager.styleSquareTextAction(
        button,
        palette,
        fillColor,
        textColor,
        R.style.TextAppearance_BinanceMonitor_Control,
        8,
        R.dimen.control_height_md,
        selected
);
```

- [ ] **Step 3: 让 `ThemedNumberPicker` 也从样式取字号**

```java
float denseTextSizePx = TextAppearanceScaleResolver.resolveTextSizePx(
        context,
        R.style.TextAppearance_BinanceMonitor_ChartDense
);
paint.setTextSize(denseTextSizePx);
editText.setTextSize(TypedValue.COMPLEX_UNIT_PX, denseTextSizePx);
```

- [ ] **Step 4: 跑定向测试确认运行时控件不再含数字字号**

Run: `.\\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.TypographyHardcodeBanSourceTest" --tests "com.binance.monitor.ui.account.AccountSessionDialogControllerSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsScreenBootstrapSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java app/src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java
git commit -m "refactor: route runtime text views through text appearances"
```

### Task 5: 把图表、自定义 View、悬浮窗绘制统一改成“读取 TextAppearance 后再画”

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/TradeWeekdayBarChartView.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`

- [ ] **Step 1: 把 `Paint` 字号改为统一从 `TextAppearanceScaleResolver` 读取**

```java
TextAppearanceScaleResolver.applyTextSize(
        crossLabelTextPaint,
        getContext(),
        R.style.TextAppearance_BinanceMonitor_ChartDense
);
TextAppearanceScaleResolver.applyTextSize(
        popupTextPaint,
        getContext(),
        R.style.TextAppearance_BinanceMonitor_ChartCompact
);
TextAppearanceScaleResolver.applyTextSize(
        tooltipTextPaint,
        getContext(),
        R.style.TextAppearance_BinanceMonitor_ChartCompact
);
TextAppearanceScaleResolver.applyTextSize(
        emptyPaint,
        getContext(),
        R.style.TextAppearance_BinanceMonitor_Meta
);
```

- [ ] **Step 2: 删除图表和悬浮窗里所有 `dp(9f)`、`8.5f`、`10.5f` 这类局部字号**

```java
// 删除旧写法
// crossLabelTextPaint.setTextSize(dp(9f));
// overlayLabelTextPaint.setTextSize(dp(8f));
// amountView.setTextSize(8.5f);

// 改为统一样式来源
UiPaletteManager.applyTextAppearance(amountView, R.style.TextAppearance_BinanceMonitor_OverlayDense);
```

- [ ] **Step 3: 更新源码测试，锁定图表必须使用样式解析器**

```java
assertTrue(source.contains("TextAppearanceScaleResolver.applyTextSize("));
assertTrue(source.contains("R.style.TextAppearance_BinanceMonitor_ChartDense"));
assertTrue(source.contains("R.style.TextAppearance_BinanceMonitor_ChartCompact"));
assertFalse(source.contains("setTextSize(dp("));
assertFalse(source.contains("setTextSize(9f)"));
assertFalse(source.contains("setTextSize(8.5f)"));
```

- [ ] **Step 4: 跑定向测试确认图表与悬浮窗主链转绿**

Run: `.\\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.TypographyHardcodeBanSourceTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerTitleSourceTest" --tests "com.binance.monitor.ui.account.CurvePaneLegendAlignmentSourceTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java app/src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java app/src/main/java/com/binance/monitor/ui/account/TradeWeekdayBarChartView.java app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java
git commit -m "refactor: make chart and overlay text read from text appearances"
```

### Task 6: 全量验证与文档同步

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: 跑全量主题/布局/源码回归**

Run: `.\\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.TypographyScaleContractSourceTest" --tests "com.binance.monitor.ui.theme.TypographyHardcodeBanSourceTest" --tests "com.binance.monitor.ui.theme.TypographyResourceUsageTest" --tests "com.binance.monitor.ui.theme.VisualDesignTokenResourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeLayoutCompatibilitySourceTest"`

Expected: PASS

- [ ] **Step 2: 跑编译验证**

Run: `.\\gradlew.bat :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 更新项目记录与架构文档**

```md
- 最新续做已把全局字号统一收口到 `22 / 18 / 16 / 14 / 12 / 10sp` 六档；XML、运行时 TextView、自定义 View、图表和悬浮窗现在都从 `TextAppearance` 取字号，不再允许硬编码、补缝字号或 `dp` 文字尺寸。
```

- [ ] **Step 4: Commit**

```bash
git add CONTEXT.md README.md ARCHITECTURE.md
git commit -m "docs: record unified typography scale architecture"
```
