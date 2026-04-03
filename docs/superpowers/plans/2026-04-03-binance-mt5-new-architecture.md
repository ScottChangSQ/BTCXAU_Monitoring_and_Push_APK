# Binance 行情 + MT5 账户新架构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性停服切换到“Binance 负责行情真值、MT5 负责账户真值、APP 只负责展示和本地快照”的新架构，彻底移除 APP 端本地拼 K 线和多份缓存互相覆盖的旧逻辑。

**Architecture:** 服务端在 `bridge/mt5_gateway/server_v2.py` 上拆出 `v2` 行情、账户和同步三层输出：闭合 K 线真值、未收盘实时 patch、MT5 当前账户快照、MT5 历史事实整理后的展示模型，以及统一的 `syncToken`。APP 端新增一层轻量同步与快照读取逻辑，页面只显示服务端结果；本地只保留最近快照和每个周期的闭合 K 线列表加当前 patch，不再承担最终真值计算。

**Tech Stack:** Python、FastAPI、MetaTrader5 Python API、Binance REST / WebSocket、Java、Android ViewBinding、Room、JUnit4、Gradle

---

### Task 1: 服务端建立 `v2` 数据契约与统一时间轴

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Create: `bridge/mt5_gateway/tests/test_v2_contracts.py`
- Modify: `bridge/mt5_gateway/API.md`

- [ ] **Step 1: 先写失败测试，锁定 `v2` 合约最小结构**

在 `bridge/mt5_gateway/tests/test_v2_contracts.py` 新增以下测试骨架，先只校验结构，不实现功能：

```python
from fastapi.testclient import TestClient
from bridge.mt5_gateway.server_v2 import app


def test_v2_market_snapshot_has_sync_token_and_market_section():
    client = TestClient(app)
    response = client.get("/v2/market/snapshot")
    assert response.status_code == 200
    payload = response.json()
    assert "serverTime" in payload
    assert "syncToken" in payload
    assert "market" in payload


def test_v2_market_candles_separates_closed_candles_and_patch():
    client = TestClient(app)
    response = client.get("/v2/market/candles", params={"symbol": "BTCUSDT", "interval": "1h", "limit": 10})
    assert response.status_code == 200
    payload = response.json()
    assert "candles" in payload
    assert "latestPatch" in payload


def test_v2_account_snapshot_has_positions_and_orders():
    client = TestClient(app)
    response = client.get("/v2/account/snapshot")
    assert response.status_code == 200
    payload = response.json()
    assert "account" in payload
    assert "positions" in payload
    assert "orders" in payload
```

- [ ] **Step 2: 运行测试，确认 `v2` 接口当前不存在或结构不符**

Run: `.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests/test_v2_contracts.py -q`

Expected: FAIL，提示 `/v2/...` 路由不存在或字段缺失。

- [ ] **Step 3: 在 `server_v2.py` 增加统一时间和 token 辅助函数**

把下列最小实现加入 `server_v2.py`，后续任务都复用：

```python
def _server_now_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def _build_sync_token(server_time_ms: int, revision: str) -> str:
    payload = f"{server_time_ms}:{revision}".encode("utf-8")
    return hashlib.sha1(payload).hexdigest()


def _normalize_display_time_ms(value_ms: int) -> int:
    if value_ms <= 0:
        return 0
    return int(value_ms)
```

- [ ] **Step 4: 补最小 `v2` 空实现路由，让结构先跑通**

在 `server_v2.py` 中补路由占位，返回最小结构：

```python
@app.get("/v2/market/snapshot")
def v2_market_snapshot() -> Dict[str, Any]:
    now_ms = _server_now_ms()
    return {
        "serverTime": now_ms,
        "syncToken": _build_sync_token(now_ms, "market-snapshot"),
        "market": {},
        "account": {},
    }


@app.get("/v2/market/candles")
def v2_market_candles(symbol: str, interval: str, limit: int = 300) -> Dict[str, Any]:
    now_ms = _server_now_ms()
    return {
        "symbol": symbol,
        "interval": interval,
        "serverTime": now_ms,
        "candles": [],
        "latestPatch": None,
        "nextSyncToken": _build_sync_token(now_ms, f"{symbol}:{interval}"),
    }


@app.get("/v2/account/snapshot")
def v2_account_snapshot() -> Dict[str, Any]:
    now_ms = _server_now_ms()
    return {
        "serverTime": now_ms,
        "syncToken": _build_sync_token(now_ms, "account-snapshot"),
        "account": {},
        "positions": [],
        "orders": [],
    }
```

