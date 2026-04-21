# Live Trading Phase 4 No-DOM Detailed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在第三阶段主链已经稳定的基础上，不做 DOM / 深度交易，直接补齐“模板与默认参数、按 `requestId` 可追、交易回放与排障、细粒度风控、阶段总验收”这五块专业化能力。

**Architecture:** 继续复用现有单笔 `check -> confirm -> submit -> result -> sync` 与第三阶段 batch `submit -> result -> strong refresh` 两条正式主链，不新增任何第二套执行逻辑。第四阶段只在三处加能力：参数层增加模板/默认值正式真值；日志层增加服务端和客户端统一审计；决策层增加细粒度风控；页面层增加“查看结果追踪/回放”的专用入口。

**Tech Stack:** Python、FastAPI、Android Java、SharedPreferences、OkHttp、JUnit4、Python unittest、Gradle `testDebugUnitTest`、`assembleDebug`、ADB

---

## 范围重定义

### 本阶段明确包含

1. 交易模板与默认参数正式边界
2. 交易审计日志与 `requestId / batchId` 查询边界
3. 交易回放 / 排障视图
4. 更细粒度的市价交易风控
5. 总回归、真机安装、阶段文档

### 本阶段明确不做

- DOM / 深度交易 / 盘口梯队
- 新的交易提交链路
- 页面层本地“猜成交结果”的兜底逻辑
- 复杂权限系统或服务端多角色审批

### 并行性判断

- 这 5 组任务表面上可以拆，但真正的关键路径高度串行：
  - Task 1 会改 `ConfigManager / MarketChartTradeDialogCoordinator / MarketChartScreen`
  - Task 2 会改 `server_v2.py / GatewayV2TradeClient / TradeExecutionCoordinator / BatchTradeCoordinator`
  - Task 3 要建立在 Task 2 的审计合同已经稳定之上
  - Task 4 会再次触达 `ConfigManager / TradeConfirmDialogController / BatchTradeCoordinator`
- 因此本阶段**不建议拆成多 Agent 并行实现**，否则会在 `ConfigManager`、交易协调器与服务端合同上形成高冲突写入。最短路径仍然是按本文顺序 inline 执行。

---

## File Map

### Existing files to modify

- `app/src/main/java/com/binance/monitor/data/local/ConfigManager.java`
  当前只承接全局配置；第四阶段要补交易默认参数、模板持久化和风控阈值真值。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
  当前快捷交易手数仍有固定默认值；第四阶段要接默认参数加载、交易追踪入口和结果页跳转。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
  当前统一交易弹窗仍使用硬编码默认手数，结果提示也只停在 toast/dialog；第四阶段要接模板选择、默认参数回填和“查看追踪”入口。
- `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java`
  当前快捷交易只读文本手数；第四阶段要消费正式默认参数和模板来源标记。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
  当前只负责确认内容和快速模式决策；第四阶段要同时接模板摘要和风控决策结果。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
  当前只做检查、提交和强刷；第四阶段要写入单笔交易审计，并给排障视图提供统一结果对象。
- `app/src/main/java/com/binance/monitor/ui/trade/BatchTradeCoordinator.java`
  当前只做 batch 提交、结果追认和强刷；第四阶段要补批量审计和批量风控。
- `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java`
  当前只承接 `/v2/trade/*` 与 batch result；第四阶段要补交易审计查询接口。
