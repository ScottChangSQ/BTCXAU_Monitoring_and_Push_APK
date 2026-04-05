# Server Control Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new browser-based server control console that hides day-to-day command-line operations behind UI actions, diagnostics, and form-based configuration, while keeping all deployable files closed under a single server bundle root.

**Architecture:** Keep the current FastAPI-based admin backend as the execution layer, but split state aggregation, diagnostics, and configuration metadata into focused helper modules. Replace the current lightweight static page with a multi-section control console front end, and make the Windows deployment bundle the single closed root used by runtime, docs, and startup scripts.

**Tech Stack:** Python (FastAPI, uvicorn), vanilla HTML/CSS/JavaScript, PowerShell startup scripts, Python `unittest`

---

## File Structure

### Existing files to modify

- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel.py`
  - Keep FastAPI app and route registration here
  - Move complex state/diagnostic/config shaping into helper modules
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\static\admin\index.html`
  - Replace current single-screen panel shell with the new console layout
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\static\admin\app.js`
  - Replace current minimal page logic with section loading, action handling, config save, and diagnostics rendering
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\static\admin\styles.css`
  - Update visual system for dashboard cards, form sections, action bars, and diagnostics
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\tests\test_admin_panel.py`
  - Add unit tests for new state shaping, diagnostics, and config metadata
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\README.md`
  - Rewrite deployment instructions around the single-root bundle and new console behavior
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\admin_panel.py`
  - Keep bundle copy aligned with runtime backend
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\static\admin\index.html`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\static\admin\app.js`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\static\admin\styles.css`

### New files to create

- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel_state.py`
  - Build unified dashboard state payload
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel_diagnostics.py`
  - Translate low-level checks into Chinese conclusions and suggested actions
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel_config.py`
  - Define form schema, field grouping, and save/restart impact rules
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\tests\test_admin_panel_bundle_parity.py`
  - Guard that the bundle keeps all required runtime files in one closed root
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\admin_panel_state.py`
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\admin_panel_diagnostics.py`
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\admin_panel_config.py`

## Task 1: Extract Unified Dashboard State Helpers

**Files:**
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel_state.py`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel.py`
- Test: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\tests\test_admin_panel.py`

- [ ] **Step 1: Write the failing tests for unified overview state**

```python
def test_build_dashboard_state_should_expose_overview_cards(self):
    payload = admin_panel_state.build_dashboard_state(
        gateway_url="http://127.0.0.1:8787",
        gateway_health={"ok": True, "login": 7400048},
        gateway_source={"eaSnapshotFresh": True},
        components={
            "gateway": {"label": "MT5 网关", "state": {"running": True, "statusText": "运行中"}},
            "mt5": {"label": "MT5 客户端", "state": {"running": True, "statusText": "运行中"}},
        },
        logs=[{"file": "gateway.log", "line": "ok"}],
    )

    self.assertEqual("正常", payload["overviewCards"]["gateway"]["status"])
    self.assertEqual("运行中", payload["overviewCards"]["mt5"]["status"])
    self.assertEqual("ok", payload["recentLogs"][0]["line"])


def test_build_dashboard_state_should_include_primary_actions(self):
    payload = admin_panel_state.build_dashboard_state(
        gateway_url="http://127.0.0.1:8787",
        gateway_health={"ok": True},
        gateway_source={},
        components={},
        logs=[],
    )

    self.assertEqual(
        ["refresh", "saveAndApply", "openDiagnostics"],
        [item["key"] for item in payload["primaryActions"]],
    )
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
```

Expected: FAIL with `ImportError` or missing `build_dashboard_state`

- [ ] **Step 3: Write the minimal state helper**

```python
# bridge/mt5_gateway/admin_panel_state.py
from typing import Any, Dict, List


def build_dashboard_state(gateway_url: str,
                          gateway_health: Dict[str, Any],
                          gateway_source: Dict[str, Any],
                          components: Dict[str, Dict[str, Any]],
                          logs: List[Dict[str, Any]]) -> Dict[str, Any]:
    return {
        "gatewayUrl": gateway_url,
        "overviewCards": {
            "gateway": {
                "status": "正常" if gateway_health.get("ok") else "异常",
                "detail": "健康检查通过" if gateway_health.get("ok") else "健康检查失败",
            },
            "mt5": {
                "status": ((components.get("mt5") or {}).get("state") or {}).get("statusText", "未知"),
                "detail": "已连接" if gateway_health.get("mt5Connected") else "待确认",
            },
            "ea": {
                "status": "新鲜" if gateway_source.get("eaSnapshotFresh") else "异常",
                "detail": "EA 心跳正常" if gateway_source.get("eaSnapshotFresh") else "EA 心跳缺失",
            },
        },
        "primaryActions": [
            {"key": "refresh", "label": "一键刷新状态"},
            {"key": "saveAndApply", "label": "保存并应用配置"},
            {"key": "openDiagnostics", "label": "打开诊断中心"},
        ],
        "components": components,
        "recentLogs": logs,
    }
```

