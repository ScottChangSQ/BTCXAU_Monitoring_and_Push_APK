# Market Direct Push Stream Implementation Plan

> 这份计划只解决当前最新确认的问题：`1m` K 线仍然只在每分钟开始跳动一次。  
> 本次不再继续围绕旧的统一 `v2 bus + marketRevision + changes.market` 机制做局部修补，而是把**市场实时推送链**从统一筛选链里拆出来，收口成一条独立的“正常行情流 + 主显示流”。

## 目标

把市场实时链正式改成下面这条最短路径：

`Binance 1m WS -> 服务端 market runtime -> 固定 0.5s 市场直推 -> APP 真值中心 -> 1m 图表 / 悬浮窗 / 其它周期投影`

本次目标只要求做到：

1. 只要 Binance 在同一分钟内继续推新的 `kline_1m` 更新，服务端就必须最多每 `0.5s` 向 APP 推一次最新市场快照。
2. APP 收到新的市场实时消息后，不再依赖 `changes.market` / `marketRevision` / 全量 snapshot digest 才更新图表与悬浮窗。
3. 账户、异常、历史修订仍可继续走旧的 `syncEvent` 链，但不能阻塞或筛掉市场实时主显示链。

## 这次为什么要单独拆市场直推链

当前旧链的问题不只是“拍点不够快”，而是**市场实时数据被放进了不适合它的发布机制里**：

- 服务端当前会先构建整份 `runtime snapshot`
- 再计算 `marketDigest`
- 再和上一份 `marketRevision` 比较
- 再决定这次 `changes.market` 要不要出现
- 最后 APP 端再通过 `syncEvent -> changes.market -> applyMarketSnapshotFromStream(...)` 落真值

这条机制适合账户、异常、历史修订这种“状态变化通知”，但不适合高频实时行情。

实时行情更适合：

- 独立运行时
- 独立节拍发布
- 直接下发完整 `market.snapshot`
- APP 直接消费，不再等统一筛选链批准

## 设计原则

### 原则 1：市场实时链独立于统一 bus

- `account / abnormal / historyRevision` 可以继续走旧 `syncEvent`
- `market realtime` 单独走 `marketTick`
- `marketTick` 不再依赖 `marketRevision` 差异检测

### 原则 2：服务端只做技术性校验，不做“值不值得推”的业务筛选

服务端对市场实时链只保留必要判断：

- 上游消息是否合法
- symbol 是否支持
- runtime 当前 patch 是否还 fresh
- WS 是否断流

不再把下面这些当作市场主链门槛：

- 全量 snapshot digest 是否变化
- `changes.market` 是否需要出现
- 是否要等 account/abnormal 一起打包

### 原则 3：APP 收到市场直推消息就立即更新真值

APP 侧只保留最小技术判断：

- 消息类型是否合法
- `seq` 是否重复 / 倒序
- payload 是否可解析

通过后立即：

- `MonitorRepository.applyMarketRuntimeSnapshot(...)`
- `MarketTruthCenter`
- 图表 1m 尾巴
- 悬浮窗当前分钟快照

### 原则 4：历史补链继续存在，但完全从实时链解耦

- 补链仍负责闭合 `1m` 缺口修复
- 不负责“当前分钟是否继续实时更新”
- 不得阻塞市场实时消息应用

## 新的数据流

### 服务端目标流

`Binance combined stream(kline_1m) -> v2_market_runtime -> market_realtime_publish_state(dirty/latestSeq/latestPublishedAt) -> market_realtime_publisher(500ms fixed batch) -> /v2/stream marketTick -> APP`

### APP 目标流

`GatewayV2StreamClient.parseMessage() -> MonitorService.handleV2StreamMessage() -> handleMarketTickMessage() -> MonitorRepository.applyMarketRuntimeSnapshot() -> MarketTruthCenter -> LiveData -> 1m 图表 / 悬浮窗 / 周期投影`

### 保留的旧流

`account / abnormal / historyRevision -> syncEvent / heartbeat`

## 协议调整

本次保留现有 `/v2/stream` websocket 连接，不另起第二条 socket，但把消息语义拆成两类：

### 1. `marketTick`

仅负责市场实时主显示流。

建议结构：

