# Data Transport & Update Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把“服务器账户真值 -> 客户端统一运行态 -> 图表/账户页/悬浮窗绑定”这条链路彻底收口，减少重复拉取、重复组装和重复绑定，让高频更新只走一条主链。

**Architecture:** 先从服务器收口账户运行态和强一致刷新接口，再压客户端账户双轨组装链，最后让图表、账户条目和悬浮窗只消费同一份 `UnifiedRuntimeSnapshotStore`。这轮不再新增缓存层，优先删重、收职责、压 UI 绑定频次。

**Tech Stack:** Python FastAPI、Android Java、OkHttp、ViewBinding、现有 `Screen/Coordinator` 模式、Gradle 单测、ADB

---

## 0. 文件边界与职责锁定

### 0.1 服务器主链文件

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_account.py`
- Modify: `bridge/mt5_gateway/v2_session_manager.py`
- Test: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`
- Test: `bridge/mt5_gateway/tests/test_v2_session_manager.py`

- [ ] 先明确服务器侧 4 类职责边界：
  - `server_v2.py` 只负责 HTTP/WS 路由、bus 发布、缓存失效和主流程编排。
  - `v2_account.py` 只负责账户响应模型拼装，不参与缓存和节拍。
  - `v2_session_manager.py` 只负责当前激活会话与账号切换，不参与账户快照派生。
  - 测试层分别锁定“响应契约”“发布节拍”“会话切换失效”。
- [ ] 在开始改代码前，先把“账户运行态真值”“账户历史真值”“客户端可见发布态”三者写成代码注释和文档术语，避免后续再次混用 `snapshot`、`runtime`、`history`。

### 0.2 客户端主链文件

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceV2SourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerTest.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`
- Test: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2ClientTest.java`

- [ ] 先明确客户端 4 类职责边界：
  - `MonitorService` 只消费 stream 和市场增量，不再私藏第二份账户聚合副本。
  - `AccountStatsPreloadManager` 只负责“运行态应用 / 历史补拉 / 强一致刷新”三种账户入口。
  - `UnifiedRuntimeSnapshotStore` 是唯一产品级运行态真值中心。
  - `MonitorFloatingCoordinator` 只做悬浮窗刷新节流和 runtime 投影。

### 0.3 UI 消费文件

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionActivitySourceTest.java`

- [ ] 明确 UI 层只允许保留“单项展示语义”和“布局绑定语义”。
- [ ] 明确产品级手数、盈亏、挂单笔数、产品名短名等都应来自统一运行态，而不是各页再次遍历列表重算。

---

## A 线：服务器账户真值与发布链收口

### Task A1: 增加单次原子强一致接口，结束客户端“双接口拼装”

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_account.py`
- Test: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`

- [ ] 新增 `GET /v2/account/full`，返回：
  - `accountMeta`
  - `account`
  - `overviewMetrics`
  - `curveIndicators`
  - `statsMetrics`
  - `positions`
  - `orders`
  - `trades`
  - `curvePoints`
  - `historyRevision`
  - `syncToken`
- [ ] 接口语义固定为“给客户端一次拿到当前运行态 + 当前历史真值”，只用于显式强一致刷新、交易后确认、深页首次强刷。
- [ ] `v2_account.py` 新增对应 response builder，避免 `server_v2.py` 自己手拼字段。
- [ ] 补测试锁定：
  - 已登录时 `/v2/account/full` 返回完整字段集。
  - 未登录时返回空账户但字段结构完整。
  - `historyRevision` 缺失时直接失败，不允许静默补位。

### Task A2: 压缩账户缓存层，只保留“运行态缓存 + 发布态缓存”

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`

- [ ] 盘点并重新命名当前账户相关缓存：
  - `snapshot_build_cache`
  - `snapshot_sync_cache`
  - `v2_sync_state`
  - `v2_bus_state`
- [ ] 改成两层语义更清楚的主状态：
  - `account_runtime_cache`：当前账户运行态真值或其轻量缓存
  - `account_publish_state`：上一次对客户端发布的 digest / seq / previous snapshot
- [ ] 保留 `market_candles_cache` 与账户缓存分离，不混合市场与账户职责。
- [ ] 删除已经只剩历史兼容价值、但不再需要的重复状态字段和辅助命名。
- [ ] 补测试锁定“成交提交后一次失效就足以让下一轮重建得到新真值”，避免仍要清 3 到 4 层状态才生效。

