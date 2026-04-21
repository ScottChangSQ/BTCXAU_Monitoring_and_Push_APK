# BTCXAU 颜色系统统一与遗留清理设计方案

## 1. 背景

这次工作不是继续按页面补颜色，而是先把全 APP 的颜色系统立成单一标准，再按标准清掉遗留色。

当前仓库的真实情况已经比较清楚：

- `colors.xml` 里已经有一套深色主基调。
- `themes.xml`、`styles.xml`、`UiPaletteManager.java` 各自都在参与颜色分配。
- 普通页面的大部分颜色已经开始走共享资源。
- 图表链路仍保留一批独立写死色，形成“第二套系统”。
- 历史遗留里还保留了重复命名色、未使用色、旧主题色和低频资源色。

所以这次不采用“逐页继续修”的方式，而采用两步法：

1. 先建立唯一正式颜色标准。
2. 再按新标准迁移主链并清理遗留。

## 2. 本次方案的目标

本次方案只解决 5 件事：

- 建立唯一的正式颜色真值，不再允许主题层、运行时层、图表层各说各话。
- 统一颜色命名规则、使用规则、统计口径和迁移顺序。
- 保留交易语义原则：买蓝、卖红，盈利绿、亏损红。
- 禁止任何页面、图表、弹层、适配器继续写死颜色字面量。
- 在颜色系统稳定后，清掉重复命名色、未使用色和历史遗留色。

## 3. 非目标

- 不在这份方案中新增业务功能。
- 不在这份方案中重做页面布局结构。
- 不为了统一而抹掉交易语义色。
- 不保留多套主题长期并存。
- 不接受“先临时兼容、后面再看”的颜色补丁路径。

## 4. 总体策略

本次采用“先立标准，再清遗留”的执行策略。

这不是折中方案，而是风险和闭环都更清楚的方案。

执行逻辑如下：

1. 先定义 canonical token，也就是唯一正式颜色集合。
2. 让资源层、主题层、运行时层都只引用这套 canonical token。
3. 先迁移最显眼、最分裂、最容易回流的主链，优先是图表。
4. 等主链全部完成后，再删掉别名色、重复色、未使用色和所有写死色。

这样可以保证：

- 第一阶段就把系统真值立住。
- 第二阶段删除时不会再误删仍在使用的颜色。
- 每一步都可以验证，不需要一次性大爆改后再补洞。

## 5. 颜色系统总原则

### 5.1 单一真值原则

后续所有正式颜色，只能从统一 token 层取值。

不允许以下做法继续存在：

- 在 Java / Kotlin 中直接写 `#RRGGBB`
- 在 Java / Kotlin 中直接写 `0xAARRGGBB`
- 使用 `Color.parseColor(...)`
- 在布局或 drawable 里继续新增字面量颜色
- 在运行时维护一份与资源层不一致的第二套十六进制真值

### 5.2 语义先于色值原则

颜色先定义“表达什么”，再定义“具体是什么值”。

以后不能再出现：

- 同一个蓝色，一会儿表示买入，一会儿表示主按钮
- 同一个红色，一会儿表示卖出，一会儿表示危险删除，一会儿表示亏损
- 页面直接拿“看起来差不多”的颜色去补局部状态

### 5.3 交易语义保留原则

这条是硬约束，不参与后续争论：

- 买入：蓝
- 卖出：红
- 盈利：绿
- 亏损：红

如果后续某些值最终色值接近，也必须在 token 命名上继续区分语义，不能合并成一个模糊名称。

### 5.4 普通系统色与交易语义色分离原则

普通控件的选中、聚焦、激活、可点击，不再使用买入蓝或盈利绿去承担。

系统级交互强调和交易语义要分开。

否则会出现：

- 普通选中态看起来像买入
- 主按钮看起来像交易动作
- 图表以外区域也充满交易语义色

### 5.5 派生色统一原则

半透明色、浅底提示色、覆盖层色都不再视作独立正式颜色。

它们必须从 canonical token 派生，不再长期单独占一个“正式真值”位置。

例如：

- 浅红提示底
- 半透明白分隔线
- 半透明深色浮层
- 图表弹层背景

这些都属于“派生使用”，不属于“正式新增颜色类目”。

## 6. Canonical Token 设计

本次 canonical token 固定为 `14` 个，不再使用区间口径。

第一阶段只允许以下 14 个正式 token 进入资源真值层：

