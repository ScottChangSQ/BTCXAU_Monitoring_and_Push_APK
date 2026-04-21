# Live Trading Phase 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在第二阶段闭合后，补齐第三阶段“批量与复杂交易”主链，让 APP 能处理批量平仓、部分平仓、加仓/反手、Close By，以及 `netting / hedging` 下不同语义的稳定执行与结果追踪。

**Architecture:** 保持第二阶段单笔 `TradeCommand -> TradeExecutionCoordinator` 主链不回退，第三阶段在其上新增“批量计划层 + 复杂语义归一层 + 批量结果展示层”。服务端新增正式 `/v2/trade/batch/check|submit|result` 契约，客户端新增 `BatchTradePlan` 与 `TradeComplexActionPlanner`，把复杂动作先展开为可追踪的批量计划，再统一执行、展示单项结果并收敛到账户真值。

**Tech Stack:** Android Java、JUnit4、Source Test、Python unittest、MT5 gateway v2、Gradle `testDebugUnitTest`、`assembleDebug`、ADB 真机安装

---

## File Map

### Existing files to modify

- `bridge/mt5_gateway/server_v2.py`
  当前只暴露单笔 `/v2/trade/check|submit|result`；第三阶段要补正式 batch API，并把批量结果存储、查询、同步发布接进现有 v2 服务壳。
- `bridge/mt5_gateway/v2_trade.py`
  当前只做单笔动作解析；第三阶段继续复用它做单项 request 生成，不在 batch 层重写 MT5 低层规则。
- `bridge/mt5_gateway/v2_trade_models.py`
  当前只定义单笔状态与错误码；第三阶段要补 batch 状态、执行策略和复杂动作错误码。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeCommandFactory.java`
  当前只负责单笔命令；第三阶段要补批量 item 辅助构建，但不把复杂动作逻辑塞回页面层。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
  当前只处理单笔；第三阶段不直接把它改成批量执行器，只补共享的结果文案与真值收敛辅助入口。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
  当前是图表页复杂交易主入口；第三阶段继续把复杂动作入口收在这里，但真正的动作展开交给 planner/coordinator，不在这里手写分支。
- `app/src/main/res/layout/dialog_trade_command.xml`
  当前是单笔交易表单；第三阶段要补复杂动作模式的最小字段组合，避免另起第二套大弹窗。
- `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`
  当前只锁定单笔；第三阶段补共享结算/收敛辅助断言，避免批量层绕过第二阶段合同。
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`
  锁定图表页交易入口依然只消费正式主链，不允许页面层直接循环调单笔接口。
- `app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java`
  用来确认快捷交易仍只覆盖第二阶段的小手数单笔快捷，不被第三阶段批量能力污染。
- `README.md`
  第三阶段完成后补“已支持批量与复杂交易”的真实口径，不提前写成已完成。
- `ARCHITECTURE.md`
  需要补 batch gateway、complex action planner、batch result view 三层职责与调用关系。
- `CONTEXT.md`
  记录第三阶段计划已写完、当前停点与推荐执行顺序。

### New files to create

- `bridge/mt5_gateway/v2_trade_batch.py`
  服务端批量交易主入口，负责 batch payload 校验、策略执行、单项结果聚合与 `batchId` 存储。
- `bridge/mt5_gateway/tests/test_v2_trade_batch.py`
  锁定 batch 契约、执行策略、Close By/部分平仓等复杂语义的服务端测试。
- `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeItem.java`
  单项批量执行项，承载 itemId、action、displayLabel、原始命令和扩展元数据。
- `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradePlan.java`
  一次批量计划，承载 `batchId`、策略、账户模式、计划摘要与 item 列表。
- `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeItemResult.java`
  一项批量结果，承载状态、错误、order/deal、可读文案与收敛标记。
