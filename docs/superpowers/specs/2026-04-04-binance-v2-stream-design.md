## 目的
确保监控服务不再以 Binance `@kline` WebSocket 作为主链，把主实时链路收敛到 `/v2/stream`。新链路要把 `syncDelta` 消息按 `marketDelta/accountDelta` 提供的更新分派给现有的 `MonitorRepository`、闪电窗口/告警逻辑和历史回退链路，旧的 Binance 订阅最多只能作为问题时的兜底。

## 可选方案
1. **原地改造**：在 `MonitorService` 里继续直接消费 Binance WebSocket，只有在判断失败（例如 `socketStates` 恒为 false）时才再监听 `/v2/stream` 并触发 REST。缺点是主链依旧是旧 WebSocket，无法满足“主链必须是 v2 stream”的要求。
2. **事件抛送+解析**（推荐）：新增 `GatewayV2StreamClient`，直接连接 `/v2/stream`，解析 `type/payload`，再从 `marketDelta` 里提取 symbol + candle/price，继续复用 `KlineData`、`maybePublishPrice`、`handleClosedKline` 等逻辑。这个方案既把主链切到 v2 stream，又保留原有数据 consumer，且有边界清晰的解析层。
3. **流量触发 REST**：把 v2 stream 视为“节拍+标记”，每次收到消息就批量调用 `GatewayV2Client` 的 HTTP 接口刷新 `1m` 价格/最终 K 线。优点是可以暂时不依赖流里具体结构；缺点是延迟较高、测试依赖网络且无法回答“主链必须由流直接提供”的要求。

## 方案详述（2 的推荐实现）
- **`GatewayV2StreamClient`**：在 `data/remote/v2/` 新建一个 WebSocket 客户端，内部用 OkHttp 管理连接、心跳、重连（同以前的 `WebSocketManager` 逻辑），负责向 `/v2/stream` 发起请求，暴露 `Listener`（连接状态 + `GatewayV2StreamMessage` 回调）。
- **`GatewayV2StreamMessageParser`**：把原始 JSON 解析成 `type/payload/serverTime/syncToken`，其中 payload 再用已有的 `SyncDeltaPayload.parseSyncDelta(...)` 解析成 `JSONArray marketDelta/accountDelta`。解析失败时记录日志不抛异常。
- **`MonitorService` 改造**：
  - 不再直接使用 Binance WebSocket，改为持有 `GatewayV2StreamClient`，在 `startPipelineIfNeeded` 里启动并监听连接状态（成功 -> 全部 symbol 标记为已连通）。
  - `onStreamMessage` 先解析 `SyncDeltaPayload`，遍历 `marketDelta`：一方面尝试把每条 `JSONObject` 里的 `symbol` + `candle/kline`/`latestPatch` 还原成 `KlineData`，调用原有的 `repository.updateClosedKline`/`maybePublishPrice`/`handleClosedKline`；另一方面提取 `price`/`closePrice` 等字段供实时价格展示。
  - `accountDelta` 当前先记录简要日志，后期需要再补针对账户快照的打法。
  - 维护 `socketStates`/`reconnectCounts`：只要 v2 stream 连接成功，就把所有监控 symbol 的 `socketStates` 设为 `true` 与 `reconnectCounts` 归零，这样既不改 `ConnectionStatusResolver`，又让状态文案持续“已连接”+“partial”+“重连中”。
  - `refreshStaleSymbolsWithRest` 保留：如果 `lastKlineTickAt` 超时（`SOCKET_STALE_TIMEOUT_MS`），就照旧回退到 REST，并在 `updateConnectionStatus` 时反映出来。
- **数据/状态流**：
  1. `GatewayV2StreamClient` 连接成功后，`Listener#onStreamStateChanged` 在主线程更新 `socketStates`/`reconnectCounts` 并重置 `repository` 连接文案。
  2. `Listener#onStreamMessage` 把 `GatewayV2StreamMessage.payload` 解析成 `SyncDeltaPayload`。
  3. 对 `marketDelta` 每条 entry：判断是否包含 `symbol` + 1m candle（字段名 `candle`/`kline`/`k` 等），封装为 `KlineData.fromSocket`；同时如果 entry 只有价格字段就当做 ticker 更新。更新成功后刷新 `lastKlineTickAt`、`lastPricePublishAt`、`floating`、通知 `floatingWindowManager`。
  4. `checkStreamFreshness` 仍定期执行，发现 stale 会触发 REST 回退与 `updateConnectionStatus`。

## 测试清单（按 TDD）
1. `GatewayV2StreamMessageParserTest`：用典型 `syncBootstrap`/`syncDelta` JSON 验证 parser 提取 `type`/`serverTime`/`syncToken` 和 `SyncDeltaPayload` 成功，payload 包含 `marketDelta`/`accountDelta` 时不崩溃。
2. `MonitorServiceV2SourceTest`：确认 `MonitorService` 已引用 `GatewayV2StreamClient` 并调用 `.connect(...)`，使 CI 无法通过旧实现。
3. `MonitorService` 单测（可复用现有测试基础）模拟 `GatewayV2StreamMessage` 执行 `handleStreamDelta`，确保 `repository.updateClosedKline`/`handleClosedKline` 被调用；如果路径尚未准备好，可安排 matching test 以便 TDD。
4. `GatewayV2StreamClient`（可选）验证公开 API（例如对 `Listener` 的回调顺序）是否在断线/重连时稳定（若难以模拟可用集成测试或手工验证）。

## 未决问题
- 还需要官方给出 `marketDelta`/`accountDelta` 的字段样例（例如 `symbol`、`candle`、`latestPrice` 等），才能定位应该靠流直接更新哪些数据，什么时候才需要退回 REST。当前先按上述推测实现，若与真实 payload 不符再调整。
- 是否要把 `payload.fullRefresh.snapshot.market` 也用于初始化（`bootstrap`）时直接写入 `MonitorRepository`？目前打算只用 `marketDelta` 里的具体 K 线数据，不强制写整个 snapshot。

完成这个方案后，主控链路就由 `/v2/stream` 提供，老 Binance `@kline` WebSocket 最多作为 REST 回退，在必要时再动。请先确认上述假设是否符合后端的 delta 结构，然后我再据此详细制定实现计划。
