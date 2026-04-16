# BTCXAU UI 重做 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 APP 的一级结构重组为 `交易 / 账户 / 分析 / 设置`，去掉独立仪表盘，完成深色统一视觉、交易首页、账户页、分析页以及顶部状态按钮/底部弹层的首轮落地。

**Architecture:** 继续沿用当前 `MainHostActivity + HostTabNavigator + Fragment + 共享 Screen` 结构，不新建第二套导航框架。实现顺序先从主题 token 与主壳底部导航开始，再重做 `交易` 页，最后收口 `账户`、`分析`、`设置` 和底部弹层，确保每一步都能独立编译、独立验证。

**Tech Stack:** Android Java、XML、ViewBinding、Material3、现有 Source Test / Resource Test、Gradle (`:app:testDebugUnitTest`, `:app:assembleDebug`)

---

## File Map

### Existing files to modify

- `app/src/main/java/com/binance/monitor/ui/host/MainHostActivity.java`
  主壳 Activity，负责默认 tab、底部导航点击、系统栏主题同步。
- `app/src/main/java/com/binance/monitor/ui/host/HostTab.java`
  一级 tab 枚举，负责稳定 key 与路由映射。
- `app/src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java`
  tab -> Fragment 映射与 show/hide 逻辑。
- `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
  主题 token、底部导航样式、系统栏样式。
- `app/src/main/res/layout/activity_main_host.xml`
  主壳底部导航布局。
- `app/src/main/res/layout/activity_market_chart.xml`
  `交易` 页共享主布局。
- `app/src/main/res/layout/content_account_position.xml`
  `账户` 页共享主布局。
- `app/src/main/res/layout/content_account_stats.xml`
  `分析` 页共享主布局。
- `app/src/main/res/layout/content_settings.xml`
  `设置` 页共享主布局。
- `app/src/main/res/values/colors.xml`
  深色主题色 token。
- `app/src/main/res/values/styles.xml`
  文本、按钮、卡片样式。
- `app/src/main/res/values/themes.xml`
  App 主题入口。
- `app/src/main/res/values/strings.xml`
  一级导航与新页面文案。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java`
  `交易` 页 Fragment 入口。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  `交易` 页共享屏幕对象，适合接入状态按钮和快看弹层触发。
- `app/src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java`
  `账户` 页 Fragment 入口。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java`
  `分析` 页 Fragment 入口。
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsFragment.java`
  `设置` 页 Fragment 入口。

### New files to create

- `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
  顶部状态按钮的底部弹层控制器。
- `app/src/main/java/com/binance/monitor/ui/chart/TradeAlertBottomSheetController.java`
  交易页异常快看弹层控制器。
- `app/src/main/res/layout/dialog_global_status_sheet.xml`
  全局状态弹层布局。
- `app/src/main/res/layout/dialog_trade_alert_sheet.xml`
  交易页异常快看弹层布局。
- `app/src/test/java/com/binance/monitor/ui/host/MainHostTradingIaSourceTest.java`
  新一级结构和默认页 source test。
- `app/src/test/java/com/binance/monitor/ui/chart/TradeHomeRedesignSourceTest.java`
  交易首页结构 source test。
- `app/src/test/java/com/binance/monitor/ui/account/AccountPageRedesignSourceTest.java`
  账户页结构 source/resource test。
- `app/src/test/java/com/binance/monitor/ui/account/AnalysisPageRedesignSourceTest.java`
  分析页结构 source/resource test。
- `app/src/test/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetSourceTest.java`
  状态按钮弹层 source/resource test。

### Existing tests to update

- `app/src/test/java/com/binance/monitor/ui/host/MainHostBottomTabSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/host/HostTabNavigatorSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/host/MainHostActivitySourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountPositionLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/settings/SettingsFragmentSourceTest.java`

## Task 1: 重建主壳信息架构与深色主题入口

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/host/MainHostTradingIaSourceTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/HostTab.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/MainHostActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Modify: `app/src/main/res/layout/activity_main_host.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/styles.xml`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/host/MainHostBottomTabSourceTest.java`

- [ ] **Step 1: 写失败测试，锁定新一级结构**

