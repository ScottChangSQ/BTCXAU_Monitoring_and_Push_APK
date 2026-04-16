# Single Host Tab Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前多 `Activity` 的底部 Tab 切页改成“单主壳 Activity + 常驻子页面”的无缝切换结构，并把账户统计页刷新严格收口到历史态，彻底切断与持仓/挂单运行态的整页联动。

**Architecture:** 主入口统一收口到 `MainHostActivity`，底部五个 Tab 由 `HostTabNavigator` 在同一容器里常驻切换，不再互相 `startActivity(...)`。迁移时不重写页面，而是先把现有页面抽成“共享内容布局 + PageController”，让旧 `Activity` 和新 `Fragment` 共用同一套页面实现；账户数据继续只认 `AccountStatsPreloadManager.Cache` 这一条上游真值，再在 UI 层拆成 `运行态` 和 `历史态` 两个只读仓。

**Tech Stack:** Java、AndroidX Fragment、ViewBinding、BottomNavigationView、RecyclerView、JUnit4、Gradle、adb/logcat/gfxinfo

---

## 方案边界

- 不做“继续保留多 `Activity`，靠缓存和跳过刷新减轻卡顿”的降级方案。
- 不做“先新建一套页面，再慢慢替换旧页”的双轨页面方案。
- 不做离屏页面继续监听并整页渲染的伪常驻方案。
- 不新造第二条账户真值链，仍只认 [AccountStatsPreloadManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java) 这一条上游真值。
- 不把行情链、账户链、交易链揉成一次性大改；必须按任务拆开，每步独立验证。

## 模块职责

- [MainHostActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/host/MainHostActivity.java)
  统一底部 Tab 主壳，只负责容器、Tab 状态、返回栈和页面显示切换。
- [HostTab.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/host/HostTab.java)
  统一五个 Tab 的稳定 key、菜单 id、默认入口。
- [HostTabNavigator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java)
  统一封装首次创建、show/hide、状态恢复、外部目标 Tab 跳转。
- [HostNavigationIntentFactory.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/host/HostNavigationIntentFactory.java)
  统一把旧入口和外部跳转收口到主壳。
- [HostTabPage.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/host/HostTabPage.java)
  定义常驻页最小生命周期契约，只允许当前可见页做页面级刷新。

- [AccountHistorySnapshotStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/history/AccountHistorySnapshotStore.java)
  从 `AccountStatsPreloadManager.Cache` 裁出账户统计页真正需要的历史态字段。
- [AccountRuntimeSnapshotStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/runtime/AccountRuntimeSnapshotStore.java)
  从同一缓存裁出账户持仓页真正需要的运行态字段。
- [AccountStatsRenderSignature.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/history/AccountStatsRenderSignature.java)
  账户统计页刷新签名，只允许包含 `historyRevision`、历史列表和本页筛选条件。
- [AccountStatsSectionDiff.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/history/AccountStatsSectionDiff.java)
  把账户统计刷新拆成曲线区、收益区、交易统计区、交易记录区四段。

- [AccountStatsPageController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageController.java)
  账户统计共享页面控制器，旧 `Activity` 与新 `Fragment` 共用。
- [AccountPositionPageController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java)
  账户持仓共享页面控制器。
- [MarketChartPageController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageController.java)
  行情持仓共享页面控制器。
- [MarketMonitorPageController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageController.java)
  行情监控共享页面控制器。
- [SettingsPageController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java)
  设置页共享页面控制器。

- [AccountStatsFragment.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java)
- [AccountPositionFragment.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java)
- [MarketChartFragment.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java)
- [MarketMonitorFragment.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/market/MarketMonitorFragment.java)
- [SettingsFragment.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsFragment.java)
  五个常驻页面壳，只持有布局与控制器，不重复实现页面业务。

## 关键链路拆解

### 链路 A：底部 Tab 导航链

**输入：**
- 底部导航点击
- 外部 `Intent` 指定目标 Tab
- 旧 `Activity` 桥接跳转

**处理：**
- `MainHostActivity` 接收目标 Tab
- `HostTabNavigator` 决定首次创建还是直接 `show/hide`
- 切页前先通知旧页 `onHostPageHidden()`
- 切页后再通知新页 `onHostPageShown()`

**状态变化：**
- `selectedTab` 改变
- Fragment 可见性改变
- 页面控制器的 `isPageVisible` 改变

