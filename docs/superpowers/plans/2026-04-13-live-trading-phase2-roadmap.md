# Live Trading Phase 2 Roadmap

**目标：** 在第一阶段“最小交易闭环”已经成立的基础上，把现有交易入口升级成真正可用、可理解、可控的交易终端界面。  

**当前判断：** 第一阶段核心主链已经完成，可以进入第二阶段；但第二阶段不应从“图上拖线”直接开做，而应先把“风险预演、拒单可读、状态表达”补齐，再把这些能力接到图表交互层。

---

## 1. 第一阶段完成情况梳理

### 1.1 已完成的核心能力

- 服务端交易网关已经存在，并且已经接入统一校验、提交、幂等和结果查询：
  - `/v2/trade/check`
  - `/v2/trade/submit`
  - `/v2/trade/result`
- 服务端交易动作已经覆盖第一阶段主范围：
  - 市价开仓
  - 单笔平仓
  - 挂单新增
  - 挂单删除
  - 单笔 TP/SL 修改
  - 挂单修改 `PENDING_MODIFY`
- 服务端已经统一承担：
  - `order_check`
  - `order_send`
  - `requestId` 幂等
  - `netting / hedging` 识别与分支
  - 交易成功后的账户运行态失效与立即同步发布
- App 侧交易领域模型和命令状态机已经落地：
  - `TradeCommandStateMachine`
  - `GatewayV2TradeClient`
  - `TradeExecutionCoordinator`
  - `TradeConfirmDialogController`
- App 已经采用“先确认、再提交、再等待同步”的正式链路，不会在提交前先乐观改本地仓位。
- 交易提交后，页面已经能表达“已受理，等待同步”，不会把“已受理但未收敛”误报成失败。
- 图表页和账户持仓页已经接入正式交易主链，不再各自维护第二套交易逻辑。

### 1.2 已有的代码与测试证据

- 服务端：
  - [server_v2.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/server_v2.py)
  - [v2_trade.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_trade.py)
  - [v2_trade_models.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_trade_models.py)
  - [test_v2_trade_contracts.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/tests/test_v2_trade_contracts.py)
- App：
  - [GatewayV2TradeClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java)
  - [TradeCommandStateMachine.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeCommandStateMachine.java)
  - [TradeExecutionCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java)
  - [TradeConfirmDialogController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java)
  - [MarketChartTradeDialogCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java)
- 关键测试：
  - [GatewayV2TradeClientTest.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClientTest.java)
  - [TradeCommandStateMachineTest.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/test/java/com/binance/monitor/ui/trade/TradeCommandStateMachineTest.java)
  - [TradeExecutionCoordinatorTest.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java)

### 1.3 当前仍要如实标注的边界

- 第一阶段“核心交易闭环”已经完成，但原计划里的“独立交易审计模块”没有看到单独落成 `v2_trade_audit.py` 这一层。
- 当前图表页虽然已经有交易按钮和统一交易弹窗，但还不是第二阶段定义里的“可交易图表”：
  - 还没有正式的草稿线状态层
  - 还没有拖拽态和选中态
  - 还没有下单前风险预演
  - 还没有用户可读的统一拒单翻译层
  - 还没有快速模式边界

结论：第一阶段的“底层交易主链”已经够用，第二阶段可以开始；但第二阶段应被视为“在现有主链之上补可用性”，不是重做交易底座。

---

## 2. 第二阶段的最短工作路径

## 总原则

- 不新增第二套交易主链。
- 不让图表交互直接绕过现有 `check -> confirm -> submit -> sync` 链路。
- 先把“用户能不能看懂、敢不敢点”解决，再做“图上拖线”。

## 推荐执行顺序

### Task A：先补可读的风险预演与拒单表达

**目的：** 让现有交易弹窗先具备“看得懂”的能力，再升级交互。

**要做什么：**

- 新增下单前风险预演模型，至少展示：
  - 手数
  - 预计保证金占用
  - 止损金额
  - 止盈金额
  - 可用保证金变化
- 新增统一拒单翻译层，把 MT5/网关错误翻成用户能理解的话。
- 保留现有统一确认弹窗，但把确认内容升级成“风险摘要 + 动作摘要”。

**为什么先做：**

- 如果先做拖拽交易线，没有风险预演和拒单翻译，用户只能看到“能拖”，看不到“代价”和“为什么失败”，风险最高。

### Task B：把图表展示线和交易草稿线分层

**目的：** 把“已生效的真实线”和“用户正在编辑的草稿线”彻底分开。

**要做什么：**

- 在图表层新建交易线状态模型：
  - 持仓线
  - 挂单线
  - SL 线
  - TP 线
  - 草稿线
