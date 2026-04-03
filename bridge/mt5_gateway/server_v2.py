import hashlib
import json
import math
import os
import urllib.error
import urllib.parse
import urllib.request
import asyncio
from pathlib import Path
from datetime import datetime, timedelta, timezone
from statistics import mean, pstdev
from threading import Lock
from typing import Any, Dict, List, Optional, Tuple

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, Header, HTTPException, Query, Request, Response, WebSocket, WebSocketDisconnect
from fastapi.middleware.gzip import GZipMiddleware
import websockets

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
try:
    MT5_TIME_OFFSET_MINUTES = int(os.getenv("MT5_TIME_OFFSET_MINUTES", "0"))
except Exception:
    MT5_TIME_OFFSET_MINUTES = 0
HOST = os.getenv("GATEWAY_HOST", "0.0.0.0")
PORT = int(os.getenv("GATEWAY_PORT", "8787"))
GATEWAY_MODE = os.getenv("GATEWAY_MODE", "auto").strip().lower()  # auto | pull | ea
EA_SNAPSHOT_TTL_SEC = int(os.getenv("EA_SNAPSHOT_TTL_SEC", "35"))
EA_INGEST_TOKEN = os.getenv("EA_INGEST_TOKEN", "").strip()
SNAPSHOT_BUILD_CACHE_MS = int(os.getenv("SNAPSHOT_BUILD_CACHE_MS", "8000"))
SNAPSHOT_BUILD_MAX_STALE_MS = max(
    SNAPSHOT_BUILD_CACHE_MS,
    int(os.getenv("SNAPSHOT_BUILD_MAX_STALE_MS", "30000"))
)
SNAPSHOT_BUILD_CACHE_MAX_ENTRIES = max(1, int(os.getenv("SNAPSHOT_BUILD_CACHE_MAX_ENTRIES", "6")))
SNAPSHOT_DELTA_ENABLED = os.getenv("SNAPSHOT_DELTA_ENABLED", "1").strip().lower() not in {"0", "false", "no"}
SNAPSHOT_DELTA_FALLBACK_RATIO = float(os.getenv("SNAPSHOT_DELTA_FALLBACK_RATIO", "0.85"))
SNAPSHOT_SYNC_CACHE_MAX_ENTRIES = max(1, int(os.getenv("SNAPSHOT_SYNC_CACHE_MAX_ENTRIES", "12")))
SNAPSHOT_RANGE_ALL_DAYS = max(30, int(os.getenv("SNAPSHOT_RANGE_ALL_DAYS", "730")))
BINANCE_REST_UPSTREAM = (os.getenv("BINANCE_REST_UPSTREAM", "https://fapi.binance.com").strip().rstrip("/"))
BINANCE_WS_UPSTREAM = (os.getenv("BINANCE_WS_UPSTREAM", "wss://fstream.binance.com").strip().rstrip("/"))
ABNORMAL_RECORD_LIMIT = max(50, int(os.getenv("ABNORMAL_RECORD_LIMIT", "500")))
ABNORMAL_ALERT_LIMIT = max(20, int(os.getenv("ABNORMAL_ALERT_LIMIT", "120")))
ABNORMAL_KLINE_LIMIT = max(2, int(os.getenv("ABNORMAL_KLINE_LIMIT", "8")))
ABNORMAL_FETCH_CACHE_MS = max(1000, int(os.getenv("ABNORMAL_FETCH_CACHE_MS", "4000")))
ABNORMAL_DELTA_ENABLED = os.getenv("ABNORMAL_DELTA_ENABLED", "1").strip().lower() not in {"0", "false", "no"}
ABNORMAL_SYMBOLS = ("BTCUSDT", "XAUUSD")

app = FastAPI(title="MT5 Bridge Gateway", version="1.1.0")
app.add_middleware(GZipMiddleware, minimum_size=512)
state_lock = Lock()
snapshot_cache_lock = Lock()
abnormal_state_lock = Lock()
ea_snapshot_cache: Optional[Dict] = None
ea_snapshot_received_at_ms = 0
ea_snapshot_change_digest = ""
mt5_last_connected_path = ""
snapshot_build_cache: Dict[str, Dict[str, Any]] = {}
snapshot_sync_cache: Dict[str, Dict[str, Any]] = {}
abnormal_config_state: Dict[str, Any] = {"logicAnd": False, "symbols": {}}
abnormal_record_store: List[Dict[str, Any]] = []
abnormal_alert_store: List[Dict[str, Any]] = []
abnormal_last_close_time_by_symbol: Dict[str, int] = {}
abnormal_last_notify_at: Dict[str, int] = {}
abnormal_kline_cache: Dict[str, Dict[str, Any]] = {}
abnormal_sync_state: Dict[str, Any] = {}


def _now_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def _apply_mt5_time_offset_ms(value: int) -> int:
    """按配置补偿 MT5 返回时间与本地展示口径之间的固定偏移。"""
    if value <= 0:
        return 0
    return int(value + MT5_TIME_OFFSET_MINUTES * 60 * 1000)


def _deal_time_ms(deal: Any) -> int:
    value = int(getattr(deal, "time_msc", 0) or 0)
    if value > 0:
        return _apply_mt5_time_offset_ms(value)
    return _apply_mt5_time_offset_ms(int(getattr(deal, "time", 0) or 0) * 1000)


# 统一裁剪缓存条目，只保留最近访问的部分，避免快照缓存长期堆满内存。
def _trim_cache_entries_locked(cache: Dict[str, Any], limit: int) -> None:
    while len(cache) > max(1, limit):
        oldest_key = next(iter(cache))
        cache.pop(oldest_key, None)


# 统一写入有序缓存，命中时会刷新顺序，便于按最近使用裁剪。
def _remember_cache_entry_locked(cache: Dict[str, Any], key: str, value: Any, limit: int) -> None:
    cache.pop(key, None)
    cache[key] = value
    _trim_cache_entries_locked(cache, limit)


# 仅在 EA 推送仍新鲜时延长快照缓存寿命，减少固定轮询下的一快一慢交替。
def _should_slide_snapshot_build_cache(cached: Optional[Dict[str, Any]], now_ms: int) -> bool:
    if not cached:
        return False
    built_at = int(cached.get("builtAt", 0))
    age = now_ms - built_at
    if age <= SNAPSHOT_BUILD_CACHE_MS or age > SNAPSHOT_BUILD_MAX_STALE_MS:
        return False
    snapshot = cached.get("snapshot") or {}
    source = str((snapshot.get("accountMeta") or {}).get("source", ""))
    if "EA Push" not in source:
        return False
    return _is_ea_snapshot_fresh()


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


def _normalize_abnormal_symbol(symbol: str) -> str:
    value = str(symbol or "").strip().upper()
    if value in {"BTC", "BTCUSD", "BTCUSDT", "XBT"}:
        return "BTCUSDT"
    if value in {"XAU", "XAUUSD", "GOLD"}:
        return "XAUUSD"
    return value


def _default_abnormal_symbol_config(symbol: str) -> Dict[str, Any]:
    normalized = _normalize_abnormal_symbol(symbol)
    if normalized == "XAUUSD":
        return {
            "symbol": normalized,
            "volumeThreshold": 3000.0,
            "amountThreshold": 15000000.0,
            "priceChangeThreshold": 10.0,
            "volumeEnabled": True,
            "amountEnabled": True,
            "priceChangeEnabled": True,
        }
    return {
        "symbol": "BTCUSDT",
        "volumeThreshold": 1000.0,
        "amountThreshold": 70000000.0,
        "priceChangeThreshold": 200.0,
        "volumeEnabled": True,
        "amountEnabled": True,
        "priceChangeEnabled": True,
    }


