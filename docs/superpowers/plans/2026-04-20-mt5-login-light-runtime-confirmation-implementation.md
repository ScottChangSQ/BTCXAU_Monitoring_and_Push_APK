# MT5 Login Light Runtime Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把手机端 MT5 账户登录成功的收口条件从 `/v2/account/full` 切换为现有 `accountRuntime` / `/v2/account/snapshot` 轻量身份确认，同时把 `/v2/account/full` 降为登录后的后台补全链路。

**Architecture:** 客户端继续复用现有 `AccountRemoteSessionCoordinator -> AccountSessionStateMachine -> AccountStatsPreloadManager` 主链，不新增第二套登录状态。实现上分三层推进：先用测试锁定“登录成功不再依赖 full”；再给 `AccountStatsPreloadManager` 增加 snapshot 轻量缓存入口，并让前台登录/切换流优先吃轻量确认；最后补服务端 identity 契约测试和 Windows 部署脚本里的 `/v2/account/full` 诊断检查。

**Tech Stack:** Android Java、FastAPI/Python、OkHttp、JUnit4、Python unittest、PowerShell、Gradle `testDebugUnitTest`、`assembleDebug`

---

## 范围检查

这次只覆盖一个子系统：远程 MT5 账户登录收口。

它虽然同时触及 Android、Python 和 Windows 部署脚本，但都是围绕同一条合同变化：

- 登录成功由轻量运行态确认
- full 只做后台补全

因此不再拆成多个 plan，按一条串行主链执行即可。

---

## File Map

### Existing files to modify

- `app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java`
  新增 `FULL_SYNCING` 状态，并把“已登录但 full 还在补全”从 `ACTIVE` 中拆出来。
- `app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java`
  保持等待目标 `login/server` 的严格匹配，同时补一个“轻量确认成功后进入 full 补全态”的正式入口。
- `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
  新增 snapshot 轻量拉取与缓存构建方法，继续复用已有 `updateLatestCache(...)` 和统一运行态派发。
- `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java`
  复用现有 `fetchAccountSnapshot()`，不改 HTTP 协议，只让上层真正使用它。
- `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java`
  前台刷新改成“等待轻量确认 -> 收口登录 -> 后台 full 补全”的顺序。
- `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshHostDelegate.java`
  给协调器增加 `fetchSnapshotForUi...` 与 `requestFullRefreshInBackground` 宿主桥接。
- `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
  登录/切换受理后不再主动打 full，而是主动打一轮 snapshot 轻量确认。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
  宿主侧接入新的 host delegate 方法，并把 full 补全态的 UI 文案接回页面。
- `deploy/tencent/windows/deploy_bundle.ps1`
  在现有健康检查里新增 `/v2/account/full` 诊断，但不把它作为登录成功条件。
- `scripts/tests/test_windows_server_bundle.py`
  锁定部署脚本新增的 full 诊断检查不会回退。
- `bridge/mt5_gateway/tests/test_v2_contracts.py`
- `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
  锁定 `snapshot` / `accountRuntime` 已登录时的 canonical `login/server` 输出。
- `README.md`
- `ARCHITECTURE.md`
- `CONTEXT.md`

### Existing tests to modify

- `app/src/test/java/com/binance/monitor/ui/account/AccountSessionStateMachineTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinatorSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountSessionDialogControllerSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerTest.java`
- `scripts/tests/test_windows_server_bundle.py`
- `bridge/mt5_gateway/tests/test_v2_contracts.py`
- `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`

### No new production files

这次不新增新接口文件，也不新增新的登录缓存模型。  
变化只在现有主链里收口职责。

---

## 顺序总览

1. Task 1 先锁定新合同，防止实现时又退回 full 收口
2. Task 2 再给客户端补 snapshot 轻量确认与 `FULL_SYNCING`
3. Task 3 最后补服务端/部署诊断和文档
4. Task 4 做总回归与提交

这个顺序必须保持，因为：

- 没有 Task 1，Task 2 很容易在实现时偷偷保留 full 依赖
- 没有 Task 2，Task 3 的部署诊断没有清晰的“轻链/重链”边界

---

### Task 1: 锁定“登录成功不再依赖 full”的客户端合同

**Files:**
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountSessionStateMachineTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinatorSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountSessionDialogControllerSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerTest.java`

