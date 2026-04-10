# CONTEXT

## 当前正在做什么
- 当前已完成 2026-04-10 这轮“行情持仓当前持仓先空白再刷新”的收口复核。
- `MarketChartActivity` 仍保持“账户叠加层优先用最新内存缓存/本地持久化快照恢复，缓存未就绪时不先画空态”的恢复链路。
- 图表页启动链路已保持后台读取 Room，不再回退到主线程同步读库。
- 最新 APK 已于 2026-04-10 12:16（Asia/Shanghai）重新全量编译、安装并用 ADB 复核：`app/build/outputs/apk/debug/app-debug.apk`。

## 上次停在哪个位置
- 上次停在用户继续反馈“每次打开 APP / 切换 Tab 到行情持仓后，当前持仓会先空白再刷新”。
- 本次继续用 ADB 按“冷启动首页 -> 行情持仓”和“行情持仓 -> 账户统计 -> 行情持仓”两条路径复核。
- 当前两条路径在 `700ms` 界面树里都已直接出现真实持仓摘要，不再先落到“当前暂无持仓”空态。

## 近期关键决定和原因
- 保持“会话有效但账户缓存尚未回填时直接保留上一帧持仓 UI，不先走空态”的恢复边界，不再新增降级分支。
- 保持“本地持久化快照恢复走后台线程”的实现，不回退到任何主线程同步读库方案。
- 这次 ADB 复核只用于确认真实前台表现；临时诊断代码已全部移除，最终安装包保持干净。

## 当前验证状态
- `.\gradlew.bat testDebugUnitTest --tests com.binance.monitor.ui.chart.MarketChartPositionPanelSourceTest --tests com.binance.monitor.ui.chart.MarketChartPositionSortSourceTest --tests com.binance.monitor.ui.chart.MarketChartAccountOverlaySourceTest --tests com.binance.monitor.ui.chart.MarketChartOverlayRestoreSourceTest --tests com.binance.monitor.ui.chart.MarketChartLaunchAnrSourceTest --tests com.binance.monitor.ui.chart.MarketChartCacheRestoreSourceTest`
- `.\gradlew.bat :app:assembleDebug -x lint --rerun-tasks`
- `adb install -r app\build\outputs\apk\debug\app-debug.apk`
- ADB 复核：
- 冷启动首页后点击“行情持仓”，`700ms` 的界面树已包含“持仓盈亏…”，未出现“当前暂无持仓”
- `行情持仓 -> 账户统计 -> 行情持仓` 返回后，`700ms` 的界面树同样已包含“持仓盈亏…”，未出现“当前暂无持仓”
- 返回场景里前台仍为同一个 `MarketChartActivity` 实例，任务栈保持 `MainActivity(root) -> MarketChartActivity(top)`
