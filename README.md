# BTC/XAU 异常交易监控 Android App

一个基于 Java、XML、ViewBinding、MVVM + Repository 的 Android Studio 工程，用于监控 Binance Futures 1 分钟 K 线异常。

## 技术栈

- Java
- XML + ViewBinding
- MVVM + Repository
- OkHttp
- compileSdk / targetSdk 34
- minSdk 24

## 已实现能力

- Binance Futures REST 初始化最近已收盘 1m K 线
- WebSocket 实时接收 `BTCUSDT` 与 `XAUUSDT` 的 1m K 线更新
- OR / AND 异常判断
- 前台服务常驻运行
- 单标通知、同轮合并通知、5 分钟冷却
- 悬浮窗显示行情，可拖动、可调透明度、可控制 BTC / XAU 显示
- SharedPreferences 持久化配置
- 本地文件持久化日志与异常记录
- 主页面与日志页面
- 行情图表页新增指标：`MA / EMA / SRA / AVL / RSI / KDJ`
- 指标按钮支持长按参数设置（周期、平滑参数等）
- 行情图表页底部新增“当前持仓”模块，并与图表持仓标注共用账户快照数据
- 底部 Tab 调整为微信风格（选中绿色文字 + 下划线）
- 账户统计网关补充 MT5 Python 接口字段（杠杆、保证金水平、账户元信息）
- 监控模块与图表模块通过共享缓存与实时收盘口径减少重复请求

## 目录说明

- `app/src/main/java/com/binance/monitor/constants/AppConstants.java`
  交易对常量、通知常量、Service Action 常量统一放在这里。
- `app/src/main/java/com/binance/monitor/service/MonitorService.java`
  前台服务、初始化、WebSocket、异常判断、通知调度。
- `app/src/main/java/com/binance/monitor/ui/main/MainActivity.java`
  主页面 UI 与交互。
- `app/src/main/java/com/binance/monitor/ui/log/LogActivity.java`
  日志页面。

## 构建方式

1. 用 Android Studio 直接打开仓库根目录。
2. 确认本机已安装 Android SDK 34。
3. 如果换到其他机器，请更新 `local.properties` 里的 `sdk.dir`。
4. 直接执行 `assembleDebug` 或在 Android Studio 中运行 `app` 模块。

## 已验证

本地已执行：

```bash
./gradlew.bat assembleDebug
```

编译通过。

## 说明

- `XAUUSDT` 在 Binance Futures 上可能不可用，项目已将交易对集中定义在 `AppConstants` 中，方便后续替换。
- 若未授予通知权限或悬浮窗权限，应用会降级运行，不会崩溃。

## 搜索记录（2026-03-29）

- `skills.sh` 检索：`android-design-guidelines`（https://skills.sh/ehmo/platform-design-skills/android-design-guidelines）
  结论：Tab 与长按交互可按 Android 一致性原则落地，底部导航采用清晰选中态更符合移动端习惯。
- GitHub 检索：`liihuu/KLineChartAndroid`（https://github.com/liihuu/KLineChartAndroid）
  结论：K 线常用指标组合包含 `MA/EMA/RSI/KDJ/BOLL/MACD`，本项目图表指标扩展采用同类组合。
- 官方文档检索：MQL5 Python `history_deals_get`（https://www.mql5.com/en/docs/python_metatrader5/mt5historydealsget_py）
  结论：账户统计与交易历史相关数据继续以 MetaTrader5 Python 接口为主数据来源。
