# 2026-04-18 字号体系统一设计

## 目标

在不改变业务功能的前提下，把 APP 当前分散、膨胀的字号体系一次性收口成可长期维护的统一规则。

这次设计必须同时满足 4 条硬约束：

1. 不允许继续在 XML 或 Java 里硬编码字号。
2. 不允许再保留 `8.2sp / 8.8sp / 10.5sp` 这类补缝字号。
3. 不允许任何页面、自定义 View、图表或悬浮窗绕过 `TextAppearance` 自行定义字号。
4. 所有字号真值统一为 `sp`。

## 当前问题

### 1. 现在不是“一套字号”，而是两套体系长期并存

- 一套是 `styles.xml` 里的语义文本体系，已经有页面标题、模块标题、正文、元信息、控件等层级。
- 另一套是布局里直接写 `textSize="10sp/11sp/12sp/13sp"`，以及 Java / Canvas 里直接 `setTextSize(...)`、`paint.setTextSize(...)` 的局部实现。

结果是“看起来像统一过”，但每个页面又都能各自偷偷长出一档新字号。

### 2. 现有字号数量已经超出可控范围

- 如果只看 `styles.xml`，当前主体系大致是 9 档。
- 如果把布局直写、运行时 `setTextSize(...)`、图表 `Paint` 都算进去，当前全项目实际已经扩散到约 21 个不同字号值。
- 当前最容易继续扩散的区域，是图表、自定义表格、悬浮窗、分析页统计表和运行时动态创建的弹窗内容。

### 3. 当前“不统一”的根因不是缺少样式，而是缺少强制入口

- XML 里的普通 `TextView` 可以走 `textAppearance`。
- 自定义 View、Canvas、动态图表、运行时创建的 `TextView` 没有统一的“字号读取入口”，所以开发时最方便的做法就变成了“直接写一个 float”。
- 一旦允许这条路径存在，后续任何密集场景都会自然回到补缝字号。

## 设计原则

### 1. 先统一规则，再统一数字

这次设计的重点不是“挑几个好看的字号”，而是先把字号的定义权收回到单一入口。

后续任何文字渲染路径都只能做两件事：

- 使用某个 `TextAppearance`
- 从某个 `TextAppearance` 读取字号后应用到 `Paint`

除此之外，不允许第三种路径。

### 2. 统一到 6 档，而不是继续保留“语义 + 特例 + 微调”

正式字号表固定为 6 档：

- `22sp`
- `18sp`
- `16sp`
- `14sp`
- `12sp`
- `10sp`

其中 `10sp` 是这次方案里唯一允许的最小微字档。

### 3. 最小字号允许下探到 `10sp`，但必须严格封边

这次不采用“最小 `11sp`”而采用“最小 `10sp`”，原因是当前图表、悬浮窗、超密集表头确实存在高密度场景。

但 `10sp` 只能作为唯一正式的小字档，不能继续向下扩散。

允许使用 `10sp` 的场景：

- 图表轴标签
- 图表交叉线标签
- 图表角标 / 外推标记 / 轻量提示
- 悬浮窗辅助小字
- 超密集表头

不允许使用 `10sp` 的场景：

- 正文
- 常规按钮
- 普通表单输入
- 卡片主内容
- 主要数据值

## 正式字号表

### 一级：顶层标题 / 顶层焦点值

- `22sp`

使用范围：

- 页面总标题
- 首页或主视图的一级焦点数字

代表含义：

- 进入页面后必须第一眼看到的文字或数字

### 二级：关键大数值 / 关键弹窗标题

- `18sp`

使用范围：

- 核心统计主数值
- 关键弹窗标题
- 强调值

代表含义：

- 比正文明显更强，但不抢页面总标题

### 三级：模块标题

- `16sp`

使用范围：

- 模块标题
- 分组标题
- 主要区块头部

代表含义：

- 页面里的结构分组层级

### 四级：正文与主标签

- `14sp`

使用范围：

- 正文
- 主要标签
- 输入框文字
- 普通卡片主说明

代表含义：

- 默认阅读层

### 五级：控件与次级信息

- `12sp`

使用范围：

- 按钮文字
- 筛选控件
- 次级说明
- 普通列表辅助信息
- 普通表格单元格

代表含义：

- 仍需正常阅读，但层级低于正文

### 六级：唯一微字档

- `10sp`

使用范围：

- 图表密集标记
- 悬浮窗辅助字
- 超密集表头

代表含义：

- 只在空间密度极高时使用

## TextAppearance 设计

### 1. 保留“语义名字”，但所有语义样式只能映射到 6 档

后续不需要把所有样式名字删掉。

可以保留当前项目易理解的语义样式名，但这些样式必须全部继承自 6 个正式字号档位之一。

推荐新增 6 个基础字号样式：

- `TextAppearance.BinanceMonitor.Scale.PageHero` -> `22sp`
- `TextAppearance.BinanceMonitor.Scale.ValueHero` -> `18sp`
- `TextAppearance.BinanceMonitor.Scale.Section` -> `16sp`
- `TextAppearance.BinanceMonitor.Scale.Body` -> `14sp`
- `TextAppearance.BinanceMonitor.Scale.Compact` -> `12sp`
- `TextAppearance.BinanceMonitor.Scale.Dense` -> `10sp`

在此基础上保留或重建语义样式：

- `PageTitle` 继承 `Scale.PageHero`
- `Value` 继承 `Scale.ValueHero`
- `SectionTitle` 继承 `Scale.Section`
- `Body / SectionLabel / BodyCompact` 继承 `Scale.Body`
- `Control / Caption / Meta / ValueCompact / ControlCompact` 继承 `Scale.Compact`
- `Micro / Tiny / ChartDense / OverlayDense` 继承 `Scale.Dense`

