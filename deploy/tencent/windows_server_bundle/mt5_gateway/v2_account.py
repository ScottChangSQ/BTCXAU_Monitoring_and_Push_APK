"""MT5 网关 V2 账户模型构建工具。

该模块仅包含纯函数，负责把上游快照/历史数据转换为 V2 可用的展示模型。
"""

from __future__ import annotations

from typing import Any, Iterable, Mapping, Sequence, MutableMapping, Dict, List, Optional


def _to_float(value: Any, default: float = 0.0) -> float:
    """将任意值安全转换为浮点，无法转换时返回默认值。"""
    if value is None:
        return default
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def _derive_margin_level(equity: float, margin: float) -> Optional[float]:
    """根据 equity 与 margin 推算保证金率。"""
    if margin <= 0:
        return None
    return (equity / margin) * 100


def _derive_position_ratio(equity: float, balance: float) -> float:
    """估算净值曲线点的仓位比例，作为兜底值。"""
    if equity:
        return (equity - balance) / equity
    if balance:
        return (equity - balance) / balance
    return 0.0


def _normalize_position(raw: Mapping[str, Any]) -> Dict[str, Any]:
    """统一处理持仓对象字段，保证数值类型和字段可预测。"""
    return {
        "productName": raw.get("productName") or raw.get("symbol") or raw.get("code"),
        "code": raw.get("code") or raw.get("symbol"),
        "side": str(raw.get("side") or raw.get("direction") or "").strip(),
        "quantity": _to_float(raw.get("quantity") or raw.get("volume") or raw.get("lots")),
        "costPrice": _to_float(raw.get("costPrice") or raw.get("entryPrice")),
        "latestPrice": _to_float(
            raw.get("latestPrice") or raw.get("markPrice") or raw.get("price") or raw.get("closePrice")
        ),
        "totalPnL": _to_float(raw.get("totalPnL") or raw.get("floatingPnL") or raw.get("profit")),
    }


def _normalize_order(raw: Mapping[str, Any]) -> Dict[str, Any]:
    """统一处理挂单对象字段，保持数值以浮点形式输出。"""
    return {
        "productName": raw.get("productName") or raw.get("symbol") or raw.get("code"),
        "code": raw.get("code") or raw.get("symbol"),
        "side": str(raw.get("side") or raw.get("direction") or "").strip(),
        "pendingLots": _to_float(raw.get("pendingLots") or raw.get("volume") or raw.get("pendingVolume")),
        "pendingPrice": _to_float(raw.get("pendingPrice") or raw.get("price")),
        "status": raw.get("status") or raw.get("state"),
    }


def _normalize_trade(raw: Mapping[str, Any]) -> Dict[str, Any]:
    """把成交原始数据标准化为 V2 历史成交行。"""
    timestamp = raw.get("timestamp") or raw.get("time") or 0
    price = _to_float(raw.get("price") or raw.get("openPrice") or raw.get("closePrice"))
    open_price = _to_float(
        raw.get("openPrice")
        or raw.get("open_price")
        or raw.get("open")
        or raw.get("priceOpen")
        or raw.get("entryPrice")
        or raw.get("entry_price"),
        price,
    )
    close_price = _to_float(
        raw.get("closePrice")
        or raw.get("close_price")
        or raw.get("close")
        or raw.get("priceClose")
        or raw.get("exitPrice")
        or raw.get("exit_price"),
        price,
    )
    open_time = int(raw.get("openTime") or raw.get("open_time") or raw.get("timeOpen") or raw.get("time_open") or timestamp)
    close_time = int(raw.get("closeTime") or raw.get("close_time") or raw.get("timeClose") or raw.get("time_close") or timestamp)
    return {
        "timestamp": int(timestamp),
        "productName": raw.get("productName") or raw.get("symbol") or raw.get("code"),
        "code": raw.get("code") or raw.get("symbol"),
        "side": str(raw.get("side") or raw.get("direction") or raw.get("entry") or "").strip(),
        "price": price,
        "quantity": _to_float(raw.get("quantity") or raw.get("volume")),
        "profit": _to_float(raw.get("profit") or raw.get("pnl")),
        "fee": _to_float(raw.get("fee") or raw.get("commission")),
        "storageFee": _to_float(raw.get("storageFee") or raw.get("storage_fee") or raw.get("swap")),
        "openTime": open_time,
        "closeTime": close_time,
        "openPrice": open_price if open_price > 0.0 else price,
        "closePrice": close_price if close_price > 0.0 else price,
        "dealTicket": int(raw.get("dealTicket") or raw.get("deal_ticket") or 0),
        "orderId": int(raw.get("orderId") or raw.get("order_id") or 0),
        "positionId": int(raw.get("positionId") or raw.get("position_id") or 0),
        "entryType": int(raw.get("entryType") or raw.get("entry_type") or 0),
        "remark": raw.get("remark") or raw.get("comment") or "",
    }


