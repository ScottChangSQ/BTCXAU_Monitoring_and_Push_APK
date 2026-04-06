"""v2 会话层需要的加密与时间校验工具。"""

from __future__ import annotations

import hashlib
import os
import platform
import sys
import uuid
from typing import Iterable

_IS_WINDOWS = sys.platform.startswith("win")


if _IS_WINDOWS:
    import ctypes
    from ctypes import wintypes

    class DATA_BLOB(ctypes.Structure):
        _fields_ = [("cbData", wintypes.DWORD), ("pbData", ctypes.POINTER(ctypes.c_byte))]

    _CRYPTPROTECT_LOCAL_MACHINE = 0x4
    _CRYPTPROTECT_UI_FORBIDDEN = 0x1

    _crypt32 = ctypes.windll.crypt32
    _kernel32 = ctypes.windll.kernel32
    _kernel32.LocalFree.argtypes = [wintypes.HLOCAL]
    _kernel32.LocalFree.restype = wintypes.HLOCAL

    def _create_blob(source: bytes) -> tuple[DATA_BLOB, ctypes.Array]:
        buffer = ctypes.create_string_buffer(source, len(source))
        blob = DATA_BLOB()
        blob.cbData = len(source)
        blob.pbData = ctypes.cast(buffer, ctypes.POINTER(ctypes.c_byte))
        return blob, buffer

    def _crypt_protect_data(data: bytes) -> bytes:
        in_blob, _in_buffer = _create_blob(data)
        out_blob = DATA_BLOB()
        flags = _CRYPTPROTECT_LOCAL_MACHINE | _CRYPTPROTECT_UI_FORBIDDEN
        if not _crypt32.CryptProtectData(
            ctypes.byref(in_blob),
            None,
            None,
            None,
            None,
            flags,
            ctypes.byref(out_blob),
        ):
            raise ctypes.WinError()
        try:
            return ctypes.string_at(out_blob.pbData, out_blob.cbData)
        finally:
            _kernel32.LocalFree(out_blob.pbData)

    def _crypt_unprotect_data(data: bytes) -> bytes:
        in_blob, _in_buffer = _create_blob(data)
        out_blob = DATA_BLOB()
        flags = _CRYPTPROTECT_UI_FORBIDDEN
        if not _crypt32.CryptUnprotectData(
            ctypes.byref(in_blob),
            None,
            None,
            None,
            None,
            flags,
            ctypes.byref(out_blob),
        ):
            raise ctypes.WinError()
        try:
            return ctypes.string_at(out_blob.pbData, out_blob.cbData)
        finally:
            _kernel32.LocalFree(out_blob.pbData)


def _normalize_bytes(value: bytes | bytearray) -> bytes:
    """把输入转成不可变字节串。"""
    if isinstance(value, bytearray):
        return bytes(value)
    if isinstance(value, bytes):
        return value
    raise TypeError("secret must be bytes or bytearray")


_DEV_FALLBACK_KEY: bytes | None = None


def _dev_fallback_entropy_key() -> bytes:
    """为测试/开发环境生成的机器特征 key（非生产）。"""
    global _DEV_FALLBACK_KEY
    if _DEV_FALLBACK_KEY is not None:
        return _DEV_FALLBACK_KEY
    components: Iterable[str] = [
        platform.node(),
        str(uuid.getnode()),
        os.environ.get("USER") or os.environ.get("USERNAME") or "",
    ]
    secret_source = "|".join(filter(None, components))
    if not secret_source:
        secret_source = "mt5-session-fallback"
    _DEV_FALLBACK_KEY = hashlib.sha256(secret_source.encode("utf-8")).digest()
    return _DEV_FALLBACK_KEY


def _dev_fallback_transform(data: bytes) -> bytes:
    """仅作为测试/开发用回退，加密强度远低于 DPAPI。"""
    key = _dev_fallback_entropy_key()
    if not key:
        return data
    return bytes(b ^ key[i % len(key)] for i, b in enumerate(data))


def validate_request_time(client_time_ms: int, now_ms: int, allowed_skew_ms: int) -> bool:
    """校验请求时间戳是否在允许的漂移范围内，否则抛出 ValueError。"""
    now = int(now_ms)
    client = int(client_time_ms)
    allowed = int(allowed_skew_ms)
    if allowed < 0:
        raise ValueError("allowed_skew_ms must be non-negative")
    skew = abs(now - client)
    if skew > allowed:
        raise ValueError("request time outside allowed skew")
    return True


def protect_secret_for_machine(secret: bytes | bytearray) -> bytes:
    """为当前机器保护一段秘密数据，返回密文；非 Windows 平台仅用测试兼容回退。"""
    secret_bytes = _normalize_bytes(secret)
    if _IS_WINDOWS:
        return _crypt_protect_data(secret_bytes)
    return _dev_fallback_transform(secret_bytes)


def unprotect_secret_for_machine(ciphertext: bytes | bytearray) -> bytes:
    """恢复 protect_secret_for_machine 的密文，非 Windows 依赖回退转换。"""
    data = _normalize_bytes(ciphertext)
    if _IS_WINDOWS:
        return _crypt_unprotect_data(data)
    return _dev_fallback_transform(data)


def fingerprint_public_key(public_key_pem: str) -> str:
    """根据 PEM 数据生成简洁 fingerprint 供会话层引用。"""
    canonical = public_key_pem.replace("\r", "").replace("\n", "").strip().encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()