- `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
  当前“日志”入口仍指向通用日志页；第四阶段要新增交易追踪入口。
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
  当前设置页没有交易专项配置；第四阶段要新增模板/默认值/风控阈值低频设置入口。
- `app/src/main/res/layout/activity_market_chart.xml`
  当前快捷交易区域只有手数输入；第四阶段要补模板入口或追踪入口占位。
- `app/src/main/res/values/strings.xml`
  本轮补模板、追踪、风控和排障提示文案。
- `bridge/mt5_gateway/server_v2.py`
  当前有 `requestId / batchId` 结果查询，但还没有正式交易审计总线；第四阶段要补审计存储与查询路由。
- `bridge/mt5_gateway/v2_trade.py`
  当前只做 payload 规范化与请求构造；第四阶段要补审计摘要构造辅助函数。
- `README.md`
- `ARCHITECTURE.md`
- `CONTEXT.md`

### New files to create

- `app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeTemplate.java`
  交易模板模型，保存模板名、默认手数、默认止损、默认止盈及启用范围。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeTemplateRepository.java`
  模板与默认参数仓库，只通过 `ConfigManager` 读写，不让页面层自己拼 SharedPreferences 键。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditEntry.java`
  统一交易审计条目模型，兼容单笔与 batch。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditStore.java`
  客户端本地交易审计存储，记录命令摘要、阶段状态、`requestId / batchId`、错误码、最终提示。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeReplayFormatter.java`
  把本地审计 + 网关审计整理成用户能看懂的时间线。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditViewModel.java`
  交易追踪页 ViewModel，负责 recent list、按 id 查询和回放数据装配。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditActivity.java`
  交易追踪/回放页。
- `app/src/main/java/com/binance/monitor/ui/trade/TradeRiskGuard.java`
  更细粒度风控策略中心，统一判定单笔与 batch 是否允许提交、是否必须强制确认。
- `bridge/mt5_gateway/v2_trade_audit.py`
  服务端交易审计环形缓存与查询逻辑。
- `bridge/mt5_gateway/tests/test_v2_trade_audit.py`
  锁定服务端审计合同。
- `app/src/test/java/com/binance/monitor/ui/trade/TradeTemplateRepositoryTest.java`
- `app/src/test/java/com/binance/monitor/ui/trade/TradeAuditStoreTest.java`
- `app/src/test/java/com/binance/monitor/ui/trade/TradeReplayFormatterTest.java`
- `app/src/test/java/com/binance/monitor/ui/trade/TradeRiskGuardTest.java`
- `app/src/test/java/com/binance/monitor/ui/trade/TradeAuditActivitySourceTest.java`

---

## 执行顺序明细

### 顺序总览

1. Task 1 先把模板和默认参数立成正式真值
2. Task 2 再把客户端/服务端交易审计立起来
3. Task 3 基于 Task 2 的审计合同接交易回放与排障视图
4. Task 4 最后补细粒度风控，因为它既要吃 Task 1 的配置真值，也要吃 Task 2/3 的追踪能力
5. Task 5 统一做总回归、真机安装、文档同步

### 为什么必须按这个顺序

- 没有 Task 1，后面的模板摘要、风控阈值、默认手数都会继续散落在页面层。
- 没有 Task 2，Task 3 的“查看追踪”只能看到通用日志，无法按 `requestId` 收口。
- 没有 Task 3，Task 4 即便拦住了交易，也缺少统一排障入口，用户还是不知道为什么被拦。
- 因此 Task 1 -> Task 2 -> Task 3 -> Task 4 是一条必须保持的主链。

---

### Task 1: 交易模板与默认参数正式边界

**目标：** 让“默认手数 / 默认止损 / 默认止盈 / 常用模板”从页面硬编码变成正式配置真值，并统一回填到图表页交易入口。

**Files:**
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeTemplate.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeTemplateRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/ConfigManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/binance/monitor/data/local/ConfigManagerSourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/TradeTemplateRepositoryTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`

- [ ] **Step 1: 在 `ConfigManager` 中建立交易默认参数和模板持久化键**
  - 新增默认手数、默认止损、默认止盈、默认模板 ID、模板 JSON 列表、快捷交易默认模板 ID。
  - 不允许页面层直接拼 pref key。

- [ ] **Step 2: 新建 `TradeTemplate` 与 `TradeTemplateRepository`**
  - `TradeTemplate` 只保存：
    - `templateId`
    - `displayName`
    - `defaultVolume`
    - `defaultSl`
    - `defaultTp`
    - `entryScope`（`market` / `pending` / `both`）
  - `TradeTemplateRepository` 负责：
    - 读取默认模板
    - 读取所有模板
    - 保存/覆盖模板
    - 回退到系统默认模板集

