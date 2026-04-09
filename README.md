# BTC/XAU 异常交易监控 Android App

一个用于监控 BTC / XAU 行情、展示 MT5 账户状态、接收异常提醒的 Android App + Python 网关项目。当前本次 1-6 步主链收口已完成，App 与服务端已经统一到单入口、单真值、纯消费展示的结构。

## 项目功能简介

- App 主入口已经固定为 `https://tradeapp.ltd`，设置页只读展示，不再允许本地改入口
- App 的 Binance REST / WebSocket 默认入口也已经固定到 `https://tradeapp.ltd/binance-rest/*` 与 `wss://tradeapp.ltd/binance-ws/*`
- 行情、账户、会话三条主链统一走服务端 `/v2/*`，客户端负责请求、展示和本地缓存
- 服务端账户真值已经收口到 `MT5 Python Pull`，旧 EA 上报只保留原始存档，不再参与主链选源
- 监控链已经收口为纯消费层，`v2/stream` 默认 1s 推送一次，消息直接携带市场/账户快照增量，客户端不再按每条消息回源市场 REST
- 行情监控页里的开盘价、收盘价、成交量、成交额、价格变化、涨跌幅，已经收口为“上一根已闭合 1 分钟 K 线”口径；实时价格和图表尾部仍继续消费实时 patch
- 账户展示链已经收口为纯消费层，只消费 canonical 字段，不再回读旧快照、不再本地猜测或补锚
- APP 账户运行态现在直接消费 `v2/stream` 下发的 `accountRuntime`，不再定时补拉 `/v2/account/snapshot`
- 账户页主动刷新和交易提交后的强一致确认，仍统一通过 `AccountStatsPreloadManager` 走一次显式 canonical `/v2/account/snapshot + /v2/account/history` 刷新，不再各自散落直连接口
- 账户统计页“账户概览”里的 `当日盈亏`、`当日收益率` 已收口为 APP 本地口径：用当天已平仓成交和今日起点结余直接计算，不再直接透传服务端日指标
- APP 从后台回前台、或点悬浮窗切回主界面时，主链不再按“重新打开 APP”处理；只有 stream 真失活时才重建连接，正常情况下只切换刷新节奏
- 图表页从其他 tab 返回前台时，已收口为“恢复消费节奏”而不是“重新启动页面链路”：普通返回不再在 `onResume()` 里重置 V2 transport，也不再由图表页切账户全量快照节奏
- 图表页 `1w / 1M / 1y` 长周期恢复前台时，若当前窗口仍新鲜则不再立刻重拉；后续自动刷新也不再走快速 fallback，而是对齐到下一次分钟边界
- 账户统计页命中同签名预加载缓存时，不再重复整页 `applySnapshot()`，切页返回时不会再把总览、曲线、持仓和成交区整套重画一遍
- 设置首页和设置子页切换到底部其他 tab 时，不再通过 `finish()` 销毁自己，tab 切换语义已统一为前台切换
- 服务端 `v2_account` 账户 helper 也已经收口为严格 canonical 字段消费，只接受显式 `productId / marketSymbol / tradeSymbol / productName`
- 会话链已经收口为服务端单真值，当前激活账号只认 `status.activeAccount`
- 历史、曲线、比例补算和启发式归并已经从主链移除，页面只基于服务端给出的标准数据做展示
- 服务端轻快照主链不再展开全量历史，但会返回 `accountMeta.historyRevision`（由成交历史 canonical digest 生成），供 App 只在历史修订号变化时补拉全量历史；缓存 miss 也不再并发放大
- 服务端轻快照增加会话代次保护（`session_snapshot_epoch`），会话切换期间构建出的旧快照不会写回新会话缓存
- `/v2/account/snapshot` 现在直接走 MT5 轻快照，`/v2/account/history` 的曲线和交易列表会复用同一份 MT5 成交历史
- `/v2/account/history` 现在也先看当前激活远程会话；未登录或已登出时直接返回空历史，不再绕过会话边界去触发 MT5
- `v2/stream` 在未激活远程会话时会直接返回市场数据和空账户摘要，不再主动拉起 MT5
- `/v2/account/snapshot` 响应现在固定包含 `account` 对象；APP 端缺少该对象会直接按协议错误处理，不再本地拼装兜底
- `v2 account delta` 已移除 `refreshHint`，账户刷新只按 canonical 字段（如 `historyRevision`、`positions/orders` 变化）驱动
- APP 连接状态文案已收口为“仅看 v2 stream 连接态”，断流后不再因旧消息时间被误判为已连接
- 服务端自带统一管理面板，可查看状态、日志、配置，并控制网关、MT5、Caddy、Nginx

