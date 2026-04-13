# Account Position Tab Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增“账户持仓”Tab 承接账户概览与当前持仓/挂单，把图表页收口为轻量图表页，并用后台预计算与分阶段落地明显压低慢帧与跳帧。

**Architecture:** 保持 `AccountStatsPreloadManager.Cache` 作为唯一账户快照来源，不再让图表页直接承载完整账户概览与持仓列表。新增 `AccountPositionActivity` 消费同一份快照并生成只读展示模型；图表页改成“主图先落地、叠加层后落地、状态文案最后落地”的三段式提交，避免一次性整页重排。

**Tech Stack:** Java、XML、ViewBinding、RecyclerView、JUnit4、Gradle、adb/gfxinfo/logcat

## Status Update (2026-04-12)

- `Task 1` 到 `Task 5` 的结构改造与本地验证已完成，相关页面、读模型、差异刷新、图表叠加快照、账户统计页职责收口都已落地。
- `Task 6` 中 `assembleDebug`、`README.md / ARCHITECTURE.md / CONTEXT.md` 更新已完成。
- `Task 6` 中 `adb install`、真机 `gfxinfo / logcat / batterystats` 复测仍未完成；当前阻塞原因是本机 `adb devices` 无在线设备，无法伪造验收结果。

---

### Task 1: 固化 5 Tab 导航与新页面入口

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/menu/menu_bottom_nav.xml`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/res/layout/activity_account_stats.xml`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/res/layout/activity_settings_detail.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsSectionActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/BottomTabVisibilityManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Create: `app/src/main/res/layout/activity_account_position.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionLayoutResourceTest.java`

- [ ] **Step 1: 先补导航与布局测试，锁定最终 5 Tab 结构**
  验证目标只保留一套正式结构：`行情监控 / 行情持仓 / 账户统计 / 账户持仓 / 设置`。测试同时覆盖新页面底部导航存在、文案正确、选中态 id 可绑定。

- [ ] **Step 2: 在资源层加入“账户持仓”正式入口**
  在 `strings.xml` 增加 `nav_account_position`，在 `menu_bottom_nav.xml` 补齐菜单项，并在 5 个页面布局里加入 `tabAccountPosition`，不再沿用“把旧页内容偷偷塞回图表页”的方式。

- [ ] **Step 3: 注册新 Activity 并接通页面跳转**
  在 `AndroidManifest.xml` 注册 `AccountPositionActivity`，并在 5 个 Activity 内统一增加 `openAccountPosition()`、更新 `updateBottomTabs(...)` 参数和选中态，保证导航口径一致。

- [ ] **Step 4: 实现 `AccountPositionActivity` 空骨架页面**
  新页面先只落标题、更新时间、账户概览区、当前持仓区、挂单区和底部导航，不在第一步接入复杂刷新逻辑，先保证结构闭合、可编译、可切换。

- [ ] **Step 5: 运行页面与编译验证**
  Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPositionLayoutResourceTest"`
  Expected: PASS

  Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`
  Expected: BUILD SUCCESSFUL

### Task 2: 抽出账户持仓页展示模型，统一账户快照到单一读模型

**Files:**
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionUiModel.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionUiModelFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountOverviewMetricsHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionUiModelFactoryTest.java`

- [ ] **Step 1: 先写展示模型工厂测试**
  覆盖三类输入：有持仓有挂单、有挂单无持仓、无账户快照。断言输出模型中的概览指标、摘要文案、持仓列表、挂单列表和签名字段全部稳定。

- [ ] **Step 2: 定义只读展示模型**
  `AccountPositionUiModel` 只承载展示需要的数据，不混入请求逻辑。字段至少包含：`overviewMetrics`、`positionSummaryText`、`pendingSummaryText`、`positions`、`pendingOrders`、`updatedAtText`、`signature`。

- [ ] **Step 3: 用同一份 `AccountStatsPreloadManager.Cache` 生成新页面读模型**
  在 `AccountPositionUiModelFactory` 中统一完成概览指标组装、持仓排序、挂单整理、空态文案生成。排序和聚合全部在后台阶段完成，UI 线程只接收最终列表。

- [ ] **Step 4: 收口快照监听入口**
  `AccountPositionActivity` 只订阅 `AccountStatsPreloadManager` 的单一监听链路，不自己再发明第二套实时源；如需元信息，复用现有缓存更新时间与会话信息。

- [ ] **Step 5: 运行模型测试**
  Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPositionUiModelFactoryTest"`
  Expected: PASS

### Task 3: 实现账户持仓页“分段刷新”，替代图表页的大块账户重排