### Task A3: 把 bus 从“固定频率重建”改成“事件驱动发布 + 心跳”

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Test: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`

- [ ] 保留 websocket 固定推送节拍，但拆开两件事：
  - `producer` 只在必要时重建并提交最新发布态。
  - websocket 客户端循环只负责按节拍发送 `syncEvent / syncBootstrap / heartbeat`。
- [ ] 触发立即发布的事件固定为：
  - 登录成功
  - 切换账号成功
  - 退出成功
  - 成交成功
  - MT5 轻快照被判断为真变化
  - 异常状态变化
- [ ] 如果到下一个节拍之间没有变化，只发 heartbeat，不重新整包构建账户运行态。
- [ ] 补测试锁定：
  - 无变化时 `busSeq` 不前进。
  - 有变化时只前进一次。
  - 成交提交后能够立刻看到新 `historyRevision`。

### Task A4: 历史接口职责收口

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_account.py`
- Test: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`

- [ ] 重新定义三个接口职责：
  - `/v2/account/snapshot`：只返回当前运行态，不返回成交历史与曲线主体。
  - `/v2/account/history`：只返回历史交易、净值曲线和统计，不承担当前运行态真值。
  - `/v2/account/full`：只给强一致刷新使用。
- [ ] 如果当前不准备真正做分页，就显式在代码注释和测试里说明 `nextCursor=""` 是当前约束，不让客户端继续假设服务端已支持大分页。
- [ ] 如果决定保留分页扩展位，就补测试锁定“分页关闭时客户端也能一次消费完整历史”。

### Task A5: 服务器回归与性能观察口

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] 增加最小诊断口径，只保留：
  - 运行态重建次数
  - bus 发布次数
  - 最后一次重建耗时
  - 最后一次发布耗时
- [ ] 不恢复旧高频调试日志，只把这些指标放进 health/admin 可读字段。
- [ ] 文档中注明：服务器高频主链现在是“事件驱动发布 + websocket 定时发送”，不是“每个客户端各自拉取和重建”。

---

## B 线：客户端账户链收口为单一主链

### Task B1: `GatewayV2Client` 接入原子强一致接口

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java`
- Test: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2ClientTest.java`
- Test: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2ClientSourceTest.java`

- [ ] 新增 `AccountFullPayload` 解析模型和 `fetchAccountFull()`。
- [ ] 保持现有 `fetchAccountSnapshot()` / `fetchAccountHistory()` 不删，避免影响其他入口，但把注释改清楚。
- [ ] 补测试锁定：
  - `fetchAccountFull()` 对缺失 `accountMeta/trades/curvePoints` 直接报错。
  - 原有 snapshot/history 解析契约不被破坏。

### Task B2: `AccountStatsPreloadManager` 改成三入口清晰结构

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerSourceTest.java`

- [ ] 明确保留 3 条入口：
  - `applyPublishedAccountRuntime(...)`
  - `refreshHistoryForRevision(...)`
  - `fetchFullForUi(...)`
- [ ] 原 `fetchForUi(...)` 改成只代理到 `fetchFullForUi(...)`，不再手写“先 snapshot 后 history 再 merge”。
- [ ] `buildStoredSnapshotFromV2(...)` 拆成更明确的两段：
  - 运行态快照构建
  - 历史主体构建
- [ ] `buildStoredSnapshotFromHistoryOnly(...)` 保留，只给 revision 补拉使用。
- [ ] 补测试锁定：
  - stream 运行态更新时不拉历史。
  - `historyRevision` 不变时不重复拉历史。
  - 显式强刷时只走一次 `fetchAccountFull()`。

### Task B3: `MonitorService` 删掉第二份账户运行态副本

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceV2SourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceSourceTest.java`

- [ ] 删除或压缩 `streamPositionSnapshot`、`streamAccountSnapshotReceived`、`streamPositionsUpdatedAt` 这类只服务于第二份账户副本的状态。
- [ ] `MonitorService` 收到 account stream 后只做：
  - 调 `AccountStatsPreloadManager.applyPublishedAccountRuntime(...)`
  - 必要时请求 `refreshHistoryForRevision(...)`
  - 请求悬浮窗刷新
- [ ] `MonitorFloatingCoordinator` 不再把 `MonitorService` 的私有仓位副本当成优先真值。
- [ ] 补 source test 锁定“服务层不再自己持有第二份账户聚合真值”。

### Task B4: 统一 revision 驱动的缓存通知

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`

- [ ] `UnifiedRuntimeSnapshotStore` 继续保留 `accountRevision / productRevision`，但把变化条件写得更明确。
- [ ] `AccountStatsPreloadManager` 的监听通知要显式区分：
  - 运行态变化
  - 历史变化
  - 失败态变化
- [ ] 避免“任何 cache 更新时间变化都整页刷新”。
- [ ] 补测试锁定：
  - 只有产品摘要变化时，相关 `productRevision` 才前进。
  - 只有历史变化时，产品级 revision 不误前进。

---

## C 线：统一产品运行态成为唯一 UI 真值

### Task C1: 扩充 `UnifiedRuntimeSnapshotStore` 的正式输出面

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ProductRuntimeSnapshot.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/ChartProductRuntimeModel.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/state/model/FloatingCardRuntimeModel.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`

- [ ] 只把真正跨三处共用的字段提升成正式 runtime 字段：
  - 展示名称
  - 紧凑名称
  - 当前持仓笔数
  - 当前挂单笔数
  - 产品总手数/方向手数
  - 产品净盈亏
  - 摘要文案