- [ ] **Step 1: 给状态机测试补 `FULL_SYNCING` 失败用例**

```java
@Test
public void activeConfirmationShouldEnterFullSyncingBeforeFinalIdleUi() {
    AccountSessionStateMachine machine = new AccountSessionStateMachine();

    machine.markSyncing("acct-2", "正在同步账户");
    machine.markFullSyncing("acct-2", "账户已登录，正在加载完整数据");

    AccountSessionStateMachine.StateSnapshot fullSyncing = machine.snapshot();
    assertEquals(AccountSessionStateMachine.AccountSessionUiState.FULL_SYNCING, fullSyncing.getState());
    assertFalse(fullSyncing.isAwaitingSync());

    machine.markActive("acct-2", "账户数据已对齐");
    assertEquals(AccountSessionStateMachine.AccountSessionUiState.ACTIVE, machine.snapshot().getState());
}
```

- [ ] **Step 2: 给 `AccountRemoteSessionCoordinatorTest` 补“轻量确认即收口”测试**

```java
@Test
public void runtimeConfirmationShouldActivateSessionBeforeFullSnapshotArrives() throws Exception {
    AccountRemoteSessionCoordinator coordinator = buildAcceptedCoordinator("12345678", "IC");

    assertTrue(coordinator.isAwaitingSync());

    boolean activated = coordinator.onSnapshotApplied("12345678", "IC");

    assertTrue(activated);
    assertFalse(coordinator.isAwaitingSync());
    assertEquals(
            AccountSessionStateMachine.AccountSessionUiState.FULL_SYNCING,
            coordinator.getStateSnapshotForTest().getState()
    );
}
```

- [ ] **Step 3: 给 `AccountStatsPreloadManagerTest` 补 snapshot 入口测试**

```java
@Test
public void fetchSnapshotForUiShouldUpdateLatestCacheWithoutDroppingStoredHistory() throws Exception {
    GatewayV2Client gateway = fakeGatewayWithSnapshot(
            "12345678",
            "IC",
            true
    );
    AccountStatsPreloadManager manager = buildManager(gateway, storedSnapshotWithTrades("12345678", "IC"));

    AccountStatsPreloadManager.Cache cache = manager.fetchSnapshotForUi();

    assertTrue(cache.isConnected());
    assertEquals("12345678", cache.getAccount());
    assertEquals("IC", cache.getServer());
    assertEquals(2, cache.getSnapshot().getTradeRecords().size());
}
```

- [ ] **Step 4: 给 source tests 锁定两处关键实现约束**

```java
assertTrue(source.contains("preloadManager.fetchSnapshotForUi()"));
assertFalse(source.contains("preloadManager.fetchFullForUi(AccountTimeRange.ALL)"));
assertTrue(refreshSource.contains("host.fetchSnapshotForUiForIdentity("));
assertTrue(refreshSource.contains("host.requestFullRefreshInBackground();"));
```

- [ ] **Step 5: 先跑这组测试，确认它们在实现前失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountSessionStateMachineTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerTest" --tests "com.binance.monitor.ui.account.AccountSnapshotRefreshCoordinatorSourceTest" --tests "com.binance.monitor.ui.account.AccountSessionDialogControllerSourceTest"
```

Expected:

```text
FAILURE: Build failed with an exception.
```

- [ ] **Step 6: 提交 Task 1**

```powershell
git add app/src/test/java/com/binance/monitor/ui/account/AccountSessionStateMachineTest.java app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerTest.java app/src/test/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinatorSourceTest.java app/src/test/java/com/binance/monitor/ui/account/AccountSessionDialogControllerSourceTest.java
git commit -m "test: lock mt5 login light confirmation contract"
```

---

### Task 2: 实现客户端轻量确认主链与 full 后台补全

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshHostDelegate.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`

- [ ] **Step 1: 在状态机中加入 `FULL_SYNCING` 正式状态**

```java
public enum AccountSessionUiState {
    IDLE,
    ENCRYPTING,
    SUBMITTING,
    SWITCHING,
    SYNCING,
    FULL_SYNCING,
    ACTIVE,
    FAILED
}

public synchronized void markFullSyncing(@Nullable String profileId, @Nullable String message) {
    current = new StateSnapshot(AccountSessionUiState.FULL_SYNCING, message, profileId, false);
}
```