- [ ] **Step 4: Wire the helper into `admin_panel.py` and rerun tests**

```python
from admin_panel_state import build_dashboard_state


@app.get("/api/state")
def api_state() -> Dict[str, Any]:
    env_map = load_env_map()
    gateway_url = resolve_gateway_url(env_map)
    registry = build_component_registry(env_map, str(REPO_ROOT))
    components = build_component_states(registry, gateway_url)
    logs = read_recent_logs(LOGS_DIR, limit=50)
    return build_dashboard_state(
        gateway_url=gateway_url,
        gateway_health=fetch_gateway_health(gateway_url),
        gateway_source=fetch_gateway_source(gateway_url),
        components=components,
        logs=logs,
    )
```

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
```

Expected: PASS for the new tests

- [ ] **Step 5: Commit**

```bash
git add bridge/mt5_gateway/admin_panel.py bridge/mt5_gateway/admin_panel_state.py bridge/mt5_gateway/tests/test_admin_panel.py
git commit -m "feat: extract admin dashboard state builder"
```

## Task 2: Add Chinese Diagnostics and Suggested Actions

**Files:**
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel_diagnostics.py`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel.py`
- Test: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\tests\test_admin_panel.py`

- [ ] **Step 1: Write the failing diagnostics tests**

```python
def test_build_diagnostics_should_report_public_gateway_failure_in_chinese(self):
    payload = admin_panel_diagnostics.build_diagnostics_report(
        local_gateway_ok=True,
        public_gateway_ok=False,
        ea_fresh=True,
        proxy_running=False,
        suggested_app_base_url="http://43.155.214.62/mt5",
    )

    self.assertEqual("APP 连不上，不是主网关挂了，是公网入口未通过", payload["summary"])
    self.assertEqual("http://43.155.214.62/mt5", payload["suggestedAppBaseUrl"])
    self.assertEqual("失败", payload["checks"][1]["status"])


def test_build_diagnostics_should_prioritize_gateway_down_summary(self):
    payload = admin_panel_diagnostics.build_diagnostics_report(
        local_gateway_ok=False,
        public_gateway_ok=False,
        ea_fresh=False,
        proxy_running=False,
        suggested_app_base_url="http://43.155.214.62/mt5",
    )

    self.assertEqual("主网关未运行，先恢复网关再检查 APP 连通性", payload["summary"])
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
```

Expected: FAIL with missing diagnostics builder

- [ ] **Step 3: Implement minimal diagnostics rules**

```python
# bridge/mt5_gateway/admin_panel_diagnostics.py
from typing import Any, Dict, List


def build_diagnostics_report(local_gateway_ok: bool,
                             public_gateway_ok: bool,
                             ea_fresh: bool,
                             proxy_running: bool,
                             suggested_app_base_url: str) -> Dict[str, Any]:
    if not local_gateway_ok:
        summary = "主网关未运行，先恢复网关再检查 APP 连通性"
    elif local_gateway_ok and not public_gateway_ok:
        summary = "APP 连不上，不是主网关挂了，是公网入口未通过"
    elif not ea_fresh:
        summary = "EA 心跳异常，当前数据源可能已经回退"
    else:
        summary = "连接正常"

    checks: List[Dict[str, Any]] = [
        {"key": "localGateway", "label": "本机网关检查", "status": "通过" if local_gateway_ok else "失败"},
        {"key": "publicGateway", "label": "公网网关检查", "status": "通过" if public_gateway_ok else "失败"},
        {"key": "proxy", "label": "反向代理检查", "status": "通过" if proxy_running else "未配置"},
        {"key": "ea", "label": "EA 心跳检查", "status": "通过" if ea_fresh else "失败"},
    ]
    return {
        "summary": summary,
        "suggestedAppBaseUrl": suggested_app_base_url,
        "checks": checks,
        "suggestedActions": [
            {"key": "recheck", "label": "一键诊断"},
            {"key": "copyAppUrl", "label": "复制建议地址"},
        ],
    }
```