```json
{
  "type": "marketTick",
  "marketSeq": 123,
  "publishedAt": 1713916800123,
  "serverTime": 1713916800123,
  "market": {
    "source": "binance",
    "symbolStates": [
      {
        "marketSymbol": "BTCUSDT",
        "tradeSymbol": "BTCUSD",
        "latestPrice": 68050.5,
        "latestOpenTime": 1713916800000,
        "latestCloseTime": 1713916859999,
        "latestClosedCandle": { ... },
        "latestPatch": { ... }
      }
    ],
    "marketRuntimeConnected": true,
    "marketRuntimeUpdatedAt": 1713916800001,
    "marketRuntimeLastEventTime": 1713916800001
  }
}
```

规则：

- 每次直接带完整 `market.snapshot`
- 不再带 `revisions` / `changes`
- 不和账户、异常一起打包

### 2. `syncEvent` / `heartbeat`

继续负责：

- account runtime
- account history revision
- abnormal delta

市场实时显示不再依赖这类消息。

## 服务端实施任务

## Task S1：新增独立的市场实时发布状态

**Files**

- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `scripts/server_v2.py`

**目标**

新增一份只服务市场直推链的状态，不再复用 `account_publish_state`。

**新增状态建议**

```python
market_realtime_publish_state = {
    "seq": 0,
    "publishedAt": 0,
    "latestEventAt": 0,
    "dirty": False,
    "latestSnapshot": None,
}
```

**实现规则**

1. Binance WS 每成功应用一条 `kline_1m` 事件，就把：
   - `dirty = True`
   - `latestEventAt = now`
2. 这份状态只为 `marketTick` 服务，不写入 `account_publish_state`
3. stale 或断流时，允许发布一条“撤销当前 patch”的 `marketTick`

## Task S2：新增独立的市场实时发布器

**Files**

- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `scripts/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`

**目标**

实现固定 `500ms` 节拍的市场发布循环。

**实现规则**

1. 新增独立线程，例如：
   - `_market_realtime_publisher_loop()`
2. 固定每 `V2_STREAM_PUSH_INTERVAL_MS` 醒一次
3. 只要这一拍期间 `dirty == True`，就：
   - 从 `market_stream_runtime` 构建当前完整 `market.snapshot`
   - `marketSeq += 1`
   - 保存为 `latestSnapshot`
   - 清掉 `dirty`
4. 不再因为新的市场事件持续到来而把本拍发布时间往后推
5. 没有新市场事件时，不重复发布相同快照

**验收标准**

1. 高频连续 wake 下，仍然稳定每 `0.5s` 产生一次新 `marketTick`
2. 不是只在分钟切换时才有新 `marketSeq`

## Task S3：让 `/v2/stream` 支持 `marketTick` 优先发送

**Files**

- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `scripts/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
- Test: `bridge/mt5_gateway/tests/test_v2_contracts.py`

**目标**

在不新增第二条 websocket 的前提下，让同一连接优先发送市场直推消息。

**实现规则**

1. 当前 `/v2/stream` 订阅者队列要能感知两类事件：
   - `marketTick`
   - `syncEvent/heartbeat`
2. 如果有新的 `marketTick`，客户端发送循环必须优先发送它
3. `syncEvent` 仍继续保留
4. `heartbeat` 只做保活，不再承担市场实时更新职责

**建议做法**

- 扩展订阅消息队列，不只传 `busSeq`，改成传轻量事件对象：
  - `{"kind": "marketTick", "seq": 123}`
  - `{"kind": "syncEvent", "seq": 77}`
- `/v2/stream` 根据 kind 构建对应消息

## Task S4：新增 `marketTick` 构造器

**Files**

- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `scripts/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_contracts.py`

**目标**

统一构造市场实时消息，不再走 `_build_v2_bus_changes(...)`。

**建议函数**

```python
def _build_market_tick_message(snapshot: Dict[str, Any], seq: int, published_at: int) -> Dict[str, Any]:
    ...
```

**规则**

1. 直接输出 `type = marketTick`
2. 直接带完整 `market.snapshot`
3. 不带 `revisions`
4. 不带 `changes`

## Task S5：保留旧 `syncEvent`，但删掉市场实时显示对它的依赖

**Files**

- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `scripts/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`

**目标**

避免旧 `syncEvent` 继续成为市场显示主链。

**规则**

1. `syncEvent` 里可以继续携带市场全量 snapshot 作为兼容期保留
2. 但 APP 不再依赖 `changes.market`
3. 后续如果兼容期结束，可以进一步把 `changes.market` 从旧链去掉

## APP 实施任务

## Task A1：扩展 stream 消息解析，识别 `marketTick`

**Files**

- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java`
- Test: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClientTest.java` 或对应现有测试文件

**目标**

APP 能识别新的 `marketTick` 消息类型。

**实现规则**

1. `parseMessage(...)` 遇到 `type = marketTick` 时：
   - 读取 `marketSeq`
   - 读取 `market`
   - 包装成新的 `StreamMessage`
2. 保持对旧 `syncEvent` / `heartbeat` 兼容

## Task A2：`MonitorService` 拆出 `handleMarketTickMessage()`

**Files**

- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceRealtimeIngressTest.java`

