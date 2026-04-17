# Bridge Runtime Finalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把桥接页残留、产品级重复派生、图表弹层高频绑定这 3 条尾线收干净，并在最后统一完成编译与真机闭环验证。

**Architecture:** 这一轮不新增缓存层，也不改网络协议，只继续压职责边界和 UI 绑定频次。先把 `AccountStatsBridgeActivity` 压到“只保留兼容壳”，再把账户条目、悬浮窗、图表摘要的产品级派生彻底收口到统一运行态，最后把图表弹窗和全局状态弹层从主图高频刷新上拆开。

**Tech Stack:** Android Java, XML/ViewBinding, existing Screen/Coordinator pattern, unit/source tests, Gradle, ADB

---

## 文件范围

- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
  旧分析深页兼容桥接页，本轮目标是只保留旧入口、旧字段和源码测试兼容壳。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
  分析深页真实宿主，本轮继续承接桥接页里剩余的前台刷新、登录弹窗和目标区块滚动职责。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java`
  分析页主壳片段，本轮只处理与 `AccountStatsScreen` 对齐后的宿主接线。
- `app/src/main/java/com/binance/monitor/runtime/state/model/ProductRuntimeSnapshot.java`
  产品级统一运行态模型，本轮只补真正被三处 UI 共用的展示字段。
- `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
  统一运行态中心，本轮继续收口产品名称、方向手数、聚合摘要等派生结果。
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
  悬浮窗产品卡片聚合器，本轮删除本地重复产品聚合判断，改读统一运行态。
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java`
  悬浮窗文案格式化器，本轮只保留纯展示格式，不再参与产品级真值派生。
- `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
  图表摘要和叠加层快照工厂，本轮继续删掉本地产品摘要拼接，改读统一运行态。
- `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
  当前持仓条目适配器，本轮保留单笔语义，只删除产品级聚合重复逻辑。
- `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
  挂单条目适配器，本轮与持仓条目保持同一规则。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  图表页共享屏幕对象，本轮重点继续拆刷新入口，并把弹层刷新与主图高频刷新解绑。
- `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
  图表刷新预算模型，本轮继续明确事件到区块的映射边界。
- `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
  图表高频刷新调度器，本轮继续压 overlay/summary/dialog 的合帧边界。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
  图表交易弹窗协调器，本轮只按弹窗自身状态变化更新，不再绑主图每秒刷新。
- `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
  全局状态弹层控制器，本轮只按当前快照和必要 revision 变化更新。
- `CONTEXT.md`
  同步本轮最新停点和后续执行顺序。

---

## A 线：桥接页收尾，压成纯兼容层

### Task A1: 盘点桥接页残留真实职责

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivityBridgeSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeCoordinatorSourceTest.java`

- [ ] Step 1: 列出 `AccountStatsBridgeActivity` 中仍属于真实页面职责的方法和状态，只保留本轮要处理的白名单。
- [ ] Step 2: 先补源码测试，锁定“桥接页只保留旧入口兼容，真实深页宿主归 `AccountStatsScreen`”。
- [ ] Step 3: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityBridgeSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeCoordinatorSourceTest"`，确认当前缺口。
- [ ] Step 4: 在任务说明里记录哪些残留本轮继续保留，避免误删仍被旧专项测试锁定的兼容字段。

### Task A2: 把桥接页里剩余深页宿主职责迁回共享屏幕

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivitySessionSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScreenAnalysisTargetSourceTest.java`

- [ ] Step 1: 先补测试，锁定登录弹窗、前台刷新挂接、目标区块滚动这些能力都由 `AccountStatsScreen` 承担。
- [ ] Step 2: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsScreenAnalysisTargetSourceTest"`，确认覆盖到缺口。
- [ ] Step 3: 迁走桥接页中仍只是转发回 Activity 自己的真实页面职责，让 `AccountStatsFragment` 也只走共享屏幕宿主。
- [ ] Step 4: 保留桥接页旧字段名、旧入口常量和必要旧方法名，不做大规模重命名。
- [ ] Step 5: 回跑本任务测试，确认桥接页继续只剩兼容壳。

