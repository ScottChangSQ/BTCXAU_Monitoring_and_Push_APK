# BTCUSDT/XAUUSDT 异常交易监控 App 一次生成提示词

你现在是资深 Android 工程师，请基于以下要求，一次性生成一个“可直接在 Android Studio 打开并编译运行”的完整 Android 项目。

## 你的输出目标

请直接输出完整项目所需的全部代码与资源，不要只给方案，不要只给目录，不要省略任何关键文件。

必须满足：
- Android Studio 可直接打开
- Gradle 可同步
- `assembleDebug` 可编译通过
- 不允许出现 `TODO`、伪代码、占位空实现
- 输出完整源码、XML 布局、资源文件

## 固定技术栈

- Java
- XML + ViewBinding
- MVVM + Repository
- compileSdk 34
- targetSdk 34
- minSdk 24
- applicationId `com.binance.monitor`
- 依赖尽量精简，网络使用 OkHttp

## App 功能说明

开发一个 Android App，监控 Binance Futures 的 `BTCUSDT` 和 `XAUUSDT` 两个交易对的 1 分钟 K 线异常。

数据规则：
- 使用 WebSocket 获取实时 1m K 线更新
- 使用 REST API 获取首次加载数据和重连初始化数据
- 异常判断必须基于“最近一根已收盘的自然 1 分钟 K 线”

异常条件：
- 成交量异常：`volume >= volumeThreshold`
- 成交额异常：`quoteAssetVolume >= amountThreshold`
- 价格变化异常：`abs(closePrice - openPrice) >= priceChangeThreshold`

触发逻辑：
- 支持 OR
- 支持 AND
- 若三个条件均关闭，则该标的不参与异常检测
- AND 仅对已启用条件求交集

默认阈值：
- BTCUSDT：volume=1000，amount=70000000，priceChange=200
- XAUUSDT：volume=3000，amount=15000000，priceChange=10

通知规则：
- BTC 单独异常时发送 BTC 通知
- XAU 单独异常时发送 XAU 通知
- 同一轮判断 BTC 与 XAU 同时异常时发送一条合并通知
- 同一标的通知冷却时间 5 分钟
- 冷却期间继续写日志和异常记录，但不重复发系统通知

## 页面要求
UI 风格要求：时尚、现代、简洁、科技感，采用深色主题为主，搭配高对比强调色，界面干净无冗余元素，卡片式布局，圆角柔和，动效流畅轻量化，整体视觉高级、专业、未来感。
### 1. 主页面

需要包含：
- 连接状态
- 监控状态
- 打开 Binance 按钮
- 打开 MT5 按钮
- 最近更新时间
- BTC/XAU 切换区
- 当前价格
- 最近一根已收盘 1m K 线的开盘价、收盘价、成交量、成交额、价格变化、涨跌幅
- 最近异常记录列表
- 三个阈值输入框
- 三个阈值启用开关
- 恢复默认按钮
- OR / AND 单选
- 开始监控按钮
- 停止监控按钮
- 悬浮窗开关
- 悬浮窗透明度滑块
- 是否显示 BTC / XAU 开关
- 查看日志按钮


### 2. 日志页面

需要包含：
- 日志列表
- INFO/WARN/ERROR 颜色区分
- 长按复制
- 删除单条
- 多选
- 全选
- 清空全部

### 3. 悬浮窗

要求：
- 可拖动
- 透明度可调
- 可分别控制显示 BTC / XAU
- 显示当前价格、最近一根已收盘 1 分钟 K 线成交量、成交额、更新时间
- 停止监控后仍可显示行情，但要体现监控已停止
- 格式简约，与UI要求保持一致

## 权限与后台要求

Manifest 中必须声明并正确处理：
- INTERNET
- ACCESS_NETWORK_STATE
- WAKE_LOCK
- SYSTEM_ALERT_WINDOW
- FOREGROUND_SERVICE
- POST_NOTIFICATIONS（Android 13+）

后台要求：
- 使用前台服务执行监控
- 创建通知渠道
- 服务运行时显示常驻通知
- 网络断开自动重连，最多 30 次
- 权限缺失时功能降级但不能崩溃

## 持久化要求

必须持久化：
- BTC 配置
- XAU 配置
- OR/AND 选择
- 悬浮窗开关与透明度
- 显示 BTC/XAU 开关
- 异常记录
- 日志

建议：
- 配置使用 SharedPreferences
- 异常记录与日志使用本地文件
- 日志最多保留 2000 条
- 异常记录最多保留 500 条

## 代码结构要求

至少包含这些模块：
- WebSocketManager
- BinanceApiClient
- MonitorService
- FloatingWindowManager
- PreferenceManager 或 ConfigManager
- LogManager
- AbnormalRecordManager
- MainActivity
- LogActivity 或 LogFragment
- 对应 adapter、model、util

## 交付要求

请按完整工程输出，确保以下文件齐全：
- 项目级 Gradle
- app 模块 Gradle
- settings.gradle
- AndroidManifest.xml
- 全部 Kotlin/Java 文件
- 全部 XML 布局
- strings.xml
- colors.xml
- themes.xml / styles.xml
- 必需 drawable
- README

## 禁止事项

禁止：
- 伪代码
- 只输出部分文件
- 用注释代替实现
- 省略资源文件
- 省略权限逻辑
- 省略通知逻辑
- 省略悬浮窗逻辑
- 省略重连逻辑

如果某个交易对符号在当前 Binance Futures 不可用，请将交易对定义为常量，代码中集中管理，方便替换，但项目本身仍需保持可编译。