- 颜色和状态固定分层：
  - 草稿
  - 已选中
  - 拖拽中
  - 提交中
  - 已生效
  - 被拒绝回退
- 现有 `PriceAnnotation` 和交易线状态不要继续混用。

**为什么第二步做：**

- 只有先把状态层拆开，后面的拖拽和选中才不会污染现有图表标注链。

### Task C：把图上交互接到现有第一阶段主链

**目的：** 让图表真正能发起交易，但提交方式仍然是第一阶段已有的正式链。

**要做什么：**

- 给交易线加入选中态和拖拽态。
- 拖拽结束只生成草稿，不直接提交。
- 点确认后仍调用现有：
  - `GatewayV2TradeClient`
  - `TradeExecutionCoordinator`
  - `AccountStatsPreloadManager`

**关键约束：**

- 图上不能直接改真实持仓。
- 图上动作必须继续受 `positionTicket / orderTicket` 硬校验保护。
- 图上交互只是“新的输入方式”，不是“新的执行链路”。

### Task D：补安全模式与快速模式边界

**目的：** 给第二阶段加入有限提速，但不破坏第一阶段的安全边界。

**要做什么：**

- 默认保持安全模式。
- 快速模式默认关闭。
- 即使开启快速模式，也只允许少数低风险动作跳过二次确认。
- 大手数、风险超限、复杂改单仍强制回到确认模式。

**为什么放在后面：**

- 如果先开快速模式，再去补边界，最容易把第二阶段做成“更快，但更危险”。

### Task E：第二阶段验收

**必须通过：**

- 图表可以直接发起交易动作
- 草稿线和已生效线肉眼可区分
- 拒单原因可读
- 下单前风险代价可读
- 快速模式有明确边界
- 所有图上动作仍复用第一阶段交易主链

---

## 3. 第二阶段实施分工建议

### 3.1 输入

- 现有图表标注数据
- 现有交易命令工厂和执行协调器
- 现有账户刷新与同步收敛链

### 3.2 处理流程

1. 图表选中真实线或生成草稿线  
2. 本地预演风险与参数变化  
3. 用户确认  
4. 走服务端 `trade/check`  
5. 走服务端 `trade/submit`  
6. 等待账户真值同步收敛  
7. 回写图表状态和账户状态

### 3.3 状态变化

- `展示线` 不等于 `草稿线`
- `草稿线` 不等于 `已提交线`
- `已受理` 不等于 `已结算`
- `已拒绝` 必须回退到安全态

### 3.4 输出

- 图上可理解的交易状态
- 弹窗里可理解的风险和结果说明
- 账户页、图表页、挂单线、持仓线同步收敛

### 3.5 上下游影响

- 上游不需要重做服务端交易接口
- 中游要新增图表交易状态层
- 下游测试要补 UI 状态、拒单文案、风险预演和真机拖拽验收

---

## 4. 第二阶段不建议的路径

- 不建议一上来先做拖拽线和复杂图表交互。  
  原因：交互看起来最显眼，但不是当前最短路径；先做它，会把风险解释、错误提示和状态表达继续拖后。

- 不建议为了第二阶段再造一套“图表专用提交逻辑”。  
  原因：第一阶段已经把正式交易主链建好了，第二阶段应该复用，而不是分叉。

- 不建议在第二阶段提前塞入批量交易、Close By、部分平仓、DOM。  
  原因：这些都属于第三、四阶段，会把当前目标从“可用”拉成“复杂但不稳”。

---

## 5. 第三阶段与第四阶段路线

### 第三阶段：批量与复杂交易

**目标：** 在第二阶段稳定后，再补复杂交易语义。

**建议范围：**

- 批量交易
- 部分平仓
- 加仓 / 反手
- Close By
- 更完整的 `netting / hedging` 差异处理

**开始条件：**

- 第二阶段图表交易稳定
- 第二阶段拒单与风险提示稳定
- 真机联调已证明“已受理 -> 同步 -> 收敛”没有明显灰区

### 第四阶段：专业级体验

**目标：** 在交易可靠的前提下，再补专业工具。

**建议范围：**

- DOM
- 模板
- 默认手数 / 默认止损止盈记忆
- 按 `requestId` 追交易日志
- 交易回放与排障视图
- 更细粒度风控

---

## 6. 当前建议的实施口径

如果现在开始第二阶段，建议按下面的顺序推进：

1. 先做风险预演和拒单翻译  
2. 再做图表交易状态分层  
3. 再做拖拽、选中和草稿线  
4. 最后补快速模式边界和第二阶段验收  

这个顺序比“先做拖拽图表”更短，也更安全。
