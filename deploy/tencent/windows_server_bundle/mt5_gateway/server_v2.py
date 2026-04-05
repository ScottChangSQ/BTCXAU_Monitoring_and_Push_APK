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
import v2_account
import v2_market

try:
    import MetaTrader5 as mt5
except Exception:  # pragma: no cover
    mt5 = None

load_dotenv()


def _configure_windows_event_loop_policy() -> None:
    if os.name != "nt":
        return
    selector_policy = getattr(asyncio, "WindowsSelectorEventLoopPolicy", None)
    if selector_policy is None:
        return
    try:
        current_policy = asyncio.get_event_loop_policy()
        if isinstance(current_policy, selector_policy):
            return
    except Exception:
        pass
    try:
        asyncio.set_event_loop_policy(selector_policy())
    except Exception:
        pass


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
MT5_HISTORY_LOOKAHEAD_HOURS = max(0, int(os.getenv("MT5_HISTORY_LOOKAHEAD_HOURS", "24")))
TRADE_HISTORY_TARGET_ITEMS = max(200, int(os.getenv("TRADE_HISTORY_TARGET_ITEMS", "1000")))
BINANCE_REST_UPSTREAM = (os.getenv("BINANCE_REST_UPSTREAM", "https://fapi.binance.com").strip().rstrip("/"))
BINANCE_WS_UPSTREAM = (os.getenv("BINANCE_WS_UPSTREAM", "wss://fstream.binance.com").strip().rstrip("/"))
HEALTH_CACHE_MS = max(1000, int(os.getenv("HEALTH_CACHE_MS", "5000")))
MARKET_CANDLES_CACHE_MS = max(1000, int(os.getenv("MARKET_CANDLES_CACHE_MS", "8000")))
MARKET_CANDLES_CACHE_MAX_ENTRIES = max(8, int(os.getenv("MARKET_CANDLES_CACHE_MAX_ENTRIES", "120")))
MARKET_CANDLES_UPSTREAM_CHUNK_LIMIT = max(100, min(1000, int(os.getenv("MARKET_CANDLES_UPSTREAM_CHUNK_LIMIT", "500"))))
MARKET_CANDLES_UPSTREAM_RETRY = max(0, int(os.getenv("MARKET_CANDLES_UPSTREAM_RETRY", "1")))
ABNORMAL_RECORD_LIMIT = max(50, int(os.getenv("ABNORMAL_RECORD_LIMIT", "5000")))
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
v2_sync_state: Dict[str, Any] = {}
market_candles_cache: Dict[str, Dict[str, Any]] = {}
health_status_cache: Dict[str, Any] = {}
abnormal_config_state: Dict[str, Any] = {"logicAnd": False, "symbols": {}}
abnormal_record_store: List[Dict[str, Any]] = []
abnormal_alert_store: List[Dict[str, Any]] = []
abnormal_last_close_time_by_symbol: Dict[str, int] = {}
abnormal_last_notify_at: Dict[str, int] = {}
abnormal_kline_cache: Dict[str, Dict[str, Any]] = {}
abnormal_sync_state: Dict[str, Any] = {}


def _now_ms() -> int:
    return int(datetime.now(timezone.utc).timestamp() * 1000)


# 统一生成 v2 同步 token，供快照、增量和 WS 消息复用。
def _build_sync_token(server_time_ms: int, revision: str) -> str:
    payload = f"{int(server_time_ms)}:{revision}".encode("utf-8")
    return hashlib.sha1(payload).hexdigest()


def _clone_json_value(value: Any) -> Any:
    return json.loads(json.dumps(value, ensure_ascii=False))


# 构建 v2 行情总快照返回体，统一保留市场区和账户区。
def _build_v2_market_snapshot_payload(market: Dict[str, Any],
                                      account: Dict[str, Any],
                                      server_time: int) -> Dict[str, Any]:
    return {
        "serverTime": int(server_time),
        "syncToken": _build_sync_token(server_time, "market-snapshot"),
        "market": dict(market or {}),
        "account": dict(account or {}),
    }