- [ ] **Step 4: Add `/api/diagnostics` and rerun tests**

```python
from admin_panel_diagnostics import build_diagnostics_report


@app.get("/api/diagnostics")
def api_diagnostics() -> Dict[str, Any]:
    env_map = load_env_map()
    gateway_url = resolve_gateway_url(env_map)
    gateway_health = fetch_gateway_health(gateway_url)
    gateway_source = fetch_gateway_source(gateway_url)
    return build_diagnostics_report(
        local_gateway_ok=bool(gateway_health.get("ok")),
        public_gateway_ok=check_public_gateway_url(env_map),
        ea_fresh=bool(gateway_source.get("eaSnapshotFresh")),
        proxy_running=is_proxy_running(load_env_map(), str(REPO_ROOT)),
        suggested_app_base_url=resolve_suggested_app_base_url(load_env_map()),
    )
```

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
```

Expected: PASS for diagnostics tests

- [ ] **Step 5: Commit**

```bash
git add bridge/mt5_gateway/admin_panel.py bridge/mt5_gateway/admin_panel_diagnostics.py bridge/mt5_gateway/tests/test_admin_panel.py
git commit -m "feat: add admin diagnostics summary API"
```

## Task 3: Add Form-Based Config Metadata and Restart Impact Rules

**Files:**
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel_config.py`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\admin_panel.py`
- Test: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\tests\test_admin_panel.py`

- [ ] **Step 1: Write the failing config metadata tests**

```python
def test_build_config_schema_should_group_fields_in_chinese_sections(self):
    schema = admin_panel_config.build_config_schema()

    self.assertEqual("MT5 账户", schema[0]["title"])
    self.assertEqual("网关与入口", schema[1]["title"])
    self.assertEqual("MT5_LOGIN", schema[0]["fields"][0]["envKey"])


def test_diff_config_changes_should_report_restart_impacts(self):
    result = admin_panel_config.diff_config_changes(
        before={"GATEWAY_PORT": "8787", "MT5_LOGIN": "7400048"},
        after={"GATEWAY_PORT": "9797", "MT5_LOGIN": "7400048"},
    )

    self.assertEqual(["主网关"], result["needRestart"])
    self.assertEqual(["GATEWAY_PORT"], [item["envKey"] for item in result["changedFields"]])
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
```

Expected: FAIL with missing config schema helpers

- [ ] **Step 3: Implement minimal config schema and diff helper**

```python
# bridge/mt5_gateway/admin_panel_config.py
from typing import Dict, List, Any


def build_config_schema() -> List[Dict[str, Any]]:
    return [
        {
            "key": "mt5",
            "title": "MT5 账户",
            "fields": [
                {"envKey": "MT5_LOGIN", "label": "MT5 账号"},
                {"envKey": "MT5_PASSWORD", "label": "投资者密码"},
                {"envKey": "MT5_SERVER", "label": "券商服务器"},
            ],
        },
        {
            "key": "gateway",
            "title": "网关与入口",
            "fields": [
                {"envKey": "GATEWAY_HOST", "label": "网关监听地址"},
                {"envKey": "GATEWAY_PORT", "label": "网关端口"},
                {"envKey": "ADMIN_GATEWAY_URL", "label": "建议 APP 地址"},
            ],
        },
    ]


def diff_config_changes(before: Dict[str, str], after: Dict[str, str]) -> Dict[str, Any]:
    changed = []
    for key, new_value in after.items():
        if before.get(key) != new_value:
            changed.append({"envKey": key, "before": before.get(key, ""), "after": new_value})
    need_restart = []
    if any(item["envKey"].startswith("GATEWAY_") or item["envKey"] == "ADMIN_GATEWAY_URL" for item in changed):
        need_restart.append("主网关")
    if any(item["envKey"].startswith("MT5_") for item in changed):
        need_restart.append("MT5 客户端")
    return {"changedFields": changed, "needRestart": need_restart}
```

- [ ] **Step 4: Add schema and save-impact APIs, then rerun tests**

