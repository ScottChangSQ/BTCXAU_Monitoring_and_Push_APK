# BTCXAU Android/MT5 网关最终审计报告

审计日期：2026-04-25  
审计口径：结合既有多 Agent 审计结论与 `audit_report.md`，仅采纳已回到源码核验的问题。  
结论：当前最高风险不在普通 UI 代码，而在 MT5 网关交易/会话接口缺少统一鉴权，以及批量交易语义不闭合。Android 端主要风险集中在账户缓存清理、stream 状态提交时序、悬浮窗异常和本地审计存储并发。

## P0：必须优先修复

### 1. Bridge v2 会话、账户、交易、内部与 WS 接口缺少统一鉴权

- 分类：架构 / Bug / 安全
- 位置：`bridge/mt5_gateway/server_v2.py:7889`, `bridge/mt5_gateway/server_v2.py:7932`, `bridge/mt5_gateway/server_v2.py:8020`, `bridge/mt5_gateway/server_v2.py:8190`, `bridge/mt5_gateway/server_v2.py:8241`, `bridge/mt5_gateway/server_v2.py:8283`, `bridge/mt5_gateway/server_v2.py:8322`, `bridge/mt5_gateway/server_v2.py:8404`, `bridge/mt5_gateway/server_v2.py:8657`, `bridge/mt5_gateway/server_v2.py:8939`, `bridge/mt5_gateway/server_v2.py:8944`
- 证据：上述路由直接处理 session/account/trade/stream 请求，未见 `Depends`、`Authorization`、API key、签名校验或 WS query/header token 校验。
- 影响：任何可访问网关的人都可能读取账户状态、发起交易检查/提交、订阅账户与行情 stream，风险直接触达资金链路。
- 修复建议：新增统一鉴权依赖，例如 `require_gateway_auth(request)`，默认要求 `Authorization: Bearer ...` 或 `X-Gateway-Token`；HTTP 路由通过 FastAPI dependency 注入，`/v2/stream` 在 `accept()` 前校验 query/header token；只把明确公开的健康检查或 Binance 代理接口加入白名单。

### 2. 内部清缓存接口可无鉴权清空运行态和交易审计

- 分类：Bug / 安全 / 运维
- 位置：`bridge/mt5_gateway/server_v2.py:8901-8924`
- 证据：`/internal/admin/cache/clear` 直接清空 `snapshot_build_cache`、`snapshot_sync_cache`、`account_publish_state`、`trade_request_store`、`batch_request_store`、`trade_audit_store`、`session_diagnostic_store`，没有任何鉴权或来源限制。
- 影响：远程调用会破坏账户运行态、交易结果查询和审计线索，造成 Android 端状态回退或审计断链。
- 修复建议：纳入同一套 admin 鉴权；建议额外限制来源 IP 或仅绑定本机/内网；清理动作写入不可被该接口清除的审计日志。

### 3. 批量交易缺少批量级幂等，重试可能重复下单

- 分类：逻辑 / Bug / 交易链路
- 位置：`bridge/mt5_gateway/server_v2.py:8657-8672`, `bridge/mt5_gateway/server_v2.py:8689-8722`
- 证据：`/v2/trade/batch/submit` 每次都调用 `submit_trade_batch()` 执行，然后才 `_store_batch_request_result()`；没有像单笔交易 `requestId` 那样先查已有 `batchId` 与 payload digest。
- 影响：Android 网络超时后重试同一 `batchId`，服务端仍可能再次执行整批交易。
- 修复建议：在执行前按 `batchId` 查询缓存；缓存中保存 payload digest 和结果；相同 digest 返回幂等结果，不同 digest 返回 `DUPLICATE_PAYLOAD_MISMATCH`；缓存写入应覆盖成功、失败和未知状态。

### 4. `ALL_OR_NONE` 和 `GROUPED` 批量语义未闭合

- 分类：逻辑 / 交易链路
- 位置：`bridge/mt5_gateway/v2_trade_batch.py:205-238`
- 证据：`ALL_OR_NONE` 只检查 prepare 阶段错误；后续 `_execute_single_item()` 仍逐条 `check_request/send_request`，任一 item 在 check/send 阶段失败后，已成功的 item 不会回滚或阻断。`groupKey` 被读取并返回，但没有任何按组执行、组内失败阻断或组间隔离逻辑。
- 影响：UI 以为是“全成或全不成 / 分组原子执行”，实际可能部分成交，资金侧语义与用户预期不一致。
- 修复建议：明确策略契约：`ALL_OR_NONE` 至少应先完成全量 `order_check`，全部通过后才进入发送；若无法回滚 MT5 已成交单，应把策略改名为 `PRECHECK_ALL_OR_NONE` 并在 UI 明示。`GROUPED` 应按 `groupKey` 分组，每组独立预检和提交，并返回组级状态。

