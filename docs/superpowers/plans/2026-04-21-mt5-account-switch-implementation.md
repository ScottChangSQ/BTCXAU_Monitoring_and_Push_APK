# MT5 Account Switch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 MT5 新登录/切换账号收口为独立服务端主链，并把 APP 重启恢复改成只做轻量确认，不再重复触发整套切号流程。

**Architecture:** 服务端新增独立的 `v2_mt5_account_switch.py` 模块，负责 GUI 主窗口检测、按需拉起、附着重试、基线读取、强制切号和 30 秒轮询确认；`server_v2.py` 只负责调用该模块并把结构化结果写回会话接口与诊断链。客户端继续复用现有 `/v2/session/login` 与 `/v2/session/status` 接口，但扩展 `SessionReceipt` 解析，并把 APP 重启恢复链改到 `AccountSessionRecoveryHelper`，只做当前实际账号轻量确认与账号失效通知。

**Tech Stack:** Python 3、FastAPI、Windows PowerShell、MetaTrader5 Python 包、Java、Android、OkHttp、JUnit、unittest

---

## 文件结构

### 服务端

- Create: `bridge/mt5_gateway/v2_mt5_account_switch.py`
  - 独立承载 MT5 GUI 主窗口检测、按需拉起、附着重试、读取基线账号、切号调用、轮询确认、统一结果结构。
- Modify: `bridge/mt5_gateway/server_v2.py`
  - 删除登录主链里对旧 `active_session / saved profile / .env / probe / reset terminal` 的切号决策依赖。
  - 接入 `v2_mt5_account_switch.py`。
  - 给 `/v2/session/login` 与 `/v2/session/switch` 返回新的结构化结果字段。
- Modify: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`
  - 调整既有合同测试，去掉旧切号决策入口的断言，补充新的 receipt 字段与接口合同。
- Create: `bridge/mt5_gateway/tests/test_v2_mt5_account_switch.py`
  - 独立覆盖 GUI 主窗口检测、按需拉起、附着重试、基线读取、切号轮询、超时失败等主链合同。
- Modify: `bridge/mt5_gateway/tests/test_v2_session_diagnostic.py`
  - 校验失败阶段只落到新的 7 个真实阶段。

### 客户端

- Modify: `app/src/main/java/com/binance/monitor/data/model/v2/session/SessionReceipt.java`
  - 扩展为可承载 `stage / elapsedMs / baselineAccount / finalAccount / loginError / lastObservedAccount`。
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java`
  - 解析新 receipt 结构。
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java`
  - 继续沿用“服务器已受理 -> UI 同步中”的框架，但保留更完整的 receipt 信息给上层。
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
  - 失败弹窗继续可复制，但优先展示新主链返回的 `message/stage/loginError/lastObservedAccount` 摘要。
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelper.java`
  - 去掉“APP 重启后自动 switchSavedAccount 切回原账号”的恢复逻辑。
  - 改成只做轻量确认：服务器当前实际账号仍一致则恢复，否则落未登录并打通知。
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
  - 承接恢复链结果，在账号失效时刷新 UI 并发出通知。
- Modify: `app/src/main/java/com/binance/monitor/util/NotificationHelper.java`
  - 增加“服务器端当前实际账号已经不是原来的账号了”的通知入口。

### 客户端测试

- Modify: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientTest.java`
- Modify: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountSessionDialogControllerSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelperSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/util/NotificationHelperSourceTest.java`

### 文档

- Modify: `CONTEXT.md`
  - 同步实现停点与新的真实主链。

## Task 1: 锁定服务端独立切号主链合同

**Files:**
- Create: `bridge/mt5_gateway/tests/test_v2_mt5_account_switch.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_diagnostic.py`

- [ ] **Step 1: 写 `v2_mt5_account_switch` 的失败测试**

