# Live Trading Terminal Rollout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把当前“行情监控 + 账户展示 + 历史分析”APP，按最短正确路径升级成可安全用于 MT5 实盘交易的移动交易终端。

**Architecture:** 保留现有“Binance 行情真值 + MT5 账户真值”的展示架构，但在服务端补一层独立的交易网关，在 APP 补一层独立的交易领域模型和命令状态机。整体从单纯“快照式读取”升级成“命令式 + 快照式”双轨：用户先发命令、服务端预检查和执行、随后账户真值强一致回写页面。

**Tech Stack:** Python、FastAPI、MetaTrader5 Python API、Java、Android ViewBinding、Room、OkHttp、WebSocket、JUnit4、Python unittest、PowerShell

---

## 范围拆分

这件事不能按一个大任务硬做，必须拆成 4 个子项目顺序推进：

1. **第一阶段：最小交易闭环（必须先做）**
   目标：先让“下单、改单、撤单、平仓”这条主链真正成立。
2. **第二阶段：可用交易终端（必须先做）**
   目标：把图表、确认、预演、拒单回执做成能安全使用的交易界面。
3. **第三阶段：批量与复杂交易（可以后做）**
   目标：补批量操作、复杂平仓和账户模式差异。
4. **第四阶段：专业级体验（可以后做）**
   目标：补 DOM、快捷模板、日志回放和更细的风险控制。

## 文件结构预案

### 服务端

- `bridge/mt5_gateway/server_v2.py`
  继续作为统一入口，新增交易路由挂载与交易后刷新入口。
- `bridge/mt5_gateway/v2_trade.py`
  新建。负责交易参数标准化、`order_check / order_send` 封装、返回码映射、幂等键处理。
- `bridge/mt5_gateway/v2_trade_models.py`
  新建。负责交易请求、预检查结果、执行结果、错误对象的标准模型。
- `bridge/mt5_gateway/v2_trade_sync.py`
  新建。负责交易后强一致刷新、等待账户真值收敛、生成交易事件流。
- `bridge/mt5_gateway/tests/test_v2_trade_contracts.py`
  新建。锁定交易接口结构。
- `bridge/mt5_gateway/tests/test_v2_trade_execution.py`
  新建。锁定预检查、执行、幂等和错误映射。
- `bridge/mt5_gateway/tests/test_v2_trade_sync.py`
  新建。锁定交易后强一致刷新与状态流转。

### Android APP

- `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java`
  扩展或拆分交易请求入口。
- `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java`
  建议新建。专门负责 `trade/check`、`trade/submit`、`trade/result/events`。
- `app/src/main/java/com/binance/monitor/data/model/v2/trade/`
  新建目录。放交易领域模型。
- `app/src/main/java/com/binance/monitor/ui/trade/`
  新建目录。放确认弹窗、交易草稿、执行状态、拒单提示。
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
  第二阶段开始接图表交易入口。
- `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
  第二阶段开始接可拖拽交易线和选中态。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
  第一阶段接入交易后强一致刷新。
- `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java`
  第一阶段接收交易后最新账户快照、挂单、成交结果。
- `app/src/test/java/com/binance/monitor/data/remote/v2/`
  新增交易客户端与交易状态机单测。
- `app/src/test/java/com/binance/monitor/ui/trade/`
  新增确认、拒单、回执和执行流测试。

## Task 1: 第一阶段先补服务端交易网关

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Create: `bridge/mt5_gateway/v2_trade.py`
- Create: `bridge/mt5_gateway/v2_trade_models.py`
- Create: `bridge/mt5_gateway/tests/test_v2_trade_contracts.py`

- [ ] 明确第一阶段只开放 4 个动作：市价开仓、单笔平仓、挂单新增/删除、单笔 TP/SL 修改。
- [ ] 定义统一接口：`/v2/trade/check`、`/v2/trade/submit`、`/v2/trade/result` 或 `/v2/trade/events`。
- [ ] 统一服务端标准错误码，至少覆盖手数不合法、步进不合法、止损止盈距离不足、保证金不足、市场关闭、重报价、超时、重复提交。
- [ ] 接入 `order_check` 和 `order_send`，禁止手机端直接猜测可下单性。
- [ ] 为每笔命令生成 `requestId`，同一 `requestId` 必须幂等。
- [ ] 加入账户模式识别，至少能区分 `netting / hedging`，避免平仓语义混乱。
- [ ] 交易接口单测先覆盖“结构正确、幂等可用、错误可区分”，再允许接真执行。

**第一阶段完成标准：**
- APP 不需要猜测任何交易是否可发。
- 服务端能明确返回“可执行 / 不可执行 / 已受理 / 执行失败”的标准结果。
- 同一请求重复点击不会重复下单。

## Task 2: 第一阶段补 APP 交易领域模型与状态机

**Files:**
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeIntent.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeCheckResult.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeCommand.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/TradeReceipt.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/ExecutionError.java`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/BrokerConstraint.java`
- Create: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeCommandStateMachine.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradeCommandStateMachineTest.java`

