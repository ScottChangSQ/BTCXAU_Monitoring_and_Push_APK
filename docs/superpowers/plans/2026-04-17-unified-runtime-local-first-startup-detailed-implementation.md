# Unified Runtime + Local-First Startup Detailed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把上一版总方案继续细化成可直接执行的任务单，覆盖：唯一市场底稿、唯一账户真值、唯一产品级统一运行态、唯一首帧状态机、唯一 revision 刷新主链。目标结果是图表、账户页、分析页、悬浮窗在冷启动、回前台、交易后刷新、切周期等场景下都表现一致：先本地可见、后远端校准、显示不空闪、口径不分叉。

**Parent Plans:**
- [2026-04-17-unified-runtime-local-first-startup-implementation.md](/E:/Github/BTCXAU_Monitoring_and_Push_APK/docs/superpowers/plans/2026-04-17-unified-runtime-local-first-startup-implementation.md)
- [2026-04-17-correct-unified-runtime-implementation.md](/E:/Github/BTCXAU_Monitoring_and_Push_APK/docs/superpowers/plans/2026-04-17-correct-unified-runtime-implementation.md)

**Architecture:** 本计划分成 8 个里程碑。前 4 个里程碑完成真值收口和状态机收口，后 4 个里程碑完成页面替换、旧轮询退出、回归验证和真机闭环。执行时必须保证“先完成真值层，再让页面消费；先完成状态机，再改 loading/空态规则”，不允许反过来做。

**Tech Stack:** Python FastAPI、Android Java、Room、OkHttp、ViewBinding、现有 `Screen/Coordinator` 模式、Gradle 单测、ADB

---

## 0. 交付物定义

### 0.1 这轮最终必须产出的代码资产

- [ ] `MarketRuntimeStore` 及其 model
- [ ] `RuntimeRevisionCenter` 或等价的 revision 统一入口
- [ ] `PageBootstrapState / PageBootstrapSnapshot / PageBootstrapStateMachine`
- [ ] 图表页 market selector 与 product runtime selector
- [ ] 账户页/分析页/悬浮窗统一的本地优先启动策略
- [ ] 图表页、账户页、分析页新的 loading/空态规则
- [ ] 对应源码测试、单测、真机验证记录

### 0.2 这轮不产出的内容

- [ ] 不做新的 UI 视觉改版
- [ ] 不做新的长周期服务端协议扩展，除非当前协议不足以支撑唯一真值
- [ ] 不做性能 benchmark 系统，只做必要真机观测

---

## 1. 里程碑总览

## M1 统一术语、revision 和状态机

### Outcome

- [ ] 所有模块用同一套术语说同一件事
- [ ] 首帧状态不再靠“列表是否为空”推导
- [ ] revision 与 bootstrap state 分工固定

### Files

- Modify: `ARCHITECTURE.md`
- Modify: `docs/superpowers/plans/2026-04-17-unified-runtime-local-first-startup-implementation.md`
- Add: `app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapState.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapSnapshot.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapStateMachine.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/revision/RuntimeRevisionCenter.java`

### Tasks

- [ ] 定义固定术语：
  - 市场底稿
  - 周期展示态
  - 账户运行态
  - 账户历史真值
  - 产品级统一运行态
  - bootstrap state
  - revision
- [ ] 定义首帧 5 态：
  - `MEMORY_READY`
  - `STORAGE_RESTORING`
  - `LOCAL_READY_REMOTE_SYNCING`
  - `REMOTE_READY`
  - `TRUE_EMPTY`
- [ ] 定义 revision 5 类：
  - `marketBaseRevision`
  - `marketWindowRevision`
  - `accountRuntimeRevision`
  - `accountHistoryRevision`
  - `productRuntimeRevision`

### Tests

- [ ] Add `PageBootstrapStateMachineTest`
- [ ] Add `RuntimeRevisionCenterTest`

## M2 市场真值唯一化

### Outcome