- [ ] **Step 5: 重新运行契约测试**

Run: `.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests/test_v2_contracts.py -q`

Expected: PASS，说明 `v2` 基础契约与统一时间/token 入口已建立。

- [ ] **Step 6: 提交这一小步**

```bash
git add bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_contracts.py bridge/mt5_gateway/API.md
git commit -m "feat: add v2 gateway contracts"
```

### Task 2: 服务端落地 Binance 行情真值层

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Create: `bridge/mt5_gateway/tests/test_v2_market_pipeline.py`
- Modify: `bridge/mt5_gateway/API.md`

- [ ] **Step 1: 先写失败测试，锁定“闭合 K 线和 patch 分层”**

在 `bridge/mt5_gateway/tests/test_v2_market_pipeline.py` 增加以下测试：

```python
from bridge.mt5_gateway import server_v2


def test_build_market_candle_payload_marks_closed_rows():
    payload = server_v2._build_market_candle_payload({
        "symbol": "BTCUSDT",
        "interval": "1h",
        "openTime": 1000,
        "closeTime": 1999,
        "open": "1",
        "high": "2",
        "low": "0.5",
        "close": "1.5",
        "volume": "10",
        "quoteVolume": "15",
        "tradeCount": 7,
    }, True)
    assert payload["isClosed"] is True
    assert payload["openTime"] == 1000


def test_split_closed_and_patch_keeps_only_last_open_patch():
    rows = [
        {"openTime": 1000, "closeTime": 1999, "isClosed": True},
        {"openTime": 2000, "closeTime": 2999, "isClosed": False},
    ]
    closed_rows, patch = server_v2._split_closed_and_patch(rows)
    assert len(closed_rows) == 1
    assert patch["openTime"] == 2000
```

- [ ] **Step 2: 运行测试，确认当前无此辅助函数**

Run: `.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests/test_v2_market_pipeline.py -q`

Expected: FAIL，提示 `_build_market_candle_payload` / `_split_closed_and_patch` 未定义。

- [ ] **Step 3: 在 `server_v2.py` 中补齐 Binance 行情真值辅助函数**

加入最小实现：

```python
def _build_market_candle_payload(row: Dict[str, Any], is_closed: bool) -> Dict[str, Any]:
    return {
        "symbol": str(row.get("symbol", "")),
        "interval": str(row.get("interval", "")),
        "openTime": int(row.get("openTime", 0) or 0),
        "closeTime": int(row.get("closeTime", 0) or 0),
        "open": float(row.get("open", 0.0) or 0.0),
        "high": float(row.get("high", 0.0) or 0.0),
        "low": float(row.get("low", 0.0) or 0.0),
        "close": float(row.get("close", 0.0) or 0.0),
        "volume": float(row.get("volume", 0.0) or 0.0),
        "quoteVolume": float(row.get("quoteVolume", 0.0) or 0.0),
        "tradeCount": int(row.get("tradeCount", 0) or 0),
        "source": "binance",
        "isClosed": bool(is_closed),
        "version": 1,
    }


def _split_closed_and_patch(rows: List[Dict[str, Any]]) -> Tuple[List[Dict[str, Any]], Optional[Dict[str, Any]]]:
    closed_rows: List[Dict[str, Any]] = []
    patch: Optional[Dict[str, Any]] = None
    for row in rows:
        if bool(row.get("isClosed", False)):
            closed_rows.append(dict(row))
        else:
            patch = dict(row)
    return closed_rows, patch
```

- [ ] **Step 4: 让 `/v2/market/candles` 走 Binance 历史真值 + 实时 patch**

把路由升级成下面的最小结构：

```python
@app.get("/v2/market/candles")
def v2_market_candles(symbol: str, interval: str, limit: int = 300) -> Dict[str, Any]:
    now_ms = _server_now_ms()
    rows = _load_binance_market_rows(symbol=symbol, interval=interval, limit=limit)
    closed_rows, patch = _split_closed_and_patch(rows)
    return {
        "symbol": symbol,
        "interval": interval,
        "serverTime": now_ms,
        "candles": closed_rows,
        "latestPatch": patch,
        "nextSyncToken": _build_sync_token(now_ms, f"market:{symbol}:{interval}:{len(closed_rows)}"),
    }
```