- `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeReceipt.java`
  一批结果回执，承载整体状态、单项结果、服务端时间和幂等字段。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeComplexActionPlanner.java`
  把“批量平仓、部分平仓、加仓、反手、Close By”先展开成 `BatchTradePlan`。
- `app/src/main/java/com/binance/monitor/ui/trade/BatchTradeCoordinator.java`
  客户端批量执行协调器，负责 batch check、统一确认、batch submit、强刷与单项结果展示模型。
- `app/src/main/java/com/binance/monitor/ui/trade/BatchTradeResultFormatter.java`
  把服务端 batch 结果转成人能看懂的“总览 + 单项清单”。
- `app/src/test/java/com/binance/monitor/ui/trade/TradeComplexActionPlannerTest.java`
  锁定复杂动作在 `netting / hedging` 下的展开规则。
- `app/src/test/java/com/binance/monitor/ui/trade/BatchTradeCoordinatorTest.java`
  锁定 batch check/submit/result 与收敛策略。
- `app/src/test/java/com/binance/monitor/ui/trade/BatchTradeResultFormatterTest.java`
  锁定部分成功、全部失败、Close By 成功等用户文案。

### Existing docs to modify

- `docs/superpowers/plans/2026-04-20-live-trading-phase3-detailed-implementation.md`
  本文件，执行时按步骤勾选。
- `CONTEXT.md`
  记录“第三阶段详细计划已生成，尚未开始实施”。

---

## 推荐执行顺序

1. 先立服务端 batch 契约，不让客户端先长出假的批量循环调用。
2. 再立客户端 batch 数据模型与执行协调器，让 APP 有正式消费边界。
3. 再补复杂动作 planner，把部分平仓/反手/Close By 统一归一。
4. 再把图表页入口接入 batch 主链，并补结果清单展示。
5. 最后做总回归、真机安装和人工验收。

---

### Task 1: 建立服务端 batch 契约与执行策略

**Files:**
- Create: `bridge/mt5_gateway/v2_trade_batch.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_trade_models.py`
- Create: `bridge/mt5_gateway/tests/test_v2_trade_batch.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_trade_contracts.py`

- [ ] **Step 1: 写失败测试，锁定 batch 契约与策略边界**

```python
class V2TradeBatchTests(unittest.TestCase):

    def test_batch_submit_should_require_batch_id_and_item_ids(self):
        payload = server_v2.v2_trade_batch_submit({
            "batchId": "",
            "strategy": "BEST_EFFORT",
            "items": [{"itemId": "", "action": "CLOSE_POSITION", "params": {"positionTicket": 1}}],
        })
        self.assertEqual("FAILED", payload["status"])
        self.assertEqual("TRADE_BATCH_INVALID_ID", payload["error"]["code"])

    def test_batch_submit_should_return_per_item_results(self):
        payload = server_v2.v2_trade_batch_submit({
            "batchId": "batch-close-001",
            "strategy": "BEST_EFFORT",
            "items": [
                {"itemId": "close-1", "action": "CLOSE_POSITION", "params": {"symbol": "BTCUSD", "positionTicket": 11, "volume": 0.10}},
                {"itemId": "close-2", "action": "CLOSE_POSITION", "params": {"symbol": "BTCUSD", "positionTicket": 12, "volume": 0.20}},
            ],
        })
        self.assertEqual("ACCEPTED", payload["status"])
        self.assertEqual(2, len(payload["items"]))
        self.assertEqual("close-1", payload["items"][0]["itemId"])

    def test_batch_submit_should_support_all_or_none(self):
        payload = server_v2.v2_trade_batch_submit({
            "batchId": "batch-aon-001",
            "strategy": "ALL_OR_NONE",
            "items": [
                {"itemId": "ok-1", "action": "CLOSE_POSITION", "params": {"symbol": "BTCUSD", "positionTicket": 11, "volume": 0.10}},
                {"itemId": "bad-2", "action": "CLOSE_POSITION", "params": {"symbol": "BTCUSD", "positionTicket": 0, "volume": 0.20}},
            ],
        })
        self.assertEqual("FAILED", payload["status"])
        self.assertEqual("REJECTED", payload["items"][0]["status"])
        self.assertEqual("REJECTED", payload["items"][1]["status"])
```

- [ ] **Step 2: 运行服务端 batch 测试，确认当前失败**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_trade_batch -v
```

Expected:

```text
AttributeError: module 'server_v2' has no attribute 'v2_trade_batch_submit'
```

- [ ] **Step 3: 在 `v2_trade_models.py` 补 batch 常量与错误码**

```python
STATUS_BATCH_ACCEPTED = "ACCEPTED"
STATUS_BATCH_PARTIAL = "PARTIAL"
STATUS_BATCH_FAILED = "FAILED"

STRATEGY_BEST_EFFORT = "BEST_EFFORT"
STRATEGY_ALL_OR_NONE = "ALL_OR_NONE"
STRATEGY_GROUPED = "GROUPED"

ERROR_BATCH_INVALID_ID = "TRADE_BATCH_INVALID_ID"
ERROR_BATCH_INVALID_STRATEGY = "TRADE_BATCH_INVALID_STRATEGY"
ERROR_BATCH_EMPTY_ITEMS = "TRADE_BATCH_EMPTY_ITEMS"
ERROR_BATCH_UNSUPPORTED_ACTION = "TRADE_BATCH_UNSUPPORTED_ACTION"
```