- [ ] `MonitorService` 不再把市场数据直接分发给 3 份展示 cache
- [ ] 市场真值先进入 `MarketRuntimeStore`
- [ ] 图表周期展示态开始由 selector 派生

### Files

- Add: `app/src/main/java/com/binance/monitor/runtime/market/MarketRuntimeStore.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/market/model/MarketRuntimeSnapshot.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/market/model/SymbolMarketWindow.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartWarmDisplayPolicyHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`

### Tasks

- [ ] `MonitorService.applyMarketSnapshotFromStream()` 改成只写 `MarketRuntimeStore`
- [ ] `MonitorRepository` 降级为 selector 暴露层，旧 display cache 仅作过渡只读镜像
- [ ] 定义 market selector：
  - `selectLatestPrice(symbol)`
  - `selectClosedMinute(symbol)`
  - `selectDisplayCandles(symbol, interval)`
- [ ] 明确长周期规则：
  - `1m` 读分钟底稿 + patch
  - `5m~1d` 读分钟底稿聚合
  - `1w/1M/1y` 读正式日线/月线窗口聚合

### Tests

- [ ] Add `MarketRuntimeStoreTest`
- [ ] Add `MarketSelectorTest`
- [ ] Modify `MonitorRepositorySourceTest`

## M3 账户真值唯一化

### Outcome

- [ ] 服务端发布态进一步简化
- [ ] 客户端只有一份正式账户运行态
- [ ] `MonitorService` 退出账户第二副本职责

### Files

- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_account.py`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`

### Tasks

- [ ] stream 发布继续只带运行态变化 + `historyRevision`
- [ ] `/v2/account/full` 固定为强一致刷新唯一入口
- [ ] `AccountStatsPreloadManager` 拆成 3 个正式入口：
  - `applyPublishedAccountRuntime(...)`
  - `refreshHistoryForRevision(...)`
  - `fetchFullForUi(...)`
- [ ] `MonitorService` 删除所有只为第二份账户聚合副本存在的字段和流程

### Tests

- [ ] Modify `test_v2_account_pipeline.py`
- [ ] Modify `AccountStatsPreloadManagerTest`
- [ ] Modify `MonitorServiceSourceTest`

## M4 产品级统一运行态唯一化

### Outcome

- [ ] 图表、账户页、悬浮窗的产品级摘要来自同一份 runtime
- [ ] 单笔语义和产品级语义彻底分开

### Files

- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ProductRuntimeSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ChartProductRuntimeModel.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/FloatingCardRuntimeModel.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionUiModelFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`

### Tasks

- [ ] 固定 runtime 字段白名单：
  - 展示名
  - 紧凑名
  - 当前持仓笔数
  - 当前挂单笔数
  - 产品总手数
  - 方向手数
  - 产品净盈亏
  - 跨页摘要文案
- [ ] 固定禁止上升到产品级 runtime 的字段：
  - ticket
  - 单笔开仓价
  - 单笔止盈止损
  - 单笔 side 原始文案
- [ ] 让 3 个页面/控件改读 runtime selector：
  - 图表摘要
  - 账户页产品摘要
  - 悬浮窗卡片摘要

### Tests

- [ ] Modify `UnifiedRuntimeSnapshotStoreTest`
- [ ] Add `ProductRuntimeSelectorConsistencyTest`

## M5 首帧状态机接入图表页

### Outcome

- [ ] 图表页不再先空图再 loading
- [ ] 本地 K 线缓存能真正抢到首帧
- [ ] 冷启动、切产品/切周期、回前台三种情形分开处理

### Files

- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`

### Method-Level Tasks

- [ ] `MarketChartPageRuntime.onColdStart()`：
  - 先建立 bootstrap state
  - 先消费内存态
  - 再启动持久化恢复
  - 并行请求远端
- [ ] `MarketChartScreen.restorePersistedCache()`：
  - 不再只做后台任务触发
  - 必须同步写入 `STORAGE_RESTORING`