## 技术架构

- Android：Java + XML + ViewBinding + MVVM + Repository + Room
- 服务端：Python MT5 Gateway + `server_v2.py` + `admin_panel.py`
- 网络主链：App 固定访问 `https://tradeapp.ltd`，服务端统一承接 `/v2/*`、`/admin/*`、`/binance-rest/*`、`/binance-ws/*`
- 账户主链：服务端只认 `MT5 Python Pull` 的快照、历史、曲线；App 只消费 `orders / trades / curvePoints / accountMeta.historyRevision` 等 canonical 字段
- 会话主链：服务端负责远程账号登录、切换、退出和激活账号确认；App 只消费 `activeAccount / active` 这组 canonical 字段
- 产品命名主链：市场侧统一为 `BTCUSDT / XAUUSDT`，交易侧统一为 `BTCUSD / XAUUSD`，跨层映射集中在 `ProductSymbolMapper`
- 安全链路：远程登录使用 `rsa-oaep+aes-gcm`，服务器端账号档案使用 Windows DPAPI，客户端本地会话摘要使用 Android Keystore

## 远程账号会话

- App 已支持获取公钥、加密登录新账号、切换已保存账号、退出当前账号
- 服务端任何时刻只允许一个当前激活账号，不再保留前端本地拼装的账号身份
- 手机端不再保存明文 MT5 密码，只保存 Keystore 加密后的会话摘要和已保存账号列表缓存
- 勾选“记住此账号”后，服务器保存的是 DPAPI 加密账号档案，不是明文密码
- 远程账号会话必须走 HTTPS 暴露，纯 HTTP 入口不应直接开放 `/v2/session/*`

## 1-6 步最终收口结论

- 第 1 步入口唯一化：已完成。构建期、运行期、设置页都已固定到 `https://tradeapp.ltd`，不再保留第二套正式入口
- 第 2 步服务端账户真值唯一化：已完成。账户主链只认 `MT5 Python Pull`，旧 EA 快照不再干扰缓存、增量状态和主接口输出
- 第 3 步监控链降成纯消费层：已完成。`v2/stream` 直接下发已发布的 `changes.market / changes.accountRuntime / changes.abnormal` 或 `heartbeat`，监控页和服务状态只消费它的结果，断流会立即下线
- 第 4 步账户展示链降成纯消费层：已完成。账户页、预加载、图表标注、失败缓存都只消费服务端标准字段，不再重用旧快照或旧别名字段
- 第 5 步收掉会话拼装：已完成。客户端只认 `status.ok=true` 且 `status.activeAccount` 完整身份，服务端只认网关确认后的完整账号身份
- 第 6 步清掉历史、曲线、比例补算和启发式归并：已完成。主链不再做本地重建、旧曲线回灌、symbol+side 归并、时间价格补位和补锚画线

## 已完成功能列表

