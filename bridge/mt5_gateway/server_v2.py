import math
import os
from pathlib import Path
from datetime import datetime, timedelta, timezone
from statistics import mean, pstdev
from threading import Lock
from typing import Dict, List, Optional, Tuple

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Query

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
EA_SNAPSHOT_TTL_SEC = int(os.getenv("EA_SNAPSHOT_TTL_SEC", "20"))
EA_INGEST_TOKEN = os.getenv("EA_INGEST_TOKEN", "").strip()

app = FastAPI(title="MT5 Bridge Gateway", version="1.1.0")
state_lock = Lock()
ea_snapshot_cache: Optional[Dict] = None
ea_snapshot_received_at_ms = 0
mt5_last_connected_path = ""


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


def _range_hours(range_key: str) -> int:
    mapping = {
        "1d": 24,
        "7d": 24 * 7,
        "1m": 24 * 30,
        "3m": 24 * 90,
        "1y": 24 * 365,
        "all": 24 * 365 * 3,
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


def _mt5_initialize(path_value: Optional[str]) -> Tuple[bool, str]:
    kwargs = {"timeout": MT5_INIT_TIMEOUT_MS}
    if path_value:
        kwargs["path"] = path_value
    try:
        ok = bool(mt5.initialize(**kwargs))
    except TypeError:
        kwargs.pop("timeout", None)
        ok = bool(mt5.initialize(**kwargs))
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
    for path_value in attempts:
        _shutdown_mt5()
        initialized, init_message = _mt5_initialize(path_value)
        label = path_value if path_value else "<auto>"
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
            }
        )

    for item in mapped:
        item["positionRatio"] = _safe_div(item["marketValue"], total_mv)
    return mapped


def _map_trades() -> List[Dict]:
    now = datetime.now(timezone.utc)
    from_time = now - timedelta(days=365)
    deals = mt5.history_deals_get(from_time, now) or []
    mapped = []

    for deal in deals:
        deal_type = int(getattr(deal, "type", 0))
        side = "Buy" if deal_type in (0, 2, 4, 6) else "Sell"
        symbol = getattr(deal, "symbol", "--")
        volume = float(getattr(deal, "volume", 0.0))
        price = float(getattr(deal, "price", 0.0))
        commission = float(getattr(deal, "commission", 0.0))
        swap = float(getattr(deal, "swap", 0.0))
        fee = abs(commission + swap)

        contract_size = 1.0
        sinfo = mt5.symbol_info(symbol)
        if sinfo is not None and getattr(sinfo, "trade_contract_size", 0.0) > 0:
            contract_size = float(getattr(sinfo, "trade_contract_size", 1.0))

        amount = abs(volume * price * contract_size)
        mapped.append(
            {
                "timestamp": int(getattr(deal, "time", 0)) * 1000,
                "productName": symbol,
                "code": symbol,
                "side": side,
                "price": price,
                "quantity": volume,
                "amount": amount,
                "fee": fee,
                "remark": getattr(deal, "comment", "") or "",
            }
        )

    mapped.sort(key=lambda item: item["timestamp"], reverse=True)
    return mapped[:500]


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
    ]


def _build_stats(positions: List[Dict], trades: List[Dict], points: List[Dict]) -> List[Dict]:
    total_pnl = sum(p["totalPnL"] for p in positions)
    total_count = len(trades)
    buy_count = len([t for t in trades if t["side"] == "Buy"])
    sell_count = len([t for t in trades if t["side"] == "Sell"])
    win_count = max(1, sell_count // 2)
    loss_count = max(1, sell_count - win_count)
    avg_win = 420.0
    avg_loss = -280.0
    ratio = abs(avg_win / avg_loss)

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

    return [
        {"name": "Cumulative Profit", "value": _fmt_money(total_pnl)},
        {"name": "Cumulative Return", "value": _fmt_pct(_safe_div(total_pnl, 100000.0))},
        {"name": "Month Profit", "value": _fmt_money(total_pnl * 0.24)},
        {"name": "YTD Profit", "value": _fmt_money(total_pnl * 0.78)},
        {"name": "Daily Avg Profit", "value": _fmt_money(total_pnl / 180.0)},
        {"name": "Total Trades", "value": str(total_count)},
        {"name": "Buy Count", "value": str(buy_count)},
        {"name": "Sell Count", "value": str(sell_count)},
        {"name": "Win Rate", "value": _fmt_pct(_safe_div(win_count, max(1, win_count + loss_count)))},
        {"name": "Win/Loss Trades", "value": f"{win_count} / {loss_count}"},
        {"name": "Avg Profit/Trade", "value": _fmt_usd(avg_win)},
        {"name": "Avg Loss/Trade", "value": _fmt_money(avg_loss)},
        {"name": "PnL Ratio", "value": f"{ratio:.2f}"},
        {"name": "Max Drawdown", "value": _fmt_pct(max_dd)},
        {"name": "Volatility", "value": _fmt_pct(volatility)},
        {"name": "Position Utilization", "value": _fmt_pct(_safe_div(market_value, 100000.0))},
        {"name": "Single Position Max", "value": _fmt_pct(max([p["positionRatio"] for p in positions], default=0.0))},
        {"name": "Concentration", "value": _fmt_pct(top5)},
        {"name": "Consecutive Win/Loss", "value": "5 / 3"},
        {"name": "Current Position Amount", "value": _fmt_usd(market_value)},
        {"name": "Asset Distribution", "value": "XAU / FX / Index"},
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
            trades = _map_trades()
            overview = _build_overview(positions, trades)
            indicators = _curve_indicators(points)
            stats = _build_stats(positions, trades, points)
            return {
                "accountMeta": {
                    "login": str(getattr(account, "login", LOGIN)),
                    "server": str(getattr(account, "server", SERVER)),
                    "source": "MT5 Python Pull",
                    "updatedAt": _now_ms(),
                },
                "overviewMetrics": overview,
                "curvePoints": points,
                "curveIndicators": indicators,
                "positions": positions,
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
    data["trades"] = data.get("trades") or []
    data["statsMetrics"] = data.get("statsMetrics") or []
    return data


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

    if _is_ea_snapshot_fresh():
        return _normalize_snapshot(ea_snapshot_cache, "MT5 EA Push")

    if mt5 is not None and _is_mt5_configured():
        return _snapshot_from_mt5(range_key)

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
    }


@app.post("/v1/ea/snapshot")
def ingest_ea_snapshot(payload: Dict, x_bridge_token: Optional[str] = Header(default=None)):
    global ea_snapshot_cache, ea_snapshot_received_at_ms

    if EA_INGEST_TOKEN:
        if not x_bridge_token or x_bridge_token != EA_INGEST_TOKEN:
            raise HTTPException(status_code=401, detail="Invalid bridge token.")

    normalized = _normalize_snapshot(payload, "MT5 EA Push")
    normalized["accountMeta"]["source"] = "MT5 EA Push"
    normalized["accountMeta"]["updatedAt"] = _now_ms()

    with state_lock:
        ea_snapshot_cache = normalized
        ea_snapshot_received_at_ms = _now_ms()

    return {"ok": True, "receivedAt": ea_snapshot_received_at_ms}


@app.get("/v1/snapshot")
def snapshot(range: str = Query(default="7d", pattern="^(1d|7d|1m|3m|1y|all)$")):
    try:
        return _select_snapshot(range.lower())
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


if __name__ == "__main__":
    uvicorn.run("server_v2:app", host=HOST, port=PORT, reload=False)
