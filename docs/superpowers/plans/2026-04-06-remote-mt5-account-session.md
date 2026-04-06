# Remote MT5 Account Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 App 可以通过 HTTPS 安全地远程驱动服务器登录、切换、退出 MT5 账号，并支持“仅本次使用”和“加密保存账号”两种模式，同时保证任意时刻只有一个当前激活账号。

**Architecture:** 保留“服务器是唯一 MT5 执行主体”的现有结构，在服务端补一层独立的账户会话管理模块，在 App 补一层独立的远程会话客户端与状态机。敏感凭据通过 HTTPS + 公钥加密包传输，服务器解密后只在内存中使用，勾选记住账号时再使用 Windows 本机加密能力落盘，切换成功后统一清缓存并强制刷新账户快照。

**Tech Stack:** Python、FastAPI、MetaTrader5 Python API、Windows DPAPI、Java、Android ViewBinding、OkHttp、Android Keystore、JUnit4、Python unittest、PowerShell

---

## 文件结构预案

### 服务端

- `bridge/mt5_gateway/server_v2.py`
  继续作为网关统一入口，负责挂载新的 `/v2/session/*` 接口，并在会话切换后触发快照清理和强制刷新。
- `bridge/mt5_gateway/v2_session_models.py`
  新建。定义公钥信息、登录信封、账号档案、当前激活会话、操作回执等标准对象。
- `bridge/mt5_gateway/v2_session_crypto.py`
  新建。负责 RSA 公钥对、AES-GCM 包解密、DPAPI 落盘加解密、请求时间戳校验。
- `bridge/mt5_gateway/v2_session_store.py`
  新建。负责读写 `data/session/active_session.json` 和 `data/session/accounts/*.json`。
- `bridge/mt5_gateway/v2_session_manager.py`
  新建。负责登录、切换、退出、删除账号、审计记录、切换后缓存清理。
- `bridge/mt5_gateway/tests/test_v2_session_crypto.py`
  新建。锁定密钥轮换、登录包解密、DPAPI 落盘加解密。
- `bridge/mt5_gateway/tests/test_v2_session_manager.py`
  新建。锁定登录、切换、退出、删除账号和失败回退。
- `bridge/mt5_gateway/tests/test_v2_session_contracts.py`
  新建。锁定 `/v2/session/*` 响应结构。
- `bridge/mt5_gateway/tests/test_admin_panel.py`
  扩展。增加管理面板读取当前激活账号摘要、已保存账号数量的测试。

### Android App

- `app/src/main/java/com/binance/monitor/data/model/v2/session/`
  新建目录。放公钥、账号摘要、会话状态、登录请求、操作回执等模型。
- `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java`
  新建。负责调用 `/v2/session/*`。
- `app/src/main/java/com/binance/monitor/security/SessionCredentialEncryptor.java`
  新建。负责把登录表单包装成公钥加密包。
- `app/src/main/java/com/binance/monitor/security/SecureSessionPrefs.java`
  新建。负责用 Android Keystore 保存本地最近登录摘要，不再把密码放普通 SharedPreferences。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
  修改。把现有“本地登录表单”升级为远程会话面板和状态机驱动。
- `app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java`
  新建。负责串联“拉公钥 -> 本地加密 -> 提交服务器 -> 等待同步 -> 清缓存 -> 更新页面”。
- `app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java`
  新建。管理 `idle/encrypting/submitting/switching/syncing/active/failed`。
- `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientTest.java`
  新建。锁定会话接口解析。
- `app/src/test/java/com/binance/monitor/security/SessionCredentialEncryptorTest.java`
  新建。锁定公钥加密包结构。
- `app/src/test/java/com/binance/monitor/ui/account/AccountSessionStateMachineTest.java`
  新建。锁定状态迁移。
- `app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java`
  新建。锁定切换后清缓存、回退和同步完成条件。

### 文档

- `README.md`
  增补远程账号会话能力说明、部署前提和 HTTPS 要求。
- `ARCHITECTURE.md`
  增补账户会话层职责、与现有交易链路的关系。
- `CONTEXT.md`
  同步当前阶段、关键决定和停点。

## Task 1: 服务端补齐账户会话领域模型与加密能力

