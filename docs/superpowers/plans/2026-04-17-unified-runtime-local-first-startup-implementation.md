# Unified Runtime + Local-First Startup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把“统一真值 / 统一 revision / 统一刷新主链”和“首帧先本地、后台远端覆盖”合成一条完整实现路线。最终目标不是只让数据口径统一，也不是只让首帧不空，而是让图表、账户页、分析页、悬浮窗同时满足 3 件事：同一份真值、同一套刷新规则、首帧真正可见。

**Architecture:** 先把市场底稿、账户真值、产品级统一运行态收口成唯一主链，再把页面从“先空后填”改成“先内存/本地恢复态，再远端覆盖态”。刷新主链统一改成 revision 驱动；本地恢复继续异步，但不再允许首帧先落空态。页面只做 selector 消费，不再自带第二份真值或第二套首帧策略。

**Tech Stack:** Python FastAPI、Android Java、Room、OkHttp、ViewBinding、现有 `Screen/Coordinator` 模式、Gradle 单测、ADB

---

## 0. 本方案解决的不是一个点，而是两个互相抵消的问题

### 0.1 只做统一真值，还不够

- [ ] 如果只把市场和账户真值统一，但页面启动仍然“先空后填”，用户看到的仍然会像整页重载。
- [ ] 如果只把图表、账户页、分析页改成“先本地后远端”，但真值层仍然分叉，用户会先看到一套本地口径，再被另一套远端口径纠正，仍然会感觉不稳。

### 0.2 所以这轮必须同时统一 4 件事

- [ ] 唯一市场底稿
- [ ] 唯一账户真值
- [ ] 唯一 revision 驱动刷新
- [ ] 唯一首帧显示状态机

### 0.3 当前已确认的现状问题

**Files:**
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`

- [ ] 市场真值仍拆成多份展示 cache，图表/悬浮窗/账户页消费路径不同。
- [ ] 账户正式快照会落库，但页面首帧仍经常先画空模型、占位图和待同步文案。
- [ ] 图表历史 K 线会落库，但恢复是后台异步；网络请求与 loading 很快开始，导致本地缓存经常来不及抢首帧。
- [ ] 页面“有没有数据”与“本地恢复是否仍在进行中”目前没有被建模成统一状态。

---

## 1. 设计原则

### 1.1 唯一真值原则

- [ ] 市场只有一份底稿真值。
- [ ] 账户只有一份运行态真值和一份历史真值。
- [ ] 产品级聚合只有一份统一运行态。
- [ ] 页面不再维护第二份真值。

### 1.2 首帧不阻塞主线程原则

- [ ] 不允许为了首帧可见而改成主线程同步读库。
- [ ] 本地恢复继续留在后台线程。
- [ ] 但在本地恢复结果未明确 miss 前，页面不允许先进入真空空态。

### 1.3 先本地后远端原则

- [ ] 内存缓存优先级最高。
- [ ] 本地持久化缓存优先级次之。
- [ ] 远端刷新与本地恢复可以并行。
- [ ] 远端不能抢首帧话语权，只能做覆盖和校准。

### 1.4 revision 驱动原则

- [ ] 页面刷新必须由 revision 前进解释。
- [ ] 定时器只做保活、过期判断和回源补拉。
- [ ] 定时器不再承担“按频率刷新 UI”的主职责。

---

## 2. 目标架构

## 2.1 真值层

### 市场真值层

**Proposed Files:**
- Add: `app/src/main/java/com/binance/monitor/runtime/market/MarketRuntimeStore.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/market/model/MarketRuntimeSnapshot.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/market/model/MarketRevisionSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`

- [ ] 统一接收 `latestPatch`、`latestClosedCandle` 和正式历史窗口。
- [ ] 统一维护：
  - 最新价格
  - 分钟底稿
  - 最近已闭合分钟
  - 长周期正式窗口
  - 市场 revision
- [ ] 旧 `displayPriceCache / displayKlineCache / displayOverviewKlineCache` 退出真值职责。

### 账户真值层

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_account.py`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`

- [ ] 服务端只发布一份账户运行态与历史 revision。
- [ ] 客户端只保留：
  - 正式账户运行态 cache
  - 正式历史真值
  - 产品级统一运行态
- [ ] `MonitorService` 不再维护第二份账户聚合状态。

### 产品级统一运行态层

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ProductRuntimeSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ChartProductRuntimeModel.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/FloatingCardRuntimeModel.java`

- [ ] 产品级 runtime 只负责跨图表、账户页、悬浮窗共用的聚合字段。
- [ ] 单笔语义留在各自页面 adapter，不上升为产品级真值。

