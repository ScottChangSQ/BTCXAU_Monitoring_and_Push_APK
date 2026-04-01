# BTC/XAU 异常交易监控 Android App

一个基于 Java、XML、ViewBinding、MVVM + Repository + Room 的 Android Studio 工程，用来监控 BTC / XAU 行情、接收异常交易提醒，并展示 MT5 账户、持仓、挂单、交易记录和统计信息。

## 项目功能简介

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
- 图表与账户：自定义 View + MT5 网关接口 + 服务端净值曲线重放
- 构建：Gradle Kotlin DSL，`compileSdk / targetSdk 34`，`minSdk 24`

## 已完成功能列表

- Binance Futures REST 初始化最近已收盘 1m K 线
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
.\.venv\Scripts\python.exe -m py_compile bridge/mt5_gateway/server_v2.py
```

## 部署方法和命令

- 服务器部署说明见 [deploy/tencent/README.md](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/README.md)
- Windows 服务器精简上传包见 [deploy/tencent/windows_server_bundle](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows_server_bundle)
- 当前默认公网入口为 `http://43.155.214.62:8787`
- 统一承接：
  - `MT5 /v1/*`
  - `Binance REST /binance-rest/*`
  - `Binance WebSocket /binance-ws/*`
- 如果服务器地址变化，可直接在 App 设置页修改“MT5 网关地址”

## 目录说明

- [app/src/main/java/com/binance/monitor/service/MonitorService.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorService.java)
  前台服务、异常判断、悬浮窗快照生成、通知调度。
- [app/src/main/java/com/binance/monitor/ui/main/MainActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/main/MainActivity.java)
  主监控页 UI 与交互。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java)
  行情图表页入口。
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
