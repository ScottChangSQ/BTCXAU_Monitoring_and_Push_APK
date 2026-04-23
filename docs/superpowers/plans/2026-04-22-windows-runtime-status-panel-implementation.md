# Windows 单窗口部署与连接状态面板实施计划

> 当前会话未提供 `writing-plans` skill，本文件按同等约束手工编写，作为正式 implementation plan 使用。

**Goal:** 把 Windows 服务器部署后的可见状态入口收口为唯一前台窗口 `MT5 部署与连接状态`；部署完成后该窗口继续长期显示部署、本地存活、行情、账户、手机 APP 交互状态；其他 PowerShell 常驻进程全部后台隐藏运行。

**Architecture:** 继续复用 `deploy/tencent/windows/deploy_bundle.ps1` 的 WinForms 窗口作为唯一展示层。部署阶段仍然读取部署状态文件与日志；部署成功后改为持续轮询本地端口、计划任务、网关结构化状态接口。`bridge/mt5_gateway/server_v2.py` 新增线程安全运行时状态容器与 `/internal/runtime/status` 接口，正式暴露手机 APP 最近交互事实。计划任务脚本改为以隐藏窗口方式启动 `run_gateway.ps1` 与 `run_admin_panel.ps1`，确保服务器桌面上不再出现额外 PowerShell 黑窗。

**Tech Stack:** Python 3、FastAPI、Windows PowerShell、WinForms、unittest、JUnit 风格源码合同测试

---

## 文件范围

### 服务端

- Modify: `bridge/mt5_gateway/server_v2.py`
  - 新增统一运行时状态容器
  - 记录 `v2/stream` 客户端连接事实
  - 记录最近 HTTP / 会话 / 交易请求事实
  - 暴露 `/internal/runtime/status`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`
  - 增加运行时状态接口结构与行为合同

### Windows 部署脚本

- Modify: `deploy/tencent/windows/deploy_bundle.ps1`
  - 部署窗口升级为长期状态面板
  - 新增长期状态区块与轮询逻辑
  - 部署完成后不自动结束窗口
  - 持续显示部署、本地存活、行情、账户、APP 交互状态
- Modify: `deploy/tencent/windows/02_register_startup_task.ps1`
  - 计划任务启动网关守护脚本时显式隐藏窗口
- Modify: `deploy/tencent/windows/04_register_admin_panel_task.ps1`
  - 计划任务启动管理面板守护脚本时显式隐藏窗口
- Modify: `scripts/tests/test_windows_server_bundle.py`
  - 锁定 GUI 单窗口、后台隐藏与状态面板新合同

### 文档

- Modify: `CONTEXT.md`
  - 记录实施停点和关键决策
- Modify: `README.md`
  - 新增 Windows 部署后“单窗口状态面板 + 后台守护”说明
- Modify: `ARCHITECTURE.md`
  - 补充状态面板、运行时状态接口与后台守护职责边界

---

## Task 1: 锁定单窗口状态面板与后台隐藏合同

**Files:**
- Modify: `scripts/tests/test_windows_server_bundle.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`

- [ ] **Step 1: 给部署脚本测试补“长期状态面板”合同**

锁定以下要求：

- `deploy_bundle.ps1` 窗口标题必须改成 `MT5 部署与连接状态`
- 脚本中必须出现部署区、行情区、账户区、APP 交互区的正式文字
- 部署完成后窗口不再提示“可关闭此窗口”作为唯一结束语，而是进入长期状态模式
- 轮询节奏固定为 1 秒

- [ ] **Step 2: 给计划任务脚本测试补“隐藏启动”合同**

锁定以下要求：

- `02_register_startup_task.ps1` 生成的 PowerShell 参数必须包含 `-WindowStyle Hidden`
- `04_register_admin_panel_task.ps1` 生成的 PowerShell 参数必须包含 `-WindowStyle Hidden`

- [ ] **Step 3: 给网关合同测试补 `/internal/runtime/status` 结构断言**

锁定以下字段：

- `streamClientsActive`
- `streamLastConnectedAt`
- `streamLastDisconnectedAt`
- `httpLastRequestAt`
- `httpLastRequestPath`
- `sessionLastAction`
- `sessionLastRequestAt`
- `sessionLastResult`
- `tradeLastAction`
- `tradeLastRequestAt`
- `tradeLastResult`
- `lastClientAddress`

- [ ] **Step 4: 先运行定向测试，确认当前代码失败**

Run:

```powershell
python -m unittest scripts.tests.test_windows_server_bundle -v
python -m unittest bridge.mt5_gateway.tests.test_v2_session_contracts -v
```

Expected:

- 部署脚本相关新断言失败
- `/internal/runtime/status` 相关断言失败

---

## Task 2: 实现网关运行时状态真值与接口

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_session_contracts.py`

- [ ] **Step 1: 在 `server_v2.py` 新增线程安全运行时状态容器**

职责：

- 维护当前活跃 `v2/stream` 客户端数
- 记录最近连接/断开时间
- 记录最近 HTTP 请求时间与路径
- 记录最近会话请求动作与结果
- 记录最近交易请求动作与结果
- 记录最近客户端来源地址

- [ ] **Step 2: 在 `/v2/stream` 链路写入连接事实**

要求：

- 连接建立时增加客户端数并写 `streamLastConnectedAt`
- 连接断开时减少客户端数并写 `streamLastDisconnectedAt`
- 客户端数不能出现负数

- [ ] **Step 3: 在会话与交易接口写入最近交互事实**

要求：

