# Correct Unified Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按“正确统一法”把行情、账户、图表、账户页、悬浮窗收口到同一套真值体系，让所有页面基于同一个 revision 看同一版数据，但不把所有周期做成常驻热更新。

**Architecture:** 统一的对象不是“所有周期的成品 K 线”，而是“市场底稿 + 账户底稿 + 产品级统一运行态 + revision 发布机制”。市场侧只保留一份基础真值，当前页面只派生当前需要的周期；账户侧只保留一份正式运行态和一份历史真值；图表、账户页、悬浮窗都改成 selector 消费者，不再各自维护第二份语义。

**Tech Stack:** Python FastAPI、Android Java、OkHttp、ViewBinding、现有 `Screen/Coordinator` 模式、Gradle 单测、ADB

---

## 0. 先锁定“正确统一法”的边界

### 0.1 本方案明确要统一什么

- [ ] 行情统一的是“唯一市场底稿”，不是 1m/5m/15m/1h/1d/1w/1M/1y 全部成品常驻热跑。
- [ ] 账户统一的是“唯一账户运行态 + 唯一历史真值”，不是服务层、页面层、悬浮窗层各自再组一份副本。
- [ ] 刷新统一的是“同一 revision 发布与消费”，不是要求所有控件在同一毫秒换字。
- [ ] 页面统一的是“只读 selector 输出”，不是页面自己拿原始列表二次聚合。

### 0.2 本方案明确不做什么

- [ ] 不做“所有周期 K 线同时实时热更新”的最贵方案。
- [ ] 不新增第二套缓存层去包住旧链路。
- [ ] 不保留“图表走一套、悬浮窗走一套、账户页走一套”的兼容双轨。
- [ ] 不继续依赖页面级自动轮询来制造同步感。

### 0.3 当前已确认的问题

**Files:**
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java`
- `app/src/main/java/com/binance/monitor/ui/chart/ChartWarmDisplayPolicyHelper.java`

- [ ] 当前市场口径被拆成 `latestPatch + latestClosedCandle + displayPriceCache + displayKlineCache + displayOverviewKlineCache`，UI 消费入口天然分叉。
- [ ] 当前图表页还保留页面级 `autoRefreshRunnable`，说明“刷新调度”和“真值变化”没有完全分开。
- [ ] 当前 1d 以下周期和周/月/年周期走的是不同实时策略，说明“周期派生”和“唯一真值”仍混在一起。
- [ ] 当前悬浮窗、账户页、图表摘要仍有不同程度的本地派生残留，无法保证口径永久一致。

---

## 1. 目标架构：统一底稿，不统一所有成品

### 1.1 市场侧目标：一份基础真值 + 按需派生

**Proposed Files:**
- Add: `app/src/main/java/com/binance/monitor/runtime/market/MarketRuntimeStore.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/market/model/MarketRuntimeSnapshot.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/market/model/MarketRevisionSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`

- [ ] 定义唯一市场底稿，最少只包含：
  - 当前最新价格真值
  - 当前正在形成的分钟底稿
  - 最近已闭合的 1 分钟底稿
  - 日线级正式历史窗口
  - 市场 revision 信息
- [ ] 如果服务端当前只推 `latestPatch` 和 `latestClosedCandle`，客户端也必须把它们先并入同一个 `MarketRuntimeStore`，而不是直接分发给多个 UI cache。
- [ ] `MonitorRepository` 从“3 份展示 cache”转成“1 份市场真值 + 若干 selector 输出”，旧 `displayPriceCache / displayKlineCache / displayOverviewKlineCache` 最终只保留兼容过渡期。
- [ ] 市场统一后，UI 层不再直接知道 `latestPatch` 或 `latestClosedCandle`，只知道 selector 结果。

### 1.2 周期派生目标：当前只算当前需要的周期

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartWarmDisplayPolicyHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`

- [ ] `1m` 周期直接由“分钟底稿 + 当前补丁”派生。
- [ ] `5m ~ 1d` 周期只在“当前用户正在看该周期”时，才由分钟底稿聚合派生。
- [ ] `1w / 1M / 1y` 不再尝试常驻吃分钟实时尾部，而是改成基于正式日线/月线底稿按需生成。
- [ ] 切换周期时允许发生“一次派生或一次补拉”，但切走后不继续维持该周期的常驻热算。
- [ ] 明确规定：周期派生结果是短生命周期展示态，不是新的真值源。

### 1.3 账户侧目标：一份正式运行态 + 一份正式历史真值

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `bridge/mt5_gateway/server_v2.py`

