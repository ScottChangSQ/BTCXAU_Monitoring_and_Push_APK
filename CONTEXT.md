# CONTEXT

## 当前正在做什么
- 当前在执行新的结构性修复计划；`R1-R5` 已闭合，`R6` 已开始但只完成了 `MonitorService` 的首个拆分切口。
- 已确认真机 + 已部署 HTTPS 服务器的人工联调记录仍成立；本轮继续以当前源码和测试为准，不再相信旧文档里“P1-P5 已全部闭合”的表述。
- 本轮已完成 `R5`：`AccountTimeRange`、`AccountMetric`、`AccountSnapshot`、`CurvePoint`、`PositionItem`、`TradeRecordItem` 已整体下沉到 `domain.account` / `domain.account.model`；`SecureSessionPrefs` 改为实现独立的 `SessionSummaryStore`，不再反向实现 `ui.account` 内部接口；旧路径 source test 也已同步修正。
- 本轮已启动 `R6`：`MonitorService` 已抽出 `MonitorForegroundNotificationCoordinator` 与 `MonitorFloatingCoordinator`，服务本体只保留生命周期、连接状态和主链编排；`AccountStatsBridgeActivity`、`MarketChartActivity` 的大类拆分仍未开始。
- `P1` 已完成：旧演示链路已从主源码移除，`AccountStatsPreloadManager` 与相关 helper 已下沉到 `runtime.account`，`AccountStorageRepository` 已去掉对悬浮窗聚合的直接依赖。
- `P2` 已完成：登录流程不再在 Activity 内缓存明文密码；切账号页面态收口已切回主线程；登出/切号时会显式通知 `MonitorService` 清流式账户运行态。
- `P3` 已完成：账户历史改为沿 `nextCursor` 拉满全量分页；本地账户快照持久化已收口到仓库层总事务。
- `P4` 已完成：已从历史记录恢复出“账户页主动刷新 / 交易后强刷新、同签名跳过重画、旧 history 回包拦截、瞬时断线空快照不清空当前可渲染持仓”等条目，并确认当前工作树代码与测试已闭合。
- `P5` 已完成：已从历史记录恢复出“图表页普通 tab 返回不重置 transport、长周期前台恢复按分钟边界刷新、设置页 tab 切换不再 finish、异常标注层输入未变化不重复重建”等条目，并确认当前工作树代码与测试已闭合。
- 旧记录里“当前 5 个批次均已完成”的表述需要重新核对；本轮多模型审计已确认至少还存在数据库主线程访问、设置页主线程清理缓存、图表页跨品种混图风险、悬浮窗返回栈不一致、安全会话缓存静默删数据等未闭合问题。

## 上次停在哪个位置
- 上次停在“`R1-R4` 已完成，`R5/R6` 还没开始”的判断。
- 本轮已完成真机联调：设备 `7fab54c4 / PKR110` 在线，客户端 `com.binance.monitor 1.0.0` 已安装并运行，`https://tradeapp.ltd/health` 返回 `200`，服务端 `bundleFingerprint` 与 `dist/windows_server_bundle/bundle_manifest.json` 一致。
- 人工联调覆盖了 `行情监控`、`行情持仓`、`账户统计`、`设置` 四个主 tab，真机可读到真实 HTTPS 数据；账户页展示真实资产、盈亏和交易记录，图表页展示 `BTCUSDT` 真实 K 线与刷新倒计时，首页展示实时连接状态。
- `设置 -> 行情监控` 往返时日志里出现 `window dying`，但结合源码与现有 `TopLevelTabNavigationSourceTest` 可确认这是当前顶层 tab 采用 `CLEAR_TOP + SINGLE_TOP` 主动清理目标页上层任务栈的既定行为，不是新的阻塞缺陷；随后再进账户页时真实数据可继续正常返回。
- 本轮停在：`R5` 已完成并验证通过；`R6` 只完成了 `MonitorService` 的通知/悬浮窗协调器拆分，`AccountStatsBridgeActivity` 与 `MarketChartActivity` 仍待继续拆。