```java
package com.binance.monitor.ui.host;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MainHostTradingIaSourceTest {

    @Test
    public void mainHostShouldUseTradingAccountAnalysisSettingsTabs() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_main_host.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String hostTab = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/HostTab.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String strings = new String(Files.readAllBytes(
                Paths.get("src/main/res/values/strings.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/tabTrading"));
        assertTrue(layout.contains("@+id/tabAccount"));
        assertTrue(layout.contains("@+id/tabAnalysis"));
        assertTrue(layout.contains("@+id/tabSettings"));
        assertFalse(layout.contains("@+id/tabMarketMonitor"));

        assertTrue(hostTab.contains("TRADING(\"trading\""));
        assertTrue(hostTab.contains("ACCOUNT(\"account\""));
        assertTrue(hostTab.contains("ANALYSIS(\"analysis\""));
        assertTrue(strings.contains("<string name=\"nav_trading\">交易</string>"));
        assertTrue(strings.contains("<string name=\"nav_account\">账户</string>"));
        assertTrue(strings.contains("<string name=\"nav_analysis\">分析</string>"));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.MainHostTradingIaSourceTest" -v
```

Expected:

```text
MainHostTradingIaSourceTest > mainHostShouldUseTradingAccountAnalysisSettingsTabs FAILED
```

- [ ] **Step 3: 最小实现主壳路由与新 tab 名称**

在 `HostTab.java` 中先把新一级结构固定下来，同时保留旧 key 的兼容映射，避免旧 intent 直接崩掉：

```java
public enum HostTab {
    TRADING("trading", R.id.nav_trading),
    ACCOUNT("account", R.id.nav_account),
    ANALYSIS("analysis", R.id.nav_analysis),
    SETTINGS("settings", R.id.nav_settings);

    public static HostTab fromKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return TRADING;
        }
        if ("market_chart".equals(key)) return TRADING;
        if ("account_position".equals(key)) return ACCOUNT;
        if ("account_stats".equals(key)) return ANALYSIS;
        if ("market_monitor".equals(key)) return TRADING;
        for (HostTab value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return TRADING;
    }
}
```

在 `HostTabNavigator.java` 中直接把 4 个入口映射到现有 Fragment：

```java
private static Fragment createFragment(@NonNull HostTab tab) {
    switch (tab) {
        case TRADING:
            return new MarketChartFragment();
        case ACCOUNT:
            return new AccountPositionFragment();
        case ANALYSIS:
            return new AccountStatsFragment();
        case SETTINGS:
        default:
            return new SettingsFragment();
    }
}
```

在 `MainHostActivity.java` 中把默认 tab 与点击事件同步切到新结构：

```java
private HostTab selectedTab = HostTab.TRADING;
private TextView tabTrading;
private TextView tabAccount;
private TextView tabAnalysis;
private TextView tabSettings;

tabTrading = findViewById(R.id.tabTrading);
tabAccount = findViewById(R.id.tabAccount);
tabAnalysis = findViewById(R.id.tabAnalysis);
tabSettings = findViewById(R.id.tabSettings);

tabTrading.setOnClickListener(v -> switchTo(HostTab.TRADING));
tabAccount.setOnClickListener(v -> switchTo(HostTab.ACCOUNT));
tabAnalysis.setOnClickListener(v -> switchTo(HostTab.ANALYSIS));
tabSettings.setOnClickListener(v -> switchTo(HostTab.SETTINGS));
```

在 `activity_main_host.xml` 中把底部导航缩成四个按钮：

```xml
<TextView
    android:id="@+id/tabTrading"
    android:layout_width="0dp"
    android:layout_height="match_parent"
    android:layout_weight="1"
    android:text="@string/nav_trading" />

<TextView
    android:id="@+id/tabAccount"
    android:layout_width="0dp"
    android:layout_height="match_parent"
    android:layout_weight="1"
    android:text="@string/nav_account" />

<TextView
    android:id="@+id/tabAnalysis"
    android:layout_width="0dp"
    android:layout_height="match_parent"
    android:layout_weight="1"
    android:text="@string/nav_analysis" />
```

在 `strings.xml` 中新增新文案，并保留旧文案给桥接入口用：

```xml
<string name="nav_trading">交易</string>
<string name="nav_account">账户</string>
<string name="nav_analysis">分析</string>
```

在 `UiPaletteManager.java` 中先把底部导航样式改成针对新 id 着色：

```java
if (viewId == R.id.tabTrading) {
    iconRes = R.drawable.ic_nav_chart;
} else if (viewId == R.id.tabAccount) {
    iconRes = R.drawable.ic_nav_account;
} else if (viewId == R.id.tabAnalysis) {
    iconRes = R.drawable.ic_nav_account;
} else if (viewId == R.id.tabSettings) {
    iconRes = R.drawable.ic_nav_settings;
}
```

