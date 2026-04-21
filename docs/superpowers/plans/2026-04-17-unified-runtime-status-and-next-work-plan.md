# Unified Runtime 当前状态与下一步工作计划

## 1. 当前目标

本轮主目标仍然是同一件事：

- 市场数据只保留一份正式真值
- 账户数据只保留一份正式真值
- 图表、账户页、分析页、悬浮窗的首帧都改成“先本地可见，后远端校准”
- 页面刷新不再靠固定频率制造同步感，而是逐步改成由 revision 和过期判断驱动

---

## 2. 已完成工作

### 2.1 基础设施已完成

- 已完成 `M1`
- 已新增统一首帧状态机：
  - `PageBootstrapState`
  - `PageBootstrapSnapshot`
  - `PageBootstrapStateMachine`
- 已新增统一 revision 入口：
  - `RuntimeRevisionCenter`
- 统一术语、统一状态流转、统一 revision 分类已经落地

### 2.2 市场真值收口已完成并补齐最后一轮消费链纯化

- 已完成 `M2`
- `MonitorService` 已不再把市场数据直接散写到多份展示 cache
- 市场 stream 数据已经先进入 `MarketRuntimeStore`
- `MonitorRepository` 已开始降级为 selector 暴露层
- 市场侧已具备：
  - `selectLatestPrice(symbol)`
  - `selectClosedMinute(symbol)`
  - 兼容旧链路的 `selectDisplayKline(symbol)`
- 最新一轮又补完了监控页消费链收口：
  - `MainViewModel` 已正式透出市场 selector
  - `MarketMonitorPageRuntime` 现在只把 `MarketRuntimeSnapshot` LiveData 当作重绘触发器
  - 具体取值统一改经 `MainViewModel -> MonitorRepository selector`
- `MarketChartRealtimeTailHelper` 与 `ChartWarmDisplayPolicyHelper` 当前已确认不再直接依赖 `MarketRuntimeSnapshot` 结构

### 2.3 图表页首帧 local-first 已完成

- 已完成 `M5`
- 图表页冷启动已改成：
  - 先建立 bootstrap state
  - 先消费内存态
  - 再启动持久化恢复
  - 并行请求远端
- 图表 loading 已从单纯布尔值改成按 bootstrap state 驱动
- `invalidateChartDisplayContext()` 已拆成冷启动失效和选择变更失效两条链
- 冷启动、回前台、切产品、切周期、手动刷新、自动刷新已用显式 `RequestReason` 分流
- 图表失败态已不再把“恢复中”误判成“真正空图”

### 2.4 账户页和分析页首帧 local-first 已完成

- 已完成 `M6`
- 账户页与分析页已接入首帧 bootstrap 主链
- 本地账户快照和本地历史统计已能真正抢到首帧
- `AccountStatsPreloadManager.start()` 已提前异步预热本地持久化快照
- 账户页恢复期间已区分：
  - restoring
  - true empty
- 分析页已不再默认先显示“暂无曲线数据”

### 2.5 revision 驱动刷新第一段已完成

- `M7` 已完成第一段
- 账户页 schedule loop 已接入 `AccountRevisionRefreshPolicy`
- 悬浮窗非即时刷新已接入 `FloatingRevisionRefreshPolicy`
- 图表页 `autoRefreshRunnable` 已接入 `MarketChartRevisionRefreshPolicy`
- 现在这些定时器都会先判断：
  - 当前 revision 是否前进
  - 当前已应用结果是否过期
- 只有命中 gate 时，才继续走远端请求

### 2.6 账户页分区 revision 刷新已补齐

- `M7` 的账户页分区刷新现已完成
- `AccountPositionPageController` 已把以下 4 段正式拆开：
  - overview
  - positions
  - pending
  - history
- 历史成交不再只是跟着缓存一起换内存列表，而是也进入独立 section diff
- history revision 前进时，现在只会更新历史入口，不会顺手重绑概览、当前持仓或挂单区块

### 2.7 图表页分区刷新已补齐

- `M7` 的图表页剩余分区刷新现已补到当前计划要求
- market window 继续只负责主图窗口与实时尾部
- product runtime 现在会先判断“叠加层视觉是否真的变化”：
  - 只变摘要时，只刷新 summary
  - 标注真变化时，才重刷 overlay + summary
- dialog/ui state 继续只负责按钮、弹层和局部文案

### 2.8 已完成的验证

- 已通过多轮 Android 定向单测
- 已多次通过 `:app:assembleDebug`
- 最新一轮已通过：
  - `AccountPositionSectionDiffTest`
  - `AccountPositionHistorySectionSourceTest`
  - `AccountPositionLocalFirstStartupTest`
  - `AccountPositionActivitySourceTest`
