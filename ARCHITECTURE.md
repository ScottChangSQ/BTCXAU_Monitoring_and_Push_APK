# ARCHITECTURE

## 每个文件/模块的职责

- [app/src/main/java/com/binance/monitor/service/MonitorService.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorService.java)
  前台服务入口，负责展示快照刷新、异常判断、通知调度，以及生成悬浮窗统一快照；当前冷启动和 fallback 补拉都已改成直接读取 `v2 market series`，不再给图表历史库写底稿。
- [app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java)
  运行策略辅助工具，负责把前后台状态转换成心跳、异常同步和悬浮窗刷新的统一节奏。
- [app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java)
  v2 同步流刷新决策器，负责把 `syncBootstrap / syncEvent / heartbeat` 映射成最小消费动作与必要的历史补拉判断。
- [app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java)
  监控展示仓库，负责保存主监控页与悬浮窗共用的最新价格/K 线展示快照、连接状态、监控开关和异常记录入口。
- [app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java)
  Binance REST 数据访问层，负责通过 `/binance-rest` 拉取行情；图表长周期现在固定只走 Binance REST 原生周期接口，不再切到日线聚合或历史回退链。
- [app/src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java)
  Binance 回退 K 线流管理器，负责通过韩国服务器的 `/binance-ws` 订阅 `@kline_1m`，仅在 `v2 stream` 不健康时补最新展示快照。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java)
  v2 网关客户端，负责请求 `/v2/market/*` 与 `/v2/account/*` 并把响应解析成 APP 侧统一载荷；当前也承接图表页按 `startTime/endTime` 的分页与增量补尾。`/v2/account/snapshot` 解析已改为严格契约：缺失 `account` 对象直接报错，不再本地拼字段兜底。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java)
  v2 会话客户端，负责请求 `/v2/session/*` 并解析 public-key、status、login/switch/logout 回执。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java)
  v2 同步流客户端，负责连接 `/v2/stream` 并把统一同步消息解析成 APP 可消费的结构。
- [app/src/main/java/com/binance/monitor/security/SessionCredentialEncryptor.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/security/SessionCredentialEncryptor.java)
  APP 会话加密器，负责把账号、密码、服务器封装成 `rsa-oaep+aes-gcm` 登录信封。
- [app/src/main/java/com/binance/monitor/security/SecureSessionPrefs.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/security/SecureSessionPrefs.java)
  APP 安全会话偏好，负责用 Android Keystore 加密保存最近一次远程会话摘要和已保存账号列表缓存；logout 时只清当前激活账号，不清已保存账号摘要。
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
  账户统计页入口，负责账户概览、收益统计、净值/结余主图、附图、交易分布、交易记录、隐私小眼睛和登录成功提示动画；当前主动刷新已统一改走 `AccountStatsPreloadManager.fetchForUi(...)`，并已接入远程账号会话面板。
- [app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java)
  远程账号会话协调器，负责把公钥获取、加密登录、已保存账号切换、退出和切换后缓存清理串成单条主链。
- [app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java)
  远程账号会话状态机，负责明确区分 `encrypting/submitting/switching/syncing/active/failed`。
- [app/src/main/java/com/binance/monitor/ui/account/AccountCurvePointNormalizer.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountCurvePointNormalizer.java)
  账户曲线归一化工具，只负责过滤无效点、排序和按时间戳去重，不再本地补点、补值或补仓位比例。
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
  账户预加载管理器，负责承接服务端已发布的账户运行态并管理历史补拉；当前运行态只消费 `v2/stream.accountRuntime`，只有在 `historyRevision` 变化或本地还没有历史时才补拉 `v2/account/history`，不再定时补拉 `/v2/account/snapshot`，也不再本地回填 `open/close time`、`open/close price` 或秒级时间戳；运行态连接状态只认完整账号身份，`remote_logged_out` 会明确收口为未连接。
- [app/src/main/java/com/binance/monitor/ui/account/AccountPreloadPolicyHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPreloadPolicyHelper.java)
  账户预加载节奏辅助工具，负责把前后台状态和全量/轻量模式转换成统一刷新间隔。
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
  设置二级页，负责显示悬浮窗、固定公网入口、主题、Tab、缓存管理等具体设置，并去掉重复隐私入口；网关项当前只读展示正式入口。