- [ ] **Step 4: 同步主题 token，去掉旧“浅色工作台”气质**

把 `colors.xml` / `themes.xml` / `styles.xml` 收口到深色单主线。先做最小替换，不引入第二套主题切换：

```xml
<color name="bg_primary">#08090A</color>
<color name="bg_surface">#0F1011</color>
<color name="bg_card">#191A1B</color>
<color name="bg_input">#1E2125</color>
<color name="stroke_card">#2A2D33</color>
<color name="text_primary">#F7F8F8</color>
<color name="text_secondary">#8A8F98</color>
<color name="accent_blue">#7170FF</color>
<color name="accent_green">#27A644</color>
<color name="accent_red">#F05D5E</color>
```

```xml
<style name="Theme.BinanceMonitor" parent="Theme.Material3.Dark.NoActionBar">
    <item name="colorPrimary">@color/accent_blue</item>
    <item name="colorSurface">@color/bg_card</item>
    <item name="android:statusBarColor">@color/bg_primary</item>
    <item name="android:navigationBarColor">@color/bg_surface</item>
    <item name="android:fontFamily">sans-serif</item>
</style>
```

```xml
<style name="Widget.BinanceMonitor.Card" parent="Widget.Material3.CardView.Filled">
    <item name="cardBackgroundColor">@color/bg_card</item>
    <item name="strokeColor">@color/stroke_card</item>
    <item name="cardCornerRadius">14dp</item>
    <item name="cardElevation">0dp</item>
</style>
```

- [ ] **Step 5: 运行主壳相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.MainHostTradingIaSourceTest" --tests "com.binance.monitor.ui.host.MainHostBottomTabSourceTest" --tests "com.binance.monitor.ui.host.HostTabNavigatorSourceTest" --tests "com.binance.monitor.ui.host.MainHostActivitySourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/host/MainHostActivity.java \
        app/src/main/java/com/binance/monitor/ui/host/HostTab.java \
        app/src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java \
        app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java \
        app/src/main/res/layout/activity_main_host.xml \
        app/src/main/res/values/colors.xml \
        app/src/main/res/values/styles.xml \
        app/src/main/res/values/themes.xml \
        app/src/main/res/values/strings.xml \
        app/src/test/java/com/binance/monitor/ui/host/MainHostTradingIaSourceTest.java
git commit -m "feat: switch host shell to trading account analysis tabs"
```

## Task 2: 新建顶部状态按钮与全局状态底部弹层

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
- Create: `app/src/main/res/layout/dialog_global_status_sheet.xml`
- Create: `app/src/test/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetSourceTest.java`
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`

- [ ] **Step 1: 写失败测试，锁定交易页状态按钮与弹层布局**

```java
package com.binance.monitor.ui.host;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GlobalStatusBottomSheetSourceTest {

    @Test
    public void marketChartShouldExposeStatusButtonAndBottomSheetController() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_market_chart.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        String controller = new String(Files.readAllBytes(
                Paths.get("src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/btnGlobalStatus"));
        assertTrue(layout.contains("@string/global_status_button_offline"));
        assertTrue(controller.contains("BottomSheetDialog"));
        assertTrue(controller.contains("dialog_global_status_sheet"));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest" -v
```

Expected:

```text
GlobalStatusBottomSheetSourceTest > marketChartShouldExposeStatusButtonAndBottomSheetController FAILED
```

- [ ] **Step 3: 创建全局状态弹层控制器**

在 `GlobalStatusBottomSheetController.java` 中用 `BottomSheetDialog` 承接状态按钮弹层：

```java
package com.binance.monitor.ui.host;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.binance.monitor.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public final class GlobalStatusBottomSheetController {

    public void show(@NonNull FragmentActivity activity,
                     @NonNull String connectionText,
                     @NonNull String accountText,
                     @NonNull String syncText,
                     @NonNull String refreshedAtText,
                     @NonNull String abnormalCountText,
                     @NonNull Runnable openSettings,
                     @NonNull Runnable openDiagnostics) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_global_status_sheet, null, false);
        ((TextView) content.findViewById(R.id.tvStatusConnectionValue)).setText(connectionText);
        ((TextView) content.findViewById(R.id.tvStatusAccountValue)).setText(accountText);
        ((TextView) content.findViewById(R.id.tvStatusSyncValue)).setText(syncText);
        ((TextView) content.findViewById(R.id.tvStatusUpdatedAtValue)).setText(refreshedAtText);
        ((TextView) content.findViewById(R.id.tvStatusAbnormalValue)).setText(abnormalCountText);
        content.findViewById(R.id.btnStatusOpenSettings).setOnClickListener(v -> {
            dialog.dismiss();
            openSettings.run();
        });
        content.findViewById(R.id.btnStatusOpenDiagnostics).setOnClickListener(v -> {
            dialog.dismiss();
            openDiagnostics.run();
        });
        dialog.setContentView(content);
        dialog.show();
    }
}
```