- 最新图表页这一轮已通过：
  - `ChartOverlayRefreshDiffTest`
  - `MarketChartZoneRefreshSourceTest`
  - `MarketChartRefreshBudgetTest`
  - `MarketChartZoneRefreshTest`
  - `MarketChartRevisionRefreshPolicyTest`
  - `MarketChartRevisionRefreshPolicySourceTest`
  - `MarketChartRefreshPlanSourceTest`
  - `MarketChartBootstrapStateSourceTest`
  - `MarketChartLocalFirstStartupSourceTest`
- 当前 `adb devices` 已看到设备 `7fab54c4`
- 最新 debug 包已重新安装到设备 `7fab54c4`

### 2.9 `M3` 第一段正式入口收口已完成

- `AccountStatsPreloadManager` 现已只保留 3 个正式入口：
  - `applyPublishedAccountRuntime(...)`
  - `refreshHistoryForRevision(...)`
  - `fetchFullForUi(...)`
- 兼容别名 `fetchForUi(...)` 与 `fetchForOverlay()` 已删除
- 未再使用的旧 helper 已删除：
  - `buildStoredSnapshotFromV2(...)`
  - `buildStoredSnapshotFromSnapshotOnly(...)`
  - `resolveRemoteHistoryRevision(...)`
  - `resolveHistoryRevisionFromPayload(...)`
- `MonitorService`、账户页、图表页、交易确认链、桥接页和 host-shell 协调器都已统一改走 `fetchFullForUi(...)`
- 这一段已通过定向源码测试、交易链测试、`assembleDebug` 与设备 `7fab54c4` 覆盖安装

### 2.10 `M3` 第二段本地删重已完成一部分

- `MonitorService` 不再自己持有 `AccountStorageRepository`
- `MonitorService` 不再自己维护 `clearPersistedAccountSnapshot(...)`
- 账户运行态清理现在统一委托给 `AccountStatsPreloadManager.clearAccountRuntimeState(...)`
- `AccountStatsPreloadManager` 已新增：
  - `clearAccountRuntimeState(account, server)`
  - `clearStoredSnapshotForIdentity(account, server)`
- 这一段已通过：
  - `MonitorServiceSourceTest`
  - `MonitorServiceV2SourceTest`
  - `AccountStatsPreloadManagerSourceTest`
  - `AccountSnapshotRefreshCoordinatorSourceTest`
  - `AccountStatsScreenLocalFirstStartupTest`
  - `AccountPositionLocalFirstStartupTest`
  - `:app:assembleDebug`
- 本轮设备未在线，因此还没补真机覆盖安装

### 2.11 `M3` 第二段本地删重继续推进

- `MonitorService` 在远程会话恢复前不再直接操作：
  - `clearLatestCache()`
  - `setFullSnapshotActive(true)`
- 强一致预热现已统一委托给 `AccountStatsPreloadManager.prepareFullSnapshotRefresh()`
- 这一段已通过：
  - `MonitorServiceSourceTest`
  - `MonitorServiceV2SourceTest`
  - `AccountStatsPreloadManagerSourceTest`
  - `AccountSnapshotRefreshCoordinatorSourceTest`
  - `AccountStatsScreenLocalFirstStartupTest`
  - `AccountPositionLocalFirstStartupTest`
  - `TradeExecutionCoordinatorTest`
  - `:app:assembleDebug`
- 本轮设备仍未在线，因此还没补真机覆盖安装

### 2.12 `M3` 服务端发布态已继续收口

- `bridge/mt5_gateway/server_v2.py` 已删除旧兼容别名：
  - `v2_bus_state`
  - `_read_v2_bus_state()`
  - `_mirror_legacy_v2_bus_state_locked()`
- `v2/stream` 构包现已只直接读取 `account_publish_state`
- `test_v2_sync_pipeline.py` 已改成只操作正式发布态，并新增“旧别名必须不存在”的断言
- 这一段已通过：
  - `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline -v`
  - `python -m unittest bridge.mt5_gateway.tests.test_v2_account_pipeline -v`
- 本轮按用户要求未做整包编译和安装

### 2.13 `M3` 客户端恢复后账户身份校验已下沉

- `MonitorService` 在远程会话恢复成功后，已不再自己：
  - 拉 `fetchFullForUi(...)`
  - 判断 `recoveredCache` 是否已连通
  - 判断全量快照是否仍是目标账号
