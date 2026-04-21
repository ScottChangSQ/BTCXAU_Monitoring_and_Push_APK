# Market Latency + 15m Gap Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把“行情比币安慢几秒”和“15 分钟 K 线固定缺口”这两个问题一次性收口：服务端市场主链改成 Binance WebSocket 驱动的实时真值，客户端图表改成能识别任意周期的中间缺口并强制修补。

**Architecture:** 这次分成两条主线顺序执行。主线 A 先把服务端市场真值从“REST 轮询 + 1 秒 stream 推送”改成“WS 实时内存真值 + REST 只做冷启动和历史回补”；主线 B 再把客户端图表序列校验从“只看最小间距、只修尾部”改成“逐段校验、发现中间缺口就强制正式快照修补”。两条主线都完成后，再做定向测试、服务器部署和真机复核。

**Tech Stack:** Python FastAPI、Binance Futures REST/WebSocket、Android Java、现有 `MarketChartScreen / DataCoordinator / RefreshHelper` 图表链、Gradle 单测、Python unittest、ADB

---

## 0. 当前已确认诊断

### 0.1 行情比币安慢几秒的已确认原因

- [x] `bridge/mt5_gateway/server_v2.py` 的 `_build_market_stream_symbol_state(...)` 当前每次都走 `_fetch_market_candle_rows_with_cache(...)`
- [x] 这条链路的 `latestPatch` 不是 Binance WebSocket 实时推送来的，而是从 REST 最后一根未闭合 K 线拆出来的
- [x] 当前还叠加了：
  - `MARKET_CANDLES_CACHE_MS`
  - `SNAPSHOT_BUILD_CACHE_MS`
  - `V2_STREAM_PUSH_INTERVAL_MS`
- [x] 结论：这不是偶发卡顿，而是“REST 近实时 + 1 秒推送”的架构结果，所以天然会慢于 Binance 原生 WebSocket

### 0.2 15 分钟固定缺口的已确认原因

- [x] 服务端 `15m` 原始序列已抽查为连续，不像是服务端固定缺桶
- [x] 客户端 `MarketChartDisplayHelper.isSeriesCompatibleForInterval(...)` 现在只看最小正间距，不能发现“中间少一根桶但整体最小间距仍合理”的情况
- [x] 客户端 `MarketChartRefreshHelper.shouldForceRequestForSeriesRepair(...)` 现在只对 `1m` 缺口强制修补
- [x] 客户端 `MarketChartDisplayHelper.mergeRealtimeTail(...)` 只会修最新尾部，不会补历史中间洞
- [x] 结论：15 分钟固定缺口更像是“图上已有洞，但页面没把它判定成必须走正式快照修补”

### 0.3 这次不再处理的内容

- [ ] 不重做账户链
- [ ] 不改页面视觉
- [ ] 不新增新的兼容双轨或兜底缓存层
- [ ] 不把客户端再改成额外轮询补偿，只做真值主链切换和严格缺口修复

---

## 1. 文件边界与责任分配

## 1.1 服务端市场主链

### Existing Files

- Modify: `bridge/mt5_gateway/server_v2.py`
  - 负责市场运行时初始化、`/v1/source`、`/v2/stream`、`/v2/market/candles` 和部署期健康检查
- Modify: `bridge/mt5_gateway/v2_market.py`
  - 负责 REST/WS K 线统一解析、组装标准 candle payload、构建 market candles 响应
- Modify: `bridge/mt5_gateway/tests/test_v2_contracts.py`
  - 负责锁定 `/v2/market` 对外契约

### New Files

- Add: `bridge/mt5_gateway/v2_market_runtime.py`
  - 负责 Binance WebSocket 行情运行时真值、1m patch/closed 状态推进、内存快照读取
- Add: `bridge/mt5_gateway/tests/test_v2_market_runtime.py`
  - 负责 WS 运行时状态机、分钟收盘滚动、快照构建约束

## 1.2 客户端图表缺口修复

### Existing Files

- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDisplayHelper.java`
  - 负责图表序列兼容性判断、序列合并、实时尾部合并
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`
  - 负责恢复前台/自动刷新/切周期时是否允许 skip 或必须正式请求
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  - 负责可见序列应用、切周期后的缓存使用与正式回包替换
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
  - 负责图表请求原因分流、正式请求调度、实时显示链路观察
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/CandleAggregationHelper.java`
  - 负责从分钟底稿聚合长周期，作为缺口识别和 bucket 对齐判断的统一工具

### Tests

- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartDisplayHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/CandleAggregationHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartFragmentRealHostSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLocalFirstStartupSourceTest.java`

---

## 2. 执行顺序总览

### Phase A: 服务端市场主链提速

- [ ] A1. 先落 Binance WS 市场运行时真值
- [ ] A2. 再让 `/v2/stream` 和 `/v2/market/candles` 改读 WS 真值
- [ ] A3. 再做服务端定向测试和服务器部署前验证

### Phase B: 客户端 15m 缺口修复

- [ ] B1. 先补“逐段完整性校验”
- [ ] B2. 再把“有洞必须正式修补”扩到 `15m / 30m / 1h / 4h / 1d`
- [ ] B3. 再做图表页定向回归和真机观察

### Phase C: 联调与最终复核

- [ ] C1. 验证服务器 `/v1/source` 与 `/v2/stream` 市场源信息
- [ ] C2. 验证 APK 里的图表页、悬浮窗、账户页行情时效
- [ ] C3. 验证 `15m` 缺口消失且切周期后不再靠手动切换触发修复

---

## 3. Phase A 详细计划

## A1. 新建 Binance WS 市场运行时真值

### Outcome

- [ ] 服务端内存里有一份正式的 `1m` 市场运行时
- [ ] 这份运行时能持续接 Binance WebSocket `kline_1m`
- [ ] 能区分：
  - 当前未闭合 patch
  - 最近已闭合 1m candle
  - 最近更新时间
  - 当前连接状态

### Files

- Add: `bridge/mt5_gateway/v2_market_runtime.py`
- Modify: `bridge/mt5_gateway/v2_market.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Add: `bridge/mt5_gateway/tests/test_v2_market_runtime.py`

### Method-Level Tasks

- [x] 在 `v2_market_runtime.py` 新增运行时模型：
  - `MarketStreamRuntimeState`
  - `SymbolMinuteRuntime`
  - `MarketStreamConnectionState`
- [x] 在 `v2_market.py` 新增统一解析入口：
  - `build_market_candle_payload(...)` 继续兼容 REST 与 WS 行
  - 新增 `build_market_candle_payload_from_ws_event(...)`
  - 新增 `merge_closed_with_patch(...)`
- [x] 在 `v2_market_runtime.py` 新增状态推进方法：
  - `apply_ws_kline_event(...)`
  - `promote_closed_candle_if_needed(...)`
  - `build_symbol_runtime_snapshot(...)`
- [x] 在 `server_v2.py` 新增后台任务：
  - `start_market_stream_runtime()`
  - `stop_market_stream_runtime()`
  - `_run_binance_market_runtime_loop()`
- [x] `server_v2.py` 里固定订阅：
  - `btcusdt@kline_1m`
  - `xauusdt@kline_1m`
- [x] 冷启动时仅允许一次轻量 REST bootstrap：
  - 每个产品补最近 `2~3` 根 `1m` K 线
  - 只用于 WS 未连上前的首屏初始化
- [x] 明确热路径禁令：
  - `_build_market_stream_symbol_state(...)` 不再每次主动拉 REST
  - `latestPatch` 不再从 REST 最后一行拆出

### TDD 顺序

- [x] 先写 `test_v2_market_runtime.py`：
  - WS 未闭合事件应更新 patch
  - WS 收盘事件应把 patch 提升为 latest closed
  - 连续两次事件应保持同一产品时间单调前进
- [x] 再写 `test_v2_contracts.py` 补充约束：
  - `v2_market_snapshot()` 应优先读取 WS 运行时快照
  - market snapshot 热路径不应触发 `_fetch_market_candle_rows_with_cache(...)`
- [x] 再实现 `v2_market_runtime.py` 和 `server_v2.py`
- [x] 最后跑 Python 单测确认通过

## A2. 让 `/v2/stream` 与 `/v2/market/candles` 改读 WS 真值

### Outcome

- [ ] `/v2/stream` 里的市场部分直接来自 WS 内存真值
- [ ] `/v2/market/candles` 的热路径不再依赖 REST 生成 latest patch
- [ ] REST 只保留：
  - 冷启动 bootstrap
  - 历史窗口补拉
  - WS 断链时显式恢复

### Files

- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_market.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_contracts.py`

### Method-Level Tasks

- [x] `server_v2.py::_build_market_stream_symbol_state(...)`
  - 改成读取 `MarketStreamRuntimeState`
  - 输出 `latestPrice / latestClosedCandle / latestPatch / updatedAt`
- [x] `server_v2.py::_build_v2_market_section(...)`
  - 增加 market runtime 健康元信息：
    - `marketRuntimeMode`
    - `marketRuntimeConnected`
    - `marketRuntimeUpdatedAt`