- [ ] **Step 5: 重新运行行情管道测试**

Run: `.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests/test_v2_market_pipeline.py -q`

Expected: PASS，说明服务端已经能把闭合历史和当前 patch 分层输出。

- [ ] **Step 6: 提交这一小步**

```bash
git add bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_market_pipeline.py bridge/mt5_gateway/API.md
git commit -m "feat: add v2 binance market pipeline"
```

### Task 3: 服务端落地 MT5 账户真值层与展示模型

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Create: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`
- Modify: `bridge/mt5_gateway/API.md`

- [ ] **Step 1: 写失败测试，锁定账户快照与历史展示模型最小结构**

在 `bridge/mt5_gateway/tests/test_v2_account_pipeline.py` 新增测试：

```python
from bridge.mt5_gateway import server_v2


def test_build_account_snapshot_payload_contains_core_totals():
    payload = server_v2._build_account_snapshot_payload(
        balance=1000.0,
        equity=1010.0,
        margin=20.0,
        free_margin=990.0,
        margin_level=5050.0,
        profit=10.0,
        positions=[],
        orders=[],
    )
    assert payload["balance"] == 1000.0
    assert payload["equity"] == 1010.0


def test_build_account_history_response_exposes_curve_and_trades():
    payload = server_v2._build_account_history_response([], [], [])
    assert "trades" in payload
    assert "orders" in payload
    assert "curvePoints" in payload
```

- [ ] **Step 2: 运行测试，确认账户真值辅助函数当前不存在**

Run: `.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests/test_v2_account_pipeline.py -q`

Expected: FAIL，提示 `_build_account_snapshot_payload` / `_build_account_history_response` 未定义。

- [ ] **Step 3: 在 `server_v2.py` 中补账户快照和历史展示模型函数**

加入最小实现：

```python
def _build_account_snapshot_payload(balance: float,
                                    equity: float,
                                    margin: float,
                                    free_margin: float,
                                    margin_level: float,
                                    profit: float,
                                    positions: List[Dict[str, Any]],
                                    orders: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "balance": float(balance),
        "equity": float(equity),
        "margin": float(margin),
        "freeMargin": float(free_margin),
        "marginLevel": float(margin_level),
        "profit": float(profit),
        "positions": list(positions),
        "orders": list(orders),
    }


def _build_account_history_response(trades: List[Dict[str, Any]],
                                    orders: List[Dict[str, Any]],
                                    curve_points: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "trades": list(trades),
        "orders": list(orders),
        "curvePoints": list(curve_points),
    }
```

- [ ] **Step 4: 新增 `/v2/account/history` 路由，统一给 APP 提供展示模型**

在 `server_v2.py` 中加入：

```python
@app.get("/v2/account/history")
def v2_account_history(range: str = "all", cursor: str = "") -> Dict[str, Any]:
    now_ms = _server_now_ms()
    trades, orders, curve_points = _load_account_history_model(range_key=range, cursor=cursor)
    return {
        **_build_account_history_response(trades, orders, curve_points),
        "serverTime": now_ms,
        "nextCursor": "",
        "syncToken": _build_sync_token(now_ms, f"account-history:{range}:{len(trades)}"),
    }
```

- [ ] **Step 5: 重新运行账户管道测试**

Run: `.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests/test_v2_account_pipeline.py -q`

Expected: PASS，说明账户快照和历史展示模型入口已经建立。

- [ ] **Step 6: 提交这一小步**

```bash
git add bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_account_pipeline.py bridge/mt5_gateway/API.md
git commit -m "feat: add v2 mt5 account pipeline"
```

### Task 4: 服务端落地统一同步与推送层

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Create: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
- Modify: `bridge/mt5_gateway/API.md`

- [ ] **Step 1: 先写失败测试，锁定 `delta` 和 WebSocket 消息结构**

在 `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py` 写入：

```python
from bridge.mt5_gateway import server_v2