- [ ] 不把单笔价格、单笔 ticket、单笔 side 这类单项语义硬塞进产品级 runtime。
- [ ] 补测试锁定“多笔同产品时，产品级字段是聚合语义；单笔条目仍需保留单笔展示”。

### Task C2: 悬浮窗只消费统一产品运行态

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java`

- [ ] `FloatingPositionAggregator` 如果只剩局部投影职责，就保留；如果仍做产品聚合，就下沉或删除。
- [ ] 悬浮窗卡片展示字段统一从 `UnifiedRuntimeSnapshotStore.selectFloatingCard(...)` 读取。
- [ ] 悬浮窗不再自己判断产品简称、方向手数和产品级盈亏。

### Task C3: 账户页条目只保留单项语义，产品语义全部来自 runtime

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionUiModelFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionActivitySourceTest.java`

- [ ] `AccountPositionActivity` 与 `UiModelFactory` 不再自己派生产品级总手数、总盈亏、挂单笔数。
- [ ] `PositionAdapterV2` 与 `PendingOrderAdapter` 保留：
  - 单笔价格
  - 单笔 ticket/orderId
  - 单笔 side
  - 单笔操作按钮
- [ ] 产品级摘要文案统一由 runtime 注入。
- [ ] 明确保留“同产品仅 1 条单笔条目时可借用产品 runtime”的规则；多笔时不覆盖单笔语义。

### Task C4: 图表摘要和叠加层只认统一产品运行态

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`

- [ ] 图表右上角摘要、图表叠加层摘要字段统一只从 `selectChartProductRuntime(...)` 取值。
- [ ] `ChartOverlaySnapshotFactory` 不再从 `latestCache` 或账户列表二次推导产品级摘要。
- [ ] 历史成交、当前仓位、挂单线仍各自保留自己的实体数据源，但产品级摘要来源必须统一。

---

## D 线：图表/UI 绑定频次继续下压

### Task D1: 把图表刷新预算继续收成“只刷必要区块”

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`

- [ ] 继续保证预算边界固定：
  - `marketTickChanged` 只影响实时尾部
  - `productRuntimeChanged` 只影响摘要和必要叠加层
  - `uiStateChanged` 只影响页面轻量状态
  - `dialogStateChanged` 只影响弹层与快捷条
- [ ] 任何一个事件都不能再回退成“整页一起 bind”。
- [ ] `ChartRefreshScheduler` 继续只做合帧，不新增持久缓存。

### Task D2: 账户页与悬浮窗也按 revision 做最小刷新

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionActivitySourceTest.java`

- [ ] 账户页分段刷新按：
  - 概览变化
  - 持仓变化
  - 挂单变化
  - 历史变化
  分开处理。
- [ ] 悬浮窗只在当前展示产品相关 `productRevision` 前进时重绑文本，不因无关产品变化重排。

---

## E 线：验证、文档与最后闭环

### Task E1: 单测与源码测试回归

**Files:**
- Test: `bridge/mt5_gateway/tests/test_v2_account_pipeline.py`
- Test: `bridge/mt5_gateway/tests/test_v2_session_manager.py`
- Test: `app/src/test/java/com/binance/monitor/service/MonitorServiceV2SourceTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPreloadManagerTest.java`
- Test: `app/src/test/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStoreTest.java`
- Test: `app/src/test/java/com/binance/monitor/data/remote/v2/GatewayV2ClientTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshBudgetTest.java`

- [ ] 跑 Python 侧账户与会话测试。
- [ ] 跑 Android 侧网关客户端、账户预加载、统一运行态、图表刷新预算相关测试。
- [ ] 若新增 `/v2/account/full`，补对应解析与契约测试，不允许只靠人工验证。

### Task E2: 编译与真机验证

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] 更新文档，明确新的主链是：
  - 服务器一份账户运行态真值
  - 客户端一份统一产品运行态真值
  - 历史只在 revision 前进或显式强刷时拉取
- [ ] 执行 `.\gradlew.bat assembleDebug`
- [ ] 安装到真机 `7fab54c4`
- [ ] 验证 4 个场景：
  - 登录后首次进入账户页
  - 交易后当前持仓/挂单刷新
  - 图表页当前产品摘要刷新
  - 悬浮窗与主界面产品结果一致

### Task E3: 收尾标准

**Files:**
- Modify: `CONTEXT.md`

- [ ] 本轮完成标准必须同时满足：
  - 交易后强一致刷新不再依赖“snapshot + history 双接口拼装”
  - `MonitorService` 不再维护第二份账户聚合副本
  - 图表、账户页、悬浮窗产品级摘要只来自 `UnifiedRuntimeSnapshotStore`
  - 图表页高频刷新没有新增缓存层
- [ ] 如果只完成一部分，必须在 `CONTEXT.md` 明确停点，不得把“方案已确定”写成“主链已收口”。

