# Kline Patch And Server UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 K 线链路收口为“闭合历史快照 + 最新 1 条 patch”，并新增一个可在本机/内网访问的轻量 Web 面板来管理 MT5 网关与相关进程。

**Architecture:** Android 图表页继续保留本地预显示与持久化，但最终真值改成服务端返回的闭合 candles 与 latestPatch，且仅允许 `1d` 及以下周期吃分钟实时 patch。服务端在现有 FastAPI 进程内新增管理与面板模块，页面通过静态 HTML + JS 调用本地管理接口，实现状态展示、配置编辑、异常规则配置和 Windows 侧常用进程管理。

**Tech Stack:** Java + Android unit tests + Room + FastAPI + Python unittest + HTML/CSS/JS + PowerShell

---

### Task 1: 锁定 K 线重构回归面

**Files:**
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartRefreshHelperTest.java`
- Modify: `app/src/test/java/com/binance/monitor/ui/chart/ChartWarmDisplayPolicyHelperTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/chart/MarketChartLatestPatchPolicySourceTest.java`

- [ ] 写失败测试，覆盖 `1m` 短尾部不可直接跳过整窗、长周期不可直接吃分钟 patch、图表页需要显式接入 long-interval patch 限制
- [ ] 运行这组测试，确认失败点落在当前跳过策略和源码缺失
- [ ] 实现最小代码让测试通过
- [ ] 复跑这组测试

### Task 2: 收口 Android 图表真值链路

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartRefreshHelper.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/ChartWarmDisplayPolicyHelper.java`

- [ ] 调整刷新计划，让 `1m` 只有在本地窗口已达目标数量时才允许纯 patch 跳过整窗
- [ ] 调整图表页实时补尾入口，只允许 `1d` 及以下周期从分钟尾部派生 patch
- [ ] 保持 latestPatch 只走内存展示，闭合 candles 继续走 Room 持久化
- [ ] 运行相关 Android 单测验证行为

### Task 3: 锁定服务端 market patch 合约

**Files:**
- Modify: `bridge/mt5_gateway/tests/test_v2_market_pipeline.py`
- Modify: `bridge/mt5_gateway/tests/test_v2_contracts.py`

- [ ] 写失败测试，覆盖 `/v2/market/candles` 需要返回闭合 candles + latestPatch
- [ ] 运行 Python 单测确认当前因为 `latest_patch=None` 而失败
- [ ] 实现最小代码让测试通过
- [ ] 复跑对应 Python 单测

### Task 4: 实现服务端 latestPatch 输出

**Files:**
- Modify: `bridge/mt5_gateway/server_v2.py`
- Modify: `bridge/mt5_gateway/v2_market.py`

- [ ] 为 `/v2/market/candles` 接入 Binance 当前未闭合 patch 获取与拼装
- [ ] 保证历史闭合 candles 与 latestPatch 分层返回
- [ ] 不破坏现有 startTime/endTime 分页语义
- [ ] 运行 Python 单测验证

### Task 5: 锁定 Web 管理面板接口

**Files:**
- Create: `bridge/mt5_gateway/tests/test_admin_panel.py`

- [ ] 写失败测试，覆盖状态页、配置读写、异常规则读写、缓存清理、进程管理命令解析
- [ ] 运行 Python 单测确认失败
- [ ] 实现最小代码让测试通过
- [ ] 复跑对应 Python 单测

### Task 6: 实现 FastAPI 管理接口与静态面板

**Files:**
- Create: `bridge/mt5_gateway/admin_panel.py`
- Create: `bridge/mt5_gateway/static/admin/index.html`
- Create: `bridge/mt5_gateway/static/admin/app.js`
- Create: `bridge/mt5_gateway/static/admin/styles.css`
- Modify: `bridge/mt5_gateway/server_v2.py`

- [ ] 新增管理模块，统一处理 Windows 进程控制、`.env` 读写、异常规则配置、缓存清理、状态聚合
- [ ] 在 FastAPI 内挂载 `/admin` 页面和 `/api/admin/*` 接口
- [ ] 面板提供中文状态卡片和常用操作按钮
- [ ] 支持网关、MT5 终端、Caddy、Nginx 的查看与启停
- [ ] 运行对应 Python 单测验证

### Task 7: 更新项目记录与最终验证

**Files:**
- Modify: `CONTEXT.md`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`

- [ ] 更新本轮进度、架构与运行说明
- [ ] 运行 Android 相关单测
- [ ] 运行 Python 相关单测与语法检查
- [ ] 编译 `:app:assembleDebug`