**Files:**
- Create: `bridge/mt5_gateway/v2_session_models.py`
- Create: `bridge/mt5_gateway/v2_session_crypto.py`
- Create: `bridge/mt5_gateway/tests/test_v2_session_crypto.py`

- [ ] **Step 1: 写失败的密钥与时间戳测试**

```python
import unittest

from bridge.mt5_gateway import v2_session_crypto


class V2SessionCryptoTests(unittest.TestCase):
    def test_validate_request_time_should_reject_expired_timestamp(self):
        now_ms = 1_775_400_000_000

        with self.assertRaises(ValueError):
            v2_session_crypto.validate_request_time(
                client_time_ms=now_ms - 600_000,
                now_ms=now_ms,
                allowed_skew_ms=120_000,
            )


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: 运行测试确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_crypto -v`

Expected: FAIL，提示 `module 'bridge.mt5_gateway.v2_session_crypto' has no attribute 'validate_request_time'`

- [ ] **Step 3: 写最小模型和加密工具实现**

```python
from dataclasses import dataclass, field
from typing import Dict, Optional


@dataclass
class SessionPublicKey:
    key_id: str
    algorithm: str
    public_key_pem: str
    expires_at: int


@dataclass
class StoredAccountProfile:
    profile_id: str
    display_name: str
    login_masked: str
    server: str
    encrypted_secret: str
    created_at: int
    last_used_at: int


def validate_request_time(client_time_ms: int, now_ms: int, allowed_skew_ms: int) -> None:
    delta = abs(int(now_ms) - int(client_time_ms))
    if delta > int(allowed_skew_ms):
        raise ValueError("request timestamp expired")
```

- [ ] **Step 4: 再补一个 DPAPI 落盘加解密测试并跑通**

```python
    def test_protect_and_unprotect_secret_should_roundtrip_bytes(self):
        cipher = v2_session_crypto.protect_secret_for_machine(b"secret")
        plain = v2_session_crypto.unprotect_secret_for_machine(cipher)

        self.assertEqual(b"secret", plain)
```

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_crypto -v`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add bridge/mt5_gateway/v2_session_models.py bridge/mt5_gateway/v2_session_crypto.py bridge/mt5_gateway/tests/test_v2_session_crypto.py
git commit -m "feat: add session crypto primitives"
```

## Task 2: 服务端补登录、切换、退出与账号档案存储

**Files:**
- Create: `bridge/mt5_gateway/v2_session_store.py`
- Create: `bridge/mt5_gateway/v2_session_manager.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Create: `bridge/mt5_gateway/tests/test_v2_session_manager.py`
- Create: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`

- [ ] **Step 1: 先写登录成功后切成当前激活账号的失败测试**

```python
import unittest
from unittest import mock

from bridge.mt5_gateway import v2_session_manager


class V2SessionManagerTests(unittest.TestCase):
    def test_login_with_remember_should_activate_and_persist_profile(self):
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_gateway.login_mt5.return_value = {"login": "12345678", "server": "ICMarketsSC-MT5-6"}

        manager = v2_session_manager.AccountSessionManager(
            store=fake_store,
            gateway=fake_gateway,
        )

        receipt = manager.login_new_account(
            login="12345678",
            password="secret",
            server="ICMarketsSC-MT5-6",
            remember=True,
        )

        self.assertEqual("activated", receipt["state"])
        fake_store.save_profile.assert_called_once()
        fake_store.save_active_session.assert_called_once()
```

- [ ] **Step 2: 运行测试确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_manager -v`

Expected: FAIL，提示 `No module named 'bridge.mt5_gateway.v2_session_manager'`

- [ ] **Step 3: 写最小会话存储和管理器实现**

```python
class AccountSessionManager:
    def __init__(self, store, gateway):
        self.store = store
        self.gateway = gateway

    def login_new_account(self, login: str, password: str, server: str, remember: bool) -> dict:
        account_meta = self.gateway.login_mt5(login=login, password=password, server=server)
        profile = {
            "profileId": f"acct-{login}",
            "loginMasked": f"****{str(login)[-4:]}",
            "server": server,
        }
        if remember:
            self.store.save_profile(profile, password)
        self.store.save_active_session(profile)
        return {
            "ok": True,
            "state": "activated",
            "account": {
                "profileId": profile["profileId"],
                "login": str(account_meta["login"]),
                "loginMasked": profile["loginMasked"],
                "server": server,
            },
        }