## 2.2 selector 层

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartWarmDisplayPolicyHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`

- [ ] 图表当前周期 K 线统一经 market selector 派生。
- [ ] 图表摘要统一经 product runtime selector 派生。
- [ ] 悬浮窗卡片统一经 floating selector 派生。
- [ ] 账户页产品摘要统一经 account/product selector 派生。

## 2.3 页面显示状态机层

**Proposed Files:**
- Add: `app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapState.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapSnapshot.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapStateMachine.java`

- [ ] 全项目统一 5 个状态：
  - `MEMORY_READY`
  - `STORAGE_RESTORING`
  - `LOCAL_READY_REMOTE_SYNCING`
  - `REMOTE_READY`
  - `TRUE_EMPTY`
- [ ] 页面首帧必须先进入这 5 态中的某一态，而不是直接从“数组为空”推导 UI。

---

## 3. 页面级启动策略

## 3.1 图表页

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`

### 目标

- [ ] 首帧优先显示内存缓存。
- [ ] 内存无数据时，优先显示本地持久化 K 线窗口。
- [ ] 本地恢复 pending 期间，不显示阻塞式 loading，不先清空图表。
- [ ] 远端请求可并行，但只能做覆盖，不得抢首帧。

### 具体规则

- [ ] `onColdStart()` 时先建立图表 bootstrap state，不立即按 `loadedCandles.isEmpty()` 判空。
- [ ] `restorePersistedCache()` 继续后台执行，但要把状态置为 `STORAGE_RESTORING`。
- [ ] `requestKlines()` 可以立即发起，但 `showLoading()` 必须受 bootstrap state 控制：
  - `STORAGE_RESTORING` 时不允许显示空图 + 强 loading
  - 只能显示轻量“恢复本地行情中”状态
- [ ] 冷启动阶段禁止调用“先清空、后等新数据”的路径。
- [ ] 本地缓存一旦恢复成功，立即进入 `LOCAL_READY_REMOTE_SYNCING`，先上屏本地序列，再等待远端结果覆盖。
- [ ] 只有“本地恢复已明确 miss 且远端也尚无结果”时，才允许进入 `TRUE_EMPTY`。

### 额外约束

- [ ] `invalidateChartDisplayContext()` 只用于切产品/切周期后的新上下文失效，不允许在冷启动无替代数据时先清空首屏。
- [ ] 远端结果若先于本地恢复完成，可直接进入 `REMOTE_READY`；但本地恢复完成后不得回滚显示。
- [ ] 图表页的 loading 文案必须区分：
  - 恢复本地缓存中
  - 正在同步最新行情
  - 当前确无可用行情

## 3.2 账户持仓页

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`

### 目标

- [ ] 页面首帧不再直接使用 `AccountPositionUiModel.empty()` 作为可见态。
- [ ] 本地恢复 pending 时，优先显示恢复态壳子，而不是空列表。
- [ ] 本地恢复成功后，立刻画本地概览/持仓/挂单，再让远端全量确认覆盖。

### 具体规则

- [ ] `AccountStatsPreloadManager.start()` 后立即后台预热一次 storage hydrate，尽量在页面真正打开前就把 `latestCache` 填进内存。
- [ ] `scheduleStoredSnapshotRestoreIfNeeded()` 继续异步，但页面必须在 `STORAGE_RESTORING` 状态下显示“恢复中”，不能先应用空模型。
- [ ] 账户页分 3 层显示：
  - 连接头部
  - 概览
  - 持仓/挂单列表
- [ ] 只要本地缓存命中，三层都应立刻进入 `LOCAL_READY_REMOTE_SYNCING`，而不是先显示“未连接/空列表”。

## 3.3 分析页

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/history/AccountHistorySnapshotStore.java`

### 目标

- [ ] 分析页首帧优先显示本地历史曲线和历史交易，不再先落“暂无曲线数据”。
- [ ] 只要本地有 `trades` 或 `curvePoints`，就应视为可首帧渲染。
- [ ] 远端同步只做新数据覆盖，不再把本地历史内容短暂打掉。

### 具体规则

- [ ] `bindLocalMeta()` 不再把“待同步/暂无曲线数据”当成默认首帧。
- [ ] 页面要先判断：
  - 是否有内存态历史
  - 是否有本地持久化历史
- [ ] 本地历史命中时，直接进入 `LOCAL_READY_REMOTE_SYNCING`：
  - 曲线先画本地
  - 交易统计先画本地
  - 头部只轻量标“同步中”
- [ ] 只有本地历史明确 miss 时，才展示真正的空占位。

---

## 4. 刷新频率与首帧策略如何合并

## 4.1 冷启动与常规刷新必须彻底分开