### Task A3: 删除桥接页死代码并标注残留类别

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivityTradeHistorySourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeSnapshotSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeLayoutCompatibilitySourceTest.java`

- [ ] Step 1: 搜出桥接页里已经不再被主运行路径命中的方法、字段和 helper。
- [ ] Step 2: 先补或修测试，锁定必须保留的布局兼容契约和可删除的重复逻辑。
- [ ] Step 3: 删除已确认不会再参与主链的死逻辑，但不触碰仍被历史专项测试绑定的兼容壳。
- [ ] Step 4: 在文件头注释或邻近注释里标明剩余内容分别属于“旧入口兼容 / 源码测试兼容 / 待后续迁移残留”。
- [ ] Step 5: 回跑桥接页相关测试，确认桥接页仍可编译、可兼容，但真实职责进一步减少。

---

## B 线：统一运行态删重，收掉产品级重复派生

### Task B1: 盘点三处 UI 的重复产品级派生

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`

- [ ] Step 1: 盘点悬浮窗、图表摘要、持仓/挂单条目里还在各自判断的产品名、手数、盈亏、方向、摘要拼接逻辑。
- [ ] Step 2: 把这些逻辑分成“产品级统一真值”和“单笔条目本地语义”两类。
- [ ] Step 3: 列出真正需要进入 `ProductRuntimeSnapshot` 的字段白名单，不把单笔语义误塞进统一运行态。

### Task B2: 扩充统一运行态产品展示字段

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ProductRuntimeSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`

- [ ] Step 1: 先补失败测试，锁定本轮需要新增或规范化的产品级展示字段和 revision 变化条件。
- [ ] Step 2: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStoreTest"`，确认缺口。
- [ ] Step 3: 只增加三处 UI 真正共用、且不会破坏单笔语义的产品级字段。
- [ ] Step 4: 回跑测试，确认产品级字段派生与 revision 更新稳定。

### Task B3: 让账户条目、悬浮窗、图表摘要共用同一份产品级结果

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingPositionAggregatorTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatterTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactoryTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionAdapterSourceTest.java`

- [ ] Step 1: 先补测试，锁定三处 UI 对同一产品的摘要输入都应来自 `ProductRuntimeSnapshot`。
- [ ] Step 2: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingPositionAggregatorTest" --tests "com.binance.monitor.ui.floating.FloatingWindowTextFormatterTest" --tests "com.binance.monitor.ui.chart.ChartOverlaySnapshotFactoryTest" --tests "com.binance.monitor.ui.account.AccountPositionAdapterSourceTest"`，确认缺口。
- [ ] Step 3: 把悬浮窗和图表摘要中仍在本地重复派生的产品级结果替换成 runtime 字段消费。
- [ ] Step 4: 把账户条目里仅剩的产品级摘要判断继续下沉，保留单笔条目文案在 adapter 内部。
- [ ] Step 5: 回跑测试，确认三处口径一致，多笔同产品时仍保持单笔语义。

### Task B4: 删除旧局部产品级聚合逻辑

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`

- [ ] Step 1: 删除已经被统一运行态替代的局部产品聚合和摘要拼接逻辑。
- [ ] Step 2: 用 `rg -n "summary|aggregate|build.*Summary|productCount|compactDisplay"` 复核仓库内是否仍有双轨残留。
- [ ] Step 3: 把复核结果写进任务记录，明确哪些残留仍属于单笔语义，哪些已全部收口。

---

## C 线：图表弹层与主图高频刷新彻底解绑

### Task C1: 盘点图表页所有刷新入口和命中区块

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradingStructureSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshSourceTest.java`

- [ ] Step 1: 列出 `MarketChartScreen` 当前所有刷新入口，以及它们分别会碰到主图、摘要、叠加层、顶部控件、交易弹窗、全局状态弹层哪一块。
- [ ] Step 2: 把入口重新归类为 `marketTickChanged / productRuntimeChanged / uiStateChanged / dialogStateChanged`。
- [ ] Step 3: 先补源码测试，锁定这份分类结果，避免后续又回到整页刷新。
- [ ] Step 4: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartTradingStructureSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartZoneRefreshSourceTest"`，确认分类约束有效。