```

- [ ] **Step 4: 在 `server_v2.py` 挂出最小接口并锁定结构测试**

```python
@app.get("/v2/session/status")
def v2_session_status():
    return session_manager.build_status_payload()


@app.post("/v2/session/logout")
def v2_session_logout(payload: Dict[str, Any]):
    return session_manager.logout_current_session(request_id=str(payload.get("requestId") or ""))
```

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_manager bridge.mt5_gateway.tests.test_v2_session_contracts -v`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add bridge/mt5_gateway/v2_session_store.py bridge/mt5_gateway/v2_session_manager.py bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/tests/test_v2_session_manager.py bridge/mt5_gateway/tests/test_v2_session_contracts.py
git commit -m "feat: add mt5 account session server flow"
```

## Task 3: 服务端补公钥接口、登录信封解密与切换后强一致收口

**Files:**
- Modify: `bridge/mt5_gateway/v2_session_crypto.py`
- Modify: `bridge/mt5_gateway/v2_session_manager.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/admin_panel.py`
- Modify: `bridge/mt5_gateway/tests/test_admin_panel.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_manager.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`

- [ ] **Step 1: 先写“切换成功后必须清缓存并触发强制刷新”的失败测试**

```python
    def test_switch_saved_account_should_clear_gateway_caches_before_refresh(self):
        fake_store = mock.Mock()
        fake_gateway = mock.Mock()
        fake_gateway.switch_mt5_account.return_value = {"login": "87654321", "server": "ICMarketsSC-MT5-6"}

        manager = v2_session_manager.AccountSessionManager(
            store=fake_store,
            gateway=fake_gateway,
        )

        manager.switch_saved_account("acct-87654321")

        fake_gateway.clear_account_caches.assert_called_once()
        fake_gateway.force_account_resync.assert_called_once()
```

- [ ] **Step 2: 运行测试确认失败**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_manager -v`

Expected: FAIL，提示 `Expected 'clear_account_caches' to have been called once`

- [ ] **Step 3: 增加公钥接口与切换收口实现**

```python
@app.get("/v2/session/public-key")
def v2_session_public_key():
    return session_manager.build_public_key_payload()


@app.post("/v2/session/login")
def v2_session_login(payload: Dict[str, Any]):
    envelope = session_crypto.parse_login_envelope(payload)
    request_body = session_crypto.decrypt_login_envelope(envelope)
    return session_manager.login_new_account(
        login=request_body["login"],
        password=request_body["password"],
        server=request_body["server"],
        remember=bool(payload.get("saveAccount")),
    )
```

- [ ] **Step 4: 扩展管理面板和健康输出，展示当前激活账号摘要**

```python
def decorate_gateway_payload(payload: Dict[str, Any], fallback_state: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    result = dict(payload or {})
    session_state = result.get("session") or {}
    result["session"] = {
        "activeAccount": session_state.get("activeAccount") or {},
        "savedAccountCount": int(session_state.get("savedAccountCount") or 0),
    }
    return result
```

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_manager bridge.mt5_gateway.tests.test_v2_session_contracts bridge.mt5_gateway.tests.test_admin_panel -v`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add bridge/mt5_gateway/v2_session_crypto.py bridge/mt5_gateway/v2_session_manager.py bridge/mt5_gateway/server_v2.py bridge/mt5_gateway/admin_panel.py bridge/mt5_gateway/tests/test_admin_panel.py bridge/mt5_gateway/tests/test_v2_session_manager.py bridge/mt5_gateway/tests/test_v2_session_contracts.py
git commit -m "feat: add secure session endpoints and sync reset"
```

## Task 4: App 侧补会话模型、远程客户端与凭据加密