- 行情监控、异常提醒、悬浮窗、图表、账户总览、交易历史、收益曲线已经跑在统一的 `/v2/*` 主链上
- `GatewayV2Client`、`GatewayV2StreamClient`、`GatewayV2SessionClient` 已经成为 App 主链入口
- 账户页主动刷新与后台预加载已经统一到同一套 `v2` 数据链
- 账户预加载现在拆成两条明确链路：运行态只应用 stream 已发布快照，历史只在 `historyRevision` 前进或本地还没有历史时补拉 `v2/account/history`
- 账户统计页高频刷新现在只更新账户概览、当前持仓和挂单；历史成交、曲线和历史统计只在 `historyRevision` 变化时补拉全量
- 账户统计页“账户概览”的 `当日盈亏 / 当日收益率` 现在固定按 APP 本地今日口径计算，只覆盖这两个字段，其余 overview 指标仍消费服务端 canonical metrics
- 账户运行态连接状态现在只认完整账号身份，`source=remote_logged_out` 不再被误判为“已连接”
- 账户历史补拉现在会在单次补拉进行中暂存后到的最新 `historyRevision`，当前一轮结束后继续追到最新版本，不再丢 revision
- 账户统计页刷新节流现在按“快照签名是否变化”自动调节：未变化时逐步降频，变化时立即回到最小刷新间隔
- 图表页历史成交标注现在只认显式生命周期字段，不再本地猜品种和补锚点
- 图表长周期 K 线现在固定只走 Binance REST 原生周期接口，不再按返回条数切换到日线聚合或历史回退链
- 行情监控主界面的概览指标现在固定消费服务端 `latestClosedCandle`，不再误用当前分钟未闭合 patch
- 本地仓储已经停止把旧历史、旧曲线、轻量快照拼回当前真值；轻量快照刷新时只保留上一轮全量历史的展示结果，不再把历史区清空
- 服务端 `v2/account/history` 已经停止旧别名兼容、价格回填和启发式成交归并
- 服务端 `v2/session/*` 已完成登录、切换、退出、状态查询、公钥获取的最小闭环
- 会话字段名已经统一只认 `activeAccount / active`，不再消费 `account / isActive` 旧别名
- BTC/XAU 产品映射已经统一收口，悬浮窗、图表、异常链、统计分析都不再靠字符串猜品种
- 服务端与 APP 现已统一移除 `XBT / GOLD` 历史别名容错，产品命名口径固定为市场侧 `BTCUSDT / XAUUSDT`、交易侧 `BTCUSD / XAUUSD`
- EA Push 在取不到 `contractSize` 时会直接报错并中止本次快照发送，不再伪造 `1.0`
- 设置页网关项已经收口为只读正式入口展示，不再允许手工保存地址
- 管理面板已经支持查看会话摘要、运行状态、日志、配置和常用控制操作
- 自动化测试已经覆盖主入口、账户链、监控链、会话链、服务端账户模型和契约层
- 前后台切换和悬浮窗切回主界面现在只改变刷新节奏，不再无条件重建 stream 或立刻触发账户页全量预加载
- 行情持仓页普通 tab 返回现在只恢复页面消费节奏；长周期窗口改为按上游分钟源节奏刷新，不再制造“像重新打开页面”一样的重复拉取体感
- 底部 tab 切换语义现已统一，设置页切走时不再先销毁再重建

## 本地运行方法

1. 用 Android Studio 打开仓库根目录。
2. 确认本机已安装 Android SDK 34。
3. 如更换机器，先更新 `local.properties` 中的 `sdk.dir`。
4. 直接运行 `app` 模块，或执行下方 Gradle 命令。

## 测试方法和常用命令

Android 常用命令：

```bash
.\gradlew.bat :app:compileDebugJavaWithJavac
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug -x lint
```

App 最终总验收命令：

```bash
.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.build.AppBuildConfigSourceTest" --tests "com.binance.monitor.data.local.ConfigManagerSourceTest" --tests "com.binance.monitor.data.local.db.AppDatabaseProviderSourceTest" --tests "com.binance.monitor.data.remote.AbnormalGatewayClientSourceTest" --tests "com.binance.monitor.data.remote.BinanceApiClientChartSourceTest" --tests "com.binance.monitor.data.remote.v2.GatewayV2ClientTest" --tests "com.binance.monitor.data.remote.v2.GatewayV2SessionClientTest" --tests "com.binance.monitor.data.remote.v2.GatewayV2StreamClientSourceTest" --tests "com.binance.monitor.util.GatewayUrlResolverTest" --tests "com.binance.monitor.ui.settings.SettingsSectionActivitySourceTest" --tests "com.binance.monitor.service.ConnectionStatusResolverTest" --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.service.MonitorServiceFallbackCleanupSourceTest" --tests "com.binance.monitor.service.AbnormalSyncRuntimeHelperTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityTradeHistorySourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivitySessionSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityV2RefreshSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerTest" --tests "com.binance.monitor.ui.account.AccountRemoteSessionCoordinatorTest" --tests "com.binance.monitor.ui.chart.HistoricalTradeAnnotationBuilderTest" --tests "com.binance.monitor.ui.chart.KlineChartViewSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartRefreshSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartV2SourceTest" --tests "com.binance.monitor.ui.main.MainActivityConnectionDialogSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.data.local.db.repository.AccountStorageRepositoryTest"
```