# 构建 v2 K 线返回体，强制把闭合历史和当前 patch 分层。
def _build_v2_market_candles_payload(symbol: str,
                                     interval: str,
                                     server_time: int,
                                     candles: List[Dict[str, Any]],
                                     latest_patch: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    safe_candles = [dict(item) for item in (candles or [])]
    safe_patch = None if latest_patch is None else dict(latest_patch)
    return {
        "symbol": str(symbol or ""),
        "interval": str(interval or ""),
        "serverTime": int(server_time),
        "candles": safe_candles,
        "latestPatch": safe_patch,
        "nextSyncToken": _build_sync_token(server_time, f"market:{symbol}:{interval}:{len(safe_candles)}"),
    }


# 构建 v2 账户快照返回体，统一输出当前账户、持仓和挂单。
def _build_v2_account_snapshot_payload(account: Dict[str, Any],
                                       positions: List[Dict[str, Any]],
                                       orders: List[Dict[str, Any]],
                                       server_time: int) -> Dict[str, Any]:
    return {
        "serverTime": int(server_time),
        "syncToken": _build_sync_token(server_time, "account-snapshot"),
        "account": dict(account or {}),
        "positions": [dict(item) for item in (positions or [])],
        "orders": [dict(item) for item in (orders or [])],
    }


# 构建 v2 增量返回体，供 HTTP delta 同步入口统一复用。
def _build_v2_delta_payload(market_delta: List[Dict[str, Any]],
                             account_delta: List[Dict[str, Any]],
                             server_time: int,
                             next_sync_token: Optional[str] = None,
                             unchanged: bool = False,
                             full_refresh: Optional[Dict[str, Any]] = None,
                             summary: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    safe_market_delta = [dict(item) for item in (market_delta or [])]
    safe_account_delta = [dict(item) for item in (account_delta or [])]
    refresh_payload = full_refresh or {
        "required": False,
        "reason": "",
        "snapshot": None,
    }
    return {
        "serverTime": int(server_time),
        "marketDelta": safe_market_delta,
        "accountDelta": safe_account_delta,
        "unchanged": bool(unchanged),
        "fullRefresh": {
            "required": bool(refresh_payload.get("required", False)),
            "reason": str(refresh_payload.get("reason", "")),
            "snapshot": refresh_payload.get("snapshot"),
        },
        "summary": dict(summary or {}),
        "nextSyncToken": str(next_sync_token or _build_sync_token(
            server_time,
            f"delta:{len(safe_market_delta)}:{len(safe_account_delta)}"
        )),
    }


# 构建 v2 WS 消息体，统一保留类型、载荷和同步 token。
def _build_v2_ws_message(message_type: str,
                         payload: Dict[str, Any],
                         sync_token: str,
                         server_time: int) -> Dict[str, Any]:
    return {
        "type": str(message_type or ""),
        "payload": dict(payload or {}),
        "serverTime": int(server_time),
        "syncToken": str(sync_token or ""),
    }


# 统一生成稳定 JSON 摘要，供 v2 同步态判断是否变化。
def _stable_payload_digest(value: Any) -> str:
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha1(payload).hexdigest()


# 为 v2 同步层生成“状态不变则 token 不变”的稳定 token。
def _build_v2_state_token(sync_seq: int, state_digest: str) -> str:
    payload = f"v2-sync:{int(sync_seq)}:{state_digest}".encode("utf-8")
    return hashlib.sha1(payload).hexdigest()


# 清理账户元数据里的易抖动字段，避免无效增量。
def _normalize_v2_account_meta(account_meta: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "login": str(account_meta.get("login", "")),
        "server": str(account_meta.get("server", "")),
        "source": str(account_meta.get("source", "")),
        "currency": str(account_meta.get("currency", "")),
        "leverage": int(account_meta.get("leverage", 0) or 0),
        "name": str(account_meta.get("name", "")),
        "company": str(account_meta.get("company", "")),
    }


# 清理 diff 里的内部字段，避免把内部实现细节暴露给客户端。
def _sanitize_diff_payload(diff_payload: Dict[str, Any]) -> Dict[str, Any]:
    upsert_items: List[Dict[str, Any]] = []
    for item in (diff_payload.get("upsert") or []):
        safe_item = dict(item)
        safe_item.pop("_key", None)
        upsert_items.append(safe_item)
    return {
        "upsert": upsert_items,
        "remove": [str(value) for value in (diff_payload.get("remove") or [])],
    }


# 构建 v2 同步层运行态快照，统一 market/account 的摘要和计数。
def _build_v2_sync_runtime_snapshot(server_time: int) -> Dict[str, Any]:
    snapshot = _build_account_light_snapshot_with_cache()
    market_section = _build_v2_market_section()
    account_section = _build_v2_account_section_from_snapshot(snapshot)
    normalized_account = {
        "accountMeta": _normalize_v2_account_meta(account_section.get("accountMeta") or {}),
        "positions": [dict(item) for item in (account_section.get("positions") or [])],
        "orders": [dict(item) for item in (account_section.get("orders") or [])],
    }
    market_digest = _stable_payload_digest(market_section)
    account_digest = _stable_payload_digest(normalized_account)
    account_revision = _stable_payload_digest(account_section)
    state_digest = _stable_payload_digest({
        "marketDigest": market_digest,
        "accountDigest": account_digest,
        "accountRevision": account_revision,
    })
    return {
        "serverTime": int(server_time),
        "market": dict(market_section),
        "account": normalized_account,
        "marketDigest": market_digest,
        "accountDigest": account_digest,
        "accountRevision": account_revision,
        "digest": state_digest,
        "tradeCount": int((account_section.get("accountMeta") or {}).get("tradeCount", 0) or 0),
        "curvePointCount": int((account_section.get("accountMeta") or {}).get("curvePointCount", 0) or 0),
    }


# 根据运行态快照构建稳定 summary，供 HTTP 和 WS 统一输出。
def _build_v2_sync_summary(runtime_snapshot: Dict[str, Any]) -> Dict[str, Any]:
    account_meta = (runtime_snapshot.get("account") or {}).get("accountMeta") or {}
    market_symbols = (runtime_snapshot.get("market") or {}).get("symbols") or []
    return {
        "marketDigest": str(runtime_snapshot.get("marketDigest", "")),
        "accountDigest": str(runtime_snapshot.get("accountDigest", "")),
        "accountRevision": str(runtime_snapshot.get("accountRevision", "")),
        "marketSymbolCount": len(market_symbols),
        "positionCount": len(((runtime_snapshot.get("account") or {}).get("positions") or [])),
        "orderCount": len(((runtime_snapshot.get("account") or {}).get("orders") or [])),
        "tradeCount": int(runtime_snapshot.get("tradeCount", 0) or 0),
        "curvePointCount": int(runtime_snapshot.get("curvePointCount", 0) or 0),
        "login": str(account_meta.get("login", "")),
        "server": str(account_meta.get("server", "")),
        "source": str(account_meta.get("source", "")),
    }


# 计算 v2 market/account 的最小增量事件，供 delta 与 stream 复用。
def _build_v2_sync_delta_events(previous_snapshot: Dict[str, Any], current_snapshot: Dict[str, Any]) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    market_delta: List[Dict[str, Any]] = []
    account_delta: List[Dict[str, Any]] = []

    previous_market = previous_snapshot.get("market") or {}
    current_market = current_snapshot.get("market") or {}
    market_changed_keys = sorted({
        key
        for key in set(previous_market.keys()) | set(current_market.keys())
        if previous_market.get(key) != current_market.get(key)
    })
    if market_changed_keys:
        market_delta.append({
            "type": "marketSnapshotChanged",
            "digest": str(current_snapshot.get("marketDigest", "")),
            "changedKeys": market_changed_keys,
            "snapshot": dict(current_market),
        })

    previous_account = previous_snapshot.get("account") or {}
    current_account = current_snapshot.get("account") or {}
    previous_meta = previous_account.get("accountMeta") or {}
    current_meta = current_account.get("accountMeta") or {}
    meta_changed = previous_meta != current_meta
    positions_diff = _sanitize_diff_payload(_diff_entities(
        previous_account.get("positions") or [],
        current_account.get("positions") or [],
        _position_key,
    ))
    orders_diff = _sanitize_diff_payload(_diff_entities(
        previous_account.get("orders") or [],
        current_account.get("orders") or [],
        _pending_key,
    ))
    has_positions_delta = bool(positions_diff["upsert"] or positions_diff["remove"])
    has_orders_delta = bool(orders_diff["upsert"] or orders_diff["remove"])
    account_revision_changed = str(previous_snapshot.get("accountRevision", "")) != str(current_snapshot.get("accountRevision", ""))
    if meta_changed or has_positions_delta or has_orders_delta or account_revision_changed:
        account_event: Dict[str, Any] = {
            "type": "accountSnapshotChanged",
            "digest": str(current_snapshot.get("accountDigest", "")),
            "revision": str(current_snapshot.get("accountRevision", "")),
            "accountMetaChanged": meta_changed,
            "positions": positions_diff,
            "orders": orders_diff,
        }
        if meta_changed:
            account_event["accountMeta"] = dict(current_meta)
        if account_revision_changed and not (meta_changed or has_positions_delta or has_orders_delta):
            account_event["refreshHint"] = "accountHistoryChanged"
        account_delta.append(account_event)

    return market_delta, account_delta


# 提交 v2 同步状态，保留“当前 + 上一版本”用于单步增量计算。
def _commit_v2_sync_state(runtime_snapshot: Dict[str, Any]) -> Dict[str, Any]:
    with snapshot_cache_lock:
        if not v2_sync_state:
            seq = 1
            token = _build_v2_state_token(seq, str(runtime_snapshot.get("digest", "")))
            v2_sync_state.update({
                "seq": seq,
                "token": token,
                "digest": runtime_snapshot.get("digest"),
                "snapshot": runtime_snapshot,
                "previousSeq": 0,
                "previousToken": "",
                "previousSnapshot": None,
            })
        elif str(v2_sync_state.get("digest", "")) == str(runtime_snapshot.get("digest", "")):
            v2_sync_state["snapshot"] = runtime_snapshot
        else:
            previous_seq = int(v2_sync_state.get("seq", 0) or 0)
            previous_token = str(v2_sync_state.get("token", ""))
            previous_snapshot = v2_sync_state.get("snapshot")
            seq = previous_seq + 1
            token = _build_v2_state_token(seq, str(runtime_snapshot.get("digest", "")))
            v2_sync_state.update({
                "seq": seq,
                "token": token,
                "digest": runtime_snapshot.get("digest"),
                "snapshot": runtime_snapshot,
                "previousSeq": previous_seq,
                "previousToken": previous_token,
                "previousSnapshot": previous_snapshot,
            })

        return {
            "seq": int(v2_sync_state.get("seq", 1) or 1),
            "token": str(v2_sync_state.get("token", "")),
            "snapshot": v2_sync_state.get("snapshot") or runtime_snapshot,
            "previousSeq": int(v2_sync_state.get("previousSeq", 0) or 0),
            "previousToken": str(v2_sync_state.get("previousToken", "")),
            "previousSnapshot": v2_sync_state.get("previousSnapshot"),
        }


# 生成 v2 同步 delta/full-refresh 响应，供 HTTP 与 WS 复用。
def _build_v2_sync_delta_response(sync_token: str, server_time: int) -> Dict[str, Any]:
    runtime_snapshot = _build_v2_sync_runtime_snapshot(server_time)
    state = _commit_v2_sync_state(runtime_snapshot)
    current_token = str(state.get("token", ""))
    previous_token = str(state.get("previousToken", ""))
    current_snapshot = state.get("snapshot") or runtime_snapshot
    previous_snapshot = state.get("previousSnapshot")
    client_token = str(sync_token or "")

    market_delta: List[Dict[str, Any]] = []
    account_delta: List[Dict[str, Any]] = []
    unchanged = False
    full_refresh = {
        "required": False,
        "reason": "",
        "snapshot": None,
    }

    if not client_token:
        full_refresh = {
            "required": True,
            "reason": "bootstrap",
            "snapshot": {
                "market": dict(current_snapshot.get("market") or {}),
                "account": dict(current_snapshot.get("account") or {}),
            },
        }
    elif client_token == current_token:
        unchanged = True
    elif previous_token and client_token == previous_token and previous_snapshot is not None:
        market_delta, account_delta = _build_v2_sync_delta_events(previous_snapshot, current_snapshot)
        if not market_delta and not account_delta:
            full_refresh = {
                "required": True,
                "reason": "stateChanged",
                "snapshot": {
                    "market": dict(current_snapshot.get("market") or {}),
                    "account": dict(current_snapshot.get("account") or {}),
                },
            }
    else:
        full_refresh = {
            "required": True,
            "reason": "tokenMismatch",
            "snapshot": {
                "market": dict(current_snapshot.get("market") or {}),
                "account": dict(current_snapshot.get("account") or {}),
            },
        }

    return _build_v2_delta_payload(
        market_delta=market_delta,
        account_delta=account_delta,
        server_time=server_time,
        next_sync_token=current_token,
        unchanged=unchanged,
        full_refresh=full_refresh,
        summary=_build_v2_sync_summary(current_snapshot),
    )


# 把 delta 结果映射成 WS 事件类型，保证首包和后续消息都有业务含义。
def _resolve_v2_stream_message_type(sync_token: str, delta_payload: Dict[str, Any]) -> str:
    full_refresh = (delta_payload.get("fullRefresh") or {}).get("required", False)
    if full_refresh:
        return "syncBootstrap" if not str(sync_token or "") else "syncRefresh"
    if bool(delta_payload.get("unchanged", False)):
        return "syncSummary"
    return "syncDelta"


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


# 统一生成行情 K 线查询缓存键，避免同窗口短时间重复打上游。
def _build_market_candles_cache_key(symbol: str,
                                    interval: str,
                                    limit: int,
                                    start_time_ms: int,
                                    end_time_ms: int) -> str:
    return "|".join(
        [
            str(symbol or "").strip().upper(),
            str(interval or "").strip(),
            str(max(1, int(limit or 0))),
            str(max(0, int(start_time_ms or 0))),
            str(max(0, int(end_time_ms or 0))),
        ]
    )


# 行情 K 线短缓存：同一窗口短时间直接复用，减小手机反复切页时的上游压力。
def _get_cached_market_candle_rows(cache_key: str, now_ms: int) -> Optional[List[Any]]:
    with snapshot_cache_lock:
        cached = market_candles_cache.get(cache_key)
        if not cached:
            return None
        if now_ms - int(cached.get("builtAt", 0) or 0) > MARKET_CANDLES_CACHE_MS:
            market_candles_cache.pop(cache_key, None)
            return None
        cached["lastAccessAt"] = now_ms
        _remember_cache_entry_locked(
            market_candles_cache,
            cache_key,
            cached,
            MARKET_CANDLES_CACHE_MAX_ENTRIES,
        )
        return [list(item) if isinstance(item, (list, tuple)) else dict(item) for item in (cached.get("rows") or [])]


# 统一写入行情 K 线短缓存，命中时刷新最近使用顺序。
def _remember_market_candle_rows(cache_key: str, rows: List[Any], now_ms: int) -> None:
    normalized_rows: List[Any] = []
    for item in rows or []:
        if isinstance(item, (list, tuple)):
            normalized_rows.append(list(item))
        elif isinstance(item, dict):
            normalized_rows.append(dict(item))
        else:
            normalized_rows.append(item)
    with snapshot_cache_lock:
        _remember_cache_entry_locked(
            market_candles_cache,
            cache_key,
            {
                "builtAt": now_ms,
                "lastAccessAt": now_ms,
                "rows": normalized_rows,
            },
            MARKET_CANDLES_CACHE_MAX_ENTRIES,
        )


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
    last_error = str(abnormal_sync_state.get("lastError", "") or "")
    if last_error:
        meta["warning"] = last_error
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
    try:
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
            abnormal_sync_state.pop("lastError", None)
            if new_records or not abnormal_sync_state:
                _append_abnormal_updates_locked(new_records)
    except Exception as exc:
        with abnormal_state_lock:
            _ensure_abnormal_defaults_locked()
            if not abnormal_sync_state:
                _commit_abnormal_snapshot_locked()
            abnormal_sync_state["lastError"] = str(exc)


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
    last_error = str(abnormal_sync_state.get("lastError", "") or "")
    if last_error:
        meta["warning"] = last_error

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


def _curve_exposure_side_for_close(deal_type: int) -> str:
    # 平仓成交方向与原持仓方向相反，关闭曝光时需要回到原持仓侧。
    return "Sell" if _is_buy_deal_type(deal_type) else "Buy"


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


# MT5 Python 历史接口对“本地无时区 datetime”更稳定，避免 UTC aware 时间把当天后半段成交截掉。
def _mt5_history_window(range_key: str) -> Tuple[datetime, datetime]:
    now_local = datetime.now()
    to_time = now_local + timedelta(hours=MT5_HISTORY_LOOKAHEAD_HOURS)
    from_time = now_local - timedelta(hours=_range_hours(range_key))
    return from_time, to_time


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

        # 优先附着到当前终端已有会话，避免重新拉起鉴权后进入缺历史缓存的上下文。
        _shutdown_mt5()
        initialized, init_message = _mt5_initialize(path_value)
        if not initialized:
            errors.append(f"init({label})={init_message}")
        else:
            logged_in, login_message = _mt5_login()
            if logged_in:
                mt5_last_connected_path = label
                return
            errors.append(f"login({label})={login_message}")

        # 回退到带鉴权初始化，兼容机器上还没有可复用登录态的场景。
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
    from_time, to_time = _mt5_history_window(range_key)
    deals = mt5.history_deals_get(from_time, to_time) or []
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
        price = float(last_price_by_symbol.get(symbol, 0.0) or 0.0)
        if price <= 0.0:
            price = float(last_price_by_symbol.get(_normalize_curve_market_symbol(symbol), 0.0) or 0.0)
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
            price = float(last_price_by_symbol.get(_normalize_curve_market_symbol(symbol), 0.0) or 0.0)
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


def _has_curve_exposure(exposures: Dict[Tuple[str, str], Dict[str, float]]) -> bool:
    for state in exposures.values():
        if float(state.get("volume", 0.0) or 0.0) > 0.0:
            return True
    return False


def _normalize_curve_market_symbol(symbol: str) -> str:
    value = str(symbol or "").strip().upper()
    if value in {"BTC", "BTCUSD", "BTCUSDT", "XBT"}:
        return "BTCUSDT"
    if value in {"XAU", "XAUUSD", "XAUUSDT", "GOLD"}:
        return "XAUUSDT"
    return value


def _curve_sampling_interval(duration_ms: int) -> Tuple[str, int]:
    safe_duration = max(0, int(duration_ms or 0))
    minute_ms = 60 * 1000
    hour_ms = 60 * minute_ms
    day_ms = 24 * hour_ms
    if safe_duration <= 6 * hour_ms:
        return "1m", minute_ms
    if safe_duration <= 3 * day_ms:
        return "5m", 5 * minute_ms
    if safe_duration <= 14 * day_ms:
        return "15m", 15 * minute_ms
    if safe_duration <= 60 * day_ms:
        return "1h", hour_ms
    return "4h", 4 * hour_ms


def _fetch_curve_price_samples(symbol: str,
                               start_ms: int,
                               end_ms: int,
                               fetch_rows_fn=None) -> List[Dict[str, float]]:
    normalized_symbol = _normalize_curve_market_symbol(symbol)
    safe_start = max(0, int(start_ms or 0))
    safe_end = max(safe_start, int(end_ms or 0))
    if not normalized_symbol or safe_end <= safe_start:
        return []

    interval, interval_ms = _curve_sampling_interval(safe_end - safe_start)
    fetcher = fetch_rows_fn or _fetch_binance_kline_rows
    cursor = safe_start
    collected: Dict[int, Dict[str, float]] = {}

    while cursor < safe_end:
        remaining = safe_end - cursor
        limit = max(1, min(1500, int(math.ceil(remaining / max(interval_ms, 1))) + 2))
        rows = fetcher(
            normalized_symbol,
            interval,
            limit,
            start_time_ms=cursor,
            end_time_ms=safe_end,
        ) or []
        if not rows:
            break
        last_open_time = cursor
        for row in rows:
            if not isinstance(row, (list, tuple)) or len(row) < 5:
                continue
            open_time = int(row[0] or 0)
            close_time = int(row[6] or 0) if len(row) > 6 else (open_time + interval_ms - 1)
            close_price = float(row[4] or 0.0)
            last_open_time = max(last_open_time, open_time)
            if close_price <= 0.0 or close_time <= safe_start or close_time >= safe_end:
                continue
            collected[close_time] = {
                "timestamp": close_time,
                "symbol": normalized_symbol,
                "price": close_price,
            }
        next_cursor = last_open_time + interval_ms
        if next_cursor <= cursor:
            break
        cursor = next_cursor

    return [collected[key] for key in sorted(collected.keys())]


def _append_curve_history_samples(points: List[Dict[str, float]],
                                  exposures: Dict[Tuple[str, str], Dict[str, float]],
                                  last_price_by_symbol: Dict[str, float],
                                  running_balance: float,
                                  leverage: float,
                                  start_ms: int,
                                  end_ms: int,
                                  fetch_rows_fn=None) -> None:
    safe_start = int(start_ms or 0)
    safe_end = int(end_ms or 0)
    if safe_end <= safe_start or not _has_curve_exposure(exposures):
        return

    sample_map: Dict[int, Dict[str, float]] = {}
    active_symbols = sorted({
        _normalize_curve_market_symbol(symbol)
        for symbol, _side in exposures.keys()
        if symbol and float((exposures.get((symbol, _side)) or {}).get("volume", 0.0) or 0.0) > 0.0
    })
    for symbol in active_symbols:
        for sample in _fetch_curve_price_samples(symbol, safe_start, safe_end, fetch_rows_fn):
            timestamp = int(sample.get("timestamp", 0) or 0)
            if timestamp <= safe_start or timestamp >= safe_end:
                continue
            price = float(sample.get("price", 0.0) or 0.0)
            if price <= 0.0:
                continue
            sample_map.setdefault(timestamp, {})[symbol] = price

    for timestamp in sorted(sample_map.keys()):
        for symbol, price in sample_map[timestamp].items():
            last_price_by_symbol[symbol] = price
        floating = _calculate_curve_floating(exposures, last_price_by_symbol)
        equity = running_balance + floating
        points.append(_curve_point(
            timestamp,
            equity,
            running_balance,
            _calculate_curve_position_ratio(exposures, last_price_by_symbol, equity, leverage),
        ))


def _inject_positions_into_exposures(
    exposures: Dict[Tuple[str, str], Dict[str, float]],
    positions: List[Dict[str, Any]],
    open_position_ids: set,
    contract_size_fn,
    last_price_by_symbol: Dict[str, float],
) -> None:
    # 把当前未平仓持仓注入到曝光映射里，避免与本窗口内开仓的重复叠加
    for position in positions or []:
        position_id = int(position.get("positionId", 0) or 0)
        ticket = int(position.get("positionTicket", 0))
        if (position_id > 0 and position_id in open_position_ids) or (ticket > 0 and ticket in open_position_ids):
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


def _resolve_open_position_ids_from_history(deal_history: List[Dict[str, Any]]) -> set:
    # 只保留在历史窗口末尾仍然未平的 position id，避免把已平历史仓位误当成当前持仓。
    states: Dict[Tuple[int, str], float] = {}
    sorted_deals = sorted(
        deal_history or [],
        key=lambda item: (int(item.get("timestamp", 0) or 0), int(item.get("position_id", 0) or 0)),
    )
    for deal in sorted_deals:
        position_id = int(deal.get("position_id", 0) or 0)
        if position_id <= 0:
            continue
        entry = int(deal.get("entry", 0) or 0)
        deal_type = int(deal.get("deal_type", -1) or -1)
        volume = abs(float(deal.get("volume", 0.0) or 0.0))
        if volume <= 0.0:
            continue
        if _is_entry_close(entry):
            close_side = _curve_exposure_side_for_close(deal_type)
            close_key = (position_id, close_side)
            states[close_key] = max(0.0, float(states.get(close_key, 0.0) or 0.0) - volume)
        if _is_entry_open(entry):
            open_side = "Buy" if _is_buy_deal_type(deal_type) else "Sell"
            open_key = (position_id, open_side)
            states[open_key] = float(states.get(open_key, 0.0) or 0.0) + volume
    return {
        position_id
        for (position_id, _side), volume in states.items()
        if float(volume or 0.0) > 1e-9
    }


def _replay_curve_from_history(
    deal_history: List[Dict[str, Any]],
    start_balance: float,
    open_positions: List[Dict[str, Any]],
    current_balance: float,
    current_equity: float,
    leverage: float,
    contract_size_fn,
    now_ms: int,
    fetch_rows_fn=None,
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
    open_position_ids = _resolve_open_position_ids_from_history(deal_history)
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

    last_event_ts = first_ts
    for index, deal in enumerate(sorted_deals):
        timestamp = int(deal.get("timestamp", 0))
        if index > 0 and timestamp > last_event_ts:
            _append_curve_history_samples(
                points,
                exposures,
                last_price_by_symbol,
                running_balance,
                leverage,
                last_event_ts,
                timestamp,
                fetch_rows_fn,
            )
        symbol = str(deal.get("symbol", "") or "")
        price = float(deal.get("price", 0.0))
        if symbol and price > 0.0:
            last_price_by_symbol[_normalize_curve_market_symbol(symbol)] = price

        entry = int(deal.get("entry", 0))
        deal_type = int(deal.get("deal_type", -1))
        volume = abs(float(deal.get("volume", 0.0)))
        direction = "Buy" if _is_buy_deal_type(deal_type) else "Sell"

        if _is_entry_close(entry):
            _remove_curve_exposure(exposures, symbol, _curve_exposure_side_for_close(deal_type), volume)
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
        last_event_ts = timestamp

    if now_ms > last_event_ts:
        _append_curve_history_samples(
            points,
            exposures,
            last_price_by_symbol,
            running_balance,
            leverage,
            last_event_ts,
            now_ms,
            fetch_rows_fn,
        )

    current_market_value = _resolve_positions_market_value(open_positions)
    if current_market_value <= 0.0:
        current_market_value = _calculate_curve_market_value(exposures, last_price_by_symbol)
    final_timestamp = max(now_ms, last_event_ts)
    final_point = _curve_point(
        final_timestamp,
        current_equity,
        current_balance,
        _curve_position_ratio_from_margin(
            _safe_div(current_market_value, _resolve_effective_leverage(leverage)),
            current_equity,
        ),
    )
    if points and int(points[-1].get("timestamp", 0) or 0) >= final_timestamp:
        points[-1] = final_point
    else:
        points.append(final_point)
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
                "positionId": int(getattr(position, "identifier", 0) or getattr(position, "ticket", 0)),
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


def _progressive_trade_history_deals(range_key: str) -> List[Any]:
    from_time, to_time = _mt5_history_window(range_key)
    if range_key.lower() != "all":
        return mt5.history_deals_get(from_time, to_time) or []

    max_days = max(30, SNAPSHOT_RANGE_ALL_DAYS)
    progressive_days = [30, 90, 180, 365, max_days]
    unique_days: List[int] = []
    for days in progressive_days:
        safe_days = max(30, min(days, max_days))
        if safe_days not in unique_days:
            unique_days.append(safe_days)

    now_local = datetime.now()
    deals: List[Any] = []
    previous_count = -1
    for days in unique_days:
        window_from = now_local - timedelta(days=days)
        deals = mt5.history_deals_get(window_from, to_time) or []
        if len(deals) <= previous_count:
            break
        previous_count = len(deals)
    return deals


def _map_trade_deals(deals: List[Any]) -> List[Dict]:
    mapped = []
    open_batches: Dict[str, List[Dict[str, Any]]] = {}
    open_batches_by_symbol_side: Dict[str, List[Dict[str, Any]]] = {}
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

    def symbol_side_key(symbol: str, side: str) -> str:
        return f"{symbol}:{side}"

    def append_open_batch(key: str,
                          symbol: str,
                          side: str,
                          open_time_ms: int,
                          open_price: float,
                          volume: float) -> None:
        if volume <= volume_epsilon:
            return
        batch = {
            "symbol": symbol,
            "side": side,
            "open_time": int(open_time_ms),
            "open_price": float(open_price),
            "remaining_volume": float(volume),
        }
        primary_queue = open_batches.setdefault(key, [])
        primary_queue.append(batch)
        fallback_queue = open_batches_by_symbol_side.setdefault(symbol_side_key(symbol, side), [])
        fallback_queue.append(batch)

    def consume_queue(queue: List[Dict[str, Any]], close_volume: float) -> Tuple[List[Tuple[Dict[str, Any], float]], float]:
        matches: List[Tuple[Dict[str, Any], float]] = []
        remaining = float(close_volume)
        while remaining > volume_epsilon and queue:
            batch = queue[0]
            batch_remaining = float(batch.get("remaining_volume", 0.0))
            if batch_remaining <= volume_epsilon:
                queue.pop(0)
                continue
            matched = min(remaining, batch_remaining)
            if matched <= volume_epsilon:
                queue.pop(0)
                continue
            matches.append((dict(batch), matched))
            batch["remaining_volume"] = max(0.0, batch_remaining - matched)
            remaining -= matched
            if float(batch.get("remaining_volume", 0.0)) <= volume_epsilon:
                queue.pop(0)
        return matches, max(0.0, remaining)

    def consume_open_batches(key: str, close_volume: float) -> Tuple[List[Tuple[Dict[str, Any], float]], float]:
        queue = open_batches.setdefault(key, [])
        return consume_queue(queue, close_volume)

    def consume_open_batches_by_symbol_side(symbol: str,
                                            side: str,
                                            close_volume: float) -> Tuple[List[Tuple[Dict[str, Any], float]], float]:
        queue = open_batches_by_symbol_side.setdefault(symbol_side_key(symbol, side), [])
        return consume_queue(queue, close_volume)

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
            original_side = infer_original_side(deal_type)
            matches, remaining_after_close = consume_open_batches(key, volume)
            if remaining_after_close > volume_epsilon:
                fallback_matches, remaining_after_close = consume_open_batches_by_symbol_side(
                    symbol,
                    original_side,
                    remaining_after_close,
                )
                matches.extend(fallback_matches)
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
                    side=original_side,
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
                symbol=symbol,
                side=current_side,
                open_time_ms=ts,
                open_price=price,
                volume=open_volume,
            )

    mapped.sort(key=lambda item: item["timestamp"], reverse=True)
    return mapped


def _map_trades(range_key: str = "all") -> List[Dict]:
    deals = _progressive_trade_history_deals(range_key)
    return _map_trade_deals(deals)


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

    total_asset = balance
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
                    "balance": float(getattr(account, "balance", 0.0) or 0.0),
                    "equity": float(getattr(account, "equity", 0.0) or 0.0),
                    "margin": float(getattr(account, "margin", 0.0) or 0.0),
                    "freeMargin": float(getattr(account, "margin_free", 0.0) or 0.0),
                    "marginLevel": float(getattr(account, "margin_level", 0.0) or 0.0),
                    "profit": float(getattr(account, "profit", 0.0) or 0.0),
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


# 构建只包含账户摘要、当前持仓和挂单的轻快照，供高频 v2 接口复用。
def _snapshot_from_mt5_light() -> Dict:
    with state_lock:
        _ensure_mt5()
        try:
            account = mt5.account_info()
            if account is None:
                raise RuntimeError("account_info is None")
            positions = _map_positions()
            pending_orders = _map_pending_orders()
            from_time, to_time = _mt5_history_window("all")
            trade_count = 0
            try:
                history_deals_total = getattr(mt5, "history_deals_total", None)
                if callable(history_deals_total):
                    trade_count = int(history_deals_total(from_time, to_time) or 0)
            except Exception:
                trade_count = 0
            return {
                "accountMeta": {
                    "login": str(getattr(account, "login", LOGIN)),
                    "server": str(getattr(account, "server", SERVER)),
                    "source": "MT5 Python Pull",
                    "updatedAt": _now_ms(),
                    "currency": str(getattr(account, "currency", "")),
                    "leverage": int(getattr(account, "leverage", 0) or 0),
                    "balance": float(getattr(account, "balance", 0.0) or 0.0),
                    "equity": float(getattr(account, "equity", 0.0) or 0.0),
                    "margin": float(getattr(account, "margin", 0.0) or 0.0),
                    "freeMargin": float(getattr(account, "margin_free", 0.0) or 0.0),
                    "marginLevel": float(getattr(account, "margin_level", 0.0) or 0.0),
                    "profit": float(getattr(account, "profit", 0.0) or 0.0),
                    "name": str(getattr(account, "name", "")),
                    "company": str(getattr(account, "company", "")),
                    "range": "light",
                    "tradeCount": trade_count,
                    "positionCount": len(positions),
                    "pendingOrderCount": len(pending_orders),
                    "curvePointCount": 0,
                },
                "positions": positions,
                "pendingOrders": pending_orders,
            }
        finally:
            _shutdown_mt5()


def _infer_trade_contract_size(item: Dict[str, Any]) -> float:
    quantity = abs(float(item.get("quantity", 0.0) or 0.0))
    price = abs(float(item.get("price", 0.0) or 0.0))
    amount = abs(float(item.get("amount", 0.0) or 0.0))
    if quantity <= 0.0 or price <= 0.0 or amount <= 0.0:
        return 1.0
    inferred = amount / (quantity * price)
    return inferred if inferred > 0.0 else 1.0


def _should_rebuild_ea_trade_records(records: List[Dict[str, Any]]) -> bool:
    if not records:
        return False
    identified_count = 0
    raw_like_count = 0
    for item in records:
        if not isinstance(item, dict):
            continue
        if any(key in item for key in ("entryType", "dealType", "dealTicket", "positionId", "orderId")):
            identified_count += 1
        timestamp = int(item.get("timestamp", item.get("time", 0)) or 0)
        price = float(item.get("price", 0.0) or 0.0)
        open_time = int(item.get("openTime", timestamp) or timestamp)
        close_time = int(item.get("closeTime", timestamp) or timestamp)
        open_price = float(item.get("openPrice", price) or price)
        close_price = float(item.get("closePrice", price) or price)
        if open_time == timestamp and close_time == timestamp and abs(open_price - price) < 1e-9 and abs(close_price - price) < 1e-9:
            raw_like_count += 1
    return identified_count > 0 and raw_like_count >= max(1, len(records) - 1)


def _rebuild_ea_trade_records(records: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    rebuilt: List[Dict[str, Any]] = []
    open_batches: Dict[str, List[Dict[str, Any]]] = {}
    open_batches_by_symbol_side: Dict[str, List[Dict[str, Any]]] = {}
    volume_epsilon = 1e-9

    def contract_size_of(item: Dict[str, Any]) -> float:
        return _infer_trade_contract_size(item)

    def lifecycle_key(position_id: int, order_id: int, ticket: int) -> str:
        if position_id > 0:
            return f"position:{position_id}"
        if order_id > 0:
            return f"order:{order_id}"
        return f"ticket:{ticket}"

    def infer_original_side(deal_type: int) -> str:
        return "Buy" if deal_type == 1 else "Sell"

    def symbol_side_key(symbol: str, side: str) -> str:
        return f"{symbol}:{side}"

    def append_open_batch(key: str,
                          symbol: str,
                          side: str,
                          open_time_ms: int,
                          open_price: float,
                          volume: float) -> None:
        if volume <= volume_epsilon:
            return
        batch = {
            "symbol": symbol,
            "side": side,
            "open_time": int(open_time_ms),
            "open_price": float(open_price),
            "remaining_volume": float(volume),
        }
        open_batches.setdefault(key, []).append(batch)
        open_batches_by_symbol_side.setdefault(symbol_side_key(symbol, side), []).append(batch)

    def consume_queue(queue: List[Dict[str, Any]], close_volume: float) -> Tuple[List[Tuple[Dict[str, Any], float]], float]:
        matches: List[Tuple[Dict[str, Any], float]] = []
        remaining = float(close_volume)
        while remaining > volume_epsilon and queue:
            batch = queue[0]
            batch_remaining = float(batch.get("remaining_volume", 0.0))
            if batch_remaining <= volume_epsilon:
                queue.pop(0)
                continue
            matched = min(remaining, batch_remaining)
            if matched <= volume_epsilon:
                queue.pop(0)
                continue
            matches.append((dict(batch), matched))
            batch["remaining_volume"] = max(0.0, batch_remaining - matched)
            remaining -= matched
            if float(batch.get("remaining_volume", 0.0)) <= volume_epsilon:
                queue.pop(0)
        return matches, max(0.0, remaining)

    def consume_open_batches(key: str, close_volume: float) -> Tuple[List[Tuple[Dict[str, Any], float]], float]:
        return consume_queue(open_batches.setdefault(key, []), close_volume)

    def consume_open_batches_by_symbol_side(symbol: str,
                                            side: str,
                                            close_volume: float) -> Tuple[List[Tuple[Dict[str, Any], float]], float]:
        return consume_queue(open_batches_by_symbol_side.setdefault(symbol_side_key(symbol, side), []), close_volume)

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
                            remark: str,
                            contract_size: float) -> None:
        if close_volume <= volume_epsilon or total_volume <= volume_epsilon:
            return
        ratio = close_volume / total_volume
        commission_fee = abs(total_commission) * ratio
        storage_fee = total_swap * ratio
        amount = abs(close_volume * close_price * contract_size)
        rebuilt.append(
            {
                "timestamp": close_time,
                "productName": symbol,
                "code": symbol,
                "side": side,
                "price": close_price,
                "quantity": close_volume,
                "amount": amount,
                "fee": commission_fee,
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

    sorted_records = sorted(records, key=lambda item: int(item.get("timestamp", item.get("time", 0)) or 0))
    for record in sorted_records:
        symbol = str(record.get("code") or record.get("productName") or "").strip()
        if not symbol:
            continue
        deal_type = int(record.get("dealType", 0 if str(record.get("side", "")).strip().lower() == "buy" else 1) or 0)
        if not _is_trade_deal_type(deal_type):
            continue
        volume = abs(float(record.get("quantity", record.get("volume", 0.0)) or 0.0))
        if volume <= 0.0:
            continue
        ticket = int(record.get("dealTicket", record.get("ticket", 0)) or 0)
        order_id = int(record.get("orderId", record.get("order", 0)) or 0)
        position_id = int(record.get("positionId", record.get("position_id", 0)) or 0)
        key = lifecycle_key(position_id, order_id, ticket)
        entry_type = int(record.get("entryType", record.get("entry", 0)) or 0)
        timestamp = int(record.get("timestamp", record.get("time", 0)) or 0)
        price = float(record.get("price", 0.0) or 0.0)
        profit = float(record.get("profit", 0.0) or 0.0)
        commission = float(record.get("commission", record.get("fee", 0.0)) or 0.0)
        swap = float(record.get("swap", record.get("storageFee", 0.0)) or 0.0)
        current_side = "Buy" if _is_buy_deal_type(deal_type) else "Sell"
        comment = str(record.get("remark", "") or "")
        contract_size = contract_size_of(record)

        if _is_entry_close(entry_type):
            original_side = infer_original_side(deal_type)
            matches, remaining_after_close = consume_open_batches(key, volume)
            if remaining_after_close > volume_epsilon:
                fallback_matches, remaining_after_close = consume_open_batches_by_symbol_side(
                    symbol,
                    original_side,
                    remaining_after_close,
                )
                matches.extend(fallback_matches)

            split_count = len(matches) if matches else 1
            if matches:
                for split_index, (batch, matched_volume) in enumerate(matches):
                    append_close_record(
                        symbol=symbol,
                        ticket=ticket,
                        order_id=order_id,
                        position_id=position_id,
                        entry_type=entry_type,
                        close_time=timestamp,
                        close_price=price,
                        close_volume=matched_volume,
                        total_volume=volume,
                        total_profit=profit,
                        total_commission=commission,
                        total_swap=swap,
                        open_time=int(batch.get("open_time", timestamp) or timestamp),
                        open_price=float(batch.get("open_price", price) or price),
                        side=original_side,
                        split_count=split_count,
                        split_index=split_index,
                        remark=comment,
                        contract_size=contract_size,
                    )
            elif remaining_after_close > volume_epsilon:
                append_close_record(
                    symbol=symbol,
                    ticket=ticket,
                    order_id=order_id,
                    position_id=position_id,
                    entry_type=entry_type,
                    close_time=timestamp,
                    close_price=price,
                    close_volume=remaining_after_close,
                    total_volume=volume,
                    total_profit=profit,
                    total_commission=commission,
                    total_swap=swap,
                    open_time=timestamp,
                    open_price=price,
                    side=original_side,
                    split_count=1,
                    split_index=0,
                    remark=comment,
                    contract_size=contract_size,
                )

        if _is_entry_open(entry_type):
            append_open_batch(key, symbol, current_side, timestamp, price, volume)

    rebuilt.sort(key=lambda item: int(item.get("closeTime", item.get("timestamp", 0)) or 0), reverse=True)
    return rebuilt


def _normalize_ea_snapshot_trades(account_meta: Dict[str, Any], trades: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    source = str((account_meta or {}).get("source", "") or "")
    if "MT5 EA Push" not in source:
        return trades
    if not _should_rebuild_ea_trade_records(trades):
        return trades
    rebuilt = _rebuild_ea_trade_records(trades)
    return rebuilt if rebuilt else trades


def _overview_metric_value(metrics: List[Dict[str, Any]], *names: str) -> float:
    if not metrics or not names:
        return 0.0
    normalized_names = [str(name or "").strip().lower() for name in names if str(name or "").strip()]
    for metric in metrics:
        if not isinstance(metric, dict):
            continue
        metric_name = str(metric.get("name", "") or "").strip().lower()
        if not metric_name:
            continue
        if any(candidate in metric_name for candidate in normalized_names):
            raw_value = str(metric.get("value", "") or "")
            number = "".join(ch for ch in raw_value if ch.isdigit() or ch in ".-+")
            try:
                return float(number)
            except Exception:
                return 0.0
    return 0.0


def _infer_ea_curve_contract_size(item: Dict[str, Any]) -> float:
    quantity = abs(float(item.get("quantity", item.get("volume", 0.0)) or 0.0))
    price = abs(float(item.get("price", 0.0) or 0.0))
    amount = abs(float(item.get("amount", 0.0) or 0.0))
    if quantity <= 0.0 or price <= 0.0 or amount <= 0.0:
        return 1.0
    inferred = amount / (quantity * price)
    return inferred if inferred > 0.0 else 1.0


def _rebuild_sparse_ea_curve_points(account_meta: Dict[str, Any],
                                    overview_metrics: List[Dict[str, Any]],
                                    curve_points: List[Dict[str, Any]],
                                    raw_trades: List[Dict[str, Any]],
                                    positions: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    source = str((account_meta or {}).get("source", "") or "")
    if "MT5 EA Push" not in source:
        return curve_points
    if not raw_trades:
        return curve_points

    deal_history: List[Dict[str, Any]] = []
    realized = 0.0
    contract_size_cache: Dict[str, float] = {}

    def contract_size_fn(symbol: str) -> float:
        cached = contract_size_cache.get(symbol)
        if cached is not None:
            return cached
        for item in raw_trades:
            item_symbol = str(item.get("code") or item.get("productName") or item.get("symbol") or "").strip()
            if item_symbol != symbol:
                continue
            cached = _infer_ea_curve_contract_size(item)
            contract_size_cache[symbol] = cached
            return cached
        contract_size_cache[symbol] = 1.0
        return 1.0

    for item in sorted(raw_trades, key=lambda current: int(current.get("timestamp", current.get("time", 0)) or 0)):
        entry = int(item.get("entryType", item.get("entry", 0)) or 0)
        deal_type = int(item.get("dealType", item.get("deal_type", 0)) or 0)
        volume = abs(float(item.get("quantity", item.get("volume", 0.0)) or 0.0))
        symbol = str(item.get("code") or item.get("productName") or item.get("symbol") or "").strip()
        if not symbol or volume <= 0.0 or not _is_trade_deal_type(deal_type):
            continue
        profit = float(item.get("profit", 0.0) or 0.0)
        commission = float(item.get("commission", item.get("fee", 0.0)) or 0.0)
        swap = float(item.get("swap", item.get("storageFee", 0.0)) or 0.0)
        realized += profit + commission + swap
        deal_history.append({
            "timestamp": int(item.get("timestamp", item.get("time", 0)) or 0),
            "price": float(item.get("price", 0.0) or 0.0),
            "profit": profit,
            "commission": commission,
            "swap": swap,
            "entry": entry,
            "deal_type": deal_type,
            "volume": volume,
            "symbol": symbol,
            "position_id": int(item.get("positionId", item.get("position_id", 0)) or 0),
        })

    if not deal_history:
        return curve_points

    current_balance = float((account_meta or {}).get("balance", 0.0) or 0.0)
    current_equity = float((account_meta or {}).get("equity", 0.0) or 0.0)
    leverage = float((account_meta or {}).get("leverage", 0.0) or 0.0)
    if current_balance <= 0.0:
        current_balance = _overview_metric_value(overview_metrics, "balance", "结余")
    if current_equity <= 0.0:
        current_equity = _overview_metric_value(overview_metrics, "current equity", "equity", "净资产", "净值")
    if leverage <= 0.0:
        leverage = _overview_metric_value(overview_metrics, "leverage", "杠杆")
    if current_balance <= 0.0:
        current_balance = current_equity
    if current_equity <= 0.0:
        current_equity = current_balance
    start_balance = current_balance - realized

    rebuilt = _replay_curve_from_history(
        deal_history=deal_history,
        start_balance=start_balance,
        open_positions=positions or [],
        current_balance=current_balance,
        current_equity=current_equity,
        leverage=leverage,
        contract_size_fn=contract_size_fn,
        now_ms=max(int((account_meta or {}).get("updatedAt", 0) or 0), _now_ms()),
        fetch_rows_fn=lambda symbol, interval, limit, **kwargs: [],
    )
    return rebuilt if rebuilt else curve_points


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
    raw_trades = data.get("trades") or []
    data["trades"] = _normalize_ea_snapshot_trades(account_meta, raw_trades)
    data["curvePoints"] = _rebuild_sparse_ea_curve_points(
        account_meta,
        data["overviewMetrics"],
        data["curvePoints"],
        raw_trades,
        data["positions"],
    )
    data["statsMetrics"] = data.get("statsMetrics") or []
    return data


def _clone_snapshot_payload(snapshot: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    safe_snapshot = snapshot or {}
    cloned: Dict[str, Any] = {}
    for key, value in safe_snapshot.items():
        if isinstance(value, dict):
            cloned[key] = dict(value)
        elif isinstance(value, list):
            cloned[key] = [dict(item) if isinstance(item, dict) else item for item in value]
        else:
            cloned[key] = value
    return cloned


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


# 构建账户轻快照缓存，避免高频 v2 snapshot/sync 重复生成整份历史对象。
def _build_account_light_snapshot_with_cache() -> Dict:
    cache_key = "account-light"
    now_ms = _now_ms()
    with snapshot_cache_lock:
        cached = snapshot_build_cache.get(cache_key)
        if cached and (now_ms - int(cached.get("builtAt", 0))) <= SNAPSHOT_BUILD_CACHE_MS:
            cached["lastAccessAt"] = now_ms
            _remember_cache_entry_locked(snapshot_build_cache, cache_key, cached, SNAPSHOT_BUILD_CACHE_MAX_ENTRIES)
            return cached.get("snapshot")
        if _should_slide_snapshot_build_cache(cached, now_ms):
            cached["builtAt"] = now_ms
            cached["lastAccessAt"] = now_ms
            _remember_cache_entry_locked(snapshot_build_cache, cache_key, cached, SNAPSHOT_BUILD_CACHE_MAX_ENTRIES)
            return cached.get("snapshot")

    snapshot = _build_account_light_snapshot()
    with snapshot_cache_lock:
        _remember_cache_entry_locked(snapshot_build_cache, cache_key, {
            "builtAt": now_ms,
            "lastAccessAt": now_ms,
            "snapshot": snapshot,
        }, SNAPSHOT_BUILD_CACHE_MAX_ENTRIES)
    return snapshot


def _snapshot_trades_from_mt5(range_key: str) -> List[Dict[str, Any]]:
    with state_lock:
        _ensure_mt5()
        try:
            return _map_trades(range_key)
        finally:
            _shutdown_mt5()


def _build_trade_history_with_cache(range_key: str, fallback_snapshot: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
    cache_key = f"{range_key}:trade-history"
    now_ms = _now_ms()
    with snapshot_cache_lock:
        cached = snapshot_build_cache.get(cache_key)
        if cached and (now_ms - int(cached.get("builtAt", 0))) <= SNAPSHOT_BUILD_CACHE_MS:
            cached["lastAccessAt"] = now_ms
            _remember_cache_entry_locked(snapshot_build_cache, cache_key, cached, SNAPSHOT_BUILD_CACHE_MAX_ENTRIES)
            return [dict(item) for item in (cached.get("trades") or [])]

    if mt5 is not None and _is_mt5_configured():
        try:
            trades = _snapshot_trades_from_mt5(range_key)
        except Exception:
            trades = [dict(item) for item in ((fallback_snapshot or {}).get("trades") or [])]
    else:
        trades = [dict(item) for item in ((fallback_snapshot or {}).get("trades") or [])]

    with snapshot_cache_lock:
        _remember_cache_entry_locked(snapshot_build_cache, cache_key, {
            "builtAt": now_ms,
            "lastAccessAt": now_ms,
            "trades": trades,
        }, SNAPSHOT_BUILD_CACHE_MAX_ENTRIES)
    return [dict(item) for item in trades]


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
    return {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "pendingOrders": snapshot.get("pendingOrders") or [],
    }


def _build_trades_snapshot(snapshot: Dict) -> Dict:
    return {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "trades": snapshot.get("trades") or [],
    }


def _build_curve_snapshot(snapshot: Dict) -> Dict:
    return {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "curvePoints": snapshot.get("curvePoints") or [],
        "curveIndicators": snapshot.get("curveIndicators") or [],
    }


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
    snapshot = _build_snapshot_with_cache(range_key)
    trades_snapshot = {
        "accountMeta": dict(snapshot.get("accountMeta") or {}),
        "trades": _build_trade_history_with_cache(range_key, snapshot),
    }
    digest = _snapshot_digest(trades_snapshot)
    cache_key = f"{range_key}:trades"

    with snapshot_cache_lock:
        state = snapshot_sync_cache.get(cache_key)
        previous_snapshot: Optional[Dict] = None
        previous_seq = 0

        if state is None:
            sync_seq = 1
            _remember_cache_entry_locked(snapshot_sync_cache, cache_key, {
                "seq": sync_seq,
                "digest": digest,
                "snapshot": trades_snapshot,
                "previousSeq": 0,
                "previousSnapshot": None,
            }, SNAPSHOT_SYNC_CACHE_MAX_ENTRIES)
        else:
            if state.get("digest") == digest:
                sync_seq = int(state.get("seq", 1))
                trades_snapshot = state.get("snapshot") or trades_snapshot
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
                    "snapshot": trades_snapshot,
                    "previousSeq": previous_seq,
                    "previousSnapshot": previous_snapshot,
                }, SNAPSHOT_SYNC_CACHE_MAX_ENTRIES)

    if not SNAPSHOT_DELTA_ENABLED or not delta or since_seq <= 0:
        return _build_trades_response(trades_snapshot, sync_seq)

    meta = dict(trades_snapshot.get("accountMeta") or {})
    meta["syncSeq"] = sync_seq
    meta["deltaEnabled"] = SNAPSHOT_DELTA_ENABLED

    if since_seq == sync_seq:
        return {"accountMeta": meta, "isDelta": True, "unchanged": True}

    if previous_snapshot is None or since_seq < previous_seq:
        return _build_trades_response(trades_snapshot, sync_seq)

    delta_payload = _build_scoped_delta_snapshot(previous_snapshot, trades_snapshot, ["trades"])
    return {
        "accountMeta": meta,
        "isDelta": True,
        "unchanged": False,
        "trades": delta_payload.get("trades", {"upsert": [], "remove": []}),
    }


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
            return _clone_snapshot_payload(ea_snapshot_cache)
        raise RuntimeError("No fresh EA snapshot found. Please start MT5 EA push first.")

    if mode == "pull":
        return _snapshot_from_mt5(range_key)

    # auto mode: 只要 EA 快照仍是新鲜的，就优先复用 EA。
    # 否则高频账户接口会持续触发 MT5 Python Pull，把线程池和 MT5 拉取链路占满，
    # 反过来又拖慢 EA push 的 POST /v1/ea/snapshot。
    if _is_ea_snapshot_fresh():
        return _clone_snapshot_payload(ea_snapshot_cache)

    # 没有新鲜 EA 时，再退回 MT5 pull。
    if mt5 is not None and _is_mt5_configured():
        try:
            return _snapshot_from_mt5(range_key)
        except Exception:
            pass

    if ea_snapshot_cache is not None:
        stale = _clone_snapshot_payload(ea_snapshot_cache)
        stale["accountMeta"]["source"] = f"{stale['accountMeta'].get('source', 'MT5 EA Push')} (stale)"
        return stale

    raise RuntimeError("No available data source. Configure MT5 pull or start EA push.")


# 统一把完整快照裁成账户轻快照，供高频账户同步接口复用。
def _strip_account_light_snapshot(snapshot: Optional[Dict[str, Any]]) -> Dict[str, Any]:
    safe_snapshot = snapshot or {}
    return {
        "accountMeta": dict(safe_snapshot.get("accountMeta") or {}),
        "positions": [dict(item) for item in (safe_snapshot.get("positions") or [])],
        "pendingOrders": [dict(item) for item in (safe_snapshot.get("pendingOrders") or [])],
    }


# 轻快照数据源选择：高频接口只拿账户摘要、持仓和挂单，保留历史接口按需走重快照。
def _build_account_light_snapshot() -> Dict[str, Any]:
    mode = GATEWAY_MODE if GATEWAY_MODE in {"auto", "pull", "ea"} else "auto"

    if mode == "ea":
        if _is_ea_snapshot_fresh():
            return _strip_account_light_snapshot(_clone_snapshot_payload(ea_snapshot_cache))
        raise RuntimeError("No fresh EA snapshot found. Please start MT5 EA push first.")

    if mode == "pull":
        return _snapshot_from_mt5_light()

    if _is_ea_snapshot_fresh():
        return _strip_account_light_snapshot(_clone_snapshot_payload(ea_snapshot_cache))

    if mt5 is not None and _is_mt5_configured():
        try:
            return _snapshot_from_mt5_light()
        except Exception:
            pass

    if ea_snapshot_cache is not None:
        stale = _clone_snapshot_payload(ea_snapshot_cache)
        stale["accountMeta"]["source"] = f"{stale['accountMeta'].get('source', 'MT5 EA Push')} (stale)"
        return _strip_account_light_snapshot(stale)

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
        with urllib.request.urlopen(upstream_request, timeout=25) as upstream_response:
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


# 基于现有快照构建 v2 账户区最小返回体，后续会替换为独立账户真值模块。
def _build_v2_account_section_from_snapshot(snapshot: Dict[str, Any]) -> Dict[str, Any]:
    meta = dict(snapshot.get("accountMeta") or {})
    return {
        "accountMeta": meta,
        "positions": [dict(item) for item in (snapshot.get("positions") or [])],
        "orders": [dict(item) for item in (snapshot.get("pendingOrders") or [])],
    }


# 基于现有配置构建 v2 行情区最小返回体，后续会替换为 Binance 真值聚合结果。
def _build_v2_market_section() -> Dict[str, Any]:
    return {
        "source": "binance",
        "symbols": ["BTCUSDT", "XAUUSDT"],
        "restUpstream": BINANCE_REST_UPSTREAM,
        "wsUpstream": BINANCE_WS_UPSTREAM,
    }


# 从 Binance REST 拉指定周期 K 线原始行，供 v2 行情真值接口使用。
def _fetch_binance_kline_rows(symbol: str,
                             interval: str,
                             limit: int,
                             *,
                             start_time_ms: int = 0,
                             end_time_ms: int = 0) -> List[Any]:
    safe_symbol = str(symbol or "").strip().upper()
    safe_interval = str(interval or "").strip()
    safe_limit = max(1, min(int(limit), 1500))
    query_payload = {
        "symbol": safe_symbol,
        "interval": safe_interval,
        "limit": safe_limit,
    }
    if int(start_time_ms or 0) > 0:
        query_payload["startTime"] = int(start_time_ms)
    if int(end_time_ms or 0) > 0:
        query_payload["endTime"] = int(end_time_ms)
    query = urllib.parse.urlencode(query_payload)
    upstream_url = f"{BINANCE_REST_UPSTREAM}/fapi/v1/klines?{query}"
    request = urllib.request.Request(
        upstream_url,
        headers={
            "User-Agent": "mt5-gateway-v2-market/1.0",
            "Accept-Encoding": "identity",
        },
        method="GET",
    )
    with urllib.request.urlopen(request, timeout=25) as response:
        body = response.read().decode("utf-8")
    payload = json.loads(body or "[]")
    if not isinstance(payload, list):
        raise ValueError("Binance kline payload is not a list")
    return payload


def _fetch_binance_kline_rows_resilient(symbol: str,
                                        interval: str,
                                        limit: int,
                                        *,
                                        start_time_ms: int = 0,
                                        end_time_ms: int = 0) -> List[Any]:
    last_error: Optional[Exception] = None
    for _ in range(max(0, MARKET_CANDLES_UPSTREAM_RETRY) + 1):
        try:
            return _fetch_binance_kline_rows(
                symbol,
                interval,
                limit,
                start_time_ms=start_time_ms,
                end_time_ms=end_time_ms,
            )
        except Exception as exc:
            last_error = exc
    if last_error is not None:
        raise last_error
    return []


# 行情 K 线大窗口查询按块拉取，避免单次 limit 过大把 Binance REST 拖到超时。
def _fetch_market_candle_rows_with_cache(symbol: str,
                                         interval: str,
                                         limit: int,
                                         *,
                                         start_time_ms: int = 0,
                                         end_time_ms: int = 0) -> List[Any]:
    safe_symbol = str(symbol or "").strip().upper()
    safe_interval = str(interval or "").strip()
    safe_limit = max(1, min(int(limit or 0), 1500))
    safe_start = max(0, int(start_time_ms or 0))
    safe_end = max(0, int(end_time_ms or 0))
    cache_key = _build_market_candles_cache_key(
        safe_symbol,
        safe_interval,
        safe_limit,
        safe_start,
        safe_end,
    )
    now_ms = _now_ms()
    cached_rows = _get_cached_market_candle_rows(cache_key, now_ms)
    if cached_rows is not None:
        return cached_rows

    rows = _fetch_market_candle_rows_paged(
        safe_symbol,
        safe_interval,
        safe_limit,
        start_time_ms=safe_start,
        end_time_ms=safe_end,
    )
    _remember_market_candle_rows(cache_key, rows, now_ms)
    return rows


# 按开始/结束时间自动选择向前或向后分页，保证返回仍是同一份升序 K 线列表。
def _fetch_market_candle_rows_paged(symbol: str,
                                    interval: str,
                                    limit: int,
                                    *,
                                    start_time_ms: int = 0,
                                    end_time_ms: int = 0) -> List[Any]:
    safe_limit = max(1, min(int(limit or 0), 1500))
    if safe_limit <= MARKET_CANDLES_UPSTREAM_CHUNK_LIMIT:
        return _fetch_binance_kline_rows_resilient(
            symbol,
            interval,
            safe_limit,
            start_time_ms=start_time_ms,
            end_time_ms=end_time_ms,
        )
    if int(start_time_ms or 0) > 0:
        return _fetch_market_candle_rows_forward(
            symbol,
            interval,
            safe_limit,
            start_time_ms=int(start_time_ms or 0),
            end_time_ms=int(end_time_ms or 0),
        )
    return _fetch_market_candle_rows_backward(
        symbol,
        interval,
        safe_limit,
        end_time_ms=int(end_time_ms or 0),
    )


# 已知 startTime 时从旧到新分页，避免精确窗口查询重复回卷。
def _fetch_market_candle_rows_forward(symbol: str,
                                      interval: str,
                                      limit: int,
                                      *,
                                      start_time_ms: int,
                                      end_time_ms: int = 0) -> List[Any]:
    remaining = max(1, min(int(limit or 0), 1500))
    cursor = max(0, int(start_time_ms or 0))
    safe_end = max(0, int(end_time_ms or 0))
    collected: Dict[int, Any] = {}

    while remaining > 0:
        chunk_limit = min(remaining, MARKET_CANDLES_UPSTREAM_CHUNK_LIMIT)
        rows = _fetch_binance_kline_rows_resilient(
            symbol,
            interval,
            chunk_limit,
            start_time_ms=cursor,
            end_time_ms=safe_end,
        ) or []
        if not rows:
            break
        last_open_time = cursor
        new_count = 0
        for row in rows:
            open_time = _extract_market_row_open_time(row)
            if open_time <= 0:
                continue
            if safe_end > 0 and open_time > safe_end:
                continue
            last_open_time = max(last_open_time, open_time)
            if open_time in collected:
                continue
            collected[open_time] = row
            new_count += 1
        if new_count <= 0:
            break
        remaining -= new_count
        if len(rows) < chunk_limit:
            break
        next_cursor = last_open_time + 1
        if next_cursor <= cursor:
            break
        cursor = next_cursor

    return [collected[key] for key in sorted(collected.keys())]


# 未指定 startTime 时从新到旧分页，再统一升序返回，适合图表整窗历史拉取。
def _fetch_market_candle_rows_backward(symbol: str,
                                       interval: str,
                                       limit: int,
                                       *,
                                       end_time_ms: int = 0) -> List[Any]:
    remaining = max(1, min(int(limit or 0), 1500))
    cursor_end = max(0, int(end_time_ms or 0))
    collected: Dict[int, Any] = {}

    while remaining > 0:
        chunk_limit = min(remaining, MARKET_CANDLES_UPSTREAM_CHUNK_LIMIT)
        rows = _fetch_binance_kline_rows_resilient(
            symbol,
            interval,
            chunk_limit,
            start_time_ms=0,
            end_time_ms=cursor_end,
        ) or []
        if not rows:
            break
        first_open_time: Optional[int] = None
        new_count = 0
        for row in rows:
            open_time = _extract_market_row_open_time(row)
            if open_time <= 0:
                continue
            if first_open_time is None:
                first_open_time = open_time
            else:
                first_open_time = min(first_open_time, open_time)
            if open_time in collected:
                continue
            collected[open_time] = row
            new_count += 1
        if new_count <= 0 or first_open_time is None:
            break
        remaining -= new_count
        if len(rows) < chunk_limit or first_open_time <= 0:
            break
        cursor_end = first_open_time - 1

    return [collected[key] for key in sorted(collected.keys())]


# 统一提取 K 线开盘时间，供分页去重和翻页游标使用。
def _extract_market_row_open_time(row: Any) -> int:
    if isinstance(row, (list, tuple)) and row:
        try:
            return int(row[0] or 0)
        except Exception:
            return 0
    if isinstance(row, dict):
        try:
            return int((row.get("k") or row).get("t") or (row.get("k") or row).get("openTime") or 0)
        except Exception:
            return 0
    return 0


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
        now_ms = _now_ms()
        with snapshot_cache_lock:
            cached_payload = _clone_json_value(health_status_cache.get("payload")) if health_status_cache.get("payload") else None
            cached_built_at = int(health_status_cache.get("builtAt", 0) or 0)
        if cached_payload and (now_ms - cached_built_at) <= HEALTH_CACHE_MS:
            cached_payload["healthCached"] = True
            cached_payload["healthCacheAgeMs"] = max(0, now_ms - cached_built_at)
            return cached_payload

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
            "healthCached": False,
            "healthCacheAgeMs": 0,
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
                    if cached_payload:
                        cached_payload["healthCached"] = True
                        cached_payload["healthCacheAgeMs"] = max(0, now_ms - cached_built_at)
                        cached_payload["warning"] = f"实时健康检查失败，暂时返回最近一次结果：{exc}"
                        return cached_payload
                    info["mt5Connected"] = False
                    info["lastError"] = str(exc)
                finally:
                    _shutdown_mt5()
            else:
                info["mt5Connected"] = False
                info["lastError"] = str(mt5.last_error())
        with snapshot_cache_lock:
            health_status_cache["payload"] = _clone_json_value(info)
            health_status_cache["builtAt"] = now_ms
        return info
    except Exception as exc:
        now_ms = _now_ms()
        with snapshot_cache_lock:
            cached_payload = _clone_json_value(health_status_cache.get("payload")) if health_status_cache.get("payload") else None
            cached_built_at = int(health_status_cache.get("builtAt", 0) or 0)
        if cached_payload:
            cached_payload["healthCached"] = True
            cached_payload["healthCacheAgeMs"] = max(0, now_ms - cached_built_at)
            cached_payload["warning"] = f"实时健康检查失败，暂时返回最近一次结果：{exc}"
            return cached_payload
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
        "healthCacheMs": HEALTH_CACHE_MS,
    }


@app.get("/v2/market/snapshot")
def v2_market_snapshot():
    try:
        now_ms = _now_ms()
        snapshot = _build_account_light_snapshot_with_cache()
        return _build_v2_market_snapshot_payload(
            market=_build_v2_market_section(),
            account=_build_v2_account_section_from_snapshot(snapshot),
            server_time=now_ms,
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v2/market/candles")
def v2_market_candles(symbol: str,
                      interval: str,
                      limit: int = Query(default=300, ge=1, le=1500),
                      startTime: int = Query(default=0, ge=0),
                      endTime: int = Query(default=0, ge=0)):
    try:
        now_ms = _now_ms()
        rest_rows = _fetch_market_candle_rows_with_cache(
            symbol,
            interval,
            limit,
            start_time_ms=startTime,
            end_time_ms=endTime,
        )
        closed_rest_rows, latest_rest_patch = v2_market.separate_closed_rest_rows(rest_rows, now_ms)
        return v2_market.build_market_candles_response(
            symbol=symbol,
            interval=interval,
            server_time=now_ms,
            rest_rows=closed_rest_rows,
            latest_patch=latest_rest_patch,
            patch_source="binance-rest",
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v2/account/snapshot")
def v2_account_snapshot():
    try:
        now_ms = _now_ms()
        snapshot = _build_account_light_snapshot_with_cache()
        account_section = _build_v2_account_section_from_snapshot(snapshot)
        snapshot_model = v2_account.build_account_snapshot_model(
            {
                "metrics": {
                    "balance": (snapshot.get("accountMeta") or {}).get("balance"),
                    "equity": (snapshot.get("accountMeta") or {}).get("equity"),
                    "margin": (snapshot.get("accountMeta") or {}).get("margin"),
                    "freeMargin": (snapshot.get("accountMeta") or {}).get("freeMargin"),
                    "marginLevel": (snapshot.get("accountMeta") or {}).get("marginLevel"),
                    "profit": (snapshot.get("accountMeta") or {}).get("profit"),
                },
                "positions": account_section.get("positions") or [],
                "orders": account_section.get("orders") or [],
            }
        )
        return v2_account.build_account_snapshot_response(
            snapshot_model,
            account_meta={
                **(account_section.get("accountMeta") or {}),
                "serverTime": now_ms,
                "syncToken": _build_sync_token(now_ms, "account-snapshot"),
            },
        )
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v2/account/history")
def v2_account_history(
    range: str = Query(default="all", pattern="^(1d|7d|1m|3m|1y|all)$"),
    cursor: str = Query(default=""),
):
    try:
        now_ms = _now_ms()
        range_key = range.lower()
        snapshot = _build_snapshot_with_cache(range_key)
        history_model = v2_account.build_account_history_model(
            {
                "trades": _build_trade_history_with_cache(range_key, snapshot),
                "orders": snapshot.get("pendingOrders") or [],
                "curvePoints": snapshot.get("curvePoints") or [],
            }
        )
        payload = v2_account.build_account_history_response(
            history_model,
            account_meta={
                **(snapshot.get("accountMeta") or {}),
                "serverTime": now_ms,
                "syncToken": _build_sync_token(now_ms, f"account-history:{range_key}:{cursor}"),
            },
        )
        payload["nextCursor"] = ""
        return payload
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/v2/sync/delta")
def v2_sync_delta(syncToken: str = Query(default="")):
    try:
        now_ms = _now_ms()
        return _build_v2_sync_delta_response(sync_token=syncToken, server_time=now_ms)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc))


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


# 清空运行时缓存，让管理面板可以强制下一轮重新构建快照。
@app.post("/internal/admin/cache/clear")
def admin_cache_clear():
    with snapshot_cache_lock:
        cleared = {
            "snapshotBuildCache": len(snapshot_build_cache),
            "snapshotSyncCache": len(snapshot_sync_cache),
            "marketCandlesCache": len(market_candles_cache),
            "v2SyncState": 1 if v2_sync_state else 0,
            "abnormalSyncState": 1 if abnormal_sync_state else 0,
        }
        snapshot_build_cache.clear()
        snapshot_sync_cache.clear()
        market_candles_cache.clear()
        v2_sync_state.clear()
        abnormal_sync_state.clear()
    return {"ok": True, "cleared": cleared}


@app.websocket("/binance-ws/{path_value:path}")
async def binance_ws_proxy(client: WebSocket, path_value: str):
    await _proxy_binance_websocket(client, path_value)


@app.websocket("/v2/stream")
async def v2_stream(client: WebSocket):
    await client.accept()
    current_sync_token = ""
    try:
        while True:
            now_ms = _now_ms()
            previous_sync_token = current_sync_token
            delta_payload = _build_v2_sync_delta_response(sync_token=previous_sync_token, server_time=now_ms)
            current_sync_token = str(delta_payload.get("nextSyncToken", ""))
            await client.send_json(
                _build_v2_ws_message(
                    message_type=_resolve_v2_stream_message_type(sync_token=previous_sync_token, delta_payload=delta_payload),
                    payload=delta_payload,
                    sync_token=current_sync_token,
                    server_time=now_ms,
                )
            )
            await asyncio.sleep(5)
    except WebSocketDisconnect:
        return
    except Exception as exc:
        try:
            await client.close(code=1011, reason=f"v2 stream failed: {exc}")
        except Exception:
            return


if __name__ == "__main__":
    _configure_windows_event_loop_policy()
    uvicorn.run("server_v2:app", host=HOST, port=PORT, reload=False)