**输出：**
- 主壳容器中只显示一个当前页
- 非当前页保留实例和页面状态

**上下游影响：**
- 上游不改数据源
- 下游页面级刷新时机从 `Activity` 生命周期改成 `HostTabPage` 可见性契约

### 链路 B：账户统计历史态链

**输入：**
- `AccountStatsPreloadManager.Cache`
- `historyRevision`
- 账户统计页本地筛选条件

**处理：**
- `AccountHistorySnapshotStore` 从总缓存裁出历史态
- `AccountStatsRenderSignature` 生成本轮签名
- `AccountStatsSectionDiff` 计算四段差异
- `AccountStatsPageController` 只刷新变化段

**状态变化：**
- `lastRenderSignature` 更新
- 曲线区/收益区/交易统计区/交易记录区局部 UI 更新

**输出：**
- 账户统计页只在历史态变化时刷新
- 持仓和挂单变化不再触发整页重算

**上下游影响：**
- 上游仍是同一份账户缓存
- 下游只影响账户统计页，不再影响账户持仓和其他 Tab

### 链路 C：账户持仓运行态链

**输入：**
- `AccountStatsPreloadManager.Cache`
- 运行态流中的 `overview / positions / pendingOrders / connected`

**处理：**
- `AccountRuntimeSnapshotStore` 从总缓存裁出运行态
- `AccountPositionPageController` 仅在页面可见时绑定运行态 UI

**状态变化：**
- 持仓列表、挂单列表、概览卡片更新
- 页面离屏时仅缓存最后一次运行态快照

**输出：**
- 账户持仓页保持实时
- 历史态重算与该页脱钩

**上下游影响：**
- 上游仍是同一份账户缓存
- 下游只影响账户持仓和图上账户叠加，不再打脏账户统计页

### 链路 D：页面复用迁移链

**输入：**
- 现有 `Activity` 页面逻辑
- 现有布局资源

**处理：**
- 抽出 `content_*.xml`
- 抽出 `*PageController`
- 旧 `Activity` 和新 `Fragment` 共用控制器

**状态变化：**
- 页面实现从“Activity 独占”变成“控制器复用”

**输出：**
- 同一套页面逻辑被主壳和旧入口同时复用

**上下游影响：**
- 上游接口和数据源不变
- 下游降低迁移风险，不会形成新旧两套页面逻辑分叉

## 页面状态模型

### 主壳状态

- `selectedTab`：当前可见 Tab
- `createdTabs`：已经实例化的 Tab 集合
- `pendingTargetTab`：来自外部 `Intent` 的待切换目标

### 页面控制器状态

- `isPageVisible`：当前页是否允许页面级刷新
- `isViewBound`：页面视图是否已完成绑定
- `lastRenderSignature`：上次完成渲染的签名
- `latestPayload`：离屏期间暂存的最新只读数据

### 页面必须遵守的状态规则

- `isPageVisible=false` 时，禁止整页渲染
- `isViewBound=false` 时，禁止消费页面级刷新结果
- `latestPayload` 只能覆盖，不允许离屏时做补算
- `lastRenderSignature` 只能在本轮 UI 真正完成后更新，不能在计算前提前写入

## 数据域边界

### 账户统计允许读取

- `historyRevision`
- `trades`
- `curvePoints`
- `statsMetrics`
- `curveIndicators`
- 本页筛选条件与排序条件

### 账户统计禁止读取

- `positions`
- `pendingOrders`
- 连接态变化触发的整页签名
- 与本页无关的行情渲染状态

### 账户持仓允许读取

- `overviewMetrics`
- `positions`
- `pendingOrders`
- `account`
- `server`
- `connected`

### 账户持仓禁止读取

- `trades`
- `curvePoints`
- `statsMetrics`
- `curveIndicators`
- 仅用于账户统计筛选的本地状态

## 迁移顺序为什么这样定

### 先抽页面，再切主壳

- 这样可以先把“现成页面拿出来”，不先引入路由变量。
- 一旦页面控制器抽完，旧 `Activity` 仍能独立回归，风险最小。

### 先做账户两页，再做其他三页

- 当前卡顿主因在账户统计页重建和整页重算。
- 先解决主痛点，才能尽快验证“无缝切 Tab”是否真的成立。

### 先拆数据域，再做全量真机复测

