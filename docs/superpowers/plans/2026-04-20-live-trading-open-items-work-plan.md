# Live Trading Open Items Work Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前实盘主线剩余的 3 个未闭项收口完成，让“代码已完成”真正变成“登录稳定、线上版本一致、真机可验收”。

**Architecture:** 现状不是交易功能没做完，而是“登录成功合同、线上部署版本、真机验收”三条收口链还没完全对齐。后续不再扩功能，直接围绕这 3 条主线收口：客户端登录成功改为轻量运行态确认，部署检查补 `/v2/account/full` 诊断并把线上 bundle 切到本地最新版本，最后用同一版 APK 做远程会话和 phase2/3/4 的人工验收。

**Tech Stack:** Android Java、Python、PowerShell、Gradle `testDebugUnitTest`、`assembleDebug`、ADB、Windows 部署脚本

---

## 当前结论

- 第一阶段远程会话：代码已完成，自动化验收已完成。
- 第二阶段可用交易终端：代码与本地回归已完成。
- 第三阶段批量与复杂交易：代码与本地回归已完成。
- 第四阶段无 DOM 市价交易增强：代码与本地回归已完成。
- 当前真正未完成的只有 3 条：
  1. 登录成功仍应从 `/v2/account/full` 收口切到 `accountRuntime` / `/v2/account/snapshot` 轻量确认。
  2. 线上部署版本还没明确闭合到本地当前 bundle，且部署检查尚未补 `/v2/account/full` 诊断。
  3. 设备当前不稳定，phase2/3/4 与远程会话的真机安装和人工验收还没补完。

## 并行性判断

- 这 3 条工作不适合拆成多 Agent 并行。
- 原因：
  - 真机验收依赖登录合同先收口。
  - 真机验收也依赖线上部署版本先闭合。
  - 部署检查要基于登录合同的最终口径补诊断。
- 最短路径是串行执行：`登录合同 -> 部署闭合 -> 真机验收`。

---

### Task 1: 把登录成功收口改为轻量运行态确认

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinatorTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountSessionDialogControllerSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsBridgeActivitySessionSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsScreenSessionSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionActivitySourceTest.java`

- [ ] **Step 1: 先补失败测试，锁定“登录成功不再依赖 full”**
  - 新增或改造测试，覆盖：
    - `accountRuntime` 命中目标账号即可收口成功
    - `/v2/account/snapshot` 命中目标账号也可收口成功
    - `/v2/account/full` 慢或失败时，已登录状态不能回退
    - 旧账号 runtime 不能误收口新账号

- [ ] **Step 2: 调整 `AccountRemoteSessionCoordinator` 的收口语义**
  - 保持 `accepted -> syncing` 不变。
  - 把 `onSnapshotApplied(...)` 从“只认 full 后的页面快照”改成“认轻量运行态或轻量 snapshot 的账号身份命中”。
  - 保留严格匹配：
    - `login/account` 必须匹配
    - 用户显式输入了 `server` 时，`server` 也必须匹配

- [ ] **Step 3: 调整账户页登录入口的执行顺序**
  - `AccountSessionDialogController`、`AccountStatsScreen`、`AccountStatsBridgeActivity` 不再把 `fetchFullForUi(AccountTimeRange.ALL)` 当作登录成功前提。
  - 登录已受理后：
    - 先进入“正在同步账户”
    - 优先触发轻量确认
    - 命中后立即切 active
    - 再后台触发 full 补全

- [ ] **Step 4: 保留 `/v2/account/full` 的正式职责**
  - `GatewayV2Client.fetchAccountFull()` 继续保留。
  - 只用于：
    - 登录后的后台补全
    - 交易后的强一致刷新
    - 历史/统计/图表完整数据装配
  - 不再承担登录成功闸门职责。

- [ ] **Step 5: 更新页面文案和状态表达**
  - 登录等待态显示“正在同步账户”。
  - 轻量确认成功后立即进入已登录/账户在线。
  - full 仍在补齐时，只在局部显示“正在加载完整数据”，不再打回登录失败。

- [ ] **Step 6: 跑 Task 1 定向回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountSessionDialogControllerSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsScreenSessionSourceTest" --tests "com.binance.monitor.ui.account.AccountPositionActivitySourceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

**Task 1 完成标准：**

- 登录成功定义正式从 `full` 改为轻量运行态确认。
- `full` 变慢只影响完整数据补齐，不再直接导致登录 timeout。
- 账户切换、新登录、旧事件污染三类场景都有测试锁定。

---

### Task 2: 闭合线上部署版本，并补 `/v2/account/full` 诊断

**Files:**
- Modify: `scripts/check_windows_server_bundle.py`
- Modify: `deploy/tencent/windows/deploy_bundle.ps1`
- Modify: `scripts/tests/test_windows_server_bundle.py`
- Modify: `scripts/tests/test_check_windows_server_bundle.py`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: 给部署检查补 `/v2/account/full` 诊断**
  - 保持 `/health`、`/v2/account/snapshot`、`/v2/account/history?range=all`、`wss /v2/stream` 现有检查不变。
  - 新增 `/v2/account/full` 检查，但分类为“诊断项”，不再把它作为登录成功前提。

- [ ] **Step 2: 给脚本和测试补正式约束**
  - `deploy_bundle.ps1` 补：
    - `8787 /v2/account/full`
    - `443 tradeapp.ltd/v2/account/full`
  - `test_windows_server_bundle.py` 锁定健康检查脚本里存在这两条诊断探针。
  - `test_check_windows_server_bundle.py` 锁定核对脚本不会因为新增诊断而破坏原有 fingerprint 检查。

- [ ] **Step 3: 重新生成本地最新 bundle**

Run:

```powershell
python scripts/build_windows_server_bundle.py
python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle --skip-remote
```

Expected:

```text
本地源码与部署目录一致
```

- [ ] **Step 4: 把服务器部署到最新 bundle，并核对线上版本**

Run:

```powershell
python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle
```

Expected:

```text
本地源码与部署目录一致

