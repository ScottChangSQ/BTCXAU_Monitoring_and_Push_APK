# 架构性能配色清理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 收口旧交易页架构、压掉图表高频重复重算、统一主题色入口并清理无效日志。

**Architecture:** 先处理高收益的主链问题：旧 `MarketChartActivity` 收桥、图表 overlay 签名和历史成交锚点优化、账户运行态缓存去掉高频读回；随后统一颜色 token 和 runtime palette，最后关闭调试日志并补文档。

**Tech Stack:** Android Java、XML、ViewBinding、Room、Python `server_v2.py`

---

### Task 1: 旧交易页收口

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`

- [ ] 删除不可达旧实现，只保留桥接逻辑和兼容常量。
- [ ] 保证旧入口 extras 继续透传到 `MainHostActivity` 的交易 Tab。

### Task 2: 图表叠加层性能修复

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartOverlaySnapshotFactory.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/HistoricalTradeAnnotationBuilder.java`
- Modify: `app/src/main/java/com/binance/monitor/runtime/account/AccountStatsPreloadManager.java`

- [ ] 把 overlay 签名改成基于可见窗口和相关数据内容。
- [ ] 把历史成交锚点定位改为二分查找。
- [ ] 去掉运行态应用后的高频“写库再读回”。
- [ ] 跑图表相关单测验证。

### Task 3: 配色统一

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/main/res/drawable/bg_level_warn.xml`
- Modify: `app/src/main/res/drawable/bg_level_info.xml`
- Modify: `app/src/main/res/drawable/bg_level_error.xml`
- Modify: `app/src/main/res/drawable/bg_tab_wechat_selected.xml`
- Modify: `app/src/main/res/drawable/bg_tab_wechat_unselected.xml`
- Modify: `app/src/main/res/drawable/ic_chevron_right.xml`

- [ ] 更新统一深色主题 token。
- [ ] 同步运行时 palette。
- [ ] 替换明显的资源硬编码色值。

### Task 4: 日志清理

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/util/ChainLatencyTracer.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`

- [ ] 默认关闭链路追踪日志。
- [ ] 去掉地址诊断和远程会话 debug。
- [ ] 去掉交易可见性 debug。

### Task 5: 文档与验证

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `bridge/mt5_gateway/server_v2.py`

- [ ] 记录本轮架构/性能/配色/日志收口。
- [ ] 调整服务端 market candles 缓存默认值，使其略长于 stream 推送节奏。
- [ ] 运行 `./gradlew assembleDebug`。
- [ ] 运行图表相关单测。
- [ ] 安装到设备 `7fab54c4`。