- [ ] 服务端只发布一份账户运行态真值，客户端只接一份正式账户运行态，不再允许服务层、悬浮窗、图表各藏一份账户聚合副本。
- [ ] `AccountStatsPreloadManager` 负责账户正式快照与历史真值的进入、失效、强一致补拉。
- [ ] `UnifiedRuntimeSnapshotStore` 负责把账户真值投影成“产品级统一运行态”，供图表、账户页、悬浮窗共用。
- [ ] `MonitorService` 只做“接流 -> 应用真值 -> 推进 revision -> 触发消费者刷新”，不再自带第二份账户快照语义。

### 1.4 revision 目标：所有 UI 看同一版，而不是各自定时刷

**Proposed Files:**
- Add: `app/src/main/java/com/binance/monitor/runtime/revision/RuntimeRevisionCenter.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`

- [ ] 至少拆出 5 类 revision：
  - `marketBaseRevision`
  - `marketWindowRevision(symbol)`
  - `accountRuntimeRevision`
  - `accountHistoryRevision`
  - `productRuntimeRevision(symbol)`
- [ ] 任何 UI 刷新都必须能解释成“因为哪一个 revision 前进了”，不能再解释成“因为定时器到了”。
- [ ] 定时器只保留两种职责：
  - 连接/watchdog 保活
  - 回源/补拉兜底
- [ ] 定时器不再承担常规 UI 刷新主链。

---

## 2. 输入、处理流程、状态变化、输出、上下游影响

### 2.1 行情链路

- [ ] 输入：
  - `/v2/stream` 市场增量
  - `/v2/market/candles` 正式历史窗口
- [ ] 处理流程：
  - `MonitorService` 接收 stream
  - 先写入 `MarketRuntimeStore`
  - 推进市场 revision
  - 当前图表周期按需派生
  - 悬浮窗/账户页只读 selector
- [ ] 状态变化：
  - 市场底稿变化时推进 `marketBaseRevision`
  - 当前周期展示态变化时推进 `marketWindowRevision(symbol)`
- [ ] 输出：
  - 图表主图 K 线
  - 图表右上角行情摘要
  - 悬浮窗价格/量/额
- [ ] 上下游影响：
  - `MonitorRepository` 展示缓存要退出真值职责
  - `MarketChartPageRuntime` 的页面级刷新编排要缩小

### 2.2 账户链路

- [ ] 输入：
  - `/v2/stream` 账户运行态增量
  - `/v2/account/history`
  - `/v2/account/full`
- [ ] 处理流程：
  - 运行态先进正式账户 cache
  - 历史只在 revision 前进或显式强刷时拉取
  - `UnifiedRuntimeSnapshotStore` 从正式账户真值生成产品级 runtime
- [ ] 状态变化：
  - 账户当前状态变化推进 `accountRuntimeRevision`
  - 历史变化推进 `accountHistoryRevision`
  - 产品聚合变化推进 `productRuntimeRevision(symbol)`
- [ ] 输出：
  - 图表持仓/挂单摘要
  - 账户页产品摘要
  - 悬浮窗产品卡片
- [ ] 上下游影响：
  - 页面 adapter 不再做产品级二次聚合
  - `MonitorService` 不再维护额外账户聚合字段

### 2.3 UI 消费链路

- [ ] 输入：
  - selector 输出
  - revision 前进通知
- [ ] 处理流程：
  - 图表只更新当前周期和当前产品相关区块
  - 账户页只更新受影响分区
  - 悬浮窗只更新当前展示产品
- [ ] 状态变化：
  - 页面自己的交互状态仍保留本地管理
  - 页面不再维护市场/账户真值
- [ ] 输出：
  - 用户肉眼看到“同步”
- [ ] 上下游影响：
  - 页面代码更像 consumer，不再像二次数据中心

---

## 3. 分阶段实施

## A 线：先统一术语和真值边界

### Task A1: 锁定术语和唯一真值归属

**Files:**
- Modify: `ARCHITECTURE.md`
- Modify: `docs/superpowers/plans/2026-04-17-data-transport-update-architecture-implementation.md`
- Modify: `docs/superpowers/plans/2026-04-17-bridge-runtime-finalization-implementation.md`

- [ ] 把“市场底稿”“周期展示态”“账户运行态”“账户历史真值”“产品级统一运行态”“revision”写成固定术语。
- [ ] 明确写入文档：周期展示态不是新真值，selector 输出不是新真值。
- [ ] 明确写入文档：本项目以后禁止再引入“页面自己的产品级聚合真值”。