### 5. 单笔交易发送异常被标记为 `ACCEPTED`

- 分类：Bug / 交易链路
- 位置：`bridge/mt5_gateway/server_v2.py:8553-8567`
- 证据：`_trade_send_request()` 抛异常时，响应 `status` 被构造成 `STATUS_ACCEPTED`，同时 error 为 `ERROR_RESULT_UNKNOWN`。
- 影响：客户端可能把未知状态当成已受理，后续状态和审计都可能误导用户。
- 修复建议：引入独立状态，例如 `UNKNOWN` / `PENDING_CONFIRMATION`，并要求客户端用 `requestId` 查询或触发 MT5 订单/历史核验；不能用 `ACCEPTED` 表达未知结果。

## P1：尽快修复

### 6. Android 无身份账户清理会退化为全量删除

- 分类：逻辑 / Bug / 数据安全
- 位置：`app/src/main/java/com/binance/monitor/service/MonitorService.java:222-224`, `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java:886-890`, `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java:425-445`
- 证据：`ACTION_CLEAR_ACCOUNT_RUNTIME` 传入 `null, null`；`clearStoredSnapshotForIdentity()` 遇到空身份时调用全局 `clearRuntimeSnapshot()` 和 `clearTradeHistory()`。
- 影响：一次运行态清理可能删除所有账户的本地运行态和历史交易缓存。
- 修复建议：空身份直接拒绝清理并记录日志；调用方必须先解析明确账号和服务器；真正全量清理应使用单独方法名和二次确认。

### 7. v2 stream 健康时间和 `busSeq` 提交早于账户异步应用成功

- 分类：逻辑 / 异步状态
- 位置：`app/src/main/java/com/binance/monitor/service/MonitorService.java:336-344`, `app/src/main/java/com/binance/monitor/service/MonitorService.java:381-393`, `app/src/main/java/com/binance/monitor/service/MonitorService.java:495-503`
- 证据：收到消息后先更新 `lastV2StreamMessageAt`；`handleV2StreamMessage()` 在排队账户异步应用后立即 `commitAppliedBusSeq()`；实际 `applyPublishedAccountRuntime()` 的异常只在后台任务里记录。
- 影响：账户运行态应用失败时，顺序守卫仍认为该 `busSeq` 已消费，连接健康也被刷新，后续补偿不一定触发。
- 修复建议：账户快照应用返回明确成功/失败结果；只有成功后提交 `busSeq` 和刷新健康时间。若必须异步，使用 Future/回调在后台成功后提交序号，失败则保留重试或触发 bootstrap。

### 8. 悬浮窗 `addView` 未捕获异常

- 分类：Bug / Android 运行时
- 位置：`app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java:215-227`
- 证据：`showIfPossible()` 在权限检查后直接 `windowManager.addView()`，未捕获 `BadTokenException`、`SecurityException`、`IllegalStateException`；隐藏路径已有 try/catch。
- 影响：权限状态变化、窗口 token 失效、重复添加等边界可能导致服务崩溃。
- 修复建议：对 `addView` 包 try/catch；失败时保持 `windowAdded=false`，记录原因，必要时关闭悬浮窗开关并提示用户重新授权。

### 9. Android `TradeAuditStore` 本地审计存在并发丢写

- 分类：Bug / 模块质量
- 位置：`app/src/main/java/com/binance/monitor/ui/trade/TradeAuditStore.java:55-65`, `app/src/main/java/com/binance/monitor/ui/trade/TradeAuditStore.java:137-139`
- 证据：`record()` 是 read-modify-write，但没有同步；`SharedPreferences.apply()` 异步提交，并发调用会互相覆盖。
- 影响：交易关键阶段事实可能丢失，导致问题排查链路不完整。
- 修复建议：给 `record/getRecent/lookup` 加同一把锁或串行 executor；写入使用 `commit()` 或迁移 Room 表；保留 traceId + stage 唯一约束。

### 10. data 层反向依赖 UI 层交易审计模型

- 分类：架构 / 模块边界
- 位置：`app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java:22`, `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java:167-178`, `app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java:239-246`
- 证据：`data.remote.v2` 直接 import `com.binance.monitor.ui.trade.TradeAuditEntry` 并构建 UI 包模型。
- 影响：数据层与 UI 层形成反向依赖，后续拆模块、测试和复用都会受阻。
- 修复建议：把 `TradeAuditEntry` 下沉到 `data.model.v2.trade` 或独立 domain model；UI 层只做展示映射。

## P2：纳入近期治理

### 11. 多品种服务端 alert 冷却规则会因一个品种冷却抑制整条 alert

