# Live Trading Phase 2 Next Steps

**目标：** 把当前“第一阶段已闭环、第二阶段已插入快捷交易但未闭合”的状态，收口成真正可验收的第二阶段。

## 当前判断

- 第一阶段最小交易闭环已经完成，正式主链仍然是 `check -> confirm -> submit -> sync`。
- 第二阶段已提前落下一部分：图表内快捷交易、挂单模式、可拖挂单线已经存在。
- 第二阶段真正未闭合的缺口仍是 4 月 13 日路线里定义的四块：
  - 风险预演
  - 统一拒单翻译
  - 交易状态分层
  - 快速模式边界

结论：下一步不该继续扩展第三阶段复杂交易，也不该继续做更多图表炫交互，而是先把第二阶段缺口补齐并做真机验收。

---

## 下一步工作顺序

### 1. 风险预演与确认内容升级

**目的：** 先解决“用户敢不敢点、看不看得懂”。

**优先级：** 最高

**要做什么：**

- 在交易确认链里补一层正式风险预演数据：
  - 手数
  - 预计保证金占用
  - 可用保证金变化
  - 止损金额
  - 止盈金额
- 把确认弹窗从当前的单句提示，升级成“动作摘要 + 风险摘要”。
- 快捷交易入口和旧统一交易弹窗都复用同一份预演结果，不分叉。

**建议关注文件：**

- `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
- `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- `app/src/main/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinator.java`
- `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`

**完成标准：**

- 交易确认不再只显示“请确认本次交易后再提交”。
- 市价、挂单、改单三类动作都能展示统一的风险摘要。
- 快捷交易与旧交易入口显示口径一致。

### 2. 统一拒单翻译层

**目的：** 让失败结果可理解，而不是只把底层原文直接甩给用户。

**优先级：** 高

**要做什么：**

- 建一个统一拒单翻译入口，把 MT5 / 网关错误码翻成稳定中文。
- 图表快捷交易、交易弹窗、账户页相关交易结果统一走这层。
- 区分“业务拒单”“结果未确认”“已受理待同步”，不要混成一类提示。

**建议关注文件：**

- `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- `app/src/main/java/com/binance/monitor/data/model/v2/trade/ExecutionError.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`

**完成标准：**

- 常见拒单场景都有固定中文文案。
- “超时待确认”和“明确拒单”在 UI 上能分清。
- 快捷交易与普通交易入口看到的是同一套结果文案。

### 3. 图表交易状态分层

**目的：** 把“真实线”和“草稿线”彻底分开，避免现在的快捷挂单线继续长成半成品。

**优先级：** 高

**要做什么：**

- 正式定义图表交易线状态：
  - 已生效线
  - 草稿线
  - 选中态
  - 拖拽态
  - 提交中
  - 拒绝回退
- 不再让 `PriceAnnotation` 同时承担真实展示和草稿交互。
- 让当前快捷挂单线自然升级到这套状态层，不另起第三套实现。

**建议关注文件：**

- `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- `app/src/test/java/com/binance/monitor/ui/chart/KlineChartViewSourceTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`

**完成标准：**

- 用户能肉眼区分“真实生效”和“正在编辑”的线。
- 拖拽结束后先形成草稿，不直接改真实状态。
- 拒单后草稿能回退，不污染真实线。

### 4. 快速模式边界

**目的：** 补第二阶段最后一块安全边界，避免快捷交易继续变成“更快但更危险”。

**优先级：** 中

**要做什么：**

- 默认仍保持安全模式。
- 明确哪些动作允许快捷，哪些动作仍强制确认。
- 对大手数、高风险、复杂改单保持强制确认。

**建议关注文件：**

- `app/src/main/java/com/binance/monitor/ui/trade/TradeConfirmDialogController.java`
- `app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`

**完成标准：**

- 快速模式默认关闭。
- 允许免确认的动作是白名单，不是全局放开。
- 文档和真机行为一致。

### 5. 第二阶段总验收

**目的：** 把“已写代码”变成“已闭环阶段”。

**优先级：** 收口项

**要做什么：**

- 跑交易主链定向单测。
- 编译 `assembleDebug`。
- 真机验：
  - 市价买入 / 卖出
  - 挂单买入 / 卖出
  - 平仓后结果提示
  - 拒单提示
  - 已受理待同步
  - 同步收敛后账户页 / 图表页一致

**建议关注文件：**

- `README.md`
- `CONTEXT.md`
- `app/src/test/java/com/binance/monitor/ui/trade/TradeExecutionCoordinatorTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/ChartQuickTradeCoordinatorTest.java`
- `app/src/test/java/com/binance/monitor/ui/chart/MarketChartTradeSourceTest.java`

**完成标准：**

- 第二阶段四块缺口全部闭合。
- 真机上“已受理 -> 同步 -> 收敛”没有明显灰区。
- 到这一步后，才进入第三阶段复杂交易。

---

## 不建议现在做的事

- 不建议直接跳去第三阶段的批量交易、部分平仓、Close By。
- 不建议继续优先做更复杂的图表拖线交互。
- 不建议为了快捷交易再造第二套提交链。
- 不建议把账户历史刷新延迟问题混进本轮计划；那条线应继续按 `historyRevision` 和服务端 `history_deals_get(...)` 真值单独排查。

## 一句话版排序

先补“风险预演 + 拒单翻译”，再补“图表状态分层”，然后补“快速模式边界”，最后做真机验收；第二阶段闭合前，不进入第三阶段。