线上服务器与当前部署目录一致
```

- [ ] **Step 5: 记录最新线上 bundle 指纹与诊断结果**
  - 把最新线上 `bundleFingerprint`、`/v2/account/full` 诊断结论回写文档。
  - 后续登录问题排查统一以这版线上结果为准，不再混用旧版本指纹。

- [ ] **Step 6: 跑 Task 2 自动化回归**

Run:

```powershell
python -m unittest scripts.tests.test_windows_server_bundle scripts.tests.test_check_windows_server_bundle bridge.mt5_gateway.tests.test_gateway_bundle_parity bridge.mt5_gateway.tests.test_v2_session_contracts -v
```

Expected:

```text
OK
```

**Task 2 完成标准：**

- 线上 bundle 指纹与本地当前部署包一致。
- 部署检查能明确区分“轻链路健康”和“full 偏慢/异常”。
- 后续再看登录 timeout 时，不会因为线上版本不明或 full 未诊断而误判。

---

### Task 3: 补 phase2/3/4 与远程会话的真机安装和人工验收

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`

- [ ] **Step 1: 先恢复设备连接**

Run:

```powershell
adb devices
```

Expected:

```text
List of devices attached
7fab54c4    device
```

- [ ] **Step 2: 安装当前最新 debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
adb -s 7fab54c4 install -r app\build\outputs\apk\debug\app-debug.apk
adb -s 7fab54c4 shell am start -W com.binance.monitor/.ui.main.MainActivity
```

Expected:

```text
BUILD SUCCESSFUL
Success
Status: ok
```

- [ ] **Step 3: 做远程会话人工验收**
  - 新账号登录：确认不会再因 full 偏慢直接 timeout。
  - 已保存账号切换：确认能切到目标账号，不会误回旧账号。
  - 退出登录：确认页面回到未登录态。
  - 登录后 full 偏慢场景：确认先显示已登录，再局部补完整数据。

- [ ] **Step 4: 做实盘 phase2/3/4 人工验收**
  - 第二阶段：
    - 风险预演显示正确
    - 拒单文案可读
    - 草稿线和真实线可区分
  - 第三阶段：
    - 部分平仓、加仓、反手、Close By 结果页正确
    - batch 部分成功时能看到单项清单
  - 第四阶段：
    - 模板默认值生效
    - `requestId / batchId` 追踪页可打开
    - 风控超限时有明确中文提示

- [ ] **Step 5: 补人工验收证据**
  - 保存最小截图/日志/验收结论。
  - 若仍失败，必须记录失败发生在：
    - 登录轻量确认前
    - 登录后 full 补全
    - 交易执行
    - 交易追踪

- [ ] **Step 6: 更新项目记录**
  - `CONTEXT.md` 记录最新真机结论和当前停点。
  - `README.md` 把 phase2/3/4 的口径从“待真机”改成真实状态；如果仍有失败，也明确是哪条链未过。

**Task 3 完成标准：**

- 设备上能稳定完成远程会话登录/切换/退出。
- 第二阶段、第三阶段、第四阶段都至少完成一轮真实设备验收。
- “代码完成”与“真机已验证”的口径终于一致。

---

## 收口顺序

1. 先做 Task 1，修正登录成功合同。
2. 再做 Task 2，确认线上部署与诊断口径。
3. 最后做 Task 3，完成真机闭环。

## 完成判断

- 只有以下 3 条同时成立，当前实盘工作才算真正闭环：
  - 登录成功已改为轻量运行态确认
  - 线上部署版本与本地 bundle 一致，且有 `/v2/account/full` 诊断
  - phase2/3/4 与远程会话都完成最新真机验收
