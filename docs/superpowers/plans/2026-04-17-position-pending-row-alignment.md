# Position And Pending Row Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一当前持仓和挂单折叠条目的左右两列对齐，并把挂单折叠态价格改成不带小数点。

**Architecture:** 继续复用现有 `item_position.xml` 作为持仓和挂单的共享条目布局，只把首行改成稳定的“左信息列 + 右操作列”结构。显示逻辑保持在两个 adapter 内部分别处理，其中挂单只调整折叠态文案格式，不动展开态、图表和弹窗的价格精度。

**Tech Stack:** Android XML、Java、JUnit4 资源/源码测试、Gradle、ADB

---

### Task 1: 锁定条目布局与折叠价格式合同

**Files:**
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionEnhancementSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionAdapterSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/SquareButtonStyleResourceTest.java`

- [ ] **Step 1: 写失败测试**

```java
assertTrue(layoutSource.contains("android:id=\"@+id/layoutSummaryColumn\""));
assertTrue(layoutSource.contains("android:id=\"@+id/layoutActionButtons\""));
assertTrue(layoutSource.contains("android:gravity=\"center_vertical\""));
assertTrue(layoutSource.contains("android:layout_width=\"@dimen/position_action_group_width\""));
assertTrue(source.contains("String pendingPriceText = formatCollapsedPendingPrice(pendingPrice);"));
assertTrue(source.contains("String.format(Locale.getDefault(), \"%,.0f\", roundedPrice)"));
```

- [ ] **Step 2: 跑测试确认失败**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPositionAdapterSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionEnhancementSourceTest" --tests "com.binance.monitor.ui.theme.SquareButtonStyleResourceTest"`
Expected: FAIL，提示共享条目布局和挂单折叠态价格格式尚未满足新合同

### Task 2: 实现共享条目对齐和挂单折叠态价格格式

**Files:**
- Modify: `app/src/main/res/layout/item_position.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`

- [ ] **Step 3: 写最小实现**

```xml
<LinearLayout
    android:id="@+id/layoutSummaryColumn"
    android:layout_width="0dp"
    android:layout_height="wrap_content"
    android:layout_weight="1"
    android:orientation="vertical">
```

```java
String pendingPriceText = formatCollapsedPendingPrice(pendingPrice);

private static String formatCollapsedPendingPrice(double price) {
    double roundedPrice = Math.rint(price);
    return String.format(Locale.getDefault(), "%,.0f", roundedPrice);
}
```

- [ ] **Step 4: 跑定向测试确认通过**

Run: `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPositionAdapterSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionEnhancementSourceTest" --tests "com.binance.monitor.ui.theme.SquareButtonStyleResourceTest"`
Expected: PASS

### Task 3: 编译、安装和上下文同步

**Files:**
- Modify: `CONTEXT.md`

- [ ] **Step 5: 运行完整验证**

Run: `./gradlew.bat assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 安装到真机**

Run: `adb -s 7fab54c4 install -r "E:\\Github\\BTCXAU_Monitoring_and_Push_APK\\app\\build\\outputs\\apk\\debug\\app-debug.apk"`
Expected: Success

- [ ] **Step 7: 更新上下文**

```md
- 最新续做已统一当前持仓/挂单折叠条目的左右两列对齐，并把挂单折叠态价格改成整数显示；相关定向测试、assembleDebug 和真机安装已通过。
```