- [app/src/main/java/com/binance/monitor/data/local/ConfigManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/ConfigManager.java)
  本地配置中心，负责保存主题、悬浮窗、Tab 和其他持久化开关；当前 MT5 网关入口已固定为唯一 HTTPS 入口 `https://tradeapp.ltd`，不再接受运行时改写。
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
  MT5 网关服务，负责 MT5 数据整理和 Binance REST / WebSocket 转发；当前也承载 `v2` 行情、账户、stream 输出，以及远程账号会话接口与运行时缓存清理。轻快照主链现在只读取账户概览、持仓、挂单和 `accountMeta.historyRevision`（成交历史 canonical digest），不再展开完整历史对象，并增加 single-flight + 会话代次保护（`session_snapshot_epoch`），避免高频消费链并发放大或旧会话结果回写；`/v2/account/snapshot` 与 `/v2/market/snapshot` 现在都会先看远程会话状态，只有存在激活账号时才读取 MT5 轻快照，`logged_out` 时统一返回标准空账户快照；`/v2/account/history` 的曲线和交易列表继续复用同一份 MT5 成交历史；轻快照/历史缓存 `builtAt` 统一按“构建完成时刻”写入，轻快照缓存默认 1s，`v2/stream` 默认按 `V2_STREAM_PUSH_INTERVAL_MS=1000` 固定节拍推送，并且账户 delta 不再输出 `refreshHint`；producer 现在在服务启动时初始化，请求路径只读已发布状态。
- [bridge/mt5_gateway/v2_session_crypto.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_crypto.py)
  远程账号会话加密模块，负责登录信封公钥生成、`rsa-oaep+aes-gcm` 解密、时间戳校验和 nonce 去重。
- [bridge/mt5_gateway/v2_session_models.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_models.py)
  远程账号会话领域模型，负责统一定义账号摘要、公钥载荷、状态载荷、动作回执和登录信封结构，避免服务端继续散落裸字典。
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
  网关启动脚本，负责加载 `.env`、比较 `requirements.txt` 指纹并使用本地虚拟环境启动服务；当前文件指纹改为脚本内置的 .NET `SHA256`，不再依赖 `Get-FileHash`。
- [bridge/mt5_gateway/start_admin_panel.ps1](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/start_admin_panel.ps1)
  管理面板启动脚本，负责加载 `.env`、比较 `requirements.txt` 指纹并使用本地虚拟环境启动轻量 Web 面板；当前文件指纹同样改为脚本内置的 .NET `SHA256`。
- [deploy/tencent/windows/](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows)
  Windows 部署脚本源码目录，负责启动、自启注册、健康检查和 Caddy 配置；现在同一套脚本同时兼容“仓库根目录”和“部署包根目录”两种布局，首次引导脚本也已改成脚本内置 .NET `SHA256` 指纹计算。
- [deploy/tencent/windows/deploy_bundle.ps1](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows/deploy_bundle.ps1)
  Windows 一键重部署入口，负责按“停旧服务与占口 -> 校验脚本 -> 初始化环境 -> 注册任务 -> 启动后台服务 -> 健康检查”的顺序执行，并把状态同步到唯一的 GUI 窗口；健康检查现在还会校验 `bundleFingerprint` 与 `wss://tradeapp.ltd/v2/stream` 首条消息。
- [deploy/tencent/windows/deploy_bundle.cmd](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows/deploy_bundle.cmd)
  Windows 双击部署入口，负责以隐藏方式拉起 `deploy_bundle.ps1` 的 GUI 模式，避免额外弹出命令行窗口。
- [scripts/build_windows_server_bundle.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/scripts/build_windows_server_bundle.py)
  Windows 部署包构建脚本，负责从 `bridge/mt5_gateway` 与 `deploy/tencent/windows` 生成唯一上传目录 `dist/windows_server_bundle`，并把根目录双击部署脚本、bundle README、完整会话链文件和 `bundle_manifest.json` 运行指纹一起放进部署包。

## 模块之间的调用关系

- `MainActivity` -> `BinanceApiClient`
  主监控页按需从韩国服务器转发入口读取公共行情补充数据。
- `MonitorService` -> `FallbackKlineSocketManager`
  fallback 行情流现在只保留观测用途，不再直接写主监控页和悬浮窗真值。
- `MonitorService` -> `GatewayV2StreamClient` -> `server_v2.py /v2/stream`
  消费统一同步消息，先把 APP 的同步入口收口到 `v2 stream`。
- `MonitorService` -> `V2StreamRefreshPlanner`
  根据 `v2 stream` 消息内容决定当前是应用市场运行态、应用账户运行态、按 `historyRevision` 补历史，还是只刷新悬浮窗。
- `MonitorService` -> `GatewayV2Client`
  图表页按需读取 `v2/market/candles`，账户侧只在 `historyRevision` 前进时补拉 `v2/account/history`；运行态不再补拉 `v2/account/snapshot`，fallback 流也不再驱动主链补拉。
