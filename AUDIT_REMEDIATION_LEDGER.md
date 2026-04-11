# 正式整改台账

> 项目：`BTCXAU_Monitoring_and_Push_APK`  
> 基准报告：`MULTI_AGENT_AUDIT_REPORT.md`  
> 台账时间：`2026-04-11`  
> 用途：把审计报告收口成一版严格、可验证、不过期的正式整改台账，作为后续执行唯一依据。

---

## 1. 台账规则

- 本台账只接受“当前代码 + 当前测试 + 当前 CONTEXT”能够支持的结论。
- 所有问题必须给出可验证证据，不接受只靠描述成立的条目。
- 所有整改项必须给出未来的验收证据，不接受“修完再看体感”。
- 严禁把 UI 层补丁、局部稳定化、启发式拦截写成正式方案。

## 2. 先纠正报告本身

`MULTI_AGENT_AUDIT_REPORT.md` 的统计口径前后不一致：

- 执行摘要写的是“P0-P3 共 12 项”
- 但“修复建议汇总”实际列出了 `P0 2项 + P1 4项 + P2 4项 + P3 6项 = 16项`

本台账以后者为准，按 **16 条原始整改建议** 逐条重判。

## 3. 状态定义

- `确认纳入`：问题当前仍成立，可直接进入正式整改队列。
- `改写后纳入`：原报告方向对，但问题表述、根因或方案不够严格，需改写后再执行。
- `移出当前整改`：问题已经闭合、证据不足，或建议本身不适合作为当前阶段任务。

## 4. 重判总览

| 原编号 | 原报告项 | 重判状态 | 当前执行优先级 | 说明 |
|---|---|---|---|---|
| P0-1 | 账户页首帧性能优化 | 确认纳入 | P0 | 当前最明确、证据最完整的阻塞项 |
| P0-2 | Room 数据库主线程访问 | 改写后纳入 | P0 | 风险仍在，但需按真实调用链重写问题定义 |
| P1-1 | AccountStatsBridgeActivity 继续拆分 | 确认纳入 | P1 | 文件体量和职责密度问题仍成立 |
| P1-2 | 图表页跨品种混图风险 | 改写后纳入 | P1 | 风险存在，但不能按报告里的局部清缓存方案执行 |
| P1-3 | 完善 WebSocket 重连机制 | 改写后纳入 | P2 | 当前已有重连，问题应改写成“状态机健壮性验证不足” |
| P1-4 | 增加交易幂等性保护 | 移出当前整改 | - | 客户端+服务端已存在 requestId 幂等链路 |
| P2-1 | 修复内存泄漏 | 改写后纳入 | P2 | 存在候选点，但需逐点验真，不能整包接收 |
| P2-2 | 修复并发安全问题 | 确认纳入 | P2 | 当前有明确并发状态变量值得收口 |
| P2-3 | 悬浮窗返回栈一致性 | 改写后纳入 | P2 | 是真实审计方向，但要先定义一致性真值 |
| P2-4 | 安全会话缓存静默删数据 | 移出当前整改 | - | 已区分空缓存与读取失败，不再静默清空 |
| P3-1 | 引入依赖注入框架 | 移出当前整改 | - | 属于架构升级，不是当前缺陷整改 |
| P3-2 | 增加 JSON Schema 校验 | 移出当前整改 | - | 现阶段收益低，不是当前主风险 |
| P3-3 | 优化包结构 | 改写后纳入 | P3 | 可作为拆分收口后的整理动作，不单列先做 |
| P3-4 | 完善单元测试覆盖 | 改写后纳入 | P2 | 应从“覆盖率目标”改成“风险点验收缺口补齐” |
| P3-5 | 引入 MVVM/MVI | 移出当前整改 | - | 属于架构迁移，不是本轮整改项 |
| P3-6 | 代码规范统一（SpotBugs/Checkstyle） | 改写后纳入 | P3 | 可纳入工程化防回归，但不是功能缺陷 |

## 5. 逐条重判明细

### 5.1 P0-1 账户页首帧性能优化

- 重判状态：`确认纳入`
- 当前判断：
  - 问题成立，且已有较完整证据链。
  - 不是单点慢，而是“首帧布局绘制 + 快照派生计算 + 局部列表重建”的组合成本。
- 当前证据：
  - `CONTEXT.md` 已记录：`Displayed` 约 `+945ms ~ +1052ms`
  - `ChainTrace account_render` 已记录：
    - `on_create_total` 约 `389ms ~ 473ms`
    - `apply_snapshot_total` 约 `179ms ~ 228ms`
    - `refresh_trade_stats` 约 `70ms ~ 88ms`
    - `refresh_trades` 约 `32ms ~ 37ms`