- 上述逻辑现已统一下沉到 `AccountStatsPreloadManager.fetchFullForUiForIdentity(...)`
- `AccountStatsPreloadManager` 已新增统一校验：
  - cache 未连通时直接抛明确异常
  - cache 身份与目标账号不一致时直接抛明确异常
- 这一段已通过：
  - `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.service.MonitorServiceSourceTest --tests com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest --tests com.binance.monitor.ui.account.AccountStatsPreloadManagerTest`
  - `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.service.MonitorServiceV2SourceTest`
- 本轮按用户要求仍未做整包编译和安装

### 2.14 `M3` 历史补拉 gate 与续跑已下沉

- `MonitorService` 已不再自己持有：
  - `AccountHistoryRefreshGate`
  - `historyRevision` 补拉排队状态
  - history 补拉完成后的递归续跑逻辑
- 上述逻辑现已统一下沉到 `AccountStatsPreloadManager.queueHistoryRefreshForRevision(...)`
- `AccountHistoryRefreshGate` 也已移到 `runtime.account`，明确归到账户运行时域
- 这一段已通过：
  - `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.service.MonitorServiceSourceTest --tests com.binance.monitor.service.MonitorServiceV2SourceTest --tests com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest --tests com.binance.monitor.architecture.AccountDomainDependencySourceTest --tests com.binance.monitor.runtime.account.AccountHistoryRefreshGateTest`
  - `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.ui.account.AccountStatsPreloadManagerTest`
- 本轮按用户要求仍未做整包编译和安装

### 2.15 `M3` 远程会话恢复第二副本已下沉

- `MonitorService` 已不再自己持有和执行：
  - 远程会话恢复主逻辑
  - `logged_out` 本地收口
  - 已保存账号匹配与身份辅助判断
  - 会话摘要写回与草稿身份回填
- 上述逻辑现已统一下沉到 `runtime.account.AccountSessionRecoveryHelper`
- `MonitorService` 现在只保留：
  - 前后台切回时触发恢复检查
  - 根据 helper 结果决定是否补一次 UI 强刷新
- 这一段已通过：
  - `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.service.MonitorServiceSourceTest --tests com.binance.monitor.service.MonitorServiceV2SourceTest --tests com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest --tests com.binance.monitor.ui.account.AccountStatsPreloadManagerTest --tests com.binance.monitor.architecture.AccountDomainDependencySourceTest --tests com.binance.monitor.runtime.account.AccountHistoryRefreshGateTest --tests com.binance.monitor.runtime.account.AccountSessionRecoveryHelperSourceTest`
  - `python -m unittest bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_v2_account_pipeline -v`
- 按当前实施任务单，`Phase A / M3` 已完成

### 2.16 `M4` 产品级统一运行态唯一化已完成

- 图表、账户页、悬浮窗的产品级摘要现已正式读取同一份 unified runtime
- `ProductRuntimeSnapshot` 已锁定为产品级白名单字段：
  - 展示名
  - 紧凑名
  - 当前持仓笔数
  - 当前挂单笔数
  - 产品总手数
  - 方向手数
  - 产品净盈亏
  - 跨页摘要文案
- 单笔字段现已退出产品级摘要主链：
  - ticket
  - 单笔开仓价
  - 单笔止盈止损
  - 单笔 side 原始文案
- `AccountPositionUiModelFactory` 已不再按“产品 + 方向 + 成本价”在页面侧重算聚合
- `PositionAggregateAdapter` 也已改成展示 unified runtime 的产品摘要，不再混入 side / 成本价
- 这一段已通过：
  - `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.runtime.state.UnifiedRuntimeSnapshotStoreTest --tests com.binance.monitor.runtime.state.ProductRuntimeSelectorConsistencyTest --tests com.binance.monitor.ui.chart.ChartOverlaySnapshotFactoryTest --tests com.binance.monitor.ui.floating.FloatingPositionAggregatorTest --tests com.binance.monitor.ui.account.AccountPositionUiModelFactoryTest --tests com.binance.monitor.ui.account.AccountPositionEnhancementSourceTest --tests com.binance.monitor.ui.account.AccountPositionSectionDiffTest --tests com.binance.monitor.ui.account.AccountPositionAdapterSourceTest`

---

## 3. 未完成工作

### 3.1 市场真值收口已完成

- `M2` 已按当前代码真实状态闭环：
  - 市场仓库层已稳定为 `MarketRuntimeSnapshot` LiveData + selector
  - 活跃市场消费链已统一切到 selector / 窗口签名
  - `MarketChartRealtimeTailHelper` 与 `ChartWarmDisplayPolicyHelper` 已确认不再构成计划内未完成项

