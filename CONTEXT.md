# CONTEXT

## 当前正在做什么
- 2026-04-26 分析 tab“净值/结余曲线”下方仓位、回撤、收益三条副曲线晚显示已修复：初始 `applySnapshot` 渲染主曲线时副曲线区尚未 attach，现改为次屏曲线区 attach 后立即排队 curve-only 异步投影刷新，只计算并绑定 `positionRatioChartView` / `drawdownChartView` / `dailyReturnChartView`，不再等待整套交易统计 deferred render 和可见性滚动门控；已通过 `AccountStatsSecondarySectionAttachSourceTest`、`AccountStatsBridgeSnapshotSourceTest`、`AccountDeferredSnapshotRenderHelperTest` 与 `:app:assembleDebug`。
- 2026-04-26 分析 tab 卡顿最终根因已定位并收口：主因不是分析页图表 `onDraw()`，而是切到分析 tab 后仍有隐藏交易页 `MarketChartScreen$3` 状态刷新和悬浮窗 `MonitorFloatingCoordinator` 快照拼装在主线程执行，抢占输入事件前的主线程时间片，表现为 `inputDelay/frameStartLag` 型 90-120ms P99 尖峰。修复后隐藏交易页不再排队 dialog/overlay 刷新，悬浮窗关闭时不再拼快照，悬浮窗开启时快照拼装移到后台线程；真机复采：悬浮窗关闭时 `MainLooperTrace=0`、P95 8ms、P99 10ms、Janky 0.11%；悬浮窗开启时 `MainLooperTrace=0`、主页面 P95 13ms、P99 24ms、Janky 2.74%，悬浮窗 ViewRoot P99 11ms。
- 2026-04-26 继续分析分析 tab P99 尾部尖峰：101ms 是前次包级 P99，不是 P95；fresh `framestats` 拆分后主页面 `MainHostActivity` 为 771 帧、Janky 5.97%、P95 27ms、P99 93ms，悬浮窗单独 ViewRoot 为 15 帧、Janky 93.33%、P99 113ms，会污染包级统计；主页面慢帧拆解显示 90-104ms 尖峰主要来自 `inputDelay/frameStartLag`，UI work P99 约 1.6ms、issue draw P99 约 1.7ms、GPU P99 约 3.4ms，暂未看到图表 `onDraw()` 或交易统计刷新在 100ms 级阻塞。
- 2026-04-26 已完成分析页真机上下滑动采样：Debug 包启用 `ChainLatencyTracer` 后安装到设备 `7fab54c4`，进入分析 tab 执行 5 次下滑 + 5 次上滑；`account_render` 仅输出 `bind_overview_and_filters` 与 `apply_curve_range` 各 0ms，无滚动期整页/交易统计刷屏；`gfxinfo` 汇总为 656 帧、Janky 50 帧（7.62%）、P50 5ms、P90 13ms、P95 27ms、P99 101ms。
- 2026-04-26 分析 tab 滚动优化 Task 1-8 已按计划落地：新增性能源码测试、曲线点二分查找、曲线图 RenderModel 缓存、交易统计图绘制缓存、曲线一次性 `setRenderData()` 绑定、交易统计次屏接近可见再挂载；相关单元测试与 `:app:assembleDebug` 已通过，真机已安装启动但未能稳定进入分析页完成滑动日志采样。
- 2026-04-26 已生成分析 tab 滚动卡顿详细优化计划：`docs/superpowers/plans/2026-04-26-analysis-tab-scroll-performance.md`，执行顺序为性能口径测试 -> 图表 RenderModel 缓存 -> 合并 setter/invalidate -> 二分查找 -> 次屏延迟挂载；若仍卡顿，再单独规划 section RecyclerView 结构迁移。
- 2026-04-26 分析 tab 上下滑动卡顿继续定位：自动整页重绘已收口后，剩余主要瓶颈判断为多张自绘图表滚动期 `onDraw()` 全量重算/重建绘制对象，以及 `NestedScrollView` 长页面内多图表、多 `RecyclerView wrap_content` 的测量绘制成本。
- 2026-04-26 分析 tab 自动重绘策略已收口：分析页后台快照自动上屏仅保留“首次打开无可渲染内容”与“`historyRevision` 推进（新平仓历史到达）”两种情况，普通运行态/连接态轮询不再触发整页重绘。
- 2026-04-26 分析 tab 核心统计“累计结余”金额链路已收口：核心统计摘要/展开区以及交易统计里的累计金额统一按已平仓净额口径回填（`profit + storageFee + fee`），不再沿用服务端已带入的毛收益值。
- 2026-04-26 主线当前正在收口异常交易提醒冷却策略修复：客户端已改为按品种独立判断并逐品种补发服务端 alert，避免 BTC / XAU 合并 alert 被单一冷却整条压掉。
- 2026-04-26 分析 tab 收益统计空值口径已收口：未发生交易的天、月统一显示 `--`，不再显示 `+0` / `+0.0%`。
- 2026-04-26 分析 tab 模块显示链路已修正：主壳页/桥接页的次屏模块挂载统一放开交易统计、交易记录卡片，避免只剩核心统计、收益统计、净值曲线。
- 2026-04-26 分析 tab 交易记录模块已从页面主链移除：分析页不再显示交易记录卡片，账户页“完整历史成交”底部抽屉保持不变。