配套布局 `dialog_global_status_sheet.xml`：

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="20dp">

    <TextView
        android:id="@+id/tvStatusSheetTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="全局状态"
        android:textAppearance="@style/TextAppearance.BinanceMonitor.SectionTitle" />

    <TextView android:id="@+id/tvStatusConnectionValue" android:layout_width="match_parent" android:layout_height="wrap_content" />
    <TextView android:id="@+id/tvStatusAccountValue" android:layout_width="match_parent" android:layout_height="wrap_content" />
    <TextView android:id="@+id/tvStatusSyncValue" android:layout_width="match_parent" android:layout_height="wrap_content" />
    <TextView android:id="@+id/tvStatusUpdatedAtValue" android:layout_width="match_parent" android:layout_height="wrap_content" />
    <TextView android:id="@+id/tvStatusAbnormalValue" android:layout_width="match_parent" android:layout_height="wrap_content" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:orientation="horizontal">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnStatusOpenSettings"
            android:layout_width="0dp"
            android:layout_height="@dimen/control_height_md"
            android:layout_weight="1"
            android:text="去设置" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnStatusOpenDiagnostics"
            android:layout_width="0dp"
            android:layout_height="@dimen/control_height_md"
            android:layout_weight="1"
            android:layout_marginStart="8dp"
            android:text="看诊断" />
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 4: 在交易页接入状态按钮**

先在 `activity_market_chart.xml` 顶部工具区加入按钮：

```xml
<TextView
    android:id="@+id/btnGlobalStatus"
    android:layout_width="wrap_content"
    android:layout_height="@dimen/control_height_md"
    android:background="@drawable/bg_chip_unselected"
    android:paddingHorizontal="12dp"
    android:gravity="center"
    android:text="@string/global_status_button_offline"
    android:textColor="@color/text_primary"
    android:textSize="12sp" />
```

再在 `MarketChartFragment.java` 里把点击委托给 `MarketChartScreen`：

```java
binding.btnGlobalStatus.setOnClickListener(v -> screen.showGlobalStatusSheet());
```

在 `MarketChartScreen.java` 增加桥接方法：

```java
public void showGlobalStatusSheet() {
    globalStatusBottomSheetController.show(
            requireActivity(),
            resolveConnectionText(),
            resolveAccountText(),
            resolveSyncText(),
            resolveUpdatedAtText(),
            resolveAbnormalCountText(),
            this::openSettingsPage,
            this::openDiagnosticsPage
    );
}
```

- [ ] **Step 5: 运行状态按钮相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartFragmentSourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java \
        app/src/main/res/layout/dialog_global_status_sheet.xml \
        app/src/main/res/layout/activity_market_chart.xml \
        app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java \
        app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java \
        app/src/test/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetSourceTest.java
