# Audit Architecture Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 基于 2026-04-25 最终审计结论，按最小完整路径修复项目架构中的高风险边界、交易语义、运行态一致性和模块依赖问题。

**Architecture:** 先修边界，再修语义，再收状态，最后治理模块。Bridge 侧建立统一鉴权和交易领域服务；Android 侧建立明确的账户运行态提交契约、本地持久化安全边界和 UI/data 依赖方向。

**Tech Stack:** Android Java、FastAPI/Python、OkHttp、Room、SharedPreferences、JUnit、pytest、PowerShell/Gradle。

---

## 一、架构重新分析

### 当前架构事实

项目现在不是纯 Android 单体，而是“Android 客户端 + MT5 Bridge 网关 + Windows 部署包”的组合系统：

- Android `app/`：负责行情展示、账户页、交易入口、悬浮窗、通知、本地缓存。
- Bridge `bridge/mt5_gateway/`：负责 MT5 会话、账户快照、交易提交、批量交易、v2 stream、内部运维接口。
- 部署 `deploy/` / `dist/windows_server_bundle/`：负责 Windows 服务端运行与发布。

按实际代码看，系统最关键的架构边界有三条：

- 信任边界：Android 到 Bridge 的 HTTP/WS 接口。
- 资金边界：单笔/批量交易从 UI 指令到 MT5 `order_check/order_send`。
- 运行态边界：Bridge 发布 stream，Android 消费后写入账户、行情、异常和悬浮窗状态。

### 主要架构问题

1. 信任边界缺失  
   Bridge 的 v2 session/account/trade/internal/stream 接口没有统一鉴权。当前风险不是“某个接口少判断”，而是网关入口没有认证中间层，导致后续每个业务模块都默认处在可信内网里。

2. 交易领域语义散落在路由和工具函数里  
   单笔交易幂等逻辑在 `server_v2.py`，批量交易逻辑在 `v2_trade_batch.py`，但 batch 幂等、`ALL_OR_NONE`、`GROUPED`、未知结果状态没有被统一抽象为交易领域契约。

3. Android 运行态提交不是原子闭环  
   v2 stream 收到消息后先刷新健康时间、提前提交 `busSeq`，但账户异步应用可能失败。这说明当前“收到消息”和“成功应用”两个状态没有分离。

4. 本地数据清理缺少身份约束  
   账户缓存清理在缺少身份时会退化到全量删除。架构上缺少“危险数据操作必须携带明确身份”的边界。

5. 模块依赖方向不够干净  
   `data.remote.v2.GatewayV2TradeClient` 依赖 `ui.trade.TradeAuditEntry`，说明 data 层已经反向依赖 UI 层。短期能运行，但会阻碍测试和后续拆模块。

6. 本地持久化策略不统一  
   Room、SharedPreferences、JSON 文件并存。交易审计、日志、异常记录仍有同步 I/O、静默失败和并发丢写风险。

## 二、目标架构

### Bridge 目标结构

目标是把 `server_v2.py` 从“大入口 + 业务逻辑混合体”收口为入口编排层：

- `server_v2.py`：只保留 FastAPI 路由、请求记录、响应转换。
- `auth_guard.py`：统一 HTTP/WS 鉴权。
- `v2_trade_service.py`：单笔交易、批量交易、幂等、策略语义、未知结果状态。
- `v2_trade_batch.py`：保留纯函数解析和 batch item 准备，去掉策略执行最终裁决。
- `v2_trade_audit.py`：继续保存服务端交易审计，但内部清缓存不能无鉴权清除审计事实。

### Android 目标结构

目标是把运行态应用和 UI 刷新解耦：

- `MonitorService`：保留服务生命周期和编排，不直接承担所有状态正确性。
- `V2StreamSequenceGuard`：只管理序号，不决定业务应用成败。
- `AccountStatsPreloadManager`：返回账户运行态应用结果，不能失败后静默。
- `AccountStorageRepository`：危险清理 API 拆成“按身份清理”和“显式全量清理”。
- `TradeAuditEntry`：下沉到 data/domain model，UI 只负责展示。
- `TradeAuditStore`：串行化写入或迁移 Room。

## 三、任务拆分判断

这些任务可以拆开，但不建议同时大并发修改同一条链：

- 第一阶段必须先做 Bridge 鉴权，因为它是所有资金与账户接口的上游边界。
- 第二阶段做交易语义，因为 batch 幂等和未知结果会影响资金行为。
- 第三阶段做 Android 状态一致性，因为需要依赖服务端协议稳定。
- 第四阶段做模块治理和存储治理，避免在高风险修复前扩大改动面。

## 四、实施任务计划

### Task 1: Bridge 统一鉴权边界

**Files:**
- Create: `bridge/mt5_gateway/auth_guard.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_auth_guard.py`
- Modify: `deploy/tencent/windows/01_bootstrap_gateway.ps1`
- Modify: `bridge/mt5_gateway/start_gateway.ps1`