```python
from admin_panel_config import build_config_schema, diff_config_changes


@app.get("/api/config/schema")
def api_config_schema() -> Dict[str, Any]:
    return {"sections": build_config_schema()}


@app.post("/api/env")
def api_save_env(payload: Dict[str, Any]) -> Dict[str, Any]:
    previous = load_env_map()
    content = str(payload.get("content") or "")
    write_text_file_utf8(ENV_PATH, content)
    current = parse_env_map(content)
    diff = diff_config_changes(previous, current)
    return {"ok": True, "path": str(ENV_PATH), "changes": diff}
```

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
```

Expected: PASS for config metadata tests

- [ ] **Step 5: Commit**

```bash
git add bridge/mt5_gateway/admin_panel.py bridge/mt5_gateway/admin_panel_config.py bridge/mt5_gateway/tests/test_admin_panel.py
git commit -m "feat: add admin config schema and restart impact rules"
```

## Task 4: Replace Lightweight Static Page with Full Console UI

**Files:**
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\static\admin\index.html`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\static\admin\app.js`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\static\admin\styles.css`
- Test: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\tests\test_admin_panel.py`

- [ ] **Step 1: Write the failing source-level UI tests**

```python
def test_console_html_should_include_overview_controls_and_diagnostics_entry(self):
    html = (ROOT / "static" / "admin" / "index.html").read_text(encoding="utf-8")

    self.assertIn("MT5 服务器控制台", html)
    self.assertIn("配置中心", html)
    self.assertIn("诊断中心", html)


def test_console_js_should_request_new_console_endpoints(self):
    js = (ROOT / "static" / "admin" / "app.js").read_text(encoding="utf-8")

    self.assertIn("/api/diagnostics", js)
    self.assertIn("/api/config/schema", js)
    self.assertIn("saveAndApply", js)
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
```

Expected: FAIL because the current static assets do not contain the new console sections

- [ ] **Step 3: Replace the HTML shell with console sections**

```html
<main class="page">
  <aside class="sidebar">
    <div class="brand">MT5 服务器控制台</div>
    <button data-section="overview">总览</button>
    <button data-section="components">组件控制</button>
    <button data-section="config">配置中心</button>
    <button data-section="diagnostics">诊断中心</button>
    <button data-section="logs">日志与历史</button>
  </aside>
  <section class="content">
    <header class="topbar">
      <button id="refreshAllBtn">一键刷新状态</button>
      <button id="saveApplyBtn">保存并应用配置</button>
      <span id="globalNotice"></span>
    </header>
    <section id="overviewSection"></section>
    <section id="componentSection" hidden></section>
    <section id="configSection" hidden></section>
    <section id="diagnosticsSection" hidden></section>
    <section id="logsSection" hidden></section>
  </section>
</main>
```

- [ ] **Step 4: Implement JS section rendering and rerun tests**

```javascript
async function refreshAll() {
  const [state, diagnostics, schema] = await Promise.all([
    requestJson('/api/state'),
    requestJson('/api/diagnostics'),
    requestJson('/api/config/schema')
  ]);
  renderOverview(state);
  renderComponents(state.components || {});
  renderDiagnostics(diagnostics);
  renderConfigSchema(schema.sections || []);
}

document.getElementById('saveApplyBtn').addEventListener('click', async () => {
  await saveEnv();
  await executeProcessAction('gateway', 'restart');
  await refreshAll();
});
```

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
```

Expected: PASS for the new source-level UI tests

- [ ] **Step 5: Commit**

```bash
git add bridge/mt5_gateway/static/admin/index.html bridge/mt5_gateway/static/admin/app.js bridge/mt5_gateway/static/admin/styles.css bridge/mt5_gateway/tests/test_admin_panel.py
git commit -m "feat: replace lightweight admin page with full control console"
```

## Task 5: Make the Windows Bundle a Single Closed Deployment Root

