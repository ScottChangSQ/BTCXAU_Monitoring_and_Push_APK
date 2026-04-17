# Bridge Runtime Followup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 继续把分析桥接页压成纯兼容层，删掉账户条目/悬浮窗/图表之间重复的产品级派生逻辑，并把图表页剩余高频绑定进一步拆成真正的分区刷新，真机验证统一放到最后一轮执行。

**Architecture:** 这一轮不新增缓存层，不改网络协议，只继续压职责边界。先把 `AccountStatsBridgeActivity` 剩余真实页面职责迁回 `AccountStatsScreen`，再把账户条目、悬浮窗、图表摘要的产品级派生统一收口到 `UnifiedRuntimeSnapshotStore` 一侧，最后继续拆 `MarketChartScreen` 的高频刷新入口和弹层绑定关系。

**Tech Stack:** Android Java, XML/ViewBinding, existing `Screen/Coordinator` pattern, unit/source tests, Gradle, ADB

---

## A 线：`AccountStatsBridgeActivity` 继续压成纯兼容层

### Task A1: 盘点桥接页当前仍在承接的真实页面职责

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivityBridgeSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeCoordinatorSourceTest.java`

- [ ] Step 1: 列出 `AccountStatsBridgeActivity` 里当前仍然属于真实页面逻辑、但已经可以迁回 `AccountStatsScreen` 的方法和状态。
- [ ] Step 2: 先补 source test，锁定“桥接页只负责旧入口兼容、共享屏幕负责真实深页承载”这一职责边界。
- [ ] Step 3: 跑 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityBridgeSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeCoordinatorSourceTest"`，确认先失败或覆盖到当前差距。
- [ ] Step 4: 记录迁移白名单，只迁本轮已验证能安全迁走的方法，避免一次性删太多。

### Task A2: 把桥接页里的深页宿主职责继续迁回 `AccountStatsScreen`

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsFragment.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivitySessionSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScreenAnalysisTargetSourceTest.java`

- [ ] Step 1: 先补测试，锁定登录弹窗、前台刷新挂接、深页目标滚动这些真实宿主能力都应由 `AccountStatsScreen` 承接。
- [ ] Step 2: 跑 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsScreenAnalysisTargetSourceTest"`，确认失败或确认缺口。
- [ ] Step 3: 把桥接页 `pageRuntime.Host` 中仍然只是转发到 Activity 自身的真实页面职责继续迁到 `AccountStatsScreen`。
- [ ] Step 4: 保留桥接页旧字段、旧字符串、旧入口常量，不做大规模删名，避免源码测试雪崩。
- [ ] Step 5: 重新运行本任务测试，确认共享屏幕主链稳定。

### Task A3: 收掉桥接页里的死代码和重复路径

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivityTradeHistorySourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeSnapshotSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeLayoutCompatibilitySourceTest.java`

- [ ] Step 1: 盘点桥接页里已经不会再被真实运行路径使用的方法、字段和旧 helper。
- [ ] Step 2: 先补/改 source test，锁定需要继续保留的兼容契约和可以删除的重复逻辑。
- [ ] Step 3: 删除已确认不再参与主链的 Activity 内部死逻辑，但不改动仍被历史专项测试锁定的兼容入口。
- [ ] Step 4: 跑桥接页相关测试，确认桥接页继续可编译、可兼容、但真实职责进一步减少。

### Task A4: 让桥接页进入“只保留兼容壳 + 共享屏幕宿主”状态

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `ARCHITECTURE.md`
- Modify: `README.md`
- Modify: `CONTEXT.md`

- [ ] Step 1: 用注释或文档明确桥接页当前保留内容分别是“旧入口兼容 / 源码测试兼容 / 仍待迁移残留”哪一类。
- [ ] Step 2: 更新文档，说明桥接页已经不是主实现，只是兼容壳。
- [ ] Step 3: 把下一轮仍未迁完的桥接残留列进 `CONTEXT.md`，避免后续重复盘点。

---

## B 线：统一运行态删重，收掉产品级重复派生逻辑

### Task B1: 盘点账户条目、悬浮窗、图表摘要的重复产品级派生

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`

- [ ] Step 1: 列出这 4 处现在还各自持有的产品级摘要拼接、产品识别、数量统计、盈亏/手数派生逻辑。
- [ ] Step 2: 把它们归成“必须留在 UI 层的单项语义”与“应该收回统一运行态层的产品语义”两类。
- [ ] Step 3: 明确哪些派生结果应该变成 `ProductRuntimeSnapshot` 的正式字段，哪些继续由单项 adapter 自己保留。

### Task B2: 扩充统一运行态的产品级展示字段

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ProductRuntimeSnapshot.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`

- [ ] Step 1: 先补失败测试，锁定本轮需要新增或规范化的产品级展示字段和 revision 变化条件。
- [ ] Step 2: 跑 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStoreTest"`，确认失败。
- [ ] Step 3: 只在 `ProductRuntimeSnapshot` 增加被三处真实共用、且不破坏单笔语义的展示字段。
- [ ] Step 4: 重新跑测试，确认新的产品级字段能稳定派生并受 revision 保护。

### Task B3: 让账户条目、悬浮窗和图表摘要进一步共用同一份产品级结果

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

