# ARCHITECTURE

## 每个文件/模块的职责

- [app/src/main/java/com/binance/monitor/service/MonitorService.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorService.java)
  前台服务入口，负责展示快照刷新、异常判断、通知调度，以及生成悬浮窗统一快照；当前冷启动和 fallback 补拉都已改成直接读取 `v2 market series`，不再给图表历史库写底稿。
- [app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java)
  运行策略辅助工具，负责把前后台状态转换成心跳、异常同步和悬浮窗刷新的统一节奏。
- [app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java)
  v2 同步流刷新决策器，负责把 `syncBootstrap / syncRefresh / syncSummary / syncDelta` 映射成最小补拉动作。
- [app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java)
  监控展示仓库，负责保存主监控页与悬浮窗共用的最新价格/K 线展示快照、连接状态、监控开关和异常记录入口。
- [app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java)
  Binance REST 数据访问层，负责通过韩国服务器的 `/binance-rest` 拉取行情。
- [app/src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java)
  Binance 回退 K 线流管理器，负责通过韩国服务器的 `/binance-ws` 订阅 `@kline_1m`，仅在 `v2 stream` 不健康时补最新展示快照。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java)
  v2 网关客户端，负责请求 `market/account/sync` 新接口，并把响应解析成 APP 侧统一载荷；当前也承接图表页按 `startTime/endTime` 的分页与增量补尾。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java)
  v2 同步流客户端，负责连接 `/v2/stream` 并把统一同步消息解析成 APP 可消费的结构。
- [app/src/main/java/com/binance/monitor/data/local/V2SnapshotStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/V2SnapshotStore.java)
  v2 快照本地存储，负责保存市场快照和账户快照原始 JSON，用于页面快速恢复，不再混存图表各周期序列。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java)
  图表页入口，负责 K 线请求调度、周期切换、指标开关、局部隐私隐藏和右上角刷新/延迟信息；当前最终真值只认服务端 `candles + latestPatch`，本地只保留 `ChartHistoryRepository + 内存窗口` 这一层图表缓存。
- [app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java)
  K 线绘制控件，负责主图、副图、指标、右侧留白、异常点胶囊、成本线和缩放交互。
- [app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java)
  图表视口计算工具，负责 K 线横向边界和右侧留白相关数学逻辑。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartRefreshMetaFormatter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartRefreshMetaFormatter.java)
  倒计时文案工具，负责拼接“剩余秒数/周期秒数 延迟ms”。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartPersistenceWindowHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartPersistenceWindowHelper.java)
  图表持久化窗口工具，负责在 Room 落库前剔除未闭合 latest patch，只保留闭合历史快照。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartScaleGestureResolver.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartScaleGestureResolver.java)
  缩放方向判定工具，负责把双指手势分成横向、纵向和斜向整体缩放。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java)
  账户统计页入口，负责账户概览、收益统计、净值/结余主图、附图、交易分布、交易记录、隐私小眼睛和登录成功提示动画；当前主动刷新已统一改走 `AccountStatsPreloadManager.fetchForUi(...)`。
- [app/src/main/java/com/binance/monitor/ui/account/AccountCurvePointNormalizer.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountCurvePointNormalizer.java)
  账户曲线归一化工具，负责修正空净值/结余、补齐最少两点，并保留历史仓位比例。
- [app/src/main/java/com/binance/monitor/ui/account/AccountCurveHighlightHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountCurveHighlightHelper.java)
  账户曲线共享高亮工具，负责把附图长按位置换算成统一时间点，再回查主图、回撤和日收益当前值。
- [app/src/main/java/com/binance/monitor/ui/account/AccountConnectionTransitionHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountConnectionTransitionHelper.java)
  账户连接状态过渡工具，负责判断何时展示一次性的登录成功提示。
- [app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java)
  净值/结余曲线控件，负责绘制账户曲线，并用高对比样式标记当前周期下的最大回撤区间。
- [app/src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java)
  账户统计分析工具，负责从曲线与交易记录中计算最大回撤、回撤附图、日收益率、交易散点和持仓时间分布。
- [app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java)
  回撤附图控件，负责绘制基于净值序列的回撤曲线。
- [app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java)
  日收益率附图控件，负责绘制正负日收益柱状分布。
- [app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java)
  历史交易分布图控件，负责绘制“最大回撤 vs 收益率”散点。