**目标**

市场实时消息不再走“先看 `changes.market` 再决定要不要刷新”的旧链。

**实现规则**

1. `handleV2StreamMessage(...)` 分支处理：
   - `marketTick` -> `handleMarketTickMessage(...)`
   - `syncEvent` -> 继续旧逻辑
   - `heartbeat` -> 继续旧逻辑
2. `handleMarketTickMessage(...)` 直接：
   - 解析 `market.snapshot`
   - `applyMarketSnapshotFromStream(...)`
   - `floatingCoordinator.requestRefresh(false)`
3. `V2StreamRefreshPlanner` 不再承担市场实时主链决策，只保留旧 `syncEvent` 兼容逻辑

## Task A3：APP 为市场直推链建立独立顺序守卫

**Files**

- Modify: `app/src/main/java/com/binance/monitor/service/stream/V2StreamSequenceGuard.java`
- 或新增独立 `MarketTickSequenceGuard`
- Test: 对应服务层测试

**目标**

`marketTick` 不和 `busSeq` 混用同一顺序口径。

**实现规则**

1. `marketTick` 用 `marketSeq`
2. `syncEvent` 继续用 `busSeq`
3. 两者互不覆盖各自的 last applied seq

## Task A4：图表和悬浮窗继续直接读统一真值，不新增二次判断

**Files**

- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Test: 现有相关测试

**目标**

市场直推到 APP 后，主显示链不再被中间层二次筛选。

**规则**

1. `applyMarketRuntimeSnapshot(...)` 保持现有直写真值中心逻辑
2. 1m 图表继续读 `draftMinute`
3. 悬浮窗继续读 `CurrentMinuteSnapshot`

## 测试计划

## 服务端测试

### 定向测试

1. 高频连续市场事件下，`marketTick` 按 `500ms` 稳定发布
2. 高频连续市场事件下，不能再次退化为“等静默窗口”
3. `marketTick` payload 直接包含完整 `market.snapshot`
4. stale 后会发布撤销当前 patch 的市场快照

### 回归测试

```bash
python -m unittest bridge.mt5_gateway.tests.test_v2_market_runtime bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_v2_contracts bridge.mt5_gateway.tests.test_v2_account_pipeline bridge.mt5_gateway.tests.test_gateway_bundle_parity scripts.tests.test_windows_server_bundle scripts.tests.test_check_windows_server_bundle -v
python -m py_compile bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/v2_market_runtime.py scripts/server_v2.py
```

## APP 测试

### 定向测试

1. `marketTick` 能正确解析
2. `handleMarketTickMessage(...)` 直接推进真值
3. 高频 `marketTick` 下，图表 1m 同一分钟内多次推进
4. 悬浮窗与 1m 最新价保持同源

### 建议命令

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceRealtimeIngressTest"
./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest"
./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.floating.FloatingPositionAggregatorTest"
./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperTest"
./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest"
./gradlew.bat :app:assembleDebug
```

## 真机验收

1. 打开 `1m` 页面，连续观察至少 2 分钟，不切周期
   - 同一分钟内应出现多次连续变化
   - 不再只是每分钟开始跳一下
2. 同时观察悬浮窗
   - 最新价必须持续跟随 1m
3. 切换 `5m / 15m / 1h`
   - 最新价与 1m 同源
4. 人为触发补修或等待 watchdog
   - 补修期间实时链仍继续更新

## 执行顺序

1. 先做服务端 Task S1 ~ S4，完成市场直推链和协议
2. 再做 APP Task A1 ~ A3，完成 `marketTick` 消费
3. 再做 APP Task A4，确认图表与悬浮窗不再受旧筛选链影响
4. 然后跑服务端回归
5. 再跑 APP 定向测试并编译 APK
6. 最后重建服务端部署包并做真机联调

## 风险边界

1. 兼容期内 `/v2/stream` 会同时存在 `marketTick` 和 `syncEvent`，必须避免 APP 双重应用同一份市场数据。
2. `marketTick` 只解决市场实时主显示，不改账户和异常链，不要把范围再次扩大。
3. 如果线上服务器没有切到最新部署包，本地修复不会体现在手机上。
