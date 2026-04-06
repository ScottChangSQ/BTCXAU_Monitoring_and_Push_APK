"""v2 会话层使用的领域模型定义。"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Dict, List, Optional


def _current_timestamp_ms() -> int:
    """获取当前时间戳（毫秒）。"""
    return int(datetime.utcnow().timestamp() * 1000)


@dataclass
class PublicKeyInfo:
    """公钥信息的标准表示，供认证与加密链路复用。"""

    key_id: str
    public_key_pem: str
    algorithm: str = "RSA"
    created_at_ms: int = field(default_factory=_current_timestamp_ms)
    metadata: Dict[str, str] = field(default_factory=dict)
    expires_at_ms: Optional[int] = None


@dataclass
class AccountProfile:
    """维护远程 MT5 账号档案时的核心字段。"""

    account_id: str
    mt5_login: int
    nickname: str
    mode: str
    active: bool = False
    owner: str = "unknown"
    public_key: Optional[PublicKeyInfo] = None
    tags: List[str] = field(default_factory=list)
    last_seen_ms: Optional[int] = None


@dataclass
class EncryptedAccountRecord:
    """代表服务端保护后的账号凭据记录。"""

    profile: AccountProfile
    encrypted_payload: bytes
    protected_at_ms: int = field(default_factory=_current_timestamp_ms)
    entropy: Optional[bytes] = None


def summarize_account(profile: AccountProfile) -> Dict[str, str]:
    """生成便于日志与审计的账号摘要。"""
    public_key_id = profile.public_key.key_id if profile.public_key else "none"
    return {
        "accountId": profile.account_id,
        "mt5Login": str(profile.mt5_login),
        "mode": profile.mode,
        "active": str(profile.active),
        "publicKeyId": public_key_id,
    }