- 不接受的旧方案：
  - 不能把 `removeAllViews()` 之类 UI 操作直接挪到后台线程。
- 后续整改目标：
  - 把“纯计算”和“首屏必要绘制”彻底分离。
- 验收证据：
  - 真机执行 `adb shell am start -W com.binance.monitor/.ui.account.AccountStatsBridgeActivity`
  - 真机执行 `adb shell dumpsys gfxinfo com.binance.monitor`
  - `logcat` 中 `ChainTrace account_render` 新样本
  - 关闭标准：
    - 首帧稳定降到 `< 700ms`
    - `refresh_trade_stats` 不再在主线程出现 70ms 级长段
    - `gfxinfo` jank 相比当前基线有明确下降

### 5.2 P0-2 Room 数据库主线程访问

- 重判状态：`改写后纳入`
- 原报告问题：
  - “Room 数据库主线程访问”
- 改写后的正式问题：
  - “账户链路的本地快照恢复入口仍保留同步读库 API，需逐调用点证明不在主线程触发”
- 当前判断：
  - 风险仍值得处理，但报告里的结论不能直接当已确认事实。
  - 当前 [AccountStatsBridgeActivity](app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java) 已把 `hydrateLatestCacheFromStorage()` 放到 `ioExecutor`。
- 当前证据：
  - `AccountStatsBridgeActivity.java:3061-3090`：本地快照恢复已切到后台执行
  - `AccountStatsPreloadManager.java:104-109`：`hydrateLatestCacheFromStorage()` 本身仍是同步读库 API
  - `AccountStorageRepository.java:266`：`loadStoredSnapshot()` 仍为同步方法
- 后续整改目标：
  - 不再讨论“有没有风险感觉”，而是给所有 `loadStoredSnapshot()` 调用点逐个定线程边界。
- 验收证据：
  - 建立 `StrictMode` / source test / 调用点清单三件套
  - 关闭标准：
    - 所有 `loadStoredSnapshot()` 调用点都有明确后台线程证据
    - debug 模式下无主线程数据库访问告警

### 5.3 P1-1 AccountStatsBridgeActivity 继续拆分

- 重判状态：`确认纳入`
- 当前判断：
  - 问题成立。
- 当前证据：
  - [AccountStatsBridgeActivity.java](app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java) 当前约 `338336 bytes / 6833 lines`
  - 当前虽已完成 `R6` 协调器拆分，但页面仍承载大量渲染、统计、筛选、生命周期编排逻辑
- 后续整改目标：
  - 不是“为了变小而拆”，而是按职责拆成可验证的边界：
    - 刷新编排
    - 总览渲染
    - 表格区渲染
    - 统计计算
    - 筛选/排序状态
- 验收证据：
  - 文件体量变化
  - 新旧 source test 回归
  - 单职责边界图更新到 `ARCHITECTURE.md`
  - 关闭标准：
    - 主 Activity 不再同时直接承载大段统计计算与明细渲染

### 5.4 P1-2 图表页跨品种混图风险

- 重判状态：`改写后纳入`
- 原报告问题：
  - “切换品种时未完全清理旧数据”
- 改写后的正式问题：
  - “图表页需证明 K 线、叠加层、历史成交和持仓标注不会跨 `symbol+interval+session` 边界串用”
- 当前判断：
  - 这是合理风险，但还不能下结论说“当前已经混图”。
  - 原报告建议的“在 `onDestroy()` 清 klineCache”不严格，属于局部补丁。
- 当前证据：
  - `MarketChartActivity.java:1201-1207`：`switchSymbol()` 只切 `selectedSymbol` 后重新请求
  - `MarketChartActivity.java:1526-1532`、`2180-2181`：已有请求返回时的 symbol/interval 守卫
  - `MarketChartActivity.java:1896-1909`：当前只有 schema 变更时全清缓存
- 后续整改目标：
  - 不做“多清几次缓存”式补丁，而是做清晰的数据边界约束。
- 验收证据：
  - 新增 source test / integration test，覆盖快速切换品种和周期
  - 真机日志证明旧 symbol 响应不会污染当前图表
  - 关闭标准：
    - 任意快速切换下，图表主序列和所有叠加层都只消费当前 key 数据

### 5.5 P1-3 完善 WebSocket 重连机制

- 重判状态：`改写后纳入`
- 原报告问题：
  - “网络断开后可能无法自动恢复”