- [ ] **Step 3: 把图表页快捷交易默认手数改成读正式默认值**
  - 收掉 `strings.xml` 里的固定 `0.05` 真值地位，只保留 UI fallback。
  - `MarketChartScreen` 初始化快捷手数时优先从 `TradeTemplateRepository` 读默认值。

- [ ] **Step 4: 把统一交易弹窗改成“模板回填 -> 用户调整 -> 提交”**
  - `MarketChartTradeDialogCoordinator.configureTradeDialog(...)` 中不再直接写死 `0.10`。
  - 开仓、挂单场景优先回填模板值；复杂平仓动作仍按目标持仓量和第三阶段复杂动作边界处理。

- [ ] **Step 5: 把模板信息带入确认摘要**
  - `TradeConfirmDialogController` 在原有“动作摘要 + 风险摘要”前后补充“当前模板：xx”。
  - 这里只展示，不改变协议。

- [ ] **Step 6: 给图表页补低频模板入口**
  - 推荐方案：快捷交易区新增一个轻量模板选择字段，不新增第二套设置弹窗。
  - 入口只负责切模板，不负责绕开确认或直接成交。

- [ ] **Step 7: 为模板边界补测试**
  - `ConfigManagerSourceTest` 锁定新增交易配置键和读写方法存在。
  - `TradeTemplateRepositoryTest` 锁定默认模板回退、覆盖保存、空数据回退系统默认模板。
  - `MarketChartTradeSourceTest` 锁定图表页不再硬编码 `0.10`。

- [ ] **Step 8: 跑 Task 1 定向回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.local.ConfigManagerSourceTest" --tests "com.binance.monitor.ui.trade.TradeTemplateRepositoryTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

**Task 1 完成标准：**

- 图表页快捷交易和统一交易弹窗都不再写死默认手数。
- 模板只改变草稿默认值，不改变执行主链。
- 页面层不再直接操作 SharedPreferences 键。

---

### Task 2: 交易审计日志与 `requestId / batchId` 查询边界

**目标：** 把“点了但没成交 / 到底在哪一步失败”这类问题收成正式可查的交易审计，而不是继续靠通用日志猜。

**Files:**
- Create: `bridge/mt5_gateway/v2_trade_audit.py`
- Create: `bridge/mt5_gateway/tests/test_v2_trade_audit.py`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditEntry.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditStore.java`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_trade.py`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/BatchTradeCoordinator.java`
- Test: `bridge/mt5_gateway/tests/test_v2_trade_contracts.py`
- Test: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClientTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/TradeAuditStoreTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/BatchTradeCoordinatorTest.java`

- [ ] **Step 1: 在服务端建立正式交易审计缓存**
  - `v2_trade_audit.py` 参考 `v2_session_manager.py` 的审计结构，建立最近 N 条环形缓存。
  - 每条记录至少包含：
    - `traceType`（single / batch）
    - `requestId` 或 `batchId`
    - `action`
    - `symbol`
    - `accountMode`
    - `stage`（check / submit / result / batch_submit / batch_result）
    - `status`
    - `errorCode`
    - `message`
    - `serverTime`

- [ ] **Step 2: 在 `server_v2.py` 的交易路由里补审计写入**
  - `/v2/trade/check`
  - `/v2/trade/submit`
  - `/v2/trade/result`
  - `/v2/trade/batch/submit`
  - `/v2/trade/batch/result`
  - 保证：
    - prepare error 会被记一条
    - accepted / failed / duplicate 都会被记一条
    - 查询 result 本身也会留下查询足迹

- [ ] **Step 3: 新增网关交易审计查询接口**
  - 推荐最小接口：
    - `GET /v2/trade/audit/recent?limit=...`
    - `GET /v2/trade/audit/lookup?id=...`
  - `lookup` 同时接受 `requestId` 与 `batchId`，优先命中单笔，再命中 batch。

