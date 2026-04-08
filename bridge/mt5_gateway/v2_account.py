"""MT5 网关 V2 账户模型构建工具。

该模块仅包含纯函数，负责把上游快照/历史数据转换为 V2 可用的展示模型。
"""

from __future__ import annotations

from typing import Any, Iterable, Mapping, Sequence, MutableMapping, Dict, List, Optional


def _to_int(value: Any, default: int = 0) -> int:
    """将任意值安全转换为整数，无法转换时返回默认值。"""
    if value is None:
        return default
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return default


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


def _normalize_position(raw: Mapping[str, Any]) -> Dict[str, Any]:
    """统一处理持仓对象字段，保证数值类型和字段可预测。"""
    return {
        "productName": raw.get("productName") or raw.get("symbol") or raw.get("code"),
        "code": raw.get("code") or raw.get("symbol"),
        "side": str(raw.get("side") or raw.get("direction") or "").strip(),
        "positionTicket": _to_int(raw.get("positionTicket") or raw.get("positionId") or raw.get("ticket")),
        "orderId": _to_int(raw.get("orderId") or raw.get("order") or raw.get("ticket")),
        "quantity": _to_float(raw.get("quantity") or raw.get("volume") or raw.get("lots")),
        "costPrice": _to_float(raw.get("costPrice") or raw.get("entryPrice")),
        "latestPrice": _to_float(
            raw.get("latestPrice") or raw.get("markPrice") or raw.get("price") or raw.get("closePrice")
        ),
        "totalPnL": _to_float(raw.get("totalPnL") or raw.get("floatingPnL") or raw.get("profit")),
        "pendingCount": _to_int(raw.get("pendingCount") or raw.get("pendingOrderCount")),
        "takeProfit": _to_float(raw.get("takeProfit") or raw.get("tp") or raw.get("tpPrice")),
        "stopLoss": _to_float(raw.get("stopLoss") or raw.get("sl") or raw.get("slPrice")),
        "storageFee": _to_float(raw.get("storageFee") or raw.get("storage_fee") or raw.get("swap")),
    }


def _normalize_order(raw: Mapping[str, Any]) -> Dict[str, Any]:
    """统一处理挂单对象字段，保持数值以浮点形式输出。"""
    return {
        "productName": raw.get("productName") or raw.get("symbol") or raw.get("code"),
        "code": raw.get("code") or raw.get("symbol"),
        "side": str(raw.get("side") or raw.get("direction") or "").strip(),
        "orderId": _to_int(raw.get("orderId") or raw.get("order") or raw.get("ticket")),
        "quantity": _to_float(raw.get("quantity") or raw.get("pendingLots") or raw.get("volume") or raw.get("pendingVolume")),
        "pendingLots": _to_float(raw.get("pendingLots") or raw.get("volume") or raw.get("pendingVolume")),
        "pendingPrice": _to_float(raw.get("pendingPrice") or raw.get("price")),
        "latestPrice": _to_float(raw.get("latestPrice") or raw.get("markPrice") or raw.get("priceCurrent")),
        "pendingCount": _to_int(raw.get("pendingCount") or raw.get("pendingOrderCount") or 1),
        "takeProfit": _to_float(raw.get("takeProfit") or raw.get("tp") or raw.get("tpPrice")),
        "stopLoss": _to_float(raw.get("stopLoss") or raw.get("sl") or raw.get("slPrice")),
        "status": raw.get("status") or raw.get("state"),
    }


def _normalize_trade(raw: Mapping[str, Any]) -> Dict[str, Any]:
    """把 canonical 成交数据标准化为 V2 历史成交行。"""
    return {
        "timestamp": _to_int(raw.get("timestamp")),
        "productName": raw.get("productName"),
        "code": raw.get("code"),
        "side": str(raw.get("side") or "").strip(),
        "price": _to_float(raw.get("price")),
        "quantity": _to_float(raw.get("quantity")),
        "profit": _to_float(raw.get("profit")),
        "fee": _to_float(raw.get("fee")),
        "storageFee": _to_float(raw.get("storageFee")),
        "openTime": _to_int(raw.get("openTime")),
        "closeTime": _to_int(raw.get("closeTime")),
        "openPrice": _to_float(raw.get("openPrice")),
        "closePrice": _to_float(raw.get("closePrice")),
        "dealTicket": _to_int(raw.get("dealTicket")),
        "orderId": _to_int(raw.get("orderId")),
        "positionId": _to_int(raw.get("positionId")),
        "entryType": _to_int(raw.get("entryType")),
        "remark": raw.get("remark") or "",
    }


def _normalize_curve_point(raw: Mapping[str, Any]) -> Dict[str, Any]:
    """规范化 canonical 曲线点，保证 equity/balance/positionRatio 始终存在。"""
    return {
        "timestamp": _to_int(raw.get("timestamp")),
        "equity": _to_float(raw.get("equity")),
        "balance": _to_float(raw.get("balance")),
        "positionRatio": _to_float(raw.get("positionRatio")),
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

    positions_source = raw_snapshot.get("positions")
    orders_source = raw_snapshot.get("orders")

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
    """根据 canonical 历史数据构造 V2 账户历史展示模型。"""
    trades_source = raw_history.get("trades")
    orders_source = raw_history.get("orders")
    curve_source = raw_history.get("curvePoints")

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