### Task C2: 让交易弹窗只因自身状态变化更新

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartQuickTradeSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`

- [ ] Step 1: 先补源码测试，锁定交易弹窗打开后只按弹窗自己的输入状态、提交状态和会话状态更新。
- [ ] Step 2: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartQuickTradeSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartZoneRefreshTest"`，确认缺口。
- [ ] Step 3: 把交易弹窗的数据读取改成“读取当前快照 + 按需监听 revision”，不再绑定主图每秒节拍。
- [ ] Step 4: 让 `ChartRefreshScheduler` 中与弹窗相关的更新只走 `dialogStateChanged`，不再混入 `marketTickChanged`。
- [ ] Step 5: 回跑测试，确认弹窗不再跟着主图高频刷新一起重绑。

### Task C3: 让全局状态弹层只因自身快照变化更新

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`

- [ ] Step 1: 先补测试，锁定全局状态弹层打开后只读取当前状态快照和必要 revision，不参与主图刷新链。
- [ ] Step 2: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartZoneRefreshTest"`，确认缺口。
- [ ] Step 3: 把全局状态弹层更新入口从主图高频刷新链拆开，明确归到 `dialogStateChanged` 或独立轻量监听。
- [ ] Step 4: 回跑测试，确认主图每秒变化不再带着全局状态弹层一起刷新。

### Task C4: 继续压高频刷新合帧边界

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`

- [ ] Step 1: 先补失败测试，锁定同一帧内重复的 overlay/summary/dialog 请求会折叠成最后一次有效更新。
- [ ] Step 2: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshBudgetTest" --tests "com.binance.monitor.ui.chart.MarketChartZoneRefreshTest"`，确认缺口。
- [ ] Step 3: 只在现有 `ChartRefreshScheduler` 基础上继续补合帧规则，不新增缓存层。
- [ ] Step 4: 回跑测试，确认主图、摘要、叠加层、弹层的刷新边界继续收敛。

---

## D 线：文档、编译与最后真机闭环

### Task D1: 同步当前停点与剩余工作

**Files:**
- Modify: `CONTEXT.md`

- [ ] Step 1: 更新“当前正在做什么”，写明本计划已接管后续尾线收口。
- [ ] Step 2: 更新“上次停在哪个位置”，写明后续执行顺序是 A 线、B 线、C 线、D 线。
- [ ] Step 3: 更新“近期关键决定和原因”，强调这轮继续坚持“不新增缓存层，只压职责边界和绑定频次”。

### Task D2: 跑关键回归与编译

**Files:**
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivityBridgeSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScreenAnalysisTargetSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionAdapterSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingPositionAggregatorTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactoryTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`

- [ ] Step 1: 运行桥接页、统一运行态、悬浮窗、账户条目、图表刷新这组关键测试集合。
- [ ] Step 2: 运行 `.\gradlew.bat :app:assembleDebug`，确认调试包构建成功。
- [ ] Step 3: 只在关键测试和编译都通过后进入最后真机验证。

### Task D3: 最后一轮统一真机验证

**Files:**
- Verify only: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] Step 1: 执行 `adb devices`，确认目标手机在线。
- [ ] Step 2: 执行 `adb -s 7fab54c4 install -r app/build/outputs/apk/debug/app-debug.apk`。
- [ ] Step 3: 统一验证 4 个场景：
  - 完整分析深页进入后是否准确滚到目标区块
  - 单产品单条目时，持仓/挂单摘要是否正确吃统一运行态
  - 多笔同产品时，是否仍保持单笔语义
  - 图表主界面、账户条目、悬浮窗在同一产品上的摘要口径是否一致
- [ ] Step 4: 如发现真机问题，只回改对应任务线，不额外扩 scope。

---

## 自检

- 本计划只覆盖当前仍未完成的桥接页收尾、统一运行态删重、图表弹层解绑和最后真机闭环 4 条主线
- 本计划没有重复已经完成的 UI 重做、配色统一和第一轮图表性能收口
- 本计划继续坚持“不新增缓存层，只压职责边界和绑定频次”
- 本计划把真机验证统一放到最后，符合当前执行要求

## 执行交接

Plan complete and saved to `docs/superpowers/plans/2026-04-17-bridge-runtime-finalization-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - 我按任务逐个派发独立子智能体执行，并在任务之间复核  

**2. Inline Execution** - 我在当前会话里按这个计划顺序直接实现

你选哪一种。
