import hashlib
import json
import math
import os
from pathlib import Path
from datetime import datetime, timedelta, timezone
from statistics import mean, pstdev
from threading import Lock
from typing import Any, Dict, List, Optional, Tuple

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.middleware.gzip import GZipMiddleware

try:
    import MetaTrader5 as mt5
except Exception:  # pragma: no cover
    mt5 = None

load_dotenv()

LOGIN = int(os.getenv("MT5_LOGIN", "7400048"))
PASSWORD = os.getenv("MT5_PASSWORD", "_fWsAeW1")
SERVER = os.getenv("MT5_SERVER", "ICMarketsSC-MT5-6")
PATH = os.getenv("MT5_PATH", "").strip() or None
SERVER_ALIASES_RAW = os.getenv("MT5_SERVER_ALIASES", "").strip()
try:
    MT5_INIT_TIMEOUT_MS = int(os.getenv("MT5_INIT_TIMEOUT_MS", "60000"))
except Exception:
    MT5_INIT_TIMEOUT_MS = 60000
HOST = os.getenv("GATEWAY_HOST", "0.0.0.0")
PORT = int(os.getenv("GATEWAY_PORT", "8787"))
GATEWAY_MODE = os.getenv("GATEWAY_MODE", "auto").strip().lower()  # auto | pull | ea
EA_SNAPSHOT_TTL_SEC = int(os.getenv("EA_SNAPSHOT_TTL_SEC", "35"))
EA_INGEST_TOKEN = os.getenv("EA_INGEST_TOKEN", "").strip()
SNAPSHOT_BUILD_CACHE_MS = int(os.getenv("SNAPSHOT_BUILD_CACHE_MS", "8000"))
SNAPSHOT_DELTA_ENABLED = os.getenv("SNAPSHOT_DELTA_ENABLED", "1").strip().lower() not in {"0", "false", "no"}
SNAPSHOT_DELTA_FALLBACK_RATIO = float(os.getenv("SNAPSHOT_DELTA_FALLBACK_RATIO", "0.85"))

app = FastAPI(title="MT5 Bridge Gateway", version="1.1.0")
app.add_middleware(GZipMiddleware, minimum_size=512)
state_lock = Lock()
snapshot_cache_lock = Lock()
ea_snapshot_cache: Optional[Dict] = None
ea_snapshot_received_at_ms = 0
ea_snapshot_change_digest = ""
mt5_last_connected_path = ""
snapshot_build_cache: Dict[str, Dict[str, Any]] = {}
snapshot_sync_cache: Dict[str, Dict[str, Any]] = {}


def _now_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def _fmt_money(value: float) -> str:
    sign = "+" if value >= 0 else "-"
    return f"{sign}${abs(value):,.2f}"


def _fmt_usd(value: float) -> str:
    return f"${value:,.2f}"


def _fmt_pct(value: float) -> str:
    return f"{value * 100:+.2f}%"


def _safe_div(a: float, b: float) -> float:
    if abs(b) < 1e-9:
        return 0.0
    return a / b


def _is_buy_trade_type(trade_type: int) -> bool:
    return trade_type in (0, 2, 4, 6)


def _order_side(order_type: int) -> str:
    return "Buy" if _is_buy_trade_type(order_type) else "Sell"


def _is_trade_deal_type(deal_type: int) -> bool:
    # MT5 deal type: 0=BUY, 1=SELL
    return deal_type in (0, 1)


def _is_buy_deal_type(deal_type: int) -> bool:
    return deal_type == 0


def _is_entry_open(entry_type: int) -> bool:
    # MT5 entry: 0=IN, 2=INOUT
    return entry_type in (0, 2)


def _is_entry_close(entry_type: int) -> bool:
    # MT5 entry: 1=OUT, 2=INOUT, 3=OUT_BY
    return entry_type in (1, 2, 3)


def _range_hours(range_key: str) -> int:
    mapping = {
        "1d": 24,
        "7d": 24 * 7,
        "1m": 24 * 30,
        "3m": 24 * 90,
        "1y": 24 * 365,
        "all": 24 * 365 * 10,
    }
    return mapping.get(range_key.lower(), 24 * 7)


def _is_mt5_configured() -> bool:
    return LOGIN > 0 and bool(PASSWORD) and bool(SERVER)


def _normalize_path(raw: str) -> str:
    value = (raw or "").strip().strip('"').strip("'")
    if not value:
        return ""
    expanded = Path(os.path.expandvars(os.path.expanduser(value)))
    try:
        return str(expanded.resolve())
    except Exception:
        return str(expanded)


def _discover_terminal_candidates() -> List[str]:
    candidates: List[str] = []
    seen = set()

    def add(path_value: str) -> None:
        normalized = _normalize_path(path_value)
        if not normalized:
            return
        if normalized in seen:
            return
        if not Path(normalized).exists():
            return
        seen.add(normalized)
        candidates.append(normalized)

    if PATH:
        add(PATH)

    static_candidates = [
        r"C:\Program Files\MetaTrader 5\terminal64.exe",
        r"C:\Program Files (x86)\MetaTrader 5\terminal64.exe",
        r"C:\Program Files\IC Markets - MetaTrader 5 - 01\terminal64.exe",
        r"C:\Program Files\IC Markets - MetaTrader 5\terminal64.exe",
    ]
    for path_value in static_candidates:
        add(path_value)

    roots = []
    local_app_data = os.getenv("LOCALAPPDATA", "")
    app_data = os.getenv("APPDATA", "")
    if local_app_data:
        roots.append(Path(local_app_data) / "Programs")
        roots.append(Path(local_app_data) / "MetaQuotes" / "Terminal")
    if app_data:
        roots.append(Path(app_data) / "MetaQuotes" / "Terminal")

    for root in roots:
        if not root.exists():
            continue
        try:
            for child in root.iterdir():
                if not child.is_dir():
                    continue
                add(str(child / "terminal64.exe"))
                add(str(child / "terminal.exe"))
        except Exception:
            continue

    return candidates