- [ ] `MarketChartScreen.showLoading()`：
  - 从“纯 bool 控制”改成“按 bootstrap state 控制”
- [ ] `MarketChartScreen.invalidateChartDisplayContext()`：
  - 拆成 `invalidateForColdStart()` 与 `invalidateForSelectionChange()`
- [ ] `MarketChartDataCoordinator.requestKlinesCore()`：
  - 不再因为 `loadedCandles.isEmpty()` 就直接把冷启动当成真正空图
- [ ] `MarketChartRefreshHelper`：
  - 冷启动计划与常规 refresh 计划分开

### UI Rules

- [ ] `STORAGE_RESTORING`：
  - 保留当前可见图或显示轻量恢复态
  - 不显示阻塞式 loading
- [ ] `LOCAL_READY_REMOTE_SYNCING`：
  - 先画本地窗口
  - 顶部显示轻量同步中
- [ ] `REMOTE_READY`：
  - 远端数据覆盖本地窗口
- [ ] `TRUE_EMPTY`：
  - 仅在本地恢复 miss 且远端暂无结果时出现

### Tests

- [ ] Add `MarketChartBootstrapStateSourceTest`
- [ ] Add `MarketChartLocalFirstStartupTest`
- [ ] Modify `MarketChartRefreshBudgetTest`

## M6 首帧状态机接入账户页和分析页

### Outcome

- [ ] 账户页不再先空模型
- [ ] 分析页不再先出现“暂无曲线数据”
- [ ] 本地账户快照和本地历史统计能真正先显示

### Files

- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/history/AccountHistorySnapshotStore.java`

### Method-Level Tasks

- [ ] `AccountStatsPreloadManager.start()`：
  - 启动后立即预热 storage hydrate
  - 把可命中的本地快照尽量提前装进 `latestCache`
- [ ] `AccountSnapshotRefreshCoordinator.scheduleStoredSnapshotRestoreIfNeeded()`：
  - 设置 bootstrap state
  - 明确 `STORAGE_RESTORING` 与 `TRUE_EMPTY` 边界
- [ ] `AccountPositionPageController`：
  - 初始 `empty()` 不再直接作为首帧可见模型
  - 分离“恢复态壳子”和“真空空态”
- [ ] `AccountStatsScreen.bindLocalMeta()`：
  - 先判断内存态与本地历史态
  - 命中就直接画本地历史内容
  - 不再默认先写“待同步/暂无曲线数据”
- [ ] `AccountStatsPageRuntime.onColdStart()`：
  - 先触发本地态绑定
  - 再开远端同步

### UI Rules

- [ ] 账户页：
  - 恢复态显示“恢复本地账户快照中”
  - 本地命中时先显示概览、持仓、挂单
- [ ] 分析页：
  - 恢复态显示“恢复本地历史统计中”
  - 本地命中时先显示曲线、统计、历史交易摘要
- [ ] 两页都禁止在 `STORAGE_RESTORING` 期间先应用空模型

### Tests

- [ ] Add `AccountBootstrapStateSourceTest`
- [ ] Add `AccountPositionLocalFirstStartupTest`
- [ ] Add `AccountStatsScreenBootstrapSourceTest`
- [ ] Add `AccountStatsScreenLocalFirstStartupTest`

## M7 定时器退出主链，revision 接管常规刷新

### Outcome

- [ ] 页面不再靠固定频率刷新获得同步感
- [ ] 定时器只剩保活、过期判断、回源补拉
- [ ] 页面区块只因 revision 前进而更新

### Files

- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`

### Tasks

- [ ] 图表页 `autoRefreshRunnable` 退出常规 UI 主链
- [ ] 分析页 schedule loop 改成 stale/backfill gate
- [ ] 悬浮窗刷新改成相关产品 revision 命中才刷
- [ ] 账户页分区刷新按：
  - overview revision
  - positions revision
  - pending revision
  - history revision
- [ ] 图表页分区刷新按：
  - market window revision
  - product runtime revision
  - dialog/ui state

### Tests