- 改写后的正式问题：
  - “当前已有重连，但缺少对 stream 状态机的系统性验收，需要验证不会出现假连接、重复重连、僵死连接”
- 当前判断：
  - 原报告“机制缺失”不成立；当前实现已存在重连逻辑。
- 当前证据：
  - [GatewayV2StreamClient.java](app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java)
  - `scheduleReconnect()` 已实现递增延迟重连，`delayMs = min(30000, 1500 * attempt)`
  - `OkHttpClient.Builder().retryOnConnectionFailure(true)`
- 后续整改目标：
  - 从“补一个指数退避模板”改成“把 stream 状态机验真并补缺口”。
- 验收证据：
  - 断网 / 恢复 / 服务端重启 / 前后台切换 四组用例
  - 连接状态 source test / 真机日志
  - 关闭标准：
    - 无重复重连风暴
    - 无“UI 显示已连接但 stream 已断”的假阳性

### 5.6 P1-4 增加交易幂等性保护

- 重判状态：`移出当前整改`
- 当前判断：
  - 原报告结论已过期。
  - 当前客户端和服务端已经存在 requestId 幂等链路。
- 当前证据：
  - `TradeExecutionCoordinator.java:194-205`：提交异常后按 `requestId` 追认回执
  - `server_v2.py:5311-5456`：`/v2/trade/submit` 和 `/v2/trade/result` 已按 `requestId + payloadDigest` 做幂等
  - 服务端会对重复 `requestId` 返回 `STATUS_DUPLICATE`
- 处理意见：
  - 该项不再作为“新增能力”立项。
  - 若后续要做，只能改成“补幂等链路验收测试”。
- 验收证据：
  - 当前已存在代码证据，后续如立新项，应补服务端/客户端联调用例

### 5.7 P2-1 修复内存泄漏

- 重判状态：`改写后纳入`
- 当前判断：
  - 存在候选点，但原报告把“候选风险”直接写成“已确认泄漏”，证据不足。
- 当前证据：
  - `FloatingWindowManager.java:48`：主线程 `Handler` 存在
  - `MonitorService.java:202-218`：`onDestroy()` 已显式移除前后台监听并销毁协调器
  - `AccountStatsPreloadManager.java:47`：监听器集合为 `CopyOnWriteArraySet`
- 改写后的正式问题：
  - “对候选泄漏点建立逐点验证，而不是整包接收‘存在内存泄漏’结论”
- 验收证据：
  - LeakCanary 或等价 heap dump 证据
  - 生命周期 source test
  - 关闭标准：
    - 浮窗、账户页、服务销毁后无残留强引用链

### 5.8 P2-2 修复并发安全问题

- 重判状态：`确认纳入`
- 当前判断：
  - 该项成立，但要按变量级别收口，不做泛化整改。
- 当前证据：
  - `MonitorService.java:83-88`：多组 `volatile` 运行态标记与异步回调混用，需要重新确认写入/读取线程边界
  - `SecureSessionPrefs.java:44`：`lastStorageError` 仍为 `volatile String`
  - `AccountStatsPreloadManager.java:53-62`：存在多组运行态变量，需要重新确认复合操作边界
- 后续整改目标：
  - 把“并发问题”改成明确变量清单。
- 验收证据：
  - 单元测试覆盖竞态路径
  - source test 锁定并发原语
  - 关闭标准：
    - 每个共享状态都有唯一并发策略：锁 / 原子类 / 单线程执行器

### 5.9 P2-3 悬浮窗返回栈一致性

- 重判状态：`改写后纳入`
- 当前判断：
  - 这是合理审计方向，但还不是闭合问题定义。
- 当前证据：
  - `FloatingWindowManager.java:630-644`：悬浮窗跳转统一经 `OverlayLaunchBridgeActivity`
  - `OverlayLaunchBridgeActivity.java` 与 `TopLevelTabNavigationSourceTest` 已对 `CLEAR_TOP | SINGLE_TOP` 有约束
- 改写后的正式问题：
  - “定义从悬浮窗进入图表/主页后的统一返回栈真值，并验证实际导航与该真值一致”
- 验收证据：
  - 手工导航矩阵
  - source test 锁定 intent flags
  - 真机 `dumpsys activity activities` 对照

### 5.10 P2-4 安全会话缓存静默删数据

- 重判状态：`移出当前整改`
- 当前判断：
  - `SecureSessionPrefs.readSessionSummary()` 已区分空缓存、正常和读取失败，不再把解密异常伪装成空缓存。