- [ ] `server_v2.py::_fetch_market_candle_rows_with_cache(...)`
  - 从市场 stream 热路径退出
  - 仅保留给历史查询接口使用
- [ ] `server_v2.py::v2_market_snapshot()`
  - 确保页面总快照里的 market 区不再临时拉 REST
- [x] `server_v2.py::v2_market_candles(...)`
  - [x] `1m`：
    - [x] 闭合 candles 读正式历史窗口
    - [x] `latestPatch` 覆盖读 WS 运行时
  - `5m / 15m / 30m / 1h / 4h / 1d`：
    - 历史仍可从 REST 正式窗口读取
    - 末尾 patch 改为基于 WS `1m` 真值聚合得到
- [ ] 缓存边界重定：
  - `MARKET_CANDLES_CACHE_MS` 只作用于历史窗口回补
  - stream 市场热路径不再吃这层缓存

### TDD 顺序

- [x] 先补 `test_v2_contracts.py`：
  - `v2_market_snapshot()` 热路径读取 WS market runtime
  - `/v1/source` 或市场 source 说明里能看到当前 market runtime mode
- [x] 再补 `test_v2_market_runtime.py`：
  - [x] 从 runtime 构建出的 symbol state 能正确反映 latest closed 与 latest patch
  - [ ] `15m` 聚合 patch 基于 `1m` WS 真值而不是 REST 尾桶
- [x] 再改实现
- [x] 最后验证：
  - `python -m unittest bridge.mt5_gateway.tests.test_v2_market_pipeline bridge.mt5_gateway.tests.test_v2_market_runtime bridge.mt5_gateway.tests.test_v2_contracts.V2ContractTests.test_build_market_stream_symbol_state_should_prefer_market_runtime_snapshot bridge.mt5_gateway.tests.test_v2_contracts.V2ContractTests.test_source_status_should_expose_market_runtime_fields bridge.mt5_gateway.tests.test_v2_contracts.V2ContractTests.test_v2_market_candles_should_use_runtime_patch_for_one_minute -v`

### Current Stop

- [ ] `Phase A` 已完成第一批落地：
  - [x] 新增 `v2_market_runtime.py`
  - [x] `server_v2.py` 已优先读取 WS runtime 构建 symbol state
  - [x] `/v1/source` 已暴露 `marketRuntimeMode / marketRuntimeConnected / marketRuntimeUpdatedAt`
  - [x] `/v2/market/candles` 的 `1m latestPatch` 已优先读取 WS runtime
- [ ] 下一步继续 `A2` 剩余部分：
  - [ ] 让 `/v2/market/snapshot` 热路径彻底退出市场 REST
  - [ ] 把 `5m / 15m / 30m / 1h / 4h / 1d` 的末尾 patch 改成基于 WS `1m` 真值聚合
  - [ ] 重新跑更完整的 Python 契约回归

## A3. 服务端部署前验证

### Outcome

- [ ] 本地或服务器上能明确证明市场主链已切到 WS
- [ ] 不再只是 `v2/stream` 每秒发一次，而是 market 真值本身就实时前进

### Verification

- [ ] `https://tradeapp.ltd/v1/source` 应新增或更新字段：
  - `marketRuntimeMode = "binance-ws"`
  - `marketRuntimeConnected = true`
  - `marketRuntimeUpdatedAt > 0`
- [ ] WebSocket 抓包验证：
  - `wss://tradeapp.ltd/v2/stream` 中 market revision 持续前进
  - 连续 2~3 秒内的最新价变化能直接跟随 WS upstream
- [ ] 对比验证：
  - 同一时刻 Binance 原生流价格
  - 服务器 `/v2/stream` 价格
  - App 图表尾部价格
  - 三者延迟应明显缩短到接近 1 秒以内

---

## 4. Phase B 详细计划

## B1. 把图表序列兼容判断改成逐段完整性校验

### Outcome

- [ ] 只要图上中间缺一根桶，就会被识别为“不兼容/需修补”
- [ ] 不再只靠最小间距推测整段序列健康

### Files

- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDisplayHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/CandleAggregationHelper.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartDisplayHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/CandleAggregationHelperTest.java`

### Method-Level Tasks

- [x] `MarketChartDisplayHelper.isSeriesCompatibleForInterval(...)`
  - 从“最小正间距 >= 期望间距”改成“逐段 gap 必须全部等于目标 bucket 间距”
  - 显式识别：
    - 中间缺桶
    - 倒序
    - 重复 openTime
    - 跨桶错位
- [ ] `MarketChartDisplayHelper` 新增完整性方法：
  - `findFirstBrokenBucket(...)`
  - `hasInternalGap(...)`
  - `isBucketAlignedForInterval(...)`
- [ ] `CandleAggregationHelper` 提供统一 bucket 解析能力给完整性检查复用：
  - `resolveBucketStart(...)` 继续做统一入口
  - 新增对外可复用的 bucket 对齐判断方法
- [x] 兼容规则固定：
  - [x] `1m` 检查 `60_000ms`
  - [x] `5m` 检查 `300_000ms`
  - [x] `15m` 检查 `900_000ms`
  - [x] `30m / 1h / 4h / 1d` 同理逐段检查

### TDD 顺序

- [x] 先补 `MarketChartDisplayHelperTest`：
  - [x] `15m` 中间少一根桶应返回不兼容
  - [x] `1h` 中间重复 openTime 应返回不兼容
  - [x] `4h` 整段连续应返回兼容
- [ ] 再补 `CandleAggregationHelperTest`：
  - bucket start/close 对齐规则与图表完整性检查保持一致
- [x] 再实现 helper 改造
- [x] 最后跑：
  - `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.ui.chart.MarketChartDisplayHelperTest --tests com.binance.monitor.ui.chart.MarketChartRefreshHelperTest`

## B2. 把“必须正式修补”扩到长周期

### Outcome

- [ ] `15m / 30m / 1h / 4h / 1d` 一旦有洞，回前台和自动刷新都必须走正式快照修补
- [ ] 不再出现“图上有洞，但因为窗口看起来还新鲜所以被跳过”

### Files

- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartFragmentRealHostSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLocalFirstStartupSourceTest.java`

### Method-Level Tasks

- [x] `MarketChartRefreshHelper.shouldForceRequestForSeriesRepair(...)`
  - 去掉“只有 `1m` 且有 realtime tail 才修”的限制
  - 改成：
    - 只要当前 interval 支持固定 bucket
    - 且 `visibleSeries` 有内部缺口
    - 就必须强制正式请求
- [x] `MarketChartRefreshHelper.shouldSkipRequestOnResume(...)`
  - 把“窗口新鲜”判断置于“无缺口”之后
  - 只要存在缺口，即使窗口时间还新，也不能 skip
- [x] `MarketChartScreen` 应用正式回包时固定规则：
  - [x] 正式回包完整且当前可见序列有洞时，优先整段替换当前窗口
  - [x] 不再继续只走 `mergeRealtimeTail(...)`
- [x] `MarketChartDataCoordinator.requestKlinesCore(...)`
  - [x] 对“系列修补请求”打上明确请求原因
  - [x] 修补请求命中后，不允许被普通 auto-refresh gate 吞掉
- [ ] 切周期规则固定：
  - 若目标周期缓存不完整，可先显示本地窗口
  - 但必须立刻并行发起正式修补请求

### TDD 顺序

- [x] 先补 `MarketChartRefreshHelperTest`：
  - [x] `15m` 有内部缺口时，`RESUME` 必须强制请求
  - [x] `1h` 有内部缺口时，`AUTO_REFRESH` 必须强制请求
  - [x] `15m` 连续完整时，仍可正常 skip
- [x] 再补 `MarketChartFragmentRealHostSourceTest`：
  - [x] 图表宿主必须保留 realtime 观察 + 正式修补双链
- [x] 再补 `MarketChartLocalFirstStartupSourceTest`：
  - [x] local-first 不得把“有洞的缓存”当成最终稳定态
- [x] 再改实现
- [x] 最后跑：
  - `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.ui.chart.MarketChartFragmentRealHostSourceTest --tests com.binance.monitor.ui.chart.MarketChartLocalFirstStartupSourceTest --tests com.binance.monitor.ui.chart.MarketChartDisplayHelperTest --tests com.binance.monitor.ui.chart.MarketChartRefreshHelperTest --tests com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest --tests com.binance.monitor.ui.chart.MarketChartRefreshPlanSourceTest`

### Current Stop

- [ ] `Phase B` 已完成到当前计划的第二段：
  - [x] `MarketChartDisplayHelper` 已改成固定周期逐段完整性校验
  - [x] `MarketChartRefreshHelper` 已把固定周期内部缺口视为必须正式修补
  - [x] `MarketChartDataCoordinator` 已引入显式 `SERIES_REPAIR` 请求原因
  - [x] 修补成功回包已改成不保视口的整段替换分支
  - [x] 已通过 `MarketChartFragmentRealHostSourceTest / MarketChartLocalFirstStartupSourceTest / MarketChartDisplayHelperTest / MarketChartRefreshHelperTest / MarketChartRefreshHelperAdditionalTest / MarketChartRefreshPlanSourceTest`