| token | 作用 |
|---|---|
| `bg_app_base` | 页面最底层背景 |
| `bg_panel_base` | 模块、页面区块背景 |
| `bg_card_base` | 卡片、面板主体背景 |
| `bg_field_base` | 输入区、选择区、控件底 |
| `border_subtle` | 弱边框与轻分层基础色 |
| `text_primary` | 主文字 |
| `text_secondary` | 次文字 |
| `text_inverse` | 反色文字 |
| `accent_primary` | 系统主交互强调 |
| `state_warning` | 警示、提醒、异常关注 |
| `trade_buy` | 买入 |
| `trade_sell` | 卖出 |
| `pnl_profit` | 盈利 |
| `pnl_loss` | 亏损 |

以下名称不计入 canonical token，只允许作为 alias / 派生语义存在：

- `divider_subtle`
- `state_danger`
- `state_success`
- `trade_pending`
- `trade_exit`
- `asset_btc`
- `asset_xau`

## 6.1 第一层：基础中性色

这层负责全局骨架，只表达层级，不表达交易语义。

本层保留的正式 token：

| token | 作用 |
|---|---|
| `bg_app_base` | 页面最底层背景 |
| `bg_panel_base` | 模块、页面区块背景 |
| `bg_card_base` | 卡片、面板主体背景 |
| `bg_field_base` | 输入区、选择区、控件底 |
| `border_subtle` | 弱边框、细分层 |
| `text_primary` | 主文字 |
| `text_secondary` | 次文字 |
| `text_inverse` | 反色文字 |

说明：

- 当前仓库里的 `bg_primary / bg_surface / bg_card / bg_input / stroke_card / divider / text_primary / text_secondary / white` 基本都能映射到这层。
- 后续可以保留旧资源名做短期兼容，但正式标准文档里只认这套职责。

## 6.2 第二层：系统语义色

这层表达系统状态，不表达交易买卖关系。

本层保留的正式 token：

| token | 作用 |
|---|---|
| `accent_primary` | 系统主交互强调 |
| `state_warning` | 警示、提醒、异常关注 |

关键决定：

- `accent_primary` 统一采用当前金色主线，不再让普通交互主色继续和买入蓝重叠。
- `state_danger` 和 `state_success` 在第一阶段不单独占 canonical token 名额，只允许作为 alias 语义映射到 `trade_sell` 与 `pnl_profit`。

## 6.3 第三层：交易语义色

这层只负责交易、持仓、成交、盈亏等领域语义。

本层保留的正式 token：

| token | 作用 |
|---|---|
| `trade_buy` | 买入 |
| `trade_sell` | 卖出 |
| `pnl_profit` | 盈利 |
| `pnl_loss` | 亏损 |

关键约束：

- `trade_buy` 和 `accent_primary` 绝不混用。
- `pnl_profit` 和 `state_success` 绝不混用。
- `trade_sell` 和 `state_danger` 命名上绝不合并。
- `trade_pending` 与 `trade_exit` 在第一阶段不单独占 canonical token 名额，只允许作为 alias 或派生规则存在。

## 6.4 第四层：产品身份色

这层不是必须项，而且不进入第一阶段 canonical token。

限制：

- 只能用于产品识别、图例、附加提示
- 不能替代买卖色
- 不能替代盈亏色

## 6.5 第五层：派生色

这层不在 `colors.xml` 中长期扩张独立名字，而是由 resolver 统一派生：

- `alpha(text_primary, 0.90)`
- `alpha(bg_card_base, 0.92)`
- `alpha(border_subtle, 0.72)`
- `surfaceOverlay(bg_card_base, text_primary, ratio)`

凡是下面这些以后都应该尽量转为派生：

- 图表弹窗半透明背景
- 十字线标签背景
- 浮层蒙版
- 浅色日志背景
- 图表提示标签底色

## 7. 命名规则

后续命名统一遵循 4 条规则：

### 7.1 一个名字只能表达一个职责

例如：

- `trade_buy` 只表示买入
- `pnl_profit` 只表示盈利
- `accent_primary` 只表示系统主交互强调

不能再出现：

- `accent_blue` 这种既像系统色，又可能像买入色的名字

### 7.2 不允许“视觉描述型”命名成为正式标准

正式 token 禁止继续新增：

- `blue_1`
- `gold_dark`
- `red_soft`
- `gray_line_2`

视觉值可以存在设计说明里，但不能成为正式接口名。

### 7.3 不允许重复命名同一色值承担不同职责

当前这类情况需要清理：

