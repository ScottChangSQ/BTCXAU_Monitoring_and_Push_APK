# App Room 持久层与刷新链路改造设计

## 1. 背景

当前 App 已经完成韩国服务器接入、MT5 网关接入，以及“后台摘要接口 + 前台全量接口”的第一轮流量优化。但图表刷新、历史数据保留、设置页分项清缓存、悬浮窗展示范围、监控页默认行为这几块仍然存在结构性问题：

- 图表历史仍然走文件缓存，刷新入口分散。
- 历史 K 线与历史交易缺少统一持久层，难以长期保留和分项清理。
- 账户页、悬浮窗、图表页分别维护自己的临时状态，数据来源不统一。
- 悬浮窗仍固定展示 BTC/XAU 行情，不符合“显示 MT5 所有持仓品种盈亏”的目标。
- 行情监控页还保留手动开关，不符合“默认监控”的目标。

用户已经确认本轮采用更彻底的方案：直接重构为数据库化持久层，并指定使用 `Room`，且不迁移旧文件缓存，数据库从空开始。

## 2. 本次目标

本次实现要同时满足以下 8 项需求：

1. 启动 App、切换到 K 线图表页、切换时间周期时，均刷新图表。
2. 图表最右侧 K 线与边框距离增加到图表区域的 `1/7`。
3. 当前持仓界面最上面的总盈亏按红绿显示，并调整文字大小。
4. 刷新图表时保留历史数据，不再先清空后显示。
5. 历史数据拉取后立即存储，后续只增量补充，不自动删除。
6. 设置页“清除缓存”新增“清除历史交易数据”“清除历史行情数据”选项。
7. 悬浮窗显示 MT5 当前所有持仓品种的分产品盈亏。
8. 行情监控界面删除“是否开始监控”按钮，默认监控；悬浮窗同步删除“是否监控”信息，仅保留连接状态。

## 3. 已确认边界

- 本地持久层使用 `Room`，不使用 `SQLiteOpenHelper`。
- 不迁移旧文件缓存，升级后数据库从空开始重新累计。
- 历史行情缓存按“交易对 + 时间周期”保存。
- 只要新版本拉取过的历史行情，就一直保留，不设置自动上限。
- 历史交易数据也长期保留，后续只做增量补充。
- 悬浮窗中的分产品盈亏范围是 MT5 当前实际所有持仓品种，而不是只看 BTC/XAU。

## 4. 方案对比

### 方案 A：继续沿用文件缓存补丁式扩展

优点：

- 改动小。

缺点：

- 历史 K 线、交易历史、当前持仓仍然分散在不同缓存逻辑中。
- 难以优雅支持分项清缓存和统一复用。

### 方案 B：抽象统一仓库但仍保留文件存储

优点：

- 比当前更规整。

缺点：

- 底层仍然不是结构化存储。
- 后续排序、聚合、清理与查询成本高。

### 方案 C：直接改为 Room 数据库持久层（采用）

优点：

- 历史行情、历史交易、当前持仓、挂单快照都可统一管理。
- 后续增量更新、分项清缓存、悬浮窗聚合都更稳定。

缺点：

- 本轮改动面更大。
- 需要新增数据库结构和仓库层。

## 5. 采用方案

采用方案 C：直接重构为 `Room` 数据库持久层。

原因：

- 用户本轮优先目标已经从“小修小补”切换为“底层结构一次理顺”。
- `Room` 更适合当前 Java Android 工程，且后续维护成本低于手写 SQLite。
- 图表页、账户页、悬浮窗都需要共享同一份本地数据，这正是数据库擅长的事情。

## 6. 数据库设计

### 6.1 表结构

本轮最少建立 5 张表：

1. `kline_history`
   - 保存历史 K 线
   - 主键：`symbol + interval + open_time`
   - 用途：图表页历史展示、增量补齐、切周期秒开

2. `trade_history`
   - 保存历史交易记录
   - 主键优先：`deal_ticket`
   - 如果 `deal_ticket` 缺失，则由 `order_id + position_id + close_time + quantity` 生成稳定键
   - 用途：账户页交易历史长期保留

3. `position_snapshot`
   - 保存当前持仓快照
   - 每次账户刷新时整批替换当前状态
   - 用途：账户页当前持仓、悬浮窗分产品盈亏

4. `pending_order_snapshot`
   - 保存当前挂单快照
   - 每次账户刷新时整批替换
   - 用途：账户页挂单展示

5. `account_snapshot_meta`
   - 保存最新一份账户摘要信息
   - 用途：账户页顶部状态、悬浮窗连接附加信息

### 6.2 DAO 设计

DAO 分为三组：

1. `KlineHistoryDao`
   - 查询某交易对某周期的全部历史
   - 批量 upsert 历史 K 线
   - 清空全部历史行情

2. `TradeHistoryDao`
   - 查询全部历史交易
   - 批量 upsert 历史交易
   - 清空全部历史交易

3. `AccountSnapshotDao`
   - 读取当前持仓、当前挂单、账户摘要
   - 刷新持仓快照
   - 刷新挂单快照
   - 刷新账户摘要
   - 清空运行时快照

### 6.3 仓库层设计

新增一个本地仓库层，负责把 Room 和页面逻辑隔开：

- `ChartHistoryRepository`
  - 对图表页暴露：读历史、合并增量、写历史

- `AccountStorageRepository`
  - 对账户页和悬浮窗暴露：写交易历史、写持仓快照、写挂单快照、读取聚合盈亏

这样页面层不再直接管 Room 细节，只处理显示逻辑。

## 7. 首次升级行为

首次升级到 Room 版本时：