### Task A2: 找出旧双轨入口并列出删重清单

**Files:**
- Modify: `docs/superpowers/plans/2026-04-17-correct-unified-runtime-implementation.md`
- Verify only: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Verify only: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Verify only: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`

- [ ] 列出旧双轨入口：
  - repository 三份市场展示 cache
  - 图表页面级 auto refresh
  - 悬浮窗本地产品聚合
  - 账户页本地产品聚合
- [ ] 为每一处都指定未来归属：删掉、下沉到真值层、或改成 selector。

## B 线：市场唯一真值收口

### Task B1: 引入 `MarketRuntimeStore`

**Proposed Files:**
- Add: `app/src/main/java/com/binance/monitor/runtime/market/MarketRuntimeStore.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/market/model/MarketRuntimeSnapshot.java`
- Add: `app/src/main/java/com/binance/monitor/runtime/market/model/SymbolMarketWindow.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`

- [ ] 让 stream 市场包先落进 `MarketRuntimeStore`，不再直接分发给 3 份 UI cache。
- [ ] store 内部只保留：
  - 最新价格
  - 分钟底稿
  - 最近闭合 1m
  - 日线/月线正式窗口
  - revision
- [ ] 旧 `MonitorRepository` 改成 selector 对外层，而不是底稿持有者。

### Task B2: 图表周期派生改为 selector 化

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartWarmDisplayPolicyHelper.java`

- [ ] 把“当前周期如何得到显示 K 线”变成统一 selector。
- [ ] selector 内部按规则派生：
  - `1m`: 分钟底稿 + patch
  - `5m~1d`: 分钟底稿聚合
  - `1w/1M/1y`: 正式日线/月线窗口聚合
- [ ] selector 只给当前图表页面调用，不给悬浮窗和账户页调用。

### Task B3: 退出旧展示 cache

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Test: `app/src/test/java/com/binance/monitor/data/repository/MonitorRepositorySourceTest.java`

- [ ] `displayPriceCache / displayKlineCache / displayOverviewKlineCache` 先改为过渡只读镜像。
- [ ] 等所有消费者切到 selector 后，再删除这 3 份 cache 的真值职责。
- [ ] 用源码测试锁定：市场真值只能来自 `MarketRuntimeStore`。

## C 线：账户唯一真值与产品级统一运行态收口

### Task C1: 服务端账户发布态继续收口

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_account.py`
- Test: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`

- [ ] 继续沿现有 `account_publish_state` 方向，把账户当前态和历史真值职责彻底分清。
- [ ] 明确 `/v2/account/full` 只服务强一致刷新，不再让客户端自己 snapshot+history 拼整包。
- [ ] stream 只发布运行态变化和 revision，不重复整包构建历史。

### Task C2: 客户端账户主链只保留一份正式运行态

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`

- [ ] `AccountStatsPreloadManager` 只保留：
  - 运行态应用
  - 历史 revision 补拉
  - 强一致 full refresh
- [ ] `MonitorService` 删除所有只为第二份账户副本存在的状态字段。
- [ ] `UnifiedRuntimeSnapshotStore` 成为唯一产品级运行态产出层。

### Task C3: 产品级统一运行态字段白名单

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ProductRuntimeSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ChartProductRuntimeModel.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/FloatingCardRuntimeModel.java`

- [ ] 正式进入产品级 runtime 的字段只允许包括：
  - 展示名/紧凑名
  - 当前持仓笔数
  - 当前挂单笔数
  - 产品总手数/方向手数
  - 产品净盈亏
  - 供三处 UI 共用的摘要文案
- [ ] 单笔开仓价、ticket、side、单笔止盈止损等字段不进入产品级 runtime。

## D 线：页面改为 selector 消费者

### Task D1: 图表页只消费市场 selector + 产品 runtime selector

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`

- [ ] 图表主图只读“当前周期 selector”。
- [ ] 图表右上角摘要和叠加层只读 `selectChartProductRuntime(symbol)`。
- [ ] 图表页不再直接读 repository 多份展示 cache，也不再本地拼产品摘要。

### Task D2: 账户页只保留单笔语义

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionUiModelFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`

- [ ] 账户页产品级摘要统一从 runtime selector 注入。
- [ ] adapter 只保留单笔语义和操作按钮。
- [ ] 多笔同产品时只显示各自单笔语义，不允许把产品聚合结果覆盖成单笔展示。