- 如果先上主壳但不拆数据域，卡顿可能只是从“切页重建”变成“离屏页串扰”。
- 所以真机验收必须放在拆完运行态/历史态之后。

## 文件结构与职责锁定

**Create:**
- `app/src/main/java/com/binance/monitor/ui/host/MainHostActivity.java`
- `app/src/main/java/com/binance/monitor/ui/host/HostTab.java`
- `app/src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java`
- `app/src/main/java/com/binance/monitor/ui/host/HostNavigationIntentFactory.java`
- `app/src/main/java/com/binance/monitor/ui/host/HostTabPage.java`
- `app/src/main/java/com/binance/monitor/ui/account/history/AccountHistorySnapshotStore.java`
- `app/src/main/java/com/binance/monitor/ui/account/history/AccountStatsRenderSignature.java`
- `app/src/main/java/com/binance/monitor/ui/account/history/AccountStatsSectionDiff.java`
- `app/src/main/java/com/binance/monitor/ui/account/runtime/AccountRuntimeSnapshotStore.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageController.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageController.java`
- `app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageController.java`
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java`
- `app/src/main/java/com/binance/monitor/ui/market/MarketMonitorFragment.java`
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsFragment.java`
- `app/src/main/res/layout/activity_main_host.xml`
- `app/src/main/res/layout/content_account_stats.xml`
- `app/src/main/res/layout/content_account_position.xml`
- `app/src/main/res/layout/content_market_chart.xml`
- `app/src/main/res/layout/content_market_monitor.xml`
- `app/src/main/res/layout/content_settings.xml`
- `app/src/main/res/layout/fragment_account_stats.xml`
- `app/src/main/res/layout/fragment_account_position.xml`
- `app/src/main/res/layout/fragment_market_chart.xml`
- `app/src/main/res/layout/fragment_market_monitor.xml`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/test/java/com/binance/monitor/ui/host/HostTabNavigatorSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/host/MainHostLayoutResourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/host/BottomTabNavigationSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/host/HostTabFragmentRoutingTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountStatsRenderSignatureTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountRuntimeSnapshotStoreTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountStatsFragmentSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountPositionFragmentSourceTest.java`

**Modify:**
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java`
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- `app/src/main/res/menu/menu_bottom_nav.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_market_chart.xml`
- `app/src/main/res/layout/activity_account_stats.xml`
- `app/src/main/res/layout/activity_account_position.xml`
- `app/src/main/res/layout/activity_settings.xml`
- `CONTEXT.md`
- `README.md`
- `ARCHITECTURE.md`

---

### Task 1: 把账户两页先抽成“共享内容布局 + 页面控制器”

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageController.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Create: `app/src/main/res/layout/content_account_stats.xml`
- Create: `app/src/main/res/layout/content_account_position.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Modify: `app/src/main/res/layout/activity_account_stats.xml`
- Modify: `app/src/main/res/layout/activity_account_position.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeSnapshotSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionActivitySourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionLayoutResourceTest.java`

**前置条件：**
- 不改主入口
- 不改数据域
- 不改底部导航行为

**完成定义：**
- 账户统计页和账户持仓页的页面主体不再由 `Activity` 独占
- 旧 `Activity` 功能和视觉行为保持不变
- 控制器已具备后续挂到 `Fragment` 的最小能力

**本任务禁止事项：**
- 禁止顺手引入主壳
- 禁止顺手修改账户刷新签名
- 禁止顺手调整页面业务文案或布局顺序

- [ ] **Step 1: 先写失败测试，锁定旧 Activity 必须改成复用控制器**

```java
@Test
public void accountStatsActivityShouldDelegatePageBindingToController() throws Exception {
    String source = read("src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java");
    assertTrue(source.contains("AccountStatsPageController"));
}
```

- [ ] **Step 2: 运行测试确认当前实现还没抽页**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionActivitySourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest"`

Expected: FAIL，当前账户两页仍由 `Activity` 自己直接持有页面逻辑。

- [ ] **Step 3: 新建共享内容布局，只保留页面内容，不保留底部壳**

```xml
<?xml version="1.0" encoding="utf-8"?>
<merge xmlns:android="http://schemas.android.com/apk/res/android">
    <include layout="@layout/layout_account_stats_header" />
    <androidx.core.widget.NestedScrollView
        android:id="@+id/accountStatsScrollView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</merge>
```

