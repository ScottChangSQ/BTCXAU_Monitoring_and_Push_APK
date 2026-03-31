# Room Persistence Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace file-based chart/account caches with Room-backed persistence and complete the 8 requested UI/behavior updates.

**Architecture:** Add a focused Room database layer for chart history and MT5 snapshot data, then route chart, account, floating window, settings, and monitor UI through repositories that expose simple read/write operations. Keep network clients intact, but move local state ownership out of activities into the database-backed repositories.

**Tech Stack:** Java, Android ViewBinding, Room, LiveData, JUnit4, Gradle Kotlin DSL

---

## File Structure

- Create: `app/src/main/java/com/binance/monitor/data/local/db/AppDatabase.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/AppDatabaseProvider.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/entity/KlineHistoryEntity.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/entity/TradeHistoryEntity.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/entity/PositionSnapshotEntity.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/entity/PendingOrderSnapshotEntity.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/entity/AccountSnapshotMetaEntity.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/dao/KlineHistoryDao.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/dao/TradeHistoryDao.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/dao/AccountSnapshotDao.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java`
- Create: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionPnlItem.java`
- Create: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/res/layout/layout_floating_window.xml`
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`
- Test: `app/src/test/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepositoryTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/floating/FloatingPositionAggregatorTest.java`
- Test: `app/src/test/java/com/binance/monitor/ui/settings/CacheSectionClassifierTest.java`

### Task 1: 文档切换到 Room 方案

**Files:**
- Modify: `docs/superpowers/specs/2026-03-30-app-cache-refresh-floating-design.md`
- Modify: `CONTEXT.md`
- Create: `docs/superpowers/plans/2026-03-30-room-persistence-refactor.md`

- [ ] **Step 1: 更新设计文档与上下文**
- [ ] **Step 2: 写出本计划文档**
- [ ] **Step 3: 自查文档无占位符与冲突**

### Task 2: 为核心纯逻辑先补失败测试

**Files:**
- Create: `app/src/test/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepositoryTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/floating/FloatingPositionAggregatorTest.java`
- Create: `app/src/test/java/com/binance/monitor/ui/settings/CacheSectionClassifierTest.java`

- [ ] **Step 1: 写历史 K 线合并测试，验证旧数据保留且按 openTime 去重**
- [ ] **Step 2: 写悬浮窗持仓聚合测试，验证同产品盈亏求和**
- [ ] **Step 3: 写缓存分项分类测试，验证不同清理动作只影响对应类别**
- [ ] **Step 4: 运行新增测试，确认先失败**

### Task 3: 接入 Room 基础设施

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/AppDatabase.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/AppDatabaseProvider.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/entity/*.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/dao/*.java`

- [ ] **Step 1: 添加 Room 依赖和注解处理**
- [ ] **Step 2: 建立数据库实体**
- [ ] **Step 3: 建立 DAO**
- [ ] **Step 4: 建立数据库入口**
- [ ] **Step 5: 运行编译，确认 Room 代码生成正常**

### Task 4: 实现仓库层并让测试转绿

**Files:**
- Create: `app/src/main/java/com/binance/monitor/data/local/db/repository/ChartHistoryRepository.java`
- Create: `app/src/main/java/com/binance/monitor/data/local/db/repository/AccountStorageRepository.java`
- Create: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionPnlItem.java`
- Create: `app/src/main/java/com/binance/monitor/ui/floating/FloatingPositionAggregator.java`

- [ ] **Step 1: 实现历史 K 线合并与写库逻辑**
- [ ] **Step 2: 实现持仓/挂单/交易快照写库逻辑**
- [ ] **Step 3: 实现悬浮窗按产品聚合逻辑**
- [ ] **Step 4: 运行新增测试，确认转绿**

### Task 5: 图表页改走 Room 历史

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`

- [ ] **Step 1: 去掉文件缓存主路径，改成先读 Room 再补最新**
- [ ] **Step 2: 统一启动、切页、切周期三个刷新入口**
- [ ] **Step 3: 修改右侧留白为绘图区 1/7**
- [ ] **Step 4: 确认刷新前不清空图表**

### Task 6: 账户页与悬浮窗改走数据库

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- Modify: `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- Modify: `app/src/main/res/layout/layout_floating_window.xml`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`

- [ ] **Step 1: 网关返回后写入交易历史、持仓快照、挂单快照、账户摘要**
- [ ] **Step 2: 账户页从数据库结果刷新当前持仓与历史交易**
- [ ] **Step 3: 顶部总盈亏改成红绿加大字号**
- [ ] **Step 4: 悬浮窗改成连接状态加分产品盈亏**

### Task 7: 设置页与监控页调整

**Files:**
- Modify: `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- Modify: `app/src/main/res/layout/activity_settings.xml`
- Modify: `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- Modify: `app/src/main/java/com/binance/monitor/service/MonitorService.java`

- [ ] **Step 1: 设置页新增三种分项清理**
- [ ] **Step 2: 删除监控页按钮和文案中的手动监控状态**
- [ ] **Step 3: 服务默认设为监控开启**
- [ ] **Step 4: 运行相关测试与构建检查**

### Task 8: 文档与验证

**Files:**
- Modify: `README.md`
- Modify: `ARCHITECTURE.md`
- Modify: `CONTEXT.md`

- [ ] **Step 1: 更新 README 与 ARCHITECTURE 中的新持久层说明**
- [ ] **Step 2: 更新 CONTEXT 到最新落点**
- [ ] **Step 3: 运行单测**
- [ ] **Step 4: 运行 assembleDebug 验证构建**