- [ ] 新模型不要复用 `PositionItem`、`TradeRecordItem` 这种展示对象，交易对象和展示对象分离。
- [ ] 固定命令状态：`Draft -> Checking -> Confirming -> Submitting -> Accepted / Rejected / Timeout -> Settled`。
- [ ] 状态机里明确“检查成功不等于已下单”“已受理不等于已结算”。
- [ ] `GatewayV2TradeClient` 只负责交易命令，不混进原有行情/账户读取客户端。
- [ ] 每次命令都带 `requestId`、`accountId`、`symbol`、`action`、`volume`、`price`、`sl`、`tp`。
- [ ] 单测锁定“重复点击、超时、拒单、已受理但未同步完成”这几种关键分支。

**第一阶段完成标准：**
- 用户每点一次交易按钮，页面都知道自己处于哪一步，而不是只有“成功/失败”两个粗状态。
- 任意失败都能落到明确状态，不能出现界面无响应但后台可能已下单的灰区。

## Task 3: 第一阶段补统一确认与交易后强一致刷新

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`

- [ ] 第一阶段统一确认弹窗必须先存在，默认不开一键交易。
- [ ] 交易发出后立即进入“待确认/提交中”，不能先乐观改本地仓位。
- [ ] 服务端返回已受理后，立即触发账户强一致刷新，不走旧的“等下次轮询自然更新”。
- [ ] 刷新完成的判定至少包含：持仓、挂单、历史成交、保证金/净值四项已更新。
- [ ] 如果超时，界面必须保留“结果未确认”态，不得直接当失败处理。
- [ ] 如果服务端明确拒绝，页面必须给出拒绝原因并回退到安全态。

**第一阶段完成标准：**
- 开仓成功后，仓位、挂单、净值会在同一条交易流程内收敛。
- 平仓成功后，图表和账户页不会继续挂着旧止损线或旧仓位。

## Task 4: 第一阶段补最小安全、审计与测试门槛

**Files:**
- Create: `bridge/mt5_gateway/v2_trade_audit.py`
- Modify: `bridge/mt5_gateway/server_v2.py`
- Create: `bridge/mt5_gateway/tests/test_v2_trade_audit.py`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradeRiskGuardTest.java`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] 记录最小审计日志：`requestId / accountId / symbol / action / volume / time / result`。
- [ ] 一键交易授权先不开放，只做确认弹窗模式。
- [ ] 交易密码或二次验证先预留接口位，第一阶段至少把配置和流程边界定好。
- [ ] 测试覆盖：预检查、拒单、超时、重复提交、交易后同步、`netting / hedging` 基础识别。
- [ ] README 和架构文档明确写清第一阶段只支持哪些交易动作，不夸大能力。

**第一阶段完成标准：**
- 任何一笔命令都能追到 requestId。
- 没有确认开关的情况下，不会出现“误触即成交”。

