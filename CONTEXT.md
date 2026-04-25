# CONTEXT

## 当前正在做什么
- 2026-04-25 已完成文档收口：`README.md` 已精简为近期主文档，只保留主要功能、技术架构、已完成功能、运行/测试/部署命令、阶段验收和少量待办。
- 当前项目可直接进入下一轮实际工作：代码审计、功能修改、APK 验证或服务端部署，不需要再回头整理前几轮长历史记录。

## 上次停在哪
- 2026-04-25 已完成 `.\gradlew.bat :app:assembleDebug`，当前 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
- 2026-04-25 已完成 `python scripts/build_windows_server_bundle.py`，服务端部署目录为 `dist/windows_server_bundle/`。
- 2026-04-25 已完成 `python scripts/check_windows_server_bundle.py --dist dist/windows_server_bundle --skip-remote`，本地源码与部署目录一致；当前 `bundleFingerprint = 1ffb40568335bbd6ce7ec5bf4072edb981c8924921f376faa54cc4d77c95db3a`。

## 近期关键决定和原因
- 三份计划已全部执行完成：
  - `docs/superpowers/plans/2026-04-25-runtime-safety-and-truth-unification.md`
  - `docs/superpowers/plans/2026-04-25-architecture-split-and-entry-convergence.md`
  - `docs/superpowers/plans/2026-04-25-heuristic-retirement-and-final-cleanup.md`
  原因：运行安全、架构收口、启发式清理这三条主线已经完成闭环，并已通过相关源码测试和 `:app:assembleDebug` 验证。
- 页面层依赖创建已统一收口到 `app/src/main/java/com/binance/monitor/ui/runtime/ScreenDependencyProvider.java`。
  原因：避免 `MarketChartScreen / AccountStatsScreen / AccountPositionPageController` 继续直接 `new` 仓储、会话客户端和线程池。
- `Mt5BridgeGatewayClient`、`TradeExecutionCoordinator`、`MonitorService`、`FloatingWindowManager` 这批高风险文件已完成本轮清理。
  原因：去掉字符串启发式、旧回退链和关键吞异常点，减少“看起来能跑、实际不可验证”的逻辑。
- `README.md` 和 `CONTEXT.md` 改为“近期优先”的极简维护方式。
  原因：当前最需要的是让后续任务能快速接续，而不是继续累积历史过程记录。

## 当前主目录口径
- `app/`：Android 客户端
- `bridge/`：MT5 网关服务端源码
- `deploy/`：部署脚本源
- `scripts/`：构建、校验脚本
- `dist/windows_server_bundle/`：最终 Windows 上传部署目录
- `archived_file/`：已归档历史文件