```python
import unittest
from unittest import mock

from bridge.mt5_gateway import v2_mt5_account_switch


class Mt5AccountSwitchFlowTests(unittest.TestCase):
    def test_should_launch_mt5_when_no_gui_window_exists(self):
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=False),
            launch_terminal=mock.Mock(return_value=None),
            wait_window_ready=mock.Mock(return_value=True),
            attach_terminal=mock.Mock(return_value=True),
            read_account=mock.Mock(side_effect=[
                {"login": "11111111", "server": "Old-Server"},
                {"login": "12345678", "server": "New-Server"},
            ]),
            switch_account=mock.Mock(return_value={"ok": False, "error": "Authorization failed"}),
            monotonic_ms=mock.Mock(side_effect=[0, 8000]),
        )

        result = controller.switch_account(
            login="12345678",
            password="secret",
            server="New-Server",
        )

        self.assertTrue(result["ok"])
        self.assertEqual("switch_succeeded", result["stage"])
        controller.launch_terminal.assert_called_once()

    def test_should_fail_when_window_not_ready_within_15000ms(self):
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=False),
            launch_terminal=mock.Mock(return_value=None),
            wait_window_ready=mock.Mock(return_value=False),
            attach_terminal=mock.Mock(),
            read_account=mock.Mock(),
            switch_account=mock.Mock(),
            monotonic_ms=mock.Mock(side_effect=[0, 15000]),
        )

        result = controller.switch_account(
            login="12345678",
            password="secret",
            server="New-Server",
        )

        self.assertFalse(result["ok"])
        self.assertEqual("window_not_found_then_window_ready_timeout", result["stage"])
        controller.attach_terminal.assert_not_called()
```

- [ ] **Step 2: 运行测试，确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_mt5_account_switch -v`

Expected: `FAIL`，提示 `No module named 'bridge.mt5_gateway.v2_mt5_account_switch'` 或 `Mt5GuiController` 未定义。

- [ ] **Step 3: 给 `server_v2` 现有合同测试补新断言**

```python
def test_session_login_should_return_switch_flow_fields(self):
    with mock.patch.object(
        server_v2.session_manager,
        "login_new_account",
        return_value={
            "ok": True,
            "state": "activated",
            "requestId": "req-login-1",
            "message": "切换前=11111111 / Old-Server；切换后=12345678 / New-Server；耗时=8000ms",
            "stage": "switch_succeeded",
            "elapsedMs": 8000,
            "baselineAccount": {"login": "11111111", "server": "Old-Server"},
            "finalAccount": {"login": "12345678", "server": "New-Server"},
            "loginError": "",
            "lastObservedAccount": {"login": "12345678", "server": "New-Server"},
        },
    ):
        payload = server_v2.v2_session_login(envelope)

    self.assertEqual("switch_succeeded", payload["stage"])
    self.assertEqual(8000, payload["elapsedMs"])
    self.assertEqual("11111111", payload["baselineAccount"]["login"])
    self.assertEqual("12345678", payload["finalAccount"]["login"])
```

- [ ] **Step 4: 跑合同测试确认新增断言失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_contracts.V2SessionContractsTests.test_session_login_should_return_switch_flow_fields -v`

Expected: `FAIL`，提示缺少 `stage` 或 `elapsedMs` 等字段。

- [ ] **Step 5: 给诊断测试补 7 个真实失败阶段断言**

```python
def test_direct_login_failure_should_only_use_real_switch_flow_stages(self):
    allowed = {
        "window_not_found_then_launch_failed",
        "window_not_found_then_window_ready_timeout",
        "attach_failed",
        "baseline_account_read_failed",
        "switch_call_exception",
        "final_account_read_failed",
        "switch_timeout_account_not_changed",
    }
    timeline = server_v2.session_diagnostic_store.lookup("req-switch-failed")
    for item in timeline:
        if item.get("status") == "failed":
            self.assertIn(item.get("stage"), allowed)
```