def test_build_delta_payload_contains_market_and_account_sections():
    payload = server_v2._build_v2_delta_payload([], [])
    assert "marketDelta" in payload
    assert "accountDelta" in payload


def test_build_ws_message_has_type_and_sync_token():
    message = server_v2._build_v2_ws_message("marketPatch", {"symbol": "BTCUSDT"}, "abc")
    assert message["type"] == "marketPatch"
    assert message["syncToken"] == "abc"
```

- [ ] **Step 2: 运行测试，确认同步辅助函数当前不存在**

Run: `.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests/test_v2_sync_pipeline.py -q`

Expected: FAIL，提示 `_build_v2_delta_payload` / `_build_v2_ws_message` 未定义。

- [ ] **Step 3: 在 `server_v2.py` 中补统一同步辅助函数**

```python
def _build_v2_delta_payload(market_delta: List[Dict[str, Any]],
                            account_delta: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "marketDelta": list(market_delta),
        "accountDelta": list(account_delta),
    }


def _build_v2_ws_message(message_type: str,
                         payload: Dict[str, Any],
                         sync_token: str) -> Dict[str, Any]:
    return {
        "type": message_type,
        "payload": dict(payload),
        "serverTime": _server_now_ms(),
        "syncToken": sync_token,
    }
```

- [ ] **Step 4: 新增 `/v2/sync/delta` 与 `WS /v2/stream`**

```python
@app.get("/v2/sync/delta")
def v2_sync_delta(syncToken: str = "") -> Dict[str, Any]:
    now_ms = _server_now_ms()
    market_delta, account_delta = _load_v2_deltas(syncToken)
    payload = _build_v2_delta_payload(market_delta, account_delta)
    payload["serverTime"] = now_ms
    payload["nextSyncToken"] = _build_sync_token(now_ms, f"delta:{len(market_delta)}:{len(account_delta)}")
    return payload


@app.websocket("/v2/stream")
async def v2_stream(websocket: WebSocket) -> None:
    await websocket.accept()
    while True:
        await websocket.send_json(_build_v2_ws_message("heartbeat", {}, _build_sync_token(_server_now_ms(), "heartbeat")))
        await asyncio.sleep(5)
```

- [ ] **Step 5: 重新运行同步测试**

Run: `.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests/test_v2_sync_pipeline.py -q`

Expected: PASS，说明统一同步语义已建立。

- [ ] **Step 6: 提交这一小步**

```bash
git add bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_sync_pipeline.py bridge/mt5_gateway/API.md
git commit -m "feat: add v2 sync pipeline"
```

### Task 5: APP 端新增 `v2` 网络模型与快照缓存层

**Files:**
- Create: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/MarketSnapshotPayload.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/MarketSeriesPayload.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/AccountSnapshotPayload.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/SyncDeltaPayload.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/V2SnapshotStore.java`
- Create: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2ClientTest.java`
- Create: `app/src/test/java/com/binance/monitor/data/local/V2SnapshotStoreTest.java`

- [ ] **Step 1: 写失败测试，锁定 `v2` 客户端和本地快照存储行为**

在 `GatewayV2ClientTest.java` 中写：

```java
@Test
public void parseMarketSnapshotShouldKeepSyncToken() throws Exception {
    String body = "{\"serverTime\":1,\"syncToken\":\"abc\",\"market\":{},\"account\":{}}";
    MarketSnapshotPayload payload = GatewayV2Client.parseMarketSnapshot(body);
    assertEquals("abc", payload.getSyncToken());
}
```

在 `V2SnapshotStoreTest.java` 中写：

```java
@Test
public void saveAndReadMarketSnapshotShouldRoundTrip() {
    V2SnapshotStore store = new V2SnapshotStore(ApplicationProvider.getApplicationContext());
    store.writeMarketSnapshot("{\"syncToken\":\"abc\"}");
    assertEquals("{\"syncToken\":\"abc\"}", store.readMarketSnapshot());
}
```

- [ ] **Step 2: 运行测试，确认客户端与快照存储当前不存在**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2ClientTest" --tests "com.binance.monitor.data.local.V2SnapshotStoreTest"`

Expected: FAIL，提示类不存在或方法未定义。

- [ ] **Step 3: 新建 `GatewayV2Client` 与基础 payload 模型**