- [app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java)
  持仓时间分布图控件，负责绘制不同持仓时长桶的交易数量。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java)
  账户预加载管理器，负责后台轻量同步和前台页面进入时的全量补齐；当前已优先走 `v2/account/snapshot + v2/account/history`，失败时才回退旧网关。
- [app/src/main/java/com/binance/monitor/ui/account/AccountPreloadPolicyHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPreloadPolicyHelper.java)
  账户预加载节奏辅助工具，负责把前后台状态和全量/轻量模式转换成统一刷新间隔。
- [app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java)
  MT5 网关客户端，负责请求 `/v1/summary`、`/v1/live`、`/v1/pending`、`/v1/trades`、`/v1/curve` 等接口，并把结果合并回统一账户模型；当前主要保留为 `v2` 失败时的临时回退。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java)
  悬浮窗管理器，负责渲染顶部合并盈亏、连接状态、分产品卡片，并处理长按拖动、最小化、点击还原。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowLayoutHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowLayoutHelper.java)
  悬浮窗布局辅助工具，负责统一展开宽度、内容列宽和产品卡片文本对齐方式。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java)
  悬浮窗文案辅助工具，负责统一“产品（盈亏）”标题，并处理零盈亏的 `$-` 展示规则。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowSnapshot.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowSnapshot.java)
  悬浮窗统一快照模型，保证连接状态、总盈亏和产品卡片在同一次渲染里一起更新。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingSymbolCardData.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingSymbolCardData.java)
  悬浮窗产品卡片数据模型，承载产品名、盈亏、最新价、成交量、成交额。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java)
  悬浮窗持仓聚合器，负责把 MT5 持仓按产品汇总成卡片级别数据。
- [app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java)
  设置首页目录，只负责显示设置分类入口。
- [app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java)
  设置二级页，负责显示悬浮窗、网关地址、主题、Tab、缓存管理等具体设置，并去掉重复隐私入口。
- [app/src/main/java/com/binance/monitor/data/local/ConfigManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/ConfigManager.java)
  本地配置中心，负责保存主题、悬浮窗、Tab、网关地址和其他持久化开关。
- [app/src/main/java/com/binance/monitor/runtime/AppForegroundTracker.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/AppForegroundTracker.java)
  应用前后台状态跟踪器，负责给服务层和预加载层提供统一的前后台切换信号。
- [app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java)
  主题与控件样式管理器，负责页面底色、底部导航、图表按钮条、按钮、复选控件和整体方正风格。
- [app/src/main/java/com/binance/monitor/util/GatewayUrlResolver.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/util/GatewayUrlResolver.java)
  网关地址标准化工具，把用户输入整理成统一可用的基础地址。
- [app/src/main/java/com/binance/monitor/util/NotificationHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/util/NotificationHelper.java)
  通知帮助类，负责前台服务通知和异常交易通知，本轮已升级为更强系统通知样式。
- [app/src/main/java/com/binance/monitor/data/local/db/](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/db)
  Room 数据库层，负责历史 K 线、历史交易、账户摘要、持仓快照、挂单快照。
- [app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java)
  图表历史仓库，负责把上层已经整理好的 K 线窗口直接写入 Room，不再重复回读整段旧历史再合并。
- [bridge/mt5_gateway/server_v2.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/server_v2.py)
  MT5 网关服务，负责 MT5 数据整理和 Binance REST / WebSocket 转发；当前也承载 `v2` 行情、账户、delta、stream 输出，以及远程账号会话接口与运行时缓存清理。
- [bridge/mt5_gateway/v2_session_crypto.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_crypto.py)
  远程账号会话加密模块，负责登录信封公钥生成、`rsa-oaep+aes-gcm` 解密、时间戳校验和 nonce 去重。
- [bridge/mt5_gateway/v2_session_manager.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_manager.py)
  远程账号会话管理器，负责新账号登录、已保存账号切换、退出、异常回滚、审计记录和强一致刷新收口。
- [bridge/mt5_gateway/v2_session_store.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_store.py)
  远程账号会话存储层，负责当前激活账号摘要和已保存账号密文档案的文件读写。
- [bridge/mt5_gateway/admin_panel.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel.py)
  统一服务器控制台服务，负责聚合总览状态、输出中文诊断、提供配置 schema / 变更影响、读取日志、编辑 `.env`、代理异常规则配置，并管理网关 / MT5 / Caddy / Nginx；当前也会透传账号会话摘要。