MT5_TERMINAL_CANDIDATES = _discover_terminal_candidates()


def _server_candidates() -> List[str]:
    values: List[str] = []
    seen = set()
    for raw in [SERVER] + SERVER_ALIASES_RAW.split(","):
        server = (raw or "").strip()
        if not server:
            continue
        key = server.lower()
        if key in seen:
            continue
        seen.add(key)
        values.append(server)
    return values


def _mt5_initialize(
    path_value: Optional[str],
    login: Optional[int] = None,
    password: Optional[str] = None,
    server_name: Optional[str] = None,
) -> Tuple[bool, str]:
    kwargs = {"timeout": MT5_INIT_TIMEOUT_MS}
    if path_value:
        kwargs["path"] = path_value
    if login and password and server_name:
        kwargs["login"] = int(login)
        kwargs["password"] = password
        kwargs["server"] = server_name
    try:
        ok = bool(mt5.initialize(**kwargs))
    except TypeError:
        # Backward compatibility for MT5 package variants
        # that do not accept timeout and/or auth fields together.
        legacy_kwargs = dict(kwargs)
        legacy_kwargs.pop("timeout", None)
        try:
            ok = bool(mt5.initialize(**legacy_kwargs))
        except TypeError:
            legacy_kwargs.pop("login", None)
            legacy_kwargs.pop("password", None)
            legacy_kwargs.pop("server", None)
            ok = bool(mt5.initialize(**legacy_kwargs))
    except Exception as exc:
        return False, str(exc)
    return ok, str(mt5.last_error())


def _mt5_login() -> Tuple[bool, str]:
    account = mt5.account_info()
    server_candidates = _server_candidates()
    if account is not None and int(getattr(account, "login", 0)) == LOGIN:
        account_server = str(getattr(account, "server", "")).strip().lower()
        if not account_server or account_server in [candidate.lower() for candidate in server_candidates]:
            return True, "already-logged-in"

    errors = []
    for server_name in server_candidates:
        try:
            ok = bool(mt5.login(LOGIN, password=PASSWORD, server=server_name, timeout=MT5_INIT_TIMEOUT_MS))
        except TypeError:
            ok = bool(mt5.login(LOGIN, password=PASSWORD, server=server_name))
        except Exception as exc:
            errors.append(f"{server_name}: {exc}")
            continue
        if ok:
            return True, server_name
        errors.append(f"{server_name}: {mt5.last_error()}")
    return False, " ; ".join(errors)


def _ensure_mt5() -> None:
    global mt5_last_connected_path
    if mt5 is None:
        raise RuntimeError("MetaTrader5 python package is not installed in gateway environment.")
    if not _is_mt5_configured():
        raise RuntimeError("MT5 credentials are not configured.")

    attempts: List[Optional[str]] = [None] + MT5_TERMINAL_CANDIDATES
    errors = []
    server_candidates = _server_candidates()
    for path_value in attempts:
        label = path_value if path_value else "<auto>"

        # First try: initialize with credentials directly.
        for server_name in server_candidates:
            _shutdown_mt5()
            initialized, init_message = _mt5_initialize(
                path_value,
                login=LOGIN,
                password=PASSWORD,
                server_name=server_name,
            )
            if initialized:
                mt5_last_connected_path = label
                return
            errors.append(f"init+login({label},{server_name})={init_message}")

        # Fallback: initialize first, then login.
        _shutdown_mt5()
        initialized, init_message = _mt5_initialize(path_value)
        if not initialized:
            errors.append(f"init({label})={init_message}")
            continue

        logged_in, login_message = _mt5_login()
        if logged_in:
            mt5_last_connected_path = label
            return
        errors.append(f"login({label})={login_message}")

    discovered_hint = ", ".join(MT5_TERMINAL_CANDIDATES[:3]) if MT5_TERMINAL_CANDIDATES else "none"
    raise RuntimeError(
        "MT5 login failed. "
        + " | ".join(errors[-6:])
        + f" | configured_server={SERVER} | discovered_terminal_candidates={discovered_hint}"
    )


def _shutdown_mt5() -> None:
    if mt5 is not None:
        mt5.shutdown()


def _deal_profit(deal) -> float:
    return (
        float(getattr(deal, "profit", 0.0))
        + float(getattr(deal, "commission", 0.0))
        + float(getattr(deal, "swap", 0.0))
    )


def _build_curve(range_key: str) -> List[Dict]:
    now = datetime.now(timezone.utc)
    from_time = now - timedelta(hours=_range_hours(range_key))
    deals = mt5.history_deals_get(from_time, now) or []
    account = mt5.account_info()
    if account is None:
        return []

    current_balance = float(account.balance)
    current_equity = float(account.equity)
    realized = sum(_deal_profit(deal) for deal in deals)
    start_balance = current_balance - realized

    points = []
    running = start_balance
    deals_sorted = sorted(deals, key=lambda x: int(getattr(x, "time", 0)))
    if not deals_sorted:
        ts = _now_ms()
        return [{"timestamp": ts, "equity": current_equity, "balance": current_balance}]

    first_ts = int(getattr(deals_sorted[0], "time", int(now.timestamp()))) * 1000
    points.append({"timestamp": first_ts, "equity": running, "balance": running})
    for deal in deals_sorted:
        running += _deal_profit(deal)
        ts = int(getattr(deal, "time", int(now.timestamp()))) * 1000
        points.append({"timestamp": ts, "equity": running, "balance": running})

    points.append({"timestamp": _now_ms(), "equity": current_equity, "balance": current_balance})
    return points


