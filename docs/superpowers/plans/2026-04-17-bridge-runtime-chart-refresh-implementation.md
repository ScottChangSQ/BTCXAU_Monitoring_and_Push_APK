# Bridge Runtime Chart Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把分析桥接页压成纯兼容层，建立统一运行态快照中心，并把图表页刷新切成分区式局部刷新。

**Architecture:** 先收掉旧兼容入口里的真实页面职责，再把账户/产品运行态收口到单一内存真值层，最后让图表页按事件类型只刷新必要区域。整个方案不新增持久化缓存层，不改网络协议，只清职责边界、统一派生逻辑和降低 UI 绑定频次。

**Tech Stack:** Android Java, XML/ViewBinding, existing `Screen/Coordinator` pattern, unit/source tests, Gradle

---

## A 线：`AccountStatsBridgeActivity` 纯兼容化

### Task A1: 盘点旧入口与 extras

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/HostNavigationIntentFactory.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivityBridgeSourceTest.java`

- [ ] Step 1: 盘点所有进入桥接页的旧入口、extras、默认值和目标行为，形成“旧入口 -> extras -> 目标行为”对照表。
- [ ] Step 2: 用 source test 锁定桥接页仍然是“读取 extras 并调用导航工厂”这一层职责。
- [ ] Step 3: 跑 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityBridgeSourceTest"`，确认当前基线通过。
- [ ] Step 4: 提交仅包含入口映射整理和测试说明的变更。

### Task A2: 定义统一深页目标协议

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/navigation/AnalysisDeepLinkTarget.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/navigation/AccountStatsRouteResolverTest.java`

- [ ] Step 1: 先写失败测试，锁定协议最小字段：`targetType / accountKey / symbol / timeRange / focusSection / filters / source`。
- [ ] Step 2: 跑 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.navigation.AccountStatsRouteResolverTest"`，确认失败。
- [ ] Step 3: 新增 `AnalysisDeepLinkTarget`，只保留导航目标语义，不掺 View/Activity 依赖。
- [ ] Step 4: 重新运行上面的测试，确认通过。
- [ ] Step 5: 提交目标协议模型和测试。

### Task A3: 抽出桥接路由解析器

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/navigation/AccountStatsRouteResolver.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/HostNavigationIntentFactory.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/navigation/AccountStatsRouteResolverTest.java`

- [ ] Step 1: 写失败测试，锁定旧 extras 能被解析成统一目标协议。
- [ ] Step 2: 跑 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.navigation.AccountStatsRouteResolverTest"`，确认失败。
- [ ] Step 3: 新增 `AccountStatsRouteResolver`，把旧 extras 解析逻辑全部收进去。
- [ ] Step 4: 给 `HostNavigationIntentFactory` 增加“按 `AnalysisDeepLinkTarget` 构造 intent”的入口。
- [ ] Step 5: 让桥接页只做：读取 extras -> 调 resolver -> 调导航工厂 -> `finish()`。
- [ ] Step 6: 跑桥接层相关测试并提交。

### Task A4: 把桥接页压成纯跳转壳

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeLayoutCompatibilitySourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivityTradeHistorySourceTest.java`

- [ ] Step 1: 写失败测试，锁定桥接页不再直接请求数据、不再直接做复杂 binding。
- [ ] Step 2: 跑桥接层测试确认失败。
- [ ] Step 3: 把桥接页剩余真实页面职责迁回 `AccountStatsScreen` 或统一深页宿主。
- [ ] Step 4: 重新跑桥接层测试，确认桥接页只保留兼容职责。
- [ ] Step 5: 提交纯兼容化改造。

---

## B 线：统一运行态快照

### Task B1: 盘点三处重复派生逻辑

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`

- [ ] Step 1: 列出图表、账户条目、悬浮窗三处重复的过滤、排序、摘要拼接逻辑。
- [ ] Step 2: 把这些重复逻辑归类成“保留唯一实现 / 删除 / 过渡兼容”三类。
- [ ] Step 3: 记录哪些逻辑以后只允许保留一份，作为后续删旧依据。