- [ ] **Step 4: 新建页面控制器，让旧 Activity 只负责壳和生命周期**

```java
public final class AccountStatsPageController {
    public void bind(@NonNull View rootView) {
        // 迁移自 AccountStatsBridgeActivity 的页面绑定逻辑
    }

    public void onPageShown() {
        // 页面可见时再接通页面级刷新
    }

    public void onPageHidden() {
        // 页面离屏时断开页面级刷新
    }
}
```

- [ ] **Step 5: 让旧 Activity 通过控制器绑定页面**

```java
public final class AccountStatsBridgeActivity extends AppCompatActivity {
    private AccountStatsPageController pageController;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_stats);
        pageController = new AccountStatsPageController();
        pageController.bind(findViewById(android.R.id.content));
    }
}
```

- [ ] **Step 6: 回跑账户两页相关测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionActivitySourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest"`

Expected: PASS

### Task 2: 建主壳、建 Tab 契约，但先只接账户两页

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/host/MainHostActivity.java`
- Create: `app/src/main/java/com/binance/monitor/ui/host/HostTab.java`
- Create: `app/src/main/java/com/binance/monitor/ui/host/HostTabNavigator.java`
- Create: `app/src/main/java/com/binance/monitor/ui/host/HostNavigationIntentFactory.java`
- Create: `app/src/main/java/com/binance/monitor/ui/host/HostTabPage.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java`
- Create: `app/src/main/res/layout/activity_main_host.xml`
- Create: `app/src/main/res/layout/fragment_account_stats.xml`
- Create: `app/src/main/res/layout/fragment_account_position.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/menu/menu_bottom_nav.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/host/HostTabNavigatorSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/host/MainHostLayoutResourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/host/BottomTabNavigationSourceTest.java`

**前置条件：**
- 账户两页已经能由控制器独立绑定

**完成定义：**
- 主壳可承载账户统计和账户持仓两页
- 这两页切换不再互相拉起 `Activity`
- 切回时保留页面内状态

**本任务禁止事项：**
- 禁止一次性迁入五页
- 禁止把旧入口直接删除
- 禁止使用 `ViewPager2` 代替显式 `show/hide`

- [ ] **Step 1: 先写失败测试，锁定主壳和五个稳定 Tab**

```java
@Test
public void hostTabShouldExposeFiveStableEntries() {
    assertEquals(5, HostTab.values().length);
    assertEquals("market_monitor", HostTab.MARKET_MONITOR.getKey());
    assertEquals("market_chart", HostTab.MARKET_CHART.getKey());
    assertEquals("account_stats", HostTab.ACCOUNT_STATS.getKey());
    assertEquals("account_position", HostTab.ACCOUNT_POSITION.getKey());
    assertEquals("settings", HostTab.SETTINGS.getKey());
}
```

- [ ] **Step 2: 运行测试确认主壳尚不存在**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.HostTabNavigatorSourceTest" --tests "com.binance.monitor.ui.host.MainHostLayoutResourceTest" --tests "com.binance.monitor.ui.host.BottomTabNavigationSourceTest"`

Expected: FAIL，当前仓库还没有 `MainHostActivity` 和 `HostTabNavigator`。

- [ ] **Step 3: 新建 Tab 枚举和可见性契约**

```java
public enum HostTab {
    MARKET_MONITOR("market_monitor", R.id.navigation_market_monitor),
    MARKET_CHART("market_chart", R.id.navigation_market_chart),
    ACCOUNT_STATS("account_stats", R.id.navigation_account_stats),
    ACCOUNT_POSITION("account_position", R.id.navigation_account_position),
    SETTINGS("settings", R.id.navigation_settings);

    private final String key;
    private final int menuItemId;
}
```

```java
public interface HostTabPage {
    void onHostPageShown();
    void onHostPageHidden();
}
```

- [ ] **Step 4: 新建主壳和导航器，先只挂账户两页**

```java
public final class HostTabNavigator {
    public void show(@NonNull FragmentManager fragmentManager,
                     @IdRes int containerId,
                     @NonNull HostTab targetTab) {
        FragmentTransaction transaction = fragmentManager.beginTransaction().setReorderingAllowed(true);
        for (HostTab tab : HostTab.values()) {
            Fragment fragment = fragmentManager.findFragmentByTag(tab.getKey());
            if (fragment == null && (tab == HostTab.ACCOUNT_STATS || tab == HostTab.ACCOUNT_POSITION) && tab == targetTab) {
                fragment = createFragment(tab);
                transaction.add(containerId, fragment, tab.getKey());
            } else if (fragment != null) {
                if (tab == targetTab) {
                    transaction.show(fragment);
                } else {
                    transaction.hide(fragment);
                }
            }
        }
        transaction.commitNowAllowingStateLoss();
    }
}
```