- 分类：逻辑 / 模块
- 位置：`app/src/main/java/com/binance/monitor/service/AbnormalSyncRuntimeHelper.java:16-45`, `app/src/main/java/com/binance/monitor/service/MonitorService.java:790-807`
- 证据：`shouldDispatchServerAlert()` 要求 alert 中全部 symbol 都脱离冷却期，只要一个 symbol 未过冷却就返回 false。
- 影响：BTC 和 XAU 同一条 alert 中，某一品种刚通知过会抑制另一个品种的有效提醒。
- 修复建议：按 symbol 拆分冷却判定，只通知未冷却的 symbol；或把服务端 alert 协议改为单品种 alert。

### 12. 本地 JSON 存储同步 I/O 且异常静默

- 分类：模块质量 / 性能
- 位置：`app/src/main/java/com/binance/monitor/data/local/LogManager.java:68-72`, `app/src/main/java/com/binance/monitor/data/local/LogManager.java:130-142`, `app/src/main/java/com/binance/monitor/data/local/AbnormalRecordManager.java:59-64`, `app/src/main/java/com/binance/monitor/data/local/AbnormalRecordManager.java:197-201`
- 证据：新增日志/异常记录时同步序列化和写文件；写入失败被忽略。
- 影响：主线程调用会卡顿；磁盘失败时调用方认为保存成功。
- 修复建议：迁移 Room 或串行 I/O executor；写入失败至少记录到 `Log.w` 并向上层暴露失败状态。

### 13. Kline/异常记录模型解析契约过硬，坏包处理分散

- 分类：模块质量 / 异常处理
- 位置：`app/src/main/java/com/binance/monitor/data/model/KlineData.java:42-69`, `app/src/main/java/com/binance/monitor/data/model/AbnormalRecord.java:60-73`, `app/src/main/java/com/binance/monitor/data/remote/KlineStreamMessageParser.java:26-41`, `app/src/main/java/com/binance/monitor/service/MonitorService.java:742-756`
- 证据：模型层使用 `getDouble/getString/parseDouble` 强解析；部分调用方 catch 后丢弃，部分本地读取失败会清空缓存。
- 影响：当前主要调用链已有 catch，不是直接崩溃 P0；但解析契约不统一，容易在新调用点变成崩溃或数据丢失。
- 修复建议：模型层提供 `tryParse` 返回错误对象；REST/WS/本地读取统一跳过坏记录并记录字段级错误，不要整库清空。

## 外部报告中降级或不采纳的点

- `SecureSessionPrefs` 使用 `java.util.Base64` 会在 API 26 以下崩溃：不采纳为当前缺陷。项目 `minSdk=24`，但 `app/build.gradle.kts:50-54` 开启 core library desugaring，`app/build.gradle.kts:98` 引入 `desugar_jdk_libs:2.1.5`。
- Android 13 通知权限完全缺失：降级。Manifest 已声明 `POST_NOTIFICATIONS`，`PermissionHelper` 有权限检查/请求，`NotificationHelper.notifyAbnormalAlert()` 也会检查权限。可继续优化权限被拒后的 UI 提示，但不是确认崩溃。
- `KlineData.fromSocket()` 坏包必崩：降级。`KlineStreamMessageParser.parse()` 已 catch `Exception` 并返回 null；底层模型仍建议治理，但不是当前主链崩溃。
- `AbnormalRecord.fromJson()` 坏包必崩：降级。`MonitorService.parseAbnormalRecords()` 已逐条 catch；本地落盘读取失败清空缓存是治理项。
- `SessionCredentialEncryptor` 未清 AES key：降级为安全硬化建议。代码已清理密码数组、明文字节和 `aesKey.getEncoded()` 副本；`SecretKey` 对象生命周期由 JVM/Provider 管理，不能据此定为明确漏洞。
- 旧入口 Activity / 旧 `ui.account.AccountStatsPreloadManager`：作为架构债保留，不按当前主链 Bug 定级。

## 修复顺序

1. 先补 Bridge 统一鉴权，覆盖 HTTP 和 WebSocket，尤其是交易、会话、内部管理接口。
2. 再修交易语义：batch 幂等、`ALL_OR_NONE/GROUPED`、未知交易结果状态。
3. 然后修 Android 状态一致性：账户清理身份约束、stream 成功应用后再提交序号、悬浮窗 addView 保护。
4. 最后做模块治理：交易审计存储并发、data/UI 依赖倒置、本地 JSON I/O、解析契约统一。

## 验证状态

- 已验证：上述结论均回到源码核对过具体位置。
- 未验证：未运行 Gradle、pytest、Android 真机或 MT5 实盘链路测试。本报告是只读审计结论，不代表修复已完成。