def _curve_indicators(points: List[Dict]) -> List[Dict]:
    if not points:
        return []

    values = [p["equity"] for p in points]
    returns = []
    for i in range(1, len(values)):
        returns.append(_safe_div(values[i] - values[i - 1], values[i - 1]))

    peak = values[0]
    max_dd = 0.0
    for value in values:
        peak = max(peak, value)
        max_dd = max(max_dd, _safe_div(peak - value, peak))

    def n_return(n: int) -> float:
        if len(values) < 2:
            return 0.0
        idx = max(0, len(values) - 1 - n)
        return _safe_div(values[-1] - values[idx], values[idx])

    volatility = (pstdev(returns) if len(returns) > 1 else 0.0) * math.sqrt(365.0)
    sharpe = 0.0
    if volatility > 1e-9 and returns:
        sharpe = (mean(returns) * 365.0) / volatility

    return [
        {"name": "1D Return", "value": _fmt_pct(n_return(24))},
        {"name": "7D Return", "value": _fmt_pct(n_return(24 * 7))},
        {"name": "30D Return", "value": _fmt_pct(n_return(24 * 30))},
        {"name": "Max Drawdown", "value": _fmt_pct(max_dd)},
        {"name": "Volatility", "value": _fmt_pct(volatility)},
        {"name": "Sharpe", "value": f"{sharpe:.2f}"},
    ]


def _map_positions() -> List[Dict]:
    positions = mt5.positions_get() or []
    orders = mt5.orders_get() or []
    pending_by_symbol: Dict[str, Dict[str, float]] = {}
    for order in orders:
        symbol = getattr(order, "symbol", "")
        if not symbol:
            continue
        state = pending_by_symbol.setdefault(symbol, {"count": 0, "lots": 0.0, "notional": 0.0})
        volume = abs(float(getattr(order, "volume_current", 0.0)))
        order_price = float(getattr(order, "price_open", 0.0))
        if order_price <= 0.0:
            order_price = float(getattr(order, "price_current", 0.0))
        state["count"] += 1
        state["lots"] += volume
        state["notional"] += abs(volume * order_price)

    total_mv = 0.0
    mapped = []
    for position in positions:
        symbol = getattr(position, "symbol", "--")
        position_type = int(getattr(position, "type", 0))
        side = "Buy" if position_type == 0 else "Sell"
        volume = float(getattr(position, "volume", 0.0))
        price_open = float(getattr(position, "price_open", 0.0))
        price_current = float(getattr(position, "price_current", 0.0))
        total_pnl = float(getattr(position, "profit", 0.0))

        contract_size = 1.0
        sinfo = mt5.symbol_info(symbol)
        if sinfo is not None and getattr(sinfo, "trade_contract_size", 0.0) > 0:
            contract_size = float(getattr(sinfo, "trade_contract_size", 1.0))

        market_value = abs(volume * price_current * contract_size)
        total_mv += market_value
        ret = _safe_div(price_current - price_open, price_open)

        mapped.append(
            {
                "productName": symbol,
                "code": symbol,
                "side": side,
                "positionTicket": int(getattr(position, "ticket", 0)),
                "quantity": volume,
                "sellableQuantity": volume,
                "costPrice": price_open,
                "latestPrice": price_current,
                "marketValue": market_value,
                "positionRatio": 0.0,
                "dayPnL": total_pnl * 0.2,
                "totalPnL": total_pnl,
                "returnRate": ret,
                "pendingLots": pending_by_symbol.get(symbol, {}).get("lots", 0.0),
                "pendingCount": int(pending_by_symbol.get(symbol, {}).get("count", 0)),
                "pendingPrice": _safe_div(
                    pending_by_symbol.get(symbol, {}).get("notional", 0.0),
                    pending_by_symbol.get(symbol, {}).get("lots", 0.0),
                ),
                "takeProfit": float(getattr(position, "tp", 0.0)),
                "stopLoss": float(getattr(position, "sl", 0.0)),
                "storageFee": float(getattr(position, "swap", 0.0)),
            }
        )

    for item in mapped:
        item["positionRatio"] = _safe_div(item["marketValue"], total_mv)
    return mapped


def _map_pending_orders() -> List[Dict]:
    orders = mt5.orders_get() or []
    mapped = []
    for order in orders:
        symbol = getattr(order, "symbol", "")
        if not symbol:
            continue

        order_type = int(getattr(order, "type", 0))
        side = _order_side(order_type)
        volume = abs(float(getattr(order, "volume_current", 0.0)))
        if volume <= 0.0:
            volume = abs(float(getattr(order, "volume_initial", 0.0)))
        if volume <= 0.0:
            continue

        price_open = float(getattr(order, "price_open", 0.0))
        price_current = float(getattr(order, "price_current", 0.0))
        pending_price = price_open if price_open > 0.0 else price_current
        latest_price = price_current if price_current > 0.0 else pending_price

        mapped.append(
            {
                "productName": symbol,
                "code": symbol,
                "side": side,
                "orderId": int(getattr(order, "ticket", 0)),
                "quantity": 0.0,
                "sellableQuantity": 0.0,
                "costPrice": 0.0,
                "latestPrice": latest_price,
                "marketValue": 0.0,
                "positionRatio": 0.0,
                "dayPnL": 0.0,
                "totalPnL": 0.0,
                "returnRate": 0.0,
                "pendingLots": volume,
                "pendingCount": 1,
                "pendingPrice": pending_price,
                "takeProfit": float(getattr(order, "tp", 0.0)),
                "stopLoss": float(getattr(order, "sl", 0.0)),
            }
        )

    mapped.sort(key=lambda item: item["pendingLots"], reverse=True)
    return mapped