Python 服务端常用命令：

```bash
python -m unittest bridge.mt5_gateway.tests.test_summary_response -v
python -m unittest bridge.mt5_gateway.tests.test_v2_account_pipeline bridge.mt5_gateway.tests.test_v2_contracts -v
python -m unittest bridge.mt5_gateway.tests.test_v2_session_crypto bridge.mt5_gateway.tests.test_v2_session_contracts bridge.mt5_gateway.tests.test_v2_session_manager bridge.mt5_gateway.tests.test_v2_session_models -v
python -m py_compile bridge/mt5_gateway/server_v2.py
python -m py_compile bridge/mt5_gateway/admin_panel.py
python -m py_compile scripts/build_windows_server_bundle.py
```

服务端最终总验收命令：

```bash
python -m unittest bridge.mt5_gateway.tests.test_summary_response.SummaryResponseTests.test_normalize_snapshot_should_not_rebuild_raw_ea_deals_into_trade_lifecycle bridge.mt5_gateway.tests.test_summary_response.SummaryResponseTests.test_normalize_snapshot_should_keep_sparse_ea_curve_points_from_payload bridge.mt5_gateway.tests.test_summary_response.SummaryResponseTests.test_normalize_snapshot_should_not_rebuild_ea_curve_points_when_source_curve_has_multiple_points bridge.mt5_gateway.tests.test_summary_response.SummaryResponseTests.test_ingest_ea_snapshot_should_not_clear_snapshot_sync_cache_when_payload_changes bridge.mt5_gateway.tests.test_abnormal_gateway bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_v2_session_crypto bridge.mt5_gateway.tests.test_v2_session_contracts bridge.mt5_gateway.tests.test_v2_session_manager bridge.mt5_gateway.tests.test_v2_session_models bridge.mt5_gateway.tests.test_v2_account_pipeline bridge.mt5_gateway.tests.test_v2_market_pipeline bridge.mt5_gateway.tests.test_v2_contracts -v
```

## 部署方法和命令

- 服务器部署说明见 [deploy/tencent/README.md](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/README.md)
- Windows 服务器上传目录使用 [dist/windows_server_bundle](/E:/Github/BTCXAU_Monitoring_and_Push_APK/dist/windows_server_bundle)
- 日常只维护两处服务器源码：
  [bridge/mt5_gateway](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway)
  [deploy/tencent/windows](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows)
