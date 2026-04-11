# CONTEXT

## 当前正在做什么
- 当前继续在迁移后版本上做页面结构收口，重点是账户统计页与行情持仓页的区块顺序调整。
- 本轮已把账户统计页的历史分析 / 收益统计整体压到页面底部，并把行情持仓页的账户概览补回到当前持仓区块底部。
- 已按确认方向输出正式落地方案，核心是新增“账户持仓”Tab 承接账户概览与当前持仓 / 挂单，并把图表页收口为轻量图表页。
- 真机复测结论仍保留在 `temp/cpu_battery_20260412_post_migration_nav`，旧基线仍保留 `temp/cpu_battery_20260411` 供对比。

## 上次停在哪个位置
- 上次停在迁移后版本真机复测完成处，还没有把两个页面的区块展示顺序按新要求重新整理。
- 这次已补完页面顺序调整，若继续推进，应回到图表页慢帧归因或继续做真机验收。
- 当前新增停点：正式实施计划已写入 `docs/superpowers/plans/2026-04-12-account-position-tab-performance.md`，尚未开始按计划落代码。

## 近期关键决定和原因
- CPU / 耗电分析继续坚持“真机受控场景 + 代码位置交叉归因”原则，不根据体感下结论。
- 账户页延后挂载必须落到首帧之后，而不是普通 `post()`，否则首屏测量仍会被长页面拖重。
- 当前已验证版本里，实时账户总览统一只放在图表页；下一阶段正式方案会把它迁移到新的“账户持仓”页，避免图表页继续承载高频大区块。
- 迁移后复测继续使用真实底部 Tab 导航，不走未导出的内部 Activity 直启路径，保证场景和用户实际操作一致。
- 账户统计页区块顺序不再靠静态 XML 硬搬移，改为在页面启动时调用现有 `placeCurveSectionToBottom()` 做一次确定性重排，避免大块布局改动带来的资源回归。
- 当前已验证版本里，行情持仓页仍保留账户概览，且顺序固定为“当前持仓 / 挂单在前，账户概览在后”；下一阶段会把这整块迁移到新的“账户持仓”Tab。
- 当前不采用降级、兜底或补丁式节流；若继续修，优先做结构性减重和调度频率收口。
- 图表页慢帧的正式收口方向已确定为“职责拆页 + 后台预计算 + 分阶段渲染”，而不是继续在图表页堆叠账户概览和持仓模块。
- 最终页面职责改为：账户统计页只保留历史统计；新增账户持仓页承接账户概览与当前持仓 / 挂单；图表页只保留 K 线主体、图上标注与轻量状态。

## 当前验证状态
- 定向测试通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeOverviewSourceTest"`
- 定向测试通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartPositionSummarySourceTest"`
- 编译通过：`.\gradlew.bat :app:compileDebugJavaWithJavac`
- 新 APK 编译通过：`.\gradlew.bat :app:assembleDebug`
- 新 APK 已安装到真机 `7fab54c4`：`adb -s 7fab54c4 install -r app\build\outputs\apk\debug\app-debug.apk`
- 真机 CPU / 耗电采样目录：`temp/cpu_battery_20260411`
- 真机迁移后复测目录：`temp/cpu_battery_20260412_post_migration_nav`
- 页面顺序测试通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsLayoutResourceTest"`
- 页面顺序测试通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.chart.MarketChartLayoutResourceTest"`
- 编译通过：`.\gradlew.bat :app:compileDebugJavaWithJavac`
- 当前关键结论：
  - 迁移后复测已确认真实进入 `MainActivity -> AccountStatsBridgeActivity -> MarketChartActivity -> SettingsActivity`。
  - 迁移后约 31.7 秒场景下，新复测 `batterystats` 显示全局 `screen=1.41 mAh`、`cpu=1.33 mAh`；应用 UID `u0a512` 为 `2.07 mAh`，其中 `screen=1.29 mAh`、`cpu=0.788 mAh`。
  - 旧基线约 32.7 秒场景下，全局 `screen=1.46 mAh`、`cpu=1.88 mAh`；但旧文件里屏幕耗电与应用 CPU 被拆到不同 UID，和新文件的 UID 口径不完全一致，因此应用总耗电只适合做参考，不宜直接硬比。
  - `gfxinfo` 对比：旧基线 248 帧 / 39 帧 jank（15.73%），新复测 205 帧 / 34 帧 jank（16.59%）；绝对慢帧数略降，但慢帧占比仍高。
  - `logcat` 对比：账户页 `on_create_total` 从 181ms 降到 174ms；图表页 `chart_pull load_done` 从 1206ms 降到 642ms，`ui_applied` 从 1813ms 降到 1382ms，但仍出现 `Skipped 57 frames`。
  - 账户统计页当前顺序为“交易记录 / 交易统计在前，历史分析 / 收益统计在底部”。
  - 行情持仓页当前顺序为“当前持仓 / 挂单在前，账户概览在底部”。
