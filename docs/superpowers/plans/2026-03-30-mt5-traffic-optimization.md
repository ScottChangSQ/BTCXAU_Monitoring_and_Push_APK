# MT5 Traffic Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 MT5 账户链路从“大快照反复轮询”改成“轻实时持仓 + 挂单增量 + 成交增量 + 曲线追加”，降低服务器外发流量和手机流量。

**Architecture:** 网关侧把完整快照拆成多个投影接口，每个接口维护自己的增量同步序号；App 侧保留现有 `AccountSnapshot` 作为页面统一数据模型，但由客户端缓存把多个小接口结果合并回完整快照。后台预加载改走轻实时持仓接口，账户页首轮仍可拿完整快照，之后切换到小接口增量刷新。

**Tech Stack:** FastAPI、MetaTrader5 Python、Java、OkHttp、Room、JUnit4、unittest

---

### Task 1: 网关投影接口测试

**Files:**
- Modify: `bridge/mt5_gateway/tests/test_summary_response.py`

- [ ] 补充失败测试，约束轻实时接口和分项接口的返回字段
- [ ] 运行 Python 单测，先确认新增测试失败
- [ ] 再进入实现

### Task 2: 网关拆分投影接口

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`

- [ ] 新增 `live / pending / trades / curve` 四类投影响应构造器
- [ ] 让各接口维护各自的 `syncSeq` 和增量缓存键
- [ ] 轻实时接口仅返回账户摘要、统计和持仓
- [ ] 挂单、成交、曲线接口分别只返回自己需要的数据
- [ ] 运行 Python 单测，确认通过

### Task 3: App 客户端拆分同步键与接口访问

**Files:**
- Modify: `app/src/test/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClientTest.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/Mt5BridgeGatewayClient.java`

- [ ] 先补失败测试，约束不同接口使用不同同步键
- [ ] 新增轻实时接口和分项接口的访问入口
- [ ] 保留现有 `AccountSnapshot`，由客户端缓存把分项结果合并回统一快照
- [ ] 首轮全量、后续增量的策略放到客户端内部，不改页面调用方式

### Task 4: 预加载与账户页取数策略切换

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java`

- [ ] 后台预加载改走轻实时持仓接口
- [ ] 账户页第一次进入时拿完整快照，后续刷新改走增量组合
- [ ] Room 持久层继续长期保存交易和曲线，且只追加不清空
- [ ] 保证悬浮窗仍能拿到最新持仓分产品盈亏

### Task 5: 验证与文档

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] 运行 `bridge` 单测
- [ ] 运行 Android 单测
- [ ] 运行 `assembleDebug -x lint`
- [ ] 更新上下文和架构文档，记录新链路
