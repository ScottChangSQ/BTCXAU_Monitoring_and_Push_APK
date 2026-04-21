# MT5 账户切换设计

## 目标

把“手机端新登录/切换账号”收口成一条单独、可验证、无旧状态干扰的服务端主链。

本设计只解决两类场景：

1. 用户主动新登录或切换账号时，如何驱动服务器端 MT5 切到目标账号。
2. APP 重启后，如何恢复会话而不重复触发整套切号流程。

本设计不解决历史兼容、降级、兜底、探针复用、按旧会话猜测账号等问题。

## 设计范围

### 在范围内

- 新建一条独立的“MT5 终端切号主链”
- 把 `/v2/session/login` 的核心登录动作切到这条新主链
- 明确“新登录/切换账号链”和“APP 重启恢复链”的边界
- 给成功和失败返回统一结构
- 给失败阶段统一真实命名

### 不在范围内

- 不改 Android UI 流程，只调整它消费的服务端结果口径
- 不保留 `active_session / saved profile / .env / probe` 参与切号决策
- 不再把 `server` 作为最终成功条件
- 不在本设计内讨论多 MT5 实例并行控制

## 核心原则

1. 服务端切号只认手机端本次输入的 `login / password / server`。
2. 最终成功只看服务器端 MT5 当前真实账号的 `login` 是否等于目标账号。
3. `server` 只作为附带信息返回，不再参与成功判定。
4. `mt5.login(...)` 的 true/false 不是最终成功结论，只是中间信号。
5. 如果 `mt5.login(...)` 直接抛异常，立刻失败。
6. 如果 `mt5.login(...)` 返回 `false`，继续进入后续轮询确认。
7. 登录主链不再依赖 `active_session / saved profile / .env / probe / reset terminal / exact path ownership`。

## 两条正式链路

### 1. 显式登录/切换账号链

只在以下场景触发：

- 用户新登录账号
- 用户手动切换账号
- 用户注销后再次登录

这条链会真正驱动服务器端 MT5 执行 account switch。

### 2. APP 重启恢复链

只在以下条件下触发：

- 用户没有手动注销
- 用户没有手动切换账号
- APP 只是重启

这条链只做轻量确认，不重新执行 account switch，不重复走 30 秒轮询。

如果轻量确认发现“服务器端当前实际账号已经不是原来的账号了”，则：

- 直接把 APP 状态改成未登录或账号失效
- 不自动切回原账号
- 弹出明确通知：`服务器端当前实际账号已经不是原来的账号了`

## 新的服务端模块边界

建议把“MT5 终端切号”从现有混合登录链里独立出来，形成单独主模块，例如概念上叫：

- `Mt5AccountSwitchFlow`

它只负责 6 类事情：

1. 识别当前是否存在正常 MT5 GUI 主窗口实例
2. 若不存在，则按 `MT5_PATH` 拉起 MT5 并等待窗口就绪
3. 对当前 MT5 执行附着/初始化
4. 读取切换前基线账号
5. 发起一次强制 account switch
6. 轮询确认最终真实账号

现有 `/v2/session/login` 只负责：

1. 接收手机端本次输入
2. 调用 `Mt5AccountSwitchFlow`
3. 原样返回结构化结果

## MT5 终端切号主链

### 步骤 1：检测主窗口

先判断服务器上是否存在一个“正常拉起了主窗口的 MT5 GUI 实例”。

约束：

- 允许不在焦点
- 允许最小化
- 只要正常主窗口实例存在，就算 MT5 可用
- 仅有残留进程但没有主窗口实例，不算可用

### 步骤 2：按需拉起 MT5

如果当前不存在正常主窗口实例，就用 `MT5_PATH` 拉起 MT5。

说明：

- `MT5_PATH` 在本设计里只用于“没有主窗口时的拉起动作”
- 不再用于“当前运行实例是否属于这个路径”的判断

### 步骤 3：等待主窗口就绪

拉起后最多等待 15 秒，直到正常主窗口实例出现。

若 15 秒内仍未出现，直接失败。

### 步骤 4：附着/初始化

主窗口出现后，对当前 MT5 做附着/初始化。

规则：

- 首次失败时不立刻判死
- 允许额外重试 2 次
- 每次重试间隔 3 秒
- 若重试后仍失败，则正式失败

### 步骤 5：读取基线账号

附着成功后，先读取一次“当前终端实际账号信息”，作为切换前基线。

若这一步读不到账号信息，直接失败。

### 步骤 6：强制发起 account switch

不关闭 `mt5.exe`，不退出旧账号。

无论当前是不是同一个账号，都强制发起一次 `account switch`。

规则：

- 若 `mt5.login(...)` 直接抛异常，立刻失败
- 若 `mt5.login(...)` 返回 `false`，不立刻失败，进入后续轮询确认

### 步骤 7：轮询确认最终账号

从切号动作发起后开始，进入 30 秒轮询窗口。