- [ ] **Step 5: 让旧账户页底部导航跳回主壳，不再互跳 Activity**

```java
public final class HostNavigationIntentFactory {
    public static Intent forTab(@NonNull Context context, @NonNull HostTab tab) {
        Intent intent = new Intent(context, MainHostActivity.class);
        intent.putExtra(MainHostActivity.EXTRA_TARGET_TAB, tab.getKey());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }
}
```

- [ ] **Step 6: 回跑主壳和导航测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.HostTabNavigatorSourceTest" --tests "com.binance.monitor.ui.host.MainHostLayoutResourceTest" --tests "com.binance.monitor.ui.host.BottomTabNavigationSourceTest"`

Expected: PASS

### Task 3: 把账户统计页收口到历史态，并改成分段刷新

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/history/AccountHistorySnapshotStore.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/history/AccountStatsRenderSignature.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/history/AccountStatsSectionDiff.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsRenderSignatureTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeSnapshotSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsFragmentSourceTest.java`

**前置条件：**
- 主壳已能常驻切换账户两页
- 账户统计页已有控制器承接页面逻辑

**完成定义：**
- 账户统计签名只看历史态和本页本地状态
- 页面刷新已分为四段
- `historyRevision` 不变时，持仓/挂单变化不再触发整页重算

**本任务禁止事项：**
- 禁止继续沿用“大 `applySnapshot(...)` 全量重绑”当正式主链
- 禁止用 if/flag 临时跳过刷新充当长期方案
- 禁止把运行态字段偷偷塞回签名

- [ ] **Step 1: 先写失败测试，锁定账户统计签名不允许带入运行态**

```java
@Test
public void accountStatsSignatureShouldIgnorePositionsAndPendingOrders() {
    AccountStatsRenderSignature signature = AccountStatsRenderSignature.from(
            "rev-1",
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptyList(),
            "全部产品",
            "全部方向",
            "平仓时间",
            true
    );
    assertFalse(signature.asText().contains("position"));
    assertFalse(signature.asText().contains("pending"));
}
```

- [ ] **Step 2: 运行测试确认旧逻辑仍把运行态拼进刷新签名**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsRenderSignatureTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsFragmentSourceTest"`

Expected: FAIL，当前源码仍会把 `positions / pendingOrders` 计入账户统计整页刷新。

- [ ] **Step 3: 新建历史态读模型仓**

```java
public final class AccountHistorySnapshotStore {
    @NonNull
    public AccountHistoryPayload build(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null || cache.getSnapshot() == null) {
            return AccountHistoryPayload.empty();
        }
        return new AccountHistoryPayload(
                safe(cache.getHistoryRevision()),
                copyTrades(cache.getSnapshot().getTrades()),
                copyCurves(cache.getSnapshot().getCurvePoints()),
                copyMetrics(cache.getSnapshot().getStatsMetrics()),
                copyMetrics(cache.getSnapshot().getCurveIndicators())
        );
    }
}
```

- [ ] **Step 4: 新建签名类和分段差异类**

```java
public final class AccountStatsSectionDiff {
    public final boolean refreshCurveSection;
    public final boolean refreshReturnSection;
    public final boolean refreshTradeStatsSection;
    public final boolean refreshTradeRecordsSection;
}
```

- [ ] **Step 5: 页面控制器只在历史态或本页筛选变化时刷新对应分段**

```java
private void onHistoryPayloadChanged(@NonNull AccountHistoryPayload payload) {
    AccountStatsRenderSignature nextSignature = AccountStatsRenderSignature.from(
            payload.getHistoryRevision(),
            payload.getTrades(),
            payload.getCurvePoints(),
            payload.getStatsMetrics(),
            selectedTradeProductFilter,
            selectedTradeSideFilter,
            selectedTradeSortFilter,
            tradeSortDescending
    );
    AccountStatsSectionDiff diff = AccountStatsSectionDiff.between(lastRenderSignature, nextSignature);
    if (diff.isEmpty()) {
        return;
    }
    renderSections(payload, diff);
    lastRenderSignature = nextSignature;
}
```

