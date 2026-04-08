# CONTEXT

## 当前正在做什么
- 当前代码收口点已经完成：账户页刷新链优化、旧失败测试清理、Windows 服务端 bundle 重打包、最新 APK 重编译并安装到已连接设备。
- 当前可直接交付的服务器目录为 `dist/windows_server_bundle`，可整体复制到服务器 `C:\mt5_bundle\windows_server_bundle`。
- 当前可直接安装的客户端产物为 `app/build/outputs/apk/debug/app-debug.apk`。
- 本轮新确认并修复：线上 `/v2/account/snapshot` 会因为 MT5 `history_deals_total` 异常而返回 500，进而让账户登录停在“正在同步”并拖坏 `/v2/stream`；轻快照 `tradeCount` 已改为复用全量历史同口径成交映射链。
- 本轮还修复了 APP 悬浮窗偶发双窗问题：悬浮窗唯一性现在以真实附着状态为准，隐藏时同步移除旧 root，避免快速重建时旧窗未清又 add 新窗。

## 上次停在哪个位置
- 上次已经完成账户页刷新链与历史接口主链修复，但还需要把最终交付物重新构建出来。
- 本轮已完成重新打包服务端目录并重新编译安装 APK，交付物与当前代码一致。
- 本轮又继续收口了服务器轻快照链，已重新生成新的 Windows 服务端 bundle，等待用户自行部署覆盖服务器。
- 本轮已完成悬浮窗双窗 BUG 修复，并把最新 debug APK 重新安装到真机。

## 近期关键决定和原因
- 决定把账户曲线价格样本默认来源从外部 Binance 改成 MT5 自身 `copy_rates_range`。原因是账户历史主链应优先使用服务端账户真值同源数据，且这样才能覆盖 MT5 里的真实交易品种，不再被外部交易所接口约束。
- 决定保留 `fetch_rows_fn` 作为显式注入测试入口，但默认运行态不再依赖 `_fetch_binance_kline_rows`。原因是测试仍需要可控样本，而生产主链应走 MT5 原生历史价格。
- 决定重新生成部署包。原因是这次修的是服务端历史接口主链代码，只有重新覆盖服务器 bundle 才会生效。
- 决定让服务端轻快照返回真实 `tradeCount`，并把客户端账户页改成“高频轻快照、历史按 `tradeCount` 变化补拉”。原因是用户明确要求平时只更新账户概览与当前持仓，新增交易记录时才全量刷新，这需要服务端提供真实历史版本而不能靠客户端猜测。
- 决定让 `persistIncrementalSnapshot()` 保留已存的曲线点、曲线指标和账户统计指标。原因是轻快照链只负责实时区，不应把上一次全量历史得到的历史展示区清空。
- 决定本轮不代替用户执行服务器部署，只负责把需要上传的完整目录整理到 `dist/windows_server_bundle`。原因是用户已明确说明服务器部署由其自行完成。
- 决定废弃轻快照里的 `history_deals_total` 计数方式，改为复用 `_progressive_trade_history_deals + _map_trade_deals`。原因是现场 MT5 Python 包对 `history_deals_total` 会抛底层异常，而客户端刷新判定需要和全量历史完全同口径的 `tradeCount` 真值。
- 决定把悬浮窗去重放在 `FloatingWindowManager` 主链里统一收口。原因是双窗问题来自窗口真实附着状态与 `showing` 布尔状态可能短暂分叉，必须在窗口管理层按系统真值保证“先清旧窗，再建新窗”。
