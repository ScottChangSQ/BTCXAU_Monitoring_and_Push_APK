# 1m Stream Latency Fix Implementation Plan

> 这份计划只解决当前确认的真实问题：`1m` 更新慢、切周期才跳一次；服务端市场推送不是事件驱动；APP 实时链会被补修阻塞。  
> 本计划不再扩展到其它功能，也不再回到“大重构”讨论。

## 目标

把当前市场实时链收口成下面这个正式行为：

1. Binance 有新的 `1m` 行情事件时，服务端最多在 `0.5s` 内对 APP 发布一次新市场快照。
2. APP 只要收到新的市场 stream 消息并通过技术校验，就立刻更新统一市场真值、图表和悬浮窗。
3. 历史补修、账户补拉、配置同步不能阻塞实时市场消息应用。
4. 图表“自动刷新/恢复前台”的长容忍窗不再主导 `1m` 是否更新；实时更新以 stream 为准，补修只负责自愈。

## 已确认的根因

### 根因 1：服务端市场链是“双层拍点”，不是事件驱动

- `bridge/mt5_gateway/server_v2.py` 的 `_consume_market_stream_runtime_message(...)`
  - 现在只做 `v2_market_runtime.apply_ws_kline_event(...)`
  - 没有 `_request_v2_publish(...)`
  - 没有 `v2_bus_wake_event.set()`
- `bridge/mt5_gateway/server_v2.py` 的 `_v2_bus_producer_loop()`
  - 按 `V2_STREAM_PUSH_INTERVAL_MS` 固定拍点醒来
- `bridge/mt5_gateway/server_v2.py` 的 `/v2/stream`
  - 客户端发送循环也按 `V2_STREAM_PUSH_INTERVAL_MS` 固定睡眠

结果：市场事件先等 bus 检查拍，再等客户端发送拍，天然多一层延迟。

### 根因 2：APP 图表刷新仍容忍长时间“不动”

- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`
  - `REALTIME_FRESHNESS_MS = 95_000L`
- `app/src/main/java/com/binance/monitor/constants/AppConstants.java`
  - `SOCKET_STALE_TIMEOUT_MS = 70_000L`

结果：只要真值链没有被明确判定失活，图表自动刷新和恢复前台会长时间 `SKIP`，用户看到的就是“停住不动，切周期才跳一次”。

### 根因 3：APP 把实时 stream 应用和补修塞进同一个串行线程

- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
  - 当前使用 `Executors.newSingleThreadExecutor()`
  - stream 消息应用走这条线程
  - 市场补修走这条线程
  - 异常配置同步也走这条线程
- `repairMarketTruthForSymbol(...)`
  - 会直接调用 `gatewayV2Client.fetchMarketSeries(...)`
  - `GatewayV2Client` 的 `readTimeout` 是 `35s`

结果：一旦补修开始，后面的实时市场消息会在 APP 端排队，实时更新被放大成“明显延迟”。

## 设计原则

### 原则 1：实时链与补链彻底分离

- 实时链只做：
  - socket 收包
  - 技术校验
  - 真值更新
  - UI 刷新派发
- 补链只做：
  - 闭合历史完整性修复
  - 长时间停更后的自愈回补

### 原则 2：APP 不做“这次值不值得更新”的业务判断

APP 端只保留最小技术判断：

- 消息能否解析
- `busSeq` 是否重复或倒序
- 当前消息是否属于支持的 symbol

一旦通过技术判断，就直接进入真值中心。

### 原则 3：服务端负责发布节奏，APP 负责立即消费

- 服务端决定何时向外发布市场快照
- APP 不再用长时间“fresh 窗口”决定是否落图
- APP 只在“没有收到 stream 很久”时才启动补修

## 目标数据流

### 服务端目标流

`Binance 1m WS -> market_stream_runtime -> market_publish_scheduler(0.5s 合批) -> account_publish_state/event -> /v2/stream -> APP`

### APP 目标流

`GatewayV2StreamClient -> realtimeMarketExecutor -> MonitorService.handleV2StreamMessage() -> MonitorRepository.applyMarketRuntimeSnapshot() -> MarketTruthCenter -> LiveData -> 图表/悬浮窗`

### 历史补链目标流

`watchdog 或 gap detector -> repairExecutor -> GatewayV2Client.fetchMarketSeries(1m) -> MonitorRepository.applyRepairedMinuteWindow() -> MarketTruthCenter`

实时链和补链只在 `MonitorRepository / MarketTruthCenter` 汇合，线程不能共用。

## 实施任务

## Task 1：服务端把市场推送改成单层 `0.5s` 合批发布

**目标**

- 删除当前“bus 固定拍 + 客户端固定拍”的双层节拍
- 改成“市场事件唤醒 + 发布器最多 `500ms` 合批一次”

**涉及文件**

- `bridge/mt5_gateway/server_v2.py`
- `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
- `bridge/mt5_gateway/tests/test_v2_contracts.py`
- `bridge/mt5_gateway/tests/test_v2_market_pipeline.py`

