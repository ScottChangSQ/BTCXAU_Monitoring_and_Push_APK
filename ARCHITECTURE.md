# ARCHITECTURE

## 每个文件/模块的职责

- [app/src/main/java/com/binance/monitor/service/MonitorService.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorService.java)
  前台服务入口，负责行情轮询、异常判断、通知调度，以及生成悬浮窗统一快照。
- [app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java)
  Binance REST 数据访问层，负责通过韩国服务器的 `/binance-rest` 拉取行情。
- [app/src/main/java/com/binance/monitor/data/remote/WebSocketManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/WebSocketManager.java)
  Binance WebSocket 管理器，负责通过韩国服务器的 `/binance-ws` 订阅实时行情。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java)
  图表页入口，负责 K 线请求调度、周期切换、指标开关和图表刷新。
- [app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java)
  K 线绘制控件，负责主图、副图、指标和右侧留白绘制。
- [app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java)
  图表视口计算工具，负责 K 线横向边界和右侧留白相关数学逻辑。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java)
  账户统计页入口，负责账户概览、收益统计、月收益表、交易记录和账户标题展示。
- [app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java)
  净值/结余曲线控件，负责绘制账户曲线并标记最大回撤区间。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java)
  账户预加载管理器，负责后台轻量同步和前台页面进入时的全量补齐。
- [app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java)
  MT5 网关客户端，负责请求 `/v1/summary`、`/v1/live`、`/v1/pending`、`/v1/trades`、`/v1/curve` 等接口，并把结果合并回统一账户模型。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java)
  悬浮窗管理器，负责渲染顶部合并盈亏、连接状态、分产品卡片，并处理长按拖动、最小化、点击还原。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowSnapshot.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowSnapshot.java)
  悬浮窗统一快照模型，保证连接状态、总盈亏和产品卡片在同一次渲染里一起更新。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingSymbolCardData.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingSymbolCardData.java)
  悬浮窗产品卡片数据模型，承载产品名、盈亏、最新价、成交量、成交额。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java)
  悬浮窗持仓聚合器，负责把 MT5 持仓按产品汇总成卡片级别数据。
- [app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java)
  设置首页目录，只负责显示设置分类入口。
- [app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java)
  设置二级页，负责显示悬浮窗、网关地址、主题、Tab、缓存管理等具体设置。
- [app/src/main/java/com/binance/monitor/data/local/ConfigManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/ConfigManager.java)
  本地配置中心，负责保存主题、悬浮窗、Tab、网关地址和其他持久化开关。
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
- `MonitorService` -> `AccountStorageRepository`
  读取最新 MT5 持仓快照。
- `MonitorService` -> `FloatingPositionAggregator` + `FloatingWindowSnapshot` + `FloatingWindowManager`
  把行情和持仓聚合成统一快照，再一次性刷新悬浮窗。
- `MarketChartActivity` -> `ChartHistoryRepository` -> Room
  先读本地历史，再按需补网络数据。
- `AccountStatsPreloadManager` -> `Mt5BridgeGatewayClient` -> `server_v2.py`
  后台轻量同步账户数据，前台页面打开时补完整数据。
- `AccountStatsBridgeActivity` -> `Mt5BridgeGatewayClient`
  获取账户概览、持仓、挂单、交易记录、权益曲线并更新界面。
- `AccountStatsBridgeActivity` -> `EquityCurveView`
  把筛选后的曲线数据交给曲线控件，并同步最大回撤高亮。
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
- 悬浮窗改为“统一快照 + 产品卡片”模型，解决不同字段更新时间不一致的问题。
- 悬浮窗拖动增加长按触发、位移阈值和帧节流，减少拖动卡顿和误触。
- 图表历史、历史交易、账户摘要、持仓快照统一切到 Room，满足“拉过就长期保留”的要求。
- 图表刷新前不再清空已显示 K 线，避免刷新瞬间白屏。
- `MA / EMA` 默认关闭，降低初始图表干扰。
- 设置页拆成“目录首页 + 二级详情页”，提升设置层级清晰度。
- 月收益表改为“左侧年份整块 + 右侧双行月份 + 横向滚动”，解决年份不对齐和百分比显示不全的问题。
- 异常交易提醒先在 App 端提升为更强系统通知；服务器端异常判断迁移后续再评估，不在本轮硬塞进去。