- [ ] **Step 6: 回跑账户统计专项测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsRenderSignatureTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsFragmentSourceTest"`

Expected: PASS

### Task 4: 把账户持仓页收口到运行态，并限制离屏刷新

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/runtime/AccountRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionFragment.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountRuntimeSnapshotStoreTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionFragmentSourceTest.java`

**前置条件：**
- 账户统计历史态拆分已经完成

**完成定义：**
- 账户持仓页只读运行态
- 页面离屏时不再做页面级绑定和重算
- 持仓更新保持实时，但不再联动账户统计

**本任务禁止事项：**
- 禁止把历史态字段保留为“以后可能会用”的冗余输入
- 禁止离屏页继续监听后直接改 UI

- [ ] **Step 1: 先写失败测试，锁定运行态仓不应产出历史区块**

```java
@Test
public void runtimeStoreShouldOnlyExposeOverviewPositionsAndPendingOrders() {
    AccountRuntimePayload payload = new AccountRuntimeSnapshotStore().build(cacheWithRuntimeOnly());
    assertNotNull(payload.getOverviewMetrics());
    assertNotNull(payload.getPositions());
    assertNotNull(payload.getPendingOrders());
    assertTrue(payload.getTrades().isEmpty());
    assertTrue(payload.getCurvePoints().isEmpty());
}
```

- [ ] **Step 2: 运行测试确认当前账户持仓页还直接拿整份 Cache**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountRuntimeSnapshotStoreTest" --tests "com.binance.monitor.ui.account.AccountPositionFragmentSourceTest"`

Expected: FAIL，当前账户持仓页仍直接依赖整份账户缓存。

- [ ] **Step 3: 新建运行态读模型仓**

```java
public final class AccountRuntimeSnapshotStore {
    @NonNull
    public AccountRuntimePayload build(@Nullable AccountStatsPreloadManager.Cache cache) {
        if (cache == null || cache.getSnapshot() == null) {
            return AccountRuntimePayload.empty();
        }
        AccountSnapshot snapshot = cache.getSnapshot();
        return new AccountRuntimePayload(
                copyOverview(snapshot.getOverviewMetrics()),
                copyPositions(snapshot.getPositions()),
                copyPendingOrders(snapshot.getPendingOrders()),
                safe(cache.getAccount()),
                safe(cache.getServer()),
                cache.isConnected()
        );
    }
}
```

- [ ] **Step 4: 页面控制器只在页面可见时接通运行态刷新**

```java
public void onPageShown() {
    isPageVisible = true;
    bindLatestRuntimePayload();
}

public void onPageHidden() {
    isPageVisible = false;
}
```

- [ ] **Step 5: 回跑账户持仓专项测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountRuntimeSnapshotStoreTest" --tests "com.binance.monitor.ui.account.AccountPositionFragmentSourceTest"`

Expected: PASS

### Task 5: 迁入行情监控、行情持仓、设置三个剩余 Tab

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageController.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageController.java`
- Create: `app/src/main/java/com/binance/monitor/ui/settings/SettingsPageController.java`
- Create: `app/src/main/java/com/binance/monitor/ui/market/MarketMonitorFragment.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartFragment.java`
- Create: `app/src/main/java/com/binance/monitor/ui/settings/SettingsFragment.java`
- Create: `app/src/main/res/layout/content_market_monitor.xml`
- Create: `app/src/main/res/layout/content_market_chart.xml`
- Create: `app/src/main/res/layout/content_settings.xml`
- Create: `app/src/main/res/layout/fragment_market_monitor.xml`
- Create: `app/src/main/res/layout/fragment_market_chart.xml`
- Create: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
- Test: `app/src/test/java/com/binance/monitor/ui/host/HostTabFragmentRoutingTest.java`

**前置条件：**
- 账户两页在主壳下已经稳定
- 账户数据域拆分已经完成

**完成定义：**
- 五个底部页都由主壳统一承载
- 旧底部页入口全部退成桥接
- 行情主数据链不因迁入主壳而改变语义

**本任务禁止事项：**
- 禁止在迁 Fragment 时顺手重构行情拉取和交易主链
- 禁止改变底部导航顺序和页面语义

- [ ] **Step 1: 先写失败测试，锁定五个 Tab 都必须由主壳创建**

```java
@Test
public void hostNavigatorShouldCreateAllFiveTabFragments() {
    assertEquals(MarketMonitorFragment.class, HostTabNavigator.fragmentClassOf(HostTab.MARKET_MONITOR));
    assertEquals(MarketChartFragment.class, HostTabNavigator.fragmentClassOf(HostTab.MARKET_CHART));
    assertEquals(AccountStatsFragment.class, HostTabNavigator.fragmentClassOf(HostTab.ACCOUNT_STATS));
    assertEquals(AccountPositionFragment.class, HostTabNavigator.fragmentClassOf(HostTab.ACCOUNT_POSITION));
    assertEquals(SettingsFragment.class, HostTabNavigator.fragmentClassOf(HostTab.SETTINGS));
}
```

- [ ] **Step 2: 运行测试确认当前主壳只接通账户两页**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.HostTabFragmentRoutingTest"`

