"""v2 会话管理器，负责登录、状态读取与退出流程。"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional


def _now_ms() -> int:
    """返回当前 UTC 毫秒时间戳。"""
    return int(datetime.now(timezone.utc).timestamp() * 1000)


def _mask_login(login: str) -> str:
    """把账号加工成尾号掩码。"""
    login_text = str(login or "")
    return f"****{login_text[-4:]}" if login_text else "****"


def _build_profile_id(login: str, server: str) -> str:
    """按账号和服务器生成稳定 profileId。"""
    server_text = str(server or "").strip().lower().replace(" ", "_")
    normalized = "".join(ch if ch.isalnum() or ch in {"_", "-"} else "_" for ch in server_text)
    return f"acct_{str(login or '').strip()}_{normalized}"


class AccountSessionManager:
    """会话主流程管理器。"""

    def __init__(
        self,
        store: Any,
        gateway: Any,
        now_ms_provider: Optional[Callable[[], int]] = None,
        on_session_changed: Optional[Callable[[str, Optional[Dict[str, Any]]], None]] = None,
    ):
        """注入存储层、网关适配层和时间函数。"""
        self.store = store
        self.gateway = gateway
        self._now_ms_provider = now_ms_provider or _now_ms
        self._on_session_changed = on_session_changed
        self._audit_logs: List[Dict[str, Any]] = []

    def _now_ms(self) -> int:
        """读取当前毫秒时间。"""
        return int(self._now_ms_provider())

    def _append_audit_log(self, action: str, request_id: str, extra: Optional[Dict[str, Any]] = None) -> None:
        """记录简化审计日志，供后续任务扩展。"""
        self._audit_logs.append(
            {
                "action": str(action or ""),
                "requestId": str(request_id or ""),
                "timestampMs": self._now_ms(),
                "extra": dict(extra or {}),
            }
        )
        if len(self._audit_logs) > 200:
            self._audit_logs = self._audit_logs[-200:]

    def _notify_session_changed(self, action: str, profile: Optional[Dict[str, Any]]) -> None:
        """触发会话变更回调，供网关清理运行时状态。"""
        if self._on_session_changed is None:
            return
        self._on_session_changed(str(action or ""), None if profile is None else dict(profile))

    def _build_profile(self, login: str, server: str, active: bool) -> Dict[str, Any]:
        """构造账号摘要结构。"""
        login_text = str(login or "").strip()
        server_text = str(server or "").strip()
        masked = _mask_login(login_text)
        return {
            "profileId": _build_profile_id(login_text, server_text),
            "login": login_text,
            "loginMasked": masked,
            "server": server_text,
            "displayName": f"{server_text} {masked}",
            "active": bool(active),
            "lastSeenMs": self._now_ms(),
        }

    def _to_account_receipt(self, profile: Dict[str, Any]) -> Dict[str, Any]:
        """把档案对象裁剪成接口返回结构。"""
        return {
            "profileId": str(profile.get("profileId") or ""),
            "login": str(profile.get("login") or ""),
            "loginMasked": str(profile.get("loginMasked") or ""),
            "server": str(profile.get("server") or ""),
            "displayName": str(profile.get("displayName") or ""),
        }

    def _rollback_login_after_persist_failed(
        self,
        active_profile: Dict[str, Any],
        request_id: str,
        persist_error: Exception,
    ) -> None:
        """登录后持久化失败时执行补偿，避免留下半成功状态。"""
        rollback_errors: List[str] = []
        try:
            self.store.clear_active_session()
        except Exception as exc:  # pragma: no cover - 回滚失败只做兜底记录
            rollback_errors.append(f"clear_active_session failed: {exc}")
        try:
            self.gateway.logout_mt5()
        except Exception as exc:  # pragma: no cover - 回滚失败只做兜底记录
            rollback_errors.append(f"logout_mt5 failed: {exc}")
        try:
            # 回滚后主动通知会话变更，让上游有机会清理缓存。
            self._notify_session_changed("logout", active_profile)
        except Exception as exc:  # pragma: no cover - 回调失败只做兜底记录
            rollback_errors.append(f"notify_session_changed failed: {exc}")

        self._append_audit_log(
            "login_rollback",
            request_id,
            {
                "profileId": str(active_profile.get("profileId") or ""),
                "error": str(persist_error),
                "rollbackErrors": rollback_errors,
            },
        )

        if rollback_errors:
            raise RuntimeError(
                f"登录持久化失败，且补偿失败：{'; '.join(rollback_errors)}"
            ) from persist_error
        raise persist_error

    def login_new_account(
        self,
        login: str,
        password: str,
        server: str,
        remember: bool,
        request_id: str = "",
    ) -> Dict[str, Any]:
        """登录新账号并切为当前激活会话。"""
        account_meta = self.gateway.login_mt5(login=login, password=password, server=server)
        actual_login = str(account_meta.get("login") or login)
        actual_server = str(account_meta.get("server") or server)
        active_profile = self._build_profile(actual_login, actual_server, active=True)

        try:
            if bool(remember):
                self.store.save_profile(active_profile, password)
            self.store.save_active_session(active_profile)
        except Exception as persist_error:
            self._rollback_login_after_persist_failed(active_profile, request_id, persist_error)

        self._append_audit_log("login", request_id, {"profileId": active_profile["profileId"], "remember": bool(remember)})
        self._notify_session_changed("login", active_profile)

        return {
            "ok": True,
            "state": "activated",
            "requestId": str(request_id or ""),
            "account": self._to_account_receipt(active_profile),
            "message": "登录成功",
        }

    def logout_current_session(self, request_id: str = "") -> Dict[str, Any]:
        """退出当前激活会话并清理激活状态。"""
        active_profile = None
        if hasattr(self.store, "load_active_session"):
            loaded = self.store.load_active_session()
            if isinstance(loaded, dict):
                active_profile = loaded
        self.store.clear_active_session()
        try:
            self.gateway.logout_mt5()
        except Exception as logout_error:
            # 运行态登出失败时，把文件态恢复到原激活账号，避免状态分裂。
            if isinstance(active_profile, dict) and active_profile:
                try:
                    self.store.save_active_session(active_profile)
                except Exception as restore_error:
                    raise RuntimeError(
                        f"MT5 登出失败且会话恢复失败: {restore_error}"
                    ) from logout_error
            raise logout_error
        self._append_audit_log("logout", request_id, {})
        self._notify_session_changed("logout", active_profile)
        return {
            "ok": True,
            "state": "logged_out",
            "requestId": str(request_id or ""),
            "activeAccount": None,
            "message": "已退出当前账号",
        }

    def build_status_payload(self) -> Dict[str, Any]:
        """构建当前会话状态结构。"""
        active = self.store.load_active_session()
        saved_accounts = [self._to_account_receipt(item) for item in self.store.list_profiles()]
        active_account = self._to_account_receipt(active) if isinstance(active, dict) and active else None
        return {
            "ok": True,
            "state": "activated" if active_account else "logged_out",
            "activeAccount": active_account,
            "savedAccounts": saved_accounts,
            "savedAccountCount": len(saved_accounts),
        }
