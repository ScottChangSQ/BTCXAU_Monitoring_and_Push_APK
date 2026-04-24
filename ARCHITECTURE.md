# ARCHITECTURE

## 每个文件/模块的职责

- [app/src/main/java/com/binance/monitor/ui/host/MainHostActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/host/MainHostActivity.java)
  单主壳入口，负责承载底部 `交易 / 账户 / 分析` 3 个常驻 Tab、接收目标 Tab intent，并统一补齐通知权限提示；`设置` 已改为独立目录页入口，launcher alias 现已指向它。
- [app/src/main/java/com/binance/monitor/ui/main/MainActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/main/MainActivity.java)
  旧行情监控入口桥接页，当前只负责把历史入口收口到主壳 `MARKET_MONITOR` Tab。
- [app/src/main/java/com/binance/monitor/service/MonitorService.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorService.java)
  前台服务入口，负责展示快照刷新、异常判断、通知调度，以及生成悬浮窗统一快照；当前市场 stream 快照已不再直接分发给 3 份 display cache，而是先整理成 `SymbolMarketWindow` 列表，再交给 `MonitorRepository -> MarketRuntimeStore / MarketTruthCenter` 落成唯一市场底稿。`v2 stream` 当前已拆成两条消费链：`marketTick -> handleMarketTickMessage() -> applyMarketSnapshotFromStream()` 负责市场主显示直推，`syncEvent/heartbeat` 继续负责账户、历史和异常兼容链；`busSeq` 与 `marketSeq` 也分别在消息真正成功应用后才提交，不再共用一个顺序口径。实时链也已收口成“服务端已发布的新 `market.snapshot` 通过技术校验后直接入真值”，不再由 APP 本地判断“这次同分钟变化算不算推进”。同时，实时市场消息与补修/配置同步也已拆成两条独立串行执行器，避免 `1m REST` 补修阻塞实时落图。当统一市场真值长时间未推进或闭合 `1m` 底稿出现缺口时，服务层会用正式 `1m REST` 对 `MarketTruthCenter` 做补修，并先经过共享缺口状态机判断同一缺口是否允许再次重试。
- [app/src/main/java/com/binance/monitor/service/MonitorServiceController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorServiceController.java)
  服务入口控制器，负责统一封装前台服务的启动与动作分发，避免页面、设置页和开机广播各自拼装 `Intent` 后直接拉起服务。
- [app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorRuntimePolicyHelper.java)
  运行策略辅助工具，负责把前后台状态转换成心跳、异常同步和悬浮窗刷新的统一节奏。
- [app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/V2StreamRefreshPlanner.java)
  v2 同步流刷新决策器，负责把旧 `syncBootstrap / syncEvent / heartbeat` 映射成最小消费动作与必要的历史补拉判断；市场主显示直推 `marketTick` 已不再经过这里决策。
- [app/src/main/java/com/binance/monitor/service/account/AccountHistoryRefreshGate.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/account/AccountHistoryRefreshGate.java)
  账户历史补拉并发 gate，负责把“当前是否在补拉”“最新待续跑 revision”收口到单一同步原语，避免 `MonitorService` 再散落锁、标志位和 revision 字符串。
- [app/src/main/java/com/binance/monitor/service/stream/V2StreamSequenceGuard.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/stream/V2StreamSequenceGuard.java)
  v2 stream 顺序守卫，负责分别按 `busSeq / marketSeq` 严格过滤重复或倒序消息，保证旧连接消息或旧 `marketTick` 不会回写当前运行态；当前拆成“先判断 `shouldApply*()`，成功应用后再 `commitApplied*()`”两段式提交。
- [app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorFloatingCoordinator.java)
  悬浮窗协调器，负责悬浮窗偏好应用、刷新节流、统一快照拼装与销毁时的清理链收口；当前在 canonical account cache 存在时已开始优先消费 `UnifiedRuntimeSnapshotStore` 的产品运行态，不再重复自行聚合同一份持仓真值。市场侧刷新门槛也已改为跟随仓库里的统一市场真值签名，不再只看旧 `MarketRuntimeStore`。
- [app/src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/service/MonitorForegroundNotificationCoordinator.java)
  前台通知协调器，负责服务前台通知的启动、去重刷新和销毁时的状态复位。
- [app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java)
  监控展示仓库，负责承接市场 selector、连接状态、监控开关和异常记录入口；当前市场显示真值已进一步收口到 `MarketTruthCenter`，对外正式边界只保留 `MarketRuntimeSnapshot` LiveData、`MarketTruthSnapshot` LiveData，以及 `selectLatestPrice / selectClosedMinute / selectDisplayKline / selectDisplaySeries / selectMarketWindowSignature` 这组统一 selector，不再暴露旧 display snapshot 或仓库级同步快照 getter。市场补修链新增 `selectMinuteGap / buildMinuteGapEvidenceToken / shouldRetryMinuteGapRepair` 这一组缺口状态机入口，供服务层和图表页共用同一份缺口记忆。
- [app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/truth/MarketTruthCenter.java)
  统一市场真值中心，负责把 `v2/stream` 的最新价、最新闭合分钟、1m 草稿，以及图表 REST 拉回的周期历史和 1m 修补窗口，合并成唯一市场读模型；图表主显示、实时尾巴和悬浮窗最新价都应从这里间接读取。
- [app/src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/truth/MinuteBaseStore.java)
  1 分钟基础底稿存储，负责维护每个品种的闭合 1m 历史、当前 1m 草稿和最新价，作为所有高周期尾部与悬浮窗读价的共同底座。
- [app/src/main/java/com/binance/monitor/runtime/market/truth/IntervalProjectionStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/truth/IntervalProjectionStore.java)
  周期投影存储，负责保存 REST 周期历史种子，并用最近闭合的 1m 正式底稿重算 `5m~1d` 的最新尾部，避免图表页自己再保留一套 `minuteKey` 聚合真值。
- [app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/truth/HistoryRepairCoordinator.java)
  历史修补协调器，负责在高周期图表回包后补最近一段正式 `1m` 底稿，再把补档结果回写 `MonitorRepository -> MarketTruthCenter`，让高周期尾部与 1m 同步重算；当前若补前已经检测到闭合 `1m` 缺口，也会与共享缺口状态机一起收口“已请求 / 仍缺失 / 已解决”状态，避免同一缺口被图表页无条件反复补。
- [app/src/main/java/com/binance/monitor/runtime/market/truth/GapRepairStateStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/truth/GapRepairStateStore.java)
  缺口补修状态存储，负责把同一缺口按 `NEW_GAP / REPAIRING / RESOLVED / STALLED / RETRY_READY` 收口成共享状态机；只有当上游证据变化时，才允许对同一缺口再次自动补修。
- [app/src/main/java/com/binance/monitor/runtime/market/MarketRuntimeStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/MarketRuntimeStore.java)
  市场运行态真值中心，负责收口每个交易品种的 `latestPrice / latestClosedMinute / latestPatch`，并通过统一 revision 推进市场底稿版本。
- [app/src/main/java/com/binance/monitor/runtime/market/MarketSelector.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/MarketSelector.java)
  市场 selector，负责从 `MarketRuntimeStore` 导出兼容读模型；当前最新价、闭合分钟与显示分钟的正式 UI 主链已切到 `MarketTruthCenter`，这里只保留运行态兼容职责。
- [app/src/main/java/com/binance/monitor/runtime/market/model/MarketRuntimeSnapshot.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/model/MarketRuntimeSnapshot.java)
  市场运行态快照，负责承载当前所有交易品种的窗口内容、市场底稿 revision 和更新时间。
- [app/src/main/java/com/binance/monitor/runtime/market/model/SymbolMarketWindow.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/market/model/SymbolMarketWindow.java)
  单个交易品种的市场窗口真值，负责把 `latestClosedMinute` 与 `latestPatch` 固定收口在同一对象内，供 selector 统一导出展示态。
- [app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/BinanceApiClient.java)
  Binance REST 数据访问层，负责通过 `/binance-rest` 拉取行情；图表长周期现在固定只走 Binance REST 原生周期接口，不再切到日线聚合或历史回退链。
- [app/src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/FallbackKlineSocketManager.java)
  Binance 回退 K 线流管理器，负责通过韩国服务器的 `/binance-ws` 订阅 `@kline_1m`，仅在 `v2 stream` 不健康时补最新展示快照。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2Client.java)
  v2 网关客户端，负责请求 `/v2/market/*` 与 `/v2/account/*` 并把响应解析成 APP 侧统一载荷；当前也承接图表页按 `startTime/endTime` 的分页与增量补尾。`/v2/account/full` 已成为客户端强一致刷新唯一入口；`/v2/account/snapshot` 解析也已改为严格契约：缺失 `account` 对象直接报错，不再本地拼字段兜底。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2SessionClient.java)
  v2 会话客户端，负责请求 `/v2/session/*` 并解析 public-key、status、login/switch/logout 回执；当前也负责回拉 `/v2/session/diagnostic/*`，把服务端按 `requestId` 记录的登录时间线格式化成可直接展示的排障文本。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2StreamClient.java)
  v2 同步流客户端，负责连接 `/v2/stream` 并把统一同步消息解析成 APP 可消费的结构；当前已支持解析新的 `marketTick + marketSeq + market` 根级字段，同时继续兼容旧 `syncEvent/heartbeat`。
- [app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/remote/v2/GatewayV2TradeClient.java)
  v2 交易命令客户端，负责请求 `/v2/trade/check|submit|result`、第三阶段 `/v2/trade/batch/submit|result`，以及第四阶段 `/v2/trade/audit/recent|lookup`，并把响应解析成单笔/批量统一交易模型；batch payload 也会显式带出 `accountMode`，保证 `netting / hedging` 真值能一路透传到网关。
- [app/src/main/java/com/binance/monitor/security/SessionCredentialEncryptor.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/security/SessionCredentialEncryptor.java)
  APP 会话加密器，负责把账号、密码、服务器封装成 `rsa-oaep+aes-gcm` 登录信封；密码链路已改成 `char[]`，加密完成后会主动清零，避免明文 `String` 长时间停留在内存里。