Expected: FAIL

- [ ] **Step 3: 给三页补共享内容布局和控制器**

```java
public final class MarketChartPageController {
    public void bind(@NonNull View rootView) {
        // 迁移自 MarketChartActivity 的页面绑定逻辑，不重构行情数据主链
    }
}
```

- [ ] **Step 4: 让旧 Activity 改成桥接入口**

```java
@Override
protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    startActivity(HostNavigationIntentFactory.forTab(this, HostTab.MARKET_CHART));
    finish();
    overridePendingTransition(0, 0);
}
```

- [ ] **Step 5: 回跑主壳路由测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.HostTabFragmentRoutingTest"`

Expected: PASS

### Task 6: 主入口收口、旧 Activity 退成桥接、补齐文档

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

**前置条件：**
- 五个底部页都已能由主壳承载

**完成定义：**
- `MainHostActivity` 成为唯一底部导航主入口
- 旧底部页 `Activity` 只保留桥接职责
- 文档口径与代码现状一致

**本任务禁止事项：**
- 禁止删掉仍被外部入口依赖的桥接 Activity
- 禁止文档提前宣称尚未落地的结果

- [ ] **Step 1: 先写失败测试，锁定底部页旧 Activity 不能再是主导航路径**

```java
@Test
public void bottomTabNavigationShouldRouteThroughMainHostOnly() throws Exception {
    String chartSource = read("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
    assertFalse(chartSource.contains("new Intent(this, AccountStatsBridgeActivity.class)"));
    assertTrue(chartSource.contains("HostNavigationIntentFactory.forTab"));
}
```

- [ ] **Step 2: 运行测试确认旧对等跳转仍存在**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.BottomTabNavigationSourceTest"`

Expected: FAIL

- [ ] **Step 3: Manifest 主入口切到 MainHostActivity**

```xml
<activity
    android:name=".ui.host.MainHostActivity"
    android:exported="true"
    android:screenOrientation="portrait">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

- [ ] **Step 4: 旧底部页 Activity 全部退成桥接**

```java
public final class AccountPositionActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(HostNavigationIntentFactory.forTab(this, HostTab.ACCOUNT_POSITION));
        finish();
        overridePendingTransition(0, 0);
    }
}
```

- [ ] **Step 5: 更新上下文和文档口径**

```markdown
- 当前底部 Tab 已收口为单主壳 + 常驻页结构。
- 账户统计只受历史态驱动；账户持仓只受运行态驱动。
- 旧底部页 Activity 仅保留桥接职责。
```

- [ ] **Step 6: 回跑导航和编译验证**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.BottomTabNavigationSourceTest" :app:compileDebugJavaWithJavac`

Expected: PASS

### Task 7: 真机验收与性能口径收口

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

**前置条件：**
- 结构改造和数据域拆分全部完成
- APK 可正常编译安装

**完成定义：**
- 有完整真机证据链
- 结构验收、刷新验收、交互验收同时通过
- `CONTEXT.md` 记录实际结果，不写推测

**本任务禁止事项：**
- 禁止只凭主观感受下结论
- 禁止只看一类日志就宣称完成优化

- [ ] **Step 1: 编译并安装迁移后 APK**

Run: `.\gradlew.bat :app:assembleDebug`

Expected: BUILD SUCCESSFUL

Run: `adb -s 7fab54c4 install -r app\\build\\outputs\\apk\\debug\\app-debug.apk`