### Task B2: 定义统一运行态模型

**Files:**
- Create: `app/src/main/java/com/binance/monitor/runtime/state/model/MarketRuntimeSnapshot.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/state/model/AccountRuntimeSnapshot.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/state/model/ProductRuntimeSnapshot.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/state/model/FloatingCardRuntimeModel.java`
- Create: `app/src/main/java/com/binance/monitor/runtime/state/model/ChartProductRuntimeModel.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`

- [ ] Step 1: 先写失败测试，锁定 `marketRevision / accountRevision / productRevision[symbol]` 三层 revision 规则。
- [ ] Step 2: 跑 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStoreTest"`，确认失败。
- [ ] Step 3: 新增 5 个运行态模型，只表达真值和派生结果，不掺 UI 控件引用。
- [ ] Step 4: 重新跑测试，确认模型与 revision 规则可被测试引用。
- [ ] Step 5: 提交模型定义。

### Task B3: 新增统一运行态中心

**Files:**
- Create: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerTest.java`

- [ ] Step 1: 写失败测试，锁定运行态接入统一 store 后，非当前产品更新不会推进当前产品 revision。
- [ ] Step 2: 跑 store 与 preload manager 测试确认失败。
- [ ] Step 3: 新增 `UnifiedRuntimeSnapshotStore`，提供统一写入口和 selector。
- [ ] Step 4: 在 `AccountStatsPreloadManager` 与 `MonitorService` 接统一写入口。
- [ ] Step 5: 重新运行测试，确认通过。
- [ ] Step 6: 提交统一运行态中心。

### Task B4: 先让悬浮窗和账户条目共用产品运行态

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingPositionAggregatorTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatterTest.java`

- [ ] Step 1: 写失败测试，锁定悬浮窗和账户条目读取同一份 `ProductRuntimeSnapshot`。
- [ ] Step 2: 跑悬浮窗相关测试确认失败。
- [ ] Step 3: 把聚合逻辑下沉到统一运行态派生层，悬浮窗和账户条目只消费派生结果。
- [ ] Step 4: 跑 `FloatingPositionAggregatorTest` 和 `FloatingWindowTextFormatterTest`，确认通过。
- [ ] Step 5: 提交共用产品运行态的改造。

### Task B5: 再让图表页改吃统一产品运行态

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactoryTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/HistoricalTradeAnnotationBuilderTest.java`

- [ ] Step 1: 写失败测试，锁定图表摘要和 overlay 输入改吃 `ChartProductRuntimeModel`。
- [ ] Step 2: 跑图表相关测试确认失败。
- [ ] Step 3: 给 `UnifiedRuntimeSnapshotStore` 增加图表 selector，返回当前产品的图表运行态模型。
- [ ] Step 4: 改 `ChartOverlaySnapshotFactory` 与 `MarketChartScreen`，让它们只消费图表运行态模型。
- [ ] Step 5: 运行图表相关测试，确认通过。
- [ ] Step 6: 提交图表接统一运行态的改造。

### Task B6: 删除旧局部聚合逻辑

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`

- [ ] Step 1: 删除已经被统一运行态替代的局部产品摘要逻辑。
- [ ] Step 2: 用 `rg -n "summary|aggregate|build.*Summary"` 复核仓库里同类逻辑是否仍有双轨残留。
- [ ] Step 3: 提交删旧收口。

---

## C 线：图表页分区刷新与降绑定频次

### Task C1: 盘点当前所有刷新入口

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradingStructureSourceTest.java`

- [ ] Step 1: 列出 `MarketChartScreen` 的所有刷新入口和它们当前影响的 UI 区域。
- [ ] Step 2: 把入口分类成 `marketTickChanged / productRuntimeChanged / uiStateChanged`。
- [ ] Step 3: 用 source test 锁定这些入口命名和职责，防止后续再次合流成整页刷新。

### Task C2: 定义 zone 与事件

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshZones.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshEvent.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`

