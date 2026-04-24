# Market Trade Stream Runtime Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把市场实时主链从当前在线上无业务帧的 `kline_1m` 订阅切换成稳定可用的 `trade` 订阅，并让服务端统一本地重建 `1m` 当前分钟 K 线，最终让 APP 的 `1m`、悬浮窗、`5m~1d` 都跟随同一份连续真值实时更新。

**Architecture:** 服务端改成“`Binance trade WS -> 本地 1m 聚合 runtime -> 固定 500ms marketTick`”单主链，禁止在 `marketTick` 发布热路径里再走 REST。APP 侧保持“收到 `marketTick` 即更新真值中心”的原则不变，只补掉图表实时渲染 token 过窄导致的同分钟不重绘残留问题。

**Tech Stack:** Python/FastAPI/websockets/现有 `bridge.mt5_gateway` runtime 模块；Android Java/LiveData/现有 `MonitorRepository + MarketTruthCenter` 真值链；unittest/Gradle unit tests。

---

## 文件结构与职责

**服务端核心文件**

- Modify: `bridge/mt5_gateway/server_v2.py`
  当前市场上游订阅、runtime 写入、`marketTick` 发布、`/v2/market/*` 接口都在这里；本次需要把上游从 `kline_1m` 切到 `trade`，并移除 `marketTick` 热路径里的 REST 依赖。
- Modify: `scripts/server_v2.py`
  必须与 `bridge/mt5_gateway/server_v2.py` 保持完全一致。
- Modify: `bridge/mt5_gateway/v2_market_runtime.py`
  当前 runtime 只接受 `kline` 事件；本次需要新增“按 trade 本地聚合 1m 草稿/闭合分钟”的正式算法。
- Modify: `bridge/mt5_gateway/v2_market.py`
  若现有 candle 构造函数不足以支持 trade 聚合后的 canonical candle，需要补正式的 payload 构造入口，而不是在 `server_v2.py` 手拼。

**服务端测试文件**

- Modify: `bridge/mt5_gateway/tests/test_v2_market_runtime.py`
  锁定 trade 聚合为 1m 草稿、跨分钟封闭、长周期 patch 投影的正确性。
- Modify: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
  锁定 `trade` 事件进入后 `dirtyVersion`、`marketTick`、`/v2/stream` 行为。
- Modify: `bridge/mt5_gateway/tests/test_v2_contracts.py`
  锁定 `/v2/market/snapshot`、`/v2/market/candles` 的 contract，不允许再把 REST 当前分钟伪装成 live patch。
- Modify: `bridge/mt5_gateway/tests/test_gateway_bundle_parity.py`
  锁定主脚本与部署脚本的同步。

**APP 核心文件**

- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  当前实时尾巴渲染 token 只带 `closePrice`；本次要改成带完整同分钟变化字段，防止价格不变时不重绘。
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
  只做小范围复核，如需补 trace/log 或修正 `marketTick` 应用细节，收口在这里；不改变“收到即更新真值”的原则。
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
  只做源码约束复核，确认悬浮窗仍只读统一 `CurrentMinuteSnapshot`。

**APP 测试文件**

- Modify: `app/src/test/java/com/binance/monitor/service/MonitorServiceRealtimeIngressTest.java`
  锁定 `marketTick` 通过后仍然直达真值中心。
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`
  锁定 `marketTick` 只触发图表刷新区域，不影响既有预算策略。
- Create: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailRenderTokenTest.java`
  锁定同一分钟内 `high/low/volume/quoteVolume` 变化也必须触发实时尾巴重绘。
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java`
  锁定悬浮窗依然只读 `selectCurrentMinuteSnapshot(symbol)`。

**文档与记录**

- Modify: `CONTEXT.md`
  每阶段完成后更新极简进度。
- Modify: `README.md`
  仅在最终实现完成、主行情架构发生正式变化时更新。
- Modify: `ARCHITECTURE.md`
  仅在最终实现完成、模块职责发生正式变化时更新。

---

### Task 1: 服务端把上游市场主链改成 `trade` 订阅

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `scripts/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`

- [ ] **Step 1: 写失败测试，锁定服务端不再订阅 `@kline_1m`**

```python
def test_build_market_stream_upstream_url_should_subscribe_trade_streams(self):
    url = server_v2._build_market_stream_upstream_url()
    self.assertIn("btcusdt@trade", url)
    self.assertIn("xauusdt@trade", url)
    self.assertNotIn("@kline_1m", url)