### 3.2 账户真值唯一化已完成

- `M3` 已按当前任务单收完：
  - 服务端发布态已收口到 `account_publish_state`
  - `/v2/account/full` 已固定为强一致主入口
  - `AccountStatsPreloadManager` 已锁定正式 3 入口
  - `MonitorService` 已退出账户第二副本职责

### 3.3 产品级统一运行态已完成

- `M4` 已按当前任务单收完：
  - 图表、账户页、悬浮窗的产品级摘要已改读同一份 runtime
  - 产品级 runtime 白名单已锁定
  - 单笔字段已退出产品级摘要主链
  - “同产品跨页面一致性”测试已补齐

### 3.4 revision 驱动刷新已完成

- `M7` 已按当前任务单闭环：
  - 页面定时器主链、账户页 4 段刷新、图表页 3 线刷新都已补齐
  - `ChartRefreshBudgetTest` 也已到当前主链要求，不再是未完成项

### 3.5 删除旧双轨和最终闭环验证已完成

对应 `M8` 这轮已完成：

- `MonitorRepository` 已删除旧 display cache 镜像字段，展示快照改为直接从统一 market runtime 现算并发布
- 账户页与分析页断线刷新不再合成空快照上屏，显式登出空态与断线元信息刷新已分开
- 图表页已删除 `restoreChartOverlayFromLatestCacheOrEmpty` 和冷启动失败时的旧清空路径
- 分析页空文案已收口到 bootstrap/资源入口，不再在页面层硬写默认文案
- Android / Python / `assembleDebug` / 覆盖安装已完成
- 真机已完成冷启动与回前台 smoke
- `CONTEXT.md` 中更晚的人工真机记录已确认：真实账户环境下“交易后刷新”已完成，延迟约 `1s`

---

## 4. 当前真实停点

当前实际停在：

- `M2` 已完成
- `M3` 已完成
- `M4` 已完成
- `M5` 已完成
- `M6` 已完成
- `M7` 已完成
- `M8` 已完成

也就是：

- 市场仓库层、监控页、图表页和悬浮窗的正式市场消费边界都已收口
- 页面定时器主链、账户页 4 段刷新、图表页 3 线刷新都已补到当前计划要求
- `AccountStatsPreloadManager` 的正式入口名也已经锁定
- `MonitorService` 已不再直接碰账户存储分区
- `MonitorService` 也已不再直接碰预加载节奏开关
- 服务端 `v2/stream` 已只认 `account_publish_state`
- 恢复后的全量账户 cache 身份校验也已下沉到 `AccountStatsPreloadManager`
- history 补拉 gate 与续跑逻辑也已下沉到 `AccountStatsPreloadManager`
- `ProductRuntimeSnapshot` 白名单与 3 个页面的产品级 selector 已统一
- 真实账户环境下的“交易后刷新”人工 smoke 也已完成

---

## 5. 下一步工作计划

## Phase A：已完成 `M2 / M3 / M4`

### A1. 真值主链与产品级运行态已闭环

结果：

- 市场链、账户链、产品级统一运行态都已固定为单一正式边界
- 账户链已经正式缩成一条
- `MonitorService` 已退出剩余账户恢复/节奏控制第二副本
- `/v2/account/full` + 单一发布态主链已作为当前正式边界固定下来

## Phase B：已完成 `M5 / M6 / M7`

### B1. local-first 与 revision 主链已闭环

结果：

- 图表、账户页、分析页、悬浮窗都已进入“先本地可见，后远端校准”的正式链路
- 常规刷新也已不再靠固定频率制造同步感

## Phase C：`M8` 已完成最终闭环

### C1. 删除旧双轨

- 已删除旧 display cache 的主真值职责
- 已删除旧空态主链
- 已删除旧页面级第二真值的主要残留

### C2. 做最终验证

- 已完成 Android 单测回归
- 已完成 Python 契约测试回归
- 已完成 `assembleDebug`
- 已完成真机冷启动/回前台 smoke
- 已完成真实交易场景下的“交易后刷新”人工复核

---

## 6. 建议执行顺序

按当前状态，这份 unified runtime 计划内已无剩余实施项。

---

## 7. 当前文档用途

这份文档的用途是：

- 作为“当前已做到哪、还差什么、下一步先做什么”的总览
- 作为继续执行时的短入口
- 避免每次都重新从长实施计划里人工判断当前停点

如果后续继续动 unified runtime，优先以这份状态文档、总实施计划和 `CONTEXT.md` 的最新口径为准，不再回退到旧停点判断。