轮询规则：

- 每 2 秒读取一次当前终端真实账号
- 轮询中任意一次读不到账号信息，立刻失败
- 只要读到的 `login` 等于目标账号，就立刻成功
- `server` 不参与成功判定，只作为附带信息记录

### 步骤 8：构造结果

成功时返回：

- 切换前基线账号
- 切换后最终账号
- 本次耗时

失败时返回：

- 真实失败阶段
- `mt5.login(...)` 的原始错误（如果存在且为 false 返回）
- 最后一次实际账号信息

失败结果只保留摘要，不重复堆叠 15 次轮询明细。

## 失败阶段定义

服务端只能使用以下真实失败阶段：

1. `window_not_found_then_launch_failed`
2. `window_not_found_then_window_ready_timeout`
3. `attach_failed`
4. `baseline_account_read_failed`
5. `switch_call_exception`
6. `final_account_read_failed`
7. `switch_timeout_account_not_changed`

各阶段含义：

- `window_not_found_then_launch_failed`
  当前没发现正常主窗口，随后按 `MT5_PATH` 拉起时，拉起动作本身失败。

- `window_not_found_then_window_ready_timeout`
  已执行拉起，但 15 秒内仍未等到正常主窗口实例。

- `attach_failed`
  主窗口已存在，但附着/初始化连同 2 次重试一起都失败。

- `baseline_account_read_failed`
  已附着成功，但切号前读取基线账号失败。

- `switch_call_exception`
  `mt5.login(...)` 直接抛异常。

- `final_account_read_failed`
  切号发起后，30 秒轮询期间某次读取真实账号失败。

- `switch_timeout_account_not_changed`
  30 秒轮询结束，真实账号的 `login` 仍未变成目标账号。

## 结果结构

建议统一返回以下字段：

- `ok`
- `stage`
- `message`
- `elapsedMs`
- `baselineAccount`
- `finalAccount`
- `loginError`
- `lastObservedAccount`

### 成功口径

- `ok=true`
- `stage=switch_succeeded`
- `message` 应直接可展示，包含：
  - 切换前基线账号
  - 切换后最终账号
  - 本次耗时

### 失败口径

- `ok=false`
- `stage` 必须是 7 个失败阶段之一
- `message` 只描述真实失败事实
- 如果 `mt5.login(...)` 当时是 false，则 `loginError` 带原始错误
- 如果最终超时，则 `lastObservedAccount` 带最后一次实际账号信息

## 必须退出主链的旧逻辑

以下内容不能再参与“切号成功与否”的决策：

1. `active_session`
2. `saved profile`
3. `.env`
4. `probe`
5. `reset terminal / stop process`
6. `exact path ownership`
7. `server match as success condition`
8. `mt5.login return value as final verdict`

## 数据流

### 显式登录/切换账号链

1. 手机端提交本次账号信息
2. `/v2/session/login` 调用 `Mt5AccountSwitchFlow`
3. `Mt5AccountSwitchFlow` 完成窗口检测、拉起、附着、基线读取、切号、轮询确认
4. 服务端返回统一结构结果
5. 手机端按 `message + 可复制详情` 展示

### APP 重启恢复链

1. APP 恢复本地当前账号
2. 服务端只做一次轻量确认，读取服务器端当前真实账号
3. 若仍是原账号，则恢复为已登录状态
4. 若已不是原账号，则标记未登录并弹通知

## 错误处理约束

1. 任何错误文案都不能描述未发生的动作。
2. 不允许再出现“已退出服务器当前 MT5 账号”“已重置终端”这类与真实动作不一致的提示。
3. 所有失败必须落到确定的失败阶段。
4. 超时失败必须同时携带：
   - `mt5.login(...)` 原始错误（若存在）
   - 最后一次实际账号信息

## 测试要求

实现时至少覆盖以下测试面：

1. 当前已有正常主窗口时，直接复用当前 MT5
2. 当前无主窗口时，按 `MT5_PATH` 拉起，并在 15 秒内等到主窗口
3. 拉起后 15 秒仍无主窗口时失败
4. 附着首次失败后，按 2 次、每次 3 秒重试
5. 基线账号读取失败时失败
6. `mt5.login(...)` 抛异常时失败
7. `mt5.login(...)` 返回 false 时，仍继续进入 30 秒轮询
8. 轮询中 `login` 变成目标账号时成功
9. 轮询中读取不到账号时失败
10. 30 秒结束后账号未变时，失败返回同时包含 `loginError + lastObservedAccount`
11. APP 重启恢复链只做轻量确认，不重复触发切号主链
12. APP 重启后发现服务器端实际账号已变化时，改成未登录并提示

## 设计结论

本设计的核心是把“服务器端 MT5 切号”从历史混合链里完全抽离，形成一条单一、真实、可验证的主链；同时把“显式登录/切号”和“APP 重启恢复”彻底分开，避免 APP 每次重启都再次触发整套切号流程。