- [app/src/main/java/com/binance/monitor/security/SecureSessionPrefs.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/security/SecureSessionPrefs.java)
  APP 安全会话偏好，负责用 Android Keystore 加密保存最近一次远程会话摘要和已保存账号列表缓存；logout 时只清当前激活账号，不清已保存账号摘要。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java)
  图表页旧入口兼容桥接页，当前启动后只负责把原始 extras 透传给主壳 `MARKET_CHART` Tab 并立即结束；应用内部主链已不再依赖它承载任何真实图表逻辑。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageRuntime.java)
  图表页页面运行时，负责把冷启动、进页、离页、自动刷新和倒计时这组宿主编排收口成统一入口；当前冷启动已改成“先 begin bootstrap，再恢复本地缓存，再继续远端校准”的单链，自动刷新也已改成先过 `MarketChartRevisionRefreshPolicy` gate，再决定是否真的回源拉 K 线。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java)
  图表页共享屏幕对象，负责把原先只留在旧 Activity 里的页面状态、刷新入口、图层恢复和指标/周期交互收口成可供 `MarketChartFragment` 复用的真实宿主主链；当前也承接顶部状态按钮、图表内快捷交易、右上角持仓监控摘要和异常标记，并已把实时尾部、叠加层摘要与对话区刷新一起接到 `ChartRefreshBudget / ChartRefreshScheduler` 的分区刷新链。实时尾巴 render token 现已显式包含 `close/high/low/volume/quoteAssetVolume`，确保同一分钟内即使价格不变、但量额或高低点变化，主图尾巴也会立即重绘。首帧可见性现在也由 `PageBootstrapStateMachine` 驱动：冷启动优先画内存/持久化 K 线，再用轻量“恢复中/同步中”提示覆盖旧的阻塞 loading；同时它也会显式记录当前图表已应用的 market-window 签名和时间，供自动刷新 gate 复用。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartRevisionRefreshPolicy.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartRevisionRefreshPolicy.java)
  图表页 revision 刷新策略，负责把“当前产品市场窗口是否前进”和“当前显示结果是否过期”收口成单一判断，避免 `autoRefreshRunnable` 继续到点就无条件回源。
- [app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/host/GlobalStatusBottomSheetController.java)
  全局状态弹层控制器，负责把连接状态、当前账户、同步状态、最近刷新时间和异常数量收口到统一底部弹层；第四阶段起也承接“交易追踪”入口。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartDataCoordinator.java)
  行情持仓页数据协调器，负责统一编排 `requestKlines / requestMoreHistory / observeRealtimeDisplayKlines / refreshChartOverlays / restoreChartOverlayFromLatestCacheOrEmpty`，把图表页重业务主链从旧 Activity 抽离成可复用宿主接口；当前实时尾部观察已改成通过 `MonitorRepository.selectDisplayKline(...)` 读取 selector 结果，不再直接把旧 map 当市场真值。冷启动、回前台、切产品/切周期和自动刷新现在也通过显式 `RequestReason` 分流，不再共用一套跳过/空态规则。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageHostDelegate.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartPageHostDelegate.java)
  行情持仓页宿主委托，负责把 `MarketChartPageController.Host` 从 Activity/Fragment 各自的匿名实现收口成统一适配层，为后续 Fragment 真实承接业务留出稳定宿主边界。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java)
  图表页交易弹窗协调器，负责收口图表页新开市价单、新增挂单、拖线改单、单笔平仓/撤单和复杂动作前的输入准备、结果展示与会话校验；当前新建交易默认手数已改为读取 `TradeSessionVolumeMemory`，不再依赖旧模板默认值。复杂动作仍先经 `TradeComplexActionPlanner` 展开成 `BatchTradePlan`，再交给 `BatchTradeCoordinator` 执行，并把 batch 结果按“总览 + 单项清单”展示给用户。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartStartupGate.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartStartupGate.java)
  图表启动门控，负责统一管理“主序列已提交 / 主图首帧已绘制”两个阶段；只有两者都成立后，才允许释放实时尾部和账户叠加层这类依赖主图的增量更新。
- [app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java)
  图表刷新策略辅助工具，负责根据本地窗口、实时分钟源新鲜度和显式请求原因决定 `SKIP / INCREMENTAL / FULL`；当前已把冷启动、恢复前台、切产品/切周期与自动刷新分成不同计划，不再让首帧恢复期误吃自动刷新跳过规则。
- [app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java)
  K 线绘制控件，负责主图、副图、指标、右侧留白、异常点胶囊、成本线和缩放交互；当前持仓、挂单、历史成交、异常记录都通过统一 annotation 明细链进入高亮详情弹窗。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java)
  图表叠加层快照工厂，负责把当前产品相关的持仓、挂单、历史成交整理成一次可绘制快照；当前签名已改成只看“当前产品 + 当前可见窗口 + 当前产品相关运行态内容”，不再被 `cache.updatedAt` 这类无意义变化反复触发整套重算；图表摘要文本也已开始直接读取统一产品运行态。
- [app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/state/UnifiedRuntimeSnapshotStore.java)
  统一运行态中心，负责把账户快照派生成产品级运行态，并按 `accountRevision / productRevision` 维护内存真值；当前除数量/盈亏外，也直接产出产品展示名称、紧凑名称和方向手数，供图表摘要、悬浮窗卡片和账户条目共用。产品级 selector 现已正式要求调用方显式带出 `account/server` 身份，只有请求身份与当前内存账户运行态完全匹配时才返回产品快照，避免切账户后旧运行态被新页面误读。
- [app/src/main/java/com/binance/monitor/runtime/revision/RuntimeRevisionCenter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/revision/RuntimeRevisionCenter.java)
  统一 revision 中心，负责把 `marketBase / marketWindow / accountRuntime / accountHistory / productRuntime` 五类版本号收口到同一入口，并提供按签名去重后的单调递增 revision。
- [app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapState.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapState.java)
  页面首帧状态枚举，负责把 `MEMORY_READY / STORAGE_RESTORING / LOCAL_READY_REMOTE_SYNCING / REMOTE_READY / TRUE_EMPTY` 这五种启动可见态固定成统一术语，避免各页再用“空列表/有数据/在加载”各自猜。
- [app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapSnapshot.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapSnapshot.java)
  页面首帧状态快照，负责显式表达“当前是否可渲染、是否显示恢复提示、是否是真正空态”，供图表页、账户页和分析页后续统一按状态画首帧。
- [app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapStateMachine.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/ui/PageBootstrapStateMachine.java)
  页面首帧状态机，负责锁定本地优先启动的合法流转，避免 `STORAGE_RESTORING -> 真空空态`、`LOCAL_READY_REMOTE_SYNCING -> TRUE_EMPTY`、`REMOTE_READY -> 本地回滚` 这类非法状态回退。
- [app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshBudget.java)
  图表刷新预算模型，负责把 `marketTickChanged / productRuntimeChanged / uiStateChanged / dialogStateChanged` 映射成 UI 区块级刷新范围。
- [app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/runtime/ChartRefreshScheduler.java)
  图表高频刷新调度器，负责把同一帧里的重复实时尾部、叠加层和摘要刷新都合并成最后一次有效请求。
- [app/src/main/java/com/binance/monitor/ui/chart/HistoricalTradeAnnotationBuilder.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/HistoricalTradeAnnotationBuilder.java)
  历史成交标注构建器，负责把历史成交映射到当前图表窗口；当前已从线性扫描改成基于 K 线窗口的二分定位，1 分钟图仍保留分钟桶对齐规则。
- [app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/KlineViewportHelper.java)
  图表视口计算工具，负责 K 线横向边界和右侧留白相关数学逻辑。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartRefreshMetaFormatter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartRefreshMetaFormatter.java)
  倒计时文案工具，负责拼接“剩余秒数/周期秒数 延迟ms”。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartPersistenceWindowHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartPersistenceWindowHelper.java)
  图表持久化窗口工具，负责在 Room 落库前剔除未闭合 latest patch，只保留闭合历史快照。
- [app/src/main/java/com/binance/monitor/ui/chart/ChartScaleGestureResolver.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/chart/ChartScaleGestureResolver.java)
  缩放方向判定工具，负责把双指手势分成横向、纵向和斜向整体缩放。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java)
  账户统计页旧入口兼容桥接页，当前普通旧入口仍会立即把原始 extras 透传给主壳 `ACCOUNT_STATS` Tab；需要直接打开完整分析深页时，真实运行路径已改由 `AccountStatsScreen` 承接，`pageRuntime.Host` 也不再保留真实页面兜底分支，旧字段和旧方法暂时只用于兼容旧专项链路和源码测试。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java)
  账户统计页共享屏幕对象，负责把原先只留在旧 Activity 里的页面状态、历史刷新协调、收益表渲染和登录会话交互收口成可供 `AccountStatsFragment` 和桥接深页共同复用的真实宿主主链；当前也承接“结构分析 / 历史成交”深页目标区块自动滚动，并已接入 `PageBootstrapStateMachine` 处理“本地恢复中 / 本地已就绪远端同步 / 真正空态”的首帧状态。
- [app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java)
  当前持仓单项 adapter，负责持仓列表的折叠/展开显示、操作按钮和单行摘要；现已开始读取 `UnifiedRuntimeSnapshotStore`，并仅在“同产品只有 1 条当前持仓”时复用统一运行态的产品手数/盈亏口径。读取前会先接收页面传入的当前 `account/server` 身份，只消费同一会话下的产品运行态。
- [app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java)
  挂单单项 adapter，负责挂单列表的折叠/展开显示、改单/撤单入口和整数折叠价展示；现已开始读取 `UnifiedRuntimeSnapshotStore` 做产品级统一识别，但继续保留单笔挂单价格和手数语义，不把产品聚合摘要误覆盖到单项上。读取前同样要先接收页面传入的当前 `account/server` 身份，只消费同一会话下的产品运行态。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsRenderCoordinator.java)
  账户统计页渲染协调器，负责统一编排 `applySnapshot`、次级区块 deferred render、交易统计和交易记录筛选，把账户统计页的大块渲染主链从旧 Activity 抽离成可复用宿主接口。
- [app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageHostDelegate.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountStatsPageHostDelegate.java)
  账户统计页宿主委托，负责把 `AccountStatsPageController.Host` 从 Activity/Fragment 各自的匿名实现收口成统一适配层，为后续 Fragment 真实承接业务留出稳定宿主边界。
- [app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java)
  账户持仓页入口，负责承接账户概览、当前持仓和挂单三段内容；页面只消费 `AccountStatsPreloadManager.Cache` 的单一快照，并按概览 / 持仓 / 挂单三段独立刷新。
- [app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPositionPageController.java)
  账户持仓页真实控制器，负责把页面首帧 bootstrap、后台存储恢复、前台快照请求和三段区块差异刷新串成一条主链；当前已显式区分 `restoring` 与 `true empty`，避免本地恢复期间先把账户页画成空模型。最新交易重构后，这里也已直接接入 `TradeExecutionCoordinator / BatchTradeCoordinator / TradeBatchActionDialogCoordinator`，负责账户页原地平仓、改单、撤单和正式批量操作入口，不再跳图表页中转。