def _normalize_curve_point(raw: Mapping[str, Any]) -> Dict[str, Any]:
    """规范化曲线点，保证 equity/balance/positionRatio 始终存在。"""
    timestamp = int(raw.get("timestamp") or raw.get("time") or 0)
    equity = _to_float(raw.get("equity"))
    balance = _to_float(raw.get("balance"))
    ratio_source = raw.get("positionRatio")
    position_ratio = _to_float(ratio_source) if ratio_source is not None else _derive_position_ratio(equity, balance)
    return {
        "timestamp": timestamp,
        "equity": equity,
        "balance": balance,
        "positionRatio": position_ratio,
    }


def _normalize_sequence(
    iterable: Optional[Iterable[Mapping[str, Any]]], normalizer: Any
) -> List[Dict[str, Any]]:
    """辅助函数，遍历可选序列并应用字段标准化器。"""
    if iterable is None:
        return []
    results: List[Dict[str, Any]] = []
    for raw in iterable:
        if not isinstance(raw, Mapping):
            continue
        results.append(normalizer(raw))
    return results


def build_account_snapshot_model(raw_snapshot: Mapping[str, Any]) -> Dict[str, Any]:
    """根据上游快照构造 V2 账户快照展示模型。"""
    metrics = (raw_snapshot.get("accountMetrics") or raw_snapshot.get("metrics") or {}) or {}

    balance = _to_float(metrics.get("balance") or raw_snapshot.get("balance"))
    equity = _to_float(metrics.get("equity") or raw_snapshot.get("equity"))
    margin = _to_float(metrics.get("margin") or raw_snapshot.get("margin"))
    free_margin = _to_float(metrics.get("freeMargin") or raw_snapshot.get("freeMargin") or raw_snapshot.get("free_margin"))
    margin_level_source = metrics.get("marginLevel") or raw_snapshot.get("marginLevel")
    margin_level = _to_float(margin_level_source) if margin_level_source is not None else _derive_margin_level(equity, margin)
    profit_source = metrics.get("profit") or raw_snapshot.get("profit")
    profit = _to_float(profit_source if profit_source is not None else equity - balance)

    positions_source = raw_snapshot.get("positions") or raw_snapshot.get("openPositions") or raw_snapshot.get("accountPositions")
    orders_source = raw_snapshot.get("orders") or raw_snapshot.get("pendingOrders") or raw_snapshot.get("pending")

    return {
        "balance": balance,
        "equity": equity,
        "margin": margin,
        "freeMargin": free_margin,
        "marginLevel": margin_level,
        "profit": profit,
        "positions": _normalize_sequence(positions_source, _normalize_position),
        "orders": _normalize_sequence(orders_source, _normalize_order),
    }


def build_account_history_model(raw_history: Mapping[str, Any]) -> Dict[str, Any]:
    """根据上游历史数据构造 V2 账户历史展示模型。"""
    trades_source = raw_history.get("trades") or raw_history.get("dealHistory")
    orders_source = raw_history.get("orders") or raw_history.get("pendingOrders") or raw_history.get("orderHistory")
    curve_source = raw_history.get("curvePoints") or raw_history.get("curve") or raw_history.get("equityCurve")

    trades = _normalize_sequence(trades_source, _normalize_trade)
    orders = _normalize_sequence(orders_source, _normalize_order)
    curve_points = _normalize_sequence(curve_source, _normalize_curve_point)
    sorted_curve = sorted(curve_points, key=lambda point: point["timestamp"])

    return {
        "trades": trades,
        "orders": orders,
        "curvePoints": sorted_curve,
    }


def build_account_snapshot_response(
    snapshot_model: Mapping[str, Any],
    account_meta: Optional[Mapping[str, Any]] = None,
    sync_seq: Optional[int] = None,
    *,
    is_delta: bool = False,
    unchanged: bool = False,
) -> Dict[str, Any]:
    """为 /v2/account/snapshot 构造响应体，保持纯函数。"""
    meta: MutableMapping[str, Any] = dict(account_meta or {})
    if sync_seq is not None:
        meta["syncSeq"] = sync_seq

    response: Dict[str, Any] = {"accountMeta": dict(meta)}
    response.update({k: v for k, v in snapshot_model.items()})
    if is_delta:
        response["isDelta"] = True
    if unchanged:
        response["unchanged"] = True
    return response


def build_account_history_response(
    history_model: Mapping[str, Any],
    account_meta: Optional[Mapping[str, Any]] = None,
    sync_seq: Optional[int] = None,
    *,
    is_delta: bool = False,
    unchanged: bool = False,
) -> Dict[str, Any]:
    """为 /v2/account/history 构造响应体，保持纯函数。"""
    meta: MutableMapping[str, Any] = dict(account_meta or {})
    if sync_seq is not None:
        meta["syncSeq"] = sync_seq

    response: Dict[str, Any] = {"accountMeta": dict(meta)}
    response.update({k: v for k, v in history_model.items()})
    if is_delta:
        response["isDelta"] = True
    if unchanged:
        response["unchanged"] = True
    return response
