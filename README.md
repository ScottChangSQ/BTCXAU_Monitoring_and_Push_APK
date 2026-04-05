# BTC/XAU 异常交易监控 Android App

一个基于 Java、XML、ViewBinding、MVVM + Repository + Room 的 Android Studio 工程，用来监控 BTC / XAU 行情、接收异常交易提醒，并展示 MT5 账户、持仓、挂单、交易记录和统计信息。

## 项目功能简介

- 新架构切换中：`BTCUSDT / XAUUSDT` 的行情、K 线、成交量和指标逐步统一改为只认服务端 `v2` 输出的 Binance 真值，APP 主要负责展示和本地快照
- 新架构切换中：账户页后台预加载已优先改走 `v2/account/snapshot + v2/account/history`，成功时直接原子替换本地交易、曲线、持仓和挂单
- 新架构切换中：账户统计页主动刷新也已收口到 `v2` 优先链路，不再由页面自己直接请求旧 `/v1/*` 快照
- 新架构切换中：监控服务已接入 `v2/stream` 作为统一同步信号入口，旧 Binance `kline` WebSocket 暂时只保留为行情 tick 回退
- 新架构切换中：`MonitorService` 收到 `v2 stream` 的 `market/account` 变化后，已经会主动补拉最新市场/账户数据，不再只做被动提示
- 新架构切换中：图表页本地缓存已收口为 `ChartHistoryRepository + 内存窗口`，不再保留旧文件 K 线缓存，也不再把图表序列混写进 `V2SnapshotStore`
- 新架构切换中：K 线链路已继续收口为“闭合历史快照 + 最新 1 条 patch”；闭合历史才会持久化到 Room，未收盘最新 K 线只留在内存与界面，且分钟实时 patch 只允许覆盖 `1d` 及以下周期
- 服务端已新增统一 Web 服务器控制台，可在服务器本机 `localhost` 或公网访问，用来查看总览、组件控制、配置中心、诊断中心、日志与历史，并控制网关 / MT5 / Caddy / Nginx
- 主监控页与图表页的 Binance 行情统一走韩国服务器转发，手机不再直接访问 Binance 官方地址
- MT5 账户支持轻量摘要、轻实时持仓、挂单增量、交易增量和权益曲线追加
- 悬浮窗支持显示连接状态、合并盈亏、分产品盈亏、最新价格、成交量、成交额
- 悬浮窗支持整块长按拖动、最小化、点击还原、透明度调节
- 设置页支持运行时修改 MT5 网关地址，不用重新打包 APK
- 设置页支持 5 套主题即时切换，并同步图表、悬浮窗和桌面图标
- 隐私显示统一改为账户统计页“小眼睛”入口，隐藏时账户统计图表显示 `****` 占位，行情持仓页只打码当前持仓模块并隐藏持仓叠加线
- 图表历史、交易历史、账户摘要、持仓快照统一写入 Room，已拉取过的数据会长期保留
- 账户统计页支持收益统计、月收益表、净值/结余曲线、回撤曲线、日收益率曲线、交易分布图、持仓时间分布图和交易记录筛选排序

## 技术架构

- Android：Java + XML + ViewBinding
- 架构：MVVM + Repository
- 本地存储：Room + SharedPreferences
- 网络：OkHttp + WebSocket
- 图表与账户：自定义 View + `GatewayV2Client` / `GatewayV2StreamClient` + MT5 网关接口 + 服务端净值曲线重放
- 构建：Gradle Kotlin DSL，`compileSdk / targetSdk 34`，`minSdk 24`

## 已完成功能列表