def _map_trades(range_key: str = "all") -> List[Dict]:
    now = datetime.now(timezone.utc)
    from_time = now - timedelta(hours=_range_hours(range_key))
    deals = mt5.history_deals_get(from_time, now) or []
    mapped = []
    lifecycle: Dict[int, Dict[str, float]] = {}
    contract_size_cache: Dict[str, float] = {}

    def contract_size_of(symbol: str) -> float:
        cached = contract_size_cache.get(symbol)
        if cached is not None:
            return cached
        size = 1.0
        sinfo = mt5.symbol_info(symbol)
        if sinfo is not None and getattr(sinfo, "trade_contract_size", 0.0) > 0:
            size = float(getattr(sinfo, "trade_contract_size", 1.0))
        contract_size_cache[symbol] = size
        return size

    sorted_deals = sorted(deals, key=lambda d: int(getattr(d, "time", 0)))

    for deal in sorted_deals:
        symbol = getattr(deal, "symbol", "")
        if not symbol:
            continue
        deal_type = int(getattr(deal, "type", -1))
        if not _is_trade_deal_type(deal_type):
            continue

        volume = abs(float(getattr(deal, "volume", 0.0)))
        if volume <= 0.0:
            continue

        ticket = int(getattr(deal, "ticket", 0))
        order_id = int(getattr(deal, "order", 0))
        position_id = int(getattr(deal, "position_id", 0))
        key = position_id if position_id > 0 else (order_id if order_id > 0 else ticket)
        entry_type = int(getattr(deal, "entry", 0))
        ts = int(getattr(deal, "time", 0)) * 1000
        price = float(getattr(deal, "price", 0.0))

        state = lifecycle.setdefault(
            key,
            {
                "open_time": 0.0,
                "open_price_notional": 0.0,
                "open_volume": 0.0,
                "open_price": price,
                "close_time": 0.0,
                "close_price": price,
                "side": "Buy",
            },
        )

        if _is_entry_open(entry_type):
            if state["open_time"] <= 0.0 or ts < state["open_time"]:
                state["open_time"] = float(ts)
            state["open_volume"] += volume
            state["open_price_notional"] += volume * price
            if state["open_volume"] > 1e-9:
                state["open_price"] = state["open_price_notional"] / state["open_volume"]
            state["side"] = "Buy" if _is_buy_deal_type(deal_type) else "Sell"

        if _is_entry_close(entry_type):
            if ts >= state["close_time"]:
                state["close_time"] = float(ts)
                state["close_price"] = price

    for deal in sorted_deals:
        symbol = getattr(deal, "symbol", "--")
        if symbol == "--" or not symbol:
            continue
        deal_type = int(getattr(deal, "type", -1))
        if not _is_trade_deal_type(deal_type):
            continue

        entry_type = int(getattr(deal, "entry", 0))
        if not _is_entry_close(entry_type):
            # 交易记录以“成交(平仓/反手)”为口径，避免开仓成交重复占用统计口径。
            continue

        volume = abs(float(getattr(deal, "volume", 0.0)))
        if volume <= 0.0:
            continue

        price = float(getattr(deal, "price", 0.0))
        profit = float(getattr(deal, "profit", 0.0))
        commission = float(getattr(deal, "commission", 0.0))
        swap = float(getattr(deal, "swap", 0.0))
        storage_fee = commission + swap
        amount = abs(volume * price * contract_size_of(symbol))

        ticket = int(getattr(deal, "ticket", 0))
        order_id = int(getattr(deal, "order", 0))
        position_id = int(getattr(deal, "position_id", 0))
        key = position_id if position_id > 0 else (order_id if order_id > 0 else ticket)
        state = lifecycle.get(key, {})
        ts = int(getattr(deal, "time", 0)) * 1000

        open_time = int(state.get("open_time", 0))
        if open_time <= 0:
            open_time = ts
        close_time = ts

        open_price = float(state.get("open_price", price))
        close_price = price
        lifecycle_side = str(state.get("side", "")).strip()
        if lifecycle_side in ("Buy", "Sell"):
            side = lifecycle_side
        else:
            # 若窗口内缺失开仓成交，则根据平仓成交方向反推原始持仓方向。
            side = "Buy" if deal_type == 1 else "Sell"

        mapped.append(
            {
                "timestamp": close_time,
                "productName": symbol,
                "code": symbol,
                "side": side,
                "price": price,
                "quantity": volume,
                "amount": amount,
                "fee": storage_fee,
                "profit": profit,
                "openTime": open_time,
                "closeTime": close_time,
                "openPrice": open_price if open_price > 0.0 else price,
                "closePrice": close_price if close_price > 0.0 else price,
                "storageFee": storage_fee,
                "dealTicket": ticket,
                "orderId": order_id,
                "positionId": position_id,
                "entryType": entry_type,
                "remark": getattr(deal, "comment", "") or "",
            }
        )

    mapped.sort(key=lambda item: item["timestamp"], reverse=True)
    return mapped


def _build_overview(positions: List[Dict], trades: List[Dict]) -> List[Dict]:
    account = mt5.account_info()
    if account is None:
        return []

    equity = float(account.equity)
    balance = float(account.balance)
    margin = float(account.margin)
    free_margin = float(account.margin_free)
    market_value = sum(p["marketValue"] for p in positions)
    total_pnl = sum(p["totalPnL"] for p in positions)
    day_pnl = sum(p["dayPnL"] for p in positions)

    today_ts = int(
        datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0).timestamp() * 1000
    )
    day_fee = sum(t["fee"] for t in trades if t["timestamp"] >= today_ts)
    day_pnl -= day_fee

    total_asset = equity + max(0.0, total_pnl * 0.3)
    day_return = _safe_div(day_pnl, max(1.0, equity - day_pnl))
    total_return = _safe_div(total_pnl, max(1.0, balance - total_pnl))
    position_ratio = _safe_div(market_value, max(1.0, equity))
    leverage = int(getattr(account, "leverage", 0) or 0)
    margin_level = float(getattr(account, "margin_level", 0.0) or 0.0)
    margin_level_text = "--" if not math.isfinite(margin_level) else f"{margin_level:.2f}%"

    return [
        {"name": "Total Asset", "value": _fmt_usd(total_asset)},
        {"name": "Margin", "value": _fmt_usd(margin)},
        {"name": "Free Fund", "value": _fmt_usd(free_margin)},
        {"name": "Position Market Value", "value": _fmt_usd(market_value)},
        {"name": "Position PnL", "value": _fmt_money(total_pnl)},
        {"name": "Daily PnL", "value": _fmt_money(day_pnl)},
        {"name": "Cumulative PnL", "value": _fmt_money(total_pnl)},
        {"name": "Current Equity", "value": _fmt_usd(equity)},
        {"name": "Daily Return", "value": _fmt_pct(day_return)},
        {"name": "Total Return", "value": _fmt_pct(total_return)},
        {"name": "Position Ratio", "value": _fmt_pct(position_ratio)},
        {"name": "Leverage", "value": f"{leverage}x" if leverage > 0 else "--"},
        {"name": "Margin Level", "value": margin_level_text},
    ]