- `MonitorService` -> `MonitorRepository`
  写入主监控页与悬浮窗共用的最新展示快照。
- `AppForegroundTracker` -> `MonitorService` / `MonitorRuntimePolicyHelper`
  统一把应用前后台状态传给服务调度层，决定当前应使用哪一档刷新节奏。
- `MonitorService` -> `AccountStatsPreloadManager` -> `AccountStorageRepository`
  先应用 stream 下发的账户运行态，再按 `historyRevision` 条件补拉历史并统一落本地库。
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
  账户运行态直接消费 `v2/stream.accountRuntime`；只有在 `historyRevision` 前进或本地还没有历史时，才补拉 `v2/account/history`，并原子替换本地历史缓存；若内存缓存为空，则回退到本地已持久化 `historyRevision` 判断是否真的需要全量补拉。
- `AppForegroundTracker` -> `AccountPreloadPolicyHelper` -> `AccountStatsPreloadManager`
  统一把应用前后台状态转换成账户预加载节奏，避免旧常量分散在多个入口里。
- `AccountStatsBridgeActivity` -> `AccountStatsPreloadManager`
  主动刷新时统一复用 `v2` 优先抓取逻辑，不再由页面自己直连旧网关。
- `AccountStatsBridgeActivity` -> `AccountRemoteSessionCoordinator` -> `GatewayV2SessionClient`
  账户页远程会话入口统一先拉公钥或读取状态，再执行登录、切换、退出，避免页面自己拼会话接口。
- `AccountRemoteSessionCoordinator` -> `SessionCredentialEncryptor`
  登录新账号时先在 APP 本地生成 `rsa-oaep+aes-gcm` 登录信封，再把密文提交给服务端。
- `AccountRemoteSessionCoordinator` -> `SecureSessionPrefs`
  会话链路每次切换后都会把当前激活账号和已保存账号列表缓存到 Android Keystore 加密存储，避免重新启动页面时只剩明文偏好。
- `GatewayV2SessionClient` -> `server_v2.py /v2/session/*` -> `v2_session_manager.py`
  远程账号会话统一由服务端作为唯一 MT5 执行主体，APP 不直接连接 MT5 服务器，也不直接保存可复用的 MT5 明文密码。
- `ConfigManager` / 固定正式入口 -> `GatewayV2SessionClient`
  远程账号会话当前固定走 `https://tradeapp.ltd`；`/v2/session/public-key` 与其余会话接口都必须通过 HTTPS 暴露，否则公钥可能被替换，App 侧加密会失去意义。
- `MonitorService` -> `AccountStatsPreloadManager`
  当 `v2 stream` 带账户运行态时直接应用已发布快照；只有 `historyRevision` 前进时才刷新历史，避免服务层再周期性补拉账户 snapshot；若补拉期间又收到更新 revision，会在当前一轮结束后继续追到最新版本。
- `AccountStatsBridgeActivity` -> `AccountCurvePointNormalizer` -> `CurvePoint`
  只对服务端曲线做严格清洗，不再在页面层补点、补值、补仓位比例，再驱动主图和附图。
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
  持久化主题、悬浮窗、Tab、缓存设置，并只读展示固定正式入口。
- `NotificationHelper` <- `MonitorService`
  异常交易触发后统一发系统通知。
- `MainViewModel` / `MainActivity` -> `MonitorRepository`
  读取主监控页当前展示所需的最新价格/K 线快照，而不是参与图表真值计算。
- `admin_panel.py` -> `server_v2.py /health`、`/v1/source`、`/v1/abnormal`、`/v1/abnormal/config`、`/internal/admin/cache/clear`
  统一控制台通过本机 HTTP 读取网关状态、诊断输入、异常规则配置，并触发缓存清理。
- `server_v2.py /v2/session/*` -> `v2_session_manager.py` -> `v2_session_store.py` / `v2_session_crypto.py`
  远程账号会话链路先解密登录信封或读取已保存账号档案，再切换 MT5 运行态，并把结果落盘到当前激活账号摘要。