- 服务端已新增 `v2/market/snapshot`、`v2/market/candles`、`v2/account/snapshot`、`v2/account/history`、`v2/sync/delta`、`v2/stream` 的最小可用链路
- APP 已新增 `GatewayV2Client`、`V2SnapshotStore` 和 `v2` 载荷模型，图表页已切到服务端 `candles + latestPatch` 口径
- 图表页左滑历史分页和增量补尾也已切到 `v2/market/candles?startTime/endTime`，不再走旧 `BinanceApiClient` 图表历史接口
- 服务端 `v2/market/candles` 已开始把最后一根未闭合 REST K 线拆成 `latestPatch`，不再把它伪装成闭合历史
- 图表页已删除本地分钟底稿对最终真值的旧覆盖链路，旧缓存版本升级为 `2` 并会在启动时一次性清旧
- 图表页已删除 `KlineCacheStore` 这层废弃文件缓存，`V2SnapshotStore` 也已收口到只保存 `market/account` 快照，避免图表清缓存误删账户恢复数据
- 图表页最新窗口落库前会自动剔除未闭合 patch，Room 里只保留闭合历史快照
- 账户预加载已改成 v2 优先，成功时走 `persistV2Snapshot(...)` 原子替换本地账户数据
- 账户统计页主动刷新已统一改走 `AccountStatsPreloadManager.fetchForUi(...)`，与后台预加载共用同一套 `v2` 优先抓取逻辑
- 监控服务已新增 `GatewayV2StreamClient`，开始消费 `/v2/stream` 的统一同步消息
- 监控服务已新增 `V2StreamRefreshPlanner`，并会在 `v2 stream` 出现 `marketDelta/accountDelta/fullRefresh` 时补拉最新真值
- 监控服务冷启动与实时回退已统一改成读取 `v2/market/candles` 最近序列，不再在服务层写旧图表 1m 底稿
- WebSocket 实时接收 `BTCUSDT` 与 `XAUUSDT` 的 1m K 线更新
- 主监控页与图表页的 Binance REST / WebSocket 已统一切到韩国服务器转发
- OR / AND 异常判断
- 前台服务常驻运行
- 单标通知、同轮合并通知、5 分钟冷却
- 异常交易提醒已升级为更强的系统通知样式
- 悬浮窗已改为统一快照刷新：顶部显示合并盈亏和最小化按钮，第二行显示连接状态，下方按产品显示分产品盈亏、最新价格、成交量、成交额
- 悬浮窗支持整块长按拖动，最小化后也可拖动并点击还原
- SharedPreferences 持久化配置
- 本地文件持久化日志与异常记录
- 主页面与日志页面
- 行情图表页支持 `MA / EMA / SRA / AVL / RSI / KDJ` 指标，其中 `MA / EMA` 默认关闭
- 指标按钮支持长按参数设置
- 图表页在启动进入、切回图表页、切换时间周期时都会主动刷新
- 图表刷新前不再先清空历史，已拉取过的历史 K 线会持续保留
- 图表页已开始把未收盘 `1m` K 线先并入本地分钟底稿，再同步覆盖到 `1d` 及以下周期的最新尾部，1 分钟图不再缺当前这根 K 线，切周期也更少等待网络
- 图表页左下角已新增“历史成交 开 / 关”按钮，可直接控制 K 线上的历史买卖点、平仓点和连线是否显示
- K 线右侧留白改为固定绘图区 `1/7`
- 行情图表页底部“当前持仓”模块与图表持仓标注共用账户快照数据
- 行情持仓页关闭隐私显示时，会保留 K 线走势和异常提示，只隐藏持仓/挂单/止盈止损/成本线，并把当前持仓模块数据改成 `****`
- 行情持仓页 K 线支持横向缩放、纵向缩放和斜向双指整体缩放
- 行情持仓页右上角倒计时会显示最近一次成功拉取数据的延迟 `ms`
- 图表历史、历史交易、账户摘要、持仓快照已统一接入 Room 持久层
- 账户统计页“账户总览”顶部格式改为“账户-账号名称-杠杆”
- 账户统计页已补上概览区持仓盈亏同步刷新
- 账户统计页净值/结余曲线支持最大回撤区间高亮
- 账户统计页顶部隐私入口已改为小眼睛，隐藏时文本数据改成 `****`，图表改为 `****` 占位态
- 账户统计页月收益表改为“左侧年份块 + 右侧三行月份”的分组布局，年份与月份行重新对齐
- MT5 网关已改为按历史成交、当前持仓和产品价格重算净值曲线，净值不再长期和结余重合
- 账户统计页已补上回撤附图、日收益率附图、历史交易分布图、持仓时间分布图
- 账户统计页净值 / 结余曲线、交易分布图、持仓时间分布图已统一收窄左右留白，图表横向显示更宽
- 账户统计页交易统计区已改成左标题右数值的清单式布局
- 账户统计页交易记录区已把产品、方向、排序收敛到同一行，说明文案已删除
- 持仓时间分布图已显示“总数（盈利 / 亏损）”
- 行情持仓页挂单信息增加回填逻辑，并改为“双源合并去重”展示
- 底部 Tab 调整为微信风格
- 行情持仓页底部 Tab 已与其他三个页面完全统一，当前持仓模块背景改为无边框
- 行情监控页 OR / AND 与阈值启用控件已删除多余方框背景
- 设置页已改成“微信式目录首页 -> 二级设置页”
- 设置页支持 MT5 网关地址运行时修改
- 设置二级页已移除重复隐私开关和 MT5 当前地址提示行
- 设置页支持分项清理：历史行情、历史交易、运行时缓存
- 设置页支持 5 套主题方案：金融专业风、复古风、币安风格、TradingView 风格、浅色风格
- 主题切换会同步影响图表、页面卡片、悬浮窗和桌面图标，控件样式也统一改为更方正的视觉
- 行情监控页已删除手动监控按钮，服务默认监控开启
- 登录成功后会在账户统计页中央显示快速淡出的成功提示动画
- 悬浮窗盈亏合计金额已加粗，桌面图标图案整体上移
- 服务器侧已新增统一控制台服务 `admin_panel.py` 与配套静态页面 / 状态 helper / 诊断 helper / 配置 helper，可直接通过浏览器操作启动 / 停止 / 重启网关、MT5 客户端、Caddy、Nginx，并支持查看中文诊断、编辑 `.env`、查看配置变更影响、修改异常规则与清空运行时缓存

## 本地运行方法

1. 用 Android Studio 打开仓库根目录。
2. 确认本机已安装 Android SDK 34。
3. 如果换到其他机器，请更新 `local.properties` 里的 `sdk.dir`。
4. 直接运行 `app` 模块，或执行下面的 Gradle 命令。