- [ ] 下一步继续：
  - [ ] 评估是否需要把切周期时的“缓存不完整立即并行修补”再做成更显式的源码约束
  - [x] 已进入 `Phase C` 并完成本地部署目录更新：`dist/windows_server_bundle` 已重建到最新服务端版本
  - [ ] 下一步改为服务器侧重部署与远端 source 验证，再做更完整联调

## B3. 图表页真机复核

### Outcome

- [x] `15m` 图表固定缺口消失
- [x] 不再需要手动切一次周期才可能刷新

### Verification

- [x] 真机进入 `15m`：
  - 连续观察至少 2~3 次 1 分钟滚动
  - 确认最新 15 分钟桶持续更新
- [ ] 在 `15m / 30m / 1h` 之间来回切换：
  - 不应再出现固定位置断层
- [ ] 回前台后停留在 `15m`：
  - 若之前窗口有洞，应自动完成修补
  - 不应靠手动切换周期触发恢复

---

## 5. Phase C 联调与部署验证

## C1. Android 定向回归

- [x] `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.ui.chart.MarketChartDisplayHelperTest --tests com.binance.monitor.ui.chart.CandleAggregationHelperTest --tests com.binance.monitor.ui.chart.MarketChartRefreshHelperTest --tests com.binance.monitor.ui.chart.MarketChartFragmentRealHostSourceTest --tests com.binance.monitor.ui.chart.MarketChartLocalFirstStartupSourceTest --tests com.binance.monitor.ui.chart.MarketChartRevisionRefreshPolicyTest`

## C2. Python 定向回归

- [x] `python -m unittest bridge.mt5_gateway.tests.test_v2_market_runtime bridge.mt5_gateway.tests.test_v2_contracts -v`

## C3. 服务器部署前检查

- [x] 更新部署目录：`dist/windows_server_bundle`
- [x] 检查 `bundle_manifest.json` 和 `server_v2.py` 时间戳已是新版本
- [x] 检查服务器 `/v1/source` 返回的 market runtime 字段已更新

## C4. 真机验证

- [x] 图表页：
  - 最新价刷新应明显贴近 Binance
  - `1m` 与 `15m` 都应持续更新
- [x] 悬浮窗：
  - 行情应随 market runtime 实时推进
  - 不应再出现长时间停住
- [x] 账户页：
  - 行情相关摘要应与图表/悬浮窗口径一致
- [x] 交易后刷新：
  - 完成一笔真实交易后，图表摘要、账户页、悬浮窗都应及时刷新

### Current Stop

- [x] 当前真机联调已确认图表页、悬浮窗、账户页都在新主链上实时推进
- [x] 当前真机联调已确认 `15分` 固定缺口不再复现
- [x] 当前已补完“真实交易后刷新”人工验证：真实账户新增 `0.01手卖出` 后，APP 刷新延迟约 `1s`
- [x] 当前这份任务单的联调闭环已完成

---

## 6. 完成标准

- [x] 服务端 market 主链已从 REST 近实时切到 Binance WS 真值
- [ ] `/v2/stream` 的市场区不再热路径拉 REST
- [x] `/v2/market/candles` 的 latest patch 来自 WS 真值
- [x] 客户端图表能识别 `15m / 30m / 1h / 4h / 1d` 中间缺口
- [x] 有缺口时恢复前台和自动刷新都会强制正式修补
- [x] Android 定向单测通过
- [x] Python 定向单测通过
- [x] 服务器已更新部署
- [x] 真机确认“行情延迟明显缩短”
- [x] 真机确认“15 分钟固定缺口消失”

---

## 7. 推荐执行顺序

1. 先做 `A1`
2. 再做 `A2`
3. 先跑 Python 定向回归
4. 更新 `dist/windows_server_bundle`
5. 部署服务器并检查 `/v1/source`
6. 再做 `B1`
7. 再做 `B2`
8. 跑 Android 定向回归
9. 必要时 `assembleDebug` 并覆盖安装真机
10. 做 `C4` 真机联调复核

---

## 8. 文档用途

- [ ] 这份文档作为修复“行情慢于币安”和“15 分钟固定缺口”的主任务单
- [ ] 后续执行时，优先按这份文档顺序推进
- [ ] 若执行中发现新增根因，只允许在对应 Phase 下补子任务，不改本次总顺序