**实现规则**

1. `_consume_market_stream_runtime_message(...)` 在成功应用 WS `1m` 事件后，必须显式唤醒市场发布链。
2. 唤醒方式不能再走账户事件语义的 `_request_v2_publish("trade_commit")` 那套 reason 混合链，应该有明确的市场发布触发入口。
3. `V2_STREAM_PUSH_INTERVAL_MS` 正式改为默认 `500`，并继续保留边界约束。
4. `/v2/stream` 不再自己按另一套固定节拍盲发 heartbeat；它应该优先消费最近一次已发布事件。
5. heartbeat 仍可保留，但必须和市场事件发布解耦，不能拦住市场事件即时下发。

**代码级改动点**

- 新增“市场发布唤醒”入口，例如：
  - `_request_v2_market_publish()`
  - 或者“统一 publish wake 入口”但带明确 `market` 原因
- 调整 `_v2_bus_producer_loop()`：
  - 改成“事件等待 + 最长 500ms 合批”
  - 同一合批窗口内多次市场事件只发布一次最新状态
- 调整 `/v2/stream`：
  - 优先按“新 busSeq 可用时立即发送”
  - heartbeat 只作为保活，不作为市场发送主时钟

**验收标准**

1. 同一秒内多次 Binance `1m` patch 更新时，服务端对 APP 最多每 `500ms` 下发一次新 `market.snapshot`。
2. 没有新市场变化时，不再每秒重复发送同一份市场快照。
3. `marketDigest` 在 `latestPatch` 变化时稳定前进。

## Task 2：APP 拆分实时市场线程与补修线程

**目标**

- stream 市场消息不能再和补修、配置同步共用同一个串行执行器

**涉及文件**

- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- `app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java`
- `app/src/test/java/com/binance/monitor/service/MonitorServiceRealtimeIngressTest.java`

**实现规则**

1. 保留服务层串行语义，但至少拆成两条执行通道：
   - `realtimeMarketExecutor`
   - `backgroundRepairExecutor`
2. `GatewayV2StreamClient.onMessage(...)` 进入后，市场消息必须始终投递到 `realtimeMarketExecutor`。
3. `requestMarketTruthRepair(...)`、`syncAbnormalConfigAsync()`、远程会话恢复等耗时任务必须转到 `backgroundRepairExecutor`。
4. 任何阻塞 HTTP 请求都不能跑在实时市场执行器上。

**代码级改动点**

- `MonitorService` 新增两个执行器字段
- `executeOnWorker(...)` 拆分成：
  - `executeRealtimeMarket(...)`
  - `executeBackgroundWork(...)`
- `startPipelineIfNeeded()` 中的 stream `onMessage(...)`
  - 改投递到实时执行器
- `requestMarketTruthRepair(...)`
  - 改投递到补修执行器
- `syncAbnormalConfigAsync()`、`reconcileRemoteSessionIfNeeded()`
  - 改投递到后台执行器

**验收标准**

1. 补修运行中，新的 stream 市场消息仍能立即推进 `MarketTruthCenter`。
2. 实时市场链不再依赖 `GatewayV2Client.fetchMarketSeries(...)` 的阻塞时长。

## Task 3：取消图表实时更新上的长容忍窗门闩

**目标**

- stream 到达后，图表立即刷新
- `95s` / `70s` 只保留给“是否需要补修”的自愈逻辑，不再控制“要不要更新 UI”

**涉及文件**

- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperAdditionalTest.java`

**实现规则**

1. `observeRealtimeDisplayKlines()` 驱动的实时落图，不能再受 `resolvePlan(... SKIP ...)` 影响。
2. `REALTIME_FRESHNESS_MS` 不再用于判断“stream 到了要不要落图”，只用于决定：
   - 自动轮询是否可以放慢
   - 是否需要自愈补修
3. `shouldRequestKlinesOnResume()` 可以保留，但它只影响“回前台是否主动打 REST”，不能抑制 stream 驱动的实时落图。
4. `SOCKET_STALE_TIMEOUT_MS` 适当收紧：
   - 作为补修触发阈值使用
   - 不再作为实时 UI 是否更新的门闩

**建议参数**

- `REALTIME_FRESHNESS_MS`
  - 从 `95_000L` 降到 `3_000L ~ 5_000L` 的“健康判断”量级
- `SOCKET_STALE_TIMEOUT_MS`
  - 从 `70_000L` 降到 `5_000L ~ 10_000L` 的“自愈补修”量级

这里的最终数值以后要以真机网络情况校准，但不能再是几十秒级。

**验收标准**

1. 页面停留在 `1m` 不切周期时，只要 stream 继续推进，图表尾巴持续更新。
2. 切周期不再是 `1m` 最新价更新的主要触发器。

## Task 4：统一悬浮窗、图表、其它周期的最新价出口

**目标**

- 最新价只认统一市场真值，不允许悬浮窗再走旧值或混合值

**涉及文件**

- `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- `app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java`
- `app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/floating/FloatingPositionAggregatorTest.java`