- [ ] **Step 4: 新建 `v2_trade_batch.py`，复用单项 `v2_trade.prepare_trade_request(...)`**

```python
def normalize_batch_payload(payload):
    batch_id = str((payload or {}).get("batchId") or "").strip()
    strategy = str((payload or {}).get("strategy") or "BEST_EFFORT").strip().upper()
    items = list((payload or {}).get("items") or [])
    return {"batchId": batch_id, "strategy": strategy, "items": items}


def submit_trade_batch(payload, *, account_mode, mt5_module, position_lookup, symbol_info_lookup, send_request):
    batch = normalize_batch_payload(payload)
    validation_error = validate_batch_payload(batch)
    if validation_error is not None:
        return build_batch_failed_response(batch, validation_error)
    prepared_items = [prepare_batch_item(item, account_mode, mt5_module, position_lookup, symbol_info_lookup) for item in batch["items"]]
    if batch["strategy"] == "ALL_OR_NONE" and any(item["error"] is not None for item in prepared_items):
        return build_all_or_none_rejected_response(batch, prepared_items)
    executed_items = [execute_prepared_item(item, send_request) for item in prepared_items]
    return build_batch_submit_response(batch, executed_items)
```

- [ ] **Step 5: 在 `server_v2.py` 暴露正式 batch API，并保存 `batchId -> result`**

```python
@app.post("/v2/trade/batch/submit")
def v2_trade_batch_submit(payload: dict):
    account_mode = _detect_account_mode()
    result = v2_trade_batch.submit_trade_batch(
        payload,
        account_mode=account_mode,
        mt5_module=mt5,
        position_lookup=_resolve_trade_position,
        symbol_info_lookup=_resolve_symbol_info,
        send_request=_trade_send_request,
    )
    batch_request_store[result["batchId"]] = result
    if result["status"] in {"ACCEPTED", "PARTIAL"}:
        _invalidate_account_runtime_cache_after_trade_commit()
        _publish_account_trade_commit_sync_state()
    return result


@app.get("/v2/trade/batch/result")
def v2_trade_batch_result(batchId: str):
    return batch_request_store.get(batchId) or build_batch_not_found(batchId)
```

- [ ] **Step 6: 补 Close By 与 grouped 策略的服务端合同测试**

```python
def test_batch_submit_should_keep_close_by_pairing():
    payload = server_v2.v2_trade_batch_submit({
        "batchId": "batch-closeby-001",
        "strategy": "GROUPED",
        "items": [
            {"itemId": "pair-1a", "action": "CLOSE_BY", "params": {"symbol": "BTCUSD", "positionTicket": 101, "oppositePositionTicket": 202, "groupKey": "pair-1"}},
            {"itemId": "pair-1b", "action": "CLOSE_BY", "params": {"symbol": "BTCUSD", "positionTicket": 202, "oppositePositionTicket": 101, "groupKey": "pair-1"}},
        ],
    })
    self.assertEqual("ACCEPTED", payload["status"])
    self.assertTrue(all(item["groupKey"] == "pair-1" for item in payload["items"]))
```

- [ ] **Step 7: 运行服务端 batch 全量相关测试**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_trade_batch bridge.mt5_gateway.tests.test_v2_trade_contracts -v
```

Expected:

```text
OK
```

- [ ] **Step 8: Commit**

```bash
git add bridge/mt5_gateway/v2_trade_models.py \
        bridge/mt5_gateway/v2_trade_batch.py \
        bridge/mt5_gateway/server_v2.py \
        bridge/mt5_gateway/tests/test_v2_trade_batch.py \
        bridge/mt5_gateway/tests/test_v2_trade_contracts.py
git commit -m "feat: add canonical batch trade gateway contract"
```

### Task 2: 建立客户端 batch 数据模型与消费边界

**Files:**
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeItem.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradePlan.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeItemResult.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeReceipt.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/BatchTradeCoordinatorTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`

- [ ] **Step 1: 写失败测试，锁定客户端 batch 只消费正式 batch 契约**