- `v2_session_crypto.py` / `v2_session_manager.py` -> `v2_session_models.py`
  会话加密层和会话管理层都统一通过领域模型生成公钥、状态和回执结构，确保服务端输出口径和 APP 解析模型一致。
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
- 图表长周期（周/月）当前固定只走 Binance REST 原生周期接口，不再按返回条数切到日线聚合或通用历史回退；原因是单一窗口必须保持单一数据口径，不能在两套算法之间切换。
- 图表本地缓存当前只保留 `ChartHistoryRepository` 这一层，已删除旧 `KlineCacheStore` 等重复快照存储；原因是旧设计会形成“同一份 K 线被多处重复存储”和缓存职责混乱的结构性问题。
- 图表历史仓库当前不再负责“读旧历史再合并”，而只负责写入上层已整理好的窗口；原因是图表页内存窗口已经是本轮展示真值，仓库层重复再读一次只会增加 IO 和复杂度。
- `MonitorService` 不再承担“预热图表 1m 底稿”的职责，冷启动和 stale 回退都统一改成 `GatewayV2Client.fetchMarketSeries(...)`；原因是服务层现在只负责展示快照和悬浮窗，不应再反向参与图表历史真值。
- 账户预加载改成“高频轻快照 + 历史按 `historyRevision` 条件补拉”；原因是账户页平时只需要账户概览、当前持仓和挂单，继续每轮都拉全量历史会浪费流量并拖慢刷新。
- 账户运行态的“已连接/未连接”现在只认完整远程账号身份，不再把 `remote_logged_out` 的空快照当成已连接；原因是账户页和交易执行链都不能基于假连接状态继续工作。
- `MonitorService` 的历史补拉改成“串行执行 + 暂存最新 revision 续跑”；原因是 stream 可能在单次全量 history 拉取期间继续前进，如果只保留单个 `inFlight` 开关会丢失后到 revision。
- 仓库内已失联的 `AccountStatsLiveActivity + Mt5GatewayClient` 旧账户页链路已直接删除；原因是它们不再属于任何业务入口，却仍保留 `/v1` 账户快照与本地补位逻辑，继续保留只会制造维护误导。
- 账户展示链当前收口成“服务端指标直出 + 页面纯消费”；原因是 `overviewMetrics / curveIndicators / statsMetrics` 已由服务端给出，页面层继续本地补算只会重新制造第二套口径。
- 账户历史与曲线主链当前只接受服务端标准时间和价格字段；原因是客户端再做秒转毫秒、`timestamp -> open/close time`、`price -> open/close price` 这类补位，会把纯消费链重新拉回本地拼装。
- 会话收口当前采用“login/switch 成功后必须 `fetchStatus()`，且只认 `status.activeAccount`”；原因是 `receipt/status/本地 saved account/输入值` 多路拼装会继续制造账号身份分裂。
- 当 `receipt.activeAccount` 与 `status.activeAccount` 冲突时直接失败；原因是这代表服务器会话真值未闭合，前端不能自行猜测哪一个才是目标账号。
- MT5 数据链路拆成摘要、轻实时持仓、挂单增量、成交增量、曲线追加，减少日常流量和重复全量请求。
- 网关快照缓存改成“按最近使用裁剪 + EA 新鲜时短时平滑续用”；原因是这样能同时压住 `snapshot/sync` 缓存的内存占用，并减少固定轮询下缓存命中与重建交替造成的延迟抖动。
- `server_v2.py` 的历史成交映射改成“按成交顺序 + FIFO 开仓库存”配对；原因是多次加仓、部分平仓、反手时，不能再用单个生命周期均价去覆盖全部平仓记录。
- MT5 历史成交时间改成“网关统一按 `MT5_TIME_OFFSET_MINUTES` 做可配置偏移”；原因是当前用户环境里，MT5 Python 返回时间与本地北京时间存在固定偏差，若只在图表层补偿会继续造成交易列表、历史成交点和账户曲线三处口径不一致。
- 图表页实时刷新改成“未收盘分钟线先进本地分钟底稿，再覆盖当前周期最新尾部”；原因是这样既能补上 1 分钟实时 K 线，也能继续沿用本地多周期缓存减少切周期卡顿。
- 服务端异常同步 `HTTP 404` 改成客户端一次识别后暂停轮询；原因是接口未部署时继续固定频率请求只会刷日志和浪费流量。
- 服务器管理 UI 独立为 `admin_panel.py` 控制台服务，而不是塞进 `server_v2.py` 本体；原因是这样即便主网关被停止或重启，控制台仍可继续提供浏览器入口，才能真正完成“启动 / 停止 / 重启网关”这类操作。
- Windows 部署现收口为“源码两处 + 产物一处”：源码只维护 `bridge/mt5_gateway` 和 `deploy/tencent/windows`，部署时统一由 `scripts/build_windows_server_bundle.py` 生成 `dist/windows_server_bundle`；原因是仓库里长期保留静态部署副本会导致改动位置漂移、文件不同步和现场排障困难。
- 线上部署验收现在必须同时满足“健康接口来自当前 bundle 指纹”和“`wss://tradeapp.ltd/v2/stream` 能收到首条消息”；原因是过去只看 `200` 无法证明公网实际运行的是哪一版网关，也无法证明 websocket 主链真的可用。
- 轻快照主链当前只负责账户摘要、持仓、挂单和 `historyRevision`，不再在高频链里展开全量历史对象；原因是账户页需要服务端真值来判断“是否需要刷新历史”，但不能为了这个条件判断重新回到每轮全量历史重建。
- `/v2/account/snapshot` 当前直接使用 MT5 轻快照，并只保留当前概览指标、持仓和挂单；原因是账户页和同步链需要的是当前账户真值，不应再被完整历史、曲线和成交重建拖慢。
- 完整账户快照当前只拉一次 MT5 成交历史，再同时供曲线与交易列表复用；原因是之前曲线和交易映射各扫一次历史，会在 `state_lock` 内重复放大耗时。
- `v2/stream` 当前先看会话真值，再决定是否读取 MT5 账户快照；原因是未激活远程会话时，监控链只需要市场真值和空账户摘要，不应把 websocket 首包建立建立在 MT5 初始化之上。
- Windows 部署包现要求闭合为单根目录 `C:\mt5_bundle\windows_server_bundle`；原因是用户明确要求把整个 `dist/windows_server_bundle` 文件夹一次性复制到服务器，而不是拆成两个子目录分别处理。
- Windows 部署脚本对 `caddy.exe` 采用兼容查找：优先 `windows_server_bundle\windows\caddy.exe`，其次 `windows_server_bundle\caddy.exe`，最后 `C:\mt5_bundle\caddy.exe` 这类上级目录；原因是服务器现场已存在历史安装位置，部署脚本需要兼容而不是强迫用户重新搬动二进制文件。
- Windows 一键重部署现收口为“前台唯一 GUI + 后台隐藏 worker”；原因是用户要求服务器端除了状态窗口外不再出现其他前台窗口，同时关闭状态窗口也不能影响后台服务继续运行。
- Windows 一键重部署当前会先显式停掉旧计划任务、旧网关、旧面板、旧 Caddy / Nginx，并强制释放 `80 / 443 / 2019 / 8787 / 8788`；原因是如果不先清掉旧进程与端口占用，重新部署最容易在服务器现场直接失败。
- Windows 启动脚本和首次引导脚本当前统一使用脚本内置 .NET `SHA256`，不再依赖 `Get-FileHash`；原因是服务器真实启动链已经证明，部分 PowerShell 环境里缺少该 cmdlet 时，网关会在健康检查前就直接起不来。
- 远程账号会话当前采用“单用户、多账号、任意时刻一个激活账号”的服务端模型；原因是这样能在现有 MT5 网关结构内用最小正确改动闭合安全、切换和同步链路。
- 远程登录链路当前采用 `cryptography` 实现 `rsa-oaep+aes-gcm`，账号落盘仍用 Windows DPAPI；原因是标准库无法完整提供设计要求的公钥信封解密，而本机密文档案仍应交给 Windows 本机保护能力处理。
- Task 1 的会话模型当前已从“占位 dataclass”收口成真实接口模型，并由加密层与会话管理层直接复用；原因是如果模型只存在文件里、不参与返回结构生成，后续维护仍会继续依赖散落字典，容易再次出现前后端字段漂移。
- APP 侧远程会话当前采用“accepted 进入 syncing，只有新账号快照真正落地后才进入 active”的状态机；原因是账户切换不能只靠接口受理结果判断成功，否则会出现伪成功状态。
- APP 本地已停止持久化明文 MT5 密码，只保留 Android Keystore 加密后的会话摘要；原因是服务端已经承担加密记住账号能力，手机端继续留明文密码会破坏安全边界。
- 远程账号登录 / 切换必须走 HTTPS 公网入口；原因是公钥信封只解决应用层敏感字段保护，不应在纯 HTTP 下开放会话控制接口。
- 第 1 步本轮继续补完“入口唯一化”的最后一刀：Manifest 不再声明全局明文流量，配置中心固定只返回 `https://tradeapp.ltd`，设置页网关项也改成只读展示；原因是只拦截新输入还不够，只要运行时还允许改写 HTTPS 域名，主链就仍然不是唯一入口。
- Task 6 当前只完成了自动化验收与文档收口，真机人工联调仍需按验收清单单独执行；原因是“已部署服务器 + App 实际 HTTPS 入口 + 真机页面收口”不属于仓库内可完全替代的验证范围。
- 远程账号会话当前只收口“单用户、多账号、单激活账号”的最小正确模型；原因是这一阶段目标是先闭合安全登录、切换、退出和页面强一致收口，多用户隔离与后台公钥轮换留到后续阶段。
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