在 `GatewayV2Client.java` 中先实现最小解析接口：

```java
public final class GatewayV2Client {

    public static MarketSnapshotPayload parseMarketSnapshot(String body) throws Exception {
        JSONObject json = new JSONObject(body);
        return new MarketSnapshotPayload(
                json.optLong("serverTime"),
                json.optString("syncToken", ""),
                json.optJSONObject("market"),
                json.optJSONObject("account")
        );
    }
}
```

- [ ] **Step 4: 新建 `V2SnapshotStore`，只负责原样读写快照字符串**

```java
public final class V2SnapshotStore {
    private final SharedPreferences preferences;

    public V2SnapshotStore(Context context) {
        preferences = context.getSharedPreferences("v2_snapshot_store", Context.MODE_PRIVATE);
    }

    public void writeMarketSnapshot(String body) {
        preferences.edit().putString("market_snapshot", body).apply();
    }

    public String readMarketSnapshot() {
        return preferences.getString("market_snapshot", "");
    }
}
```

- [ ] **Step 5: 重新运行 `v2` 客户端测试**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2ClientTest" --tests "com.binance.monitor.data.local.V2SnapshotStoreTest"`

Expected: PASS，说明 APP 端 `v2` 读写入口已就绪。

- [ ] **Step 6: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java app/src/main/java/com/binance/monitor/data/model/v2 app/src/main/java/com/binance/monitor/data/local/V2SnapshotStore.java app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2ClientTest.java app/src/test/java/com/binance/monitor/data/local/V2SnapshotStoreTest.java
git commit -m "feat: add android v2 gateway client"
```

### Task 6: APP 图表页切到 `v2` 行情链路

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/WebSocketManager.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/KlineCacheStore.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java`
- Create: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartV2SourceTest.java`

- [ ] **Step 1: 先写失败测试，锁定图表页只读 `v2 candles + patch`**

在 `MarketChartV2SourceTest.java` 中加入：

```java
@Test
public void chartActivityShouldReadClosedCandlesAndPatchSeparately() throws Exception {
    Path file = Paths.get("src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java");
    String source = Files.readString(file, StandardCharsets.UTF_8);
    assertTrue(source.contains("latestPatch"));
    assertTrue(source.contains("GatewayV2Client"));
}
```

- [ ] **Step 2: 运行测试，确认图表页尚未切到 `v2`**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartV2SourceTest"`

Expected: FAIL，说明页面仍依赖旧行情链路。

- [ ] **Step 3: 让 `MarketChartActivity` 改成“先读本地快照，再读 `v2/market/candles`”**

把旧的整窗真值决策逻辑替换成：

```java
private void loadSeriesFromV2(String symbol, String intervalKey) {
    MarketSeriesPayload payload = gatewayV2Client.fetchMarketSeries(symbol, intervalKey, FULL_WINDOW_LIMIT);
    List<CandleEntry> closedCandles = payload.getCandles();
    CandleEntry patch = payload.getLatestPatch();
    renderSeries(closedCandles, patch);
    snapshotStore.writeSeriesSnapshot(symbol, intervalKey, payload.getRawJson());
}
```

- [ ] **Step 4: 让 `MonitorService` / `WebSocketManager` 不再发布图表真值，只转发 `v2 stream`**

最小替换方向：

```java
public interface Listener {
    void onV2Message(String type, String body);
}
```

并删除“APP 内部用 WebSocket 最终决定闭合 K 线真值”的职责。

- [ ] **Step 5: 重新运行图表相关测试和编译**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.*" assembleDebug`

Expected: PASS，且图表页代码已显式依赖 `v2` 行情来源。

- [ ] **Step 6: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/data/remote/WebSocketManager.java app/src/main/java/com/binance/monitor/data/local/KlineCacheStore.java app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java app/src/test/java/com/binance/monitor/ui/chart/MarketChartV2SourceTest.java
git commit -m "feat: migrate chart to v2 market pipeline"
```

### Task 7: APP 账户页与持仓页切到 `v2` 账户链路

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Create: `app/src/test/java/com/binance/monitor/ui/account/AccountV2SourceTest.java`

- [ ] **Step 1: 写失败测试，锁定账户页读取 `v2/account/snapshot` 与 `v2/account/history`**

```java
@Test
public void accountPreloadManagerShouldDependOnGatewayV2Client() throws Exception {
    Path file = Paths.get("src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java");
    String source = Files.readString(file, StandardCharsets.UTF_8);
    assertTrue(source.contains("GatewayV2Client"));
    assertTrue(source.contains("/v2/account/snapshot"));
}
```

- [ ] **Step 2: 运行测试，确认账户链路尚未切换**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountV2SourceTest"`

