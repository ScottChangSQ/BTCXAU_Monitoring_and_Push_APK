# CONTEXT

## 当前正在做什么
- 本轮已完成“历史成交在 K 线图与交易记录里整体晚 3 小时”的严格修复收口：服务端时间源头只认 `MT5_SERVER_TIMEZONE`，把 MT5 原始时间统一归一化成 UTC 毫秒；App 不再做时间纠偏，只按设备本地时区显示。
- 已同步补齐测试契约、部署样例和网关 README，避免空时区配置继续静默通过。

## 上次停在哪个位置
- 上次停在服务端已收紧时间归一化入口，但完整回归里仍有旧测试默认“空时区也能通过”。
- 本轮继续把 `test_summary_response.py`、`test_v2_contracts.py` 等旧测试改成显式声明 `MT5_SERVER_TIMEZONE`，并补部署契约测试。

## 近期关键决定和原因
- 根因确认在网关时间源头而不是客户端图表或列表层：线上 `health` 返回 `mt5ServerTimezone=\"\"`，历史成交时间整体比设备本地晚 3 小时。
- 严格方案固定为“服务端统一归一化 + App 只本地显示”，不接受图表层减 3 小时、客户端猜服务器时区、固定分钟差补偿等做法。
- `MT5_SERVER_TIMEZONE` 改为部署必填项；`MT5_TIME_OFFSET_MINUTES` 仅保留健康面板旧值展示，不再参与历史时间真值。

## 当前验证状态
- 已通过：
  - `python -m unittest bridge.mt5_gateway.tests.test_summary_response bridge.mt5_gateway.tests.test_admin_panel bridge.mt5_gateway.tests.test_deploy_contracts -v`
  - `python -m unittest bridge.mt5_gateway.tests.test_v2_contracts -v`
  - `python -m unittest discover -s bridge/mt5_gateway/tests -p "test_*.py" -v`
- 结果：`bridge/mt5_gateway/tests` 共 229 个测试全部通过。