- [ ] **Step 4: 在客户端建立本地交易审计存储**
  - `TradeAuditStore` 只存最近本机发起的交易链关键阶段，不替代服务端真值。
  - 本地记录字段至少包含：
    - `traceId`
    - `traceType`
    - `actionSummary`
    - `stage`
    - `status`
    - `message`
    - `createdAt`

- [ ] **Step 5: `TradeExecutionCoordinator` 与 `BatchTradeCoordinator` 写本地审计**
  - 单笔至少记录：
    - 已检查
    - 已提交
    - 已受理待同步
    - 已拒绝
    - 已收敛
    - 结果未确认
  - batch 至少记录：
    - 计划已提交
    - 整批 accepted / partial / failed
    - 强刷是否成功

- [ ] **Step 6: `GatewayV2TradeClient` 补交易审计查询方法与解析**
  - 增加 `auditRecent(limit)`、`auditLookup(id)`。
  - 不把交易审计混进 `GatewayV2Client`，继续保持交易客户端独立。

- [ ] **Step 7: 为审计合同补测试**
  - Python 侧锁定 recent / lookup 返回结构。
  - Android 侧锁定 audit 解析与客户端方法。
  - 执行协调器测试补“accepted / rejected / result unknown 都会落审计”的断言。

- [ ] **Step 8: 跑 Task 2 定向回归**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_trade_audit bridge.mt5_gateway.tests.test_v2_trade_contracts -v
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.remote.v2.GatewayV2TradeClientTest" --tests "com.binance.monitor.ui.trade.TradeAuditStoreTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.ui.trade.BatchTradeCoordinatorTest"
```

Expected:

```text
OK
BUILD SUCCESSFUL
```

**Task 2 完成标准：**

- 任一单笔或 batch 都能按 `requestId / batchId` 查到正式审计记录。
- 客户端本地和服务端最近审计都已建立，不再只剩通用日志。
- 审计层只记录事实，不负责猜测或修正结果。

---

### Task 3: 交易回放 / 排障视图

**目标：** 给用户一个正式页面，能看最近交易、按 `requestId / batchId` 查、并把“从检查到受理到刷新”的时间线整理出来。

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeReplayFormatter.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditViewModel.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/TradeReplayFormatterTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/TradeAuditActivitySourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/theme/LogActivityThemeWiringTest.java`

- [ ] **Step 1: 定义统一回放格式**
  - `TradeReplayFormatter` 把本地审计与网关审计合成：
    - 命令摘要
    - 服务器检查结果
    - 提交结果
    - 幂等/重复说明
    - 账户同步结果
  - 输出必须是用户能直接读懂的“结果清单 + 时间线”，不能只显示原始 JSON。

- [ ] **Step 2: 新建 `TradeAuditActivity`**
  - 页面分两段：
    - 最近交易列表
    - 当前选中条目的回放详情
  - 支持：
    - 最近记录列表
    - 输入 `requestId / batchId` 直接查询
    - 复制追踪结果

- [ ] **Step 3: 在交易结果弹窗里补“查看追踪”入口**
  - `MarketChartTradeDialogCoordinator` 在单笔和 batch 结果弹窗中新增次级按钮：
    - 单笔传 `requestId`
    - batch 传 `batchId`
  - 原有 toast 保留给“已收敛成功”的轻提示，但凡是 rejected / partial / result unknown，都能直接跳追踪页。

- [ ] **Step 4: 给图表页和设置页接交易追踪入口**
  - `GlobalStatusBottomSheetController` 新增“交易追踪”入口，不替代原通用日志，只并列存在。
  - `SettingsActivity / SettingsSectionActivity` 加一个低频入口，保证用户离开图表页后仍能查。

- [ ] **Step 5: ViewModel 侧统一查询 recent + lookup**
  - recent 先读本地，再补最近服务端记录。
  - lookup 以网关审计为主，本地审计为补充。
  - 不允许页面层直接自己拼请求。