## 近期关键决定和原因
- 修复按 `P1 -> P5` 批次顺序执行，但每个批次内部按同一条链路一起收口，避免留下临时兼容层。
- `P1` 采用“严格迁移，不保留兼容壳”原则：直接删除旧演示 Activity/Repository，运行时组件直接迁包，不保留旧 UI 包转发层。
- 运行时和 UI 共用的账户 helper 一并下沉到 `runtime.account`，避免只挪主类、依赖仍留在 UI 包里的半修状态。
- `P2` 采用“显式动作 + 主线程收口”原则：不靠隐式缓存失效或页面刷新兜底，而是新增明确的服务动作清理账户运行态。
- `P3` 采用“第一页单值字段固定为准 + 列表字段全量分页拼接”原则：`overviewMetrics/curveIndicators/statsMetrics/accountMeta/serverTime` 固定取第一页，`trades/orders/curvePoints` 按服务端分页顺序全部拼接，不做启发式裁剪或本地推断。
- `P3` 的持久化事务边界收口在 `AccountStorageRepository`：由仓库统一调用 `RoomDatabase.runInTransaction(...)`，不再依赖多个 DAO 小事务拼接成“伪原子写入”。
- `P4/P5` 的原始批次文档在仓库内未单独保留，本轮改为以 `README.md` 2026-04-08 / 2026-04-09 的最终 BUG review / 最终收口记录为准，再用对应 SourceTest / UnitTest 反向核对代码真值，避免根据记忆补批次。
- `P4` 归为“账户页刷新与展示收口”，`P5` 归为“图表 / 设置 / 生命周期收口”；分批验证时按短命令分组执行，避免一次性长命令卡住。
- 真机联调阶段继续沿用“证据优先”原则：每进一个主页面都同时抓 UI dump 与进程日志，再结合服务健康接口回看，不根据体感判断是否联通。
- 对 `设置 -> 行情监控` 的销毁日志不按“新 bug”直接定性，而是先回查顶层 tab 导航源码与已有 SourceTest；确认当前实现明确要求使用 `CLEAR_TOP + SINGLE_TOP` 清理上层隐藏页面后，再以是否出现真实数据丢失或异常重启感作为真机判定标准。
- 本轮重新审计时，不再把 `CONTEXT.md` 中的“已闭合”表述视为事实；必须以当前工作树源码和测试证据为准。
- 多模型重新核查后，已确认“文档结论”和“当前源码”存在偏差；后续若继续修复，应以本轮审计列出的未闭合问题单为起点。
- `R5` 采用“共享模型一次性整包下沉 + import 全量切换 + 删除旧路径”原则：不保留旧 `ui.account.model` 兼容壳，不走双轨。
- 远程会话摘要存储接口采用“从 UI 类中剥离到安全层独立契约”原则：`SecureSessionPrefs` 直接实现 `security.SessionSummaryStore`，避免 `security -> ui.account` 反向依赖。
- `R6` 先从 `MonitorService` 开始，按“整段职责链整块抽走”原则处理：前台通知和悬浮窗两段各自下沉到 coordinator，Service 不保留同名薄转发方法。
- `AccountStatsBridgeActivity` 和 `MarketChartActivity` 的拆分仍需继续，但下一步应直接抽连续职责段，不要再加新的零散 helper。

