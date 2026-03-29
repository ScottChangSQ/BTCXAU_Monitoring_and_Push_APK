# ARCHITECTURE

## 模块职责
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
  行情图表页入口；负责 K 线请求调度、指标开关与参数设置、图表底部持仓模块展示。
- `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
  K 线绘制控件；负责价格区/副图区绘制与指标计算（MACD、BOLL、STOCHRSI、MA、EMA、SRA、AVL、RSI、KDJ）。
- `app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java`
  行情 REST 数据访问层；新增共享缓存，供监控与图表模块复用请求结果。
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
  账户统计页入口；负责账户快照刷新、统计展示与底部导航。
- `bridge/mt5_gateway/server_v2.py`
  MT5 网关服务；通过 MetaTrader5 Python 接口生成账户快照并输出给 App。

## 调用关系
- 图表页 `MarketChartActivity` -> `BinanceApiClient`：拉取 K 线历史与增量数据。
- 监控服务 `MonitorService` -> `BinanceApiClient`：拉取初始化/兜底收盘 K 线。
- 图表页 `MarketChartActivity` -> `AccountStatsPreloadManager`：复用账户快照用于图上标注与底部持仓模块。
- 账户统计页 `AccountStatsBridgeActivity` -> `Mt5BridgeGatewayClient` -> `server_v2.py`：获取账户、持仓、交易、历史曲线数据。

## 关键设计决定
- 指标参数集中在图表控件内计算，参数由页面长按弹窗修改后回写，避免多层传递导致状态分散。
- 图表与监控共用 `BinanceApiClient` 的共享缓存，减少同窗口内重复 REST 请求。
- 图表底部持仓模块与图上持仓标注共用同一账户快照，保证展示一致性并降低重复获取。
