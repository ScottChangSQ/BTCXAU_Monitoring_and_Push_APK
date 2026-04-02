# ARCHITECTURE

## 每个文件/模块的职责

- [app/src/main/java/com/binance/monitor/service/MonitorService.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorService.java)
  前台服务入口，负责行情轮询、异常判断、通知调度，以及生成悬浮窗统一快照。
- [app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java)
  运行策略辅助工具，负责把前后台状态转换成心跳、异常同步和悬浮窗刷新的统一节奏。
- [app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java)
  Binance REST 数据访问层，负责通过韩国服务器的 `/binance-rest` 拉取行情。
- [app/src/main/java/com/binance/monitor/data/remote/WebSocketManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/WebSocketManager.java)
  Binance WebSocket 管理器，负责通过韩国服务器的 `/binance-ws` 订阅实时行情。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java)
  图表页入口，负责 K 线请求调度、周期切换、指标开关、局部隐私隐藏和右上角刷新/延迟信息。
- [app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java)
  K 线绘制控件，负责主图、副图、指标、右侧留白、异常点胶囊、成本线和缩放交互。
- [app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java)
  图表视口计算工具，负责 K 线横向边界和右侧留白相关数学逻辑。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartRefreshMetaFormatter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartRefreshMetaFormatter.java)
  倒计时文案工具，负责拼接“剩余秒数/周期秒数 延迟ms”。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartScaleGestureResolver.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartScaleGestureResolver.java)
  缩放方向判定工具，负责把双指手势分成横向、纵向和斜向整体缩放。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java)
  账户统计页入口，负责账户概览、收益统计、净值/结余主图、附图、交易分布、交易记录、隐私小眼睛和登录成功提示动画。
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
  账户预加载管理器，负责后台轻量同步和前台页面进入时的全量补齐。
- [app/src/main/java/com/binance/monitor/ui/account/AccountPreloadPolicyHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPreloadPolicyHelper.java)
  账户预加载节奏辅助工具，负责把前后台状态和全量/轻量模式转换成统一刷新间隔。
- [app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java)
  MT5 网关客户端，负责请求 `/v1/summary`、`/v1/live`、`/v1/pending`、`/v1/trades`、`/v1/curve` 等接口，并把结果合并回统一账户模型。
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
- [bridge/mt5_gateway/server_v2.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/server_v2.py)
  MT5 网关服务，负责 MT5 数据整理和 Binance REST / WebSocket 转发。
- [bridge/mt5_gateway/start_gateway.ps1](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/start_gateway.ps1)
  网关启动脚本，负责加载 `.env` 并使用本地虚拟环境启动服务。

## 模块之间的调用关系

- `MainActivity` / `MarketChartActivity` / `MonitorService` -> `BinanceApiClient` / `WebSocketManager`
  统一从韩国服务器转发入口读取行情。
- `MonitorService` -> `MonitorRepository`
  读取最新 BTC / XAU 行情数据。
- `AppForegroundTracker` -> `MonitorService` / `MonitorRuntimePolicyHelper`
  统一把应用前后台状态传给服务调度层，决定当前应使用哪一档刷新节奏。
- `MonitorService` -> `AccountStorageRepository`
  读取最新 MT5 持仓快照。
- `MonitorService` -> `FloatingPositionAggregator` + `FloatingWindowSnapshot` + `FloatingWindowManager`
  把行情和持仓聚合成统一快照，再一次性刷新悬浮窗。
- `MarketChartActivity` -> `ChartHistoryRepository` -> Room
  先读本地历史，再按需补网络数据。
- `MarketChartActivity` -> `ChartRefreshMetaFormatter`
  把自动刷新倒计时和最近一次成功请求延迟整理成统一文案。
- `KlineChartView` -> `ChartScaleGestureResolver`
  根据双指跨度变化决定走横向、纵向还是整体缩放。
- `AccountStatsPreloadManager` -> `Mt5BridgeGatewayClient` -> `server_v2.py`
  后台轻量同步账户数据，前台页面打开时补完整数据。
- `AppForegroundTracker` -> `AccountPreloadPolicyHelper` -> `AccountStatsPreloadManager`
  统一把应用前后台状态转换成账户预加载节奏，避免旧常量分散在多个入口里。
- `AccountStatsBridgeActivity` -> `Mt5BridgeGatewayClient`
  获取账户概览、持仓、挂单、交易记录、权益曲线并更新界面。
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

## 关键的设计决定和原因

- Binance 行情链路统一走韩国服务器转发，避免手机继续直连 Binance 官方地址。
- MT5 数据链路拆成摘要、轻实时持仓、挂单增量、成交增量、曲线追加，减少日常流量和重复全量请求。
- 网关快照缓存改成“按最近使用裁剪 + EA 新鲜时短时平滑续用”；原因是这样能同时压住 `snapshot/sync` 缓存的内存占用，并减少固定轮询下缓存命中与重建交替造成的延迟抖动。
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
- 异常交易提醒先在 App 端提升为更强系统通知；服务器端异常判断迁移后续再评估，不在本轮硬塞进去。