## 上次停在哪
- 分析 tab 副曲线晚显示修复已完成最小验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountDeferredSnapshotRenderHelperTest" --tests "com.binance.monitor.ui.account.AccountStatsSecondarySectionAttachSourceTest"` 与 `.\gradlew.bat :app:assembleDebug`。
- 分析 tab 卡顿问题已先完成一轮最小收口：远端定时快照回包不再默认整页 `applySnapshot`，而是统一经“首次可渲染 / `historyRevision` 变化”门控；后续若滚动仍有明显卡顿，再继续收口图表 `onDraw()` 与长页面结构本身。
- 本次异常提醒修复已完成最小回归验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.AbnormalSyncRuntimeHelperTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.data.remote.AbnormalGatewayClientTest" --tests "com.binance.monitor.util.NotificationHelperSourceTest"`、`python -m pytest bridge/mt5_gateway/tests/test_abnormal_gateway.py -q`。
- 本次收益统计空值格式调整已完成最小验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsDailyReturnsSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsDailyReturnFutureDateSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsMonthlyNoTradePlaceholderSourceTest"`。
- 本次分析页模块显示修复已完成最小验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AnalysisPageRedesignSourceTest" --tests "com.binance.monitor.ui.account.AnalysisGroupedStatsSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsTradeStatsVisibilitySourceTest" --tests "com.binance.monitor.ui.account.AccountStatsScreenAnalysisTargetSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsSecondarySectionAttachSourceTest"`。
- 本次分析页交易记录模块移除已完成最小验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AnalysisPageRedesignSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsDeferredVisibilitySourceTest" --tests "com.binance.monitor.ui.account.AccountStatsSecondarySectionAttachSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsTradeStatsVisibilitySourceTest"`。
- 本次分析页自动重绘门控已完成最小验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsAnalysisAutoRefreshSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityV2RefreshSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsRuntimeRefreshGateSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsScreenHistorySourceTest"`。
- 本次分析页核心统计“累计结余”文案调整已完成最小验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AnalysisStatsSummaryExpandSourceTest" --tests "com.binance.monitor.ui.account.AccountDeferredSnapshotRenderHelperTest" --tests "com.binance.monitor.ui.account.AnalysisGroupedStatsSourceTest"`。
- 本次分析页“累计结余”金额链路修正已完成最小验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountDeferredSnapshotRenderHelperTest" --tests "com.binance.monitor.ui.account.AnalysisStatsSummaryExpandSourceTest" --tests "com.binance.monitor.ui.account.AnalysisGroupedStatsSourceTest"`。

## 近期关键决定和原因
- `MonitorService` 继续以“成功应用后才推进健康时间 / busSeq”为准，避免把收到坏包误判成链路正常。
- 后台运行态固定三档：前台全量、亮屏后台最小悬浮窗、熄屏 `alert-only`；原因是用户当前重点是 CPU/电量，不再优先扩协议面。
- 网关鉴权统一走 `GatewayAuthRequestHelper`，HTTP / WS 客户端不再各自散落 header 逻辑；部署端仍要求 `.env` 显式配置 `GATEWAY_AUTH_TOKEN`。
- 设置页口径固定为“公网入口只读 + Auth Token 可编辑保存”，避免再次出现手机端无 token 输入而部署端要求必填的断链。
- 主线收口策略固定为“先在隔离分支完成验证，再一次性合并回 `main`”，避免未解决冲突和半成品继续滞留在主工作区。
- 服务端合并 alert 的客户端消费策略改为“原始 alert id 去重 + 按品种独立冷却并分别发通知”；原因是业务目标是按产品提醒，不能再让一个品种的冷却压掉另一品种的有效异常。
- 收益统计空值占位固定为“无交易显示 `--`，真实发生交易但收益恰好为 0 仍保留数值格式”；原因是 `+0` / `+0.0%` 会把“无交易”和“真实 0 收益”混在一起。
- 分析页正式主链模块口径固定为“核心统计 -> 收益统计 -> 净值/结余曲线 -> 交易统计”；`交易记录` 已从分析页主链移除，`交易结构统计`、`交易分析入口` 继续保留布局占位但默认隐藏；账户页完整历史成交底部抽屉继续保留。
- 分析 tab 自动刷新口径固定为“仅首次打开或历史成交版本推进时自动重绘”；原因是分析页目标是看已完成交易统计，普通运行态和连接态轮询不应打断滚动并重复重画整页。

## 当前主目录口径
- `app/`：Android 客户端
- `bridge/`：MT5 网关服务端源码
- `deploy/`：部署脚本源
- `scripts/`：构建、校验脚本
- `dist/windows_server_bundle/`：最终 Windows 上传部署目录
- `archived_file/`：已归档历史文件
