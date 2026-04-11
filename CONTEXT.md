# CONTEXT

## 当前正在做什么
- 当前在做 Android 端真机性能 / CPU / 耗电排查，重点是账户页、图表页和顶层 Tab 切换时的主线程负载。
- 本轮已把账户页“次屏区块”改成首帧真正绘制后再挂载，并修掉首版 `OnDrawListener` 在真机上的移除时机崩溃。
- 本轮已完成一组 ADB 受控场景采样，产物在 `temp/cpu_battery_20260411`。

## 上次停在哪个位置
- 上次停在账户页卡顿优化后继续深挖，用户要求进一步分析“目前 APP 的 CPU 使用和耗电情况如何”。
- 当前已拿到 `top`、`cpuinfo`、`batterystats`、`gfxinfo`、`logcat`，正在按页面和后台服务分别归因。

## 近期关键决定和原因
- CPU / 耗电分析继续坚持“真机受控场景 + 代码位置交叉归因”原则，不根据体感下结论。
- 账户页延后挂载必须落到首帧之后，而不是普通 `post()`，否则首屏测量仍会被长页面拖重。
- 当前不采用降级、兜底或补丁式节流；若继续修，优先做结构性减重和调度频率收口。

## 当前验证状态
- 定向测试通过：`.\gradlew.bat :app:testDebugUnitTest --tests "com.binance.monitor.ui.account.AccountStatsBridgeSnapshotSourceTest.accountPageShouldDeferSecondarySectionsUntilAfterFirstFrame"`
- 编译通过：`.\gradlew.bat :app:compileDebugJavaWithJavac`
- 调试包已重新安装真机：`.\gradlew.bat :app:installDebug`
- 真机 CPU / 耗电采样目录：`temp/cpu_battery_20260411`
- 当前关键结论：
  - 32.6 秒模拟电池场景下，`batterystats` 估算 `com.binance.monitor` 耗电约 `2.27 mAh`，其中屏幕约 `1.41 mAh`、CPU 约 `0.868 mAh`。
  - `top` 采样显示账户页瞬时 CPU 峰值最高，图表页次之，设置页很轻。
  - `gfxinfo` 显示 248 帧里 39 帧 jank（15.73%），慢帧主因仍是主线程和 draw command。
  - `logcat` 仍显示图表页存在 `chart_pull phase=ui_applied durationMs=1813` 和明显 `Skipped frames`。
