# BTC/XAU 监控与推送项目

一个由 Android 客户端和 Windows 上运行的 Python 网关组成的项目，用来做 BTC / XAU 行情监控、MT5 账户展示、异常提醒和远程账号会话管理。

## 项目功能简介

- Android App 提供 3 个主页面：`交易`、`账户`、`分析`
- 行情展示统一走服务端 `/v2/*`，客户端不再本地拼多套真值
- MT5 账户数据、历史、曲线由服务端统一整理后下发，客户端只负责展示和本地缓存
- 支持远程账号登录、切换、退出，以及失败时的服务端诊断信息展示
- 支持图表页和账户页的交易操作，包括单笔操作和批量操作
- 支持异常提醒、前台服务、悬浮窗和全局状态展示

## 技术架构

- Android 端：Java、XML、ViewBinding、Repository、Room
- 服务端：Python、`bridge/mt5_gateway/server_v2.py`
- UI 结构：`MainHostActivity + 交易 / 账户 / 分析 3 Tab + Settings 独立入口`
- 市场主链：`Binance trade WS -> 服务端本地 1m 聚合 -> /v2/stream marketTick -> App 市场真值中心`
- 账户主链：`MT5 Python Pull -> /v2/account/full 与 /v2/stream -> App 展示`
- 会话主链：服务端统一管理远程账号登录、切换、退出和当前激活账号
- 部署产物：统一由 `scripts/build_windows_server_bundle.py` 生成到 `dist/windows_server_bundle`

## 已完成功能列表

- 已完成单主壳入口收口，`MainActivity` 只保留桥接职责
- 已完成前台服务闭环，服务真实进入 `startForeground(...)`
- 已完成网关地址真值收口，运行时统一从 `ConfigManager` 持久化配置读取
- 已完成页面层依赖装配收口，`MarketChartScreen / AccountStatsScreen / AccountPositionPageController` 不再直接 `new` 关键底层依赖
- 已完成 `MonitorService` 的协调器 seam 起步拆分，已建立 `MonitorStreamCoordinator` 和 `MonitorAlertCoordinator`
- 已完成 MT5 网关探测启发式清理，去掉字符串探测和旧 `snapshot` 回退链
- 已完成交易动作分类收口，去掉 `action.contains(...)` 判断
- 已完成本轮计划边界内的吞异常清理，关键失败点已改为可观测日志
- 已完成旧 `PositionAdapter` 降级为 `@Deprecated`，避免继续误入主链
- 已完成 Windows 服务端部署包构建与本地一致性校验链

## 本地运行方法

1. 安装 Android Studio、JDK、Python 3
2. 打开仓库根目录：`E:\Github\BTCXAU_Monitoring_and_Push_APK`
3. Android 客户端构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

4. 服务端本地测试或打包前，使用仓库内 Python 环境直接运行对应脚本

## 测试方法和常用命令

Android 全量单测：

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Android 构建：

```powershell
.\gradlew.bat :app:assembleDebug
```

服务端全量单测：

```powershell
python -m unittest discover -s bridge/mt5_gateway/tests -p "test_*.py" -v
```

服务端部署包测试：

```powershell
python -m unittest scripts.tests.test_windows_server_bundle scripts.tests.test_check_windows_server_bundle -v
```

生成 Windows 服务端部署包：

```powershell
python scripts/build_windows_server_bundle.py
```

校验部署包与源码一致：

```powershell
python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle --skip-remote
```

## 部署方法和命令

- 服务端上传目录使用 [dist/windows_server_bundle](/E:/Github/BTCXAU_Monitoring_and_Push_APK/dist/windows_server_bundle)
- 部署前先重新生成部署包：

```powershell
python scripts/build_windows_server_bundle.py
```

- 上传前先校验：

```powershell
python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle
```

- 如果只做本地离线核验：

```powershell
python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle --skip-remote
```

- Windows 服务器部署根目录统一为：

```text
C:\mt5_bundle\windows_server_bundle
```

- 服务器一键部署命令：

```powershell
C:\mt5_bundle\windows_server_bundle\deploy_bundle.cmd
```

- PowerShell 等价入口：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\mt5_bundle\windows_server_bundle\deploy_bundle.ps1 -Mode Gui -BundleRoot "C:\mt5_bundle\windows_server_bundle"
```

- 详细 Windows 部署说明见 [deploy/tencent/README.md](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/README.md)

## 阶段验收说明

- 当前三份整改计划已经执行完成：运行安全与真值统一、架构收口、启发式与残留清理
- 最近一次计划复核已确认没有新的未完成代码项
- 最近一次 Android 相关验证已通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.host.HostTabNavigatorSourceTest" --tests "com.binance.monitor.ui.account.Mt5BridgeGatewayClientSourceTest" --tests "com.binance.monitor.ui.trade.TradeExecutionCoordinatorSourceTest" --tests "com.binance.monitor.service.MonitorServiceParsingSourceTest" --tests "com.binance.monitor.ui.floating.FloatingWindowManagerSourceTest" --tests "com.binance.monitor.ui.main.MainActivityExportSourceTest"`
- 最近一次 Android 构建已通过：`.\gradlew.bat :app:assembleDebug`
- 最近一次服务端部署包生成已通过：`python scripts/build_windows_server_bundle.py`
- 最近一次服务端部署包本地校验已通过：`python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle --skip-remote`
- 当前部署包指纹以 `bundle_manifest.json` 和 `check_windows_server_bundle.py` 输出为准

## 目录说明

- [app](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app)：Android 客户端
- [bridge](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge)：Python 网关与服务端逻辑
- [deploy](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy)：部署脚本源
- [scripts](/E:/Github/BTCXAU_Monitoring_and_Push_APK/scripts)：构建、校验、辅助脚本
- [dist/windows_server_bundle](/E:/Github/BTCXAU_Monitoring_and_Push_APK/dist/windows_server_bundle)：最终 Windows 上传部署目录
- [archived_file](/E:/Github/BTCXAU_Monitoring_and_Push_APK/archived_file)：已归档的历史文件

## 搜索记录

- 2026-04-25：本轮为文档精简与状态同步，没有新增外部搜索

## 待办事项

- 真机继续观察 `1m / 5m / 15m / 1h / 1d` 页面上的最新价是否持续前进且同源
- 继续评估是否彻底删除 `AccountStatsBridgeActivity`、`MarketChartActivity` 中剩余的历史兼容实现
- 现场部署时继续确认域名、证书、443 放行和 `/v2/session/*` HTTPS 暴露状态