git commit -m "feat: add global status bottom sheet for trading home"
```

## Task 3: 把交易页重做成默认首页，并把异常整合进图表

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/chart/TradeAlertBottomSheetController.java`
- Create: `app/src/main/res/layout/dialog_trade_alert_sheet.xml`
- Create: `app/src/test/java/com/binance/monitor/ui/chart/TradeHomeRedesignSourceTest.java`
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`

- [ ] **Step 1: 写失败测试，锁定“交易首页”结构**

```java
package com.binance.monitor.ui.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TradeHomeRedesignSourceTest {

    @Test
    public void tradingHomeShouldDropMarketOverviewAndUseRiskBar() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/activity_market_chart.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/layoutTradeRiskBanner"));
        assertTrue(layout.contains("@+id/btnTradeAlertList"));
        assertTrue(layout.contains("@+id/layoutTradeQuickActions"));
        assertTrue(layout.contains("@+id/cardTradeSymbolSummary"));
        assertFalse(layout.contains("@string/market_card_title"));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.TradeHomeRedesignSourceTest" -v
```

Expected:

```text
TradeHomeRedesignSourceTest > tradingHomeShouldDropMarketOverviewAndUseRiskBar FAILED
```

- [ ] **Step 3: 重写交易页 XML 结构**

把 `activity_market_chart.xml` 顶部结构收口成“顶部控制条 + 风险提示条 + 主图 + 快捷交易区 + 当前品种摘要”。先落最小骨架：

```xml
<LinearLayout
    android:id="@+id/layoutTradeToolbar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">

    <TextView android:id="@+id/tvTradeSymbolValue" android:layout_width="wrap_content" android:layout_height="wrap_content" />
    <TextView android:id="@+id/tvTradeIntervalValue" android:layout_width="wrap_content" android:layout_height="wrap_content" />
    <TextView android:id="@+id/btnIndicators" android:layout_width="wrap_content" android:layout_height="wrap_content" />
    <TextView android:id="@+id/btnChartTools" android:layout_width="wrap_content" android:layout_height="wrap_content" />
    <TextView android:id="@+id/btnGlobalStatus" android:layout_width="wrap_content" android:layout_height="wrap_content" />
</LinearLayout>

<LinearLayout
    android:id="@+id/layoutTradeRiskBanner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">

    <TextView
        android:id="@+id/tvTradeRiskBanner"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="当前无风险提示" />

    <TextView
        android:id="@+id/btnTradeAlertList"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="查看异常" />
</LinearLayout>

<LinearLayout
    android:id="@+id/layoutTradeQuickActions"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal">
    <!-- 买入 / 卖出 / 平仓 -->
</LinearLayout>

<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardTradeSymbolSummary"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

- [ ] **Step 4: 在 `MarketChartScreen` 中加入异常快看入口**

新增异常快看控制器 `TradeAlertBottomSheetController.java`：

```java
public final class TradeAlertBottomSheetController {

    public void show(@NonNull FragmentActivity activity,
                     @NonNull String title,
                     @NonNull String summary,
                     @NonNull Runnable openFullDetails) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View content = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_trade_alert_sheet, null, false);
        ((TextView) content.findViewById(R.id.tvTradeAlertTitle)).setText(title);
        ((TextView) content.findViewById(R.id.tvTradeAlertSummary)).setText(summary);
        content.findViewById(R.id.btnTradeAlertOpenDetails).setOnClickListener(v -> {
            dialog.dismiss();
            openFullDetails.run();
        });
        dialog.setContentView(content);
        dialog.show();
    }
}
```

在 `MarketChartScreen.java` 中把顶部风险提示条与图上异常标记统一汇入这个快看入口：

```java
public void onRiskBannerClicked() {
    tradeAlertBottomSheetController.show(
            requireActivity(),
            resolvePrimaryAlertTitle(),
            resolvePrimaryAlertSummary(),
            this::openAbnormalRecordPage
    );
}
```

在 `MarketChartFragment.java` 中接点击：

```java
binding.layoutTradeRiskBanner.setOnClickListener(v -> screen.onRiskBannerClicked());
binding.btnTradeAlertList.setOnClickListener(v -> screen.onRiskBannerClicked());
```

- [ ] **Step 5: 运行交易首页相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.TradeHomeRedesignSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest" --tests "com.binance.monitor.ui.chart.MarketChartFragmentSourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/TradeAlertBottomSheetController.java \
        app/src/main/res/layout/dialog_trade_alert_sheet.xml \
        app/src/main/res/layout/activity_market_chart.xml \
        app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java \
        app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java \
        app/src/test/java/com/binance/monitor/ui/chart/TradeHomeRedesignSourceTest.java
git commit -m "feat: redesign trading home around chart and alerts"
```

## Task 4: 把账户页收口成“总览 + 持仓 + 挂单 + 历史入口”

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/account/AccountPageRedesignSourceTest.java`
- Modify: `app/src/main/res/layout/content_account_position.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionLayoutResourceTest.java`

- [ ] **Step 1: 写失败测试，锁定账户页默认可见模块**

```java
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AccountPageRedesignSourceTest {

    @Test
    public void accountPageShouldShowOverviewPositionsPendingAndHistoryEntry() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_account_position.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/cardOverviewSection"));
        assertTrue(layout.contains("@+id/cardPositionSection"));
        assertTrue(layout.contains("@+id/cardPendingSection"));
        assertTrue(layout.contains("@+id/cardHistoryEntrySection"));
        assertFalse(layout.contains("@+id/ivAccountPrivacyToggle"));
        assertFalse(layout.contains("@+id/tvAccountConnectionStatus"));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPageRedesignSourceTest" -v
```

Expected:

```text
AccountPageRedesignSourceTest > accountPageShouldShowOverviewPositionsPendingAndHistoryEntry FAILED
```

- [ ] **Step 3: 重写账户页共享布局**

在 `content_account_position.xml` 中移除顶部连接状态和隐私图标，把第四张卡固定为历史入口：

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardHistoryEntrySection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="@dimen/page_section_gap"
    android:layout_marginBottom="@dimen/space_20">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="@dimen/card_content_padding">

        <TextView
            android:id="@+id/tvHistoryEntryTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="历史成交"
            android:textAppearance="@style/TextAppearance.BinanceMonitor.SectionTitle" />

        <TextView
            android:id="@+id/tvHistoryEntrySummary"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/space_8"
            android:text="最近成交摘要 / 点击进入完整列表"
            android:textAppearance="@style/TextAppearance.BinanceMonitor.Meta" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 4: 接上账户页历史入口点击**

在 `AccountPositionFragment.java` 中显式接出历史成交入口，跳到分析页或历史详情页：

```java
binding.cardHistoryEntrySection.setOnClickListener(v -> {
    startActivity(HostNavigationIntentFactory.forTab(requireContext(), HostTab.ANALYSIS));
});
```

如果当前页面已有对象点击逻辑，保持“先弹层，再进深页”的策略，不要把整页直接堆成长列表。

- [ ] **Step 5: 运行账户页测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPageRedesignSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountPositionFragmentSourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/content_account_position.xml \
        app/src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java \
        app/src/test/java/com/binance/monitor/ui/account/AccountPageRedesignSourceTest.java
git commit -m "feat: streamline account page to overview positions pending and history entry"
```

## Task 5: 把分析页收口成“收益曲线 + 统计摘要 + 结构分析卡 + 历史分析入口”

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/account/AnalysisPageRedesignSourceTest.java`
- Modify: `app/src/main/res/layout/content_account_stats.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`

- [ ] **Step 1: 写失败测试，锁定分析页默认模块**

```java
package com.binance.monitor.ui.account;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AnalysisPageRedesignSourceTest {

    @Test
    public void analysisPageShouldShowCurveStatsStructureCardsAndTradeAnalysisEntry() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_account_stats.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("@+id/cardCurveSection"));
        assertTrue(layout.contains("@+id/cardStatsSummarySection"));
        assertTrue(layout.contains("@+id/cardStructureAnalysisSection"));
        assertTrue(layout.contains("@+id/cardTradeAnalysisEntrySection"));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AnalysisPageRedesignSourceTest" -v
```

Expected:

```text
AnalysisPageRedesignSourceTest > analysisPageShouldShowCurveStatsStructureCardsAndTradeAnalysisEntry FAILED
```

- [ ] **Step 3: 在分析页布局中补摘要卡和深页入口**

在 `content_account_stats.xml` 中保留曲线区块，再新增三张摘要卡：

```xml
<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardStatsSummarySection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <TextView android:id="@+id/tvStatsSummaryTitle" android:text="核心统计" />
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerStatsSummary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</com.google.android.material.card.MaterialCardView>

<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardStructureAnalysisSection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <TextView android:text="结构分析" />
    <LinearLayout android:id="@+id/layoutStructureCards" android:orientation="vertical" />
</com.google.android.material.card.MaterialCardView>

<com.google.android.material.card.MaterialCardView
    android:id="@+id/cardTradeAnalysisEntrySection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <TextView android:text="历史成交分析" />
    <TextView android:id="@+id/tvTradeAnalysisEntrySummary" android:text="点击进入逐笔分析" />
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 4: 在 `AccountStatsFragment` 中接结构分析与历史分析入口**

```java
binding.cardTradeAnalysisEntrySection.setOnClickListener(v -> screen.openTradeAnalysisPage());
```

在 `AccountStatsScreen.java` 中补一个无歧义的深页桥接方法：

```java
public void openTradeAnalysisPage() {
    host.openLegacyTradeHistoryEntry();
}
```

第一轮实现先复用现有历史深页入口，不把完整分析详情继续堆回一级页。

- [ ] **Step 5: 运行分析页测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AnalysisPageRedesignSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsFragmentSourceTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/content_account_stats.xml \
        app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java \
        app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java \
        app/src/test/java/com/binance/monitor/ui/account/AnalysisPageRedesignSourceTest.java
git commit -m "feat: restructure analysis page around curve summaries and deep links"
```

## Task 6: 收口设置页、回归验证与文档同步

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/settings/SettingsStatusEntrySourceTest.java`
- Modify: `app/src/main/res/layout/content_settings.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsFragment.java`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 写失败测试，锁定设置页承接状态入口**

```java
package com.binance.monitor.ui.settings;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SettingsStatusEntrySourceTest {

    @Test
    public void settingsPageShouldExposeDiagnosticsAndConnectionEntry() throws Exception {
        String layout = new String(Files.readAllBytes(
                Paths.get("src/main/res/layout/content_settings.xml")
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n');

        assertTrue(layout.contains("连接"));
        assertTrue(layout.contains("诊断"));
        assertTrue(layout.contains("状态"));
    }
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.settings.SettingsStatusEntrySourceTest" -v
```

Expected:

```text
SettingsStatusEntrySourceTest > settingsPageShouldExposeDiagnosticsAndConnectionEntry FAILED
```

- [ ] **Step 3: 在设置页补上状态弹层的承接入口**

在 `content_settings.xml` 中明确放出连接/诊断分组：

```xml
<TextView
    android:id="@+id/tvSettingsDiagnosticsTitle"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="连接与诊断"
    android:textAppearance="@style/TextAppearance.BinanceMonitor.SectionTitle" />
```

在 `SettingsFragment.java` 中把点击入口接出来：

```java
binding.rowConnectionDiagnostics.setOnClickListener(v -> {
    startActivity(new Intent(requireContext(), SettingsSectionActivity.class)
            .putExtra(SettingsSectionActivity.EXTRA_SECTION_KEY, "diagnostics"));
});
```

- [ ] **Step 4: 跑完整 UI 回归与构建**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.MainHostTradingIaSourceTest" --tests "com.binance.monitor.ui.host.GlobalStatusBottomSheetSourceTest" --tests "com.binance.monitor.ui.chart.TradeHomeRedesignSourceTest" --tests "com.binance.monitor.ui.account.AccountPageRedesignSourceTest" --tests "com.binance.monitor.ui.account.AnalysisPageRedesignSourceTest" --tests "com.binance.monitor.ui.settings.SettingsStatusEntrySourceTest" -v
.\gradlew.bat :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 5: 同步文档**

在 `README.md` 的“已完成功能列表”与“待办事项”中同步：

```md
- UI 一级结构已收口为 `交易 / 账户 / 分析 / 设置`
- 交易页已接管默认首页与异常整合展示
```

在 `ARCHITECTURE.md` 中同步：

```md
- `MainHostActivity` 当前以 `交易 / 账户 / 分析 / 设置` 为固定一级结构；`MarketChartFragment` 现作为默认交易首页承接图表、异常与快捷交易。
```

在 `CONTEXT.md` 中同步当前已完成阶段与下阶段残留。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/layout/content_settings.xml \
        app/src/main/java/com/binance/monitor/ui/settings/SettingsFragment.java \
        app/src/test/java/com/binance/monitor/ui/settings/SettingsStatusEntrySourceTest.java \
        README.md ARCHITECTURE.md CONTEXT.md
git commit -m "chore: verify redesigned UI flow and sync docs"
```

## Self-Review

### Spec coverage

- 一级结构 `交易 / 账户 / 分析 / 设置`：Task 1
- 深色视觉统一：Task 1
- 顶部状态按钮与底部弹层：Task 2
- 异常整合进图表：Task 3
- 账户页默认模块：Task 4
- 分析页默认模块：Task 5
- 设置页承接状态与诊断：Task 6

没有遗漏的 spec 主要求。

### Placeholder scan

- 未使用 `TBD / TODO / implement later`
- 每个任务都给出具体文件、测试、命令和代码片段
- 每个提交点都明确到文件路径与 commit message

### Type consistency

- 一级 tab 命名统一为 `TRADING / ACCOUNT / ANALYSIS / SETTINGS`
- 状态弹层统一命名为 `GlobalStatusBottomSheetController`
- 交易页异常快看统一命名为 `TradeAlertBottomSheetController`

未发现前后名称漂移。