## 测试方法和常用命令

```bash
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -x lint
```

MT5 网关 Python 侧常用验证：

```bash
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_summary_response -v
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel -v
.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_admin_panel_bundle_parity -v
.\.venv\Scripts\python.exe -m py_compile bridge/mt5_gateway/server_v2.py
.\.venv\Scripts\python.exe -m py_compile bridge/mt5_gateway/admin_panel.py
```

## 部署方法和命令

- 服务器部署说明见 [deploy/tencent/README.md](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/README.md)
- Windows 服务器精简上传包见 [deploy/tencent/windows_server_bundle](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows_server_bundle)
- Windows 部署根目录现统一为 `C:\mt5_bundle`
- 上传方式改为：整体替换 `deploy/tencent/windows_server_bundle` 对应内容，不再手工挑单个文件补传
- 当前默认公网入口为 `http://43.155.214.62`
- 统一控制台默认入口为 `http://43.155.214.62/admin/`
- 管理面板直连端口入口仍可用：`http://43.155.214.62:8788`
- 统一承接：
  - `MT5 /v1/*`
  - `Admin /admin/*`
  - `Binance REST /binance-rest/*`
  - `Binance WebSocket /binance-ws/*`
- 如果需要让管理面板开机自启，可执行：
  - `.\deploy\tencent\windows\04_register_admin_panel_task.ps1 -RepoRoot "<仓库路径>" -Force`
- 如果服务器地址变化，可直接在 App 设置页修改“MT5 网关地址”
- 本轮网关新增 `SNAPSHOT_RANGE_ALL_DAYS` 配置；若服务器内存偏高，可在 `bridge/mt5_gateway/.env` 里调低 `all` 区间历史回看天数后重启网关
- 如果 MT5 返回的成交时间比北京时间固定慢若干分钟，可在 `bridge/mt5_gateway/.env` 里设置 `MT5_TIME_OFFSET_MINUTES`；例如慢 8 小时就填 `480`，这样交易记录、历史成交点和账户曲线会一起按同一口径修正

## 目录说明

- [app/src/main/java/com/binance/monitor/service/MonitorService.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorService.java)
  前台服务、异常判断、悬浮窗快照生成、通知调度。
- [app/src/main/java/com/binance/monitor/ui/main/MainActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/main/MainActivity.java)
  主监控页 UI 与交互。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java)
  行情图表页入口。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartPersistenceWindowHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartPersistenceWindowHelper.java)
  图表持久化窗口工具，负责在落库前剔除未闭合 latest patch。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartRefreshMetaFormatter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartRefreshMetaFormatter.java)
  行情图右上角倒计时与延迟文案格式化工具。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartScaleGestureResolver.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartScaleGestureResolver.java)
  K 线缩放手势方向判定工具。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java)
  账户统计页入口。
- [app/src/main/java/com/binance/monitor/ui/account/AccountConnectionTransitionHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountConnectionTransitionHelper.java)
  登录成功提示动画触发条件工具。
- [app/src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java)
  账户统计分析工具，负责最大回撤、日收益、交易散点和持仓时间分布计算。
- [app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java)
  历史交易分布图控件。
- [app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java)
  持仓时间分布图控件。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java)
  悬浮窗渲染与交互管理。
- [app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java)
  设置首页目录。
- [app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java)
  设置二级页。
- [app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java)
  主题与控件样式工具，负责页面底色、按钮、复选控件和方正风格资源。
- [app/src/main/java/com/binance/monitor/data/local/db/](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/db)
  Room 数据库层。
- [bridge/mt5_gateway/server_v2.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/server_v2.py)
  MT5 网关服务。
- [bridge/mt5_gateway/admin_panel.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel.py)
  轻量 Web 管理面板服务，负责状态聚合、日志、`.env` 编辑、异常规则代理、缓存清理和本机组件管理。

## 搜索记录（2026-03-29）

- `skills.sh` 检索：`android-design-guidelines`
  结论：Tab、长按交互和设置入口布局可按 Android 一致性原则落地。
- GitHub 检索：`liihuu/KLineChartAndroid`
  结论：K 线常用指标组合包含 `MA/EMA/RSI/KDJ/BOLL/MACD`，本项目图表扩展采用同类思路。
- 官方文档检索：MQL5 Python `history_deals_get`
  结论：账户统计与交易历史继续以 MetaTrader5 Python 接口为主来源。
- 官方文档检索：Android Room
  结论：本地持久层统一采用 Room，更适合历史 K 线、交易历史与账户快照的结构化保存。
- 本地实现检索：`bridge/mt5_gateway/server_v2.py` + `Mt5BridgeGatewayClient`
  结论：在不改变页面模型的前提下，把 MT5 数据拆成摘要、轻实时持仓、挂单、成交、曲线几条链路，是当前最省流量的落地方式。

## 待办事项

- 资源占用优化、流量进一步压缩、CPU 与内存整理
- 更大范围的代码规范化整理与注释补齐
- 评估是否把异常交易判断迁移到服务器端
