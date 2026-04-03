# Binance 行情 + MT5 账户新架构设计

## 1. 背景

当前 APP 的核心问题不是单点 bug，而是“历史真值、实时补丁、本地缓存、页面合并”职责混杂，导致以下现象会反复出现：

- K 线与 Binance 官方图不一致
- 成交量与指标一起漂移
- 未收盘最后一根和历史真值互相污染
- 本地旧缓存恢复后继续参与错误合并
- 账户历史、持仓、净值曲线排查成本高

用户已确认本次可以接受停服，并希望一次性切换到新架构，不再维护旧架构兼容层。

## 2. 本次设计目标

按优先级排序：

1. 绝对一致
2. 刷新快
3. 离线后恢复快

同时满足以下边界：

- `BTCUSDT` 与 `XAUUSDT` 的行情、K 线、成交量、指标基础数据统一来自 Binance
- MT5 只负责账户相关真值：持仓、挂单、历史成交、净值、结余
- 服务端负责多周期生成、历史修正、断线补齐、时间口径统一
- APP 只负责展示、本地快照缓存、断线恢复
- 本次允许停服并一次性切换到新架构

## 3. 非目标

以下内容不在本次设计范围内：

- 继续修补旧的 APP 本地拼 K 线策略
- 同时维护旧架构和新架构双写双读
- 保留多个历史缓存来源共同参与真值判断
- 让 APP 继续承担最终数据推导职责

## 4. 新架构总览

新架构拆成 3 个服务端模块和 3 个 APP 模块。

### 4.1 服务端模块

1. `Binance Market Service`
   - 负责 `BTCUSDT` 与 `XAUUSDT` 的行情真值
   - 拉取历史 K 线
   - 接收实时 K 线流
   - 生成所有周期的闭合 K 线真值和当前未收盘补丁

2. `MT5 Account Service`
   - 负责账户真值
   - 提供当前账户快照、持仓、挂单、历史成交、净值、结余
   - 维护“原始事实层”和“展示模型层”

3. `Snapshot & Sync Service`
   - 对 APP 输出可直接使用的快照和增量补丁
   - 负责统一 `syncToken`
   - 负责断线补齐和启动恢复

### 4.2 APP 模块

1. `View 层`
   - 页面只显示，不参与最终计算

2. `Local Snapshot 层`
   - 保存最近可直接显示的图表和账户快照

3. `Sync 层`
   - 启动先读本地快照
   - 再拉服务端快照
   - 在线时吃 WebSocket 与 delta
   - 断线恢复时按 `syncToken` 补差

## 5. 真值边界

新架构的底线是“单一真值”。

### 5.1 行情真值

- `BTCUSDT`、`XAUUSDT` 的行情真值只来自 Binance
- 历史闭合 K 线是真值
- 当前未收盘的一根是实时补丁
- 真值与补丁分层存储，禁止混写

### 5.2 账户真值

- 当前状态只来自 MT5 当前接口
- 历史事实只来自 MT5 历史接口
- APP 页面使用的生命周期交易、净值曲线等展示模型由服务端统一整理
- APP 不再重建最终生命周期真相

### 5.3 时间真值

- 所有时间统一在服务端归一
- APP 不再做加减小时差、时间补正、K 线归桶修正

## 6. 服务端数据模型

### 6.1 Market Candle

用途：保存已经确认的闭合 K 线真值。

字段建议：

- `symbol`
- `interval`
- `openTime`
- `closeTime`
- `open`
- `high`
- `low`
- `close`
- `volume`
- `quoteVolume`
- `tradeCount`
- `source`
- `isClosed`
- `version`

约束：

- 唯一键：`symbol + interval + openTime`
- 只保存闭合 K 线
- 版本化更新，不靠多层缓存覆盖

### 6.2 Market Realtime Patch

用途：保存当前未收盘 K 线的实时补丁。

字段建议：

- `symbol`
- `interval`
- `openTime`
- `eventTime`
- `open`
- `high`
- `low`
- `close`
- `volume`
- `quoteVolume`
- `tradeCount`
- `isClosed`

