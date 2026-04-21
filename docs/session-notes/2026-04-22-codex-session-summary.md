# 2026-04-22 会话整理

## 会话背景

本次会话围绕 Android APP 登录异常、MT5 网关登录链路、Windows 服务器部署脚本、APK 打包与部署问题展开，多次根据实机反馈和部署日志调整方案。

## 关键问题与处理过程

### 1. APP 登录错误持续复现

- 用户多次反馈 APP 登录出现相同错误，说明此前的 attach 方案没有解决根因。
- 经过确认，原有“附着到已打开 MT5”这条链路存在概念和实现上的偏差，不能稳定支撑独立登录和切号。
- 最终决策是不再走 attach，把登录主链切换为“独立 MT5 实例直登/切号”。

### 2. MT5 独立实例登录链路调整

- 网关侧新增独立 MT5 登录能力，补充 `mt5_direct_login.py`。
- `server_v2.py` 相关会话处理逻辑围绕独立登录、切号、诊断和协议返回做了调整。
- `test_v2_session_contracts.py` 等测试同步更新，用于覆盖新的会话契约。
- `start_gateway.ps1` 也做了配套调整，便于服务器端实际拉起与诊断。

### 3. APP 端会话接入与账户页面联动

- `GatewayV2SessionClient.java`、`AccountRemoteSessionCoordinator.java` 等 APP 侧会话接入代码做了多轮修改，配合新的网关会话结构。
- 账户页和统计页相关类也做了联动调整，涉及账户快照恢复、预加载、历史成交来源与 MT5 网关客户端调用。
- `activity_account_stats.xml` 一并调整，用于匹配当前账户统计页展示与恢复逻辑。

### 4. 新错误定位方向调整

- 用户指出服务器端没有看到 MT5 程序在前台启动，方向转为先验证“是否真的驱动了 MT5 程序”。
- 后续方案围绕“先保证 MT5 独立进程被显式拉起，再做登录/切号”继续推进，并重新打包验证。

### 5. Windows 部署脚本报错修复

- 部署时出现计划任务注册失败，错误为 `LogonType=InteractiveToken` 无法转换。
- 已修复 `deploy/tencent/windows/02_register_startup_task.ps1` 与 `deploy/tencent/windows/04_register_admin_panel_task.ps1` 中的计划任务注册方式，使其兼容当前 PowerShell / ScheduledTasks 枚举值。
- 这部分修复此前已经单独形成提交，但本次按你的要求继续纳入最终提交范围。

### 6. 工作区梳理与 Git 推送策略

- 曾分析过当前仓库中必要文件、临时文件和建议清理项，但那次仅做分析，没有执行删除。
- 当前工作区包含大量临时截图、XML、日志、草稿和调试产物，不适合直接整体推送到 `main`。
- 本次根据你的最新要求，改为只整理“本会话总结 MD + 你明确列出的文件”，并提交推送到 GitHub `main`。

## 本次纳入提交的文件范围

### 会话总结文件

- `docs/session-notes/2026-04-22-codex-session-summary.md`

### 部署与脚本

- `deploy/tencent/windows/02_register_startup_task.ps1`
- `deploy/tencent/windows/04_register_admin_panel_task.ps1`
- `scripts/tests/test_windows_server_bundle.py`

### MT5 网关与测试

- `bridge/mt5_gateway/server_v2.py`
- `bridge/mt5_gateway/tests/test_v2_session_contracts.py`
- `bridge/mt5_gateway/start_gateway.ps1`
- `bridge/mt5_gateway/mt5_direct_login.py`

### Android APP 相关

- `app/src/main/java/com/binance/monitor/ui/account/AccountCurveRebuildHelper.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotRestoreHelper.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsLiveActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsRepository.java`
- `app/src/main/java/com/binance/monitor/ui/account/Mt5GatewayClient.java`
- `app/src/main/java/com/binance/monitor/ui/chart/ChartHistoricalTradeSourceResolver.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- `app/src/main/res/layout/activity_account_stats.xml`
- `app/src/main/java/com/binance/monitor/ui/account/AccountSnapshotDisplayResolver.java`
- `app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java`
- `app/src/main/java/com/binance/monitor/ui/account/TradeLifecycleMergeHelper.java`
- `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java`
- `local.properties`

## 当前已知事实

- 本地当前工作区极脏，且本地 `main` 相比远端 `origin/main` 处于落后状态。
- 为避免把未点名的临时文件误带入 `main`，需要基于远端最新 `main` 在干净目录中组装这批文件后再提交。
- `local.properties` 属于本地环境文件，按常规并不建议进入远端主分支，但本次按你的明确要求纳入提交范围。

## 后续建议

- 推送完成后，建议单独再做一次仓库清理，重点处理临时截图、XML dump、日志、草稿目录和本地环境文件。
- 如果还要继续排查 MT5 登录/切号链路，建议优先在服务器端补一条“MT5 进程拉起成功 + 登录结果 + 当前账号”的可观测诊断输出。
