# 2026-04-15 主壳固定路径验收摘要

- 设备：7fab54c4
- 路径：行情监控 -> 行情持仓 -> 账户统计 -> 账户持仓 -> 设置 -> 回到账户统计（循环 5 轮）
- 入口：MainActivity 首启直接拉起 MainHostActivity
- 顶层 Activity：MainHostActivity
- logcat：未出现旧底部页 Displayed，也未出现新的 AndroidRuntime/FATAL EXCEPTION
- gfxinfo：Total frames rendered = 2035，Janky frames = 343 (16.86%)，P90 = 19ms，P95 = 27ms，P99 = 150ms
- batterystats：appId = 10518，对应 UID u0a518；短路径采样约 cpu = 1.19 mAh，wifi = 0.0181 mAh
