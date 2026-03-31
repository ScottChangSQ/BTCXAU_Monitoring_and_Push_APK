2026-03-31 微信式设置页改版
==========================

## 背景
目前设置首页和二级页仍在使用卡片式分区，视觉显得厚重，跟用户要求“更贴近微信设置界面”的目标不符。当前功能入口和交互都需要保留，改版仅在界面结构上减少圆角/卡片感，改为整行列表 + 轻型分组，二级页也改成平直的分段列表风格。

## 目标
- 保留“首页 → 二级页”的导航流程和已有逻辑（点击入口进入对应分组并复用现有 ViewModel/回传）。
- 首页按微信式列表展示六个入口，分成「常规」和「辅助工具」两组；每一行都是全宽文本+箭头、底部细线，点击有 ripple，背景保持整体渐变。
- 二级页去掉 MaterialCardView 大卡片，直接用 LinearLayout/Divider 将每个设置块、主题项、按钮排列成纯文本行式结构，仅在必要的位置保留色块式主题预览。
- 视觉上尽量靠近微信设置，右侧使用 `chevron` 图标，分组间用空白/细线分隔。
- 只在 `SettingsActivity`, `SettingsSectionActivity`, `activity_settings.xml`, `activity_settings_detail.xml` 及必要的资源里做改动，不 touch 账户统计页或服务端文件。

## 设计方案（推荐）
1. **设置首页**：保持现有六个入口顺序，使用两个 `LinearLayout` group 容器，分别插入组标题文本、若干列表行、行与行之间用 1dp Divider；每一行高度保持 56dp，内容为 `TextView` + `ImageView`（chevron）；在 XML 里设置 `android:foreground="?attr/selectableItemBackground"` 确保点击反馈，`styleEntry()` 只负责填充背景/边框颜色（目前计划用 `palette.surfaceEnd`）。分组之间留 12dp 间距，底部保留原来的底部导航。
2. **二级页结构**：保留顶部返回栏，滚动区域由纯 `LinearLayout` 组成，每一段前后都用透明的 margin 或 `View` 分隔线。每段布局里的控件（开关、按钮、输入框）保持原样，只是外层容器不用 `MaterialCardView`，而是用 `LinearLayout`/`FrameLayout` 作为 simple container，设置统一的 background/Divider。主题选择部分改为列表式条目，体现在每个主题行用 `LinearLayout` + 3 个小色块，选中时用 `UiPaletteManager.createThemeItemDrawable()` 画出细边框，未选中时淡色底。
3. **代码变动**：
   - `SettingsActivity`: `styleEntry()` 改为生成扁平的 `GradientDrawable`（0 圆角、仅填充）并把 `foreground` 设置为 `?attr/selectableItemBackground`; `applyPaletteStyles()` 仍调用 `styleEntry` 并同步 new `ImageView` tint。
   - `SettingsSectionActivity`: 新增 `styleThemeItem(View item, TextView title, TextView desc, View swatchA, View swatchB, View swatchC, Palette palette, boolean selected)` 替代原来的 `MaterialCardView` 处理；`applyPaletteStyles()` 也要把每段容器背景换成 `UiPaletteManager.createSectionBackground(...)`、标题文字、按钮、Divider 色一致；原来的 `MaterialCardView` 引用改为 `View`。
   - `UiPaletteManager`: 新增 `createListRowBackground(...)`, `createSectionBackground(...)`, `createThemeItemDrawable(...)` 供上述页面复用，corner 设为 0/4dp，stroke 颜色用 `Palette.stroke`。

## 资源与布局
- 新建 `res/drawable/ic_chevron_right.xml` 作为右箭头，宽高 24dp，path 形状简单。
- 继续复用现有 `@dimen/page_horizontal_padding` 等间距，不新增外部依赖。
- 通过布局中的 `View` 或 `android:divider` 表现分组线条（统一用 `@color/divider`），页面整体仍用 `@drawable/bg_app` 作为底板，配合 `UiPaletteManager.applyPageTheme` 的渐变。

## 验证
- 仅改 UI 结构，功能逻辑依然走 `MainViewModel`/`MonitorService`，手动点击各入口和值班设置确认跳转与状态保留。
- 目前尚未执行 `gradlew`（因为只是布局层改动），完成后可考虑 `./gradlew :app:testDebugUnitTest` 快速跑一遍。

## 备注
- 由于没有从你那边得到分组偏好，我先按「常规 + 辅助工具」默认拆分；如需调整分组/顺序再回来改。在实现前我会发完整布局草案让你确认。