```

- [ ] **Step 2: 运行测试，确认当前实现失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline.V2SyncPipelineTests.test_build_market_stream_upstream_url_should_subscribe_trade_streams -v`

Expected: FAIL，当前 URL 仍包含 `@kline_1m`

- [ ] **Step 3: 最小实现，把订阅入口切到 `trade`**

```python
def _build_market_stream_upstream_url() -> str:
    streams = "/".join(symbol.lower() + "@trade" for symbol in ABNORMAL_SYMBOLS)
    return _build_binance_ws_upstream_url("/stream", f"streams={streams}")
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline.V2SyncPipelineTests.test_build_market_stream_upstream_url_should_subscribe_trade_streams -v`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add bridge/mt5_gateway/server_v2.py scripts/server_v2.py bridge/mt5_gateway/tests/test_v2_sync_pipeline.py
git commit -m "feat: switch market upstream subscription to trade streams"
```

### Task 2: 服务端在 runtime 内用 `trade` 本地重建当前 `1m`

**Files:**
- Modify: `bridge/mt5_gateway/v2_market_runtime.py`
- Modify: `bridge/mt5_gateway/v2_market.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_market_runtime.py`

- [ ] **Step 1: 写失败测试，锁定同一分钟内 trade 能持续推进 `latestPatch`**

```python
def test_apply_ws_trade_event_should_update_same_minute_patch(self):
    runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])
    runtime_module.apply_ws_trade_event(runtime, {
        "data": {"e": "trade", "E": 1713952800100, "s": "BTCUSDT", "p": "65000.0", "q": "1.2"}
    })
    runtime_module.apply_ws_trade_event(runtime, {
        "data": {"e": "trade", "E": 1713952800400, "s": "BTCUSDT", "p": "65010.0", "q": "0.8"}
    })
    state = runtime_module.build_symbol_state(runtime, "BTCUSDT")
    self.assertIsNotNone(state["latestPatch"])
    self.assertEqual(65010.0, state["latestPatch"]["close"])
    self.assertEqual(2.0, state["latestPatch"]["volume"])
    self.assertEqual(1713952800400, state["lastEventTime"])
```

- [ ] **Step 2: 再写失败测试，锁定跨分钟时旧草稿必须封成闭合分钟**

```python
def test_apply_ws_trade_event_should_close_previous_minute_when_bucket_advances(self):
    runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])
    runtime_module.apply_ws_trade_event(runtime, {
        "data": {"e": "trade", "E": 1713952859000, "s": "BTCUSDT", "p": "65000.0", "q": "1.0"}
    })
    runtime_module.apply_ws_trade_event(runtime, {
        "data": {"e": "trade", "E": 1713952861000, "s": "BTCUSDT", "p": "65020.0", "q": "0.5"}
    })
    state = runtime_module.build_symbol_state(runtime, "BTCUSDT")
    self.assertIsNotNone(state["latestClosedCandle"])
    self.assertEqual(1713952800000, state["latestClosedCandle"]["openTime"])
    self.assertEqual(1713952860000, state["latestPatch"]["openTime"])
```

- [ ] **Step 3: 运行测试，确认当前实现失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_market_runtime -v`

Expected: FAIL，当前 runtime 还没有 `apply_ws_trade_event`

- [ ] **Step 4: 最小实现 trade -> 1m 聚合器**