- [x] Step 1: 定义鉴权环境变量契约

在启动脚本中要求 `GATEWAY_AUTH_TOKEN` 非空。开发环境可显式设置 `GATEWAY_AUTH_OPTIONAL=1`，生产部署不允许默认放行。

- [x] Step 2: 新增 HTTP 鉴权函数

`auth_guard.py` 提供 `require_http_auth(request)`，接受 `Authorization: Bearer <token>` 或 `X-Gateway-Token: <token>`。

- [x] Step 3: 新增 WS 鉴权函数

`auth_guard.py` 提供 `verify_ws_auth(websocket)`，在 `client.accept()` 前读取 query/header token，不通过则 close 1008。

- [x] Step 4: 接入 v2 与 internal 路由

覆盖 `/v2/session/*`、`/v2/account/*`、`/v2/trade/*`、`/v2/abnormal/*`、`/internal/*`、`/v2/stream`。健康检查和 Binance 代理如果要公开，必须写入白名单。

- [x] Step 5: 补 pytest

覆盖无 token、错误 token、正确 token、WS 无 token、WS 正确 token。

**验证命令：**

```powershell
python -m pytest bridge/mt5_gateway/tests/test_v2_auth_guard.py -q
python -m pytest bridge/mt5_gateway/tests/test_v2_trade_contracts.py -q
```

### Task 2: 交易领域语义闭环

**Files:**
- Create: `bridge/mt5_gateway/v2_trade_service.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_trade_batch.py`
- Modify: `bridge/mt5_gateway/v2_trade_models.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_trade_batch.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_trade_contracts.py`

- [x] Step 1: 抽出单笔交易服务

把单笔交易的幂等、prepare、check、send、result unknown 处理集中到 `v2_trade_service.submit_single_trade()`。

- [x] Step 2: 定义未知结果状态

新增状态 `UNKNOWN` 或 `PENDING_CONFIRMATION`，替换当前 send 异常时返回 `ACCEPTED` 的行为。

- [x] Step 3: 实现 batch 幂等

`batchId` 执行前先查缓存。相同 digest 直接返回旧结果，不同 digest 返回 payload mismatch。

- [x] Step 4: 收口 `ALL_OR_NONE`

先对所有 item 做 prepare 和 `order_check`；全部通过才进入 send。若 MT5 无法回滚，接口文档和 UI 文案必须明确这是“预检全通过后逐条提交”，不是严格数据库事务。

- [x] Step 5: 收口 `GROUPED`

按 `groupKey` 分组；组内单独预检和提交；一个组失败不影响其他组；响应加入组级状态。

- [x] Step 6: 补交易契约测试

覆盖重复 batch、batchId 相同 payload 不同、ALL_OR_NONE 预检失败、ALL_OR_NONE send 失败、GROUPED 单组失败、多组隔离。

**验证命令：**

```powershell
python -m pytest bridge/mt5_gateway/tests/test_v2_trade_batch.py -q
python -m pytest bridge/mt5_gateway/tests/test_v2_trade_contracts.py -q
```

### Task 3: Android 账户运行态提交契约

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/service/stream/V2StreamSequenceGuard.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorServiceV2SourceTest.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/account/AccountStatsPreloadManagerSourceTest.java`

- [x] Step 1: 定义应用结果对象

`AccountStatsPreloadManager.applyPublishedAccountRuntime()` 返回 `ApplyResult`，包含 `success`、`message`、`account`、`server`。

- [x] Step 2: 调整 `busSeq` 提交时机

账户快照消息必须在账户运行态成功应用后才 `commitAppliedBusSeq()`；失败时不推进序号，触发 bootstrap 或重试。

- [x] Step 3: 调整 stream 健康时间

`lastV2StreamMessageAt` 区分“收到消息时间”和“成功应用时间”。连接健康优先看成功应用时间，避免坏包维持假健康。

- [x] Step 4: 补源码测试

用 source test 锁定：不能在 `applyPublishedAccountRuntime` 异步成功前提交 `busSeq`；不能在 catch 分支刷新成功应用时间。

**验证命令：**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceV2SourceTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.account.AccountStatsPreloadManagerSourceTest"
```

### Task 4: Android 账户数据清理安全边界

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/test/java/com/binance/monitor/runtime/account/AccountStatsPreloadManagerSourceTest.java`

- [x] Step 1: 拆分清理 API

保留 `clearRuntimeSnapshot(account, server)` / `clearTradeHistory(account, server)`；新增 `clearAllRuntimeSnapshotsExplicitly()` 这类显式命名 API，禁止空身份自动全量清理。

- [x] Step 2: 调整调用方

`ACTION_CLEAR_ACCOUNT_RUNTIME` 必须先解析当前 active account；解析失败只清内存运行态，不删持久化历史。

- [x] Step 3: 补测试

覆盖 null account/server 不调用 `clearAll()`，只在明确全量清理入口调用全量删除。

**验证命令：**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.runtime.account.AccountStatsPreloadManagerSourceTest"
```