- [app/src/main/java/com/binance/monitor/ui/account/AccountPositionUiModelFactory.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPositionUiModelFactory.java)
  账户持仓页展示模型工厂，负责把正式 `AccountStatsPreloadManager.Cache` 直接整理成页面只读展示模型，把排序、摘要拼接和空态文案都前置到后台阶段；旧的 `AccountRuntimePayload / AccountRuntimeSnapshotStore` 中转层已删除，账户页不再维护第二套页面运行态模型。
- [app/src/main/java/com/binance/monitor/ui/account/AccountPositionSectionDiff.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPositionSectionDiff.java)
  账户持仓页分段差异比较器，负责按真实展示字段判断概览、持仓、挂单三段是否变化，避免止盈止损、挂单价、库存费变化时漏刷对应区块。
- [app/src/main/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelper.java)
  账户总览指标组装工具，负责把服务端 overview、当前持仓、历史成交和净值曲线整理成固定顺序的账户总览列表；当前主要由账户持仓页展示模型工厂复用，并已通过 `IndicatorRegistry + IndicatorFormatterCenter` 输出正式指标名与统一格式。
- [app/src/main/java/com/binance/monitor/ui/rules/IndicatorRegistry.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/rules/IndicatorRegistry.java)
  全局指标注册表，负责维护正式指标 ID、正式显示名、值类型、精度、颜色规则和历史别名映射，页面后续不得再私有定义指标标题。
- [app/src/main/java/com/binance/monitor/ui/rules/IndicatorFormatterCenter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/rules/IndicatorFormatterCenter.java)
  全局格式中心，负责统一金额、百分比、数量、价格和紧凑金额展示，页面不再直接拼字符串。
- [app/src/main/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicy.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/rules/IndicatorPresentationPolicy.java)
  全局展示策略，负责把指标定义、原始值、打码规则和红绿规则组装成页面可直接消费的结果，并输出统一的 span 着色入口。
- [app/src/main/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistry.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/rules/ContainerSurfaceRegistry.java)
  模块容器注册表，负责把 `page / section / row / field / overlay / floating` 这 6 类容器角色映射到 `UiPaletteManager` 的正式外壳入口。
- [app/src/main/java/com/binance/monitor/ui/account/AccountDeferredSnapshotRenderHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountDeferredSnapshotRenderHelper.java)
  账户页次级区块后台计算助手，负责把交易统计、交易筛选和曲线投影整理成可直接绑定的结果；当前保留在 `ui.account` 包内，因为它依赖账户页内部包级工具，不为整理目录而扩大可见性。
- [app/src/main/java/com/binance/monitor/ui/account/AccountOverviewCumulativeMetricsCalculator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountOverviewCumulativeMetricsCalculator.java)
  账户概览累计指标真值辅助工具，负责判断当前是否拥有足够真值来覆盖 `累计盈亏 / 累计收益率`；优先净值曲线，其次历史成交 + 当前持仓，仅有当前持仓时不输出累计指标。
- [app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountRemoteSessionCoordinator.java)
  远程账号会话协调器，负责把公钥获取、加密登录、已保存账号切换、退出和切换后缓存清理串成单条主链；当前状态语义已收口成“接口 accepted 先进入 syncing，再在轻量身份确认完成后进入 `FULL_SYNCING`，只有登录后的后台 full 补全稳定后才回到 active”。
- [app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java)
  账户登录弹窗控制器，负责独立登录/切换/退出交互、会话 accepted 收口，以及失败时回拉服务端诊断时间线并展示可停留的失败对话框，避免用户只能看到一闪而过的错误提示。
- [app/src/main/java/com/binance/monitor/ui/account/session/AccountSessionRestoreHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/session/AccountSessionRestoreHelper.java)
  账户页会话恢复助手，负责把安全会话摘要和本地 UI 偏好合成为页面初始化状态；当前已独立放到 `ui.account.session`，避免账户页 Activity 自己拼装会话恢复细节。
- [app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountSessionStateMachine.java)
  远程账号会话状态机，负责明确区分 `encrypting/submitting/switching/syncing/active/failed`。
- [app/src/main/java/com/binance/monitor/ui/account/AccountCurvePointNormalizer.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountCurvePointNormalizer.java)
  账户曲线归一化工具，只负责过滤无效点、排序和按时间戳去重，不再本地补点、补值或补仓位比例。
- [app/src/main/java/com/binance/monitor/ui/account/AccountCurveHighlightHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountCurveHighlightHelper.java)
  账户曲线共享高亮工具，负责把附图长按位置换算成统一时间点，再回查主图、回撤和日收益当前值。
- [app/src/main/java/com/binance/monitor/ui/account/AccountConnectionTransitionHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountConnectionTransitionHelper.java)
  账户连接状态过渡工具，负责判断何时展示一次性的登录成功提示。
- [app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java)
  净值/结余曲线控件，负责绘制账户曲线，并用高对比样式标记当前周期下的最大回撤区间。
- [app/src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java)
  账户统计分析工具，负责从曲线与交易记录中计算最大回撤、回撤附图、日收益率、交易散点和持仓时间分布。
- [app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java)
  回撤附图控件，负责绘制基于净值序列的回撤曲线。
- [app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java)
  日收益率附图控件，负责绘制正负日收益柱状分布。
- [app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java)
  历史交易分布图控件，负责绘制“最大回撤 vs 收益率”散点。
- [app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java)
  持仓时间分布图控件，负责绘制不同持仓时长桶的交易数量。
- [app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java)
  账户预加载管理器，负责承接服务端已发布的账户运行态并管理历史补拉；当前主链已经固定成 5 个入口：`applyPublishedAccountRuntime(...)`、`refreshHistoryForRevision(...)`、`fetchSnapshotForUi()`、`fetchSnapshotForUiForIdentity(...)`、`fetchFullForUi(...)`。运行态只消费 `v2/stream.accountRuntime`，只有在 `historyRevision` 变化时才补拉 `v2/account/history`；登录成功收口现在先走轻量 `snapshot` 身份确认，显式强刷和登录后的后台补全才走 `/v2/account/full`，不再把 full 当成前台登录成功前提，也不再本地回填 `open/close time`、`open/close price` 或秒级时间戳；运行态连接状态只认完整账号身份，`remote_logged_out` 会明确收口为未连接。应用启动阶段还会异步预热本地已持久化账户快照，并通过统一 `updateLatestCache(...)` 链把内存缓存、统一运行态和页面监听器一起提前唤醒。
- [app/src/main/java/com/binance/monitor/ui/account/AccountPreloadPolicyHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/account/AccountPreloadPolicyHelper.java)
  账户预加载节奏辅助工具，负责把前后台状态和全量/轻量模式转换成统一刷新间隔。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java)
  悬浮窗管理器，负责渲染顶部合并盈亏、连接状态、分产品卡片，并处理长按拖动、最小化、点击还原；销毁时会显式切断 handler 回调、最小化重排任务和 view 引用，不再只靠窗口隐藏。
- [config/checkstyle/audit-critical.xml](/E:/Github/BTCXAU_Monitoring_and_Push_APK/config/checkstyle/audit-critical.xml)
  审计关键链路静态检查配置，负责给本轮整改热点加上低误报 Checkstyle 护栏。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowLayoutHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowLayoutHelper.java)
  悬浮窗布局辅助工具，负责统一展开宽度、内容列宽和产品卡片文本对齐方式。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowTextFormatter.java)
  悬浮窗文案辅助工具，负责统一“产品（盈亏）”标题，并处理零盈亏的 `$-` 展示规则；当前金额、价格、手数与紧凑金额已统一改为通过 `IndicatorFormatterCenter` 输出。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowSnapshot.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowSnapshot.java)
  悬浮窗统一快照模型，保证连接状态、总盈亏和产品卡片在同一次渲染里一起更新。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingSymbolCardData.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingSymbolCardData.java)
  悬浮窗产品卡片数据模型，承载产品名、盈亏、最新价、成交量、成交额。
- [app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java)
  悬浮窗持仓聚合器，负责把 MT5 持仓按产品汇总成卡片级别数据；当统一运行态存在时，优先直接消费产品快照里的紧凑名称、方向手数和盈亏口径，不再重复本地判断。
- [app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java)
  设置首页目录，负责显示设置分类入口；交易设置当前已收口为低频项，只保留一键模式入口，不再暴露模板、默认参数或多组风控阈值。
- [app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java)
  设置二级页，负责显示悬浮窗、固定公网入口、交易设置、缓存管理等具体设置，并去掉重复隐私入口；网关项当前只读展示正式入口，交易设置卡片现已收口为“会话默认手数说明 + 一键模式开关”，不再提供模板管理、默认参数编辑和旧风控阈值输入。
- [app/src/main/java/com/binance/monitor/data/local/ConfigManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/ConfigManager.java)
  本地配置中心，负责保存悬浮窗、Tab、监控开关和交易一键模式等持久化真值；主题当前已收口为单一默认主题，仅保留历史字段兼容，不再提供切换入口。当前 MT5 网关入口也已固定为唯一 HTTPS 入口 `https://tradeapp.ltd`，不再接受运行时改写。
- [app/src/main/java/com/binance/monitor/ui/trade/TradeSessionVolumeMemory.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeSessionVolumeMemory.java)
  交易会话手数记忆器，负责把“冷启动默认 `0.05` 手、会话内沿用最近一次成功提交手数、重启后恢复 `0.05`”收口成唯一真值；图表页快捷交易、交易弹窗、单笔/批量成功回写都统一经过这里。
- [app/src/main/java/com/binance/monitor/ui/trade/TradeBatchActionDialogCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeBatchActionDialogCoordinator.java)
  批量交易交互协调器，负责把图表页与账户页的“批量操作”入口统一成同一条对话链和提交链；当前正式支持批量平仓、批量撤销挂单、批量修改挂单、批量修改多笔持仓 `TP/SL`，并按一键模式决定关闭类动作是否免确认。
- [app/src/main/java/com/binance/monitor/ui/trade/TradeTemplateRepository.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeTemplateRepository.java)
  交易模板仓库，负责承接旧模板数据结构与历史兼容字段；当前新建交易与设置页正式链路已不再依赖它作为用户可见真值。
- [app/src/main/java/com/binance/monitor/ui/trade/TradeAuditStore.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeAuditStore.java)
  客户端交易审计存储，负责记录本机发起交易的关键阶段、本地提示文案和 `requestId / batchId`，作为交易追踪页的最近记录来源。
- [app/src/main/java/com/binance/monitor/ui/trade/TradeAuditActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeAuditActivity.java)
  交易追踪与回放页，负责展示 recent list、按 `requestId / batchId` 查询，以及“结果清单 + 时间线”排障视图。