- [ ] **Step 2: 在 `AccountStatsPreloadManager` 增加 snapshot 轻量入口**

```java
public Cache fetchSnapshotForUi() {
    if (!isAccountSessionActive()) {
        clearStoredSnapshotForResolvedIdentity();
        clearLatestCache();
        nextDelayMs = MAX_REFRESH_MS;
        return null;
    }
    try {
        AccountSnapshotPayload payload = gatewayV2Client.fetchAccountSnapshot();
        AccountStorageRepository.StoredSnapshot runtimeSnapshot = buildStoredSnapshotFromSnapshotPayload(payload);
        AccountStorageRepository.StoredSnapshot merged = mergePublishedRuntimeWithStoredHistory(
                runtimeSnapshot,
                loadStoredSnapshotForWorkerThread()
        );
        Cache cache = buildCache(merged, merged.getHistoryRevision());
        updateLatestCache(cache);
        return cache;
    } catch (Exception exception) {
        return updateFailureCacheForUi(exception.getMessage());
    }
}
```

- [ ] **Step 3: 抽出 snapshot payload 到 stored snapshot 的转换函数**

```java
private AccountStorageRepository.StoredSnapshot buildStoredSnapshotFromSnapshotPayload(AccountSnapshotPayload payload) {
    JSONObject accountMeta = payload == null ? new JSONObject() : payload.getAccountMeta();
    return new AccountStorageRepository.StoredSnapshot(
            resolveRuntimeConnected(accountMeta),
            optString(accountMeta, "login", ""),
            optString(accountMeta, "server", ""),
            resolveV2Source(accountMeta),
            resolveGatewayEndpoint(),
            payload == null ? System.currentTimeMillis() : payload.getServerTime(),
            "",
            System.currentTimeMillis(),
            optString(accountMeta, "historyRevision", ""),
            parseMetrics(payload == null ? null : payload.getOverviewMetrics()),
            new ArrayList<>(),
            parseMetrics(payload == null ? null : payload.getCurveIndicators()),
            parsePositionItems(payload == null ? null : payload.getPositions(), false),
            parsePositionItems(payload == null ? null : payload.getOrders(), true),
            new ArrayList<>(),
            parseMetrics(payload == null ? null : payload.getStatsMetrics())
    );
}
```

- [ ] **Step 4: 在远程会话协调器里明确“轻量确认成功 -> full 补全态”**

```java
public boolean onSnapshotApplied(@Nullable String account, @Nullable String server) {
    AwaitingSyncState pendingState = awaitingSyncState.get();
    if (!matchesAwaitingIdentity(pendingState, account, server)) {
        return false;
    }
    if (!awaitingSyncState.compareAndSet(pendingState, AwaitingSyncState.empty())) {
        return false;
    }
    sessionSummaryStore.saveSession(pendingState.getActiveAccount(), pendingState.getSavedAccounts(), true);
    stateMachine.markFullSyncing(pendingState.getProfileId(), "账户已登录，正在加载完整数据");
    return true;
}
```

- [ ] **Step 5: 让登录弹窗受理后先打 snapshot，不再主动打 full**

```java
private void requestForegroundEntrySnapshot() {
    if (sessionExecutor.isShutdown()) {
        return;
    }
    sessionExecutor.execute(() -> preloadManager.fetchSnapshotForUi());
}
```

- [ ] **Step 6: 重写 `AccountSnapshotRefreshCoordinator` 的等待逻辑**

```java
if (host.isAwaitingSync()) {
    AccountStatsPreloadManager.Cache runtime = host.fetchSnapshotForUiForIdentity(
            host.getLoginAccountInput(),
            host.getLoginServerInput()
    );
    if (runtime != null && runtime.isConnected()) {
        boolean sessionActivatedNow = host.onSnapshotApplied(runtime.getAccount(), runtime.getServer());
        if (sessionActivatedNow) {
            host.showLoginSuccessBanner();
            host.requestFullRefreshInBackground();
        }
        host.applyConnectedMeta(true, runtime.getAccount(), runtime.getAccount(), runtime.getServer(), runtime.getSource(), runtime.getGateway(), runtime.getUpdatedAt(), "");
        host.setLoading(false);
        return;
    }
}
AccountStatsPreloadManager.Cache remote = host.fetchFullForUi(AccountTimeRange.ALL);
```