- [ ] Step 1: 先补测试，锁定三处对同一产品的摘要输入应来自同一份 `ProductRuntimeSnapshot`。
- [ ] Step 2: 跑相关测试，确认失败或确认缺口。
- [ ] Step 3: 把账户条目里仅剩的产品级摘要判断继续下沉，保留单笔文案部分在 adapter，自产品聚合部分统一读 runtime。
- [ ] Step 4: 把悬浮窗和图表摘要还在本地重复拼接的产品级派生替换成 runtime 字段消费。
- [ ] Step 5: 重新跑本任务测试，确认三处口径一致。

### Task B4: 删除旧局部产品级聚合逻辑

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- Modify: `ARCHITECTURE.md`

- [ ] Step 1: 搜索并删除已经被统一运行态替代的产品级本地摘要逻辑。
- [ ] Step 2: 用 `rg -n "summary|aggregate|build.*Summary|productCount"` 复核仓库里是否仍有双轨残留。
- [ ] Step 3: 更新架构文档，说明产品级派生唯一入口已经收口到 `UnifiedRuntimeSnapshotStore`。

---

## C 线：继续压 `MarketChartScreen` 的 UI 绑定频次

### Task C1: 盘点 `MarketChartScreen` 现有刷新入口和命中区块

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradingStructureSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshSourceTest.java`

- [ ] Step 1: 列出当前所有刷新入口，以及它们分别影响主图、摘要、叠加层、顶部控件、弹层哪一块。
- [ ] Step 2: 把入口彻底归类为 `marketTickChanged / productRuntimeChanged / uiStateChanged / dialogStateChanged`。
- [ ] Step 3: 用 source test 锁定这份分类结果，防止后续又回到整页刷新。

### Task C2: 继续拆主图、摘要、叠加层的绑定函数

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`

- [ ] Step 1: 先补失败测试，锁定产品运行态变化不应重绑顶部控件，UI 状态变化不应重画主图。
- [ ] Step 2: 跑 zone/budget 相关测试确认失败。
- [ ] Step 3: 继续把 `MarketChartScreen` 拆成更稳定的分区绑定函数，只保留每个 zone 对应的最小绑定。
- [ ] Step 4: 重新运行测试，确认事件到 zone 的映射更稳定。

### Task C3: 让弹层和主图高频刷新彻底解绑

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartQuickTradeSourceTest.java`

- [ ] Step 1: 先补 source test，锁定交易弹窗和全局状态弹层只能按自己状态变化更新。
- [ ] Step 2: 跑相关测试确认失败。
- [ ] Step 3: 把弹层打开后的数据读取改成“读当前快照 + 可选 revision 监听”，不再订阅主图每秒刷新节拍。
- [ ] Step 4: 重新运行测试，确认弹层不再绑在主图高频刷新上。

### Task C4: 继续压高频刷新合帧边界

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`

- [ ] Step 1: 先补失败测试，锁定同一帧内重复的 overlay/summary 请求会被折叠成最后一次有效更新。
- [ ] Step 2: 跑相关测试确认失败。
- [ ] Step 3: 只在已有 `ChartRefreshScheduler` 基础上继续补合帧规则，不新增缓存层。
- [ ] Step 4: 重新运行测试，确认高频路径继续收敛。

---

## D 线：统一文档、编译和最终真机验证

### Task D1: 同步第二阶段跟进落点

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] Step 1: 更新“已完成 / 未完成 / 下一步”摘要，确保文档和当前真实停点一致。
- [ ] Step 2: 在文档里明确：本计划把真机验证统一放在最后，不在中间阶段打断。

### Task D2: 跑最终回归和编译

**Files:**
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivityBridgeSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScreenAnalysisTargetSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionAdapterSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingPositionAggregatorTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactoryTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`

- [ ] Step 1: 跑桥接页、统一运行态、悬浮窗、图表刷新、账户条目这组关键测试集合。
- [ ] Step 2: 跑 `.\gradlew.bat :app:assembleDebug`，确认调试包构建成功。

### Task D3: 把真机验证统一放到最后执行

**Files:**
- Verify only: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] Step 1: 先执行 `adb devices`，确认目标手机在线。
- [ ] Step 2: 执行 `adb -s 7fab54c4 install -r app/build/outputs/apk/debug/app-debug.apk`。
- [ ] Step 3: 只在全部代码任务完成后，统一验证 4 个场景：
  - 完整分析深页进入后是否准确滚到目标区块
  - 单产品单条目时，持仓/挂单摘要是否正确吃统一运行态
  - 多笔同产品时，是否仍保持单笔语义
  - 图表主界面、账户条目、悬浮窗在同一产品上的摘要口径是否一致
- [ ] Step 4: 如发现真机问题，只回改对应任务线，不额外扩 scope。

---

## 自检

- 本计划只覆盖当前未完成的 4 条主线，没有重复已经完成的视觉重构与第一轮性能收口
- 本计划把真机验证统一放到最后，符合“暂时先不先真机验证，最后再做”的要求
- 本计划继续坚持“不新增缓存层，只压职责边界和绑定频次”
- 本计划把桥接页、统一运行态、图表刷新拆成逐文件任务，便于后续直接执行

## 执行交接

Plan complete and saved to `docs/superpowers/plans/2026-04-17-bridge-runtime-followup-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - 我按任务逐个派发独立子智能体执行，并在任务之间复核  

**2. Inline Execution** - 我在当前会话里按这个计划顺序直接实现

你选哪一种。