- [bridge/mt5_gateway/admin_panel_state.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel_state.py)
  控制台总览状态构造器，负责把首页卡片、主动作、最近日志和旧字段兼容收口成统一响应。
- [bridge/mt5_gateway/admin_panel_diagnostics.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel_diagnostics.py)
  控制台诊断构造器，负责把本机网关、公网入口、EA 心跳和代理状态翻译成中文结论与建议动作。
- [bridge/mt5_gateway/admin_panel_config.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel_config.py)
  控制台配置元数据与影响分析工具，负责输出字段分组 schema，并判断保存后哪些组件需要重启。
- [bridge/mt5_gateway/v2_market.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_market.py)
  v2 行情模型工具，负责把 Binance K 线原始数据整理成闭合 candles 与 latestPatch，并能从 REST 窗口里拆出最后一根未闭合 patch。
- [bridge/mt5_gateway/v2_account.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_account.py)
  v2 账户模型工具，负责把账户快照、历史成交、曲线转换成 APP 侧展示模型。
- [bridge/mt5_gateway/start_gateway.ps1](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/start_gateway.ps1)
  网关启动脚本，负责加载 `.env` 并使用本地虚拟环境启动服务。
- [bridge/mt5_gateway/start_admin_panel.ps1](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/start_admin_panel.ps1)
  管理面板启动脚本，负责加载 `.env` 并使用本地虚拟环境启动轻量 Web 面板。
- [deploy/tencent/windows/](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows)
  Windows 部署脚本源码目录，负责启动、自启注册、健康检查和 Caddy 配置；现在同一套脚本同时兼容“仓库根目录”和“部署包根目录”两种布局。
- [scripts/build_windows_server_bundle.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/scripts/build_windows_server_bundle.py)
  Windows 部署包构建脚本，负责从 `bridge/mt5_gateway` 与 `deploy/tencent/windows` 生成唯一上传目录 `dist/windows_server_bundle`，并把根目录双击部署脚本一起放进部署包。

## 模块之间的调用关系

- `MainActivity` -> `BinanceApiClient`
  主监控页按需从韩国服务器转发入口读取公共行情补充数据。
- `MonitorService` -> `FallbackKlineSocketManager`
  只在 `v2 stream` 不健康时接收官方 `@kline_1m` 回退流，用来维持主监控页和悬浮窗展示快照。
- `MonitorService` -> `GatewayV2StreamClient` -> `server_v2.py /v2/stream`
  消费统一同步消息，先把 APP 的同步入口收口到 `v2 stream`。
- `MonitorService` -> `V2StreamRefreshPlanner`
  根据 `v2 stream` 消息内容决定当前是补市场、补账户，还是只刷新悬浮窗。
- `MonitorService` -> `GatewayV2Client`
  当 `v2 stream` 带市场变化，或 fallback 流 stale 时，补拉最新 `1m` 市场序列，修正最新价和最近收盘。
- `MonitorService` -> `MonitorRepository`
  写入主监控页与悬浮窗共用的最新展示快照。
- `AppForegroundTracker` -> `MonitorService` / `MonitorRuntimePolicyHelper`
  统一把应用前后台状态传给服务调度层，决定当前应使用哪一档刷新节奏。
- `MonitorService` -> `AccountStorageRepository`
  读取最新 MT5 持仓快照。
- `MonitorService` -> `FloatingPositionAggregator` + `FloatingWindowSnapshot` + `FloatingWindowManager`
  把行情和持仓聚合成统一快照，再一次性刷新悬浮窗。
- `MarketChartActivity` -> `ChartHistoryRepository` -> Room
  先读本地历史，再按需补网络数据；落库时直接 upsert 当前整理好的窗口，不再回读整段旧历史。
- `MarketChartActivity` -> `ChartPersistenceWindowHelper` -> `ChartHistoryRepository`
  最新窗口在持久化前先剔除未闭合 patch，避免 Room 把活 K 线当成历史真值恢复。
- `MarketChartActivity` -> `GatewayV2Client` -> `server_v2.py /v2/market/candles`
  图表页最终真值、左滑历史分页、以及增量补尾都从服务端读取闭合 candles 与 latestPatch，本地只负责快照与展示。
- `MarketChartActivity` -> `ChartRefreshMetaFormatter`
  把自动刷新倒计时和最近一次成功请求延迟整理成统一文案。
