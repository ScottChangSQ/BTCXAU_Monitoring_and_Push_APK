"""独立执行 MT5 登录探针，避免主进程被 MT5 调用永久阻塞。"""

from __future__ import annotations

import json
import sys
from typing import Any, Dict


def _read_payload() -> Dict[str, Any]:
    """从标准输入读取登录探针参数。"""
    raw = sys.stdin.buffer.read()
    if not raw:
        raise ValueError("probe payload is empty")
    payload = json.loads(raw.decode("utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("probe payload must be an object")
    return payload


def _build_error(message: str) -> int:
    """输出统一错误结构并返回失败退出码。"""
    sys.stdout.write(json.dumps({"ok": False, "error": str(message or "")}, ensure_ascii=False))
    sys.stdout.flush()
    return 1


def main() -> int:
    """执行带鉴权初始化并返回 canonical 账号身份。"""
    try:
        payload = _read_payload()
        login_value = int(payload.get("login") or 0)
        password_value = str(payload.get("password") or "")
        server_value = str(payload.get("server") or "").strip()
        path_value = str(payload.get("path") or "").strip()
        timeout_ms = int(payload.get("timeoutMs") or 0)
        if login_value <= 0 or not password_value or not server_value or timeout_ms <= 0:
            return _build_error("probe payload missing login/password/server/timeoutMs")

        try:
            import MetaTrader5 as mt5  # type: ignore
        except Exception as exc:
            return _build_error(f"MetaTrader5 import failed: {exc}")

        kwargs = {"timeout": timeout_ms, "login": login_value, "password": password_value, "server": server_value}
        if path_value:
            kwargs["path"] = path_value
        try:
            initialized = bool(mt5.initialize(**kwargs))
        except TypeError:
            return _build_error("MetaTrader5 initialize does not support authenticated init")
        except Exception as exc:
            return _build_error(f"MetaTrader5 initialize failed: {exc}")
        if not initialized:
            return _build_error(f"MetaTrader5 initialize/login failed: {mt5.last_error()}")

        account = mt5.account_info()
        canonical_login = str(getattr(account, "login", "") or "").strip()
        canonical_server = str(getattr(account, "server", "") or "").strip()
        if not canonical_login or not canonical_server:
            return _build_error(f"MetaTrader5 account_info missing canonical identity: {mt5.last_error()}")

        sys.stdout.write(
            json.dumps(
                {
                    "ok": True,
                    "login": canonical_login,
                    "server": canonical_server,
                },
                ensure_ascii=False,
            )
        )
        sys.stdout.flush()
        return 0
    except Exception as exc:  # pragma: no cover - 兜底保护仅用于返回明确错误
        return _build_error(f"unexpected probe failure: {exc}")


if __name__ == "__main__":
    raise SystemExit(main())