- [ ] Modify `MarketChartRefreshBudgetTest`
- [ ] Add `AccountRevisionRefreshPolicyTest`
- [ ] Add `FloatingRevisionRefreshPolicyTest`

## M8 删除旧双轨并完成闭环验证

### Outcome

- [ ] 旧 display cache 真值职责删除
- [ ] 旧空态路径删除
- [ ] 旧页面级第二真值删除
- [ ] 编译、测试、真机验证通过

### Files

- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

### Tasks

- [ ] 删除旧显示 cache 真值入口
- [ ] 删除旧空模型直接上屏逻辑
- [ ] 删除图表冷启动先清空图表的旧路径
- [ ] 删除分析页默认先落“暂无曲线数据”的旧路径
- [ ] 删除服务层第二份账户聚合状态
- [ ] 更新项目文档和停点说明

### Verification

- [ ] Python：
  - `test_v2_account_pipeline.py`
  - `test_v2_contracts.py`
- [ ] Android：
  - bootstrap state tests
  - revision policy tests
  - unified runtime tests
  - chart/account local-first startup tests
- [ ] `.\gradlew.bat assembleDebug`
- [ ] `adb -s 7fab54c4 install -r app/build/outputs/apk/debug/app-debug.apk`

---

## 2. 首帧状态机详细定义

## 2.1 状态定义

### `MEMORY_READY`

- [ ] 含义：内存里已有当前页面可消费的真值
- [ ] UI：直接画内容，不显示恢复态
- [ ] 下一跳：
  - revision 前进 -> 继续内容刷新
  - 远端强刷完成 -> `REMOTE_READY`

### `STORAGE_RESTORING`

- [ ] 含义：内存 miss，但本地持久化恢复仍在进行中
- [ ] UI：显示轻量恢复态，不允许先落真正空态
- [ ] 下一跳：
  - 本地命中 -> `LOCAL_READY_REMOTE_SYNCING`
  - 本地 miss 且远端无数据 -> `TRUE_EMPTY`
  - 远端先到 -> `REMOTE_READY`

### `LOCAL_READY_REMOTE_SYNCING`

- [ ] 含义：本地数据已上屏，远端仍在同步
- [ ] UI：本地内容 + 轻量“同步中”
- [ ] 下一跳：
  - 远端完成 -> `REMOTE_READY`
  - revision 无变化 -> 保持当前态

### `REMOTE_READY`

- [ ] 含义：远端最新数据已成为当前显示主版本
- [ ] UI：标准正常态
- [ ] 下一跳：
  - revision 前进 -> 局部刷新

### `TRUE_EMPTY`

- [ ] 含义：本地恢复完成确认 miss，远端也暂无可画内容
- [ ] UI：允许真正空态和重试入口
- [ ] 下一跳：
  - 本地补到数据 -> `LOCAL_READY_REMOTE_SYNCING`
  - 远端补到数据 -> `REMOTE_READY`

## 2.2 非法状态流转

- [ ] 禁止 `STORAGE_RESTORING -> empty model visible`
- [ ] 禁止 `LOCAL_READY_REMOTE_SYNCING -> true empty`
- [ ] 禁止 `REMOTE_READY -> stale local rollback`
- [ ] 禁止远端启动请求直接强制 `MEMORY_READY/LOCAL_READY_REMOTE_SYNCING` 退回 loading 空态

---

## 3. 方法改造清单

## 3.1 `AccountStatsPreloadManager`

### 新增方法

- [ ] `warmLatestCacheFromStorageAsync()`
- [ ] `getBootstrapCacheForCurrentSession()`
- [ ] `hasRenderableStoredHistory(...)`

### 改造方法

- [ ] `start()`
  - 启动时就做 storage hydrate 预热
- [ ] `hydrateLatestCacheFromStorage()`
  - 保留后台线程约束
  - 返回值显式区分“miss”和“命中但不匹配当前会话”

## 3.2 `MarketChartScreen`

### 新增方法

