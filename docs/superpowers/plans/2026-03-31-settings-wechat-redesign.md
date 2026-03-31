# 微信式设置页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把设置首页和二级页转成更接近微信的分组列表视觉，在不改业务路由和 ViewModel 的前提下清理卡片感。

**Architecture:** 利用 LinearLayout + NestedScrollView 重建两张布局，入口/分段用标准行+细线；Java 端以新的 GradientDrawable 工具提供统一的背景、分组边框、主题行高亮。

**Tech Stack:** Android ViewBinding, `LinearLayout`/`NestedScrollView`, Material switch/button/text-input, `GradientDrawable`, Java 8。

---

### Task 1: 重建 `activity_settings.xml` 首页布局

**Files:**
- Modify: `app/src/main/res/layout/activity_settings.xml`

- [ ] **Step 1: 替换原来的“条目 + 卡片”结构，先插入“常规设置”组**

```xml
<TextView
    android:id="@+id/tvSettingsHeader"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="常规设置"
    android:textAppearance="@style/TextAppearance.BinanceMonitor.Caption"
    android:layout_marginTop="@dimen/space_16" />

<LinearLayout
    android:id="@+id/itemDisplay"
    android:layout_width="match_parent"
    android:layout_height="56dp"
    android:layout_marginTop="@dimen/space_8"
    android:paddingHorizontal="@dimen/page_horizontal_padding"
    android:gravity="center_vertical"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackgroundBorderless"
    android:orientation="horizontal">

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:text="悬浮窗与显示"
        android:textColor="@color/text_primary"
        android:textSize="15sp" />

    <ImageView
        android:layout_width="24dp"
        android:layout_height="24dp"
        android:src="@drawable/ic_chevron_right"
        android:tint="@color/text_secondary"
        android:contentDescription="@null" />
</LinearLayout>

<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:layout_marginStart="@dimen/page_horizontal_padding"
    android:background="@color/divider" />
```

- [ ] **Step 2: 复制上面的行样式为 MT5、主题三个入口，保证 ID 分别为 `itemGateway`、`itemTheme`，各自保持 `layout_height="56dp"`、箭头、点击状态。**

```xml
<!-- 另外两个条目参考上面的 itemDisplay，替换文本和 id -->
```

- [ ] **Step 3: 增加“辅助工具”组头，后续追加 `itemTab`、`itemCache`、`itemLogs` 条目，每两个条目间插入相同的 Divider，组之间通过 `View`/间距分隔**

```xml
<View
    android:layout_width="match_parent"
    android:layout_height="12dp" />

<TextView
    android:text="辅助工具"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textAppearance="@style/TextAppearance.BinanceMonitor.Caption"
    android:layout_marginTop="@dimen/space_12" />
```

- [ ] **Step 4: 保留底部 `tabBar` （保持原有 `LinearLayout`）与 `ScrollView`，确认新内容仍在 `ScrollView` 内并且 `tabBar` 在根布局末尾。**

### Task 2: 将 `activity_settings_detail.xml` 改为扁平分段

**Files:**
- Modify: `app/src/main/res/layout/activity_settings_detail.xml`

- [ ] **Step 1: 用 `LinearLayout` 替换每个 `MaterialCardView` 容器，保持原有 `id`（如 `cardFloatingSection`）以免影响绑定，内部仍放 `TextView`/Switch/SeekBar。外层加 `android:paddingHorizontal="@dimen/page_horizontal_padding"`，并在容器之间插入 1dp Divider**

```xml
<LinearLayout
    android:id="@+id/cardFloatingSection"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingVertical="@dimen/space_12">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="悬浮窗与显示"
        android:textAppearance="@style/TextAppearance.BinanceMonitor.SectionTitle" />

    <com.google.android.material.switchmaterial.SwitchMaterial
        android:id="@+id/switchFloatingEnabled"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="@dimen/space_12"
        android:text="@string/floating_window"
        android:textColor="@color/text_primary"
        android:textSize="14sp" />

    <!-- 其余控件按原样放入 -->
</LinearLayout>

<View
    android:layout_width="match_parent"
    android:layout_height="1dp"
    android:background="@color/divider" />
```

- [ ] **Step 2: 把主题区改成扁平的“主题条目”列表，每个条目是 `LinearLayout`（保留 `cardTheme*` ID），左侧上下两行文本，右侧三个小色块，条目设置 `android:foreground="?attr/selectableItemBackgroundBorderless"` 供点击反馈**

```xml
<LinearLayout
    android:id="@+id/cardThemeFinancial"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:layout_marginTop="@dimen/space_12"
    android:foreground="?attr/selectableItemBackgroundBorderless"
    android:padding="@dimen/space_12">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/tvThemeFinancialTitle"
            android:text="金融专业风"
            android:textColor="@color/text_primary"
            android:textSize="14sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvThemeFinancialDesc"
            android:layout_marginTop="2dp"
            android:text="深色交易屏，绿涨红跌。"
            android:textColor="@color/text_secondary"
            android:textSize="11sp" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="72dp"
        android:layout_height="28dp"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <View
            android:id="@+id/viewThemeFinancialA"
            android:layout_width="16dp"
            android:layout_height="16dp"
            android:background="@drawable/bg_overlay_mini" />
        <!-- 另外两个色块同样保留 -->
    </LinearLayout>
</LinearLayout>
```

- [ ] **Step 3: 依次为主题条目、Tab 组、缓存组添加 Divider、Margin，最终 `btnViewLogs` 保留在 scrollview 底部，仍使用 `@drawable/bg_inline_button`。**