- 当前证据：
  - `SecureSessionPrefs.java:134-160`：读取失败返回 `SessionSummarySnapshot.storageFailure(...)`，并携带 `lastStorageError`
  - `AccountSessionRestoreHelper.java:23-33`：`storageFailure` 分支会清空活跃账号并阻断“已激活”标记
- 处理意见：
  - 本项原始风险已在当前代码闭合，移出本轮整改。
  - 如需后续增强，可补 UI 提示或遥测，但不再作为整改阻塞项。
- 验收证据：
  - 当前代码与调用链已提供区分失败的路径，无需新增验收动作

### 5.11 P3-1 引入依赖注入框架

- 重判状态：`移出当前整改`
- 当前判断：
  - 这是架构演进建议，不是当前缺陷整改项。
- 原因：
  - 当前主风险是性能、状态边界和线程语义，不是“没有 Hilt”。
- 处理意见：
  - 不进入当前整改台账执行面。

### 5.12 P3-2 增加 JSON Schema 校验

- 重判状态：`移出当前整改`
- 当前判断：
  - 当前 `GatewayV2Client` 已对关键 canonical 字段采用显式必填约束。
  - 全量 JSON Schema 校验不是当前最划算的缺陷修复方向。
- 处理意见：
  - 不纳入本轮正式整改。

### 5.13 P3-3 优化包结构

- 重判状态：`改写后纳入`
- 当前判断：
  - 这项不应独立先做，应作为 Activity 拆分和职责下沉的收口动作。
- 处理意见：
  - 改写为“伴随拆分任务进行包级整理”，不单独起任务抢前排。
- 验收证据：
  - 新文件职责图
  - `ARCHITECTURE.md` 同步更新

### 5.14 P3-4 完善单元测试覆盖

- 重判状态：`改写后纳入`
- 当前判断：
  - “覆盖率 80%”不是严格目标。
  - 当前更需要的是针对风险点的验收缺口补齐。
- 当前证据：
  - 当前已有大规模 source test 和服务端 `228` 项通过
  - 但性能、并发、状态边界、导航矩阵等仍有验收空白
- 改写后的正式问题：
  - “按风险补测试，而不是按覆盖率补测试”
- 验收证据：
  - 为每个正式整改项建立一条对应回归测试或真机验证脚本

### 5.15 P3-5 引入 MVVM/MVI

- 重判状态：`移出当前整改`
- 当前判断：
  - 这是架构迁移，不是当前整改项。
- 原因：
  - 若在当前阶段引入，会和性能收口、页面拆分混成一次大迁移，风险过高。

### 5.16 P3-6 代码规范统一（SpotBugs / Checkstyle）

- 重判状态：`改写后纳入`
- 当前判断：
  - 可作为低优先级工程化护栏，但不是当前功能/性能缺陷。
- 处理意见：
  - 后置到主要问题收口后执行。
- 验收证据：
  - CI 新增静态检查命令
  - 无新增阻断误报

## 6. 当前正式整改队列

### 6.1 当前确认纳入的正式整改项

1. P0-1 账户页首帧性能优化  
2. P1-1 AccountStatsBridgeActivity 继续拆分  
3. P2-2 并发安全问题收口  

### 6.2 改写后纳入的正式整改项

1. P0-2 本地快照恢复线程边界审计  
2. P1-2 图表页 `symbol+interval+session` 数据边界收口  
3. P1-3 v2 stream 状态机健壮性验证与补强  
4. P2-1 候选内存泄漏点逐点验真  
5. P2-3 悬浮窗返回栈一致性定义与验证  
6. P3-3 包结构整理（伴随拆分执行）  
7. P3-4 风险驱动测试补齐  
8. P3-6 静态检查护栏

### 6.3 移出当前整改的项

1. P1-4 交易幂等性保护  
2. P2-4 安全会话缓存静默删数据（已在现代码闭合）  
3. P3-1 引入依赖注入框架  
4. P3-2 增加 JSON Schema 校验  
5. P3-5 引入 MVVM/MVI

## 7. 建议的下一步执行顺序

1. 先做 `P0-1 + P0-2`  
2. 再做 `P1-1`  
3. 然后进入 `P1-2 / P1-3 / P2-2 / P2-3`  
4. 最后再做 `P3-3 / P3-4 / P3-6`

---

## 8. 本台账的结论

- 这份审计报告**不是可直接执行的事实清单**，而是问题发现清单。
- 按当前代码与验证重判后，**16 条建议中只有 3 条可直接进入正式整改**。
- 其余条目要么需要改写成更严格的问题定义，要么不应纳入当前阶段。
- 后续一律以本台账为准，不再直接引用旧报告中的原始优先级和原始措辞。