**实现规则**

1. 悬浮窗最新价继续只读当前分钟统一快照。
2. 图表 `1m` 和悬浮窗必须共用同一份当前分钟最新价。
3. `5m~1d` 最新显示必须来自统一真值中心投影，不允许页面单独拿旧缓存价格。

**验收标准**

1. 悬浮窗最新价不再和 `1m` 当前分钟价闪烁切换。
2. 不同周期页尾的最新价显示口径一致。

## Task 5：补修链继续保留，但只负责自愈，不再干预实时主显示

**目标**

- 保留已有 `GapRepairStateStore`
- 继续防止同一缺口无限重试
- 补修只修历史，不阻塞实时主链

**涉及文件**

- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- `app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java`
- `app/src/main/java/com/binance/monitor/runtime/market/truth/GapRepairStateStore.java`
- `app/src/test/java/com/binance/monitor/service/MonitorServiceMarketRepairPolicyTest.java`
- `app/src/test/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinatorTest.java`

**实现规则**

1. 保持“同一缺口无新证据不得重复补”的现有规则。
2. 补修的触发条件缩小为：
   - 闭合 `1m` 历史出现缺口
   - stream 长时间完全不前进
3. 补修回写仍通过 `applyRepairedMinuteWindow(...)`
4. 补修结果不能覆盖当前分钟的实时最新价

## Task 6：服务端部署与客户端联调验证

**目标**

- 不只改源码，还要验证部署包和真机行为一致

**涉及文件**

- `bridge/mt5_gateway/server_v2.py`
- `scripts/server_v2.py`
- `dist/windows_server_bundle/mt5_gateway/server_v2.py`
- `CONTEXT.md`

**执行顺序**

1. 先完成源码实现和测试
2. 再同步 Windows bundle
3. 再编译 APK
4. 再安装真机联调

**联调口径**

1. 服务端：
   - 市场链默认 `500ms` 合批
   - Binance `1m` 新事件到来后，`/v2/stream` 很快出现新的 `busSeq`
2. APP：
   - 停留在 `1m` 页面时，无需切周期也能持续更新
   - 悬浮窗最新价跟随 `1m`
   - 补修运行时，实时链不停止
3. 历史：
   - 缺口可补
   - 同一缺口不会无限循环请求

## 测试计划

## 服务端测试

- `python -m pytest bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
- `python -m pytest bridge/mt5_gateway/tests/test_v2_contracts.py`
- `python -m pytest bridge/mt5_gateway/tests/test_v2_market_pipeline.py`

新增测试重点：

1. 市场 WS 入站后会唤醒发布链
2. `500ms` 合批窗口内多次 market 更新只发布一次最新快照
3. `/v2/stream` 不再必须等第二层固定拍点才发送市场变更

## APP 测试

- `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceRealtimeIngressTest"`
- `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceMarketRepairPolicyTest"`
- `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest"`
- `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest"`
- `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest"`
- `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingPositionAggregatorTest"`

新增测试重点：

1. 实时 stream 与补修不共用执行器
2. 补修进行中，新的 stream 仍可推进真值
3. 图表实时尾巴更新不再被长时间 fresh 窗口阻塞

## 真机验收

1. 打开 `1m` 页面，连续观察至少 2 分钟，不切周期
   - 最新一根应持续前进
2. 同时观察悬浮窗
   - 最新价应与 `1m` 最新价同步
3. 人为制造补修场景或等待 watchdog 补修
   - 补修期间实时价仍继续走
4. 切换 `5m / 15m / 1h`
   - 最新价与 `1m` 不再分叉

## 执行顺序

1. 先做 Task 1，先把服务端市场发布改成单层 `0.5s` 合批
2. 再做 Task 2，拆 APP 实时执行器和补修执行器
3. 再做 Task 3，取消图表实时更新上的长容忍窗门闩
4. 再做 Task 4，复核统一最新价出口
5. 再做 Task 5，收紧补修职责
6. 最后做 Task 6，部署与真机联调

## 风险提示

- 如果只改 APP，不改服务端，`1m` 仍会受到服务端双拍点节奏上限约束。
- 如果只改服务端，不拆 APP 执行器，补修期间实时消息仍可能在 APP 端排队。
- 如果不收紧长容忍窗，即使服务端和执行器都修好，图表仍可能表现出“表面不刷新”。
