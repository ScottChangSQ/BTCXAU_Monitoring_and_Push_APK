# CONTEXT

## 当前正在做什么
- 2026-04-26 已将 CPU/电量专项、网关鉴权链、账户持续刷新修复和部署契约修复收口到 `main`，当前口径是主线已包含全部已完成任务，后续从 `main` 继续开发。

## 上次停在哪
- 本次主线收口已补齐：Android 前后台运行态裁剪、悬浮窗/图表节流、账户持续刷新修复、设置页 `Gateway Auth Token` 入口恢复、bridge 鉴权与部署契约一致化、相关源码约束测试同步到新架构。
- 本次最终验证已完成：`.\gradlew.bat :app:testDebugUnitTest`、`.\gradlew.bat :app:assembleDebug`、`python -m pytest bridge/mt5_gateway/tests -q`。

## 近期关键决定和原因
- `MonitorService` 继续以“成功应用后才推进健康时间 / busSeq”为准，避免把收到坏包误判成链路正常。
- 后台运行态固定三档：前台全量、亮屏后台最小悬浮窗、熄屏 `alert-only`；原因是用户当前重点是 CPU/电量，不再优先扩协议面。
- 网关鉴权统一走 `GatewayAuthRequestHelper`，HTTP / WS 客户端不再各自散落 header 逻辑；部署端仍要求 `.env` 显式配置 `GATEWAY_AUTH_TOKEN`。
- 设置页口径固定为“公网入口只读 + Auth Token 可编辑保存”，避免再次出现手机端无 token 输入而部署端要求必填的断链。
- 主线收口策略固定为“先在隔离分支完成验证，再一次性合并回 `main`”，避免未解决冲突和半成品继续滞留在主工作区。

## 当前主目录口径
- `app/`：Android 客户端
- `bridge/`：MT5 网关服务端源码
- `deploy/`：部署脚本源
- `scripts/`：构建、校验脚本
- `dist/windows_server_bundle/`：最终 Windows 上传部署目录
- `archived_file/`：已归档历史文件