- [app/src/main/java/com/binance/monitor/ui/trade/TradeRiskGuard.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeRiskGuard.java)
  交易风控中心，负责统一判定单笔与批量交易是否具备基本可提交条件，并把“一键模式是否允许免确认”收口到同一处；旧模板阈值、批量规模阈值和复杂强制确认规则已从用户功能里移除。
- [app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeExecutionCoordinator.java)
  交易执行协调器，负责串起预检查、提交、轮询结果和强刷确认；当前新增 `ACCEPTED_AWAITING_SYNC` 状态，用来表达“服务端已受理，但账户真值还在同步中”。第四阶段起单笔链也会写本地审计，并在真正提交前再次经过 `TradeRiskGuard` 硬拦截。
- [app/src/main/java/com/binance/monitor/ui/trade/BatchTradeCoordinator.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/BatchTradeCoordinator.java)
  批量交易协调器，负责把正式 batch submit/result、交易后账户强刷，以及本地显示文案回填收在正式边界里，避免页面层自己循环调单笔接口；页面提交前的账户模式真值也通过这里前后的 `BatchTradePlan / BatchTradeReceipt` 继续保持一致。当前图表页与账户页共用这条链，若批量计划中包含新增开仓/挂单，也会在成功后同步回写 `TradeSessionVolumeMemory`。
- [app/src/main/java/com/binance/monitor/ui/trade/TradeComplexActionPlanner.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/TradeComplexActionPlanner.java)
  复杂交易规划器，负责把“部分平仓 / 批量平仓 / 加仓 / 反手 / Close By”统一展开成 `BatchTradePlan`；`netting / hedging` 的差异也只允许在这里归一，不再散落到页面层分支。
- [app/src/main/java/com/binance/monitor/ui/trade/BatchTradeResultFormatter.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/trade/BatchTradeResultFormatter.java)
  批量交易结果格式化器，负责把整批状态和单项结果转成“总览 + 清单”文案，供图表页复杂交易结果弹窗复用。
- [app/src/main/java/com/binance/monitor/runtime/AppForegroundTracker.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/runtime/AppForegroundTracker.java)
  应用前后台状态跟踪器，负责给服务层和预加载层提供统一的前后台切换信号。
- [app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java)
  主题与控件样式管理器，负责页面底色、底部导航、标准主体运行时样式、弹窗/抽屉外壳和整体深色终端风格；当前已收口为单一默认主题，并继续作为背景、按钮、弹窗、抽屉、菜单、边框和图表指标色的统一配色入口。运行时交互控件只允许通过 `styleActionButton / styleTextTrigger / styleSegmentedOption / styleSelectFieldLabel / styleInputField / styleToggleChoice / applyPickerWheelTextStyle` 这组标准主体入口设置，不再在页面侧手写背景、高度、字号和文字色。新增 `createSurfaceForRole(...)` 后，页面和弹层容器开始通过 `ContainerSurfaceRegistry` 走正式角色外壳，而不是继续散落页面私有背景分支。
- [app/src/main/res/values/dimens.xml](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/res/values/dimens.xml)
  全局尺寸资源真值文件，负责维护 `2 / 4 / 8 / 12 / 16 / 24dp` 基础阶梯、页面级语义 spacing token，以及悬浮窗、曲线图、K 线文本、滚动条这类组件几何 token；活跃页面不再直接引用已删除的 legacy spacing 名。
- [app/src/main/res/values/styles.xml](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/res/values/styles.xml)
  标准主体资源真值文件，负责定义 7 类标准主体（`Widget.BinanceMonitor.Subject.*`）、共享 `TextAppearance`、全局 lineHeight 合同与标准主体 padding；旧 `Button / TextAction / Spinner / TextInputLayout` 包装样式已退出活跃主链，不再作为扩展入口。
- [app/src/main/res/values/themes.xml](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/res/values/themes.xml)
  默认主题入口，负责把 `materialButtonStyle / materialButtonOutlinedStyle / textInputStyle` 等系统控件默认映射到标准主体体系，避免页面再从主题层长出第二套按钮或输入框语言。
- [app/src/main/java/com/binance/monitor/ui/theme/SpacingTokenResolver.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/theme/SpacingTokenResolver.java)
  全局尺寸解析器，负责把 `dimen` token 统一解析成运行时 `px / dp float`，是 Java 动态布局、helper 几何值和自定义 View 读取尺寸的唯一正式入口。
- [app/src/main/java/com/binance/monitor/ui/theme/TextAppearanceScaleResolver.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/theme/TextAppearanceScaleResolver.java)
  字号样式解析器，负责从共享 `TextAppearance` 读取统一字号，并把它转换给运行时 `TextView`、`Paint` 和 NumberPicker 内部输入框复用，避免图表、自定义 View 和动态控件再次各自硬编码文字尺寸。
- [app/src/main/java/com/binance/monitor/util/GatewayUrlResolver.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/util/GatewayUrlResolver.java)
  网关地址标准化工具，把用户输入整理成统一可用的基础地址。
- [app/src/main/java/com/binance/monitor/util/NotificationHelper.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/util/NotificationHelper.java)
  通知帮助类，负责前台服务通知和异常交易通知；当前通知点击已直接收口到主壳 `MARKET_MONITOR` Tab。
- [app/src/main/java/com/binance/monitor/util/ChainLatencyTracer.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/util/ChainLatencyTracer.java)
  图表/悬浮窗链路延迟追踪工具，负责在需要时记录 stream、抓取与渲染时序；当前默认关闭，避免前期调试日志继续干扰主链性能。
- [app/src/main/java/com/binance/monitor/ui/launch/OverlayLaunchBridgeActivity.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/ui/launch/OverlayLaunchBridgeActivity.java)
  悬浮窗点击桥接页，负责把悬浮窗点击统一转成主壳目标 Tab 路由；当前图表目标会把 `symbol` 等参数直接透传给主壳 `MARKET_CHART` Tab。
- [app/src/main/java/com/binance/monitor/data/local/db/](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/db)
  Room 数据库层，负责历史 K 线、历史交易、账户摘要、持仓快照、挂单快照；当前账户缓存已改为按 `account + server` 分区，`account_snapshot_meta` 保存多身份摘要行，其余表通过稳定身份前缀隔离不同账户的数据。
- [app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java](/E:/Github/BTCXAU_Monitoring_and_Push_APK/app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java)
  图表历史仓库，负责把上层已经整理好的 K 线窗口直接写入 Room，不再重复回读整段旧历史再合并。
- [bridge/mt5_gateway/server_v2.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/server_v2.py)
  MT5 网关服务，负责 MT5 数据整理和 Binance REST / WebSocket 转发；当前也承载 `v2` 行情、账户、stream 输出，以及远程账号会话接口与运行时缓存清理。轻快照主链现在只读取账户概览、持仓、挂单和 `accountMeta.historyRevision`（成交历史 canonical digest），不再展开完整历史对象，并增加 single-flight + 会话代次保护（`session_snapshot_epoch`），避免高频消费链并发放大或旧会话结果回写；`/v2/account/full` 现已提供“运行态 + 历史真值”原子强刷，`/v2/account/snapshot` 与 `/v2/market/snapshot` 也都会先看远程会话状态，只有存在激活账号时才读取 MT5 轻快照，`logged_out` 时统一返回标准空账户快照；`/v2/account/history` 的曲线和交易列表继续复用同一份 MT5 成交历史；账户发布链已从 `v2_sync_state + v2_bus_state` 收口到单一 `account_publish_state`，市场链则新增“Binance `@trade` -> 服务端本地 `1m latestPatch/latestClosedCandle` 聚合 -> `0.5s marketTick` 单层合批 + websocket 订阅者事件驱动发送”的正式机制，空闲时只发 heartbeat，不再让客户端再套一层固定拍点睡眠，同时 `marketTick` 热路径也不再在 runtime 缺 patch 时回退 REST 当前分钟。远程账号新登录主链现在固定为独立 `v2_mt5_account_switch.py`：检测 MT5 GUI 主窗口、必要时按 `MT5_PATH` 拉起、附着重试、读取基线账号、强制 `mt5.login(...)` 切号，再在 30 秒内轮询真实账号 `login` 是否切到目标账号；`active_session / saved profile / .env / current terminal / probe / reset terminal` 不再作为 `/v2/session/login` 的切号前置决策。第四阶段起单笔/batch 的 check、submit、result 也会把审计阶段写入 `v2_trade_audit.py`，并开放 recent / lookup 查询。现在还额外维护 `gateway_runtime_status`，把 `v2/stream` 客户端连接、最近会话动作、最近交易动作和最近客户端来源收口到 `/internal/runtime/status`，供 Windows 长期状态面板直接消费，不再靠日志猜测手机 APP 是否还在交互。
- [bridge/mt5_gateway/v2_trade_audit.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_trade_audit.py)
  服务端交易审计缓存，负责把单笔与 batch 的 check、submit、result 时间线收口成最近记录与按 id 查询入口，作为客户端排障页的服务端事实来源。
- [bridge/mt5_gateway/v2_session_crypto.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_crypto.py)
  远程账号会话加密模块，负责登录信封公钥生成、`rsa-oaep+aes-gcm` 解密、时间戳校验和 nonce 去重。
- [bridge/mt5_gateway/v2_session_models.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_models.py)
  远程账号会话领域模型，负责统一定义账号摘要、公钥载荷、状态载荷、动作回执和登录信封结构，避免服务端继续散落裸字典。
- [bridge/mt5_gateway/v2_session_manager.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_manager.py)
  远程账号会话管理器，负责新账号登录、已保存账号切换、退出、异常回滚、审计记录和强一致刷新收口。
- [bridge/mt5_gateway/v2_session_diagnostic.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_diagnostic.py)
  会话诊断缓存，负责把 `/v2/session/login`、`/v2/session/switch` 及登录探针各阶段按 `requestId` 收口成可查询时间线，供 `latest / lookup` 接口和手机端失败提示复用；显式切号失败当前只允许落到 `window_not_found_then_launch_failed / window_not_found_then_window_ready_timeout / attach_failed / baseline_account_read_failed / switch_call_exception / final_account_read_failed / switch_timeout_account_not_changed` 这 7 个真实阶段。
- [bridge/mt5_gateway/mt5_login_probe.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/mt5_login_probe.py)
  MT5 独立登录探针子进程，负责在不拖死主进程的前提下执行基础初始化、当前终端身份读取、现有会话复用判断、authenticated initialize / legacy login 与 canonical identity 确认，并把每一步阶段轨迹回传给服务端诊断缓存。
- [bridge/mt5_gateway/v2_session_store.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_session_store.py)
  远程账号会话存储层，负责当前激活账号摘要和已保存账号密文档案的文件读写。