## 当前验证状态
- `P1` 编译验证通过：`.\gradlew.bat :app:compileDebugJavaWithJavac`
- `P1` 定向单测通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPreloadPolicyHelperTest" --tests "com.binance.monitor.ui.account.AccountHistoryRefreshPolicyHelperTest" --tests "com.binance.monitor.ui.account.MetricNameTranslatorTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.ui.account.AccountV2SourceTest" --tests "com.binance.monitor.ui.chart.MarketChartPositionAnnotationSourceTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositoryTest" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositoryOpenTimeSourceTest"`
- `P2` 编译验证通过：`.\gradlew.bat :app:compileDebugJavaWithJavac`
- `P2` 定向单测通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.service.MonitorServiceFallbackCleanupSourceTest"`
- `P3` 编译验证通过：`.\gradlew.bat :app:compileDebugJavaWithJavac`
- `P3` 定向单测通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositoryTest" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositorySourceTest" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositoryOpenTimeSourceTest"`
- `P4` 定向单测通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityV2RefreshSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityV2SourceTest" --tests "com.binance.monitor.ui.account.AccountSessionStateMachineTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest"`
- `P5` 定向单测通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartLifecycleSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest" --tests "com.binance.monitor.ui.chart.MarketChartV2SourceTest" --tests "com.binance.monitor.ui.chart.MarketChartPositionAnnotationSourceTest" --tests "com.binance.monitor.ui.settings.SettingsTabNavigationSourceTest"`
- 真机环境验证通过：`adb devices -l` 可见设备 `7fab54c4 / PKR110`，`adb shell dumpsys package com.binance.monitor | Select-String "versionName|versionCode|lastUpdateTime"` 显示 `versionName=1.0.0`、`lastUpdateTime=2026-04-10 12:15:24`。
- 服务健康验证通过：`Invoke-WebRequest https://tradeapp.ltd/health` 返回 `200`，`bundleFingerprint=e8ee797f7991c62a6af9f22ede91e302d6fa8c5228851eff1e31024198d07012`；与 `dist/windows_server_bundle/bundle_manifest.json` 一致。
- 真机首页联调通过：`temp/monitor_dump.xml` 显示“监控工作台 / 实时连接正常 / 更新时间 2026-04-10 16:13:22（1秒/1秒）”和实时行情指标。
- 真机图表页联调通过：`temp/chart_dump.xml` 显示 `BTCUSDT`、`共300根K线，更新时间：16:10:04`、刷新倒计时和交易按钮；`temp/chart_log.txt` 未见崩溃栈。
- 真机设置页联调通过：`temp/settings_dump.xml` 显示设置首页全部主入口；`temp/settings_log.txt` 未见崩溃栈。
- 真机账户页联调通过：`temp/account_dump.xml` 与 `temp/account_return_dump.xml` 均显示“账户-7400048（400x）/ 已连接账户 / 总资产 $19,087.90 / 累计盈亏 +$4,068.45 / 当日盈亏 +$155.28”及多条真实交易记录，说明主链往返后账户数据仍能稳定回显。
- `R1` 定向验证通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartDisplayHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartAccountOverlaySourceTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.main.MainActivityStateRestoreSourceTest"`
- `R2` 定向验证通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLifecycleSourceTest"`
- `R3` 定向验证通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.local.db.AppDatabaseProviderSourceTest" --tests "com.binance.monitor.ui.settings.SettingsSectionActivitySourceTest" --tests "com.binance.monitor.security.SecureSessionPrefsSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest"`
- `R4` 定向验证通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.launch.OverlayLaunchBridgeActivitySourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest" --tests "com.binance.monitor.ui.navigation.TopLevelTabNavigationSourceTest" --tests "com.binance.monitor.ui.settings.SettingsTabNavigationSourceTest"`
- 当前合并验证通过：`.\gradlew.bat :app:compileDebugJavaWithJavac :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartDisplayHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartAccountOverlaySourceTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.main.MainActivityStateRestoreSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSourceTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartLifecycleSourceTest" --tests "com.binance.monitor.data.local.db.AppDatabaseProviderSourceTest" --tests "com.binance.monitor.ui.settings.SettingsSectionActivitySourceTest" --tests "com.binance.monitor.security.SecureSessionPrefsSourceTest" --tests "com.binance.monitor.ui.launch.OverlayLaunchBridgeActivitySourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest" --tests "com.binance.monitor.ui.navigation.TopLevelTabNavigationSourceTest" --tests "com.binance.monitor.ui.settings.SettingsTabNavigationSourceTest"`
- `R5` 编译验证通过：`.\gradlew.bat :app:compileDebugJavaWithJavac :app:compileDebugUnitTestJavaWithJavac`
- `R5` 分段验证通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.architecture.AccountDomainDependencySourceTest" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositoryTest" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositorySourceTest" --tests "com.binance.monitor.security.SecureSessionPrefsSourceTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.domain.account.model.TradeRecordItemTest"`
- `R5` 共享模型回归通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.ui.floating.FloatingPositionAggregatorTest" --tests "com.binance.monitor.ui.chart.HistoricalTradeAnnotationBuilderTest" --tests "com.binance.monitor.ui.chart.MarketChartPositionSortHelperTest" --tests "com.binance.monitor.ui.chart.MarketChartTradeSupportTest" --tests "com.binance.monitor.ui.account.AccountOverviewMetricsCalculatorTest" --tests "com.binance.monitor.ui.account.AccountOverviewDailyMetricsCalculatorTest" --tests "com.binance.monitor.ui.account.AccountOverviewCumulativeMetricsCalculatorTest" --tests "com.binance.monitor.ui.account.AccountCurvePointNormalizerTest" --tests "com.binance.monitor.ui.account.CurveAnalyticsHelperTest" --tests "com.binance.monitor.ui.account.AccountPeriodReturnHelperTest" --tests "com.binance.monitor.ui.account.TradeWeekdayStatsHelperTest" --tests "com.binance.monitor.ui.account.TradeWeekdayBarChartHelperTest" --tests "com.binance.monitor.ui.account.TradeDistributionAnalyticsTest" --tests "com.binance.monitor.ui.account.TradeVisibilityDiagnosticsHelperTest" --tests "com.binance.monitor.ui.account.AccountLeverageResolverTest" --tests "com.binance.monitor.ui.account.AccountCurveHighlightHelperTest" --tests "com.binance.monitor.ui.account.CurveSeriesInterpolationHelperTest"`
- `R6` MonitorService 拆分验证通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceCoordinatorSourceTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.architecture.AccountDomainDependencySourceTest" --tests "com.binance.monitor.ui.chart.MarketChartPositionAnnotationSourceTest"`