def _build_stats(positions: List[Dict], trades: List[Dict], points: List[Dict]) -> List[Dict]:
    open_position_pnl = sum(p["totalPnL"] for p in positions)
    realized_pnl = sum(float(t.get("profit", 0.0)) for t in trades)
    total_pnl = realized_pnl + open_position_pnl

    total_count = len(trades)
    buy_count = len([t for t in trades if t.get("side") == "Buy"])
    sell_count = len([t for t in trades if t.get("side") == "Sell"])

    wins = [float(t.get("profit", 0.0)) for t in trades if float(t.get("profit", 0.0)) > 0.0]
    losses = [float(t.get("profit", 0.0)) for t in trades if float(t.get("profit", 0.0)) < 0.0]
    win_count = len(wins)
    loss_count = len(losses)
    avg_win = mean(wins) if wins else 0.0
    avg_loss = mean(losses) if losses else 0.0
    ratio = abs(avg_win / avg_loss) if abs(avg_loss) > 1e-9 else 0.0
    win_rate = _safe_div(win_count, max(1, win_count + loss_count))

    now = datetime.now(timezone.utc)
    month_start = datetime(now.year, now.month, 1, tzinfo=timezone.utc)
    year_start = datetime(now.year, 1, 1, tzinfo=timezone.utc)
    month_start_ms = int(month_start.timestamp() * 1000)
    year_start_ms = int(year_start.timestamp() * 1000)
    month_profit = sum(float(t.get("profit", 0.0)) for t in trades if int(t.get("closeTime", t.get("timestamp", 0))) >= month_start_ms)
    ytd_profit = sum(float(t.get("profit", 0.0)) for t in trades if int(t.get("closeTime", t.get("timestamp", 0))) >= year_start_ms)

    close_times = [int(t.get("closeTime", t.get("timestamp", 0))) for t in trades if int(t.get("closeTime", t.get("timestamp", 0))) > 0]
    if close_times:
        span_days = max(1.0, (max(close_times) - min(close_times)) / (24.0 * 60.0 * 60.0 * 1000.0))
    else:
        span_days = 1.0
    daily_avg_profit = _safe_div(realized_pnl, span_days)

    max_dd = 0.0
    if points:
        peak = points[0]["equity"]
        for point in points:
            peak = max(peak, point["equity"])
            max_dd = max(max_dd, _safe_div(peak - point["equity"], peak))

    volatility = 0.0
    if len(points) > 2:
        returns = []
        for i in range(1, len(points)):
            returns.append(_safe_div(points[i]["equity"] - points[i - 1]["equity"], points[i - 1]["equity"]))
        volatility = (pstdev(returns) if len(returns) > 1 else 0.0) * math.sqrt(365.0)

    market_value = sum(p["marketValue"] for p in positions)
    top5 = sum(sorted([p["positionRatio"] for p in positions], reverse=True)[:5])
    latest_equity = points[-1]["equity"] if points else max(1.0, market_value)
    cumulative_return = _safe_div(realized_pnl, max(1.0, latest_equity - realized_pnl))

    longest_win = 0
    longest_loss = 0
    current_win = 0
    current_loss = 0
    ordered_trades = sorted(trades, key=lambda t: int(t.get("closeTime", t.get("timestamp", 0))))
    for trade in ordered_trades:
        pnl = float(trade.get("profit", 0.0))
        if pnl > 0.0:
            current_win += 1
            current_loss = 0
            longest_win = max(longest_win, current_win)
        elif pnl < 0.0:
            current_loss += 1
            current_win = 0
            longest_loss = max(longest_loss, current_loss)

    symbol_exposure: Dict[str, float] = {}
    for position in positions:
        code = str(position.get("code", ""))
        if not code:
            continue
        symbol_exposure[code] = symbol_exposure.get(code, 0.0) + float(position.get("marketValue", 0.0))
    symbols = sorted(symbol_exposure.items(), key=lambda item: item[1], reverse=True)
    asset_distribution = " / ".join([symbol for symbol, _ in symbols[:3]]) if symbols else "--"

    return [
        {"name": "Cumulative Profit", "value": _fmt_money(total_pnl)},
        {"name": "Cumulative Return", "value": _fmt_pct(cumulative_return)},
        {"name": "Month Profit", "value": _fmt_money(month_profit)},
        {"name": "YTD Profit", "value": _fmt_money(ytd_profit)},
        {"name": "Daily Avg Profit", "value": _fmt_money(daily_avg_profit)},
        {"name": "Total Trades", "value": str(total_count)},
        {"name": "Buy Count", "value": str(buy_count)},
        {"name": "Sell Count", "value": str(sell_count)},
        {"name": "Win Rate", "value": _fmt_pct(win_rate)},
        {"name": "Win/Loss Trades", "value": f"{win_count} / {loss_count}"},
        {"name": "Avg Profit/Trade", "value": _fmt_usd(avg_win)},
        {"name": "Avg Loss/Trade", "value": _fmt_money(avg_loss)},
        {"name": "PnL Ratio", "value": f"{ratio:.2f}"},
        {"name": "Max Drawdown", "value": _fmt_pct(max_dd)},
        {"name": "Volatility", "value": _fmt_pct(volatility)},
        {"name": "Position Utilization", "value": _fmt_pct(_safe_div(market_value, max(1.0, latest_equity)))},
        {"name": "Single Position Max", "value": _fmt_pct(max([p["positionRatio"] for p in positions], default=0.0))},
        {"name": "Concentration", "value": _fmt_pct(top5)},
        {"name": "Consecutive Win/Loss", "value": f"{longest_win} / {longest_loss}"},
        {"name": "Current Position Amount", "value": _fmt_usd(market_value)},
        {"name": "Asset Distribution", "value": asset_distribution},
        {"name": "Top-5 Position Ratio", "value": _fmt_pct(top5)},
    ]