- 准备上传前，在仓库根目录执行 `python scripts/build_windows_server_bundle.py`
- Windows 部署根目录统一为 `C:\mt5_bundle\windows_server_bundle`
- 服务器一键停旧服务并重部署命令：`C:\mt5_bundle\windows_server_bundle\deploy_bundle.cmd`
- 命令行等价入口：`powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\mt5_bundle\windows_server_bundle\deploy_bundle.ps1 -Mode Gui -BundleRoot "C:\mt5_bundle\windows_server_bundle"`
- 这套一键部署会先停掉旧计划任务、旧网关、旧管理面板、旧 Caddy/Nginx，并强制释放 `80 / 443 / 2019 / 8787 / 8788` 端口，再重新注册任务、启动后台服务并做健康检查
- 部署包里的 `.ps1` / `.cmd` 会在构建时统一转换成 Windows 兼容编码和换行；如果直接对比源码与部署包的原始文件哈希，PowerShell 脚本可能不同，但归一化后的逻辑内容应保持一致
- 部署包根目录会生成 `bundle_manifest.json`，部署脚本会用它校验运行中的 `8787 / 80 / 443` 返回的 `bundleFingerprint` 是否与当前 bundle 一致
- 网关启动脚本、管理面板启动脚本、首次引导脚本现在统一在脚本内部用 .NET `SHA256` 计算 `requirements.txt` 指纹，不再依赖 `Get-FileHash`，避免部分 Windows PowerShell 环境里网关根本起不来
- 部署脚本会把 443 验收拆成 `loopback SNI` 和 `public HTTPS` 两段，并额外校验 `wss://tradeapp.ltd/v2/stream` 首条消息，方便现场区分是本机 Caddy/网关链卡住、公网入口链卡住，还是 websocket 主链没真正起来
- 服务器端只会出现一个状态窗口，用来显示每一步是否成功、当前状态和日志；这个窗口可以直接关闭，关闭后不会影响已经启动的后台服务
- 统一控制台默认入口为 `http://43.155.214.62/admin/`
- 管理面板直连端口入口为 `http://43.155.214.62:8788`
- App 正式入口固定为 `https://tradeapp.ltd`，如果正式域名变化，需要修改构建配置并重新发版
- 远程账号会话必须通过 HTTPS 暴露；纯 HTTP 入口只适合健康检查、管理面板和只读接口
- 启用 HTTPS 时，优先使用 [deploy/tencent/windows/Caddyfile.example](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows/Caddyfile.example) 的域名站点配置，并放行 443
- `caddy.exe` 可放在 `C:\mt5_bundle\windows_server_bundle\windows\caddy.exe`、`C:\mt5_bundle\windows_server_bundle\caddy.exe` 或 `C:\mt5_bundle\caddy.exe`
- 管理面板如需开机自启，可在 `C:\mt5_bundle\windows_server_bundle` 下执行 `.\windows\04_register_admin_panel_task.ps1 -BundleRoot "C:\mt5_bundle\windows_server_bundle" -Force`

## 目录说明

- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java)
  负责读取 `/v2/account/*`、`/v2/market/*` 的标准数据与历史窗口。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java)
  负责读取 `/v2/stream` 已发布事件与 `heartbeat`。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java)
  负责读取 `/v2/session/*` 会话接口。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java)
  账户页入口，负责消费账户运行态、历史和会话状态。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java)
  账户页预加载管理器，负责应用运行态并按 `historyRevision` 管理历史缓存；内存缓存为空时会回退到本地已持久化 revision 判断是否需要全量补拉。
- [app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java)
  账户页远程会话协调器，负责登录、切换、退出和切换后收口。
- [app/src/main/java/com/binance/monitor/service/MonitorService.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorService.java)
  前台监控服务，负责同步、提醒和悬浮窗刷新；账户历史补拉进行中会暂存后到的最新 revision，避免丢历史版本。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java)
  行情图表页入口，负责消费 K 线和历史成交标注。
- [app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java)
  本地账户仓储，负责保存快照、历史和曲线，但不再自行拼装真值。
- [bridge/mt5_gateway/server_v2.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/server_v2.py)
  服务端主入口，负责行情、账户、同步流和会话接口。
- [bridge/mt5_gateway/v2_account.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_account.py)
  服务端账户模型与标准字段映射；当前只接受显式 canonical 字段，并且保留 `0 / 0.0` 这类合法数值真值，不再把零值误判为缺失。
- [bridge/mt5_gateway/v2_session_crypto.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_crypto.py)
  会话加密工具，负责公钥、登录信封、时间戳和 nonce 校验。
- [bridge/mt5_gateway/v2_session_manager.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_manager.py)
  会话管理器，负责登录、切换、退出、回滚和激活态确认。
- [bridge/mt5_gateway/admin_panel.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel.py)
  管理面板入口，负责状态、日志、配置和组件控制。

## 搜索记录

- `2026-03-29`：`skills.sh` 检索 `android-design-guidelines`
  结论：Tab、长按交互和设置入口布局可按 Android 一致性原则落地。
- `2026-03-29`：GitHub 检索 `liihuu/KLineChartAndroid`
  结论：K 线常用指标组合可参考 `MA / EMA / RSI / KDJ` 一类成熟做法。