Expected: Success

- [ ] **Step 2: 按固定路径收集真机证据**

输出目录：

```text
temp/cpu_battery_20260414_single_host_tab_shell
```

固定场景：

```text
行情监控 -> 行情持仓 -> 账户统计 -> 账户持仓 -> 设置 -> 回到账户统计
```

- [ ] **Step 3: 收集日志、帧渲染、Activity 和耗电四类证据**

Run: `adb logcat -d | Select-String "Displayed com.binance.monitor/.ui.account.AccountStatsBridgeActivity|Displayed com.binance.monitor/.ui.host.MainHostActivity|account_render phase=|Skipped"`

Expected: 不再出现“切到账户统计就重新 Display AccountStatsBridgeActivity”的主路径。

Run: `adb shell dumpsys gfxinfo com.binance.monitor`

Expected: 账户统计切回时慢帧和卡顿帧数量明显低于多 Activity 版本。

Run: `adb shell dumpsys activity activities | Select-String "MainHostActivity|AccountStatsBridgeActivity|AccountPositionActivity|MarketChartActivity"`

Expected: 底部导航主链只保留 `MainHostActivity`。

Run: `adb shell dumpsys batterystats --charged com.binance.monitor`

Expected: 切 Tab 场景下 CPU 额外开销下降，不再因账户统计整页重算出现尖峰。

- [ ] **Step 4: 用统一验收口径判定是否通过**

必须同时满足：

```text
1. 底部 Tab 切换不再 startActivity 到其他底部页。
2. 再次切回账户统计页时，不再出现 AccountStatsBridgeActivity onCreate 级别重建。
3. historyRevision 不变时，仅持仓/挂单变化不会触发账户统计整页 applySnapshot。
4. 账户统计页只在 historyRevision 或本页筛选条件变化时刷新对应分段。
5. 同一页无缝切 Tab 时，筛选条件、滚动位置、展开状态保持。
6. 离屏页不执行页面级整页渲染，只在重新显示时按签名补刷。
```

---

## 实施顺序总结

1. 先抽账户两页的共享内容布局和控制器，不动主入口。
2. 再建主壳和 Tab 契约，但只接账户两页，先把“无缝切 Tab”主链跑通。
3. 再拆账户统计历史态和账户持仓运行态，切断整页串扰。
4. 最后迁行情监控、行情持仓、设置三页，统一退役旧底部页主路径。
5. 全部迁完后，再做真机复测和文档口径统一。

## 验收矩阵

- 结构验收：
  `MainHostActivity` 成为唯一底部导航主壳，旧底部页 `Activity` 只保留桥接。
- 刷新验收：
  `AccountStatsRenderSignature` 不再包含 `positions / pendingOrders`，`AccountStatsSectionDiff` 只刷新变化段。
- 生命周期验收：
  当前可见页才允许页面级刷新；离屏页只缓存状态，不做整页重算。
- 真机验收：
  `Displayed`、`Skipped frames`、`gfxinfo`、`batterystats` 四类证据共同通过。

## 量化验收口径

- `Activity` 级验收：
  切换底部 Tab 时，`logcat` 不再出现新的 `Displayed AccountStatsBridgeActivity`、`Displayed AccountPositionActivity`、`Displayed MarketChartActivity` 作为主路径。
- 渲染级验收：
  回到账户统计页时，不再出现整页 `on_create_total` 风格初始化链；如果有渲染日志，只允许出现局部分段刷新日志。
- 数据级验收：
  固定 `historyRevision` 下连续制造持仓变化，账户统计页签名保持不变。
- 状态级验收：
  切换 `账户统计 -> 账户持仓 -> 账户统计` 后，筛选项、排序项、滚动位置、展开区状态不丢失。
- 耗电级验收：
  同一固定操作路径下，CPU 耗电不高于多 `Activity` 基线，且不再出现由账户统计整页重算导致的尖峰。

## 自检结论

- 方案覆盖了用户要求的三件事：模块职责、改动顺序、验收口径。
- 方案显式吸收了参考方案中可取的部分：`HostTab`、`HostTabNavigator`、运行态/历史态拆仓、签名类、分段刷新类、旧页桥接、强制先红后绿。
- 方案明确排除了不接受的路径：多 `Activity` 继续保留、补丁式跳过刷新、离屏页继续整页重算。