## Task 5: 第二阶段把图表升级成可交易图表

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/TradeLineDraft.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/TradeLineStateResolver.java`
- Create: `app/src/test/java/com/binance/monitor/ui/chart/TradeLineStateResolverTest.java`

- [ ] 把现有 `PriceAnnotation` 展示层和后续交易线状态层拆开，不能继续混成一种标注。
- [ ] 图上至少区分：持仓线、挂单线、SL 线、TP 线、草稿线。
- [ ] 每条线加入选中态和拖拽态。
- [ ] 拖拽后先生成草稿，不直接提交真实命令。
- [ ] 颜色固定分层：草稿、提交中、已生效、被拒绝回退。
- [ ] 图上支持改单、删单、平仓，但所有动作都走第一阶段统一交易主链。

**第二阶段完成标准：**
- 图表可以直接发起交易动作，但不会绕过服务端预检查和确认流程。
- 用户能一眼分清“草稿线”和“已生效线”。

## Task 6: 第二阶段补预演、拒单回执和快速模式边界

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradePreviewCalculator.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeRiskModeManager.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/TradeRejectMessageMapper.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/ConfigManager.java`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/TradePreviewCalculatorTest.java`

- [ ] 下单前显示最少 5 项：手数、保证金占用、止损金额、止盈金额、可用保证金变化。
- [ ] 拒单原因统一翻译成用户能看懂的话，不直接把底层错误码暴露成原始字符串。
- [ ] 安全模式默认开启，快速模式默认关闭。
- [ ] 快速模式开启后，也只允许少数动作免二次确认，不能覆盖批量操作和大手数操作。
- [ ] 风险阈值超限时强制退回确认模式。

**第二阶段完成标准：**
- 用户能在发单前看到风险代价。
- 快速模式有清晰边界，不会变成全局免确认。

## Task 7: 第三阶段补批量交易与复杂平仓

**Files:**
- Create: `bridge/mt5_gateway/v2_trade_batch.py`
- Create: `app/src/main/java/com/binance/monitor/data/model/v2/trade/BatchTradePlan.java`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/BatchTradeCoordinator.java`
- Create: `bridge/mt5_gateway/tests/test_v2_trade_batch.py`
- Create: `app/src/test/java/com/binance/monitor/ui/trade/BatchTradeCoordinatorTest.java`

- [ ] 批量交易必须有 `batchId`，不能只是前端循环调单笔接口。
- [ ] 明确三种执行策略：尽力执行、全有或全无、分组执行。
- [ ] 每一项结果都要可追踪，不能只给一个整批成功/失败。
- [ ] 部分成功时，界面必须能展示哪些已成交、哪些失败。
- [ ] 在这阶段补 `Close By`、部分平仓、加仓、反手等复杂语义。
- [ ] 把 `netting / hedging` 的差异在批量层完整跑通。

**第三阶段完成标准：**
- “批量平仓 10 单”执行后，用户能看清每一单结果。
- 不会因为账户模式不同，把反手、加仓、减仓混成同一种操作。

## Task 8: 第四阶段补 DOM 与专业级体验

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/trade/dom/`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/template/`
- Create: `app/src/main/java/com/binance/monitor/ui/trade/audit/`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] DOM 交易单独做，不和普通图表交易混成一套交互。
- [ ] 常用下单模板、默认手数、默认止损止盈记忆单独管理。
- [ ] 交易日志支持按 `requestId`、品种、时间过滤。
- [ ] 补交易回放和错误追踪视图，方便排查“用户说点了但没成交”。
- [ ] 高级风控再做更细粒度：批量阈值、夜盘限制、特定品种限制、模板权限。

**第四阶段完成标准：**
- 专业级功能建立在前 3 阶段稳定之后，而不是提前压进主链。

## Task 9: 文档、联调和阶段验收

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Create: `docs/superpowers/specs/2026-04-06-live-trading-terminal-design.md`

- [ ] 每完成一个阶段，都同步更新 `CONTEXT.md` 当前状态、停点和关键决定。
- [ ] 第一阶段完成前，不对外宣称“已支持实盘终端”，只能说“已支持最小交易闭环”。
- [ ] 每阶段结束都做一次真机联调，重点看“操作后状态是否收敛一致”。
- [ ] 每阶段结束都补文档，防止后续继续把展示对象和交易对象混用。

## 阶段验收口径

### 第一阶段必须通过

- 服务端有交易网关
- APP 有交易状态机
- 有统一确认
- 交易后强一致刷新可见
- 最小审计存在
- 单笔开仓 / 平仓 / 挂单 / 改 TP/SL 闭环成立

### 第二阶段必须通过

- 图表可交易
- 拒单原因可读
- 风险预演可读
- 快速模式有边界

### 第三阶段可以后做

- 批量交易
- Close By
- 完整 `netting / hedging`

### 第四阶段可以后做

- DOM
- 模板
- 回放
- 高级风控
