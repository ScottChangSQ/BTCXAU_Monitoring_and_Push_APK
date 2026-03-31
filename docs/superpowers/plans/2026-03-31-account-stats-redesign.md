# Account Stats Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让服务端输出真实区分的净值/结余曲线，并把客户端账户统计页和设置页改成更专业、更清晰的新版样式。

**Architecture:** 服务端先把 `curvePoints` 重算为“结余 + 浮盈亏”的混合路径，客户端再基于同一份曲线数据重画主图、副图、收益表和分布图。设置页维持现有分级结构，但去卡片化并改成微信式列表骨架。

**Tech Stack:** FastAPI、MetaTrader5 Python、Java、XML、ViewBinding、JUnit4、unittest

---

### Task 1: 服务端曲线重算测试

**Files:**
- Modify: `bridge/mt5_gateway/tests/test_summary_response.py`

- [ ] 先补服务端失败测试，约束“有持仓浮动时 equity 与 balance 不能长期完全相同”
- [ ] 运行 `.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_summary_response -v`
- [ ] 确认新增测试先失败，再进入实现

### Task 2: 服务端净值/结余曲线重算

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`

- [ ] 引入历史价格采样与持仓生命周期重建逻辑
- [ ] 用成交、持仓、产品价格、手数重算 `curvePoints`
- [ ] 保留历史价缺失时的降级路径
- [ ] 再运行 `.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_summary_response -v`

### Task 3: 设置页微信式改版

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/res/layout/activity_settings_detail.xml`

- [ ] 先按现有功能保留原则梳理首页与二级页结构
- [ ] 改成整行列表、分组分隔、右箭头、弱圆角骨架
- [ ] 去掉重卡片依赖，保留现有设置功能入口
- [ ] 本地检查设置页主要入口与点击跳转是否仍然成立

### Task 4: 账户统计页图表与表格测试

**Files:**
- Create: `app/src/test/java/com/binance/monitor/ui/account/CurveAnalyticsHelperTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/account/TradeDistributionAnalyticsTest.java`

- [ ] 先补失败测试，约束最大回撤区间重算、回撤序列、日收益序列、交易分布与持仓时间分布
- [ ] 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.CurveAnalyticsHelperTest" --tests "com.binance.monitor.ui.account.TradeDistributionAnalyticsTest"`
- [ ] 确认先失败，再进入实现

### Task 5: 账户统计页分析与图表实现

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/CurveAnalyticsHelper.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/res/layout/activity_account_stats.xml`

- [ ] 提取账户统计分析逻辑，避免继续把大计算堆进 `AccountStatsBridgeActivity`
- [ ] 主图最大回撤高亮改成高对比风格，并按当前周期重算
- [ ] 新增回撤曲线、日收益率曲线、历史交易分布图、持仓时间分布图
- [ ] 把页面主体去卡片化，调整为平直分段结构
- [ ] 接入新分析结果并刷新渲染

### Task 6: 收益表与交易统计区改版

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricAdapter.java`
- Modify: `app/src/main/res/layout/item_stats_metric.xml`

- [ ] 月收益表改成热力格风格
- [ ] 日收益、年收益、阶段收益统一风格
- [ ] 交易统计区改成清单表排版，并保留金额收益率红绿显示
- [ ] 运行 `.\gradlew.bat :app:testDebugUnitTest`

### Task 7: 总体验证与文档

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] 运行 `.\.venv\Scripts\python.exe -m unittest bridge.mt5_gateway.tests.test_summary_response -v`
- [ ] 运行 `.\gradlew.bat :app:testDebugUnitTest`
- [ ] 运行 `.\gradlew.bat :app:assembleDebug -x lint`
- [ ] 更新 `CONTEXT.md`
- [ ] 如果模块职责或运行方式已变化，再更新 `README.md` 与 `ARCHITECTURE.md`