```java
@Test
public void submitBatchShouldExposePerItemResults() {
    FakeBatchTradeGateway gateway = new FakeBatchTradeGateway();
    gateway.batchReceipt = BatchTradeReceipt.accepted(
            "batch-close-001",
            Arrays.asList(
                    BatchTradeItemResult.accepted("item-1", "平仓 BTCUSD #1"),
                    BatchTradeItemResult.rejected("item-2", "平仓 BTCUSD #2", ExecutionError.of("TRADE_INVALID_POSITION", "position missing"))
            )
    );
    BatchTradeCoordinator coordinator = new BatchTradeCoordinator(gateway, new FakeAccountRefreshGateway(), 2);

    BatchTradeCoordinator.ExecutionResult result = coordinator.submit(buildCloseBatchPlan());

    assertEquals(BatchTradeCoordinator.UiState.PARTIAL, result.getUiState());
    assertEquals(2, result.getReceipt().getItems().size());
    assertEquals("item-2", result.getReceipt().getItems().get(1).getItemId());
}
```

- [ ] **Step 2: 运行测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.BatchTradeCoordinatorTest" -v
```

Expected:

```text
BatchTradeCoordinatorTest > submitBatchShouldExposePerItemResults FAILED
```

- [ ] **Step 3: 新建 batch 模型，固定 `batchId + strategy + items` 边界**

```java
public class BatchTradePlan {
    private final String batchId;
    private final String strategy;
    private final String accountMode;
    private final String summary;
    private final List<BatchTradeItem> items;
}

public class BatchTradeItem {
    private final String itemId;
    private final String displayLabel;
    private final TradeCommand command;
    private final JSONObject extras;
}
```

- [ ] **Step 4: 新建 `BatchTradeCoordinator`，只调 batch API，不在页面层循环单笔 submit**

```java
public final class BatchTradeCoordinator {
    public ExecutionResult submit(BatchTradePlan plan) {
        BatchTradeReceipt receipt = batchTradeGateway.submit(plan);
        if (receipt.isAccepted() || receipt.isPartial()) {
            AccountStatsPreloadManager.Cache latestCache = accountRefreshGateway.fetchFullForUi(AccountTimeRange.ALL);
            return ExecutionResult.fromReceipt(receipt, latestCache);
        }
        return ExecutionResult.failed(receipt);
    }
}
```

- [ ] **Step 5: 在 `TradeExecutionCoordinatorTest` 补共享边界断言**

```java
assertFalse("第三阶段批量执行不能改写第二阶段单笔状态机", prepared.getMessage().contains("batchId"));
```

- [ ] **Step 6: 运行客户端 batch 模型与共享边界测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.BatchTradeCoordinatorTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeItem.java \
        app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradePlan.java \
        app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeItemResult.java \
        app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradeReceipt.java \
        app/src/main/java/com/binance/monitor/ui/trade/BatchTradeCoordinator.java \
        app/src/test/java/com/binance/monitor/ui/trade/BatchTradeCoordinatorTest.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java
git commit -m "feat: add phase3 batch trade client boundary"
```

### Task 3: 把复杂交易语义归一到 planner，而不是页面分支

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeComplexActionPlanner.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradeComplexActionPlannerTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeCommandFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeIntent.java`

- [ ] **Step 1: 写失败测试，锁定 `netting / hedging` 下的复杂语义展开**

```java
@Test
public void reverseInNettingShouldExpandToCloseThenOpen() {
    BatchTradePlan plan = TradeComplexActionPlanner.planReverse(
            "acc-1",
            "netting",
            buildLongPosition("BTCUSD", 0.30d, 1001L),
            0.50d,
            65100d
    );

    assertEquals("BEST_EFFORT", plan.getStrategy());
    assertEquals(2, plan.getItems().size());
    assertEquals("CLOSE_POSITION", plan.getItems().get(0).getCommand().getAction());
    assertEquals("OPEN_MARKET", plan.getItems().get(1).getCommand().getAction());
}

@Test
public void closeByInHedgingShouldKeepSinglePairGroup() {
    BatchTradePlan plan = TradeComplexActionPlanner.planCloseBy(
            "acc-1",
            buildHedgingPosition("BTCUSD", "buy", 0.20d, 3001L),
            buildHedgingPosition("BTCUSD", "sell", 0.20d, 3002L)
    );

    assertEquals(2, plan.getItems().size());
    assertEquals("pair-1", plan.getItems().get(0).getExtras().optString("groupKey"));
    assertEquals("CLOSE_BY", plan.getItems().get(0).getCommand().getAction());
}
```

- [ ] **Step 2: 运行 planner 测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeComplexActionPlannerTest" -v
```