- [ ] **Step 7: 给 host delegate 和宿主补新方法**

```java
AccountStatsPreloadManager.Cache fetchSnapshotForUiForIdentity(@NonNull String account, @NonNull String server);
void requestFullRefreshInBackground();
```

```java
public AccountStatsPreloadManager.Cache fetchSnapshotForUiForIdentity(@NonNull String account, @NonNull String server) {
    return owner.fetchSnapshotForUiForIdentity(account, server);
}

public void requestFullRefreshInBackground() {
    owner.requestFullRefreshInBackground();
}
```

- [ ] **Step 8: 在页面宿主里把 full 补全从“登录闸门”改成后台刷新**

```java
void requestFullRefreshInBackground() {
    uiModelExecutor.execute(() -> preloadManager.fetchFullForUi(AccountTimeRange.ALL));
}
```

- [ ] **Step 9: 跑客户端回归，确认新合同通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountSessionStateMachineTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerTest" --tests "com.binance.monitor.ui.account.AccountSnapshotRefreshCoordinatorSourceTest" --tests "com.binance.monitor.ui.account.AccountSessionDialogControllerSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsScreenSessionSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionActivitySourceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 10: 提交 Task 2**

```powershell
git add app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshCoordinator.java app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRefreshHostDelegate.java app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java
git commit -m "feat: confirm mt5 login from light runtime"
```

---

### Task 3: 补服务端 identity 契约与 Windows full 诊断

**Files:**
- Modify: `bridge/mt5_gateway/tests/test_v2_contracts.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`
- Modify: `deploy/tencent/windows/deploy_bundle.ps1`
- Modify: `scripts/tests/test_windows_server_bundle.py`

- [ ] **Step 1: 先写服务端契约测试，锁定 snapshot 与 stream 的 canonical identity**

```python
def test_account_snapshot_should_expose_canonical_login_and_server_for_remote_active(self):
    with mock.patch.object(server_v2.session_manager, "build_status_payload", return_value={
        "ok": True,
        "state": "active",
        "activeAccount": {"login": "7400048", "server": "ICMarketsSC-MT5-6"},
        "savedAccounts": [],
        "savedAccountCount": 0,
    }):
        payload = server_v2.v2_account_snapshot()

    self.assertEqual("7400048", payload["accountMeta"]["login"])
    self.assertEqual("ICMarketsSC-MT5-6", payload["accountMeta"]["server"])
```

```python
def test_v2_stream_bootstrap_should_keep_account_runtime_identity_for_remote_active(self):
    payload = server_v2._build_v2_stream_bootstrap_message({
        "busSeq": 1,
        "publishedAt": 1000,
        "runtimeSnapshot": {
            "accountRevision": "acct-1",
            "account": {"accountMeta": {"login": "7400048", "server": "ICMarketsSC-MT5-6"}}
        }
    })

    self.assertEqual("7400048", payload["changes"]["accountRuntime"]["snapshot"]["accountMeta"]["login"])
    self.assertEqual("ICMarketsSC-MT5-6", payload["changes"]["accountRuntime"]["snapshot"]["accountMeta"]["server"])
```

- [ ] **Step 2: 跑 Python 测试，确认实现前失败**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_contracts bridge.mt5_gateway.tests.test_v2_sync_pipeline -v
```

Expected:

```text
FAILED (failures=...)
```

- [ ] **Step 3: 给部署脚本补 `/v2/account/full` 诊断，不把它纳入登录主链**

```powershell
Start-HealthProbe -Context $Context -Label "8787 /v2/account/full (diagnostic)"
$directAccountFull = Wait-HttpOk -Url "http://127.0.0.1:8787/v2/account/full" -MaxSeconds 180
Write-DeployLog -Context $Context -Message ("健康检查通过: 8787 /v2/account/full (diagnostic) -> " + $directAccountFull.StatusCode)
```

```powershell
("8787 /v2/account/full (diagnostic) -> " + $directAccountFull.StatusCode),
```

- [ ] **Step 4: 给 bundle 测试锁定新的诊断行**

```python
self.assertIn('Start-HealthProbe -Context $Context -Label "8787 /v2/account/full (diagnostic)"', deploy_script)
self.assertIn('http://127.0.0.1:8787/v2/account/full', deploy_script)
self.assertIn('健康检查通过: 8787 /v2/account/full (diagnostic)', deploy_script)
```

- [ ] **Step 5: 跑服务端和部署回归**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_contracts bridge.mt5_gateway.tests.test_v2_sync_pipeline scripts.tests.test_windows_server_bundle -v
```

