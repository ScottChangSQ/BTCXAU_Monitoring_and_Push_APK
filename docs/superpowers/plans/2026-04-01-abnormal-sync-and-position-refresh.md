# 异常同步与首屏持仓刷新 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让账户/图表页在打开时立刻显示最新持仓，并把异常交易判断迁到服务端后同步到手机端通知与图表。

**Architecture:** 继续沿用现有“MT5 预加载缓存 + 前台页面展示”与“服务端网关 + 手机前台服务”两条链路。账户页补上预加载缓存监听的立即刷新，异常交易改成服务端统一判断、手机端周期同步记录和提醒，图表继续消费本地异常记录并按时间桶绘制强度。

**Tech Stack:** Android Java, LiveData, OkHttp/JSON, FastAPI Python, JUnit, unittest

---

### Task 1: 首屏持仓即时刷新

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountSnapshotDisplayResolverTest.java`

- [ ] 检查 `AccountStatsBridgeActivity` 当前只在 `onCreate` 使用一次预加载缓存，确认首屏延迟来自监听链路未接通。
- [ ] 让 `AccountStatsPreloadManager` 在页面注册监听时，能把当前最新缓存立即派发给监听方。
- [ ] 给 `AccountStatsBridgeActivity` 增加缓存监听，在 `onResume` 注册、`onPause/onDestroy` 移除，并收到缓存后立即刷新“当前持仓”区域。
- [ ] 运行 `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountSnapshotDisplayResolverTest"` 验证现有快照选择逻辑未被破坏。

### Task 2: 服务端异常交易同步到手机端

**Files:**
- Create: `app/src/main/java/com/binance/monitor/data/model/AbnormalAlertItem.java`
- Create: `app/src/main/java/com/binance/monitor/data/remote/AbnormalGatewayClient.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/constants/AppConstants.java`
- Test: `app/src/test/java/com/binance/monitor/data/remote/AbnormalGatewayClientTest.java`
- Test: `bridge/mt5_gateway/tests/test_abnormal_gateway.py`

- [ ] 先写客户端解析测试，覆盖服务端 `/v1/abnormal` 的全量/增量返回、异常记录解析和提醒解析。
- [ ] 新增 `AbnormalGatewayClient`，负责读取网关地址、拉取异常同步、推送异常配置到 `/v1/abnormal/config`。
- [ ] 调整 `MonitorService`：停止本地 `handleClosedKline` 异常判断，改成周期拉取服务端异常；把新记录写入 `AbnormalRecordManager`，把新提醒转成系统通知；配置变更时同步到服务端。
- [ ] 保持主页面、图表页继续只读 `AbnormalRecordManager`，不改页面消费方式。
- [ ] 运行 Android 单测与 `.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_abnormal_gateway -v`，确认客户端解析与服务端异常接口行为正常。

### Task 3: 图表异常圆点强度确认与收口

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilder.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/AbnormalAnnotationOverlayBuilderTest.java`

- [ ] 检查现有聚合逻辑是否已经满足“次数越多颜色越深、位置最高到 2 倍”的要求。
- [ ] 若仍有缺口，再最小化调整强度映射或绘制偏移；若已满足，只补验证并保持实现稳定。
- [ ] 运行 `./gradlew.bat testDebugUnitTest --tests "com.binance.monitor.ui.chart.AbnormalAnnotationOverlayBuilderTest"` 验证图表强度逻辑。

### Task 4: 文档与收尾

**Files:**
- Modify: `CONTEXT.md`
- Modify: `ARCHITECTURE.md`

- [ ] 更新 `CONTEXT.md`，记录本轮完成内容、停留位置与关键决定。
- [ ] 如果最终实现新增了明确模块职责或调用关系，再同步更新 `ARCHITECTURE.md`。
- [ ] 运行本轮用到的全部验证命令，整理结果后再汇报完成状态。