Expected:

```text
TradeComplexActionPlannerTest > reverseInNettingShouldExpandToCloseThenOpen FAILED
```

- [ ] **Step 3: 在 `TradeCommandFactory` 补第三阶段复杂动作命令辅助方法**

```java
public static TradeCommand closeBy(String accountId,
                                   String symbol,
                                   long positionTicket,
                                   long oppositePositionTicket) {
    JSONObject params = createBaseParams(symbol, 0d, 0d, 0d, 0d);
    putQuietly(params, "positionTicket", positionTicket);
    putQuietly(params, "oppositePositionTicket", oppositePositionTicket);
    return new TradeCommand(nextRequestId(), accountId, symbol, "CLOSE_BY", 0d, 0d, 0d, 0d, params);
}
```

- [ ] **Step 4: 新建 `TradeComplexActionPlanner`，统一展开复杂语义**

```java
public final class TradeComplexActionPlanner {
    public static BatchTradePlan planPartialClose(...) { ... }
    public static BatchTradePlan planBatchClose(...) { ... }
    public static BatchTradePlan planAddPosition(...) { ... }
    public static BatchTradePlan planReverse(...) { ... }
    public static BatchTradePlan planCloseBy(...) { ... }
}
```

- [ ] **Step 5: 明确 planner 的固定规则**

```java
// netting 反手：先平当前净仓，再开反向净仓
// hedging 反手：不开启隐式净额合并，保留明确的 close/open 或 close_by 成对动作
// 部分平仓：必须显式 volume，不能用“留剩余手数”反推
// 批量平仓：每个 position 都有独立 itemId，不能只传一串 ticket
```

- [ ] **Step 6: 运行复杂语义 planner 回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeComplexActionPlannerTest" --tests "com.binance.monitor.ui.trade.TradeCommandFactoryTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/trade/TradeComplexActionPlanner.java \
        app/src/main/java/com/binance/monitor/ui/trade/TradeCommandFactory.java \
        app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeIntent.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeComplexActionPlannerTest.java \
        app/src/test/java/com/binance/monitor/ui/trade/TradeCommandFactoryTest.java
git commit -m "feat: add complex trade action planner"
```

### Task 4: 把图表页复杂交易入口接回正式 batch 主链

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- Modify: `app/src/main/res/layout/dialog_trade_command.xml`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/BatchTradeResultFormatter.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/BatchTradeResultFormatterTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java`

- [ ] **Step 1: 写失败源码/行为测试，锁定图表页不允许页面层自己 for-loop 提交**

```java
@Test
public void marketChartTradeDialogShouldUseBatchCoordinatorForComplexActions() throws Exception {
    String source = readSource("app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java");
    assertTrue(source.contains("BatchTradeCoordinator"));
    assertTrue(source.contains("TradeComplexActionPlanner"));
    assertFalse(source.contains("for (TradeCommand"));
}
```

- [ ] **Step 2: 运行源码测试，确认当前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" -v
```

Expected:

```text
MarketChartTradeSourceTest > marketChartTradeDialogShouldUseBatchCoordinatorForComplexActions FAILED
```

- [ ] **Step 3: 扩展 `dialog_trade_command.xml`，只补第三阶段最小字段，不另起第二套弹窗**

```xml
<Spinner
    android:id="@+id/spinnerTradeComplexAction"
    android:layout_width="match_parent"
    android:layout_height="@dimen/control_height_lg"
    android:visibility="gone" />

<EditText
    android:id="@+id/etTradeTargetVolume"
    android:layout_width="match_parent"
    android:layout_height="@dimen/control_height_lg"
    android:visibility="gone" />