```python
def apply_ws_trade_event(runtime: Dict[str, Any], payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    event = dict((payload or {}).get("data") or payload or {})
    symbol = str(event.get("s") or "").strip().upper()
    event_time_ms = max(0, int(event.get("E", 0) or 0))
    price = float(event.get("p", 0.0) or 0.0)
    quantity = float(event.get("q", 0.0) or 0.0)
    if not symbol or event_time_ms <= 0 or price <= 0:
        return None
    bucket_open = (event_time_ms // ONE_MINUTE_MS) * ONE_MINUTE_MS
    bucket_close = bucket_open + ONE_MINUTE_MS - 1
    with runtime["lock"]:
        bucket = runtime["symbols"][v2_market._symbol_descriptor(symbol)["marketSymbol"]]
        current_patch = _clone_candle(bucket.get("latestPatch"))
        if current_patch is not None and int(current_patch.get("openTime", 0) or 0) < bucket_open:
            _remember_closed_minute(bucket["recentClosedMinutes"], dict(current_patch, isClosed=True))
            bucket["latestClosedCandle"] = dict(current_patch, isClosed=True)
            current_patch = None
        next_patch = _merge_trade_into_patch(current_patch, symbol, bucket_open, bucket_close, price, quantity)
        bucket["latestPatch"] = next_patch
        bucket["updatedAt"] = event_time_ms
        bucket["lastEventTime"] = event_time_ms
        runtime["updatedAt"] = event_time_ms
        runtime["lastEventTime"] = event_time_ms
        return _clone_candle(next_patch)
```

- [ ] **Step 5: 修改服务端消息消费入口，让市场 WS 按 trade 事件推进 runtime**

```python
def _consume_market_stream_runtime_message(raw_message: Any) -> None:
    text = raw_message.decode("utf-8") if isinstance(raw_message, (bytes, bytearray)) else str(raw_message or "")
    if not text:
        return
    payload = json.loads(text)
    event = dict((payload or {}).get("data") or payload or {})
    if str(event.get("e", "") or "") != "trade":
        return
    applied = v2_market_runtime.apply_ws_trade_event(market_stream_runtime, payload)
    if applied is not None:
        _request_v2_market_publish("market_stream_trade")
```

- [ ] **Step 6: 运行测试，确认通过**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_market_runtime bridge.mt5_gateway.tests.test_v2_sync_pipeline -v`

Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add bridge/mt5_gateway/v2_market_runtime.py bridge/mt5_gateway/v2_market.py bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_market_runtime.py bridge/mt5_gateway/tests/test_v2_sync_pipeline.py
git commit -m "feat: derive 1m market runtime from binance trade stream"
```

### Task 3: 服务端禁止在 `marketTick` 热路径中用 REST 冒充实时 patch

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `scripts/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_contracts.py`
- Test: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`

- [ ] **Step 1: 写失败测试，锁定 `/v2/market/snapshot` 在 runtime 无 patch 时不能回填 REST 当前分钟**

```python
def test_market_stream_symbol_state_should_not_build_rest_realtime_patch_when_runtime_missing(self):
    with patch.object(server_v2, "_build_market_runtime_symbol_state", return_value={
        "marketSymbol": "BTCUSDT",
        "latestPrice": 0.0,
        "latestClosedCandle": None,
        "latestPatch": None,
        "updatedAt": 0,
        "lastEventTime": 0,
    }), patch.object(server_v2, "_fetch_market_candle_rows_with_cache", side_effect=AssertionError("hot path should not hit rest")):
        state = server_v2._build_market_stream_symbol_state("BTCUSDT", 1713952805000)
    self.assertIsNone(state["latestPatch"])
```

- [ ] **Step 2: 写失败测试，锁定 `/v2/market/candles?interval=1m` 只认 runtime patch**

```python
def test_market_candles_should_not_return_rest_current_minute_as_live_patch(self):
    with patch.object(server_v2, "_build_market_runtime_symbol_state", return_value={
        "marketSymbol": "BTCUSDT",
        "latestPatch": None,
        "latestClosedCandle": None,
        "updatedAt": 0,
        "lastEventTime": 0,
    }):
        response = client.get("/v2/market/candles", params={"symbol": "BTCUSDT", "interval": "1m", "limit": 2})
    payload = response.json()
    self.assertIsNone(payload["latestPatch"])
