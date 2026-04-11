# CONTEXT

## 当前正在做什么
- 当前已完成一次“多模型独立智能体 + 主线复验”的全量任务复核。
- 当前基线：本轮复核中发现并修复了 4 个真实问题，App 与服务端全量自动化验证已重新通过。
- 当前已完成最新 APK 编译、真机安装和 Windows 服务器部署目录重建。

## 上次停在哪个位置
- 上次停在按模块并行复核所有 Task：账户/安全、图表/悬浮窗/导航、网关/部署、台账/文档四条线并行推进。
- 本轮已完成主线整合、冲突消解、源码测试更新、统一回归、文档口径统一，以及 APK / 部署目录更新；当前停在执行结果收口。

## 近期关键决定和原因
- 图表页 `onNewIntent -> applyIntentSymbol(..., true)` 改为先 `invalidateChartDisplayContext()` 再重拉；原因是切换展示上下文时，必须先失效旧 symbol 的显示状态，不能靠后续请求结果“自然覆盖”。
- 账户页新增 `refreshSessionStorageErrorFromPrefs()`，把本地会话摘要读写错误改成实时同步状态；原因是“曾经失败”不能继续被页面展示成“当前仍失败”。
- 服务端把 `MT5_INIT_TIMEOUT_MS` 的上限从 `50000` 放宽到 `120000`，并新增部署契约测试；原因是部署样例允许 `90000`，服务端不应静默截断用户显式配置。
- 旧 source test 改为校验真实职责位置：账户快照刷新链看 `AccountSnapshotRefreshCoordinator`，悬浮窗 overview K 线消费看 `MonitorFloatingCoordinator`；原因是测试必须跟随职责边界，而不是继续盯旧 Activity/Service 字符串位置。
- 台账与文档口径重新收口到“16 条原始建议、11 条正式整改队列、P2-4 已闭合移出”；原因是当前代码已经能区分空缓存与读取失败，且 UI 错误状态也已同步闭合。
- README 最终口径改为“本轮复核与修复已收口，但正式整改台账仍余 P0-1 / P1-1 / P2-2”；原因是要把“已完成的收口工作”和“仍待执行的正式整改项”明确分开，避免描述性结论过度外推。
- APK 交付口径固定为当前工作区 `:app:assembleDebug` 产物，并直接安装到当前在线设备 `7fab54c4`；原因是本次需求是立即下发最新版安装包到已连接手机。
- 服务器部署目录通过 `python scripts/build_windows_server_bundle.py` 重建到 `dist/windows_server_bundle`；原因是要确保待上传目录与当前仓库代码同步，而不是沿用旧 bundle。

## 当前验证状态
- App 全量验证通过：`.\gradlew.bat :app:auditCriticalCheckstyle :app:compileDebugJavaWithJavac :app:testDebugUnitTest`，结果 `BUILD SUCCESSFUL`。
- 服务端全量验证通过：`python -m unittest discover -s bridge/mt5_gateway/tests -p "test_*.py" -v`，结果 `Ran 229 tests ... OK`。
- APK 已重新编译：`.\gradlew.bat :app:assembleDebug`，结果 `BUILD SUCCESSFUL`。
- 真机安装已完成：`adb -s 7fab54c4 install -r app\\build\\outputs\\apk\\debug\\app-debug.apk`，结果 `Success`。
- 部署目录已更新：`python scripts/build_windows_server_bundle.py` 输出 `dist/windows_server_bundle`，目录时间 `2026-04-11 20:21:37`。
- 本轮新增真实修复点已包含在全量验证中：
  - 图表 `onNewIntent` 切品种数据边界收口
  - 账户页本地会话摘要错误状态实时同步
  - 部署超时配置 `MT5_INIT_TIMEOUT_MS=90000` 契约
  - 旧源码约束测试迁移到当前真实职责边界
- 文档口径说明：
  - `README.md / CONTEXT.md / AUDIT_REMEDIATION_LEDGER.md` 现已统一按正式整改台账表述。
  - `MULTI_AGENT_AUDIT_REPORT.md` 保留为原始审计报告，不再直接作为执行口径；后续执行以 `AUDIT_REMEDIATION_LEDGER.md` 为准。