```

- [ ] **Step 4: 在 `MarketChartTradeDialogCoordinator` 中接入 planner + batch coordinator**

```java
if (action == ChartTradeAction.COMPLEX_REVERSE) {
    BatchTradePlan plan = TradeComplexActionPlanner.planReverse(...);
    requestBatchTradeExecution(plan);
    return;
}
if (action == ChartTradeAction.COMPLEX_PARTIAL_CLOSE) {
    BatchTradePlan plan = TradeComplexActionPlanner.planPartialClose(...);
    requestBatchTradeExecution(plan);
    return;
}
```

- [ ] **Step 5: 新建 `BatchTradeResultFormatter`，统一总览与单项结果文案**

```java
public final class BatchTradeResultFormatter {
    public static String buildSummary(BatchTradeReceipt receipt) { ... }
    public static List<String> buildItemLines(BatchTradeReceipt receipt) { ... }
}
```

- [ ] **Step 6: 锁定第二阶段快捷交易不被第三阶段污染**

```java
assertFalse(source.contains("BatchTradeCoordinator") && source.contains("btnQuickTradePrimary"));
```

- [ ] **Step 7: 运行图表页与结果格式相关测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.chart.ChartQuickTradeCoordinatorTest" --tests "com.binance.monitor.ui.trade.BatchTradeResultFormatterTest" -v
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java \
        app/src/main/res/layout/dialog_trade_command.xml \
        app/src/main/java/com/binance/monitor/ui/trade/BatchTradeResultFormatter.java \
        app/src/test/java/com/binance/monitor/ui/trade/BatchTradeResultFormatterTest.java \
        app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java \
        app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java
git commit -m "feat: wire chart complex trade entry to batch flow"
```

### Task 5: 第三阶段总回归、真机安装与文档同步

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: 跑第三阶段最小总回归**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_trade_batch bridge.mt5_gateway.tests.test_v2_trade_contracts -v
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.BatchTradeCoordinatorTest" --tests "com.binance.monitor.ui.trade.TradeComplexActionPlannerTest" --tests "com.binance.monitor.ui.trade.BatchTradeResultFormatterTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.ui.chart.ChartQuickTradeCoordinatorTest" -v
```

Expected:

```text
OK
BUILD SUCCESSFUL
```

- [ ] **Step 2: 编译 Debug 包**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 3: 安装并启动真机**

Run:

```powershell
adb -s 7fab54c4 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 7fab54c4 shell am start -W com.binance.monitor/.ui.main.MainActivity
```

Expected:

```text
Success
Status: ok
```

- [ ] **Step 4: 手动验收 checklist**

```text
1. 批量平仓 2-3 单后，弹窗能显示总览与单项结果，不只是“整批成功”
2. 部分平仓必须按目标 volume 执行，不允许模糊的“自动留仓”
3. netting 反手显示为“先平后开”，hedging 不把两种语义混成一个动作
4. Close By 只在满足成对条件时出现，并能展示 pair 结果
5. 一项失败、其余成功时，页面文案明确显示部分成功
6. 第二阶段快捷交易按钮仍是单笔快捷，不会误走第三阶段批量流
7. 执行后账户页、图表页、挂单/持仓层最终保持一致
```

- [ ] **Step 5: 更新文档**

写入：

```md
## 当前正在做什么
- 第三阶段详细计划 `docs/superpowers/plans/2026-04-20-live-trading-phase3-detailed-implementation.md` 已执行完毕。
- 批量交易、部分平仓、加仓/反手、Close By 与 netting/hedging 复杂语义已正式落地。
- 下一步若继续，才进入第四阶段 DOM 与专业级体验，不再回头补第三阶段主链能力。
```

同时在 `README.md / ARCHITECTURE.md` 补：

```md
- 第三阶段已支持正式 batch gateway，不再由页面层循环调单笔接口
- 复杂动作统一先经 `TradeComplexActionPlanner` 展开，再由 `BatchTradeCoordinator` 执行
```

- [ ] **Step 6: Commit**

```bash
git add CONTEXT.md README.md ARCHITECTURE.md
git commit -m "chore: sync phase3 complex trade rollout context"
```

## Self-Review

### Spec coverage

- 批量交易正式契约：Task 1
- 客户端 batch 正式边界：Task 2
- 部分平仓 / 加仓 / 反手 / Close By / 账户模式差异：Task 3
- 图表页入口接入与结果清单展示：Task 4
- 总回归、真机安装、阶段文档：Task 5

### Placeholder scan

- 计划中未使用 `TODO / TBD / implement later`
- 每个任务都给出文件、测试、命令和提交点
- 每个阶段都明确了“不能怎么做”的收口边界

### Type consistency

- 服务端批量主线统一使用 `batchId / strategy / items`
- 客户端批量主线统一使用 `BatchTradeItem / BatchTradePlan / BatchTradeReceipt`
- 复杂动作统一使用 `TradeComplexActionPlanner`
- 图表页批量执行统一使用 `BatchTradeCoordinator`