def _snapshot_from_mt5(range_key: str) -> Dict:
    with state_lock:
        _ensure_mt5()
        try:
            account = mt5.account_info()
            if account is None:
                raise RuntimeError("account_info is None")
            points = _build_curve(range_key)
            positions = _map_positions()
            pending_orders = _map_pending_orders()
            trades = _map_trades(range_key)
            overview = _build_overview(positions, trades)
            indicators = _curve_indicators(points)
            stats = _build_stats(positions, trades, points)
            return {
                "accountMeta": {
                    "login": str(getattr(account, "login", LOGIN)),
                    "server": str(getattr(account, "server", SERVER)),
                    "source": "MT5 Python Pull",
                    "updatedAt": _now_ms(),
                    "currency": str(getattr(account, "currency", "")),
                    "leverage": int(getattr(account, "leverage", 0) or 0),
                    "name": str(getattr(account, "name", "")),
                    "company": str(getattr(account, "company", "")),
                    "range": range_key,
                    "tradeCount": len(trades),
                    "positionCount": len(positions),
                    "pendingOrderCount": len(pending_orders),
                    "curvePointCount": len(points),
                },
                "overviewMetrics": overview,
                "curvePoints": points,
                "curveIndicators": indicators,
                "positions": positions,
                "pendingOrders": pending_orders,
                "trades": trades,
                "statsMetrics": stats,
            }
        finally:
            _shutdown_mt5()


def _normalize_snapshot(payload: Optional[Dict], source_fallback: str) -> Dict:
    data = payload or {}
    account_meta = data.get("accountMeta") or {}
    account_meta.setdefault("login", str(LOGIN))
    account_meta.setdefault("server", SERVER)
    account_meta.setdefault("source", source_fallback)
    account_meta.setdefault("updatedAt", _now_ms())
    data["accountMeta"] = account_meta
    data["overviewMetrics"] = data.get("overviewMetrics") or []
    data["curvePoints"] = data.get("curvePoints") or []
    data["curveIndicators"] = data.get("curveIndicators") or []
    data["positions"] = data.get("positions") or []
    data["pendingOrders"] = data.get("pendingOrders") or []
    data["trades"] = data.get("trades") or []
    data["statsMetrics"] = data.get("statsMetrics") or []
    return data


