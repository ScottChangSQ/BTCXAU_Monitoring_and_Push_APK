# V2 Single Producer Bus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把服务端和 APP 的市场、账户、异常三条主链统一收口到“服务端唯一生产、客户端纯消费、历史按 revision 只读拉取”的架构。

**Architecture:** 服务端新增统一发布总线，后台生产市场运行态、账户运行态、账户历史 revision、异常状态四类已发布状态；`/v2/stream` 和相关 HTTP 接口只消费已发布状态，不再由连接或请求触发构建。APP 端 `MonitorService`、账户预加载、异常页和图表页全部改成消费服务端已发布结果，删除本地异常生产和请求驱动同步链。

**Tech Stack:** Python/FastAPI/Uvicorn、Android Java、OkHttp、LiveData、unittest、JUnit

---

### Task 1: 锁定统一发布总线协议与失败测试

**Files:**
- Modify: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
- Modify: `bridge/mt5_gateway/tests/test_abnormal_gateway.py`
- Modify: `bridge/mt5_gateway/tests/test_summary_response.py`
- Modify: `bridge/mt5_gateway/server_v2.py`

- [ ] **Step 1: 先写服务端协议失败测试**

```python
def test_v2_stream_reads_published_bus_state_without_triggering_runtime_build(self):
    calls = {"runtime": 0}

    def fake_runtime_snapshot(server_time):
        calls["runtime"] += 1
        return {"digest": "runtime", "market": {}, "account": {}}

    server_v2._build_v2_sync_runtime_snapshot = fake_runtime_snapshot
    server_v2.v2_bus_state.clear()
    server_v2.v2_bus_state.update({
        "busSeq": 3,
        "publishedAt": 1000,
        "revisions": {
            "marketRevision": "m3",
            "accountRuntimeRevision": "a7",
            "accountHistoryRevision": "h9",
            "abnormalRevision": "ab2",
        },
        "event": {
            "type": "syncEvent",
            "changes": {"market": {"snapshot": {"symbolStates": []}}},
        },
    })

    payload = server_v2._build_v2_stream_event_for_client(last_bus_seq=2)

    self.assertEqual(payload["busSeq"], 3)
    self.assertEqual(calls["runtime"], 0)
```

- [ ] **Step 2: 运行服务端协议相关测试，确认先失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_abnormal_gateway bridge.mt5_gateway.tests.test_summary_response`

Expected: 失败，原因是 `v2_bus_state/_build_v2_stream_event_for_client` 等新结构尚不存在，且异常链仍是请求驱动重算。

- [ ] **Step 3: 在 `server_v2.py` 中引入统一发布状态骨架**

```python
v2_bus_state: Dict[str, Any] = {
    "busSeq": 0,
    "publishedAt": 0,
    "revisions": {
        "marketRevision": "",
        "accountRuntimeRevision": "",
        "accountHistoryRevision": "",
        "abnormalRevision": "",
    },
    "event": None,
}


def _publish_v2_bus_event(event_type: str,
                          changes: Dict[str, Any],
                          revisions: Dict[str, str],
                          published_at: int) -> Dict[str, Any]:
    with snapshot_cache_lock:
        bus_seq = int(v2_bus_state.get("busSeq", 0) or 0) + 1
        event = {
            "type": str(event_type or "syncEvent"),
            "busSeq": bus_seq,
            "publishedAt": int(published_at or 0),
            "revisions": dict(revisions or {}),
            "changes": _clone_json_value(changes or {}),
        }
        v2_bus_state.update({
            "busSeq": bus_seq,
            "publishedAt": int(published_at or 0),
            "revisions": dict(revisions or {}),
            "event": event,
        })
        return _clone_json_value(event)
```

- [ ] **Step 4: 重新运行服务端协议测试，确认骨架通过**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_abnormal_gateway bridge.mt5_gateway.tests.test_summary_response`

Expected: 至少新增协议骨架相关断言转绿，剩余失败聚焦到 stream 和 abnormal 仍未改为纯消费。

- [ ] **Step 5: 提交这一小步**

```bash
git add bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_sync_pipeline.py bridge/mt5_gateway/tests/test_abnormal_gateway.py bridge/mt5_gateway/tests/test_summary_response.py
git commit -m "feat: add v2 published bus state skeleton"
```

### Task 2: 服务端重构 v2 stream 为纯消费已发布状态

**Files:**
- Create: `bridge/mt5_gateway/v2_sync_bus.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_contracts.py`

- [ ] **Step 1: 先写 stream 纯消费测试**

```python
def test_v2_stream_heartbeat_does_not_rebuild_snapshot(self):
    calls = {"build": 0}

    def fake_build(sync_token, server_time):
        calls["build"] += 1
        return {}

    server_v2._build_v2_sync_delta_response = fake_build
    message = server_v2._build_v2_stream_event_for_client(last_bus_seq=5)

    self.assertEqual(message["type"], "heartbeat")
    self.assertEqual(calls["build"], 0)
```