- [ ] `beginChartBootstrap()`
- [ ] `applyBootstrapState(...)`
- [ ] `showChartRestoreHint(...)`
- [ ] `hideChartRestoreHint()`
- [ ] `invalidateForColdStart()`
- [ ] `invalidateForSelectionChange()`

### 改造方法

- [ ] `restorePersistedCache(...)`
- [ ] `schedulePersistedCacheRestore(...)`
- [ ] `applyPersistedCacheRestoreResult(...)`
- [ ] `showLoading(boolean loading)` -> 改为状态驱动
- [ ] `invalidateChartDisplayContext()`

## 3.3 `AccountSnapshotRefreshCoordinator`

### 新增方法

- [ ] `enterStorageRestoringState()`
- [ ] `enterLocalReadyRemoteSyncingState(...)`
- [ ] `enterTrueEmptyStateIfNeeded()`

### 改造方法

- [ ] `enterAccountScreen(boolean coldStart)`
- [ ] `scheduleStoredSnapshotRestoreIfNeeded()`
- [ ] `applyPreloadedCacheIfAvailable()`
- [ ] `requestSnapshot()`

## 3.4 `AccountStatsScreen`

### 新增方法

- [ ] `showAnalysisRestoreHint(...)`
- [ ] `hideAnalysisRestoreHint()`
- [ ] `applyAnalysisBootstrapState(...)`

### 改造方法

- [ ] `bindLocalMeta()`
- [ ] `hasRenderableHistorySections(...)`
- [ ] `applyLoggedOutEmptyState()` 仅用于真正无可恢复数据时

---

## 4. 测试矩阵

## 4.1 冷启动矩阵

- [ ] 图表页：
  - 内存命中
  - 本地命中
  - 本地 miss + 远端命中
  - 本地 miss + 远端 miss
- [ ] 账户页：
  - 内存命中
  - 本地命中
  - 本地 miss + 远端命中
  - 本地 miss + 远端 miss
- [ ] 分析页：
  - 本地有 trades
  - 本地有 curvePoints
  - 本地两者都无
  - 远端先到

## 4.2 交互矩阵

- [ ] 切周期
- [ ] 切产品
- [ ] 回前台
- [ ] 交易后强一致刷新
- [ ] 账号切换
- [ ] 悬浮窗回到主界面

## 4.3 口径矩阵

- [ ] 同一产品在图表、账户页、悬浮窗摘要一致
- [ ] 多笔同产品时单笔语义不被产品聚合覆盖
- [ ] 长周期不常驻热算
- [ ] 本地首帧内容与后续远端覆盖口径一致

---

## 5. 真机验证步骤

- [ ] 安装 debug 包到 `7fab54c4`
- [ ] 清空 app 进程
- [ ] 冷启动进图表页，观察：
  - 是否先看到本地 K 线窗口
  - 是否仍出现空图闪烁
- [ ] 冷启动进账户页，观察：
  - 是否先看到本地概览/持仓/挂单
  - 是否仍出现空列表闪烁
- [ ] 冷启动进分析页，观察：
  - 是否先看到本地曲线/统计
  - 是否仍先出现“暂无曲线数据”
- [ ] 完成一笔交易，观察：
  - 产品摘要是否 3 处一致
  - 首帧本地态是否被远端错误清空
- [ ] 回前台，观察：
  - 是否走局部校准，不再整页从无到有

---

## 6. 完成标准

- [ ] 统一真值完成
- [ ] 首帧状态机完成
- [ ] 定时器退出 UI 主链完成
- [ ] 图表/账户页/分析页本地优先首帧完成
- [ ] 悬浮窗与主页面口径一致
- [ ] 编译通过
- [ ] 关键测试通过
- [ ] 真机验证通过

---

## 7. 说明

- [ ] 这份文档是当前最细的一版执行计划，应作为后续编码实施的主任务单。
- [ ] 若后续继续细化，只允许补“某一里程碑内的子任务清单”，不再改动总方向。