- `2026-03-29`：官方文档检索 MQL5 Python `history_deals_get`
  结论：账户统计和交易历史继续以 MetaTrader5 Python 接口为主来源。
- `2026-03-29`：官方文档检索 Android Room
  结论：本地持久层适合统一承接历史 K 线、交易历史和账户快照。
- `2026-04-07`：本轮为最终收口与文档同步，未新增外部搜索。
- `2026-04-09`：本轮继续做最终收口与文档同步，未新增外部搜索。

## 待办事项

- 真机 + 已部署 HTTPS 服务器的人工作业联调还需要做一次最终现场确认
- 部署现场需要再次确认域名、证书、443 放行和 `/v2/session/*` 的 HTTPS 暴露状态

## 阶段验收说明

- 当前 1-6 步代码收口已经完成，自动化验收已经通过
- App 最终总验收已通过：执行上面的 App 总验收命令，结果为 `BUILD SUCCESSFUL`
- 服务端最终总验收已通过：执行上面的服务端总验收命令，结果为 `Ran 135 tests ... OK`
- 2026-04-08 新增验证：启动脚本去噪回归、Windows bundle 回归、App 入口固定回归均已通过
- 2026-04-08 新增验证：轻快照去全历史扫描、轻快照 single-flight、bundle 指纹校验、websocket 验收链均已通过
- 2026-04-08 新增验证：`logged_out` 状态下 `v2/stream` 不再触发 MT5，同步链回归通过
- 2026-04-08 新增验证：真实 `windows/run_gateway.ps1` 启动链已通过，`/health` 可返回最新 `bundleFingerprint`
- 2026-04-08 最终复核新增验证：账户页刷新节流 BUG（`finalUnchanged` 写死）已修复为“快照签名 + historyRevision”判定，`.\gradlew.bat :app:testDebugUnitTest`、`python bridge/mt5_gateway/tests/test_v2_contracts.py`、`python bridge/mt5_gateway/tests/test_v2_sync_pipeline.py`、`python bridge/mt5_gateway/tests/test_summary_response.py` 均通过
- 2026-04-08 最终复核新增验证：图表长周期启发式聚合回退已清理，周/月线图表固定走 Binance REST 原生周期接口，`.\gradlew.bat :app:testDebugUnitTest` 已通过
- 2026-04-09 最终收口新增验证：`v2_account` 严格 canonical 字段、`v2_market` 去除 `XBT/GOLD`、悬浮窗聚合只认 `code`、EA `contractSize` 严格失败、`v2_account` 零值真值保护，以及 `/v2/market/snapshot` 在 `logged_out` 时不再读取 MT5 轻快照，均已通过当前工作区回归验证；`python -m unittest bridge.mt5_gateway.tests.test_summary_response bridge.mt5_gateway.tests.test_v2_sync_pipeline bridge.mt5_gateway.tests.test_v2_contracts bridge.mt5_gateway.tests.test_v2_account_pipeline bridge.mt5_gateway.tests.test_v2_session_contracts bridge.mt5_gateway.tests.test_v2_market_pipeline -v` 结果为 `Ran 122 tests ... OK`
- 2026-04-09 最终收口新增验证：`.\gradlew.bat :app:testDebugUnitTest --tests com.binance.monitor.data.remote.v2.GatewayV2ClientTest --tests com.binance.monitor.data.remote.v2.GatewayV2ClientSourceTest --tests com.binance.monitor.service.ConnectionStatusResolverTest --tests com.binance.monitor.service.MonitorServiceSourceTest --tests com.binance.monitor.service.MonitorServiceV2SourceTest --tests com.binance.monitor.service.MonitorServiceFallbackCleanupSourceTest --tests com.binance.monitor.data.remote.FallbackKlineSocketManagerSourceTest --tests com.binance.monitor.ui.floating.FloatingPositionAggregatorTest --tests com.binance.monitor.util.ProductSymbolMapperTest --tests com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest --tests com.binance.monitor.ui.chart.MarketChartV2SourceTest --tests com.binance.monitor.ui.chart.HistoricalTradeViewportHelperTest --tests com.binance.monitor.ui.chart.KlineChartViewSourceTest` 已通过
- 2026-04-09 最终收口新增验证：`.\gradlew.bat :app:compileDebugJavaWithJavac --rerun-tasks` 已通过
- 2026-04-09 最终 BUG review 收口新增验证：账户历史 bootstrap 不再因内存缓存为空而重复全量补拉；`remote_logged_out` 运行态不再误判为已连接；history 补拉并发期间会续跑最新 revision。`README` 中的 App 最终总验收命令已通过，结果为 `BUILD SUCCESSFUL`；服务端最终总验收命令已通过，结果为 `Ran 135 tests ... OK`
- 2026-04-09 最终收口补充验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.data.repository.MonitorRepositoryDisplaySnapshotSourceTest" --tests "com.binance.monitor.ui.main.MainViewModelDisplaySnapshotSourceTest" --tests "com.binance.monitor.ui.main.MainActivityOverviewClosedCandleSourceTest" --tests "com.binance.monitor.service.MonitorServiceMarketOverviewSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartRealtimeSourceTest"` 已通过，覆盖“概览指标只认上一根闭合 1m K 线、实时尾部继续走 patch”这一组约束
- 2026-04-09 最终收口补充验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountOverviewDailyMetricsCalculatorTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeOverviewSourceTest" --tests "com.binance.monitor.ui.account.AccountPeriodReturnHelperTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsDailyReturnsSourceTest"` 已通过，覆盖“账户概览的当日盈亏 / 当日收益率改为 APP 本地今日口径”这一组约束
- 2026-04-09 最终收口补充验证：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.service.MonitorServiceSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest"` 已通过，覆盖“前台恢复健康时只切节奏、失活时才重建 stream”这一组约束
- 2026-04-09 最终收口补充验证：修复 `fetchForUi()` 被误改成“只读内存缓存”导致账户页主动刷新与交易后强刷新失效的问题；`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityV2RefreshSourceTest"` 已通过
- 2026-04-09 最终 BUG review 补充验证：`/v2/account/history` 已收口到远程会话真值边界；APP 账户缓存不再把断开态硬写成已连接，也不再用历史订单覆盖当前挂单。`.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerSourceTest" --tests "com.binance.monitor.ui.account.AccountStatsPreloadManagerTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityV2RefreshSourceTest"` 通过；`python -m unittest bridge.mt5_gateway.tests.test_v2_contracts.V2ContractTests.test_v2_account_history_should_use_complete_trade_history_builder bridge.mt5_gateway.tests.test_v2_contracts.V2ContractTests.test_v2_account_history_should_use_logged_out_snapshot_when_no_active_session bridge.mt5_gateway.tests.test_v2_contracts.V2ContractTests.test_v2_account_snapshot_should_use_logged_out_snapshot_when_no_active_session bridge.mt5_gateway.tests.test_v2_contracts.V2ContractTests.test_v2_market_snapshot_should_use_logged_out_snapshot_when_no_active_session bridge.mt5_gateway.tests.test_v2_sync_pipeline.V2SyncPipelineTests.test_runtime_snapshot_should_not_touch_mt5_when_session_logged_out -v` 通过；服务端最终总验收结果更新为 `Ran 136 tests ... OK`
- 2026-04-09 最终收口新增验证：`.\gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartRefreshHelperAdditionalTest" --tests "com.binance.monitor.ui.chart.MarketChartLifecycleSourceTest" --tests "com.binance.monitor.ui.chart.MarketChartV2SourceTest" --tests "com.binance.monitor.ui.account.AccountStatsBridgeActivityV2RefreshSourceTest" --tests "com.binance.monitor.ui.settings.SettingsTabNavigationSourceTest"` 已通过，覆盖“图表页普通 tab 返回不再重置 transport、长周期恢复前台按分钟边界刷新、账户页同签名缓存不重复整页重画、设置页 tab 切换不再 finish”这一组约束
- 2026-04-09 最终收口新增验证：`.\gradlew.bat :app:assembleDebug` 已重新通过，最新 APK 已输出到 `app/build/outputs/apk/debug/app-debug.apk`
- 当前唯一未完成的是“真机 + 已部署 HTTPS 服务器”的人工联调记录