```

- [ ] **Step 3: 运行测试，确认当前实现失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_contracts -v`

Expected: FAIL，当前热路径仍会打 REST

- [ ] **Step 4: 最小实现，去掉 `marketTick` 快照构建中的 REST fallback**

```python
def _build_market_stream_symbol_state(symbol: str, server_time: int) -> Dict[str, Any]:
    runtime_state = _build_market_runtime_symbol_state(symbol)
    sanitized_runtime_state = _sanitize_runtime_symbol_state(runtime_state, server_time)
    descriptor = _resolve_symbol_descriptor(symbol)
    return {
        "productId": descriptor["productId"],
        "marketSymbol": descriptor["marketSymbol"],
        "tradeSymbol": descriptor["tradeSymbol"],
        "interval": "1m",
        "latestPrice": float(sanitized_runtime_state.get("latestPrice", 0.0) or 0.0),
        "latestOpenTime": int(sanitized_runtime_state.get("latestOpenTime", 0) or 0),
        "latestCloseTime": int(sanitized_runtime_state.get("latestCloseTime", 0) or 0),
        "latestClosedCandle": _clone_json_value(sanitized_runtime_state.get("latestClosedCandle")),
        "latestPatch": _clone_json_value(sanitized_runtime_state.get("latestPatch")),
        "updatedAt": int(sanitized_runtime_state.get("updatedAt", 0) or 0),
        "lastEventTime": int(sanitized_runtime_state.get("lastEventTime", 0) or 0),
    }
```

- [ ] **Step 5: 保留 REST 只给冷启动和历史查询，不再进入 `marketTick` 发布热路径**

```python
def _publish_market_realtime_current_state() -> None:
    with snapshot_cache_lock:
        dirty = bool(market_realtime_publish_state.get("dirty", False))
        dirty_version = int(market_realtime_publish_state.get("dirtyVersion", 0) or 0)
        published_version = int(market_realtime_publish_state.get("publishedVersion", 0) or 0)
    if not dirty or dirty_version <= published_version:
        return
    now_ms = _now_ms()
    snapshot = _build_v2_market_section(now_ms)
    _publish_market_tick(snapshot=snapshot, published_at=now_ms, published_version=dirty_version)
```

- [ ] **Step 6: 运行测试，确认通过**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_contracts bridge.mt5_gateway.tests.test_v2_sync_pipeline -v`

Expected: PASS

- [ ] **Step 7: 提交**

```bash
git add bridge/mt5_gateway/server_v2.py scripts/server_v2.py bridge/mt5_gateway/tests/test_v2_contracts.py bridge/mt5_gateway/tests/test_v2_sync_pipeline.py
git commit -m "fix: remove rest fallback from market realtime publish path"
```

### Task 4: 服务端把 `5m~1d` 最新未闭合周期统一改成基于本地 `1m` 真值聚合

**Files:**
- Modify: `bridge/mt5_gateway/v2_market_runtime.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_market_runtime.py`
- Test: `bridge/mt5_gateway/tests/test_v2_contracts.py`

- [ ] **Step 1: 写失败测试，锁定 `5m` patch 必须来自当前分钟草稿和闭合 1m**

```python
def test_build_interval_patch_should_use_trade_derived_minute_truth(self):
    runtime = runtime_module.create_market_stream_runtime(["BTCUSDT"])
    for payload in build_trade_sequence_covering_five_minutes():
        runtime_module.apply_ws_trade_event(runtime, payload)
    patch = runtime_module.build_interval_patch(runtime, "BTCUSDT", "5m")
    self.assertIsNotNone(patch)
    self.assertEqual("binance-ws", patch["source"])
    self.assertFalse(patch["isClosed"])
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_market_runtime.V2MarketRuntimeTests.test_build_interval_patch_should_use_trade_derived_minute_truth -v`

Expected: FAIL 或断言不满足

- [ ] **Step 3: 最小实现，保证长周期 patch 只从当前 `1m` 真值聚合**

```python
def build_interval_patch(runtime: Dict[str, Any], symbol: str, interval: str) -> Optional[Dict[str, Any]]:
    safe_interval = str(interval or "").strip()
    interval_ms = _resolve_fixed_interval_ms(safe_interval)
    if interval_ms <= ONE_MINUTE_MS:
        return None
    snapshot = build_symbol_state(runtime, symbol)
    latest_patch = snapshot.get("latestPatch")
    if latest_patch is None:
        return None
    minute_inputs = _collect_interval_minute_inputs(runtime, symbol, safe_interval, latest_patch)
    return _aggregate_interval_patch(symbol, safe_interval, minute_inputs)
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_market_runtime bridge.mt5_gateway.tests.test_v2_contracts -v`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add bridge/mt5_gateway/v2_market_runtime.py bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_market_runtime.py bridge/mt5_gateway/tests/test_v2_contracts.py
git commit -m "feat: derive higher interval patches from local 1m truth"
```