**Files:**
- Modify: `app/src/main/res/layout/activity_account_position.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/AccountMetricAdapter.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PositionAdapterV2.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/adapter/PendingOrderAdapter.java`
- Create: `app/src/main/java/com/binance/monitor/ui/account/AccountPositionSectionDiff.java`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountPositionSectionDiffTest.java`

- [ ] **Step 1: 先定义分段刷新边界**
  页面固定顺序为：账户概览 -> 当前持仓 -> 挂单。账户概览、持仓列表、挂单列表分别独立刷新，禁止整页 `requestLayout()` 式联动重排。

- [ ] **Step 2: 写分段签名与差异测试**
  `AccountPositionSectionDiffTest` 覆盖“只改概览”“只改持仓”“只改挂单”“三者都不变”四种情况，确保每次只刷新变动区块。

- [ ] **Step 3: 在 `AccountPositionActivity` 落地段式绑定**
  首次进入时先绑定页面骨架与空容器，再按顺序提交：概览卡片、持仓列表、挂单列表。后续快照到达时依据 `AccountPositionSectionDiff` 只刷新发生变化的分段。

- [ ] **Step 4: 复用现有 Adapter，但禁止页面层再次排序和拼接**
  `PositionAdapterV2`、`PendingOrderAdapter` 仅负责显示；排序、汇总、摘要文案全部由 `AccountPositionUiModelFactory` 输出完成。

- [ ] **Step 5: 运行分段刷新测试与编译验证**
  Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountPositionSectionDiffTest"`
  Expected: PASS

  Run: `.\gradlew.bat :app:compileDebugJavaWithJavac`
  Expected: BUILD SUCCESSFUL

### Task 4: 把图表页收口为轻量图表页，并把叠加层改为后台预计算 + 三段式提交

**Files:**
- Modify: `app/src/main/res/layout/activity_market_chart.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartPositionSortHelper.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshot.java`
- Create: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactoryTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLayoutResourceTest.java`

- [ ] **Step 1: 先补结构测试，锁定图表页去账户概览/持仓列表后的最终布局**
  `MarketChartLayoutResourceTest` 断言图表页保留 K 线主体、周期切换、图层开关、摘要状态；不再包含账户概览 RecyclerView、当前持仓列表和挂单列表容器。

- [ ] **Step 2: 抽出图表叠加层快照工厂**
  `ChartOverlaySnapshotFactory` 负责把账户快照与图表状态转换成图表叠加层最终结果，包括持仓标注、挂单标注、成本线、顶部摘要文案、签名。所有排序与列表构造在后台线程完成。

- [ ] **Step 3: 将 `MarketChartActivity` 改成三段式提交**
  第一段只提交 K 线主序列与主图布局；第二段提交 `ChartOverlaySnapshot` 到 `KlineChartView`；第三段才更新顶部摘要文案与轻量状态。任何一段都不再顺带刷新整页账户模块。

- [ ] **Step 4: 删掉图表页里的账户概览与持仓列表绑定链**
  移除 `chartOverviewAdapter`、`recyclerChartOverview`、当前持仓列表 RecyclerView、挂单明细列表在图表页内的使用；图表页只保留与图上标注直接相关的数据。

- [ ] **Step 5: 运行图表侧测试**
  Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.ChartOverlaySnapshotFactoryTest" --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest"`
  Expected: PASS

### Task 5: 把账户统计页彻底收口为历史统计页

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/res/layout/activity_account_stats.xml`
- Test: `app/src/test/java/com/binance/monitor/ui/account/AccountStatsLayoutResourceTest.java`

- [ ] **Step 1: 保持当前已确认的区块顺序**
  页面继续是“交易记录 / 交易统计在前，净值结余曲线 / 收益统计在底部”，不再重新塞回实时账户概览或当前持仓。

- [ ] **Step 2: 清掉账户统计页对高频实时块的残留依赖**
  账户统计页只保留历史统计、收益曲线、交易分析；如有仍面向实时概览的刷新分支，一并删掉，避免新的双份高频维护。

- [ ] **Step 3: 跑顺序测试**
  Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest"`
  Expected: PASS

### Task 6: 真机复测、耗电对比与文档同步

**Files:**
- Modify: `CONTEXT.md`
- Modify: `ARCHITECTURE.md`
- Modify: `README.md`

- [ ] **Step 1: 先做构建与安装**
  Run: `.\gradlew.bat :app:assembleDebug`
  Expected: BUILD SUCCESSFUL

  Run: `adb -s 7fab54c4 install -r app\\build\\outputs\\apk\\debug\\app-debug.apk`
  Expected: Success

- [ ] **Step 2: 按与旧方案一致的真机场景复测**
  保持同一台真机、同一条 Tab 切换链路、同一采样时长，输出新目录，例如 `temp/cpu_battery_20260412_account_position_tab`。

- [ ] **Step 3: 采集三类关键证据**
  1. `dumpsys gfxinfo`：看总帧数、jank 数、慢帧占比。
  2. `logcat`：看 `chart_pull load_done`、`chart_pull ui_applied`、`Skipped frames`。
  3. `batterystats`：看 CPU 与 screen 口径变化。

- [ ] **Step 4: 验收标准按结构性目标判断**
  图表页必须不再出现“图上主序列加载完成后，还要继续整页重排账户块”的日志特征；`Skipped frames` 要明显下降，且 `ui_applied` 再明显缩短；账户持仓页切换时不得反向拖慢图表页。

- [ ] **Step 5: 更新文档**
  `CONTEXT.md` 必须同步本轮实施状态和复测结论；由于新增正式页面与职责变更，`ARCHITECTURE.md`、`README.md` 也一并更新。

---

## 方案约束

- 不保留“图表页完整账户模块 + 新账户持仓页”双份正式承载结构。
- 不引入降级渲染、兜底隐藏、启发式节流或补丁式 post-delay 稳定化。
- 账户数据只认 `AccountStatsPreloadManager.Cache` 这一份上游真值，页面只做读模型转换。
- 排序、聚合、摘要拼接、图上标注列表构造都在后台完成，主线程只做最终绑定。
- 实施顺序必须按任务推进；每完成一段就可独立编译、验证、回归。