- `accent_cyan` 与 `accent_blue`
- `text_control_selected` 与 `white`
- `text_control_unselected` 与 `text_secondary`

### 7.4 派生色禁止长期升格为正式 token

如果某颜色只是：

- 某个正式 token 的透明版本
- 某个背景与文字色的混合结果

那它应当通过 resolver 生成，而不是新增一个长期资源名。

## 8. 现有颜色到新体系的映射

| 当前资源/颜色 | 新标准归属 | 处理方式 |
|---|---|---|
| `bg_primary` | `bg_app_base` | 保留语义，后续可兼容映射 |
| `bg_surface` | `bg_panel_base` | 保留语义，后续可兼容映射 |
| `bg_card` | `bg_card_base` | 保留语义 |
| `bg_input` | `bg_field_base` | 保留语义 |
| `stroke_card` | `border_subtle` | 统一为边框语义 |
| `divider` | `divider_subtle` | 统一为分隔语义 |
| `text_primary` | `text_primary` | 保留 |
| `text_secondary` | `text_secondary` | 保留 |
| `white` | `text_inverse` | 不再单独以“white”作为正式职责名 |
| `accent_gold` | `accent_primary` | 统一为系统主强调 |
| `accent_blue` | `trade_buy` 或产品/图表相关 token | 不再继续同时承担系统主交互 |
| `accent_cyan` | 删除 | 与 `accent_blue` 重复 |
| `accent_green` | `pnl_profit` 或 `state_success` | 按场景拆语义，不能继续混用 |
| `accent_red` | `trade_sell` / `pnl_loss` / `state_danger` | 按场景拆语义，不能继续混名 |
| `vintage_*` | 删除 | 不进入正式主链 |
| `grain_overlay` | 删除 | 不进入正式主链 |
| `log_level_*_bg` | 转派生 | 不保留为长期独立真值 |

## 9. 主题与运行时真值收口方案

### 9.1 资源层

资源层以后只做两件事：

- 保存 canonical token 的正式色值
- 提供统一资源出口给主题、样式和运行时使用

真实入口保持在：

- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/styles.xml`

### 9.2 主题层

`themes.xml` 的职责必须收紧成：

- 把 Material 主题位映射到 canonical token
- 不再自行表达第二套颜色逻辑

需要统一的重点：

- `colorPrimary`
- `colorSecondary`
- `colorTertiary`
- `colorSurface`
- `colorControlActivated`
- `statusBarColor`
- `navigationBarColor`

关键决定：

- `colorPrimary` 与运行时 palette 的 `primary` 必须统一成同一套值
- 当前蓝金分叉必须消失

### 9.3 运行时层

`UiPaletteManager.java` 以后只允许承担“运行时取色和派生”职责，不再保存原始十六进制真值。

后续要求：

- palette 可以继续存在，但其输入必须来自资源 token
- `normalizePaletteId(...)` 已经统一回落默认主题，因此没有必要继续维护“资源一套、代码一套”的平行真值
- 所有运行时派生色必须集中在 resolver/helper 统一生成

### 9.4 图表层

图表层是这次最优先的颜色收口对象，因为它现在仍是最明显的第二套系统。

主链涉及文件：

- `KlineChartView.java`
- `ChartOverlaySnapshotFactory.java`
- `MarketChartScreen.java`
- `AbnormalAnnotationOverlayBuilder.java`

图表统一后的目标是：

- 主背景、网格、轴线、十字线、弹层、标签、挂单线、极值线都通过 token 或派生函数得到
- 买卖点、盈亏线、历史退出点全部走正式交易语义 token
- 图表代码中不再出现任何颜色字面量

## 10. 实施阶段与顺序

## 10.1 Phase 0：冻结标准

目标：

- 先把这份 spec 定为唯一设计基线
- 不在实现中再临时改颜色职责

输出：

- 确认 canonical token 清单
- 确认旧资源映射表
- 确认图表主链优先级

## 10.2 Phase 1：建立 canonical 资源层

目标：

- 在 `colors.xml` 中建立最终正式 token 集
- 保留必要兼容映射，但新真值先立住

涉及文件：

- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/styles.xml`

本阶段完成标准：

- 正式 token 集完整可用
- Material 主题映射统一
- 不再新增重复职责资源名

## 10.3 Phase 2：统一运行时取色入口

目标：

- 让 `UiPaletteManager` 不再维护资源外的独立原始色值
- 统一派生色生成方式

涉及文件：

- `app/src/main/java/com/binance/monitor/ui/theme/UiPaletteManager.java`
- 如有需要，新增专门的颜色 resolver / alpha helper