- [ ] **Step 6: 为回放页面补测试**
  - `TradeReplayFormatterTest` 锁定 accepted / partial / rejected / unknown 四类时间线文案。
  - `TradeAuditActivitySourceTest` 锁定存在 recent list、lookup、copy 和 replay detail。
  - 保留 `LogActivity` 主题测试，不破坏原通用日志页。

- [ ] **Step 7: 跑 Task 3 定向回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeReplayFormatterTest" --tests "com.binance.monitor.ui.trade.TradeAuditActivitySourceTest" --tests "com.binance.monitor.ui.theme.LogActivityThemeWiringTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

**Task 3 完成标准：**

- 用户能从图表页结果弹窗直接打开对应追踪记录。
- recent list、按 id 查询、复制回放三条能力都已存在。
- 回放视图展示的是“交易事实时间线”，不是通用系统日志列表。

---

### Task 4: 更细粒度的市价交易风控

**目标：** 在不做 DOM 的前提下，把真正影响市价交易安全的几条边界收紧：单笔手数、批量规模、复杂动作强制确认、模板和快速模式的可用范围。

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeRiskGuard.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/ConfigManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeQuickModePolicy.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/trade/BatchTradeCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/TradeRiskGuardTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/TradeQuickModePolicyTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/trade/BatchTradeCoordinatorTest.java`

- [ ] **Step 1: 定义正式风控真值**
  - 建议最小配置项：
    - `maxQuickMarketVolume`
    - `maxSingleMarketVolume`
    - `maxBatchItems`
    - `maxBatchTotalVolume`
    - `forceConfirmForAddPosition`
    - `forceConfirmForReverse`
  - 全部进入 `ConfigManager`，不允许页面单独手写阈值。

- [ ] **Step 2: 新建 `TradeRiskGuard`**
  - 输入：
    - 单笔 `TradeCommand`
    - batch `BatchTradePlan`
    - 当前配置真值
  - 输出：
    - `allowed`
    - `confirmationRequired`
    - `message`
  - 这里既可拦截，也可强制降回确认模式。

- [ ] **Step 3: 收紧快速模式策略**
  - `TradeQuickModePolicy` 不再只看 `OPEN_MARKET <= 0.10`。
  - 必须同时吃 `TradeRiskGuard` 的阈值真值。
  - 模板不能把原本不允许的一键交易变成允许。

- [ ] **Step 4: 单笔执行链接风控**
  - `TradeConfirmDialogController` 先做 guard 判断，再决定：
    - 直接拒绝
    - 强制确认
    - 允许走快速模式
  - `TradeExecutionCoordinator` 在真正提交前再做一次硬拦截，防止页面绕过。

- [ ] **Step 5: batch 执行链接风控**
  - `BatchTradeCoordinator.validatePlan(...)` 扩成正式风控检查，不再只校验 `batchId` 与 `items` 非空。
  - 对“批量项数过多 / 累计手数过大 / 复杂动作超边界”给明确中文提示。

- [ ] **Step 6: 设置页补交易专项低频配置**
  - `SettingsActivity` 新增 `SECTION_TRADE`。
  - `SettingsSectionActivity` 增加交易设置卡片，集中修改默认参数、模板和风控阈值。
  - 不在图表页堆第二套设置面板。

- [ ] **Step 7: 为风控补测试**
  - `TradeRiskGuardTest` 锁定：
    - 小手数市价允许快速模式
    - 大手数市价强制确认
    - 超过单笔阈值直接拒绝
    - batch 项数或总手数超限直接拒绝
    - 加仓 / 反手永远不允许免确认

- [ ] **Step 8: 跑 Task 4 定向回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.trade.TradeRiskGuardTest" --tests "com.binance.monitor.ui.trade.TradeQuickModePolicyTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.ui.trade.BatchTradeCoordinatorTest" --tests "com.binance.monitor.data.local.ConfigManagerSourceTest"
```

Expected:

```text
BUILD SUCCESSFUL
```

**Task 4 完成标准：**