- 不导入旧文件缓存。
- 新数据库从空开始。
- 后续所有新拉到的数据直接进入数据库。

这样实现更简单，也符合用户已确认的边界。

## 8. 图表链路设计

### 8.1 进入图表页

`MarketChartActivity` 在以下时机统一刷新：

1. App 启动后首次进入图表页
2. 从其他页面切回图表页
3. 切换时间周期

统一流程：

1. 先异步读取 Room 中当前 `symbol + interval` 的历史 K 线
2. 如果数据库里有数据，先立即绘图
3. 页面不清空已有 K 线
4. 再请求最新缺口数据
5. 增量合并后回写数据库
6. 再次刷新图表

### 8.2 历史保留策略

- 不再裁剪历史 K 线数量。
- 不再使用旧文件缓存作为主来源。
- 去重键为 `openTime`。
- 同一根 K 线只保留最新版本。

### 8.3 图表右侧留白

`KlineChartView` 当前右侧留白逻辑调整为固定使用图表绘图区宽度的 `1/7`。

要求：

- 基于价格绘图区宽度计算。
- 切周期、切品种、刷新后保持一致。
- “回到最新”按钮与倒计时文案的位置要跟随新的右边界重新布局。

## 9. 账户链路设计

### 9.1 请求网关后的写库流程

`AccountStatsBridgeActivity` 每次从网关拿到快照后，不再只更新内存，而是：

1. 将历史交易 upsert 到 `trade_history`
2. 将当前持仓整批刷新到 `position_snapshot`
3. 将当前挂单整批刷新到 `pending_order_snapshot`
4. 将账户摘要写入 `account_snapshot_meta`
5. 然后再从数据库结果刷新界面

### 9.2 历史交易长期保留

- 历史交易以 `deal_ticket` 或稳定组合键去重。
- 后续刷新只增量补充，不自动删除。
- 设置页新增单独入口后，只有用户手动清理时才删除。

### 9.3 总盈亏样式

顶部总盈亏摘要继续保留现有信息结构，但增强视觉层级：

- 盈利显示绿色，亏损显示红色，持平显示主文字颜色。
- 总盈亏数字字号大于其余摘要文字。
- 其余说明保留较小字号。

## 10. 悬浮窗设计

悬浮窗从“BTC/XAU 行情窗”调整为“连接状态 + MT5 持仓盈亏窗”。

### 10.1 展示内容

- 删除“是否监控”状态文案
- 保留连接状态
- 展示当前所有 MT5 持仓品种的分产品盈亏

### 10.2 数据来源

悬浮窗不再依赖行情仓库作为主展示来源，而是读取数据库中最新的：

- `position_snapshot`
- `account_snapshot_meta`

然后按产品聚合：

- 产品名
- 总浮盈亏

### 10.3 空状态

如果拿不到持仓数据：

- 仍显示连接状态
- 显示“暂无持仓数据”

## 11. 行情监控页设计

`MainActivity` 删除“是否开始监控”按钮，监控逻辑改为默认启用。

行为定义：

- 服务启动即默认处于监控状态。
- 页面不再提供手动开关入口。
- 页面上保留连接状态与更新时间。
- 悬浮窗不再显示监控状态文案。

注意：这里删除的是用户可见开关，不是删除后台监控能力。

## 12. 设置页设计

“缓存管理”拆成 3 个独立清理动作：

1. 清除历史行情数据
   - 只删除 `kline_history`

2. 清除历史交易数据
   - 只删除 `trade_history`

3. 清除运行时缓存
   - 删除 `position_snapshot`
   - 删除 `pending_order_snapshot`
   - 删除 `account_snapshot_meta`
   - 删除原有运行时 SharedPreferences 和临时缓存

这样可以避免“一键全清导致历史都没了”。

## 13. 模块改动范围

预计涉及以下模块：

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/java/com/binance/monitor/data/local/...` 新增 Room 数据库与仓库
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartActivity.java`
- `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsPreloadManager.java`
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
- `app/src/main/java/com/binance/monitor/data/repository/MonitorRepository.java`
- `app/src/main/java/com/binance/monitor/ui/settings/SettingsActivity.java`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_settings.xml`
- `app/src/main/res/layout/layout_floating_window.xml`

## 14. 错误处理

- 数据库读取失败时，不中断页面显示，按“本地无数据”处理。
- 增量刷新失败时，保留当前已显示内容，不清空页面。
- 悬浮窗拿不到持仓时，降级为只显示连接状态。
- 清理缓存操作必须明确说明删除范围。

## 15. 测试策略

至少覆盖以下验证：

1. 历史 K 线合并后不会丢旧数据，也不会重复保存同一根。
2. 图表切周期时不会先清空图表。
3. 历史交易写入数据库后能去重保留。
4. 持仓聚合逻辑能正确输出“按产品总盈亏”。
5. 清理历史行情只影响 `kline_history`。
6. 清理历史交易只影响 `trade_history`。
7. 行情监控页不再显示监控按钮，默认监控仍然生效。
8. 悬浮窗不再显示“是否监控”，仅显示连接状态和分产品盈亏。

## 16. 非目标

本次不包含以下内容：

- 不迁移旧文件缓存到数据库
- 不改韩国服务器部署结构
- 不改 MT5 网关协议
- 不新增新的后台推送协议

## 17. 实施顺序

推荐顺序：

1. 先接入 Room 依赖，建立数据库、实体、DAO、仓库。
2. 再接入图表页历史读写，替换文件缓存主路径。
3. 再接入账户页交易历史、持仓快照、账户摘要写库。
4. 再改悬浮窗为数据库聚合展示。
5. 再改设置页分项清缓存和监控页默认监控。
6. 最后更新文档并完成验证。