- `KlineChartView` -> `ChartScaleGestureResolver`
  根据双指跨度变化决定走横向、纵向还是整体缩放。
- `AccountStatsPreloadManager` -> `GatewayV2Client` -> `server_v2.py /v2/account/*`
  后台优先同步账户快照、历史成交和净值曲线，并原子替换本地账户缓存。
- `AccountStatsPreloadManager` -> `Mt5BridgeGatewayClient` -> `server_v2.py /v1/*`
  作为 v2 失败时的回退链路，暂时保留旧账户接口。
- `AppForegroundTracker` -> `AccountPreloadPolicyHelper` -> `AccountStatsPreloadManager`
  统一把应用前后台状态转换成账户预加载节奏，避免旧常量分散在多个入口里。
- `AccountStatsBridgeActivity` -> `AccountStatsPreloadManager`
  主动刷新时统一复用 `v2` 优先抓取逻辑，不再由页面自己直连旧网关。
- `MonitorService` -> `AccountStatsPreloadManager`
  当 `v2 stream` 带账户变化时，统一走 `fetchForUi(...)` 刷新本地账户真值，避免服务层再单独拼账户接口。
- `AccountStatsBridgeActivity` -> `AccountCurvePointNormalizer` -> `CurvePoint`
  先把账户曲线修正成可展示的连续序列，再驱动主图和仓位比例附图。
- `AccountStatsBridgeActivity` -> `AccountCurveHighlightHelper` -> `EquityCurveView` / `PositionRatioChartView` / `DrawdownChartView` / `DailyReturnChartView`
  附图长按时按共享横轴位置反推目标时间，再同步四张图的十字光标和主图弹窗数据。
- `AccountStatsBridgeActivity` -> `AccountConnectionTransitionHelper`
  判断账户连接是否从未连接切到已连接，决定是否播放登录成功提示。
- `AccountStatsBridgeActivity` -> `CurveAnalyticsHelper` -> `EquityCurveView` / `DrawdownChartView` / `DailyReturnChartView` / `TradeDistributionScatterView` / `HoldingDurationDistributionView`
  先把曲线和交易记录转成统一统计结果，再驱动主图、副图和两张分布图。
- `FloatingWindowManager` -> `FloatingWindowLayoutHelper` + `FloatingWindowTextFormatter`
  统一产品卡片的宽度、左对齐规则和标题盈亏文案。
- `SettingsActivity` -> `SettingsSectionActivity`
  设置首页只负责分类入口，具体设置都在二级页里完成。
- `SettingsSectionActivity` -> `ConfigManager`
  持久化主题、悬浮窗、Tab、缓存和网关地址设置。
- `ConfigManager` -> `GatewayUrlResolver`
  统一派生 Binance 与 MT5 请求地址。
- `NotificationHelper` <- `MonitorService`
  异常交易触发后统一发系统通知。
- `MainViewModel` / `MainActivity` -> `MonitorRepository`
  读取主监控页当前展示所需的最新价格/K 线快照，而不是参与图表真值计算。
- `admin_panel.py` -> `server_v2.py /health`、`/v1/source`、`/v1/abnormal`、`/v1/abnormal/config`、`/internal/admin/cache/clear`
  统一控制台通过本机 HTTP 读取网关状态、诊断输入、异常规则配置，并触发缓存清理。
- `server_v2.py /v2/session/*` -> `v2_session_manager.py` -> `v2_session_store.py` / `v2_session_crypto.py`
  远程账号会话链路先解密登录信封或读取已保存账号档案，再切换 MT5 运行态，并把结果落盘到当前激活账号摘要。
- `v2_session_manager.py` -> `server_v2.py` 会话网关适配器
  会话管理器通过适配器执行 `login_mt5/switch_mt5_account/logout_mt5/clear_account_caches/force_account_resync`，把运行态切换和缓存收口统一封装在网关层。
- `admin_panel.py` -> `server_v2.py /health`、`/v1/source`
  控制台总览页从网关聚合结果中透传 `session.activeAccount` 和 `session.savedAccountCount`，用于展示当前激活账号与已保存账号数量。
- `admin_panel.py` -> PowerShell / Windows 计划任务 / 进程控制
  统一控制台直接管理网关、MT5 客户端、Caddy、Nginx 的启停与重启。

## 关键的设计决定和原因