def _stable_json_size(value: Any) -> int:
    return len(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")))


def _normalize_digest_curve_points(points: List[Dict]) -> List[Dict]:
    if not points:
        return []
    keep_timestamp = len(points) > 1
    normalized: List[Dict] = []
    for item in points:
        point = {
            "equity": round(float(item.get("equity", 0.0) or 0.0), 2),
            "balance": round(float(item.get("balance", 0.0) or 0.0), 2),
        }
        if keep_timestamp:
            point["timestamp"] = int(item.get("timestamp", 0) or 0)
        if normalized and normalized[-1] == point:
            continue
        normalized.append(point)
    return normalized


def _normalize_digest_stats_metrics(metrics: List[Dict]) -> List[Dict]:
    normalized: List[Dict] = []
    for item in metrics or []:
        name = str(item.get("name", ""))
        if name.strip().lower() == "pushed at":
            continue
        normalized.append({
            "name": name,
            "value": str(item.get("value", "")),
        })
    return normalized


def _snapshot_digest(snapshot: Dict) -> str:
    account_meta = snapshot.get("accountMeta") or {}
    core = {
        "accountMeta": {
            "login": str(account_meta.get("login", "")),
            "server": str(account_meta.get("server", "")),
            "source": str(account_meta.get("source", "")),
        },
        "overviewMetrics": snapshot.get("overviewMetrics") or [],
        "curvePoints": _normalize_digest_curve_points(snapshot.get("curvePoints") or []),
        "curveIndicators": snapshot.get("curveIndicators") or [],
        "positions": snapshot.get("positions") or [],
        "pendingOrders": snapshot.get("pendingOrders") or [],
        "trades": snapshot.get("trades") or [],
        "statsMetrics": _normalize_digest_stats_metrics(snapshot.get("statsMetrics") or []),
    }
    payload = json.dumps(core, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha1(payload).hexdigest()


def _position_key(item: Dict) -> str:
    ticket = int(item.get("positionTicket", 0) or 0)
    if ticket > 0:
        return f"position:{ticket}"
    code = str(item.get("code", "")).upper()
    side = str(item.get("side", "")).upper()
    qty = round(float(item.get("quantity", 0.0) or 0.0), 4)
    cost = round(float(item.get("costPrice", 0.0) or 0.0), 2)
    return f"position:{code}|{side}|{qty}|{cost}"


def _pending_key(item: Dict) -> str:
    order_id = int(item.get("orderId", 0) or 0)
    if order_id > 0:
        return f"pending:{order_id}"
    code = str(item.get("code", "")).upper()
    side = str(item.get("side", "")).upper()
    lots = round(float(item.get("pendingLots", 0.0) or 0.0), 4)
    price = round(float(item.get("pendingPrice", 0.0) or 0.0), 4)
    return f"pending:{code}|{side}|{lots}|{price}"


def _trade_key(item: Dict) -> str:
    ticket = int(item.get("dealTicket", 0) or 0)
    if ticket > 0:
        return f"trade:{ticket}"
    order_id = int(item.get("orderId", 0) or 0)
    position_id = int(item.get("positionId", 0) or 0)
    close_time = int(item.get("closeTime", item.get("timestamp", 0)) or 0)
    qty = round(float(item.get("quantity", 0.0) or 0.0), 4)
    return f"trade:{order_id}|{position_id}|{close_time}|{qty}"


def _index_entities(items: List[Dict], key_fn) -> Dict[str, Dict]:
    indexed: Dict[str, Dict] = {}
    for item in items:
        key = key_fn(item)
        payload = dict(item)
        payload["_key"] = key
        indexed[key] = payload
    return indexed


def _diff_entities(previous: List[Dict], current: List[Dict], key_fn) -> Dict[str, List]:
    previous_map = _index_entities(previous, key_fn)
    current_map = _index_entities(current, key_fn)

    upsert: List[Dict] = []
    for key, item in current_map.items():
        if key not in previous_map or previous_map.get(key) != item:
            upsert.append(item)

    remove = [key for key in previous_map.keys() if key not in current_map]
    return {"upsert": upsert, "remove": remove}


def _diff_curve_points(previous: List[Dict], current: List[Dict]) -> Dict[str, Any]:
    if not previous:
        return {"reset": False, "append": current}
    if not current:
        return {"reset": True, "append": []}

    prev_by_ts = {int(item.get("timestamp", 0)): item for item in previous if int(item.get("timestamp", 0)) > 0}
    cur_by_ts = {int(item.get("timestamp", 0)): item for item in current if int(item.get("timestamp", 0)) > 0}
    if not prev_by_ts or not cur_by_ts:
        return {"reset": True, "append": current}

    for ts, prev_item in prev_by_ts.items():
        cur_item = cur_by_ts.get(ts)
        if cur_item is None:
            return {"reset": True, "append": current}
        if cur_item != prev_item:
            return {"reset": True, "append": current}

    prev_last_ts = max(prev_by_ts.keys())
    append = [item for item in current if int(item.get("timestamp", 0)) > prev_last_ts]
    return {"reset": False, "append": append}


def _build_delta_snapshot(previous: Dict, current: Dict) -> Dict:
    return {
        "positions": _diff_entities(previous.get("positions") or [], current.get("positions") or [], _position_key),
        "pendingOrders": _diff_entities(previous.get("pendingOrders") or [], current.get("pendingOrders") or [], _pending_key),
        "trades": _diff_entities(previous.get("trades") or [], current.get("trades") or [], _trade_key),
        "curvePoints": _diff_curve_points(previous.get("curvePoints") or [], current.get("curvePoints") or []),
    }


def _build_full_response(snapshot: Dict, sync_seq: int) -> Dict:
    meta = dict(snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED
    return {
        "accountMeta": meta,
        "isDelta": False,
        "unchanged": False,
        "overviewMetrics": snapshot.get("overviewMetrics") or [],
        "curvePoints": snapshot.get("curvePoints") or [],
        "curveIndicators": snapshot.get("curveIndicators") or [],
        "positions": snapshot.get("positions") or [],
        "pendingOrders": snapshot.get("pendingOrders") or [],
        "trades": snapshot.get("trades") or [],
        "statsMetrics": snapshot.get("statsMetrics") or [],
    }


def _build_snapshot_with_cache(range_key: str) -> Dict:
    now_ms = _now_ms()
    with snapshot_cache_lock:
        cached = snapshot_build_cache.get(range_key)
        if cached and (now_ms - int(cached.get("builtAt", 0))) <= SNAPSHOT_BUILD_CACHE_MS:
            return cached.get("snapshot")

    snapshot = _normalize_snapshot(_select_snapshot(range_key), "MT5 Gateway")
    with snapshot_cache_lock:
        snapshot_build_cache[range_key] = {"builtAt": now_ms, "snapshot": snapshot}
    return snapshot


def _build_snapshot_response(range_key: str, since_seq: int, delta: bool) -> Dict:
    snapshot = _build_snapshot_with_cache(range_key)
    digest = _snapshot_digest(snapshot)

    with snapshot_cache_lock:
        state = snapshot_sync_cache.get(range_key)
        previous_snapshot: Optional[Dict] = None
        previous_seq = 0

        if state is None:
            sync_seq = 1
            snapshot_sync_cache[range_key] = {
                "seq": sync_seq,
                "digest": digest,
                "snapshot": snapshot,
                "previousSeq": 0,
                "previousSnapshot": None,
            }
            changed = True
        else:
            if state.get("digest") == digest:
                sync_seq = int(state.get("seq", 1))
                changed = False
                snapshot = state.get("snapshot") or snapshot
                previous_seq = int(state.get("previousSeq", 0))
                previous_snapshot = state.get("previousSnapshot")
            else:
                previous_seq = int(state.get("seq", 0))
                previous_snapshot = state.get("snapshot")
                sync_seq = previous_seq + 1
                snapshot_sync_cache[range_key] = {
                    "seq": sync_seq,
                    "digest": digest,
                    "snapshot": snapshot,
                    "previousSeq": previous_seq,
                    "previousSnapshot": previous_snapshot,
                }
                changed = True

    if not SNAPSHOT_DELTA_ENABLED or not delta or since_seq <= 0:
        return _build_full_response(snapshot, sync_seq)

    meta = dict(snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED

    if since_seq == sync_seq:
        return {"accountMeta": meta, "isDelta": True, "unchanged": True}

    if changed and previous_snapshot is not None and since_seq == previous_seq:
        delta_payload = _build_delta_snapshot(previous_snapshot, snapshot)
        delta_response = {
            "accountMeta": meta,
            "isDelta": True,
            "unchanged": False,
            "delta": delta_payload,
            "overviewMetrics": snapshot.get("overviewMetrics") or [],
            "curveIndicators": snapshot.get("curveIndicators") or [],
            "statsMetrics": snapshot.get("statsMetrics") or [],
        }
        full_response = _build_full_response(snapshot, sync_seq)
        if _stable_json_size(delta_response) <= _stable_json_size(full_response) * SNAPSHOT_DELTA_FALLBACK_RATIO:
            return delta_response

    return _build_full_response(snapshot, sync_seq)


def _is_ea_snapshot_fresh() -> bool:
    if ea_snapshot_cache is None:
        return False
    updated_at = int((ea_snapshot_cache.get("accountMeta") or {}).get("updatedAt", 0))
    reference = max(updated_at, ea_snapshot_received_at_ms)
    return (_now_ms() - reference) <= EA_SNAPSHOT_TTL_SEC * 1000


def _select_snapshot(range_key: str) -> Dict:
    mode = GATEWAY_MODE if GATEWAY_MODE in {"auto", "pull", "ea"} else "auto"

    if mode == "ea":
        if _is_ea_snapshot_fresh():
            return _normalize_snapshot(ea_snapshot_cache, "MT5 EA Push")
        raise RuntimeError("No fresh EA snapshot found. Please start MT5 EA push first.")

    if mode == "pull":
        return _snapshot_from_mt5(range_key)

    # auto mode: prefer MT5 pull first (full historical fidelity), then EA push fallback.
    if mt5 is not None and _is_mt5_configured():
        try:
            return _snapshot_from_mt5(range_key)
        except Exception:
            pass

    if _is_ea_snapshot_fresh():
        return _normalize_snapshot(ea_snapshot_cache, "MT5 EA Push")

    if ea_snapshot_cache is not None:
        stale = _normalize_snapshot(ea_snapshot_cache, "MT5 EA Push")
        stale["accountMeta"]["source"] = f"{stale['accountMeta'].get('source', 'MT5 EA Push')} (stale)"
        return stale

    raise RuntimeError("No available data source. Configure MT5 pull or start EA push.")


@app.get("/health")
def health():
    try:
        info = {
            "ok": True,
            "gatewayMode": GATEWAY_MODE,
            "mt5PackageAvailable": mt5 is not None,
            "mt5Configured": _is_mt5_configured(),
            "mt5ConfiguredLogin": str(LOGIN),
            "mt5ConfiguredServer": SERVER,
            "mt5PathEnv": PATH or "",
            "mt5LastConnectedPath": mt5_last_connected_path,
            "mt5DiscoveredTerminalCandidates": MT5_TERMINAL_CANDIDATES[:6],
            "eaSnapshotFresh": _is_ea_snapshot_fresh(),
            "eaSnapshotReceivedAt": ea_snapshot_received_at_ms,
            "snapshotDeltaEnabled": SNAPSHOT_DELTA_ENABLED,
            "snapshotBuildCacheMs": SNAPSHOT_BUILD_CACHE_MS,
        }
        if mt5 is None:
            return info
        with state_lock:
            if _is_mt5_configured():
                try:
                    _ensure_mt5()
                    account = mt5.account_info()
                    info["mt5Connected"] = account is not None
                    info["login"] = str(getattr(account, "login", LOGIN)) if account else str(LOGIN)
                    info["server"] = str(getattr(account, "server", SERVER)) if account else SERVER
                    info["lastError"] = str(mt5.last_error())
                except Exception as exc:
                    info["mt5Connected"] = False
                    info["lastError"] = str(exc)
                finally:
                    _shutdown_mt5()
            else:
                info["mt5Connected"] = False
                info["lastError"] = str(mt5.last_error())
        return info
    except Exception as exc:
        return {"ok": False, "error": str(exc)}


@app.get("/v1/source")
def source_status():
    return {
        "gatewayMode": GATEWAY_MODE,
        "mt5PackageAvailable": mt5 is not None,
        "mt5Configured": _is_mt5_configured(),
        "mt5ConfiguredLogin": str(LOGIN),
        "mt5ConfiguredServer": SERVER,
        "mt5PathEnv": PATH or "",
        "mt5LastConnectedPath": mt5_last_connected_path,
        "mt5DiscoveredTerminalCandidates": MT5_TERMINAL_CANDIDATES[:6],
        "eaSnapshotFresh": _is_ea_snapshot_fresh(),
        "eaSnapshotReceivedAt": ea_snapshot_received_at_ms,
        "snapshotDeltaEnabled": SNAPSHOT_DELTA_ENABLED,
        "snapshotBuildCacheMs": SNAPSHOT_BUILD_CACHE_MS,
    }


@app.post("/v1/ea/snapshot")
def ingest_ea_snapshot(payload: Dict, x_bridge_token: Optional[str] = Header(default=None)):
    global ea_snapshot_cache, ea_snapshot_received_at_ms, ea_snapshot_change_digest

    if EA_INGEST_TOKEN:
        if not x_bridge_token or x_bridge_token != EA_INGEST_TOKEN:
            raise HTTPException(status_code=401, detail="Invalid bridge token.")

    normalized = _normalize_snapshot(payload, "MT5 EA Push")
    normalized["accountMeta"]["source"] = "MT5 EA Push"
    normalized["accountMeta"]["updatedAt"] = _now_ms()
    change_digest = _snapshot_digest(normalized)
    changed = False
    received_at = _now_ms()

    with state_lock:
        changed = ea_snapshot_cache is None or change_digest != ea_snapshot_change_digest
        ea_snapshot_received_at_ms = received_at
        if changed:
            ea_snapshot_cache = normalized
            ea_snapshot_change_digest = change_digest
    if changed:
        with snapshot_cache_lock:
            snapshot_build_cache.clear()

    return {"ok": True, "receivedAt": ea_snapshot_received_at_ms, "changed": changed}


@app.get("/v1/snapshot")
def snapshot(
    range: str = Query(default="7d", pattern="^(1d|7d|1m|3m|1y|all)$"),
    since: int = Query(default=0, ge=0),
    delta: int = Query(default=1, ge=0, le=1),
):
    try:
        return _build_snapshot_response(range.lower(), since_seq=since, delta=(delta == 1))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


if __name__ == "__main__":
    uvicorn.run("server_v2:app", host=HOST, port=PORT, reload=False)