Expected: FAIL，说明账户页仍依赖旧接口。

- [ ] **Step 3: 让 `AccountStatsPreloadManager` 改读新的账户快照和历史接口**

最小改造骨架：

```java
AccountSnapshotPayload snapshot = gatewayV2Client.fetchAccountSnapshot();
AccountHistoryPayload history = gatewayV2Client.fetchAccountHistory(rangeKey, cursor);
accountStorageRepository.replaceFromV2(snapshot, history);
```

- [ ] **Step 4: 让 `AccountStorageRepository` 增加 “replaceFromV2” 原子入口**

```java
public void replaceFromV2(AccountSnapshotPayload snapshot, AccountHistoryPayload history) {
    database.runInTransaction(() -> {
        accountDao.replaceSnapshot(snapshot);
        tradeDao.replaceHistory(history.getTrades());
        curveDao.replaceCurve(history.getCurvePoints());
    });
}
```

- [ ] **Step 5: 重新运行账户相关测试和编译**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.account.*" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositoryTest" assembleDebug`

Expected: PASS，说明账户页和本地存储已切到 `v2` 账户真值。

- [ ] **Step 6: 提交这一小步**

```bash
git add app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/test/java/com/binance/monitor/ui/account/AccountV2SourceTest.java
git commit -m "feat: migrate account pipeline to v2"
```

### Task 8: 清理旧架构、重置缓存、完成一次性切换验证

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/KlineCacheStore.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java`
- Modify: `bridge/mt5_gateway/README.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 写失败测试，锁定旧职责已被移除**

新增或改写源码断言，确保关键旧职责被清掉：

```java
assertFalse(source.contains("RealtimeMinuteKlineAssembler"));
assertFalse(source.contains("mergeRealtimeTailIntoSeries"));
assertFalse(source.contains("fetchChartKlineFullWindow"));
```

- [ ] **Step 2: 运行测试，确认仍存在旧职责**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.*SourceTest"`

Expected: FAIL，说明旧链路仍留在代码中。

- [ ] **Step 3: 提升缓存版本并清空旧结构**

在切换代码中执行统一清理：

```java
private static final int V2_SCHEMA_VERSION = 2;

private void clearLegacyCachesForV2() {
    chartHistoryRepository.clearAllHistory();
    klineCacheStore.clearAll();
    accountStorageRepository.clearAll();
    snapshotStore.clearAll();
}
```

- [ ] **Step 4: 删除旧的最终真值决策逻辑，并更新文档**

必须删除：

```text
APP 内部拼高周期 K 线
页面层决定历史真值和实时尾部如何混合
APP 自己做时间偏移修正
多份缓存共同参与最终真值判断
```

并同步更新：

```text
README.md
ARCHITECTURE.md
bridge/mt5_gateway/README.md
CONTEXT.md
```

- [ ] **Step 5: 跑完整验证并编译 APK**

Run:

```bash
.\.venv\Scripts\python.exe -m pytest bridge/mt5_gateway/tests -q
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Expected:

```text
服务端测试 PASS
Android 单测 PASS
assembleDebug PASS
输出 app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 6: 提交最终切换**

```bash
git add bridge/mt5_gateway README.md ARCHITECTURE.md CONTEXT.md app/src/main/java app/src/test/java
git commit -m "refactor: switch app to v2 market and account architecture"
```

## 自检

- 规格覆盖：
  - 行情真值、账户真值、统一同步、APP 快照、停服一次性切换都已有任务覆盖。
- 占位词检查：
  - 计划中未使用任何未决占位描述。
- 类型与命名一致性：
  - 服务端统一使用 `syncToken`、`latestPatch`、`candles`、`curvePoints`。
  - APP 统一使用 `GatewayV2Client`、`V2SnapshotStore`、`AccountSnapshotPayload` 等命名。