- `/v2/session/login|switch|logout` 写入 `sessionLastAction / sessionLastRequestAt / sessionLastResult`
- `/v2/trade/check|submit|batch/submit` 写入 `tradeLastAction / tradeLastRequestAt / tradeLastResult`
- 其他 `/v2/*` 请求更新 `httpLastRequestAt / httpLastRequestPath`

- [ ] **Step 4: 暴露 `GET /internal/runtime/status`**

要求：

- 固定返回结构
- 不依赖日志
- 缺省值明确可序列化

- [ ] **Step 5: 跑服务端定向测试**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_session_contracts -v
```

Expected:

- 新增运行时状态合同通过
- 旧会话合同不回退

---

## Task 3: 把部署窗口升级为长期连接状态总面板

**Files:**
- Modify: `deploy/tencent/windows/deploy_bundle.ps1`
- Modify: `scripts/tests/test_windows_server_bundle.py`

- [ ] **Step 1: 调整窗口定位**

要求：

- 窗口正式名称改为 `MT5 部署与连接状态`
- 保留部署步骤与日志区
- 新增长期状态展示区，不再让日志成为唯一状态来源

- [ ] **Step 2: 新增状态采集函数**

PowerShell 侧增加正式采集函数，分别读取：

- 本地端口状态
- 计划任务状态
- 网关 `/health`
- 网关 `/v1/source`
- 网关 `/v2/session/status`
- 网关 `/v2/session/diagnostic/latest`
- 网关 `/internal/runtime/status`

- [ ] **Step 3: 新增 5 个状态区块**

区块固定为：

- 顶部总览区
- 本地部署区
- 行情连接区
- 账户连接区
- 手机 APP 交互区

- [ ] **Step 4: 修改部署完成后的行为**

要求：

- 部署成功后窗口不关闭
- 状态文案改成“运行中”
- 继续每秒刷新长期状态
- 关闭窗口只关闭展示层，不停止后台服务

- [ ] **Step 5: 保持部署期与运行期共存**

要求：

- 部署阶段继续显示步骤和日志
- 运行阶段保留最近部署结果
- 如果后台 worker 异常退出，窗口必须显示明确错误

- [ ] **Step 6: 跑部署脚本测试**

Run:

```powershell
python -m unittest scripts.tests.test_windows_server_bundle -v
```

Expected:

- 单窗口状态面板相关断言通过
- 原有 bundle 与 deploy 合同不回退

---

## Task 4: 收口后台隐藏启动链

**Files:**
- Modify: `deploy/tencent/windows/02_register_startup_task.ps1`
- Modify: `deploy/tencent/windows/04_register_admin_panel_task.ps1`
- Modify: `scripts/tests/test_windows_server_bundle.py`

- [ ] **Step 1: 修改计划任务参数**

要求：

- 生成的 `powershell.exe` 参数显式包含 `-WindowStyle Hidden`
- 保留现有 `-NoProfile -ExecutionPolicy Bypass -File ...`
- 不改变“交互用户优先，SYSTEM 兜底”的任务注册策略

- [ ] **Step 2: 确认部署链仍能自动启动任务**

要求：

- `deploy_bundle.ps1` 注册并启动计划任务的逻辑不变
- 隐藏行为只影响可见性，不影响保活链

- [ ] **Step 3: 跑部署脚本定向测试**

Run:

```powershell
python -m unittest scripts.tests.test_windows_server_bundle.WindowsServerBundleTests.test_register_task_scripts_should_prefer_interactive_user_and_fallback_to_system -v
python -m unittest scripts.tests.test_windows_server_bundle -v
```

Expected:

- 计划任务合同通过
- 其他部署合同不回退

---

## Task 5: 总回归、打包与文档同步

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: 跑服务端和部署脚本总回归**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_session_contracts scripts.tests.test_windows_server_bundle -v
```

Expected:

- `OK`

- [ ] **Step 2: 重建 Windows 部署包**

Run:

```powershell
python scripts/build_windows_server_bundle.py
```

Expected:

- 成功生成最新 `dist/windows_server_bundle`

- [ ] **Step 3: 更新文档**

要求：

- `CONTEXT.md` 写明当前已完成“单窗口状态面板 + 后台隐藏守护”
- `README.md` 补充部署后只保留一个状态窗口的说明
- `ARCHITECTURE.md` 补充 `deploy_bundle.ps1` 与 `/internal/runtime/status` 的职责边界

- [ ] **Step 4: 手工验收口径**

服务器侧验收应确认：

- 部署后桌面只看到一个前台窗口
- 网关与管理面板后台继续运行
- 手机 APP 连接、断开、登录、交易后，状态面板对应时间与结果会刷新

---

## 执行顺序结论

本计划必须按以下顺序执行：

1. 先锁测试合同，避免窗口和状态口径边做边漂
2. 先补网关结构化运行时真值，再做 GUI 展示
3. 最后再改计划任务隐藏启动，避免调试阶段误把状态入口一起隐藏
4. 全部通过后再重建部署包和更新文档

---

## 自检

### Scope check

- 只覆盖 Windows 单窗口状态面板需求
- 不扩散到 Android 界面改造
- 不触碰 MT5 登录/切号算法本身

### Placeholder scan

- 已检查，无 `TODO / TBD / 后续再说 / 暂不实现` 占位

### Consistency check

- 前台窗口唯一
- 后台守护隐藏
- 手机 APP 状态来自结构化接口，不来自日志猜测
- 部署期与运行期共享同一个窗口，不新开第二窗口