### Task D3: 悬浮窗只消费 runtime selector

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java`

- [ ] 悬浮窗卡片的数据只从：
  - `selectFloatingCardRuntime(symbol)`
  - 市场 selector
  读取。
- [ ] 悬浮窗不再本地判断产品简称、方向手数、产品级盈亏。

## E 线：刷新机制统一成 revision 驱动

### Task E1: 删除页面级主动轮询主链

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/constants/AppConstants.java`

- [ ] `MarketChartPageRuntime` 的 `autoRefreshRunnable` 不再承担常规行情刷新主链。
- [ ] 图表页进入前台时只做：
  - 订阅 revision
  - 必要时拉一次正式窗口
  - 启动 watchdog/backfill 计时
- [ ] 计时器只负责“数据是否陈旧、是否需要回源补拉”，不是“每到时间就整页刷新”。

### Task E2: 所有区块按 revision 最小刷新

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`

- [ ] 图表页按以下 revision 分区刷新：
  - 市场主图
  - 产品摘要
  - 叠加层
  - 弹层/快捷条
- [ ] 账户页按以下 revision 分区刷新：
  - 概览
  - 持仓
  - 挂单
  - 历史
- [ ] 悬浮窗只在当前相关产品的 `productRuntimeRevision(symbol)` 或市场 selector revision 前进时刷新。

## F 线：清理旧链路与回归

### Task F1: 删除旧双轨和兼容副本

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java`

- [ ] 删除旧页面轮询主链。
- [ ] 删除旧展示 cache 的真值职责。
- [ ] 删除 `MonitorService` 内部第二份账户聚合副本。
- [ ] 删除页面内重复产品级派生逻辑。

### Task F2: 测试与验收

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] 新增或更新测试，覆盖：
  - 市场 selector 派生正确
  - 产品级 runtime 口径一致
  - 多笔同产品时单笔语义不被覆盖
  - revision 不误前进
  - 图表页不再依赖页面级自动轮询
- [ ] 跑 Python 侧账户与发布态测试。
- [ ] 跑 Android 侧市场 selector、账户主链、统一 runtime、图表刷新预算测试。
- [ ] 编译 `assembleDebug` 并真机验证。

---

## 4. 迁移顺序要求

- [ ] 必须按顺序执行：
  1. 术语和真值边界
  2. 市场底稿唯一化
  3. 账户正式运行态唯一化
  4. 产品级统一运行态收口
  5. 页面改 selector 消费
  6. 页面级轮询退出主链
  7. 删除旧双轨
- [ ] 不允许先改某一个页面的展示，再回头补真值层；否则会重新长出兼容分支。
- [ ] 不允许先追求“看起来同步”，再回头统一数据源；否则只是把旧分叉包上一层刷新糖衣。

## 5. 风险、假设与未验证前提

- [ ] 假设服务端可以稳定提供分钟增量、闭合分钟和正式历史窗口；若实际窗口契约不足，需先补服务端协议。
- [ ] 假设当前长周期正式历史接口可支撑 `1w / 1M / 1y` 的按需派生；若不能，需要先补日线/月线真值接口。
- [ ] 假设 `UnifiedRuntimeSnapshotStore` 已足够接管产品级统一运行态；若发现仍混入页面语义，需要先拆 selector 层。
- [ ] 未验证前提：切换高周期时首次派生的真实耗时尚未压测，实施时需要补一次真机观察，但不做多轮 benchmark。

## 6. 完成标准

- [ ] 同一产品在图表、账户页、悬浮窗看到的产品级摘要来自同一个 selector 结果。
- [ ] 图表页不再依赖页面级自动轮询来刷新常规行情。
- [ ] `1m ~ 1d` 只在当前查看时派生，`1w / 1M / 1y` 不做常驻分钟热聚合。
- [ ] `MonitorService` 和页面层都不再维护第二份账户聚合真值。
- [ ] `MonitorRepository` 不再持有 3 份并行市场展示真值。
- [ ] 如果只完成部分，必须明确写成“真值层已收口到哪一步”，不能写成“已完全统一”。

---

## 7. 结论

- [ ] 本方案的核心不是“让所有周期一起跑”，而是“让所有页面看同一份底稿”。
- [ ] 本方案一旦执行完，CPU 和流量的主成本会落在“当前正在看的周期”和“真正发生变化的产品”上，而不是落在所有周期和所有页面的常驻热更新上。
- [ ] 这就是当前项目应采用的最小完整统一方案。