### Task 5: 悬浮窗运行时防崩边界

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/theme/FloatingWindowThemeSourceTest.java`

- [x] Step 1: 包装 `addView`

`showIfPossible()` 对 `windowManager.addView()` 捕获 `RuntimeException`，失败时保持 `windowAdded=false`。

- [x] Step 2: 暴露失败状态

记录日志，并把悬浮窗状态收敛成 disabled 或 waiting-permission，避免循环崩溃。

- [x] Step 3: 补 source test

检查 `addView` 周围存在 try/catch，且 catch 后不会设置 `windowAdded=true`。

**验证命令：**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.theme.FloatingWindowThemeSourceTest"
```

### Task 6: 模块依赖方向治理

**Files:**
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeAuditEntry.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditStore.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditActivity.java`
- Modify: `app/src/test/java/com/binance/monitor/architecture/AuditStaticGuardSourceTest.java`

- [x] Step 1: 下沉模型

把 `ui.trade.TradeAuditEntry` 移到 `data.model.v2.trade`，保持 JSON 字段不变。

- [x] Step 2: 更新引用

`GatewayV2TradeClient` 只依赖 data model；UI 层 import 新模型。

- [x] Step 3: 增加架构守卫测试

禁止 `app/src/main/java/com/binance/monitor/data/**` import `com.binance.monitor.ui.`。

**验证命令：**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.architecture.AuditStaticGuardSourceTest"
```

### Task 7: 本地审计与 JSON 存储治理

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditStore.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/LogManager.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/AbnormalRecordManager.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/trade/TradeAuditStoreTest.java`

- [x] Step 1: 修复交易审计并发

短期用同一把锁包住 read-modify-write，并把 `apply()` 改成能返回结果的写入策略；中期再迁移 Room。

- [x] Step 2: JSON 写入失败可观测

`LogManager` 和 `AbnormalRecordManager` 的 persist 失败至少 `Log.w`，并保留 `lastStorageError`。

- [x] Step 3: 补并发测试

模拟多线程 record，断言记录数不丢失，顺序允许不稳定但 traceId 必须完整。

**验证命令：**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeAuditStoreTest"
```

### Task 8: 解析契约统一

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/data/model/KlineData.java`
- Modify: `app/src/main/java/com/binance/monitor/data/model/AbnormalRecord.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/KlineStreamMessageParser.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/test/java/com/binance/monitor/data/remote/KlineStreamMessageParserTest.java`
- Modify: `app/src/test/java/com/binance/monitor/service/MonitorServiceParsingSourceTest.java`

- [x] Step 1: 增加安全解析入口

为 Kline 和 abnormal record 提供 `tryParse` 或 `parseOrNull`，字段缺失/非有限数字返回 null 或带错误信息的结果。

- [x] Step 2: 调整调用方

WS、REST、落盘读取统一逐条跳过坏记录，不能整批清空，日志要包含字段名和来源。

- [x] Step 3: 补坏包测试

覆盖缺字段、非数字、NaN/Infinity、空 symbol、旧本地 JSON 单条损坏。

**验证命令：**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.KlineStreamMessageParserTest"
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceParsingSourceTest"
```

## 五、总体验证策略

每个阶段先跑最小测试，再跑相关聚合测试：

```powershell
python -m pytest bridge/mt5_gateway/tests/test_v2_auth_guard.py bridge/mt5_gateway/tests/test_v2_trade_batch.py bridge/mt5_gateway/tests/test_v2_trade_contracts.py -q
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

部署包涉及 Bridge 修改后必须额外跑：

```powershell
python scripts/build_windows_server_bundle.py
python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle --skip-remote
```

## 六、验收标准

- Bridge 所有 v2/account/session/trade/internal/stream 请求没有 token 时被拒绝。
- 单笔交易 send 异常不再返回 `ACCEPTED`。
- 批量交易重复 `batchId` 不会重复执行。
- `ALL_OR_NONE` 和 `GROUPED` 的行为与接口命名一致，或命名被调整为真实语义。
- Android 账户运行态应用失败不会推进 `busSeq`。
- 空身份清理不会删除全部账户历史。
- 悬浮窗 `addView` 异常不会崩溃服务。
- data 层不再 import UI 包。
- 本地交易审计并发写入不丢记录。

## 七、风险与回退

- 鉴权上线会影响 Android、Windows 面板和脚本调用，必须同步配置 token。
- 交易语义改动必须优先在模拟/测试 MT5 环境验证，不直接上实盘。
- Android 状态提交时序修复可能暴露已有坏包问题，需要配套日志和 bootstrap 恢复。
- 持久化 API 改名会影响多个账户页入口，必须用 source test 锁住全局清理入口。

## 八、推荐执行顺序

1. Bridge 鉴权。
2. Bridge 交易语义和 batch 幂等。
3. Android 账户运行态提交和清理边界。
4. Android 悬浮窗防崩。
5. 模块依赖方向治理。
6. 本地存储与解析契约治理。