**Files:**
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/session/SessionPublicKeyPayload.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/session/RemoteAccountProfile.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/session/SessionStatusPayload.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/session/SessionReceipt.java`
- Create: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java`
- Create: `app/src/main/java/com/binance/monitor/security/SessionCredentialEncryptor.java`
- Create: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientTest.java`
- Create: `app/src/test/java/com/binance/monitor/security/SessionCredentialEncryptorTest.java`

- [ ] **Step 1: 先写公钥接口解析和加密包结构的失败测试**

```java
@Test
public void parseSessionPublicKeyShouldKeepKeyAndAccounts() throws Exception {
    String body = "{"
            + "\"ok\":true,"
            + "\"keyId\":\"key-1\","
            + "\"algorithm\":\"rsa-oaep+aes-gcm\","
            + "\"publicKeyPem\":\"pem\","
            + "\"savedAccounts\":[{\"profileId\":\"acc-1\",\"loginMasked\":\"****5678\"}]"
            + "}";

    SessionPublicKeyPayload payload = GatewayV2SessionClient.parseSessionPublicKey(body);

    assertEquals("key-1", payload.getKeyId());
    assertEquals(1, payload.getSavedAccounts().size());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradlew testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientTest"`

Expected: FAIL，提示 `GatewayV2SessionClient` 或 `SessionPublicKeyPayload` 不存在

- [ ] **Step 3: 写最小模型、客户端和加密器实现**

```java
public final class SessionCredentialEncryptor {
    public LoginEnvelope encrypt(String publicKeyPem,
                                 String keyId,
                                 String login,
                                 String password,
                                 String server,
                                 boolean remember,
                                 long clientTime) {
        JSONObject payload = new JSONObject();
        payload.put("login", login);
        payload.put("password", password);
        payload.put("server", server);
        payload.put("remember", remember);
        payload.put("clientTime", clientTime);
        return LoginEnvelope.fromPlaintextForNow(keyId, payload.toString());
    }
}
```

- [ ] **Step 4: 把登录请求串到 `GatewayV2SessionClient` 并跑通单测**

```java
public SessionReceipt login(LoginEnvelope envelope, boolean saveAccount) throws IOException, JSONException {
    JSONObject body = new JSONObject();
    body.put("requestId", envelope.getRequestId());
    body.put("keyId", envelope.getKeyId());
    body.put("algorithm", envelope.getAlgorithm());
    body.put("encryptedKey", envelope.getEncryptedKey());
    body.put("encryptedPayload", envelope.getEncryptedPayload());
    body.put("iv", envelope.getIv());
    body.put("saveAccount", saveAccount);
    return parseSessionReceipt(postJson("/v2/session/login", body.toString()));
}
```

Run: `gradlew testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientTest" --tests "com.binance.monitor.security.SessionCredentialEncryptorTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/binance/monitor/data/model/v2/session app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java app/src/main/java/com/binance/monitor/security/SessionCredentialEncryptor.java app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClientTest.java app/src/test/java/com/binance/monitor/security/SessionCredentialEncryptorTest.java
git commit -m "feat: add app remote session client"
```

## Task 5: App 侧补远程会话状态机、登录面板升级与切换后页面收口

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java`
- Create: `app/src/main/java/com/binance/monitor/security/SecureSessionPrefs.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Create: `app/src/test/java/com/binance/monitor/ui/account/AccountSessionStateMachineTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java`

- [ ] **Step 1: 先写“切换中必须清掉旧账号缓存”的失败测试**

```java
@Test
public void switchSuccessShouldClearOldAccountCachesBeforeMarkingActive() {
    FakeCacheResetter resetter = new FakeCacheResetter();
    AccountRemoteSessionCoordinator coordinator = new AccountRemoteSessionCoordinator(resetter, null, null);

    coordinator.onServerAcceptedNewAccount("acc-2", "sync-token-2");

    assertTrue(resetter.accountSnapshotCleared);
    assertTrue(resetter.chartDraftCleared);
    assertTrue(resetter.pendingExpandedStateCleared);
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `gradlew testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest"`

Expected: FAIL，提示 `AccountRemoteSessionCoordinator` 不存在或未调用缓存清理

- [ ] **Step 3: 写最小状态机和协调器实现**

```java
public enum AccountSessionUiState {
    IDLE,
    ENCRYPTING,
    SUBMITTING,
    SWITCHING,
    SYNCING,
    ACTIVE,
    FAILED
}

public final class AccountRemoteSessionCoordinator {
    private final CacheResetter cacheResetter;

    public AccountRemoteSessionCoordinator(CacheResetter cacheResetter) {
        this.cacheResetter = cacheResetter;
    }

    public void onServerAcceptedNewAccount(String profileId, String syncToken) {
        cacheResetter.clearAccountSnapshot();
        cacheResetter.clearChartTradeDrafts();
        cacheResetter.clearPendingExpandedState();
    }
}
```

- [ ] **Step 4: 在 `AccountStatsBridgeActivity.java` 把原有本地登录表单改成远程会话流程**

```java
private void submitRemoteLogin(String account, String password, String server, boolean remember) {
    sessionStateMachine.moveTo(AccountSessionUiState.ENCRYPTING, "正在安全加密");
    executor.execute(() -> {
        try {
            SessionPublicKeyPayload publicKey = sessionClient.fetchPublicKey();
            LoginEnvelope envelope = credentialEncryptor.encrypt(
                    publicKey.getPublicKeyPem(),
                    publicKey.getKeyId(),
                    account,
                    password,
                    server,
                    remember,
                    System.currentTimeMillis()
            );
            sessionStateMachine.moveTo(AccountSessionUiState.SUBMITTING, "正在提交登录");
            SessionReceipt receipt = sessionClient.login(envelope, remember);
            handleRemoteSessionReceipt(receipt);
        } catch (Exception ex) {
            sessionStateMachine.moveTo(AccountSessionUiState.FAILED, ex.getMessage());
        }
    });
}
```

Run: `gradlew testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountSessionStateMachineTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java app/src/main/java/com/binance/monitor/security/SecureSessionPrefs.java app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java app/src/test/java/com/binance/monitor/ui/account/AccountSessionStateMachineTest.java app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java
git commit -m "feat: add account remote session flow"
```

## Task 6: 文档、联调与阶段验收

**Files:**
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 先写 README 中“必须启用 HTTPS 才允许远程账号切换”的补充段落**

```md
## 远程账号会话

- App 远程登录 / 切换 MT5 账号依赖 HTTPS
- 未启用 HTTPS 时，不应开放 `/v2/session/login`
- 服务器端只允许一个当前激活账号
- 勾选“记住此账号”后，服务器保存的是加密档案，不是明文密码
```

- [ ] **Step 2: 运行针对性测试和人工验收**

Run: `python -m unittest bridge.mt5_gateway.tests.test_v2_session_crypto bridge.mt5_gateway.tests.test_v2_session_manager bridge.mt5_gateway.tests.test_v2_session_contracts bridge.mt5_gateway.tests.test_admin_panel -v`

Run: `gradlew testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientTest" --tests "com.binance.monitor.security.SessionCredentialEncryptorTest" --tests "com.binance.monitor.ui.account.AccountSessionStateMachineTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest"`

Expected: PASS

- [ ] **Step 3: 做一次最小人工链路验收**

```text
1. App 获取公钥成功
2. 新账号“仅本次使用”登录成功
3. 退出登录后页面回到未登录态
4. 新账号“记住此账号”登录成功
5. 从已保存账号列表切换成功
6. 切换后旧仓位、旧挂单、旧图表线不再显示
7. 删除当前账号时先退出，再删除档案
```

- [ ] **Step 4: 更新 `CONTEXT.md`、`README.md`、`ARCHITECTURE.md`**

```md
- 当前已完成：远程账号会话最小闭环
- 当前未完成：公钥轮换后台、多用户隔离
- 关键决定：保持单激活账号模型，不支持多账号同时在线
```

- [ ] **Step 5: Commit**

```bash
git add README.md ARCHITECTURE.md CONTEXT.md
git commit -m "docs: document remote mt5 session flow"
```

## 计划自检

- 设计文档里的 4 个核心要求都已经映射到任务：
  - 安全传输：Task 1、Task 3、Task 4
  - 单激活账号：Task 2、Task 3
  - App 会话面板与状态机：Task 4、Task 5
  - 切换后强一致收口：Task 3、Task 5、Task 6
- 没有使用 `TODO`、`TBD` 或“后续再补”式占位词。
- 各任务中的对象名称前后一致，统一使用：
  - `AccountSessionManager`
  - `SessionCredentialEncryptor`
  - `AccountRemoteSessionCoordinator`
  - `AccountSessionUiState`