- 实盘升级先按“交易网关 -> 命令状态机 -> 强一致同步 -> 交易界面增强”四阶段推进；原因是当前项目最大缺口不在图表展示，而在没有真正的下单、校验、确认、回执和审计主链。
- APP 从实盘阶段开始要采用“命令式 + 快照式”双轨；原因是用户发出交易指令后，页面不能只靠旧快照轮询判断结果，必须显式区分草稿、预检查、待确认、提交中、已受理、已拒绝、超时和已结算。
- 服务端交易能力要独立于现有读取接口新增 `trade/check`、`trade/submit`、`trade/result` 或事件流；原因是最小手数、步进、冻结距离、交易时间、保证金不足、重报价和账户模式判断都不能放在手机端猜测。
- 第一阶段范围固定为“最小交易闭环”，只覆盖单笔市价开仓、单笔平仓、挂单新增/删除、单笔 TP/SL 修改、统一确认弹窗和交易后强一致刷新；原因是这是一条足够短、又不会引入结构性错误的正确路径。
- 图表直接交易、批量交易、Close By、DOM、一键交易和高级风险控制全部后置；原因是这些能力都建立在底层交易命令链可靠之后，先做界面会放大误操作和状态错乱风险。
- Binance 行情链路统一走韩国服务器转发，避免手机继续直连 Binance 官方地址。
- 新架构下把“Binance 行情真值”和“MT5 账户真值”明确拆开：行情、K 线、成交量、指标只认 Binance，MT5 只负责账户侧事实；原因是这样才能消除 APP 本地拼 K 和多份缓存互相覆盖造成的口径漂移。
- 图表页当前不再让本地分钟底稿参与最终真值计算，只保留服务端 `candles + latestPatch` 和本地展示缓存；原因是之前本地聚合尾部会反向污染官方历史窗口。
- 图表页这轮继续收口为“闭合历史快照 + 最新 1 条 patch”：分钟实时 patch 只允许覆盖 `1d` 及以下周期，且未闭合 patch 不再写入 Room；原因是之前 1m 短尾部会把整窗历史跳过，且长周期还会被分钟 patch 错误覆盖。
- 图表本地缓存当前只保留 `ChartHistoryRepository` 这一层，已删除旧 `KlineCacheStore` 文件缓存，并禁止把图表序列写进 `V2SnapshotStore`；原因是旧设计会形成“图表缓存清理误伤账户快照”和“同一份 K 线被多处重复存储”的结构性问题。
- 图表历史仓库当前不再负责“读旧历史再合并”，而只负责写入上层已整理好的窗口；原因是图表页内存窗口已经是本轮展示真值，仓库层重复再读一次只会增加 IO 和复杂度。
- `MonitorService` 不再承担“预热图表 1m 底稿”的职责，冷启动和 stale 回退都统一改成 `GatewayV2Client.fetchMarketSeries(...)`；原因是服务层现在只负责展示快照和悬浮窗，不应再反向参与图表历史真值。
- 账户预加载改成“v2 成功就原子替换，失败才回退旧网关”；原因是继续走旧增量拼装，会把新架构下的历史成交和净值曲线再次拆散。
- MT5 数据链路拆成摘要、轻实时持仓、挂单增量、成交增量、曲线追加，减少日常流量和重复全量请求。
- 网关快照缓存改成“按最近使用裁剪 + EA 新鲜时短时平滑续用”；原因是这样能同时压住 `snapshot/sync` 缓存的内存占用，并减少固定轮询下缓存命中与重建交替造成的延迟抖动。
- `server_v2.py` 的历史成交映射改成“按成交顺序 + FIFO 开仓库存”配对；原因是多次加仓、部分平仓、反手时，不能再用单个生命周期均价去覆盖全部平仓记录。
- MT5 历史成交时间改成“网关统一按 `MT5_TIME_OFFSET_MINUTES` 做可配置偏移”；原因是当前用户环境里，MT5 Python 返回时间与本地北京时间存在固定偏差，若只在图表层补偿会继续造成交易列表、历史成交点和账户曲线三处口径不一致。
- 图表页实时刷新改成“未收盘分钟线先进本地分钟底稿，再覆盖当前周期最新尾部”；原因是这样既能补上 1 分钟实时 K 线，也能继续沿用本地多周期缓存减少切周期卡顿。
- 服务端异常同步 `HTTP 404` 改成客户端一次识别后暂停轮询；原因是接口未部署时继续固定频率请求只会刷日志和浪费流量。
- 服务器管理 UI 独立为 `admin_panel.py` 控制台服务，而不是塞进 `server_v2.py` 本体；原因是这样即便主网关被停止或重启，控制台仍可继续提供浏览器入口，才能真正完成“启动 / 停止 / 重启网关”这类操作。
- Windows 部署现收口为“源码两处 + 产物一处”：源码只维护 `bridge/mt5_gateway` 和 `deploy/tencent/windows`，部署时统一由 `scripts/build_windows_server_bundle.py` 生成 `dist/windows_server_bundle`；原因是仓库里长期保留静态部署副本会导致改动位置漂移、文件不同步和现场排障困难。
- Windows 部署包现要求闭合为单根目录 `C:\mt5_bundle\windows_server_bundle`；原因是用户明确要求把整个 `dist/windows_server_bundle` 文件夹一次性复制到服务器，而不是拆成两个子目录分别处理。
- Windows 部署脚本对 `caddy.exe` 采用兼容查找：优先 `windows_server_bundle\windows\caddy.exe`，其次 `windows_server_bundle\caddy.exe`，最后 `C:\mt5_bundle\caddy.exe` 这类上级目录；原因是服务器现场已存在历史安装位置，部署脚本需要兼容而不是强迫用户重新搬动二进制文件。
- 远程账号会话当前采用“单用户、多账号、任意时刻一个激活账号”的服务端模型；原因是这样能在现有 MT5 网关结构内用最小正确改动闭合安全、切换和同步链路。
- 远程登录链路当前采用 `cryptography` 实现 `rsa-oaep+aes-gcm`，账号落盘仍用 Windows DPAPI；原因是标准库无法完整提供设计要求的公钥信封解密，而本机密文档案仍应交给 Windows 本机保护能力处理。
- nonce 去重当前只做进程内内存态；原因是本阶段先收口单机单进程的最小安全闭环，多实例共享去重留到后续阶段再做集中存储。
- 账户预加载节奏从管理器内联常量改成 `AccountPreloadPolicyHelper` 统一计算；原因是这样更容易和前后台策略保持一致，也便于后续继续压缩账户页相关资源消耗。
- 悬浮窗改为“统一快照 + 产品卡片”模型，解决不同字段更新时间不一致的问题。
- 悬浮窗拖动增加长按触发、位移阈值和帧节流，减少拖动卡顿和误触。
- 图表历史、历史交易、账户摘要、持仓快照统一切到 Room，满足“拉过就长期保留”的要求。
- 图表刷新前不再清空已显示 K 线，避免刷新瞬间白屏。
- 行情持仓页的隐私隐藏改为“保留 K 线主体、只隐藏持仓相关叠加层 + 当前持仓模块打码”，避免整页遮罩把行情走势一起盖掉。
- 隐私入口统一收口到账户统计页的小眼睛，设置页不再保留重复开关，避免两套入口互相打架。
- K 线缩放增加斜向整体缩放分支，解决双指斜拉时只能勉强落到横向或纵向的问题。
- `MA / EMA` 默认关闭，降低初始图表干扰。
- 设置页拆成“目录首页 + 二级详情页”，提升设置层级清晰度。
- 主题递归样式不再把 `Switch / RadioButton / CheckBox` 当普通按钮套背景，解决 OR / AND、启用开关下方方框背景问题。
- 登录成功提示只在“未连接 -> 已连接”时触发一次，避免自动刷新期间重复弹出。
- 月收益表改为“左侧年份整块 + 右侧三行月份 + 横向滚动”，解决年份不对齐和百分比显示不全的问题。
- MT5 网关曲线改为“成交 + 持仓 + 价格”重放，解决净值与结余长期重合的问题。
- 账户统计页把最大回撤、回撤附图、日收益率和交易分布统一建立在同一套净值口径上，避免不同模块口径不一致。
- `MonitorRepository` 收口成“展示快照仓库”而不是“实时主链仓库”；原因是当前图表与账户真值已经分别切到服务端 `v2`，继续让旧名字留着，只会误导后续维护时把它当成主数据源。
- 旧 Binance WebSocket 管理器改名为 `FallbackKlineSocketManager`，回调也统一改成 fallback 语义；原因是它现在只承担 `v2 stream` 失效时的回退输入，继续保留主链式命名会误导后续维护。
- 异常交易提醒先在 App 端提升为更强系统通知；服务器端异常判断迁移后续再评估，不在本轮硬塞进去。