- [ ] 冷启动目标是“尽快显示本地已知状态”。
- [ ] 常规刷新目标是“让当前显示内容对齐最新 revision”。
- [ ] 这两件事不能继续共用“空数组 + loading + requestKlines/fetchForUi”这一套条件。

## 4.2 定时器角色重定义

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`

- [ ] 图表页 `autoRefreshRunnable` 退出常规 UI 主链。
- [ ] 分析页/账户页的定时拉取只保留为：
  - 远端保活
  - stale 判断
  - 回源兜底
- [ ] 首帧期间不使用“固定频率刷新”驱动页面显示。

## 4.3 revision 与 bootstrap state 的关系

- [ ] `bootstrap state` 负责解释“首帧此刻该显示什么”。
- [ ] `revision` 负责解释“已经显示出来后，为什么需要更新”。
- [ ] 两者职责不能混用：
  - 不能用 revision 直接替代首帧状态
  - 不能用首帧状态代替后续刷新预算

---

## 5. 迁移顺序

- [ ] 必须按以下顺序执行：
  1. 定义统一术语与状态机
  2. 市场真值收口
  3. 账户真值收口
  4. 产品级 runtime 收口
  5. 页面 selector 化
  6. 图表首帧策略改为本地优先
  7. 账户页首帧策略改为本地优先
  8. 分析页首帧策略改为本地优先
  9. 页面级定时刷新退出主链
  10. 删除旧双轨和旧空态路径

- [ ] 不允许先只改某一页首帧，因为会和旧真值层互相打架。
- [ ] 不允许先只把定时器频率调慢或调快，这不会解决首帧可见问题。

---

## 6. 测试与验收

## 6.1 必测场景

- [ ] 冷启动后图表页：
  - 有本地 K 线缓存时，首帧直接显示本地窗口
  - 不再先出现空图
- [ ] 冷启动后账户页：
  - 有本地账户快照时，首帧直接显示本地概览/持仓/挂单
  - 不再先出现空列表
- [ ] 冷启动后分析页：
  - 有本地历史时，首帧直接显示本地曲线/统计
  - 不再先出现“暂无曲线数据”
- [ ] 同一产品在图表、账户页、悬浮窗摘要口径一致
- [ ] 页面已显示本地态后，远端返回只做覆盖，不再先清空再重建

## 6.2 需要新增或调整的测试

**Files:**
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`
- Add: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartBootstrapStateSourceTest.java`
- Add: `app/src/test/java/com/binance/monitor/ui/account/AccountBootstrapStateSourceTest.java`
- Add: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScreenBootstrapSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`
- Modify: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`

- [ ] 锁定图表页冷启动时不允许在 `STORAGE_RESTORING` 状态下先空图。
- [ ] 锁定账户页冷启动时不允许在本地恢复 pending 时先应用空模型。
- [ ] 锁定分析页本地历史命中时必须首帧画本地历史，不允许先落空占位。
- [ ] 锁定 revision 与 bootstrap state 不互相覆盖。

## 6.3 完成标准

- [ ] 图表、账户页、分析页首帧都满足“先内存/本地，再远端覆盖”。
- [ ] 首帧可见的同时，不阻塞主线程。
- [ ] 图表、账户页、悬浮窗继续看同一份产品级真值。
- [ ] 页面不再依赖页面级固定频率刷新来获得“同步感”。
- [ ] 页面不再有“本地明明有缓存，但首帧先空一下”的明显体验。

---

## 7. 风险、假设与未验证前提

- [ ] 假设当前 Room 中的 K 线窗口和账户历史数据足够支撑首帧恢复；如果命中率过低，需先评估本地缓存覆盖范围。
- [ ] 假设 `AccountStatsPreloadManager` 可以安全提前做 storage hydrate 预热；若现有线程模型不允许，需要先加轻量状态锁。
- [ ] 假设图表页当前的上下文失效逻辑可区分“冷启动”与“用户切周期/切产品”；若不能，需要先拆上下文失效原因。
- [ ] 未验证前提：不同机型上本地 Room 读取耗时分布尚未统一测量；实施时需要做一次真机观测，但不做多轮 benchmark。

---

## 8. 与现有方案的关系

- [ ] 本方案覆盖并扩展 [2026-04-17-correct-unified-runtime-implementation.md](/E:/Github/BTCXAU_Monitoring_and_Push_APK/docs/superpowers/plans/2026-04-17-correct-unified-runtime-implementation.md)。
- [ ] 旧方案主要解决“统一真值和统一刷新主链”。
- [ ] 本方案在其基础上补齐“首帧真正可见”的完整实现路径。
- [ ] 后续如果进入编码实施，应以本方案为总计划，旧方案作为其中的真值收口子章节参考。