### Task 5: APP 侧补掉“价格不变时实时尾巴不重绘”的残留干扰

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailRenderTokenTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java`

- [ ] **Step 1: 写失败测试，锁定同一分钟 volume 变化也必须改变 render token**

```java
@Test
public void realtimeTailRenderTokenShouldChangeWhenVolumeChangesWithinSameMinute() throws Exception {
    String source = readSource("app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java");
    assertTrue(source.contains("latestKline.getHighPrice()"));
    assertTrue(source.contains("latestKline.getLowPrice()"));
    assertTrue(source.contains("latestKline.getVolume()"));
    assertTrue(source.contains("latestKline.getQuoteVolume()"));
}
```

- [ ] **Step 2: 运行测试，确认当前实现失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRealtimeTailRenderTokenTest"`

Expected: FAIL，当前 token 只包含 `closePrice`

- [ ] **Step 3: 最小实现，扩大实时尾巴渲染 token**

```java
private String buildRealtimeTailRenderToken(@NonNull KlineData latestKline) {
    return latestKline.getSymbol()
            + '|'
            + latestKline.getOpenTime()
            + '|'
            + latestKline.getCloseTime()
            + '|'
            + latestKline.getClosePrice()
            + '|'
            + latestKline.getHighPrice()
            + '|'
            + latestKline.getLowPrice()
            + '|'
            + latestKline.getVolume()
            + '|'
            + latestKline.getQuoteVolume();
}
```

- [ ] **Step 4: 运行测试，确认通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRealtimeTailRenderTokenTest" --tests "com.binance.monitor.ui.chart.MarketChartZoneRefreshTest"`

Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartRealtimeTailRenderTokenTest.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartZoneRefreshTest.java
git commit -m "fix: refresh chart tail when intraminute candle fields change"
```

### Task 6: APP 侧复核 `marketTick -> 真值中心 -> 悬浮窗/图表` 主链不被回归破坏

**Files:**
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorServiceRealtimeIngressTest.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java`

- [ ] **Step 1: 补失败测试，锁定 `marketTick` 仍然直接进入真值中心**

```java
@Test
public void marketTickShouldStillApplySnapshotDirectly() throws Exception {
    String source = readSource("app/src/main/java/com/binance/monitor/service/MonitorService.java");
    assertTrue(source.contains("handleMarketTickMessage(message);"));
    assertTrue(source.contains("applyMarketSnapshotFromStream(message.getMarketSnapshot());"));
}
```

- [ ] **Step 2: 补失败测试，锁定悬浮窗仍只读 `CurrentMinuteSnapshot`**

```java
@Test
public void floatingCoordinatorShouldReadCurrentMinuteSnapshotOnly() throws Exception {
    String source = readSource("app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java");
    assertTrue(source.contains("repository.selectCurrentMinuteSnapshot(symbol)"));
    assertFalse(source.contains("selectClosedMinute(symbol)"));
    assertFalse(source.contains("selectLatestPrice(symbol)"));
}
```