- [bridge/mt5_gateway/admin_panel.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel.py)
  统一服务器控制台服务，负责聚合总览状态、输出中文诊断、提供配置 schema / 变更影响、读取日志、编辑 `.env`、代理异常规则配置，并管理网关 / MT5 / Caddy / Nginx；当前也会透传账号会话摘要。
- [bridge/mt5_gateway/admin_panel_state.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel_state.py)
  控制台总览状态构造器，负责把首页卡片、主动作、最近日志和旧字段兼容收口成统一响应。
- [bridge/mt5_gateway/admin_panel_diagnostics.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel_diagnostics.py)
  控制台诊断构造器，负责把本机网关、公网入口、EA 心跳和代理状态翻译成中文结论与建议动作。
- [bridge/mt5_gateway/admin_panel_config.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/admin_panel_config.py)
  控制台配置元数据与影响分析工具，负责输出字段分组 schema，并判断保存后哪些组件需要重启。
- [bridge/mt5_gateway/v2_market.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_market.py)
  v2 行情模型工具，负责把 Binance K 线原始数据整理成闭合 candles 与 latestPatch，并能从 REST 窗口里拆出最后一根未闭合 patch；服务端 trade 主链聚合出的本地 `1m` patch 也会复用这里的标准 candle payload 结构，保证 `snapshot / marketTick / candles` 三条输出口径一致。
- [bridge/mt5_gateway/v2_market_runtime.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_market_runtime.py)
  市场运行时聚合器，负责维护 Binance 市场 WS 的连接状态、冷启动 REST 底稿、最近闭合 `1m`、当前未闭合 `1m latestPatch`，以及基于本地 `1m` 真值聚合出来的 `5m~1d` 最新未闭合 patch；当前正式主链已经从 `@kline_1m` 切到 `@trade`，由服务端按 trade 本地重建当前分钟 K 线，再供 `/v2/market/snapshot`、`marketTick` 与 `/v2/market/candles` 统一复用。
- [bridge/mt5_gateway/v2_account.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/v2_account.py)
  v2 账户模型工具，负责把账户快照、历史成交、曲线转换成 APP 侧展示模型。
- [bridge/mt5_gateway/start_gateway.ps1](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/start_gateway.ps1)
  网关启动脚本，负责加载 `.env`、比较 `requirements.txt` 指纹并使用本地虚拟环境启动服务；当前文件指纹改为脚本内置的 .NET `SHA256`，不再依赖 `Get-FileHash`。
- [bridge/mt5_gateway/start_admin_panel.ps1](/E:/Github/BTCXAU_Monitoring_and_Push_APK/bridge/mt5_gateway/start_admin_panel.ps1)
  管理面板启动脚本，负责加载 `.env`、比较 `requirements.txt` 指纹并使用本地虚拟环境启动轻量 Web 面板；当前文件指纹同样改为脚本内置的 .NET `SHA256`。
- [deploy/tencent/windows/](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows)
  Windows 部署脚本源码目录，负责启动、自启注册、健康检查和 Caddy 配置；现在同一套脚本同时兼容“仓库根目录”和“部署包根目录”两种布局，首次引导脚本也已改成脚本内置 .NET `SHA256` 指纹计算。
- [deploy/tencent/windows/deploy_bundle.ps1](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows/deploy_bundle.ps1)
  Windows 一键重部署入口，负责按“停旧服务与占口 -> 校验脚本 -> 初始化环境 -> 注册任务 -> 启动后台服务 -> 健康检查”的顺序执行，并把状态同步到唯一的 GUI 窗口；这个窗口现在正式命名为 `MT5 部署与连接状态`，部署完成后不会退出，而是继续长期显示本地部署、行情、账户和手机 APP 交互状态。健康检查现在还会校验 `bundleFingerprint`、`wss://tradeapp.ltd/v2/stream` 首条消息，以及本机 `8787 /v2/account/full (diagnostic)` 诊断项，用来把“轻链路正常”和“full 全量链异常”明确区分开。
- [deploy/tencent/windows/deploy_bundle.cmd](/E:/Github/BTCXAU_Monitoring_and_Push_APK/deploy/tencent/windows/deploy_bundle.cmd)
  Windows 双击部署入口，负责以隐藏方式拉起 `deploy_bundle.ps1` 的 GUI 模式，避免额外弹出命令行窗口。
- [scripts/build_windows_server_bundle.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/scripts/build_windows_server_bundle.py)
  Windows 部署包构建脚本，负责从 `bridge/mt5_gateway` 与 `deploy/tencent/windows` 生成唯一上传目录 `dist/windows_server_bundle`，并把根目录双击部署脚本、bundle README、完整会话链文件、`tzdata` 所在依赖清单和 `bundle_manifest.json` 运行指纹一起放进部署包。
- [scripts/check_windows_server_bundle.py](/E:/Github/BTCXAU_Monitoring_and_Push_APK/scripts/check_windows_server_bundle.py)
  Windows 部署前一键核对脚本，负责临时重建期望 bundle，并与当前 `dist/windows_server_bundle` 的实际文件指纹做差异比对；默认还会继续核对 `https://tradeapp.ltd/v1/source` 返回的 `bundleFingerprint`，直接判断服务器当前部署版本是否最新；传 `--skip-remote` 时才跳过线上检查。

## 模块之间的调用关系

- `MainHostActivity` -> `HostTabNavigator` -> `MarketMonitorFragment / MarketChartFragment / AccountStatsFragment / AccountPositionFragment / SettingsFragment`
  主壳根据目标 Tab 统一创建或复用常驻页面实例，不再通过底部 Tab 互相 `startActivity(...)`。
- `MainActivity` -> `HostNavigationIntentFactory` -> `MainHostActivity`
  旧行情监控入口已退成桥接，默认直接跳到主壳 `MARKET_MONITOR` Tab。
- `MonitorService` -> `FallbackKlineSocketManager`
  fallback 行情流现在只保留观测用途，不再直接写主监控页和悬浮窗真值。
- `MonitorService` -> `GatewayV2StreamClient` -> `server_v2.py /v2/stream`
  消费统一同步消息，先把 APP 的同步入口收口到 `v2 stream`。
- `MonitorService` -> `V2StreamRefreshPlanner`
  根据 `v2 stream` 消息内容决定当前是应用市场运行态、应用账户运行态、按 `historyRevision` 补历史，还是只刷新悬浮窗。
- `MonitorService` -> `service.stream.V2StreamSequenceGuard`
  先按 `busSeq` 判断当前消息是否仍属于当前有效序列，只有通过顺序守卫且成功应用的 stream 消息才会提交序列进度。
- `MainHostActivity` / `MainActivity` / `MarketChartActivity` / `AccountStatsBridgeActivity` / `SettingsSectionActivity` / `BootCompletedReceiver` -> `MonitorServiceController`
  服务启动和动作分发统一走控制器，不再由各入口自己手写 `Intent + startForegroundService`。
- `MonitorService` -> `service.account.AccountHistoryRefreshGate`
  账户历史补拉进入前先走 gate，结束后再由 gate 决定是否续跑到最新 revision。
- `MonitorService` -> `GatewayV2Client`
  图表页按需读取 `v2/market/candles`，账户侧只在 `historyRevision` 前进时补拉 `v2/account/history`；运行态不再补拉 `v2/account/snapshot`，fallback 流也不再驱动主链补拉。
- `MonitorService` -> `MonitorRepository`
  先写入 `MarketRuntimeStore`，再把 `SymbolMarketWindow` 同步推入 `MarketTruthCenter`，由仓库统一发布 `MarketTruthSnapshot` 给图表与悬浮窗。
- `MonitorRepository` -> `MarketTruthCenter` -> `MinuteBaseStore / IntervalProjectionStore / GapDetector`
  市场统一出口先把 stream 与 REST 输入合并到真值中心，再由分钟底稿、高周期投影和缺口判断产出唯一读模型。
- `AppForegroundTracker` -> `MonitorService` / `MonitorRuntimePolicyHelper`
  统一把应用前后台状态传给服务调度层，决定当前应使用哪一档刷新节奏。
- `MonitorService` -> `AccountStatsPreloadManager` -> `AccountStorageRepository`
  先应用 stream 下发的账户运行态，再按 `historyRevision` 条件补拉历史并统一落本地库；显式强刷统一走 `/v2/account/full`，不再自己拼 snapshot/history。
- `MonitorService` -> `FloatingPositionAggregator` + `FloatingWindowSnapshot` + `FloatingWindowManager`
  把行情和持仓聚合成统一快照，再一次性刷新悬浮窗。
- `MonitorService` -> `MonitorFloatingCoordinator` -> `FloatingWindowManager`
  悬浮窗的刷新节流、快照拼装和销毁清理都统一收口到协调器，服务层不再自己散落悬浮窗生命周期逻辑。
- `MonitorFloatingCoordinator` -> `MonitorRepository.selectLatestPrice / selectClosedMinute`
  悬浮窗最新价和 1 分钟量额都统一经仓库 selector 读取同一份市场真值，不再单独直连图表缓存或 REST 行情链。
- `MarketChartFragment` / `MarketChartScreen` -> `ChartHistoryRepository` -> Room
  先读本地历史，再按需补网络数据；落库时直接 upsert 当前整理好的窗口，不再回读整段旧历史。
- `MarketChartScreen` -> `MarketChartDataCoordinator`
  图表页请求、补页、实时观察和叠加层恢复主编排统一先进入数据协调器，再由宿主回调执行底层抓取与最终落图。
- `MarketChartDataCoordinator` / `MarketChartScreen` -> `MonitorRepository.selectDisplaySeries(...)`
  图表主请求、实时尾巴和左滑补页在 REST 回包后都会优先回读统一市场真值，不再直接拿原始 REST K 线作为最终显示结果。
- `MarketChartFragment` -> `MarketChartScreen` -> `MarketChartDataCoordinator`
  主壳内的图表页现在直接复用共享屏幕对象与数据协调器，不再停留在空宿主回调阶段。
- `MarketChartScreen` -> `ChartPersistenceWindowHelper` -> `ChartHistoryRepository`
  最新窗口在持久化前先剔除未闭合 patch，避免 Room 把活 K 线当成历史真值恢复。
- `MarketChartScreen` -> `GatewayV2Client` -> `server_v2.py /v2/market/candles`
  图表页最终真值、左滑历史分页、以及增量补尾都从服务端读取闭合 candles 与 latestPatch，本地只负责快照与展示。
- `MarketChartScreen` -> `ChartOverlaySnapshotFactory` -> `HistoricalTradeAnnotationBuilder`
  图表叠加层按当前产品和当前窗口构建快照，历史成交锚点通过二分定位快速映射到当前可见 K 线。