- [ ] Step 1: 写失败测试，锁定四个 zone：`StaticZone / UiStateZone / SummaryZone / ChartRenderZone`。
- [ ] Step 2: 写失败测试，锁定三类事件分别只命中必要 zone。
- [ ] Step 3: 跑 zone/budget 测试确认失败。
- [ ] Step 4: 新增 `ChartRefreshZones`、`ChartRefreshEvent`、`ChartRefreshBudget`。
- [ ] Step 5: 重新运行测试，确认通过。
- [ ] Step 6: 提交 zone 和预算定义。

### Task C3: 拆 `MarketChartScreen` 的整页绑定

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`

- [ ] Step 1: 写失败测试，锁定市场 patch 不会带着顶部控件重绑。
- [ ] Step 2: 跑图表 zone 测试确认失败。
- [ ] Step 3: 在 `MarketChartScreen` 拆出 `applyUiState / applySummary / applyOverlay / applyRealtimeTail`。
- [ ] Step 4: 用 `ChartRefreshBudget` 驱动局部刷新，去掉整页式刷新调用。
- [ ] Step 5: 重新运行 zone/budget 测试，确认通过。
- [ ] Step 6: 提交图表分区刷新改造。

### Task C4: 给图表高频刷新做合帧

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`

- [ ] Step 1: 写失败测试，锁定同一帧内只保留最后一次有效 chart render 请求。
- [ ] Step 2: 跑图表 zone 测试确认失败。
- [ ] Step 3: 新增 `ChartRefreshScheduler`，只做高频刷新去重和合帧，不改业务顺序。
- [ ] Step 4: 把主图高频 patch 路径接入调度器。
- [ ] Step 5: 重新运行图表相关测试，确认通过。
- [ ] Step 6: 提交合帧逻辑。

### Task C5: 让弹层与主图刷新解绑

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartQuickTradeSourceTest.java`

- [ ] Step 1: 写 source test，锁定弹层只按自己的状态变化更新，不跟主图每秒刷新联动。
- [ ] Step 2: 跑相关测试确认失败。
- [ ] Step 3: 把弹层打开后的数据读取改成“读快照 + 可选 revision 订阅”，不再绑主图高频事件。
- [ ] Step 4: 重新运行相关测试，确认通过。
- [ ] Step 5: 提交弹层解绑改造。

### Task C6: 补最终回归与文档同步

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactoryTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/HistoricalTradeAnnotationBuilderTest.java`

- [ ] Step 1: 跑本轮关键测试集合：
  - `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.navigation.AccountStatsRouteResolverTest" --tests "com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStoreTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshBudgetTest" --tests "com.binance.monitor.ui.chart.MarketChartZoneRefreshTest" --tests "com.binance.monitor.ui.chart.ChartOverlaySnapshotFactoryTest" --tests "com.binance.monitor.ui.chart.HistoricalTradeAnnotationBuilderTest"`
- [ ] Step 2: 跑 `.\gradlew.bat :app:assembleDebug`，确认构建成功。
- [ ] Step 3: 同步 `CONTEXT.md / README.md / ARCHITECTURE.md` 的第二阶段优化落点。
- [ ] Step 4: 如果真机在线，执行 `adb -s 7fab54c4 install -r app/build/outputs/apk/debug/app-debug.apk`。
- [ ] Step 5: 提交文档与最终验证结果。

---

## 自检

- A 线覆盖了“旧入口 -> 新协议 -> 纯桥接化”
- B 线覆盖了“统一真值 -> 产品级派生 -> 三处共用”
- C 线覆盖了“事件分类 -> zone 刷新 -> 绑定预算 -> 合帧 -> 弹层解绑”
- 计划没有新增持久化缓存层，符合“优先压绑定频次、不再加缓存层”的要求
- 计划没有改网络协议，只继续使用现有 `/v2/*` 主链

## 执行交接

Plan complete and saved to `docs/superpowers/plans/2026-04-17-bridge-runtime-chart-refresh-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - 我按任务逐个派发独立子智能体执行，并在任务之间复核  

**2. Inline Execution** - 我在当前会话里按这个计划顺序直接实现

你选哪一种。