- [ ] **Step 3: 运行测试，确认当前实现通过或按需补齐**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceRealtimeIngressTest" --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest"`

Expected: PASS；若 FAIL，只允许做与主链一致性直接相关的最小修正

- [ ] **Step 4: 提交**

```bash
git add app/src/test/java/com/binance/monitor/service/MonitorServiceRealtimeIngressTest.java app/src/test/java/com/binance/monitor/service/MonitorFloatingCoordinatorSourceTest.java app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java
git commit -m "test: lock direct market tick ingress and unified floating source"
```

### Task 7: 部署包、APK 与联调验收

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`（仅在实现完成后）
- Modify: `ARCHITECTURE.md`（仅在实现完成后）

- [ ] **Step 1: 跑完整服务端回归**

Run:

```bash
python -m unittest bridge.mt5_gateway.tests.test_v2_market_runtime bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_v2_contracts bridge.mt5_gateway.tests.test_gateway_bundle_parity scripts.tests.test_windows_server_bundle scripts.tests.test_check_windows_server_bundle -v
python -m py_compile bridge/mt5_gateway/server_v2.py scripts/server_v2.py bridge/mt5_gateway/v2_market_runtime.py
```

Expected: 全绿

- [ ] **Step 2: 跑 APP 定向回归**

Run:

```bash
./gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceRealtimeIngressTest" --tests "com.binance.monitor.service.MonitorFloatingCoordinatorSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartRealtimeTailRenderTokenTest" --tests "com.binance.monitor.ui.chart.MarketChartZoneRefreshTest"
./gradlew.bat :app:assembleDebug
```

Expected: 全绿并成功产出 APK

- [ ] **Step 3: 重建 Windows 服务端部署包**

Run:

```bash
python scripts/build_windows_server_bundle.py
python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle --skip-remote
```

Expected: 通过并生成新的 `bundleFingerprint`

- [ ] **Step 4: 安装最新 APK 到真机**

Run:

```bash
adb -s 7fab54c4 install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `Success`

- [ ] **Step 5: 线上人工验收**

Run / Check:

```bash
curl https://tradeapp.ltd/health
curl https://tradeapp.ltd/v2/market/snapshot
```

Expected:

```text
marketRuntimeLastEventTime 持续前进，不再是 0
symbolStates.latestPatch 对 BTCUSDT/XAUUSDT 均长期存在
/v2/stream 在同一分钟内连续收到不同 marketSeq 的 marketTick
marketTick 中 latestPatch.close / volume / quoteVolume 会在分钟内持续变化
APP 不切周期时，1m 图表持续变化
悬浮窗最新价与 1m 尾巴一致，不再在旧价和新价之间闪烁
5m~1d 最新一根与当前 1m 聚合结果一致
```

- [ ] **Step 6: 更新项目记录**

Run:

```bash
git add CONTEXT.md README.md ARCHITECTURE.md
git commit -m "docs: record trade-stream market runtime architecture"
```

---

## 自检

**Spec coverage**

- 服务端主实时链切换到单一稳定链：已覆盖 Task 1-4
- 禁止 REST 冒充当前分钟实时 patch：已覆盖 Task 3
- `5m~1d` 统一由本地 `1m` 真值重算：已覆盖 Task 4
- APP 收到即更新，不做业务判断：现有主链保留，已在 Task 6 锁定
- 悬浮窗、图表、不同周期同源：服务端同源 + APP 渲染修正，已覆盖 Task 4-6
- 防止“看起来修了其实没修”：已覆盖 Task 7 验收标准

**Placeholder scan**

- 无 `TBD/TODO`
- 每个任务都给了明确文件、测试、命令
- 没有“参考上一任务”式占位

**Type consistency**

- 服务端统一使用 `marketTick/latestPatch/latestClosedCandle/lastEventTime`
- APP 统一使用 `marketTick -> applyMarketSnapshotFromStream -> CurrentMinuteSnapshot`
- 新增服务端入口统一命名为 `apply_ws_trade_event`

---

Plan complete and saved to `docs/superpowers/plans/2026-04-24-market-trade-stream-runtime-fix-implementation.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