约束：

- 每个 `symbol + interval` 只保留最新一根未收盘 patch
- 一旦闭合，转入 `Market Candle`
- 禁止把 patch 写回历史真值表后继续当 patch 使用

### 6.3 Account Snapshot

用途：保存当前账户状态。

字段建议：

- `accountId`
- `snapshotTime`
- `balance`
- `equity`
- `margin`
- `freeMargin`
- `marginLevel`
- `profit`
- `positions[]`
- `orders[]`

约束：

- 整体替换
- 只代表当前状态，不代表历史事实

### 6.4 Account History Fact

用途：保存 MT5 历史原始事实。

字段建议：

- `accountId`
- `type`
- `ticket`
- `orderId`
- `dealId`
- `positionId`
- `symbol`
- `side`
- `entryType`
- `volume`
- `price`
- `commission`
- `swap`
- `profit`
- `time`
- `rawPayload`
- `version`

约束：

- 保留可追溯原始记录
- 展示模型与原始事实分层

## 7. 服务端接口设计

### 7.1 `GET /v2/market/snapshot`

用途：

- APP 启动
- 断线重连后整体恢复
- 首屏秒开后的服务端真值替换

返回建议：

- `serverTime`
- `symbols`
- `selectedSymbol`
- `defaultIntervals`
- `market`
- `account`
- `syncToken`

设计原则：

- 一次返回 APP 首屏可显示的关键快照
- 避免 APP 启动时再等多接口拼装

### 7.2 `GET /v2/market/candles`

参数：

- `symbol`
- `interval`
- `limit`
- `beforeOpenTime` 可选
- `afterOpenTime` 可选

返回建议：

- `symbol`
- `interval`
- `serverTime`
- `candles[]`
- `latestPatch`
- `nextSyncToken`

设计原则：

- `candles[]` 只返回闭合真值
- `latestPatch` 单独返回未收盘实时补丁

### 7.3 `GET /v2/account/snapshot`

用途：

- 刷新账户卡片
- 刷新当前持仓和挂单

返回建议：

- `account snapshot`
- `positions`
- `orders`
- `syncToken`

### 7.4 `GET /v2/account/history`

参数：

- `range`
- `cursor`

返回建议：

- `trades`
- `orders`
- `curvePoints`
- `nextCursor`
- `syncToken`

设计原则：

- 返回展示模型
- 不把 MT5 半加工原始结果直接交给 APP 再推导

### 7.5 `GET /v2/sync/delta`

参数：

- `syncToken`

返回建议：

- `marketDelta`
- `accountDelta`
- `nextSyncToken`

设计原则：

- 在线增量与断线补齐共用一套语义

### 7.6 `WS /v2/stream`

消息类型：

- `marketPatch`
- `marketClosedCandle`
- `accountSnapshotChanged`
- `accountHistoryChanged`

统一字段：

- `type`
- `entityId`
- `serverTime`
- `version`
- `syncToken`

设计原则：

- 不再用一个混乱事件流同时表达多种口径
- 每类事件都有明确职责

## 8. APP 读取流程

### 8.1 启动流程

1. 读取本地 `snapshot cache`
2. 先显示本地快照
3. 调用 `GET /v2/market/snapshot`
4. 用服务端快照整体替换本地数据
5. 建立 `WS /v2/stream`
6. 在线时持续消费增量
7. WebSocket 不可用时退回 `GET /v2/sync/delta`

目标：

- 首屏快
- 真值统一
- 不靠本地推导恢复页面

### 8.2 切周期流程

1. 用户切到某个周期
2. 先读本地 `symbol + interval` 快照
3. 若有缓存，立即显示
4. 请求 `GET /v2/market/candles`
5. 用返回的闭合 K 线整体替换
6. 若有 `latestPatch`，只覆盖当前未收盘显示层
7. 后续继续消费该周期 patch

目标：

- 先快后准
- 但最终真值只认服务端

### 8.3 断线恢复流程