Expected:

```text
OK
```

- [ ] **Step 6: 提交 Task 3**

```powershell
git add bridge/mt5_gateway/tests/test_v2_contracts.py bridge/mt5_gateway/tests/test_v2_sync_pipeline.py deploy/tencent/windows/deploy_bundle.ps1 scripts/tests/test_windows_server_bundle.py
git commit -m "chore: add mt5 full diagnostic to deploy checks"
```

---

### Task 4: 总回归、文档同步与交付

**Files:**
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 跑 Android 账户链总回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountSessionStateMachineTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerTest" --tests "com.binance.monitor.ui.account.AccountSnapshotRefreshCoordinatorSourceTest" --tests "com.binance.monitor.ui.account.AccountSessionDialogControllerSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsScreenSessionSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionActivitySourceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 2: 跑 Python 和部署脚本总回归**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_session_contracts bridge.mt5_gateway.tests.test_v2_contracts bridge.mt5_gateway.tests.test_v2_sync_pipeline scripts.tests.test_windows_server_bundle -v
```

Expected:

```text
OK
```

- [ ] **Step 3: 重新生成 Windows bundle 并做本地核对**

Run:

```powershell
python scripts/build_windows_server_bundle.py
python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle --skip-remote
```

Expected:

```text
本地源码与部署目录一致
```

- [ ] **Step 4: 更新 README 的部署/登录说明**

```markdown
- 手机端登录成功现在基于轻量账户运行态确认，不再等待 `/v2/account/full`
- `/v2/account/full` 仅用于完整历史、统计和图表补全
- 部署脚本会额外打印 `/v2/account/full` 诊断结果，帮助发现 full 链偏慢
```

- [ ] **Step 5: 更新 ARCHITECTURE 的登录链说明**

```markdown
- `AccountRemoteSessionCoordinator` 只负责“目标账号等待态”与会话状态机
- `AccountStatsPreloadManager.fetchSnapshotForUi()` 负责轻量身份确认
- `AccountStatsPreloadManager.fetchFullForUi()` 负责登录后的完整补全
```

- [ ] **Step 6: 更新 CONTEXT 当前停点**

```markdown
- 2026-04-20 已完成“MT5 登录轻量确认收口”实现与回归：登录成功改由 `accountRuntime` / `/v2/account/snapshot` 收口，`/v2/account/full` 降为后台补全；Windows 部署脚本已新增 full 诊断检查。下一步待重新部署后做真机登录复验。
```

- [ ] **Step 7: 跑 assembleDebug，准备真机验证包**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected:

```text
BUILD SUCCESSFUL
```

- [ ] **Step 8: 提交 Task 4**

```powershell
git add README.md ARCHITECTURE.md CONTEXT.md dist/windows_server_bundle docs/superpowers/plans/2026-04-20-mt5-login-light-runtime-confirmation-implementation.md
git commit -m "feat: decouple mt5 login confirmation from full snapshot"
```

---

## Self-Review

### Spec coverage

- “登录成功改为轻量运行态确认”：Task 1、Task 2
- “`/v2/account/full` 只做后台补全”：Task 2
- “服务端 identity 稳定输出”：Task 3
- “部署检查新增 full 诊断”：Task 3
- “文档和当前进度同步”：Task 4

### Placeholder scan

- 本计划没有 `TODO / TBD / implement later`
- 每个代码步骤都给出了明确方法名、断言或命令
- 没有“类似 Task N”这类跳步说明

### Type consistency

- 轻量入口统一命名为 `fetchSnapshotForUi()` / `fetchSnapshotForUiForIdentity(...)`
- full 后台补全统一命名为 `requestFullRefreshInBackground()`
- 新状态统一命名为 `FULL_SYNCING`