- [ ] **Step 2: 运行 stream 相关测试，确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_v2_contracts`

Expected: 失败，当前 `v2_stream` 仍依赖 `_build_v2_sync_delta_response()`。

- [ ] **Step 3: 新增独立总线模块并把 WS 发送链改为只读 bus**

```python
def build_stream_message_for_client(last_bus_seq: int, now_ms: int) -> Dict[str, Any]:
    state = read_bus_state()
    current_seq = int(state.get("busSeq", 0) or 0)
    if current_seq <= int(last_bus_seq or 0):
        return {
            "type": "heartbeat",
            "busSeq": current_seq,
            "publishedAt": int(state.get("publishedAt", 0) or 0),
            "revisions": dict(state.get("revisions") or {}),
            "changes": {},
        }
    event = dict(state.get("event") or {})
    event["serverTime"] = int(now_ms or 0)
    return event
```

- [ ] **Step 4: 运行 stream 测试，确认 `heartbeat/syncEvent` 语义成立**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_v2_contracts`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add bridge/mt5_gateway/v2_sync_bus.py bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_sync_pipeline.py bridge/mt5_gateway/tests/test_v2_contracts.py
git commit -m "feat: make v2 stream consume published bus state"
```

### Task 3: 服务端重构 abnormal 为后台唯一生产

**Files:**
- Create: `bridge/mt5_gateway/v2_runtime_abnormal.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/tests/test_abnormal_gateway.py`

- [ ] **Step 1: 先写异常接口“只读已发布状态”的失败测试**

```python
def test_build_abnormal_response_reads_published_snapshot_without_refresh(self):
    calls = {"refresh": 0}

    def fake_refresh():
        calls["refresh"] += 1

    server_v2._refresh_abnormal_state = fake_refresh
    with server_v2.abnormal_state_lock:
        server_v2.abnormal_sync_state.clear()
        server_v2.abnormal_sync_state.update({
            "seq": 9,
            "snapshot": {
                "abnormalMeta": {"syncSeq": 9},
                "records": [{"id": "r1"}],
                "alerts": [],
                "configs": [],
            },
        })

    response = server_v2._build_abnormal_response(since_seq=0, delta=True)

    self.assertEqual(response["abnormalMeta"]["syncSeq"], 9)
    self.assertEqual(calls["refresh"], 0)
```

- [ ] **Step 2: 运行异常测试，确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_abnormal_gateway`

Expected: 失败，当前 `_build_abnormal_response()` 一进来就会调用 `_refresh_abnormal_state()`。

- [ ] **Step 3: 引入后台异常生产者并让 HTTP 接口只读**

```python
def _read_abnormal_snapshot() -> Dict[str, Any]:
    with abnormal_state_lock:
        if not abnormal_sync_state:
            _commit_abnormal_snapshot_locked()
        return _clone_json_value(abnormal_sync_state.get("snapshot") or _build_abnormal_snapshot_locked())


def _build_abnormal_response(since_seq: int, delta: bool) -> Dict[str, Any]:
    snapshot = _read_abnormal_snapshot()
    with abnormal_state_lock:
        sync_seq = int(abnormal_sync_state.get("seq", 1) or 1)
        previous_seq = int(abnormal_sync_state.get("previousSeq", 0) or 0)
        previous_snapshot = abnormal_sync_state.get("previousSnapshot")
```

- [ ] **Step 4: 运行异常测试，确认接口纯读取**

Run: `python -m unittest bridge.mt5_gateway.tests.test_abnormal_gateway`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add bridge/mt5_gateway/v2_runtime_abnormal.py bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_abnormal_gateway.py
git commit -m "feat: make abnormal sync state producer-driven"
```

### Task 4: APP 把 stream/watchdog 改成纯消费统一发布事件

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java`
- Modify: `app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/constants/AppConstants.java`
- Test: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClientTest.java`
- Test: `app/src/test/java/com/binance/monitor/service/V2StreamRefreshPlannerTest.java`
- Test: `app/src/test/java/com/binance/monitor/service/ConnectionStatusResolverTest.java`

- [ ] **Step 1: 先写 APP stream 新协议测试**

```java
@Test
public void parseMessage_shouldReadBusSeqAndHeartbeatType() throws Exception {
    String body = "{"
            + "\"type\":\"heartbeat\","
            + "\"busSeq\":12,"
            + "\"publishedAt\":1000,"
            + "\"revisions\":{\"marketRevision\":\"m1\"},"
            + "\"changes\":{}"
            + "}";

    GatewayV2StreamClient.StreamMessage message = GatewayV2StreamClient.parseMessage(body);

    assertEquals("heartbeat", message.getType());
    assertEquals(12L, message.getBusSeq());
}
```

- [ ] **Step 2: 运行 Android 定向测试，确认先失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2StreamClientTest" --tests "com.binance.monitor.service.V2StreamRefreshPlannerTest" --tests "com.binance.monitor.service.ConnectionStatusResolverTest"`

Expected: 失败，当前 `StreamMessage` 不含 `busSeq/revisions/changes` 语义。

- [ ] **Step 3: 改 APP stream 模型和消费规划**