- [ ] **Step 4: 确保底部导航 (`tabBar`) 与 `BottomNavigationView` 依旧放在根布局末尾，ScrollView 仍带 `padding="@dimen/page_horizontal_padding"`。**

### Task 3: 调整 `SettingsActivity.java` 的样式方法

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`

- [ ] **Step 1: 让 `styleEntry(View view, Palette palette)` 调用新 Helper，将每行背景换成无圆角、与页面背景一致的 Rectangle**

```java
private void styleEntry(View view, UiPaletteManager.Palette palette) {
    if (view == null) {
        return;
    }
    view.setBackground(UiPaletteManager.createListRowBackground(this, palette.surfaceEnd, palette.stroke));
}
```

- [ ] **Step 2: 保持 `applyPaletteStyles()` 逻辑不变（Page theme + bottom tabs），只要 `styleEntry` 产生的背景保持扁平即可。**

### Task 4: 更新 `SettingsSectionActivity.java` 为新结构

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`

- [ ] **Step 1: 把 `setupThemeCards()` 及 `styleThemeCard()` 改为 `setupThemeItems()`/`styleThemeItem()`，每个条目接受 `View item`，通过 `UiPaletteManager.createThemeItemDrawable` 设置边框和背景，title/desc 用主题颜色**

```java
private void styleThemeItem(View item,
                            TextView titleView,
                            TextView descView,
                            View previewA,
                            View previewB,
                            View previewC,
                            UiPaletteManager.Palette palette,
                            boolean selected) {
    if (item != null) {
        int border = selected ? palette.primary : palette.stroke;
        int fill = selected ? palette.control : palette.surfaceEnd;
        item.setBackground(UiPaletteManager.createThemeItemDrawable(this, fill, border));
    }
    if (titleView != null) {
        titleView.setTextColor(palette.textPrimary);
    }
    if (descView != null) {
        descView.setTextColor(palette.textSecondary);
    }
    previewA.setBackground(UiPaletteManager.createFilledDrawable(this, palette.primary));
    previewB.setBackground(UiPaletteManager.createFilledDrawable(this, palette.rise));
    previewC.setBackground(UiPaletteManager.createFilledDrawable(this, palette.fall));
}
```

- [ ] **Step 2: `applyPaletteStyles()` 用新的 `createSectionBackground()` 为 `cardFloatingSection`, `cardGatewaySection` 等设置扁平边框，并继续调用 `styleThemeItem()`**

```java
binding.cardFloatingSection.setBackground(UiPaletteManager.createSectionBackground(this, palette.surfaceEnd, palette.stroke));
// 其余两个 section 同理
```

- [ ] **Step 3: 在 `onCreate()` 把 `setupThemeCards()` 改为 `setupThemeItems()`，其内部逻辑和 `selectTheme(...)` 保持不变。**

### Task 5: 扩展 `UiPaletteManager`

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`

- [ ] **Step 1: 增加通用的矩形构造器和三个 Helper：`createListRowBackground` (corner 0)、`createSectionBackground` (corner 4dp)、`createThemeItemDrawable` (corner 6dp)**

```java
private static GradientDrawable createRectDrawable(Context context,
                                                   int fillColor,
                                                   int strokeColor,
                                                   int cornerDp) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setShape(GradientDrawable.RECTANGLE);
    drawable.setCornerRadius(dp(context, cornerDp));
    drawable.setColor(fillColor);
    drawable.setStroke(dp(context, 1), strokeColor);
    return drawable;
}

public static GradientDrawable createListRowBackground(Context context, int fillColor, int strokeColor) {
    return createRectDrawable(context, fillColor, strokeColor, 0);
}

public static GradientDrawable createSectionBackground(Context context, int fillColor, int strokeColor) {
    return createRectDrawable(context, fillColor, strokeColor, 4);
}

public static GradientDrawable createThemeItemDrawable(Context context, int fillColor, int strokeColor) {
    return createRectDrawable(context, fillColor, strokeColor, 6);
}
```

### Task 6: 新增 Chevron 右箭头资源

**Files:**
- Create: `app/src/main/res/drawable/ic_chevron_right.xml`

- [ ] **Step 1: 写一个简单的 VectorDrawable，24dp 宽高，pathData 描述 `>` 形状**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/text_secondary"
        android:pathData="M9,6l6,6l-6,6" />
</vector>
```

### Task 7: 验证与提交

**Files:**
- Test: `./gradlew :app:assembleDebug`

- [ ] **Step 1: 运行打包确认无布局资源错误**

```bash
./gradlew :app:assembleDebug
```
Expected: 构建成功，没有 `aapt` 或 `layout` 相关错误，并且 `activity_settings.xml`/`activity_settings_detail.xml` 编译通过。

- [ ] **Step 2: 手动打开设置首页与分组详情**

```
1. 启动 App 到设置首页，确认六个条目按常规/辅助工具分组、点击能跳转。
2. 在任意分组页确认组件仍按逻辑展示，主题列表在选中时边框高亮。
```

### Self-review

1. Spec 覆盖：每条需求（首页分组、箭头、颜色、二级页扁平、主题行、资源）在 Tasks 1-6 中都有对应内容，没有遗漏。
2. Placeholder 扫描：所有步骤都给出具体代码片段，没有留 “TODO”。
3. 类型一致：新 helper/方法名在整个计划中统一命名为 `createListRowBackground`、`createThemeItemDrawable` 等。
