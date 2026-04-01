# 账户统计页 Phase B 设计

## 背景

第 1 批已经完成行情持仓页和 K 线核心行为。本批只收口账户统计页，避免和后面的监控页 / 设置页 / 悬浮窗样式交叉返工。

当前账户统计页还有四类明确问题：

1. 隐私入口仍沿用旧的整页遮罩思路，不符合“小眼睛切换，数据改成 `*`”的新交互。
2. 净值/结余三联图左右边距偏大，三张图左右坐标轴也没有完全统一。
3. 月收益三行和左侧年份单元格存在肉眼可见的不对齐。
4. 两张分布图还缺少用户要求的统一坐标对齐和“总数 / 盈利 / 亏损”信息表达。

## 本批目标

本批覆盖需求 2 / 3 / 4 / 5 / 6 / 7 / 8：

1. 全周期历史交易分布图、全周期持仓时间分布图左右坐标轴和全周期总计盈亏图对齐。
2. 月收益表的 1-12 月三行整体上移，并与左侧年份单元格完全对齐。
3. 净值/结余曲线三张图左右尽量拉伸，减少屏幕左右空白。
4. 持仓时间分布图补上总数、盈利数量、亏损数量。
5. 净值线和结余线改成明确不同颜色。
6. 隐私入口改成放在“已连接账户”左侧的小眼睛。
7. 隐私关闭时，账户统计页所有数据都改成 `****`，不再使用整页遮罩。

## 关键决定

### 1. 隐私交互

采用“小眼睛直接切换同一个全局隐私状态”的方案。

- 保留现有 `ConfigManager.KEY_DATA_MASKED` 作为状态存储，不额外造一套新配置。
- 删除账户统计页的整页隐私遮罩。
- 在账户总览标题行加入眼睛图标：
  - 睁眼：正常显示
  - 闭眼：账户统计页所有敏感数据改成 `****`

### 2. 账户统计页隐私显示规则

隐私关闭时采用“保留版式，隐藏数据”的方案：

- 概览标题里的账号、概览指标、当前持仓、挂单、交易记录、交易统计、收益表数值全部改成 `****`
- 三联图、交易盈亏柱状图、散点图、持仓时间分布图保留外框和网格，但实际数据改成中心 `****` 占位
- 图表 tooltip、坐标值、图内数值不再显示真实数据

原因：

- 用户要求“所有账户统计数据均用 `*` 替代”，不能再用整页遮罩。
- 只隐藏文字但继续显示真实曲线形状仍会泄露账户信息，因此图表也要进入占位态。

### 3. 图表布局

三联图和交易统计图统一采用同一套绘图区边距：

- 左边给左轴预留固定宽度
- 右边给右轴标题 / 刻度预留较窄固定宽度
- 比当前版本更靠近屏幕左右边缘

这样可以同时解决：

- 三联图显示区域过窄
- 交易分布图 / 持仓时间图与总计盈亏图左右轴线不齐

### 4. 月收益表对齐方式

保留现有“三行分组”的结构，但把年份单元格高度按三行真实高度计算，不再手写一个偏大的估算值。

这样能直接消除图 1 里左侧年份块和右侧 1-12 月三行的错位。

### 5. 持仓时间分布图表达

沿用现有分桶，不改统计口径，只改图内文案：

- 柱顶显示 `总数`
- 同一桶内部继续保留盈利 / 亏损堆叠
- 顶部说明改成 `总 / 盈 / 亏`

## 涉及文件

- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java`
- `app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java`
- `app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java`
- `app/src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java`
- `app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java`
- `app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/TradeRecordAdapterV2.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/AccountMetricAdapter.java`
- `app/src/main/java/com/binance/monitor/ui/account/adapter/StatsMetricAdapter.java`
- `app/src/main/res/layout/activity_account_stats.xml`
- 新增账户统计隐私显示辅助类与对应单测

## 测试策略

至少验证：

1. 新的账户统计隐私辅助类能把账号、金额、收益表、标题文案统一替换成 `****`
2. 持仓时间分布格式化后的桶文案包含总数 / 盈利 / 亏损
3. 构建后账户统计页能正常编译，现有账户相关单测继续通过