- [ ] **Step 6: 跑诊断测试确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_diagnostic.V2SessionDiagnosticTests.test_direct_login_failure_should_only_use_real_switch_flow_stages -v`

Expected: `FAIL`，因为当前代码仍会落到旧的 `direct_terminal_reset_*` 或其他旧阶段名。

- [ ] **Step 7: Commit**

```bash
git add bridge/mt5_gateway/tests/test_v2_mt5_account_switch.py bridge/mt5_gateway/tests/test_v2_session_contracts.py bridge/mt5_gateway/tests/test_v2_session_diagnostic.py
git commit -m "test: lock mt5 account switch flow contracts"
```

## Task 2: 实现服务端独立切号模块

**Files:**
- Create: `bridge/mt5_gateway/v2_mt5_account_switch.py`
- Test: `bridge/mt5_gateway/tests/test_v2_mt5_account_switch.py`

- [ ] **Step 1: 建立最小实现骨架**

```python
"""MT5 账户切换主链，负责窗口检测、按需拉起、附着、切号和最终账号确认。"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Dict, Optional


@dataclass
class Mt5GuiController:
    detect_window: Callable[[], bool]
    launch_terminal: Callable[[], None]
    wait_window_ready: Callable[[int], bool]
    attach_terminal: Callable[[], bool]
    read_account: Callable[[], Optional[Dict[str, str]]]
    switch_account: Callable[[str, str, str], Dict[str, Any]]
    monotonic_ms: Callable[[], int]

    def switch_account(self, login: str, password: str, server: str) -> Dict[str, Any]:
        raise NotImplementedError
```

- [ ] **Step 2: 跑测试，确认骨架仍失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_mt5_account_switch -v`

Expected: `FAIL`，提示 `NotImplementedError`。

- [ ] **Step 3: 实现窗口检测、按需拉起、15 秒窗口等待**

```python
    def _ensure_window_ready(self) -> Optional[Dict[str, Any]]:
        if self.detect_window():
            return None
        try:
            self.launch_terminal()
        except Exception as exc:
            return {
                "ok": False,
                "stage": "window_not_found_then_launch_failed",
                "message": f"未发现 MT5 主窗口，拉起失败: {exc}",
            }
        if not self.wait_window_ready(15000):
            return {
                "ok": False,
                "stage": "window_not_found_then_window_ready_timeout",
                "message": "已尝试拉起 MT5，但 15s 内主窗口未出现",
            }
        return None
```

- [ ] **Step 4: 实现附着重试和基线账号读取**

```python
    def _attach_with_retry(self) -> bool:
        if self.attach_terminal():
            return True
        for _ in range(2):
            import time
            time.sleep(3)
            if self.attach_terminal():
                return True
        return False

    def _require_account(self, stage: str) -> Dict[str, Any]:
        account = self.read_account()
        if not account or not str(account.get("login") or "").strip():
            return {
                "ok": False,
                "stage": stage,
                "message": "当前终端实际账号信息读取失败",
            }
        return account
```

- [ ] **Step 5: 实现切号调用和 30 秒轮询确认**

```python
    def switch_account(self, login: str, password: str, server: str) -> Dict[str, Any]:
        started_at = self.monotonic_ms()
        early_failure = self._ensure_window_ready()
        if early_failure is not None:
            early_failure["elapsedMs"] = self.monotonic_ms() - started_at
            return early_failure
        if not self._attach_with_retry():
            return {
                "ok": False,
                "stage": "attach_failed",
                "message": "MT5 主窗口已就绪，但附着/初始化失败",
                "elapsedMs": self.monotonic_ms() - started_at,
            }
        baseline = self._require_account("baseline_account_read_failed")
        if baseline.get("ok") is False:
            baseline["elapsedMs"] = self.monotonic_ms() - started_at
            return baseline
        try:
            login_call = self.switch_account(login, password, server)
        except Exception as exc:
            return {
                "ok": False,
                "stage": "switch_call_exception",
                "message": f"切换调用异常: {exc}",
                "elapsedMs": self.monotonic_ms() - started_at,
                "baselineAccount": baseline,
            }
        login_error = str(login_call.get("error") or "") if isinstance(login_call, dict) else ""
        import time
        last_account = baseline
        for _ in range(15):
            observed = self.read_account()
            if not observed or not str(observed.get("login") or "").strip():
                return {
                    "ok": False,
                    "stage": "final_account_read_failed",
                    "message": "轮询期间读取当前终端账号失败",
                    "elapsedMs": self.monotonic_ms() - started_at,
                    "baselineAccount": baseline,
                    "loginError": login_error,
                    "lastObservedAccount": last_account,
                }
            last_account = observed
            if str(observed.get("login") or "").strip() == str(login).strip():
                return {
                    "ok": True,
                    "stage": "switch_succeeded",
                    "message": f"切换前={baseline.get('login')} / {baseline.get('server')}；切换后={observed.get('login')} / {observed.get('server')}；耗时={self.monotonic_ms() - started_at}ms",
                    "elapsedMs": self.monotonic_ms() - started_at,
                    "baselineAccount": baseline,
                    "finalAccount": observed,
                    "loginError": login_error,
                    "lastObservedAccount": observed,
                }
            time.sleep(2)
        return {
            "ok": False,
            "stage": "switch_timeout_account_not_changed",
            "message": f"30s 内未切换到目标账号；mt5.login 返回: {login_error}；最后实际账号={last_account.get('login')} / {last_account.get('server')}",
            "elapsedMs": self.monotonic_ms() - started_at,
            "baselineAccount": baseline,
            "finalAccount": None,
            "loginError": login_error,
            "lastObservedAccount": last_account,
        }
```

- [ ] **Step 6: 跑新测试确认通过**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_mt5_account_switch -v`

Expected: `PASS`

- [ ] **Step 7: Commit**

```bash
git add bridge/mt5_gateway/v2_mt5_account_switch.py bridge/mt5_gateway/tests/test_v2_mt5_account_switch.py
git commit -m "feat: add standalone mt5 account switch flow"
```

## Task 3: 把服务端会话接口切到新主链

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_diagnostic.py`

- [ ] **Step 1: 写集成失败测试，锁定 `server_v2` 调新模块**

```python
def test_session_gateway_adapter_should_delegate_to_standalone_switch_flow(self):
    fake_flow = mock.Mock(return_value={
        "ok": True,
        "stage": "switch_succeeded",
        "message": "切换前=11111111 / Old-Server；切换后=12345678 / New-Server；耗时=8000ms",
        "elapsedMs": 8000,
        "baselineAccount": {"login": "11111111", "server": "Old-Server"},
        "finalAccount": {"login": "12345678", "server": "New-Server"},
        "loginError": "",
        "lastObservedAccount": {"login": "12345678", "server": "New-Server"},
    })
    with mock.patch.object(server_v2, "_switch_mt5_account_via_gui_flow", fake_flow):
        payload = server_v2._SessionGatewayAdapter().login_mt5(
            login="12345678",
            password="secret",
            server="New-Server",
            request_id="req-switch-1",
        )

    fake_flow.assert_called_once_with(
        login="12345678",
        password="secret",
        server="New-Server",
        request_id="req-switch-1",
        action="login",
    )
    self.assertEqual("switch_succeeded", payload["stage"])
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_contracts.V2SessionContractsTests.test_session_gateway_adapter_should_delegate_to_standalone_switch_flow -v`

Expected: `FAIL`，提示 `_switch_mt5_account_via_gui_flow` 不存在或未被调用。

- [ ] **Step 3: 在 `server_v2.py` 接入新主链并统一返回字段**

```python
from bridge.mt5_gateway import v2_mt5_account_switch


def _switch_mt5_account_via_gui_flow(*, login: str, password: str, server: str, request_id: str, action: str) -> Dict[str, Any]:
    result = v2_mt5_account_switch.run_mt5_account_switch(
        login=login,
        password=password,
        server=server,
    )
    _append_session_diagnostic_entry(
        request_id=request_id,
        action=action,
        stage=str(result.get("stage") or ""),
        status="ok" if bool(result.get("ok")) else "failed",
        message=str(result.get("message") or ""),
        server_time=_now_ms(),
        error_code="" if bool(result.get("ok")) else "SESSION_LOGIN_FAILED",
    )
    return result


class _SessionGatewayAdapter:
    def login_mt5(self, login: str, password: str, server: str, request_id: str = "") -> Dict[str, Any]:
        result = _switch_mt5_account_via_gui_flow(
            login=login,
            password=password,
            server=server,
            request_id=request_id,
            action="login",
        )
        if not bool(result.get("ok")):
            raise RuntimeError(str(result.get("message") or "MT5 账户切换失败"))
        final_account = dict(result.get("finalAccount") or {})
        _set_runtime_session_credentials(str(final_account.get("login") or login), password, str(final_account.get("server") or server))
        return result
```

- [ ] **Step 4: 在 `v2_session_login` / `v2_session_switch` 回执里保留新增字段**

```python
return {
    "ok": True,
    "state": "activated",
    "requestId": request_id,
    "activeAccount": active_account_payload,
    "message": str(result.get("message") or ""),
    "errorCode": "",
    "retryable": False,
    "stage": str(result.get("stage") or ""),
    "elapsedMs": int(result.get("elapsedMs") or 0),
    "baselineAccount": result.get("baselineAccount"),
    "finalAccount": result.get("finalAccount"),
    "loginError": str(result.get("loginError") or ""),
    "lastObservedAccount": result.get("lastObservedAccount"),
}
```

- [ ] **Step 5: 跑服务端合同测试**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_contracts bridge.mt5_gateway.tests.test_v2_session_diagnostic -v`

Expected: `PASS`

- [ ] **Step 6: Commit**

```bash
git add bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_session_contracts.py bridge/mt5_gateway/tests/test_v2_session_diagnostic.py
git commit -m "feat: wire session endpoints to mt5 switch flow"
```

## Task 4: 客户端消费新的 receipt 结果并保留可复制失败摘要

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/data/model/v2/session/SessionReceipt.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- Modify: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientTest.java`
- Modify: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/account/AccountSessionDialogControllerSourceTest.java`

- [ ] **Step 1: 给 `SessionReceipt` 扩展新字段**

```java
private final String stage;
private final long elapsedMs;
private final RemoteAccountProfile baselineAccount;
private final RemoteAccountProfile finalAccount;
private final String loginError;
private final RemoteAccountProfile lastObservedAccount;

public String getStage() { return stage; }
public long getElapsedMs() { return elapsedMs; }
public RemoteAccountProfile getBaselineAccount() { return baselineAccount; }
public RemoteAccountProfile getFinalAccount() { return finalAccount; }
public String getLoginError() { return loginError; }
public RemoteAccountProfile getLastObservedAccount() { return lastObservedAccount; }
```

- [ ] **Step 2: 写解析失败测试**

```java
@Test
public void parseSessionReceiptShouldReadSwitchFlowFields() throws Exception {
    String body = "{"
            + "\"ok\":true,"
            + "\"state\":\"activated\","
            + "\"requestId\":\"req-login-1\","
            + "\"message\":\"切换前=11111111 / Old-Server；切换后=12345678 / New-Server；耗时=8000ms\","
            + "\"stage\":\"switch_succeeded\","
            + "\"elapsedMs\":8000,"
            + "\"baselineAccount\":{\"profileId\":\"baseline\",\"login\":\"11111111\",\"server\":\"Old-Server\"},"
            + "\"finalAccount\":{\"profileId\":\"final\",\"login\":\"12345678\",\"server\":\"New-Server\"},"
            + "\"loginError\":\"\","
            + "\"lastObservedAccount\":{\"profileId\":\"last\",\"login\":\"12345678\",\"server\":\"New-Server\"}"
            + "}";

    SessionReceipt receipt = GatewayV2SessionClient.parseSessionReceipt(body);

    assertEquals("switch_succeeded", receipt.getStage());
    assertEquals(8000L, receipt.getElapsedMs());
    assertEquals("11111111", receipt.getBaselineAccount().getLogin());
    assertEquals("12345678", receipt.getFinalAccount().getLogin());
}
```

- [ ] **Step 3: 跑测试，确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientTest"`

Expected: `FAIL`，提示 `SessionReceipt` 缺少 getter 或解析未覆盖新字段。

- [ ] **Step 4: 在 `GatewayV2SessionClient.parseSessionReceipt` 解析新字段**

```java
return new SessionReceipt(
        json.optBoolean("ok", false),
        json.optString("state", ""),
        json.optString("requestId", ""),
        account,
        json.optString("message", ""),
        json.optString("errorCode", ""),
        json.optBoolean("retryable", false),
        safeBody(body),
        json.optString("stage", ""),
        json.optLong("elapsedMs", 0L),
        parseProfile(json.optJSONObject("baselineAccount")),
        parseProfile(json.optJSONObject("finalAccount")),
        json.optString("loginError", ""),
        parseProfile(json.optJSONObject("lastObservedAccount"))
);
```

- [ ] **Step 5: 在 `AccountSessionDialogController` 优先展示新摘要**

```java
private String buildStructuredFailureSummary(@NonNull SessionReceipt receipt) {
    StringBuilder builder = new StringBuilder();
    appendLine(builder, trim(receipt.getMessage()));
    appendLine(builder, "stage=" + trim(receipt.getStage()));
    if (!trim(receipt.getLoginError()).isEmpty()) {
        appendLine(builder, "loginError=" + trim(receipt.getLoginError()));
    }
    RemoteAccountProfile lastObserved = receipt.getLastObservedAccount();
    if (lastObserved != null && !trim(lastObserved.getLogin()).isEmpty()) {
        appendLine(builder, "lastObserved=" + trim(lastObserved.getLogin()) + " / " + trim(lastObserved.getServer()));
    }
    return builder.toString().trim();
}
```

- [ ] **Step 6: 跑客户端定向测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientTest" --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientSourceTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountSessionDialogControllerSourceTest"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/data/model/v2/session/SessionReceipt.java app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientTest.java app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientSourceTest.java app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java app/src/test/java/com/binance/monitor/ui/account/AccountSessionDialogControllerSourceTest.java
git commit -m "feat: parse and display mt5 switch flow receipts"
```

## Task 5: 把 APP 重启恢复链改成轻量确认 + 账号失效通知

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/util/NotificationHelper.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelperSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/util/NotificationHelperSourceTest.java`

- [ ] **Step 1: 先写恢复链失败测试，锁定“不再自动 switchSavedAccount”**

```java
@Test
public void helperShouldNotAutoSwitchSavedAccountDuringAppRestartRecovery() throws Exception {
    String source = readUtf8(
            "app/src/main/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelper.java",
            "src/main/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelper.java"
    ).replace("\r\n", "\n").replace('\r', '\n');

    assertFalse(source.contains("sessionClient.switchAccount("));
    assertTrue(source.contains("SessionStatusPayload status = sessionClient.fetchStatus();"));
}
```

- [ ] **Step 2: 跑测试，确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.account.AccountSessionRecoveryHelperSourceTest"`

Expected: `FAIL`，因为当前 helper 仍会 `switchAccount(...)`。

- [ ] **Step 3: 重写 `AccountSessionRecoveryHelper.reconcileRemoteSession()` 为轻量确认**

```java
if (!configManager.isAccountSessionActive()) {
    return RecoveryResult.NO_CHANGE;
}
SessionSummarySnapshot localSummary = secureSessionPrefs.loadSessionSummary();
RemoteAccountProfile localActiveAccount = sanitizeCompleteProfile(localSummary.getActiveAccount());
if (localActiveAccount == null) {
    return RecoveryResult.NO_CHANGE;
}
sessionClient.resetTransport();
SessionStatusPayload status = sessionClient.fetchStatus();
RemoteAccountProfile currentRemoteActiveAccount = sanitizeCompleteProfile(status.getActiveAccount());
if (currentRemoteActiveAccount != null && matchesSessionIdentity(currentRemoteActiveAccount, localActiveAccount)) {
    secureSessionPrefs.saveSession(
            currentRemoteActiveAccount,
            RemoteAccountProfileDeduplicationHelper.deduplicate(status.getSavedAccounts()),
            true
    );
    return RecoveryResult.SESSION_SUMMARY_SYNCED;
}
configManager.setAccountSessionActive(false);
secureSessionPrefs.saveSession(null, RemoteAccountProfileDeduplicationHelper.deduplicate(status.getSavedAccounts()), false);
secureSessionPrefs.saveDraftIdentity(localActiveAccount.getLogin(), localActiveAccount.getServer());
accountStatsPreloadManager.clearAccountRuntimeState(localActiveAccount.getLogin(), localActiveAccount.getServer());
return RecoveryResult.ACCOUNT_MISMATCH;
```

- [ ] **Step 4: 给 `NotificationHelper` 增加账号失效通知入口**

```java
public void notifyAccountMismatch(String login, String server) {
    String safeLogin = login == null ? "" : login.trim();
    String safeServer = server == null ? "" : server.trim();
    notifyAbnormalAlert(
            "MT5 账号已失效",
            "服务器端当前实际账号已经不是原来的账号了: " + safeLogin + " / " + safeServer,
            9101
    );
}
```

- [ ] **Step 5: 在 `MonitorService` 承接账号失效通知**

```java
AccountSessionRecoveryHelper.RecoveryResult recoveryResult =
        accountSessionRecoveryHelper.reconcileRemoteSession();
if (recoveryResult == AccountSessionRecoveryHelper.RecoveryResult.ACCOUNT_MISMATCH) {
    mainHandler.post(() -> {
        updateConnectionStatus();
        if (notificationHelper != null) {
            notificationHelper.notifyAccountMismatch("", "");
        }
        if (floatingCoordinator != null) {
            floatingCoordinator.requestRefresh(true);
        }
    });
}
```

- [ ] **Step 6: 跑恢复链和通知定向测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.account.AccountSessionRecoveryHelperSourceTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.util.NotificationHelperSourceTest"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelper.java app/src/main/java/com/binance/monitor/service/MonitorService.java app/src/main/java/com/binance/monitor/util/NotificationHelper.java app/src/test/java/com/binance/monitor/runtime/account/AccountSessionRecoveryHelperSourceTest.java app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java app/src/test/java/com/binance/monitor/util/NotificationHelperSourceTest.java
git commit -m "feat: recover account session with lightweight verification"
```

## Task 6: 总回归、部署包校验与记录同步

**Files:**
- Modify: `CONTEXT.md`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_diagnostic.py`

- [ ] **Step 1: 跑完整服务端回归**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_mt5_account_switch bridge.mt5_gateway.tests.test_v2_session_contracts bridge.mt5_gateway.tests.test_v2_session_diagnostic -v`

Expected: all `OK`

- [ ] **Step 2: 跑完整客户端定向回归**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientTest" --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientSourceTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountSessionDialogControllerSourceTest" --tests "com.binance.monitor.runtime.account.AccountSessionRecoveryHelperSourceTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.util.NotificationHelperSourceTest"`

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 重建服务器部署包**

Run: `python scripts/build_windows_server_bundle.py`

Expected: `built windows server bundle: E:\Github\BTCXAU_Monitoring_and_Push_APK\dist\windows_server_bundle`

- [ ] **Step 4: 更新 `CONTEXT.md`**

```markdown
- 2026-04-21 已按 `2026-04-21-mt5-account-switch-design.md` 完成 MT5 账户切换主链实现计划：服务端新增独立 `v2_mt5_account_switch.py`，显式登录/切号链改成“检测 GUI 主窗口 -> 必要时按 MT5_PATH 拉起 -> 附着重试 -> 读基线账号 -> 强制切号 -> 30s 轮询真实账号 login”；APP 重启恢复链改成只做轻量确认，不再自动切回原账号，发现服务器端当前实际账号变化时会落未登录并发通知。
```

- [ ] **Step 5: 最后提交**

```bash
git add CONTEXT.md docs/superpowers/plans/2026-04-21-mt5-account-switch-implementation.md
git commit -m "docs: record mt5 account switch implementation plan"
```

## 自检

### Spec coverage

- 新登录/切换账号链：Task 1-3 覆盖
- 统一结果结构：Task 3-4 覆盖
- 失败阶段真实命名：Task 1、Task 3 覆盖
- APP 重启恢复链：Task 5 覆盖
- 账号失效通知：Task 5 覆盖
- 回归与部署包：Task 6 覆盖

### Placeholder scan

- 已检查，无 `TBD / TODO / implement later / add appropriate` 之类占位符。

### Type consistency

- 服务端统一使用 `ok / stage / message / elapsedMs / baselineAccount / finalAccount / loginError / lastObservedAccount`
- 客户端 `SessionReceipt` 与服务端字段名保持一致
- APP 重启恢复链结果枚举统一新增 `ACCOUNT_MISMATCH`