- 快速模式、模板和真实风控之间不再互相打架。
- 市价交易的关键风险边界都已进入正式配置真值。
- 单笔和 batch 都有统一、明确、可读的拦截说明。

---

### Task 5: 总回归、真机安装、阶段文档

**目标：** 用一轮完整回归把第四阶段无 DOM 范围闭合，并把文档口径同步到真实状态。

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: 跑服务端交易相关总回归**

Run:

```powershell
python -m unittest bridge.mt5_gateway.tests.test_v2_trade_contracts bridge.mt5_gateway.tests.test_v2_trade_batch bridge.mt5_gateway.tests.test_v2_trade_audit -v
```

- [ ] **Step 2: 跑 Android 第四阶段最小总回归**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.local.ConfigManagerSourceTest" --tests "com.binance.monitor.data.remote.v2.GatewayV2TradeClientTest" --tests "com.binance.monitor.ui.trade.TradeTemplateRepositoryTest" --tests "com.binance.monitor.ui.trade.TradeAuditStoreTest" --tests "com.binance.monitor.ui.trade.TradeReplayFormatterTest" --tests "com.binance.monitor.ui.trade.TradeRiskGuardTest" --tests "com.binance.monitor.ui.trade.TradeQuickModePolicyTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.ui.trade.BatchTradeCoordinatorTest" --tests "com.binance.monitor.ui.trade.TradeAuditActivitySourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest"
```

- [ ] **Step 3: 编译 APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
```

- [ ] **Step 4: 真机安装与人工验收**

Run:

```powershell
adb -s <deviceId> install -r app\build\outputs\apk\debug\app-debug.apk
adb -s <deviceId> shell am start -W com.binance.monitor/.ui.main.MainActivity
```

人工验收顺序：

```text
1. 快捷交易默认手数是否来自模板/默认值，而不是固定 0.05
2. 统一交易弹窗是否能正确回填默认 SL/TP
3. 单笔提交后是否能在结果页打开对应 requestId 追踪
4. batch 复杂动作后是否能打开 batchId 追踪，并看到总览 + 时间线
5. 风控超限时是否给出明确中文提示，并阻止实际提交
6. 设置页改动默认值/风控阈值后，回到交易页是否立即生效
```

- [ ] **Step 5: 更新文档**
  - `README.md` 把第四阶段正式口径写成“无 DOM 的市价交易效率与排障增强”，不要再写“专业级体验 = DOM”。
  - `ARCHITECTURE.md` 补：
    - `TradeTemplateRepository`
    - `TradeAuditStore / TradeAuditActivity`
    - `TradeRiskGuard`
    - `v2_trade_audit.py`
  - `CONTEXT.md` 记录第四阶段无 DOM 计划或实现停点。

- [ ] **Step 6: 阶段收口判断**
  - 只有以下五项都成立，才能把第四阶段写成“代码完成、待真机复核”：
    - 模板与默认参数已正式接入
    - `requestId / batchId` 审计可查
    - 追踪/回放页可用
    - 风控边界已收口
    - 总回归与 `assembleDebug` 通过

**Task 5 完成标准：**

- 第四阶段无 DOM 范围的代码、测试、APK 编译和文档口径全部闭合。
- DOM 仍明确留在范围外，不被误写成已规划内实现项。

---

## Self-Review

### Scope coverage

- 模板与默认参数：Task 1
- 交易审计与 `requestId / batchId` 查询：Task 2
- 回放与排障视图：Task 3
- 更细粒度风控：Task 4
- 总回归、真机安装、阶段文档：Task 5

### Placeholder scan

- 没有把“排障页”“风控”“模板”停在概念层。
- 每个任务都明确了文件、关键步骤、测试入口和完成标准。
- DOM 已被显式排除，避免后续范围再漂移。

### Dependency check

- Task 3 依赖 Task 2 的审计合同。
- Task 4 同时依赖 Task 1 的配置真值和 Task 2/3 的追踪能力。
- 因此执行顺序不能颠倒。