1. 保存最近 `syncToken`
2. 网络恢复后重连 WebSocket
3. 同时请求 `GET /v2/sync/delta`
4. 若增量可补齐，则补增量
5. 若增量已失效，则重新拉 snapshot
6. 用新 snapshot 覆盖本地旧状态

目标：

- 不靠 APP 自己推断缺了哪些 K 线或账户记录

## 9. APP 本地存储策略

本地只保留以下 3 类缓存：

1. `market_snapshot_cache`
   - 首页与图表页最近一次可直接显示快照

2. `market_series_cache`
   - 每个 `symbol + interval` 的闭合 K 线列表
   - 单独附带当前 `latestPatch`

3. `account_snapshot_cache`
   - 最近账户快照
   - 最近历史记录与曲线结果

明确禁止：

- 内存缓存、文件缓存、Room 历史、页面临时覆盖共同决定最终真值
- 同一份缓存同时存闭合 K 线和未收盘 K 线并混合覆盖

## 10. 一次性切换迁移方案

由于用户明确允许停服，本次采用一次性切换，不再保留旧架构兼容层。

### 10.1 切换顺序

1. 停服
2. 上线新服务端数据模型
3. 上线新服务端接口
4. APP 改读新接口
5. 清理旧缓存结构
6. 首次发布新架构 APK
7. 启动后按新快照链路重新建本地缓存

### 10.2 切换前准备

1. 对 Binance 行情真值与当前旧结果做差异校验
2. 对 MT5 当前状态和历史事实做差异校验
3. 确认新接口能完整返回首屏、图表页、账户页所需字段
4. 准备缓存版本升级与旧缓存清空策略

### 10.3 切换后校验

行情校验：

- 同一时刻、同一周期对比 Binance 官方图
- 检查开高低收、成交量、最后一根未收盘状态

账户校验：

- 对比 MT5 当前持仓、挂单、历史成交、净值、结余
- 确认展示模型能追溯到原始事实

同步校验：

- 冷启动秒开
- 断网后恢复补差
- 切周期稳定
- 不出现旧缓存回灌

## 11. 旧逻辑删除清单

### 11.1 APP 侧要删除或降级的职责

删除：

- 本地用成交或 1m 数据拼高周期 K 线作为最终答案
- 页面层自行决定历史真值与实时尾部如何混合
- APP 自己做时间偏移修正
- 多份缓存共同参与最终真值判断

降级为展示/缓存职责：

- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- `app/src/main/java/com/binance/monitor/data/remote/WebSocketManager.java`
- `app/src/main/java/com/binance/monitor/data/local/KlineCacheStore.java`
- `app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java`

### 11.2 服务端要删除或避免继续扩大的职责

- 一个接口同时混行情真值和账户真值
- 把半加工 MT5 原始结果丢给 APP 再补
- 时间偏移逻辑散落在多个函数里
- 历史真值和当前状态写入同一可变结构反复覆盖

## 12. 风险与控制

### 12.1 主要风险

1. 一次性切换后旧缓存污染新页面
2. 某些页面仍偷偷依赖旧字段
3. XAUUSDT 若在 Binance 与预期展示模式不同，容易出现“看起来像不一致”
4. 账户展示模型若漏字段，会导致 APP 页面无法完全替换旧链路

### 12.2 控制措施

1. 切换时提高缓存版本并清空旧结构
2. APP 统一从新 snapshot 入口读数据
3. 用单一 `syncToken` 管控增量恢复
4. 在服务端保留原始事实层，便于追溯

## 13. 结论

本次推荐方案是“服务端单一真值 + APP 轻展示”的一次性切换架构。

它比继续修补旧链路更适合当前项目，原因是：

1. 能从结构上消掉 K 线真值污染
2. 能统一 BTCUSDT 与 XAUUSDT 的行情链路
3. 能把 MT5 账户真值和 Binance 行情真值彻底拆开
4. 更符合“绝对一致、刷新快、离线恢复快”的优先级

下一步在用户确认本设计文档后，再进入实施计划编写，不直接跳到代码修改。