- `MarketChartScreen` -> `ChartRefreshMetaFormatter`
  把自动刷新倒计时和最近一次成功请求延迟整理成统一文案。
- `KlineChartView` -> `ChartScaleGestureResolver`
  根据双指跨度变化决定走横向、纵向还是整体缩放。
- `AccountStatsPreloadManager` -> `GatewayV2Client` -> `server_v2.py /v2/account/*`
  账户运行态直接消费 `v2/stream.accountRuntime`；只有在 `historyRevision` 前进时，才补拉 `v2/account/history`，并原子替换本地历史缓存；页面显式强刷则直接请求 `/v2/account/full`；若内存缓存为空，则回退到本地已持久化 `historyRevision` 判断是否真的需要全量补拉。
- `AppForegroundTracker` -> `AccountPreloadPolicyHelper` -> `AccountStatsPreloadManager`
  统一把应用前后台状态转换成账户预加载节奏，避免旧常量分散在多个入口里。
- `AccountStatsBridgeActivity` -> `AccountStatsPreloadManager`
  主动刷新时统一复用 `v2` 优先抓取逻辑，不再由页面自己直连旧网关。
- `AccountStatsBridgeActivity` -> `AccountStatsRenderCoordinator`
  账户统计页快照落地、deferred secondary render、交易统计和交易记录筛选统一先进入渲染协调器，再由宿主回调执行最终绑定。
- `AccountStatsFragment` -> `AccountStatsScreen` -> `AccountStatsRenderCoordinator` / `AccountSnapshotRefreshCoordinator`
  主壳内的账户统计页现在直接复用共享屏幕对象与两条协调器主链，不再停留在空宿主回调阶段。
- `AccountStatsBridgeActivity` -> `AccountOverviewCumulativeMetricsCalculator`
  账户概览渲染前先判断是否已具备累计指标真值；只有曲线或历史成交侧能证明累计真值时，才覆盖 `累计盈亏 / 累计收益率`。
- `AccountStatsBridgeActivity` -> `AccountRemoteSessionCoordinator` -> `GatewayV2SessionClient`
  账户页远程会话入口统一先拉公钥或读取状态，再执行登录、切换、退出，避免页面自己拼会话接口。
- `AccountStatsBridgeActivity` -> `ui.account.session.AccountSessionRestoreHelper`
  页面初始化时先把安全会话摘要和本地 UI 偏好合成为统一恢复结果，再驱动登录态和默认输入框。
- `AccountRemoteSessionCoordinator` -> `SessionCredentialEncryptor`
  登录新账号时先在 APP 本地生成 `rsa-oaep+aes-gcm` 登录信封，再把密文提交给服务端；密码只在 `char[]` 链路里短暂存在，加密后立即清零。
- `AccountRemoteSessionCoordinator` -> `SecureSessionPrefs`
  会话链路每次切换后都会把当前激活账号和已保存账号列表缓存到 Android Keystore 加密存储；只有快照真正对齐后的 active 账号才会写入激活状态。
- `GatewayV2SessionClient` -> `server_v2.py /v2/session/*` -> `v2_session_manager.py`
  远程账号会话统一由服务端作为唯一 MT5 执行主体，APP 不直接连接 MT5 服务器，也不直接保存可复用的 MT5 明文密码。
- `ConfigManager` / 固定正式入口 -> `GatewayV2SessionClient`
  远程账号会话当前固定走 `https://tradeapp.ltd`；`/v2/session/public-key` 与其余会话接口都必须通过 HTTPS 暴露，否则公钥可能被替换，App 侧加密会失去意义。
- `MonitorService` -> `AccountStatsPreloadManager`
  当 `v2 stream` 带账户运行态时直接应用已发布快照；只有 `historyRevision` 前进时才刷新历史，避免服务层再周期性补拉账户 snapshot；若补拉期间又收到更新 revision，会在当前一轮结束后继续追到最新版本。
- `AccountStatsBridgeActivity` -> `AccountCurvePointNormalizer` -> `CurvePoint`
  只对服务端曲线做严格清洗，不再在页面层补点、补值、补仓位比例，再驱动主图和附图。
- `MarketChartScreen` -> `AccountStorageRepository`
  图表账户叠加层在内存缓存缺失时回退到本地已持久化快照恢复当前持仓/挂单，保证首帧连续。
- `MarketChartTradeDialogCoordinator` -> `TradeExecutionCoordinator` -> `TradeCommandFactory` -> `GatewayV2TradeClient`
  图表页交易链先做会话身份校验，再做 `positionTicket` 硬校验，提交后把“已受理但未同步完成”明确收口为等待同步状态。
- `MarketChartTradeDialogCoordinator` / `AccountPositionPageController` -> `TradeSessionVolumeMemory`
  新建市价单、挂单和快捷交易的默认手数统一从这里取；单笔或批量成功提交后，也只允许通过这条链更新本次运行期的最近手数。
- `MarketChartTradeDialogCoordinator` -> `TradeComplexActionPlanner` -> `BatchTradeCoordinator` -> `GatewayV2TradeClient`
  第三阶段复杂动作统一先展开成 `BatchTradePlan`，再走正式 batch submit/result 契约，页面层不再 for-loop 提交单笔命令；`加仓 / 部分平仓 / 反手 / Close By` 都在这条链上显式透传 `accountMode`。
- `MarketChartScreen` / `AccountPositionPageController` -> `TradeBatchActionDialogCoordinator` -> `BatchTradeCoordinator`
  图表页与账户页的“批量操作”入口共用同一条选择、确认与提交链，不再各自维护第二套批量交互。
- `AccountStatsBridgeActivity` -> `AccountCurveHighlightHelper` -> `EquityCurveView` / `PositionRatioChartView` / `DrawdownChartView` / `DailyReturnChartView`
  附图长按时按共享横轴位置反推目标时间，再同步四张图的十字光标和主图弹窗数据。
- `AccountStatsBridgeActivity` -> `AccountConnectionTransitionHelper`
  判断账户连接是否从未连接切到已连接，决定是否播放登录成功提示。
- `AccountStatsBridgeActivity` -> `CurveAnalyticsHelper` -> `EquityCurveView` / `DrawdownChartView` / `DailyReturnChartView` / `TradeDistributionScatterView` / `HoldingDurationDistributionView`
  先把曲线和交易记录转成统一统计结果，再驱动主图、副图和两张分布图。
- `AccountStatsBridgeActivity` -> `AccountDeferredSnapshotRenderHelper`
  账户页把次级区块纯计算放到后台 helper 里完成，主线程只接收已准备好的可绑定结果。
- `FloatingWindowManager` -> `FloatingWindowLayoutHelper` + `FloatingWindowTextFormatter`
  统一产品卡片的宽度、左对齐规则和标题盈亏文案。
- `SettingsActivity` -> `SettingsSectionActivity`
  设置首页只负责分类入口，具体设置都在二级页里完成。
- `SettingsSectionActivity` -> `ConfigManager`
  持久化悬浮窗、Tab、缓存设置，并只读展示固定正式入口。
- `NotificationHelper` <- `MonitorService`
  异常交易触发后统一发系统通知。
- `MainViewModel` / `MainActivity` -> `MonitorRepository`
  读取主监控页当前展示所需的最新价格/K 线快照；当前也会同时观察 `MarketTruthSnapshot`，确保首页在统一市场真值被 REST 补修时也能同步刷新，而不是只等旧 runtime 变化。
- `admin_panel.py` -> `server_v2.py /health`、`/v1/source`、`/v1/abnormal`、`/v1/abnormal/config`、`/internal/admin/cache/clear`
  统一控制台通过本机 HTTP 读取网关状态、诊断输入、异常规则配置，并触发缓存清理。
- `server_v2.py /v2/session/*` -> `v2_session_manager.py` -> `v2_session_store.py` / `v2_session_crypto.py`
  远程账号会话链路先解密登录信封或读取已保存账号档案，再切换 MT5 运行态，并把结果落盘到当前激活账号摘要。
- `server_v2.py /v2/session/login` -> 会话网关适配器
  当前正式登录算法固定为“检测可附着的 MT5 终端进程 -> 必要时按 `MT5_PATH` 拉起 -> 附着重试 -> 读取基线账号 -> 用本次输入执行一次正式 `mt5.login(...)` -> 30 秒轮询 `mt5.account_info()` 的真实 `login`”；这里的“检测”已不再依赖 `MainWindowHandle`，而是按 `Win32_Process` 的真实 `terminal64/terminal` 进程识别，因为网关运行在 `SYSTEM` 非交互上下文时拿窗口句柄并不可靠；失败时直接把 7 个真实切号阶段写入会话诊断缓存，不再先走旧会话复用、探针探索或 reset terminal 链。
- `v2_session_crypto.py` / `v2_session_manager.py` -> `v2_session_models.py`
  会话加密层和会话管理层都统一通过领域模型生成公钥、状态和回执结构，确保服务端输出口径和 APP 解析模型一致。
- `v2_session_manager.py` -> `server_v2.py` 会话网关适配器
  会话管理器通过适配器执行 `login_mt5/switch_mt5_account/logout_mt5/clear_account_caches/force_account_resync`，把运行态切换和缓存收口统一封装在网关层。
- `admin_panel.py` -> `server_v2.py /health`、`/v1/source`
  控制台总览页从网关聚合结果中透传 `session.activeAccount` 和 `session.savedAccountCount`，用于展示当前激活账号与已保存账号数量。
- `admin_panel.py` -> PowerShell / Windows 计划任务 / 进程控制
  统一控制台直接管理网关、MT5 客户端、Caddy、Nginx 的启停与重启。

## 关键的设计决定和原因