def _abnormal_bool(value: Any, fallback: bool) -> bool:
    if isinstance(value, bool):
        return value
    if value is None:
        return fallback
    text = str(value).strip().lower()
    if text in {"1", "true", "yes", "on"}:
        return True
    if text in {"0", "false", "no", "off"}:
        return False
    return fallback


def _abnormal_float(value: Any, fallback: float) -> float:
    try:
        return float(value)
    except Exception:
        return float(fallback)


def _sanitize_abnormal_symbol_config(symbol: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    defaults = _default_abnormal_symbol_config(symbol)
    return {
        "symbol": defaults["symbol"],
        "volumeThreshold": max(0.0, _abnormal_float(payload.get("volumeThreshold"), defaults["volumeThreshold"])),
        "amountThreshold": max(0.0, _abnormal_float(payload.get("amountThreshold"), defaults["amountThreshold"])),
        "priceChangeThreshold": max(0.0, _abnormal_float(payload.get("priceChangeThreshold"), defaults["priceChangeThreshold"])),
        "volumeEnabled": _abnormal_bool(payload.get("volumeEnabled"), defaults["volumeEnabled"]),
        "amountEnabled": _abnormal_bool(payload.get("amountEnabled"), defaults["amountEnabled"]),
        "priceChangeEnabled": _abnormal_bool(payload.get("priceChangeEnabled"), defaults["priceChangeEnabled"]),
    }


def _ensure_abnormal_defaults_locked() -> None:
    symbols = abnormal_config_state.setdefault("symbols", {})
    for symbol in ABNORMAL_SYMBOLS:
        normalized = _normalize_abnormal_symbol(symbol)
        if normalized not in symbols:
            symbols[normalized] = _default_abnormal_symbol_config(normalized)


def _copy_abnormal_config_locked() -> Dict[str, Any]:
    _ensure_abnormal_defaults_locked()
    return {
        "logicAnd": bool(abnormal_config_state.get("logicAnd", False)),
        "symbols": {
            symbol: dict(config)
            for symbol, config in (abnormal_config_state.get("symbols") or {}).items()
        },
    }


def _abnormal_snapshot_digest(snapshot: Dict[str, Any]) -> str:
    payload = json.dumps(snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha1(payload).hexdigest()


def _build_abnormal_snapshot_locked() -> Dict[str, Any]:
    config_snapshot = _copy_abnormal_config_locked()
    return {
        "abnormalMeta": {
            "updatedAt": _now_ms(),
            "logicAnd": bool(config_snapshot.get("logicAnd", False)),
            "recordCount": len(abnormal_record_store),
            "alertCount": len(abnormal_alert_store),
        },
        "configs": [
            dict(config_snapshot["symbols"][symbol])
            for symbol in ABNORMAL_SYMBOLS
            if symbol in config_snapshot["symbols"]
        ],
        "records": [dict(item) for item in abnormal_record_store],
        "alerts": [dict(item) for item in abnormal_alert_store],
    }


def _commit_abnormal_snapshot_locked() -> None:
    snapshot = _build_abnormal_snapshot_locked()
    digest = _abnormal_snapshot_digest(snapshot)
    state = abnormal_sync_state
    if not state:
        abnormal_sync_state.update({
            "seq": 1,
            "digest": digest,
            "snapshot": snapshot,
            "previousSeq": 0,
            "previousSnapshot": None,
        })
        return
    if state.get("digest") == digest:
        abnormal_sync_state["snapshot"] = snapshot
        return
    previous_seq = int(state.get("seq", 0))
    previous_snapshot = state.get("snapshot")
    abnormal_sync_state.update({
        "seq": previous_seq + 1,
        "digest": digest,
        "snapshot": snapshot,
        "previousSeq": previous_seq,
        "previousSnapshot": previous_snapshot,
    })


def _build_full_abnormal_response(snapshot: Dict[str, Any], sync_seq: int) -> Dict[str, Any]:
    meta = dict(snapshot.get("abnormalMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = ABNORMAL_DELTA_ENABLED
    return {
        "abnormalMeta": meta,
        "configs": snapshot.get("configs") or [],
        "records": snapshot.get("records") or [],
        "alerts": snapshot.get("alerts") or [],
        "isDelta": False,
        "unchanged": False,
    }


def _diff_abnormal_items(previous: List[Dict[str, Any]], current: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    previous_ids = {str(item.get("id", "")) for item in (previous or [])}
    return [dict(item) for item in (current or []) if str(item.get("id", "")) not in previous_ids]


def _parse_recent_closed_kline(row: List[Any], symbol: str, now_ms: int) -> Optional[Dict[str, Any]]:
    if row is None or len(row) < 8:
        return None
    try:
        open_time = int(row[0])
        close_time = int(row[6])
        if close_time <= 0 or close_time >= now_ms:
            return None
        open_price = float(row[1])
        close_price = float(row[4])
        volume = float(row[5])
        amount = float(row[7])
    except Exception:
        return None
    price_change = abs(close_price - open_price)
    percent_change = abs(_safe_div(close_price - open_price, open_price) * 100.0)
    return {
        "symbol": _normalize_abnormal_symbol(symbol),
        "openTime": open_time,
        "closeTime": close_time,
        "openPrice": open_price,
        "closePrice": close_price,
        "volume": volume,
        "amount": amount,
        "priceChange": price_change,
        "percentChange": percent_change,
    }


def _fetch_recent_closed_binance_klines(symbol: str, limit: int = ABNORMAL_KLINE_LIMIT) -> List[Dict[str, Any]]:
    normalized_symbol = _normalize_abnormal_symbol(symbol)
    now_ms = _now_ms()
    with abnormal_state_lock:
        cached = abnormal_kline_cache.get(normalized_symbol)
        if cached and now_ms - int(cached.get("fetchedAt", 0) or 0) <= ABNORMAL_FETCH_CACHE_MS:
            return [dict(item) for item in (cached.get("items") or [])]

    upstream_url = _build_binance_rest_upstream_url(
        "/fapi/v1/klines",
        {"symbol": normalized_symbol, "interval": "1m", "limit": max(2, limit)},
    )
    request = urllib.request.Request(
        upstream_url,
        headers={"User-Agent": "mt5-gateway-abnormal-monitor/1.0"},
        method="GET",
    )
    with urllib.request.urlopen(request, timeout=12) as response:
        body = response.read().decode("utf-8")
    rows = json.loads(body or "[]")
    items: List[Dict[str, Any]] = []
    for row in rows:
        item = _parse_recent_closed_kline(row, normalized_symbol, now_ms)
        if item is not None:
            items.append(item)
    items.sort(key=lambda current: int(current.get("closeTime", 0) or 0))
    with abnormal_state_lock:
        abnormal_kline_cache[normalized_symbol] = {
            "fetchedAt": now_ms,
            "items": [dict(item) for item in items],
        }
    return items


def _evaluate_abnormal_kline(kline: Dict[str, Any], config: Dict[str, Any], use_and_mode: bool) -> Dict[str, Any]:
    enabled_count = 0
    triggered: List[str] = []
    if config.get("volumeEnabled", False):
        enabled_count += 1
        if float(kline.get("volume", 0.0) or 0.0) >= float(config.get("volumeThreshold", 0.0) or 0.0):
            triggered.append("成交量")
    if config.get("amountEnabled", False):
        enabled_count += 1
        if float(kline.get("amount", 0.0) or 0.0) >= float(config.get("amountThreshold", 0.0) or 0.0):
            triggered.append("成交额")
    if config.get("priceChangeEnabled", False):
        enabled_count += 1
        if float(kline.get("priceChange", 0.0) or 0.0) >= float(config.get("priceChangeThreshold", 0.0) or 0.0):
            triggered.append("价格变化")
    if enabled_count == 0:
        return {"participating": False, "abnormal": False, "summary": ""}
    abnormal = len(triggered) == enabled_count if use_and_mode else bool(triggered)
    return {"participating": True, "abnormal": abnormal, "summary": " / ".join(triggered)}


def _build_abnormal_record(symbol: str, kline: Dict[str, Any], summary: str) -> Dict[str, Any]:
    normalized_symbol = _normalize_abnormal_symbol(symbol)
    close_time = int(kline.get("closeTime", 0) or 0)
    record_id = hashlib.sha1(f"{normalized_symbol}:{close_time}:{summary}".encode("utf-8")).hexdigest()
    return {
        "id": record_id,
        "symbol": normalized_symbol,
        "timestamp": _now_ms(),
        "closeTime": close_time,
        "openPrice": float(kline.get("openPrice", 0.0) or 0.0),
        "closePrice": float(kline.get("closePrice", 0.0) or 0.0),
        "volume": float(kline.get("volume", 0.0) or 0.0),
        "amount": float(kline.get("amount", 0.0) or 0.0),
        "priceChange": float(kline.get("priceChange", 0.0) or 0.0),
        "percentChange": float(kline.get("percentChange", 0.0) or 0.0),
        "triggerSummary": summary or "",
    }


def _compose_abnormal_alert_line(asset: str, trigger_summary: str) -> str:
    return f"{asset} 的 {trigger_summary} 出现异常！"


def _is_abnormal_alert_eligible_locked(record: Optional[Dict[str, Any]], now_ms: int) -> bool:
    if not record:
        return False
    symbol = _normalize_abnormal_symbol(record.get("symbol"))
    last_notify = int(abnormal_last_notify_at.get(symbol, 0) or 0)
    return now_ms - last_notify >= 5 * 60 * 1000


def _build_abnormal_alert(symbols: List[str], close_time: int, content: str) -> Dict[str, Any]:
    normalized_symbols = sorted({_normalize_abnormal_symbol(symbol) for symbol in (symbols or []) if symbol})
    alert_key = ",".join(normalized_symbols)
    alert_id = hashlib.sha1(f"{alert_key}:{close_time}:{content}".encode("utf-8")).hexdigest()
    return {
        "id": alert_id,
        "symbols": normalized_symbols,
        "title": "异常提醒",
        "content": content,
        "closeTime": int(close_time or 0),
        "createdAt": _now_ms(),
    }


def _build_abnormal_alerts_locked(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    grouped: Dict[int, Dict[str, Dict[str, Any]]] = {}
    for record in records or []:
        close_time = int(record.get("closeTime", 0) or 0)
        if close_time <= 0:
            continue
        grouped.setdefault(close_time, {})[_normalize_abnormal_symbol(record.get("symbol"))] = record

    created: List[Dict[str, Any]] = []
    for close_time in sorted(grouped.keys()):
        current_group = grouped.get(close_time) or {}
        now_ms = _now_ms()
        btc_record = current_group.get("BTCUSDT")
        xau_record = current_group.get("XAUUSD")
        btc_eligible = _is_abnormal_alert_eligible_locked(btc_record, now_ms)
        xau_eligible = _is_abnormal_alert_eligible_locked(xau_record, now_ms)
        if btc_eligible and xau_eligible:
            content = _compose_abnormal_alert_line("BTC", str(btc_record.get("triggerSummary", "")))
            content += "\n" + _compose_abnormal_alert_line("XAU", str(xau_record.get("triggerSummary", "")))
            created.append(_build_abnormal_alert(["BTCUSDT", "XAUUSD"], close_time, content))
            abnormal_last_notify_at["BTCUSDT"] = now_ms
            abnormal_last_notify_at["XAUUSD"] = now_ms
            continue
        if btc_eligible and btc_record:
            created.append(_build_abnormal_alert(
                ["BTCUSDT"],
                close_time,
                _compose_abnormal_alert_line("BTC", str(btc_record.get("triggerSummary", ""))),
            ))
            abnormal_last_notify_at["BTCUSDT"] = now_ms
        if xau_eligible and xau_record:
            created.append(_build_abnormal_alert(
                ["XAUUSD"],
                close_time,
                _compose_abnormal_alert_line("XAU", str(xau_record.get("triggerSummary", ""))),
            ))
            abnormal_last_notify_at["XAUUSD"] = now_ms
    return created


def _append_abnormal_updates_locked(records: List[Dict[str, Any]]) -> None:
    existing_record_ids = {str(item.get("id", "")) for item in abnormal_record_store}
    appended_records: List[Dict[str, Any]] = []
    for record in sorted(records or [], key=lambda item: (int(item.get("closeTime", 0) or 0), str(item.get("symbol", "")))):
        record_id = str(record.get("id", ""))
        if not record_id or record_id in existing_record_ids:
            continue
        abnormal_record_store.insert(0, dict(record))
        existing_record_ids.add(record_id)
        appended_records.append(dict(record))
    if appended_records:
        del abnormal_record_store[ABNORMAL_RECORD_LIMIT:]

    existing_alert_ids = {str(item.get("id", "")) for item in abnormal_alert_store}
    for alert in _build_abnormal_alerts_locked(appended_records):
        alert_id = str(alert.get("id", ""))
        if not alert_id or alert_id in existing_alert_ids:
            continue
        abnormal_alert_store.insert(0, dict(alert))
        existing_alert_ids.add(alert_id)
    if abnormal_alert_store:
        del abnormal_alert_store[ABNORMAL_ALERT_LIMIT:]

    _commit_abnormal_snapshot_locked()


def _set_abnormal_config(payload: Dict[str, Any]) -> Dict[str, Any]:
    with abnormal_state_lock:
        _ensure_abnormal_defaults_locked()
        current = _copy_abnormal_config_locked()
        normalized = {
            "logicAnd": _abnormal_bool((payload or {}).get("logicAnd"), current.get("logicAnd", False)),
            "symbols": {symbol: _default_abnormal_symbol_config(symbol) for symbol in ABNORMAL_SYMBOLS},
        }
        raw_configs = (payload or {}).get("configs")
        if isinstance(raw_configs, list):
            for item in raw_configs:
                if not isinstance(item, dict):
                    continue
                symbol = _normalize_abnormal_symbol(item.get("symbol"))
                if symbol not in normalized["symbols"]:
                    continue
                normalized["symbols"][symbol] = _sanitize_abnormal_symbol_config(symbol, item)
        changed = normalized != current
        if changed:
            abnormal_config_state["logicAnd"] = normalized["logicAnd"]
            abnormal_config_state["symbols"] = normalized["symbols"]
            _commit_abnormal_snapshot_locked()
        return {
            "ok": True,
            "changed": changed,
            "config": {
                "logicAnd": normalized["logicAnd"],
                "configs": [dict(normalized["symbols"][symbol]) for symbol in ABNORMAL_SYMBOLS],
            },
        }


def _refresh_abnormal_state() -> None:
    with abnormal_state_lock:
        _ensure_abnormal_defaults_locked()
        config_snapshot = _copy_abnormal_config_locked()
        last_close_by_symbol = dict(abnormal_last_close_time_by_symbol)

    new_records: List[Dict[str, Any]] = []
    for symbol in ABNORMAL_SYMBOLS:
        recent_klines = _fetch_recent_closed_binance_klines(symbol, ABNORMAL_KLINE_LIMIT)
        symbol_config = (config_snapshot.get("symbols") or {}).get(symbol) or _default_abnormal_symbol_config(symbol)
        last_close_time = int(last_close_by_symbol.get(symbol, 0) or 0)
        for kline in recent_klines:
            close_time = int(kline.get("closeTime", 0) or 0)
            if close_time <= last_close_time:
                continue
            evaluation = _evaluate_abnormal_kline(kline, symbol_config, bool(config_snapshot.get("logicAnd", False)))
            if evaluation.get("abnormal"):
                new_records.append(_build_abnormal_record(symbol, kline, str(evaluation.get("summary", ""))))
            last_close_time = max(last_close_time, close_time)
        if last_close_time > 0:
            last_close_by_symbol[symbol] = last_close_time

    with abnormal_state_lock:
        for symbol, close_time in last_close_by_symbol.items():
            abnormal_last_close_time_by_symbol[symbol] = max(
                int(abnormal_last_close_time_by_symbol.get(symbol, 0) or 0),
                int(close_time or 0),
            )
        if new_records or not abnormal_sync_state:
            _append_abnormal_updates_locked(new_records)


def _build_abnormal_response(since_seq: int, delta: bool) -> Dict[str, Any]:
    _refresh_abnormal_state()
    with abnormal_state_lock:
        _ensure_abnormal_defaults_locked()
        if not abnormal_sync_state:
            _commit_abnormal_snapshot_locked()
        snapshot = abnormal_sync_state.get("snapshot") or _build_abnormal_snapshot_locked()
        sync_seq = int(abnormal_sync_state.get("seq", 1) or 1)
        previous_seq = int(abnormal_sync_state.get("previousSeq", 0) or 0)
        previous_snapshot = abnormal_sync_state.get("previousSnapshot")

    if not ABNORMAL_DELTA_ENABLED or not delta or since_seq <= 0:
        return _build_full_abnormal_response(snapshot, sync_seq)

    meta = dict(snapshot.get("abnormalMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = ABNORMAL_DELTA_ENABLED

    if since_seq == sync_seq:
        return {"abnormalMeta": meta, "isDelta": True, "unchanged": True}

    if previous_snapshot is not None and since_seq == previous_seq:
        return {
            "abnormalMeta": meta,
            "isDelta": True,
            "unchanged": False,
            "delta": {
                "records": _diff_abnormal_items(previous_snapshot.get("records") or [], snapshot.get("records") or []),
                "alerts": _diff_abnormal_items(previous_snapshot.get("alerts") or [], snapshot.get("alerts") or []),
            },
        }

    return _build_full_abnormal_response(snapshot, sync_seq)


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
        "all": 24 * SNAPSHOT_RANGE_ALL_DAYS,
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


def _build_curve(range_key: str, current_positions: Optional[List[Dict]] = None) -> List[Dict]:
    now = datetime.now(timezone.utc)
    from_time = now - timedelta(hours=_range_hours(range_key))
    deals = mt5.history_deals_get(from_time, now) or []
    account = mt5.account_info()
    if account is None:
        return []

    current_balance = float(getattr(account, "balance", 0.0))
    current_equity = float(getattr(account, "equity", 0.0))
    realized = 0.0
    deal_history: List[Dict[str, Any]] = []
    for deal in deals:
        profit = float(getattr(deal, "profit", 0.0))
        commission = float(getattr(deal, "commission", 0.0))
        swap = float(getattr(deal, "swap", 0.0))
        realized += profit + commission + swap
        deal_history.append({
            "timestamp": _deal_time_ms(deal),
            "price": float(getattr(deal, "price", 0.0)),
            "profit": profit,
            "commission": commission,
            "swap": swap,
            "entry": int(getattr(deal, "entry", 0)),
            "deal_type": int(getattr(deal, "type", -1)),
            "volume": abs(float(getattr(deal, "volume", 0.0))),
            "symbol": str(getattr(deal, "symbol", "") or ""),
            "position_id": int(getattr(deal, "position_id", 0)),
        })

    start_balance = current_balance - realized
    positions = current_positions or _map_positions()
    contract_cache: Dict[str, float] = {}
    contract_size_fn = lambda symbol: _contract_size_for_symbol(symbol, contract_cache)
    leverage = float(getattr(mt5.account_info(), "leverage", 0.0) or 0.0) if mt5 is not None else 0.0
    return _replay_curve_from_history(
        deal_history=deal_history,
        start_balance=start_balance,
        open_positions=positions,
        current_balance=current_balance,
        current_equity=current_equity,
        leverage=leverage,
        contract_size_fn=contract_size_fn,
        now_ms=_now_ms(),
    )


def _contract_size_for_symbol(symbol: str, cache: Dict[str, float]) -> float:
    if not symbol:
        return 1.0
    cached = cache.get(symbol)
    if cached is not None:
        return cached
    if mt5 is None:
        cache[symbol] = 1.0
        return 1.0
    size = 1.0
    sinfo = mt5.symbol_info(symbol)
    if sinfo is not None and float(getattr(sinfo, "trade_contract_size", 0.0) or 0.0) > 0:
        size = float(getattr(sinfo, "trade_contract_size", 1.0))
    cache[symbol] = size
    return size


def _curve_point(timestamp: int, equity: float, balance: float, position_ratio: float = 0.0) -> Dict[str, float]:
    safe_ratio = float(position_ratio or 0.0)
    if not math.isfinite(safe_ratio) or safe_ratio < 0.0:
        safe_ratio = 0.0
    return {
        "timestamp": int(timestamp),
        "equity": float(equity),
        "balance": float(balance),
        "positionRatio": safe_ratio,
    }


def _add_curve_exposure(
    exposures: Dict[Tuple[str, str], Dict[str, float]],
    symbol: str,
    side: str,
    volume: float,
    price: float,
    contract_size: float,
) -> None:
    if not symbol or volume <= 0.0 or price <= 0.0 or contract_size <= 0.0:
        return
    key = (symbol, side)
    state = exposures.setdefault(key, {"volume": 0.0, "open_notional": 0.0, "contract_size": contract_size})
    state["volume"] += volume
    state["open_notional"] += volume * price * contract_size
    state["contract_size"] = contract_size


def _remove_curve_exposure(
    exposures: Dict[Tuple[str, str], Dict[str, float]],
    symbol: str,
    side: str,
    volume: float,
) -> None:
    if not symbol or volume <= 0.0:
        return
    key = (symbol, side)
    state = exposures.get(key)
    if not state:
        return
    contract_size = state.get("contract_size", 1.0)
    current_volume = state.get("volume", 0.0)
    if current_volume <= 0.0:
        exposures.pop(key, None)
        return
    remove_volume = min(volume, current_volume)
    avg_open_price = state.get("open_notional", 0.0) / max(current_volume * contract_size, 1e-9)
    reduction = avg_open_price * remove_volume * contract_size
    state["volume"] = max(0.0, current_volume - remove_volume)
    state["open_notional"] = max(0.0, state.get("open_notional", 0.0) - reduction)
    if state["volume"] <= 1e-9:
        exposures.pop(key, None)


def _calculate_curve_floating(
    exposures: Dict[Tuple[str, str], Dict[str, float]],
    last_price_by_symbol: Dict[str, float],
) -> float:
    # 计算每个持仓按照最新价格的浮动盈亏，缺少价格时就跳过
    total = 0.0
    for (symbol, side), state in exposures.items():
        volume = state.get("volume", 0.0)
        contract_size = state.get("contract_size", 1.0)
        if volume <= 0.0 or contract_size <= 0.0:
            continue
        price = last_price_by_symbol.get(symbol, 0.0)
        if price <= 0.0:
            continue
        avg_price = state.get("open_notional", 0.0) / max(volume * contract_size, 1e-9)
        direction = 1.0 if side == "Buy" else -1.0
        total += (price - avg_price) * direction * volume * contract_size
    return total


def _calculate_curve_market_value(
    exposures: Dict[Tuple[str, str], Dict[str, float]],
    last_price_by_symbol: Dict[str, float],
) -> float:
    total = 0.0
    for (symbol, _side), state in exposures.items():
        volume = float(state.get("volume", 0.0) or 0.0)
        contract_size = float(state.get("contract_size", 1.0) or 1.0)
        if volume <= 0.0 or contract_size <= 0.0:
            continue
        price = float(last_price_by_symbol.get(symbol, 0.0) or 0.0)
        if price <= 0.0:
            continue
        total += abs(volume * price * contract_size)
    return total


def _resolve_effective_leverage(leverage: float) -> float:
    return max(1.0, float(leverage or 0.0))


def _curve_position_ratio_from_margin(margin: float, equity: float) -> float:
    ratio = _safe_div(max(0.0, float(margin or 0.0)), max(1.0, float(equity or 0.0)))
    if not math.isfinite(ratio) or ratio < 0.0:
        return 0.0
    return ratio


def _calculate_curve_position_ratio(
    exposures: Dict[Tuple[str, str], Dict[str, float]],
    last_price_by_symbol: Dict[str, float],
    equity: float,
    leverage: float,
) -> float:
    market_value = _calculate_curve_market_value(exposures, last_price_by_symbol)
    margin = _safe_div(market_value, _resolve_effective_leverage(leverage))
    return _curve_position_ratio_from_margin(margin, equity)


def _resolve_positions_market_value(positions: List[Dict[str, Any]]) -> float:
    total = 0.0
    for position in positions or []:
        total += max(0.0, float(position.get("marketValue", 0.0) or 0.0))
    return total


def _inject_positions_into_exposures(
    exposures: Dict[Tuple[str, str], Dict[str, float]],
    positions: List[Dict[str, Any]],
    open_position_ids: set,
    contract_size_fn,
    last_price_by_symbol: Dict[str, float],
) -> None:
    # 把当前未平仓持仓注入到曝光映射里，避免与本窗口内开仓的重复叠加
    for position in positions or []:
        ticket = int(position.get("positionTicket", 0))
        if ticket > 0 and ticket in open_position_ids:
            continue
        symbol = str(position.get("code") or position.get("productName") or "").strip()
        if not symbol:
            continue
        side = str(position.get("side") or "Buy")
        volume = float(position.get("quantity", 0.0))
        if volume <= 0.0:
            continue
        open_price = float(position.get("costPrice", 0.0))
        contract_size = contract_size_fn(symbol)
        latest_price = float(position.get("latestPrice", 0.0))
        if open_price <= 0.0 and latest_price > 0.0:
            open_price = latest_price
        _add_curve_exposure(exposures, symbol, side, volume, open_price, contract_size)
        if latest_price > 0.0:
            last_price_by_symbol.setdefault(symbol, latest_price)


def _replay_curve_from_history(
    deal_history: List[Dict[str, Any]],
    start_balance: float,
    open_positions: List[Dict[str, Any]],
    current_balance: float,
    current_equity: float,
    leverage: float,
    contract_size_fn,
    now_ms: int,
) -> List[Dict[str, float]]:
    # 以时间序列方式重放成交和持仓，生成 equity/balance 分离的曲线点
    if not deal_history:
        market_value = _resolve_positions_market_value(open_positions)
        return [_curve_point(
            now_ms,
            current_equity,
            current_balance,
            _curve_position_ratio_from_margin(
                _safe_div(market_value, _resolve_effective_leverage(leverage)),
                current_equity,
            ),
        )]

    exposures: Dict[Tuple[str, str], Dict[str, float]] = {}
    last_price_by_symbol: Dict[str, float] = {}
    open_position_ids = {
        int(deal.get("position_id", 0)) for deal in deal_history if _is_entry_open(int(deal.get("entry", 0)))
    }
    _inject_positions_into_exposures(exposures, open_positions, open_position_ids, contract_size_fn, last_price_by_symbol)

    sorted_deals = sorted(deal_history, key=lambda item: int(item.get("timestamp", 0)))
    running_balance = float(start_balance)
    points: List[Dict[str, float]] = []
    first_ts = max(int(sorted_deals[0].get("timestamp", 0)), 0)
    floating = _calculate_curve_floating(exposures, last_price_by_symbol)
    first_equity = running_balance + floating
    points.append(_curve_point(
        first_ts,
        first_equity,
        running_balance,
        _calculate_curve_position_ratio(exposures, last_price_by_symbol, first_equity, leverage),
    ))

    for deal in sorted_deals:
        timestamp = int(deal.get("timestamp", 0))
        symbol = str(deal.get("symbol", "") or "")
        price = float(deal.get("price", 0.0))
        if symbol and price > 0.0:
            last_price_by_symbol[symbol] = price

        entry = int(deal.get("entry", 0))
        deal_type = int(deal.get("deal_type", -1))
        volume = abs(float(deal.get("volume", 0.0)))
        direction = "Buy" if _is_buy_deal_type(deal_type) else "Sell"

        if _is_entry_close(entry):
            _remove_curve_exposure(exposures, symbol, direction, volume)
        if _is_entry_open(entry):
            contract_size = contract_size_fn(symbol)
            _add_curve_exposure(exposures, symbol, direction, volume, price, contract_size)

        running_balance += (
            float(deal.get("profit", 0.0))
            + float(deal.get("commission", 0.0))
            + float(deal.get("swap", 0.0))
        )
        floating = _calculate_curve_floating(exposures, last_price_by_symbol)
        equity = running_balance + floating
        points.append(_curve_point(
            timestamp,
            equity,
            running_balance,
            _calculate_curve_position_ratio(exposures, last_price_by_symbol, equity, leverage),
        ))

    current_market_value = _resolve_positions_market_value(open_positions)
    if current_market_value <= 0.0:
        current_market_value = _calculate_curve_market_value(exposures, last_price_by_symbol)
    points.append(_curve_point(
        now_ms,
        current_equity,
        current_balance,
        _curve_position_ratio_from_margin(
            _safe_div(current_market_value, _resolve_effective_leverage(leverage)),
            current_equity,
        ),
    ))
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
    open_batches: Dict[str, List[Dict[str, Any]]] = {}
    contract_size_cache: Dict[str, float] = {}
    volume_epsilon = 1e-9

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

    def lifecycle_key(position_id: int, order_id: int, ticket: int) -> str:
        if position_id > 0:
            return f"position:{position_id}"
        if order_id > 0:
            return f"order:{order_id}"
        return f"ticket:{ticket}"

    def infer_original_side(deal_type: int) -> str:
        return "Buy" if deal_type == 1 else "Sell"

    def append_open_batch(key: str,
                          side: str,
                          open_time_ms: int,
                          open_price: float,
                          volume: float) -> None:
        if volume <= volume_epsilon:
            return
        queue = open_batches.setdefault(key, [])
        queue.append({
            "side": side,
            "open_time": int(open_time_ms),
            "open_price": float(open_price),
            "remaining_volume": float(volume),
        })

    def consume_open_batches(key: str, close_volume: float) -> Tuple[List[Tuple[Dict[str, Any], float]], float]:
        queue = open_batches.setdefault(key, [])
        matches: List[Tuple[Dict[str, Any], float]] = []
        remaining = float(close_volume)
        while remaining > volume_epsilon and queue:
            batch = queue[0]
            matched = min(remaining, float(batch.get("remaining_volume", 0.0)))
            if matched <= volume_epsilon:
                queue.pop(0)
                continue
            matches.append((dict(batch), matched))
            batch["remaining_volume"] = max(0.0, float(batch.get("remaining_volume", 0.0)) - matched)
            remaining -= matched
            if float(batch.get("remaining_volume", 0.0)) <= volume_epsilon:
                queue.pop(0)
        return matches, max(0.0, remaining)

    def resolve_split_ticket(ticket: int, split_count: int, split_index: int) -> int:
        if split_count <= 1 or ticket <= 0:
            return ticket
        return ticket * 1000 + split_index

    def append_close_record(symbol: str,
                            ticket: int,
                            order_id: int,
                            position_id: int,
                            entry_type: int,
                            close_time: int,
                            close_price: float,
                            close_volume: float,
                            total_volume: float,
                            total_profit: float,
                            total_commission: float,
                            total_swap: float,
                            open_time: int,
                            open_price: float,
                            side: str,
                            split_count: int,
                            split_index: int,
                            remark: str) -> None:
        if close_volume <= volume_epsilon or total_volume <= volume_epsilon:
            return
        ratio = close_volume / total_volume
        storage_fee = (total_commission + total_swap) * ratio
        amount = abs(close_volume * close_price * contract_size_of(symbol))
        mapped.append(
            {
                "timestamp": close_time,
                "productName": symbol,
                "code": symbol,
                "side": side,
                "price": close_price,
                "quantity": close_volume,
                "amount": amount,
                "fee": storage_fee,
                "profit": total_profit * ratio,
                "openTime": open_time,
                "closeTime": close_time,
                "openPrice": open_price if open_price > 0.0 else close_price,
                "closePrice": close_price if close_price > 0.0 else open_price,
                "storageFee": storage_fee,
                "dealTicket": resolve_split_ticket(ticket, split_count, split_index),
                "orderId": order_id,
                "positionId": position_id,
                "entryType": entry_type,
                "remark": remark or "",
            }
        )

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
        key = lifecycle_key(position_id, order_id, ticket)
        entry_type = int(getattr(deal, "entry", 0))
        ts = _deal_time_ms(deal)
        price = float(getattr(deal, "price", 0.0))
        profit = float(getattr(deal, "profit", 0.0))
        commission = float(getattr(deal, "commission", 0.0))
        swap = float(getattr(deal, "swap", 0.0))
        current_side = "Buy" if _is_buy_deal_type(deal_type) else "Sell"
        comment = getattr(deal, "comment", "") or ""

        if _is_entry_close(entry_type):
            matches, remaining_after_close = consume_open_batches(key, volume)
            if entry_type == 2 and matches:
                synthetic_close_volume = 0.0
                reverse_open_volume = remaining_after_close
            elif entry_type == 2:
                synthetic_close_volume = volume
                reverse_open_volume = 0.0
            else:
                synthetic_close_volume = remaining_after_close
                reverse_open_volume = 0.0
            split_count = len(matches) + (1 if synthetic_close_volume > volume_epsilon else 0)
            split_index = 0
            for batch, matched_volume in matches:
                split_index += 1
                append_close_record(
                    symbol=symbol,
                    ticket=ticket,
                    order_id=order_id,
                    position_id=position_id,
                    entry_type=entry_type,
                    close_time=ts,
                    close_price=price,
                    close_volume=matched_volume,
                    total_volume=volume,
                    total_profit=profit,
                    total_commission=commission,
                    total_swap=swap,
                    open_time=int(batch.get("open_time", ts)),
                    open_price=float(batch.get("open_price", price)),
                    side=str(batch.get("side", infer_original_side(deal_type))),
                    split_count=split_count,
                    split_index=split_index,
                    remark=comment,
                )
            if synthetic_close_volume > volume_epsilon:
                split_index += 1
                append_close_record(
                    symbol=symbol,
                    ticket=ticket,
                    order_id=order_id,
                    position_id=position_id,
                    entry_type=entry_type,
                    close_time=ts,
                    close_price=price,
                    close_volume=synthetic_close_volume,
                    total_volume=volume,
                    total_profit=profit,
                    total_commission=commission,
                    total_swap=swap,
                    open_time=ts,
                    open_price=price,
                    side=infer_original_side(deal_type),
                    split_count=split_count,
                    split_index=split_index,
                    remark=comment,
                )
        else:
            reverse_open_volume = 0.0

        if _is_entry_open(entry_type):
            open_volume = volume if entry_type != 2 else reverse_open_volume
            append_open_batch(
                key=key,
                side=current_side,
                open_time_ms=ts,
                open_price=price,
                volume=open_volume,
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
            positions = _map_positions()
            points = _build_curve(range_key, positions)
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
            "positionRatio": round(float(item.get("positionRatio", 0.0) or 0.0), 6),
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


def _build_summary_response(snapshot: Dict, sync_seq: int) -> Dict:
    meta = dict(snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED
    return {
        "accountMeta": meta,
        "isDelta": False,
        "unchanged": False,
        "overviewMetrics": snapshot.get("overviewMetrics") or [],
        "statsMetrics": snapshot.get("statsMetrics") or [],
    }


def _build_live_response(snapshot: Dict, sync_seq: int) -> Dict:
    meta = dict(snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED
    return {
        "accountMeta": meta,
        "isDelta": False,
        "unchanged": False,
        "overviewMetrics": snapshot.get("overviewMetrics") or [],
        "positions": snapshot.get("positions") or [],
        "statsMetrics": snapshot.get("statsMetrics") or [],
    }


def _build_pending_response(snapshot: Dict, sync_seq: int) -> Dict:
    meta = dict(snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED
    return {
        "accountMeta": meta,
        "isDelta": False,
        "unchanged": False,
        "pendingOrders": snapshot.get("pendingOrders") or [],
    }


def _build_trades_response(snapshot: Dict, sync_seq: int) -> Dict:
    meta = dict(snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED
    return {
        "accountMeta": meta,
        "isDelta": False,
        "unchanged": False,
        "trades": snapshot.get("trades") or [],
    }


def _build_curve_response(snapshot: Dict, sync_seq: int) -> Dict:
    meta = dict(snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED
    return {
        "accountMeta": meta,
        "isDelta": False,
        "unchanged": False,
        "curvePoints": snapshot.get("curvePoints") or [],
        "curveIndicators": snapshot.get("curveIndicators") or [],
    }


def _build_snapshot_with_cache(range_key: str) -> Dict:
    now_ms = _now_ms()
    with snapshot_cache_lock:
        cached = snapshot_build_cache.get(range_key)
        if cached and (now_ms - int(cached.get("builtAt", 0))) <= SNAPSHOT_BUILD_CACHE_MS:
            cached["lastAccessAt"] = now_ms
            _remember_cache_entry_locked(snapshot_build_cache, range_key, cached, SNAPSHOT_BUILD_CACHE_MAX_ENTRIES)
            return cached.get("snapshot")
        if _should_slide_snapshot_build_cache(cached, now_ms):
            cached["builtAt"] = now_ms
            cached["lastAccessAt"] = now_ms
            _remember_cache_entry_locked(snapshot_build_cache, range_key, cached, SNAPSHOT_BUILD_CACHE_MAX_ENTRIES)
            return cached.get("snapshot")

    snapshot = _normalize_snapshot(_select_snapshot(range_key), "MT5 Gateway")
    with snapshot_cache_lock:
        _remember_cache_entry_locked(snapshot_build_cache, range_key, {
            "builtAt": now_ms,
            "lastAccessAt": now_ms,
            "snapshot": snapshot,
        }, SNAPSHOT_BUILD_CACHE_MAX_ENTRIES)
    return snapshot


def _build_summary_snapshot(snapshot: Dict) -> Dict:
    summary = {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "overviewMetrics": snapshot.get("overviewMetrics") or [],
        "statsMetrics": snapshot.get("statsMetrics") or [],
    }
    return _normalize_snapshot(summary, str((snapshot.get("accountMeta") or {}).get("source", "MT5 Gateway")))


def _build_live_snapshot(snapshot: Dict) -> Dict:
    live = {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "overviewMetrics": snapshot.get("overviewMetrics") or [],
        "positions": snapshot.get("positions") or [],
        "statsMetrics": snapshot.get("statsMetrics") or [],
    }
    return _normalize_snapshot(live, str((snapshot.get("accountMeta") or {}).get("source", "MT5 Gateway")))


def _build_pending_snapshot(snapshot: Dict) -> Dict:
    pending = {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "pendingOrders": snapshot.get("pendingOrders") or [],
    }
    return _normalize_snapshot(pending, str((snapshot.get("accountMeta") or {}).get("source", "MT5 Gateway")))


def _build_trades_snapshot(snapshot: Dict) -> Dict:
    trades = {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "trades": snapshot.get("trades") or [],
    }
    return _normalize_snapshot(trades, str((snapshot.get("accountMeta") or {}).get("source", "MT5 Gateway")))


def _build_curve_snapshot(snapshot: Dict) -> Dict:
    curve = {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "curvePoints": snapshot.get("curvePoints") or [],
        "curveIndicators": snapshot.get("curveIndicators") or [],
    }
    return _normalize_snapshot(curve, str((snapshot.get("accountMeta") or {}).get("source", "MT5 Gateway")))


def _projection_profile(name: str) -> Dict[str, Any]:
    profiles = {
        "snapshot": {
            "cacheSuffix": "snapshot",
            "project": lambda snapshot: snapshot,
            "buildResponse": _build_full_response,
            "deltaKeys": ["positions", "pendingOrders", "trades", "curvePoints"],
            "carryKeys": ["overviewMetrics", "curveIndicators", "statsMetrics"],
        },
        "summary": {
            "cacheSuffix": "summary",
            "project": _build_summary_snapshot,
            "buildResponse": _build_summary_response,
            "deltaKeys": [],
            "carryKeys": [],
        },
        "live": {
            "cacheSuffix": "live",
            "project": _build_live_snapshot,
            "buildResponse": _build_live_response,
            "deltaKeys": ["positions"],
            "carryKeys": ["overviewMetrics", "statsMetrics"],
        },
        "pending": {
            "cacheSuffix": "pending",
            "project": _build_pending_snapshot,
            "buildResponse": _build_pending_response,
            "deltaKeys": ["pendingOrders"],
            "carryKeys": [],
        },
        "trades": {
            "cacheSuffix": "trades",
            "project": _build_trades_snapshot,
            "buildResponse": _build_trades_response,
            "deltaKeys": ["trades"],
            "carryKeys": [],
        },
        "curve": {
            "cacheSuffix": "curve",
            "project": _build_curve_snapshot,
            "buildResponse": _build_curve_response,
            "deltaKeys": ["curvePoints"],
            "carryKeys": ["curveIndicators"],
        },
    }
    return profiles[name]


def _build_scoped_delta_snapshot(previous: Dict, current: Dict, keys: List[str]) -> Dict:
    payload: Dict[str, Any] = {}
    if "positions" in keys:
        payload["positions"] = _diff_entities(previous.get("positions") or [], current.get("positions") or [], _position_key)
    if "pendingOrders" in keys:
        payload["pendingOrders"] = _diff_entities(previous.get("pendingOrders") or [], current.get("pendingOrders") or [], _pending_key)
    if "trades" in keys:
        payload["trades"] = _diff_entities(previous.get("trades") or [], current.get("trades") or [], _trade_key)
    if "curvePoints" in keys:
        payload["curvePoints"] = _diff_curve_points(previous.get("curvePoints") or [], current.get("curvePoints") or [])
    return payload


def _build_projected_snapshot_response(range_key: str, since_seq: int, delta: bool, projection_name: str) -> Dict:
    profile = _projection_profile(projection_name)
    snapshot = _build_snapshot_with_cache(range_key)
    projected_snapshot = profile["project"](snapshot)
    digest = _snapshot_digest(projected_snapshot)
    cache_key = f"{range_key}:{profile['cacheSuffix']}"

    with snapshot_cache_lock:
        state = snapshot_sync_cache.get(cache_key)
        previous_snapshot: Optional[Dict] = None
        previous_seq = 0

        if state is None:
            sync_seq = 1
            _remember_cache_entry_locked(snapshot_sync_cache, cache_key, {
                "seq": sync_seq,
                "digest": digest,
                "snapshot": projected_snapshot,
                "previousSeq": 0,
                "previousSnapshot": None,
            }, SNAPSHOT_SYNC_CACHE_MAX_ENTRIES)
            changed = True
        else:
            if state.get("digest") == digest:
                sync_seq = int(state.get("seq", 1))
                changed = False
                projected_snapshot = state.get("snapshot") or projected_snapshot
                previous_seq = int(state.get("previousSeq", 0))
                previous_snapshot = state.get("previousSnapshot")
                _remember_cache_entry_locked(snapshot_sync_cache, cache_key, state, SNAPSHOT_SYNC_CACHE_MAX_ENTRIES)
            else:
                previous_seq = int(state.get("seq", 0))
                previous_snapshot = state.get("snapshot")
                sync_seq = previous_seq + 1
                _remember_cache_entry_locked(snapshot_sync_cache, cache_key, {
                    "seq": sync_seq,
                    "digest": digest,
                    "snapshot": projected_snapshot,
                    "previousSeq": previous_seq,
                    "previousSnapshot": previous_snapshot,
                }, SNAPSHOT_SYNC_CACHE_MAX_ENTRIES)
                changed = True

    if not SNAPSHOT_DELTA_ENABLED or not delta or since_seq <= 0:
        return profile["buildResponse"](projected_snapshot, sync_seq)

    meta = dict(projected_snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED

    if since_seq == sync_seq:
        return {"accountMeta": meta, "isDelta": True, "unchanged": True}

    if not profile["deltaKeys"]:
        return profile["buildResponse"](projected_snapshot, sync_seq)

    if changed and previous_snapshot is not None and since_seq == previous_seq:
        delta_payload = _build_scoped_delta_snapshot(previous_snapshot, projected_snapshot, profile["deltaKeys"])
        delta_response = {
            "accountMeta": meta,
            "isDelta": True,
            "unchanged": False,
            "delta": delta_payload,
        }
        for key in profile["carryKeys"]:
            delta_response[key] = projected_snapshot.get(key) or []
        full_response = profile["buildResponse"](projected_snapshot, sync_seq)
        if _stable_json_size(delta_response) <= _stable_json_size(full_response) * SNAPSHOT_DELTA_FALLBACK_RATIO:
            return delta_response

    return profile["buildResponse"](projected_snapshot, sync_seq)


def _build_snapshot_response(range_key: str, since_seq: int, delta: bool) -> Dict:
    return _build_projected_snapshot_response(range_key, since_seq, delta, projection_name="snapshot")


def _build_summary_snapshot_response(range_key: str, since_seq: int, delta: bool) -> Dict:
    return _build_projected_snapshot_response(range_key, since_seq, delta, projection_name="summary")


def _build_live_snapshot_response(range_key: str, since_seq: int, delta: bool) -> Dict:
    return _build_projected_snapshot_response(range_key, since_seq, delta, projection_name="live")


def _build_pending_snapshot_response(range_key: str, since_seq: int, delta: bool) -> Dict:
    return _build_projected_snapshot_response(range_key, since_seq, delta, projection_name="pending")


def _build_trades_snapshot_response(range_key: str, since_seq: int, delta: bool) -> Dict:
    return _build_projected_snapshot_response(range_key, since_seq, delta, projection_name="trades")


def _build_curve_snapshot_response(range_key: str, since_seq: int, delta: bool) -> Dict:
    return _build_projected_snapshot_response(range_key, since_seq, delta, projection_name="curve")


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


def _join_query(params: Dict[str, Any]) -> str:
    clean_params: List[Tuple[str, Any]] = []
    for key, value in params.items():
        if value is None:
            continue
        clean_params.append((key, value))
    return urllib.parse.urlencode(clean_params, doseq=True)


def _build_binance_rest_upstream_url(path_value: str, query_params: Dict[str, Any]) -> str:
    safe_path = "/" + (path_value or "").lstrip("/")
    upstream = BINANCE_REST_UPSTREAM + safe_path
    query_string = _join_query(query_params)
    if query_string:
        upstream += "?" + query_string
    return upstream


def _build_binance_ws_upstream_url(path_value: str, query_string: str) -> str:
    safe_path = "/" + (path_value or "").lstrip("/")
    upstream = BINANCE_WS_UPSTREAM + safe_path
    if query_string:
        upstream += "?" + query_string
    return upstream


def _proxy_binance_rest(path_value: str, request: Request) -> Response:
    upstream_url = _build_binance_rest_upstream_url(path_value, dict(request.query_params))
    upstream_request = urllib.request.Request(
        upstream_url,
        headers={
            "User-Agent": "mt5-gateway-binance-proxy/1.0",
            "Accept-Encoding": "identity",
        },
        method="GET",
    )
    try:
        with urllib.request.urlopen(upstream_request, timeout=15) as upstream_response:
            body = upstream_response.read()
            content_type = upstream_response.headers.get("Content-Type", "application/json")
            return Response(
                content=body,
                status_code=getattr(upstream_response, "status", 200),
                media_type=content_type.split(";")[0],
                headers={"Content-Type": content_type},
            )
    except urllib.error.HTTPError as exc:
        body = exc.read()
        content_type = exc.headers.get("Content-Type", "application/json")
        return Response(
            content=body,
            status_code=exc.code,
            media_type=content_type.split(";")[0],
            headers={"Content-Type": content_type},
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Binance REST proxy failed: {exc}")


async def _pipe_websocket_client_to_upstream(client: WebSocket, upstream) -> None:
    while True:
        message = await client.receive()
        message_type = message.get("type", "")
        if message_type == "websocket.disconnect":
            return
        if message.get("text") is not None:
            await upstream.send(message["text"])
        elif message.get("bytes") is not None:
            await upstream.send(message["bytes"])


async def _pipe_websocket_upstream_to_client(client: WebSocket, upstream) -> None:
    async for message in upstream:
        if isinstance(message, bytes):
            await client.send_bytes(message)
        else:
            await client.send_text(message)


async def _proxy_binance_websocket(client: WebSocket, path_value: str) -> None:
    upstream_url = _build_binance_ws_upstream_url(path_value, str(client.url.query or ""))
    await client.accept()
    try:
        async with websockets.connect(
            upstream_url,
            ping_interval=20,
            ping_timeout=20,
            close_timeout=5,
            max_size=None,
        ) as upstream:
            client_to_upstream = asyncio.create_task(_pipe_websocket_client_to_upstream(client, upstream))
            upstream_to_client = asyncio.create_task(_pipe_websocket_upstream_to_client(client, upstream))
            done, pending = await asyncio.wait(
                {client_to_upstream, upstream_to_client},
                return_when=asyncio.FIRST_COMPLETED,
            )
            for task in pending:
                task.cancel()
            for task in done:
                task.result()
    except WebSocketDisconnect:
        return
    except Exception as exc:
        await client.close(code=1011, reason=f"Binance WS proxy failed: {exc}")


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
            "mt5TimeOffsetMinutes": MT5_TIME_OFFSET_MINUTES,
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


@app.get("/binance-rest/{path_value:path}")
def binance_rest_proxy(path_value: str, request: Request):
    return _proxy_binance_rest(path_value, request)


@app.get("/v1/source")
def source_status():
    return {
        "gatewayMode": GATEWAY_MODE,
        "mt5PackageAvailable": mt5 is not None,
        "mt5Configured": _is_mt5_configured(),
        "mt5ConfiguredLogin": str(LOGIN),
        "mt5ConfiguredServer": SERVER,
        "mt5PathEnv": PATH or "",
        "mt5TimeOffsetMinutes": MT5_TIME_OFFSET_MINUTES,
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
            snapshot_sync_cache.clear()

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


@app.get("/v1/summary")
def summary(
    range: str = Query(default="7d", pattern="^(1d|7d|1m|3m|1y|all)$"),
    since: int = Query(default=0, ge=0),
    delta: int = Query(default=1, ge=0, le=1),
):
    try:
        return _build_summary_snapshot_response(range.lower(), since_seq=since, delta=(delta == 1))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v1/live")
def live(
    range: str = Query(default="7d", pattern="^(1d|7d|1m|3m|1y|all)$"),
    since: int = Query(default=0, ge=0),
    delta: int = Query(default=1, ge=0, le=1),
):
    try:
        return _build_live_snapshot_response(range.lower(), since_seq=since, delta=(delta == 1))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v1/pending")
def pending(
    range: str = Query(default="7d", pattern="^(1d|7d|1m|3m|1y|all)$"),
    since: int = Query(default=0, ge=0),
    delta: int = Query(default=1, ge=0, le=1),
):
    try:
        return _build_pending_snapshot_response(range.lower(), since_seq=since, delta=(delta == 1))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v1/trades")
def trades(
    range: str = Query(default="7d", pattern="^(1d|7d|1m|3m|1y|all)$"),
    since: int = Query(default=0, ge=0),
    delta: int = Query(default=1, ge=0, le=1),
):
    try:
        return _build_trades_snapshot_response(range.lower(), since_seq=since, delta=(delta == 1))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v1/curve")
def curve(
    range: str = Query(default="7d", pattern="^(1d|7d|1m|3m|1y|all)$"),
    since: int = Query(default=0, ge=0),
    delta: int = Query(default=1, ge=0, le=1),
):
    try:
        return _build_curve_snapshot_response(range.lower(), since_seq=since, delta=(delta == 1))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v1/abnormal")
def abnormal(
    since: int = Query(default=0, ge=0),
    delta: int = Query(default=1, ge=0, le=1),
):
    try:
        return _build_abnormal_response(since_seq=since, delta=(delta == 1))
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.post("/v1/abnormal/config")
def abnormal_config(payload: Dict[str, Any]):
    try:
        return _set_abnormal_config(payload or {})
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.websocket("/binance-ws/{path_value:path}")
async def binance_ws_proxy(client: WebSocket, path_value: str):
    await _proxy_binance_websocket(client, path_value)


if __name__ == "__main__":
    uvicorn.run("server_v2:app", host=HOST, port=PORT, reload=False)