本阶段完成标准：

- 普通页面、弹层、底部导航、分段按钮、悬浮窗都通过统一入口取色
- `UiPaletteManager` 中不存在原始十六进制颜色真值

## 10.4 Phase 3：图表主链迁移

目标：

- 清除图表第二套系统

实施顺序：

1. `KlineChartView.java`
2. `ChartOverlaySnapshotFactory.java`
3. `MarketChartScreen.java`
4. `AbnormalAnnotationOverlayBuilder.java`

原因：

- `KlineChartView` 是图表最大色源
- `ChartOverlaySnapshotFactory` 是交易标注真值
- `MarketChartScreen` 是绑定链残留色入口
- `AbnormalAnnotationOverlayBuilder` 是特殊渐变异常链

本阶段完成标准：

- 图表相关源码字面量颜色清零
- 图表语义色全部回到正式 token

## 10.5 Phase 4：普通页面与低频链补齐

目标：

- 把账户、分析、悬浮窗、日志等低频残留链一起收口

优先范围：

- 账户页数字着色链
- 分析页统计着色链
- 悬浮窗背景与状态色
- 日志背景与级别提示底色
- 快速滚动条、按钮态、输入态等低频资源

## 10.6 Phase 5：删除遗留

只有前四阶段闭合后，才允许开始删除。

删除内容包括：

- 重复命名资源
- 未使用资源
- 历史主题资源
- 旧别名
- 所有字面量颜色

## 11. 验证与测试策略

## 11.1 验证口径

后续统一按 3 个统计口径汇报：

### 口径 A：canonical token 数

系统正式允许存在的颜色 token 数量。

### 口径 B：基础 RGB 数

剔除透明度后，整个主 UI 里实际还有多少种底层颜色。

### 口径 C：违规字面量数

源码和资源中还剩多少处颜色字面量写法。

这 3 个口径以后必须一起报，不能再只报“看起来还有很多颜色”。

## 11.2 约束测试

建议新增或扩展两类测试：

### 资源层约束测试

检查：

- canonical token 集合是否完整
- 禁止重复职责命名
- 禁止未批准新增颜色资源

### 源码层约束测试

检查：

- UI 包禁止 `Color.parseColor(...)`
- UI 包禁止 `#hex`
- UI 包禁止 `0xAARRGGBB`
- 图表包禁止字面量颜色

## 11.3 验收标准

最终完成时必须满足：

1. `colors.xml` 中只保留正式 token 和必要兼容过渡项。
2. 重复命名为 0。
3. 未使用颜色为 0。
4. UI 源码中的颜色字面量为 0。
5. 图表源码中的颜色字面量为 0。
6. 普通系统交互色与交易语义色不再混用。
7. 买蓝、卖红，盈利绿、亏损红原则保持不变。
8. 新增页面或新模块不能绕开 canonical token。

## 12. 风险与控制

### 风险 1：主色切换后普通控件与交易语义混淆

控制方式：

- 先冻结 `accent_primary` 与 `trade_buy` 的职责边界
- 普通系统交互不再直接借用买入蓝

### 风险 2：图表迁移时视觉突然变化过大

控制方式：

- 图表先做语义映射，不先追求外观大改
- 先保证“颜色来源统一”，再做必要微调

### 风险 3：删除遗留色过早导致回归

控制方式：

- 只有主链全部迁移闭合后才允许删除
- 删除前必须有“资源使用统计 + 源码字面量统计”

### 风险 4：后续继续回流写死色

控制方式：

- 通过源码约束测试和统计口径长期拦截
- 不再允许“先临时写一个颜色，后面再收口”

## 13. 最终目标值

这次不追求极限压缩，而追求规则闭合。

建议目标值：

- canonical token：固定 `14` 个
- 基础中性色：`8` 个
- 系统语义色：`2` 个
- 交易语义色：`4` 个
- 违规字面量：`0`
- 重复命名：`0`
- 未使用颜色：`0`

## 14. 结论

这次颜色系统优化的核心，不是“少几个颜色”，而是“每个颜色只有一个职责，每条链都说同一种颜色语言”。

真正的实现顺序必须是：

1. 先定唯一正式标准
2. 再统一资源、主题、运行时
3. 再优先清图表主链
4. 最后再删遗留

只要顺序倒过来，就会重新回到一边删除、一边补写死色的旧状态。

这份文档就是后续颜色系统统一、实施计划拆解和代码改造的唯一设计基线。