```java
public static final class StreamMessage {
    private final String type;
    private final long busSeq;
    private final long publishedAt;
    private final JSONObject revisions;
    private final JSONObject changes;
}
```

```java
boolean shouldPullAccountHistory = historyRevisionAdvanced;
boolean shouldRefreshRuntimeAccount = hasAccountRuntimeSnapshot;
boolean shouldApplyAbnormal = hasAbnormalChange;
```

- [ ] **Step 4: 运行 Android 定向测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2StreamClientTest" --tests "com.binance.monitor.service.V2StreamRefreshPlannerTest" --tests "com.binance.monitor.service.ConnectionStatusResolverTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/constants/AppConstants.java app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClientTest.java app/src/test/java/com/binance/monitor/service/V2StreamRefreshPlannerTest.java app/src/test/java/com/binance/monitor/service/ConnectionStatusResolverTest.java
git commit -m "feat: consume producer-driven v2 stream events in app"
```

### Task 5: APP 收掉异常轮询和本地异常生产

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/AbnormalGatewayClient.java`
- Modify: `app/src/main/java/com/binance/monitor/service/AbnormalSyncRuntimeHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/HistoricalAbnormalRecordBuilder.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilder.java`
- Test: `app/src/test/java/com/binance/monitor/data/remote/AbnormalGatewayClientTest.java`
- Test: `app/src/test/java/com/binance/monitor/service/AbnormalSyncRuntimeHelperTest.java`

- [ ] **Step 1: 先写异常消费链失败测试**

```java
@Test
public void shouldNotUseLocalFallbackNotification_whenAppConsumesServerProducedAbnormal() {
    assertFalse(AbnormalSyncRuntimeHelper.shouldUseLocalFallbackNotification(true, false));
}
```

- [ ] **Step 2: 运行异常相关测试，确认先失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.AbnormalGatewayClientTest" --tests "com.binance.monitor.service.AbnormalSyncRuntimeHelperTest"`

Expected: 失败，当前 helper 和 `MonitorService` 仍保留轮询与本地兜底语义。

- [ ] **Step 3: 删除 APP 本地异常生产与轮询消费**

```java
// 删除 abnormalSyncRunnable/requestAbnormalSync/applyAbnormalSyncResult
// 删除 handleClosedKline() 内 recordManager.createRecord(...) 的本地异常生产
// 图表页只消费 recordManager 已有记录，不再调用 HistoricalAbnormalRecordBuilder.buildFromCandles(...)
```

- [ ] **Step 4: 运行异常相关测试和图表定向测试，确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.AbnormalGatewayClientTest" --tests "com.binance.monitor.service.AbnormalSyncRuntimeHelperTest" --tests "com.binance.monitor.ui.chart.HistoricalAbnormalRecordBuilderTest" --tests "com.binance.monitor.ui.chart.AbnormalAnnotationOverlayBuilderTest"`

Expected: PASS

- [ ] **Step 5: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/data/remote/AbnormalGatewayClient.java app/src/main/java/com/binance/monitor/service/AbnormalSyncRuntimeHelper.java app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java app/src/main/java/com/binance/monitor/ui/chart/HistoricalAbnormalRecordBuilder.java app/src/main/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilder.java app/src/test/java/com/binance/monitor/data/remote/AbnormalGatewayClientTest.java app/src/test/java/com/binance/monitor/service/AbnormalSyncRuntimeHelperTest.java
git commit -m "refactor: remove app-side abnormal production paths"
```

### Task 6: 账户运行态/历史按 revision 收口并跑全量回归

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`
- Test: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`
- Test: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
- Test: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2ClientTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerSourceTest.java`

- [ ] **Step 1: 先写“仅 history revision 前进才拉历史”的失败测试**

```java
@Test
public void fetchForOverlay_shouldSkipHistoryReload_whenHistoryRevisionUnchanged() {
    // 断言：运行态快照更新后，只刷新运行态，不再重拉 history
}
```

- [ ] **Step 2: 运行账户相关定向测试，确认先失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2ClientTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest"`

Expected: 失败，当前 overlay 链仍会主动走 snapshot/history 组合拉取。

- [ ] **Step 3: 按 revision 收口账户运行态和历史拉取**

```java
if (runtimeRevisionChanged) {
    applyRuntimeSnapshot(...);
}
if (historyRevisionChanged) {
    AccountHistoryPayload history = gatewayV2Client.fetchAccountHistory(AccountTimeRange.ALL, "");
    persistHistory(history);
}
```

- [ ] **Step 4: 跑服务端与 Android 回归**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_v2_account_pipeline bridge.mt5_gateway.tests.test_abnormal_gateway`

Expected: PASS

Run: `./gradlew :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2ClientTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest"`

Expected: PASS

- [ ] **Step 5: 更新文档并提交收口**

```bash
git add app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java README.md ARCHITECTURE.md CONTEXT.md bridge/mt5_gateway/tests/test_v2_sync_pipeline.py bridge/mt5_gateway/tests/test_v2_account_pipeline.py app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2ClientTest.java app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerSourceTest.java
git commit -m "refactor: unify runtime and history consumption by revision"
```