**Files:**
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\bridge\mt5_gateway\tests\test_admin_panel_bundle_parity.py`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\admin_panel.py`
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\admin_panel_state.py`
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\admin_panel_diagnostics.py`
- Create: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\admin_panel_config.py`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\static\admin\index.html`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\static\admin\app.js`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\mt5_gateway\static\admin\styles.css`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\deploy\tencent\windows_server_bundle\README.md`

- [ ] **Step 1: Write the failing bundle parity test**

```python
def test_windows_bundle_should_include_console_runtime_files(self):
    bundle_root = ROOT.parent / "deploy" / "tencent" / "windows_server_bundle" / "mt5_gateway"
    required = [
        "admin_panel.py",
        "admin_panel_state.py",
        "admin_panel_diagnostics.py",
        "admin_panel_config.py",
        "server_v2.py",
        "v2_account.py",
        "v2_market.py",
        "static/admin/index.html",
        "static/admin/app.js",
        "static/admin/styles.css",
    ]

    missing = [path for path in required if not (bundle_root / path).exists()]
    self.assertEqual([], missing)
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel_bundle_parity -v
```

Expected: FAIL with missing console helper files in the bundle

- [ ] **Step 3: Copy runtime files into the bundle and rewrite bundle docs**

```text
Copy source runtime files:
- bridge/mt5_gateway/admin_panel.py
- bridge/mt5_gateway/admin_panel_state.py
- bridge/mt5_gateway/admin_panel_diagnostics.py
- bridge/mt5_gateway/admin_panel_config.py
- bridge/mt5_gateway/server_v2.py
- bridge/mt5_gateway/v2_account.py
- bridge/mt5_gateway/v2_market.py
- bridge/mt5_gateway/static/admin/index.html
- bridge/mt5_gateway/static/admin/app.js
- bridge/mt5_gateway/static/admin/styles.css

Update bundle README summary:
- deployment root is always C:\mt5_bundle
- all runtime files are closed under that root
- upload by replacing the full bundle, not cherry-picking individual files
```

- [ ] **Step 4: Run parity and admin tests**

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel_bundle_parity -v
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
.\.venv\Scripts\python.exe -m py_compile bridge/mt5_gateway/admin_panel.py
```

Expected: PASS, PASS, no compile errors

- [ ] **Step 5: Commit**

```bash
git add bridge/mt5_gateway/tests/test_admin_panel_bundle_parity.py deploy/tencent/windows_server_bundle
git commit -m "feat: align windows bundle with server control console runtime"
```

## Task 6: Final Verification and Documentation Sync

**Files:**
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\README.md`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\ARCHITECTURE.md`
- Modify: `E:\Github\BTCXAU_Monitoring_and_Push_APK\CONTEXT.md`

- [ ] **Step 1: Update top-level docs for the new console**

```markdown
- 新增“服务器唯一运维控制台”
- 首次人工初始化后，日常运维全部通过 Web 控制台完成
- 部署文件统一收口到 `C:\mt5_bundle`
```

- [ ] **Step 2: Run the final verification commands**

Run:

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel_bundle_parity -v
.\.venv\Scripts\python.exe -m py_compile bridge/mt5_gateway/admin_panel.py
.\.venv\Scripts\python.exe -m py_compile bridge/mt5_gateway/admin_panel_state.py
.\.venv\Scripts\python.exe -m py_compile bridge/mt5_gateway/admin_panel_diagnostics.py
.\.venv\Scripts\python.exe -m py_compile bridge/mt5_gateway/admin_panel_config.py
```

Expected: all tests PASS, all `py_compile` commands succeed with no output

- [ ] **Step 3: Smoke-check the rendered console locally**

Run:

```bash
cd bridge/mt5_gateway
.\start_admin_panel.ps1
```

Then verify in browser:

```text
http://127.0.0.1:8788
```

Expected:
- Sidebar shows 5 sections
- Overview shows Chinese status cards
- Diagnostics shows Chinese summary and suggested APP address
- Config screen uses white text in inputs

- [ ] **Step 4: Sync `CONTEXT.md` with the implementation stop point**

```markdown
- 新控制台第一阶段已完成
- 单文件夹部署包已闭合
- 下一步若继续扩展，优先做自动修复和更完整的 EA 就绪检查
```

- [ ] **Step 5: Commit**

```bash
git add README.md ARCHITECTURE.md CONTEXT.md
git commit -m "docs: document server control console rollout"
```

## Self-Review

### Spec coverage

- 总览页：Task 1 + Task 4
- 组件控制页：Task 1 + Task 4
- 配置中心：Task 3 + Task 4
- 诊断中心：Task 2 + Task 4
- 日志与历史：Task 1 + Task 4
- 首次初始化向导：Task 4 + Task 6 smoke-check/docs
- 单文件夹部署：Task 5

No spec section is currently uncovered.

### Placeholder scan

- No `TODO` / `TBD`
- All code-changing steps include concrete snippets
- All verification steps include concrete commands

### Type consistency

- `build_dashboard_state`, `build_diagnostics_report`, `build_config_schema`, and `diff_config_changes` are defined before later tasks reference them
- `/api/state`, `/api/diagnostics`, and `/api/config/schema` match the front-end task references

