"""网关统一鉴权守卫。"""

from __future__ import annotations

import os
from typing import Any

try:
    from fastapi import HTTPException
except Exception:  # pragma: no cover
    HTTPException = None  # type: ignore

AUTH_ENV_NAME = "GATEWAY_AUTH_TOKEN"
AUTH_OPTIONAL_ENV_NAME = "GATEWAY_AUTH_OPTIONAL"


def _configured_token() -> str:
    return str(os.getenv(AUTH_ENV_NAME, "") or "").strip()


def _auth_optional() -> bool:
    value = str(os.getenv(AUTH_OPTIONAL_ENV_NAME, "") or "").strip().lower()
    return value in {"1", "true", "yes", "on"}


def _extract_bearer(value: str) -> str:
    text = str(value or "").strip()
    if text.lower().startswith("bearer "):
        return text[7:].strip()
    return text


def _header(connection: Any, name: str) -> str:
    headers = getattr(connection, "headers", None)
    if headers is None:
        return ""
    try:
        return str(headers.get(name, "") or "").strip()
    except Exception:
        return ""


def _query(connection: Any, name: str) -> str:
    params = getattr(connection, "query_params", None)
    if params is None:
        return ""
    try:
        return str(params.get(name, "") or "").strip()
    except Exception:
        return ""


def _matches(candidate: str) -> bool:
    token = _configured_token()
    if not token:
        return _auth_optional()
    return str(candidate or "").strip() == token


def require_http_auth(request: Any) -> None:
    """校验 HTTP 请求鉴权，失败时抛出 401。"""
    if (
        _matches(_header(request, "x-gateway-token"))
        or _matches(_extract_bearer(_header(request, "authorization")))
        or _matches(_query(request, "token"))
    ):
        return
    if HTTPException is None:  # pragma: no cover
        raise RuntimeError("Gateway authentication required")
    raise HTTPException(status_code=401, detail="Gateway authentication required")


def verify_ws_auth(websocket: Any) -> bool:
    """校验 WebSocket 请求鉴权。"""
    return (
        _matches(_header(websocket, "x-gateway-token"))
        or _matches(_extract_bearer(_header(websocket, "authorization")))
        or _matches(_query(websocket, "token"))
    )


def should_protect_http_path(path: str) -> bool:
    """只保护账户、会话、交易、异常与内部运行态接口。"""
    safe_path = str(path or "").strip()
    return (
        safe_path.startswith("/v2/session/")
        or safe_path.startswith("/v2/account/")
        or safe_path.startswith("/v2/trade/")
        or safe_path.startswith("/v2/abnormal/")
        or safe_path.startswith("/internal/")
    )