### 2. 名字可以多，字号不能再多

这次真正要收口的是“实际字号值”，不是“语义命名数量”。

因此允许出现多个语义样式名，但这些样式最终只能映射到 6 个正式字号值。

## 技术方案

### 1. XML 文字：只允许 `textAppearance`

布局文件中：

- 不允许继续出现 `android:textSize="..."`
- 不允许继续出现 `android:textSize="@dimen/..."`
- 统一改为 `android:textAppearance="@style/..."`

### 2. 运行时 TextView：统一走 TextAppearance 应用入口

对于 Java 中动态创建的 `TextView`、`Button`、`EditText`、下拉项文字：

- 不再直接 `setTextSize(...)`
- 统一调用共享 helper，例如：
  - `TextViewCompat.setTextAppearance(...)`
  - 或项目内封装的 `UiPaletteManager.applyTextAppearance(...)`

### 3. 自定义 View / Canvas：允许读样式，不允许定义字号

图表和自定义绘制没有办法直接“套上 `TextAppearance`”，但仍然不能自己定义字号。

统一方案是新增共享解析入口，例如：

- `TextAppearanceScaleResolver.resolveTextSizePx(context, @StyleRes int textAppearanceResId)`

然后 `Paint` 只能从这个入口取值：

- `paint.setTextSize(TextAppearanceScaleResolver.resolveTextSizePx(...))`

这样自定义绘制仍然由 `TextAppearance` 决定，而不是由局部 float 决定。

### 4. 统一替换 `dp` 文字尺寸

当前存在用 `dp` 定义文字尺寸的场景，这次全部移除。

原因：

- `dp` 是图形尺寸，不是文字语义尺寸
- 会和系统字体缩放逻辑脱钩
- 会破坏“统一由 `TextAppearance + sp` 决定字号”的目标

## 影响范围

### 1. 资源层

- `app/src/main/res/values/styles.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/dimens.xml`

### 2. XML 布局层

当前明确带有字号硬编码的主文件：

- `app/src/main/res/layout/activity_account_position.xml`
- `app/src/main/res/layout/activity_account_stats.xml`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/activity_market_chart.xml`
- `app/src/main/res/layout/activity_settings_detail.xml`
- `app/src/main/res/layout/content_account_position.xml`
- `app/src/main/res/layout/content_account_stats.xml`
- `app/src/main/res/layout/content_settings.xml`
- `app/src/main/res/layout/dialog_abnormal_threshold_settings.xml`
- `app/src/main/res/layout/dialog_indicator_params.xml`
- `app/src/main/res/layout/dialog_trade_command.xml`
- `app/src/main/res/layout/layout_floating_window.xml`

### 3. 运行时和自定义绘制层

- `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- `app/src/main/java/com/binance/monitor/ui/widget/ThemedNumberPicker.java`
- `app/src/main/java/com/binance/monitor/ui/chart/KlineChartView.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartScreen.java`
- `app/src/main/java/com/binance/monitor/ui/chart/MarketChartTradeDialogCoordinator.java`
- `app/src/main/java/com/binance/monitor/ui/floating/FloatingWindowManager.java`
- `app/src/main/java/com/binance/monitor/ui/market/MarketMonitorPageRuntime.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountSessionDialogController.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsScreen.java`
- `app/src/main/java/com/binance/monitor/ui/account/AccountStatsBridgeActivity.java`
- `app/src/main/java/com/binance/monitor/ui/account/DailyReturnChartView.java`
- `app/src/main/java/com/binance/monitor/ui/account/DrawdownChartView.java`
- `app/src/main/java/com/binance/monitor/ui/account/EquityCurveView.java`
- `app/src/main/java/com/binance/monitor/ui/account/HoldingDurationDistributionView.java`
- `app/src/main/java/com/binance/monitor/ui/account/PositionRatioChartView.java`
- `app/src/main/java/com/binance/monitor/ui/account/TradeDistributionScatterView.java`
- `app/src/main/java/com/binance/monitor/ui/account/TradePnlBarChartView.java`
- `app/src/main/java/com/binance/monitor/ui/account/TradeWeekdayBarChartView.java`

## 验证方案

### 1. 资源合同测试

新增或更新源码/资源测试，锁定以下规则：

- `styles.xml` 中所有 `android:textSize` 只能来自 6 个正式字号值
- 布局文件不允许继续出现 `android:textSize=`
- Java 代码不允许继续出现带数字字面量的 `setTextSize(...)`
- Java 代码不允许继续出现 `paint.setTextSize(dp(...))`
- 资源中不允许继续存在文字字号用 `dp`

### 2. 主链回归测试

至少覆盖：

- 主题 token 测试
- 图表布局资源测试
- 分析页布局资源测试
- 悬浮窗主题/源码测试
- `KlineChartView` 源码测试

### 3. 构建验证

- `:app:testDebugUnitTest`
- `:app:assembleDebug`

## 非目标

这次设计不做以下事情：

- 不重做页面布局
- 不改业务逻辑
- 不增加新的视觉主题
- 不以“缩小字号”解决布局本身拥挤的问题

如果某个区域在统一后放不下，应该通过减少标签密度、缩短文案或增大可用空间解决。

## 预期结果

- 全 APP 实际字号值只剩 6 档
- 所有文字字号真值只保留在 `TextAppearance` 中
- 所有自定义 View 与图表都通过同一个样式解析入口取字号
- XML / Java / Canvas 三条路径不再各自长出新字号
- 后续如果有人再写新字号，会直接被源码测试拦住
