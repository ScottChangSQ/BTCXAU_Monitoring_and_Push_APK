# Account Stats Phase B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把账户统计页收口为新的隐私交互和更紧凑一致的图表排版，同时修复月收益表与分布图表达问题。

**Architecture:** 继续复用现有全局隐私状态，但把账户统计页从“整页遮罩”改为“局部数据打码 + 图表占位态”。图表边距通过统一常量收敛，月收益表改用真实三行高度对齐，持仓时间图补充总数文案。

**Tech Stack:** Java、XML、ViewBinding、JUnit4、Gradle

---

### Task 1: 补账户统计隐私与文案测试

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPrivacyFormatter.java`
- Create: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsPrivacyFormatterTest.java`

- [ ] **Step 1: 先写失败测试，覆盖账号标题、更新时间、收益表数值、通用金额在隐私关闭时改成 `****`**
- [ ] **Step 2: 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsPrivacyFormatterTest"`**
- [ ] **Step 3: 写最小实现让测试通过**

### Task 2: 改账户统计页隐私入口和整体显示规则

**Files:**
- Modify: `app/src/main/res/layout/activity_account_stats.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/AccountMetricAdapter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricAdapter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapterV2.java`
- Create: `app/src/main/res/drawable/ic_privacy_visible.xml`
- Create: `app/src/main/res/drawable/ic_privacy_hidden.xml`

- [ ] **Step 1: 删掉账户统计页整页隐私遮罩布局**
- [ ] **Step 2: 在“已连接账户”左侧加入眼睛按钮并接到全局隐私状态**
- [ ] **Step 3: 把标题、概览、交易统计、交易记录、持仓摘要等文本改成按隐私状态打码**
- [ ] **Step 4: 刷新页面时同步更新眼睛图标和各区块显示**

### Task 3: 统一三联图和交易统计图的绘图区边距

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java`

- [ ] **Step 1: 抽齐左右边距和顶部底部留白，让三联图更靠近屏幕边缘**
- [ ] **Step 2: 让历史交易分布图和持仓时间分布图与总计盈亏图左右轴线对齐**
- [ ] **Step 3: 给这些图加上隐私占位态，隐藏真实图形和数值**
- [ ] **Step 4: 把净值线和结余线保持明显不同颜色**

### Task 4: 修月收益表和持仓时间图表达

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java`

- [ ] **Step 1: 把年份单元格高度改成按三行真实高度对齐**
- [ ] **Step 2: 月收益表在隐私关闭时把数值改成 `****`**
- [ ] **Step 3: 持仓时间图补上总数 / 盈利 / 亏损显示**
- [ ] **Step 4: 隐私关闭时把持仓时间图切到 `****` 占位**

### Task 5: 验证和文档

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] **Step 1: 运行 `.\gradlew.bat :app:testDebugUnitTest`**
- [ ] **Step 2: 运行 `.\gradlew.bat :app:assembleDebug -x lint`**
- [ ] **Step 3: 更新 `CONTEXT.md`**
- [ ] **Step 4: 如职责描述有变化，再同步更新 `README.md` 与 `ARCHITECTURE.md`**