- 首帧可见性当前先收口为统一 `bootstrap state` 状态机，而不是继续让图表页、账户页、分析页各自按“列表是否为空”推导 loading/空态；原因是“恢复中”和“真正为空”在用户体验和刷新主链里是两种完全不同的状态，必须先显式建模。
- 这次字号统一按“`styles.xml` 六档真值 + `TextAppearanceScaleResolver` 单一解析入口”处理，不再允许 XML 直写字号、运行时 `setTextSize(float)`、图表 `dp(9f)` 这类局部文字尺寸；原因是用户明确要求不再靠硬编码、补缝字号或绕过 `TextAppearance` 维持密度，最短路径就是让布局、运行时控件和画布绘制都共用同一套字号真值。
- 这次全局尺寸系统固定为“`dimens.xml` 基础阶梯与语义 token + `styles.xml` 行高与主体 padding + `SpacingTokenResolver` 运行时解析”三层真值，而不是继续让 XML、helper 和页面代码各自写一套间距；原因是只有把资源真值和 Java 入口一起锁死，非零 spacing literal 才不会在后续页面里重新长出来。
- 这次标准主体重构按“只保留 7 类标准主体，不允许第 8 类主体”处理；原因是旧 `Button / TextAction / Spinner / TextInputLayout` 多套命名并存已经证明会让页面不断长回自己的私有控件语言，最短路径就是把资源层和运行时入口都固定到同一套 Subject 真值。
- 标准主体真值固定为 `styles.xml + themes.xml + UiPaletteManager` 三层组合，而不是让 XML、主题和页面代码各自定义一套按钮/筛选/输入样式；原因是只有把资源入口和运行时入口一起锁死，后续新增页面才不会绕回旧包装层。
- 分段按钮和密集控件这次也不再用 `0.5sp` 递减之类缩字补缝，而是固定使用正式 `TextAppearance` 后再调整布局宽度、标签密度和可用空间；原因是这次目标不是把旧问题藏起来，而是把字号体系真正收口成稳定、可复用、可回归验证的规则。
- 图表页冷启动当前采用“先恢复态/本地态，再远端覆盖态”，而不是继续在进入页面时先清空主图再显示阻塞 loading；原因是本地 K 线缓存只有真正抢到首帧，用户才能感知到“先本地后远端”这条策略成立。
- 图表页请求计划当前按 `COLD_START / RESUME / SELECTION_CHANGE / MANUAL / AUTO_REFRESH` 显式分流，而不是继续让冷启动、切产品和自动刷新共用同一套跳过规则；原因是进入新显示上下文时必须做远端校准，但自动刷新和恢复前台在本地窗口仍新鲜时可以跳过请求。
- 图表页自动刷新当前改成“定时检查 + revision/stale gate 决定是否真的回源”，而不是继续让 `autoRefreshRunnable` 固定频率直连远端；原因是定时器现在只该负责保活和过期判断，不应继续充当页面同步感的主要来源。
- 运行时版本推进当前先收口为 `RuntimeRevisionCenter` 的 5 类 canonical revision，而不是继续把市场、账户、产品的刷新序号散落在各模块本地字段里；原因是后续图表、账户页、分析页和悬浮窗都要按同一 revision 语义做分区刷新，不先统一命名，后面只能继续各写各的。
- 市场真值这一轮先收口为 `MonitorService -> MonitorRepository -> MarketRuntimeStore -> MarketSelector` 单链，而不是继续让服务层直接写 3 份展示 cache；原因是价格、最新 patch、闭合 1 分钟必须先进入同一市场底稿，旧 display map 只能做兼容镜像，不能继续充当主真值。
- 主题切换功能和 `Notion Data Desk` 主题已删除，设置页也不再保留“主题设置”入口；原因是用户明确确认该主题未达到预期，当前最小完整方案是回到单一默认主题，避免继续保留无效入口和无效配置。
- 2026-04-16 UI 第一轮重做已落地：主壳常驻结构已收口为 `交易 / 账户 / 分析`，`设置` 改由独立目录页承接；原因是用户已明确不再需要旧版“行情概览”型首页，也不需要把低频设置入口继续占在底部常驻导航里。
- 实盘升级先按“交易网关 -> 命令状态机 -> 强一致同步 -> 交易界面增强”四阶段推进；原因是当前项目最大缺口不在图表展示，而在没有真正的下单、校验、确认、回执和审计主链。
- APP 从实盘阶段开始要采用“命令式 + 快照式”双轨；原因是用户发出交易指令后，页面不能只靠旧快照轮询判断结果，必须显式区分草稿、预检查、待确认、提交中、已受理、已拒绝、超时和已结算。
- 包结构整理遵循“只移动真正独立的 helper，不为了目录整齐去扩大访问级别”；原因是整理包结构本身不能反过来破坏既有职责边界。
- 审计护栏采用“关键链路低误报静态检查”策略；原因是当前目标是先让整改热点具备可持续的工程化防回归能力，而不是一次性把全项目样式问题全部拉成阻断项。
- 服务端交易能力要独立于现有读取接口新增 `trade/check`、`trade/submit`、`trade/result` 或事件流；原因是最小手数、步进、冻结距离、交易时间、保证金不足、重报价和账户模式判断都不能放在手机端猜测。
- 第一阶段范围固定为“最小交易闭环”，只覆盖单笔市价开仓、单笔平仓、挂单新增/删除、单笔 TP/SL 修改、统一确认弹窗和交易后强一致刷新；原因是这是一条足够短、又不会引入结构性错误的正确路径。
- 图表直接交易、批量交易、Close By、DOM、一键交易和高级风险控制全部后置；原因是这些能力都建立在底层交易命令链可靠之后，先做界面会放大误操作和状态错乱风险。
- Binance 行情链路统一走韩国服务器转发，避免手机继续直连 Binance 官方地址。
- 新架构下把“Binance 行情真值”和“MT5 账户真值”明确拆开：行情、K 线、成交量、指标只认 Binance，MT5 只负责账户侧事实；原因是这样才能消除 APP 本地拼 K 和多份缓存互相覆盖造成的口径漂移。
- 图表页当前不再让本地分钟底稿参与最终真值计算，只保留服务端 `candles + latestPatch` 和本地展示缓存；原因是之前本地聚合尾部会反向污染官方历史窗口。
- 图表页这轮继续收口为“闭合历史快照 + 最新 1 条 patch”：分钟实时 patch 只允许覆盖 `1d` 及以下周期，且未闭合 patch 不再写入 Room；原因是之前 1m 短尾部会把整窗历史跳过，且长周期还会被分钟 patch 错误覆盖。
- 图表长周期（周/月）当前固定只走 Binance REST 原生周期接口，不再按返回条数切到日线聚合或通用历史回退；原因是单一窗口必须保持单一数据口径，不能在两套算法之间切换。
- 图表本地缓存当前只保留 `ChartHistoryRepository` 这一层，已删除旧 `KlineCacheStore` 等重复快照存储；原因是旧设计会形成“同一份 K 线被多处重复存储”和缓存职责混乱的结构性问题。
- 图表历史仓库当前不再负责“读旧历史再合并”，而只负责写入上层已整理好的窗口；原因是图表页内存窗口已经是本轮展示真值，仓库层重复再读一次只会增加 IO 和复杂度。
- `MonitorService` 不再承担“预热图表 1m 底稿”的职责，冷启动和 stale 回退都统一改成 `GatewayV2Client.fetchMarketSeries(...)`；原因是服务层现在只负责展示快照和悬浮窗，不应再反向参与图表历史真值。
- `v2 stream` 的 `busSeq` 当前采用“成功应用后再提交”的两段式规则；原因是坏包、半应用包或数值异常包都不应推进序列，否则会把后续真消息一起挡掉。
- 账户预加载改成“高频轻快照 + 历史按 `historyRevision` 条件补拉”；原因是账户页平时只需要账户概览、当前持仓和挂单，继续每轮都拉全量历史会浪费流量并拖慢刷新。
- 账户运行态的“已连接/未连接”现在只认完整远程账号身份，不再把 `remote_logged_out` 的空快照当成已连接；原因是账户页和交易执行链都不能基于假连接状态继续工作。
- `MonitorService` 的历史补拉改成“串行执行 + 暂存最新 revision 续跑”；原因是 stream 可能在单次全量 history 拉取期间继续前进，如果只保留单个 `inFlight` 开关会丢失后到 revision。
- 仓库内已失联的 `AccountStatsLiveActivity + Mt5GatewayClient` 旧账户页链路已直接删除；原因是它们不再属于任何业务入口，却仍保留 `/v1` 账户快照与本地补位逻辑，继续保留只会制造维护误导。
- 账户展示链当前收口成“服务端指标直出 + 页面只在能证明真值时做有限覆盖”；原因是 `overviewMetrics / curveIndicators / statsMetrics` 主体仍由服务端给出，但 `累计盈亏 / 累计收益率` 这类字段在 APP 端已有完整曲线或历史成交真值时，可以严格覆盖；继续完全禁用或继续无条件覆盖，都会制造新的错误口径。
- 图表页账户叠加层恢复链当前收口成“先内存，再 Room 持久化快照”；原因是 tab 切换或重新进入图表页时，问题根因是本地首帧主动清空，而不是上游真值不存在。
- 图表页启动门控当前收口成“主序列提交 + 主图首帧绘制”双阶段规则；原因是“何时允许实时尾部和持仓标注开始消费”必须由图表实际完成首帧来定义，不能再靠条数阈值、延时或局部稳定化补丁。
- 图表叠加层签名当前收口成“只看当前产品、当前窗口和当前产品相关运行态内容”；原因是 `cache.updatedAt` 这类每秒变化但不改变展示内容的字段，会无意义触发整套叠加层重建。
- 历史成交锚点当前收口成“窗口内二分定位 + 1 分钟图分钟桶对齐”；原因是线性扫描会随着 K 线条数增长直接拖慢 1 分钟图上的历史交易加载。
- 账户运行态应用链当前收口成“持久化后直接基于本次快照建缓存”，不再写库再读回；原因是这类高频链路必须避免把轻量刷新放大成磁盘 I/O 回环。
- 产品运行态读取当前收口成“统一运行态中心保存 1 份当前账户真值，但所有产品级 selector 在读取时必须显式带 `account/server` 身份”；原因是问题根因不在内存里是否短暂残留旧快照，而在新页面过去可以无身份直接读取全局产品运行态，导致切账户后旧账号摘要、图层和悬浮卡片被误当成当前会话真值。
- 调试时序日志当前默认关闭：原因是 `ChainLatencyTracer` 和远程会话 debug 已完成阶段性定位，继续常态输出只会增加主链噪音和性能负担。
- `/v2/market/candles` 缓存寿命当前要求略长于 `v2/stream` 推送节拍；原因是主界面图表、悬浮窗和持仓摘要会在同一秒窗口内并发读同一批市场数据，缓存过短会把同一轮读取重复放大到上游。
- 账户历史与曲线主链当前只接受服务端标准时间和价格字段；原因是客户端再做秒转毫秒、`timestamp -> open/close time`、`price -> open/close price` 这类补位，会把纯消费链重新拉回本地拼装。
- 会话收口当前采用“login/switch 成功后必须 `fetchStatus()`，且只认 `status.activeAccount`”；原因是 `receipt/status/本地 saved account/输入值` 多路拼装会继续制造账号身份分裂。
- 当 `receipt.activeAccount` 与 `status.activeAccount` 冲突时直接失败；原因是这代表服务器会话真值未闭合，前端不能自行猜测哪一个才是目标账号。
- `/v2/session/login` 当前采用“本次输入即本次 GUI 切号”的单路径算法，不再把 `active_session`、已保存账号、`.env`、当前终端身份、probe 或 reset terminal 作为登录前置决策；原因是用户目标是每次显式登录/切换都直接驱动服务器端 MT5 切到本次目标账号，继续保留旧混合链只会把切号问题隐藏成状态比对问题。
- MT5 数据链路拆成摘要、轻实时持仓、挂单增量、成交增量、曲线追加，减少日常流量和重复全量请求。
- 网关快照缓存改成“按最近使用裁剪 + EA 新鲜时短时平滑续用”；原因是这样能同时压住 `snapshot/sync` 缓存的内存占用，并减少固定轮询下缓存命中与重建交替造成的延迟抖动。
- `server_v2.py` 的历史成交映射改成“按成交顺序 + FIFO 开仓库存”配对；原因是多次加仓、部分平仓、反手时，不能再用单个生命周期均价去覆盖全部平仓记录。
- MT5 历史成交时间改成“网关只按 `MT5_SERVER_TIMEZONE` 把服务器 wall-clock 时间归一化成 UTC，`MT5_TIME_OFFSET_MINUTES` 仅保留健康面板旧值展示”；原因是固定分钟差只能压住局部现象，严格口径应从时间语义源头统一，才能让交易列表、历史成交点和账户曲线共用同一条时间轴。
- 图表页实时刷新改成“未收盘分钟线先进本地分钟底稿，再覆盖当前周期最新尾部”；原因是这样既能补上 1 分钟实时 K 线，也能继续沿用本地多周期缓存减少切周期卡顿。
- 服务端异常同步 `HTTP 404` 改成客户端一次识别后暂停轮询；原因是接口未部署时继续固定频率请求只会刷日志和浪费流量。
- 服务器管理 UI 独立为 `admin_panel.py` 控制台服务，而不是塞进 `server_v2.py` 本体；原因是这样即便主网关被停止或重启，控制台仍可继续提供浏览器入口，才能真正完成“启动 / 停止 / 重启网关”这类操作。
- Windows 部署现收口为“源码两处 + 产物一处”：源码只维护 `bridge/mt5_gateway` 和 `deploy/tencent/windows`，部署时统一由 `scripts/build_windows_server_bundle.py` 生成 `dist/windows_server_bundle`；原因是仓库里长期保留静态部署副本会导致改动位置漂移、文件不同步和现场排障困难。
- 线上部署验收现在必须同时满足“健康接口来自当前 bundle 指纹”和“`wss://tradeapp.ltd/v2/stream` 能收到首条消息”；原因是过去只看 `200` 无法证明公网实际运行的是哪一版网关，也无法证明 websocket 主链真的可用。
- 轻快照主链当前只负责账户摘要、持仓、挂单和 `historyRevision`，不再在高频链里展开全量历史对象；原因是账户页需要服务端真值来判断“是否需要刷新历史”，但不能为了这个条件判断重新回到每轮全量历史重建。
- `/v2/account/snapshot` 当前直接使用 MT5 轻快照，并只保留当前概览指标、持仓和挂单；原因是账户页和同步链需要的是当前账户真值，不应再被完整历史、曲线和成交重建拖慢。
- 完整账户快照当前只拉一次 MT5 成交历史，再同时供曲线与交易列表复用；原因是之前曲线和交易映射各扫一次历史，会在 `state_lock` 内重复放大耗时。
- `v2/stream` 当前先看会话真值，再决定是否读取 MT5 账户快照；原因是未激活远程会话时，监控链只需要市场真值和空账户摘要，不应把 websocket 首包建立建立在 MT5 初始化之上。
- Windows 部署包现要求闭合为单根目录 `C:\mt5_bundle\windows_server_bundle`；原因是用户明确要求把整个 `dist/windows_server_bundle` 文件夹一次性复制到服务器，而不是拆成两个子目录分别处理。
- Windows 部署脚本对 `caddy.exe` 采用兼容查找：优先 `windows_server_bundle\windows\caddy.exe`，其次 `windows_server_bundle\caddy.exe`，最后 `C:\mt5_bundle\caddy.exe` 这类上级目录；原因是服务器现场已存在历史安装位置，部署脚本需要兼容而不是强迫用户重新搬动二进制文件。
- Windows 网关依赖现显式包含 `tzdata`；原因是 `server_v2.py` 用 `zoneinfo` 严格按 `MT5_SERVER_TIMEZONE` 归一化 MT5 时间轴，`Europe/Athens` 这类 IANA 时区在部分 Windows Python 环境里如果没有 IANA 时区库会被误判成配置错误，直接卡死账户快照和 APP 登录收口。
- Windows 一键重部署现收口为“前台唯一 GUI + 后台隐藏 worker”；原因是用户要求服务器端除了状态窗口外不再出现其他前台窗口，同时关闭状态窗口也不能影响后台服务继续运行。
- Windows 一键重部署当前会先显式停掉旧计划任务、旧网关、旧面板、旧 Caddy / Nginx，并强制释放 `80 / 443 / 2019 / 8787 / 8788`；原因是如果不先清掉旧进程与端口占用，重新部署最容易在服务器现场直接失败。
- Windows 启动脚本和首次引导脚本当前统一使用脚本内置 .NET `SHA256`，不再依赖 `Get-FileHash`；原因是服务器真实启动链已经证明，部分 PowerShell 环境里缺少该 cmdlet 时，网关会在健康检查前就直接起不来。
- 远程账号会话当前采用“单用户、多账号、任意时刻一个激活账号”的服务端模型；原因是这样能在现有 MT5 网关结构内用最小正确改动闭合安全、切换和同步链路。
- 远程登录链路当前采用 `cryptography` 实现 `rsa-oaep+aes-gcm`，账号落盘仍用 Windows DPAPI；原因是标准库无法完整提供设计要求的公钥信封解密，而本机密文档案仍应交给 Windows 本机保护能力处理。
- Task 1 的会话模型当前已从“占位 dataclass”收口成真实接口模型，并由加密层与会话管理层直接复用；原因是如果模型只存在文件里、不参与返回结构生成，后续维护仍会继续依赖散落字典，容易再次出现前后端字段漂移。
- APP 侧远程会话当前采用“accepted 进入 syncing，轻量身份确认后进入 `FULL_SYNCING`，后台 full 补全稳定后才进入 active”的状态机；原因是账户切换不能只靠接口受理结果判断成功，但也不能把登录成功错误绑死在 `/v2/account/full` 这条重链上。
- APP 本地密码链路当前统一改成 `char[]` 短生命周期，不再用 `String` 长链保存；原因是服务端已经承担加密记住账号能力，手机端继续保留明文字符串会破坏安全边界。
- APP 本地已停止持久化明文 MT5 密码，只保留 Android Keystore 加密后的会话摘要；原因是服务端已经承担加密记住账号能力，手机端继续留明文密码会破坏安全边界。
- 远程账号登录 / 切换必须走 HTTPS 公网入口；原因是公钥信封只解决应用层敏感字段保护，不应在纯 HTTP 下开放会话控制接口。
- 第 1 步本轮继续补完“入口唯一化”的最后一刀：Manifest 不再声明全局明文流量，配置中心固定只返回 `https://tradeapp.ltd`，设置页网关项也改成只读展示；原因是只拦截新输入还不够，只要运行时还允许改写 HTTPS 域名，主链就仍然不是唯一入口。
- 图表页交易链当前采用“身份真值一致 + `positionTicket` 三层硬校验 + 受理后等待同步显式化”的收口方式；原因是平仓和改单目标缺失时不能继续流入网关，而服务端已受理也不等于页面真值已经完成切换。
- 服务入口当前统一收口到 `MonitorServiceController`，监控开关真值统一落 `ConfigManager`；原因是页面和开机广播不该各自决定服务启动，也不该在重启后把用户主动关闭的监控重新打开。
- Task 6 当前只完成了自动化验收与文档收口，真机人工联调仍需按验收清单单独执行；原因是“已部署服务器 + App 实际 HTTPS 入口 + 真机页面收口”不属于仓库内可完全替代的验证范围。
- 远程账号会话当前只收口“单用户、多账号、单激活账号”的最小正确模型；原因是这一阶段目标是先闭合安全登录、切换、退出和页面强一致收口，多用户隔离与后台公钥轮换留到后续阶段。
- nonce 去重当前只做进程内内存态；原因是本阶段先收口单机单进程的最小安全闭环，多实例共享去重留到后续阶段再做集中存储。
- 账户预加载节奏从管理器内联常量改成 `AccountPreloadPolicyHelper` 统一计算；原因是这样更容易和前后台策略保持一致，也便于后续继续压缩账户页相关资源消耗。
- 悬浮窗改为“统一快照 + 产品卡片”模型，解决不同字段更新时间不一致的问题。
- 悬浮窗拖动增加长按触发、位移阈值和帧节流，减少拖动卡顿和误触。
- 图表历史、历史交易、账户摘要、持仓快照统一切到 Room，满足“拉过就长期保留”的要求。
- 图表刷新前不再清空已显示 K 线，避免刷新瞬间白屏。
- 行情持仓页的隐私隐藏改为“保留 K 线主体、只隐藏持仓相关叠加层和顶部轻量账户状态”，避免整页遮罩把行情走势一起盖掉。
- 隐私入口统一收口到账户统计页的小眼睛，设置页不再保留重复开关，避免两套入口互相打架。
- K 线缩放增加斜向整体缩放分支，解决双指斜拉时只能勉强落到横向或纵向的问题。
- `MA / EMA` 默认关闭，降低初始图表干扰。
- 设置页拆成“目录首页 + 二级详情页”，提升设置层级清晰度。
- 主题递归样式不再把 `Switch / RadioButton / CheckBox` 当普通按钮套背景，解决 OR / AND、启用开关下方方框背景问题。
- 登录成功提示只在“未连接 -> 已连接”时触发一次，避免自动刷新期间重复弹出。
- 月收益表改为“左侧年份整块 + 右侧三行月份 + 横向滚动”，解决年份不对齐和百分比显示不全的问题。
- MT5 网关曲线改为“成交 + 持仓 + 价格”重放，解决净值与结余长期重合的问题。
- 账户统计页把最大回撤、回撤附图、日收益率和交易分布统一建立在同一套净值口径上，避免不同模块口径不一致。
- `MonitorRepository` 收口成“展示快照仓库”而不是“实时主链仓库”；原因是当前图表与账户真值已经分别切到服务端 `v2`，继续让旧名字留着，只会误导后续维护时把它当成主数据源。
- 旧 Binance WebSocket 管理器改名为 `FallbackKlineSocketManager`，回调也统一改成 fallback 语义；原因是它现在只承担 `v2 stream` 失效时的回退输入，继续保留主链式命名会误导后续维护。
- 异常交易提醒先在 App 端提升为更强系统通知；服务器端异常判断迁移后续再评估，不在本轮硬塞进去。
