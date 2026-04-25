"""v2 单笔交易服务层。

该模块承接单笔交易提交的幂等、预检、发送和未知结果语义，
server_v2 只负责路由、审计和运行态副作用编排。
"""

from __future__ import annotations

from typing import Any, Callable, Dict, Mapping, Optional

import v2_trade
import v2_trade_models


def _existing_response(existing_entry: Optional[Mapping[str, Any]]) -> Dict[str, Any]:
    if not isinstance(existing_entry, Mapping):
        return {}
    response = existing_entry.get("response")
    return dict(response) if isinstance(response, Mapping) else {}


def _build_response(*,
                    request_id: str,
                    action: str,
                    account_mode: str,
                    status: str,
                    error: Optional[Dict[str, Any]],
                    check: Optional[Dict[str, Any]],
                    result: Optional[Dict[str, Any]],
                    server_time: int,
                    idempotent: bool) -> Dict[str, Any]:
    return v2_trade_models.build_trade_submit_response(
        request_id=request_id,
        action=action,
        account_mode=account_mode,
        status=status,
        error=error,
        check=check,
        result=result,
        server_time=server_time,
        idempotent=idempotent,
    )


def _result(command: Dict[str, Any],
            command_digest: str,
            response: Dict[str, Any],
            audit_message: str,
            invalidate_account_runtime: bool) -> Dict[str, Any]:
    return {
        "command": dict(command),
        "commandDigest": str(command_digest or ""),
        "response": dict(response),
        "auditMessage": str(audit_message or ""),
        "invalidateAccountRuntime": bool(invalidate_account_runtime),
    }


def submit_single_trade(*,
                        payload: Optional[Mapping[str, Any]],
                        account_mode: str,
                        server_time: int,
                        prepare_command: Callable[[Dict[str, Any], str], Dict[str, Any]],
                        command_digest: Callable[[Dict[str, Any]], str],
                        existing_entry_lookup: Callable[[str], Optional[Dict[str, Any]]],
                        result_store: Callable[[str, str, Dict[str, Any]], None],
                        check_request: Callable[[Dict[str, Any]], Any],
                        send_request: Callable[[Dict[str, Any]], Any]) -> Dict[str, Any]:
    """提交单笔交易并返回路由层需要的响应和审计上下文。"""
    safe_payload = dict(payload or {})
    prepared = prepare_command(safe_payload, account_mode)
    command = prepared.get("command") if isinstance(prepared.get("command"), dict) else v2_trade.normalize_trade_payload(safe_payload)
    request_id = str(command.get("requestId") or "")
    action = str(command.get("action") or "")
    digest = command_digest(command)

    existing_entry = existing_entry_lookup(request_id)
    if existing_entry is not None:
        existing_digest = str(existing_entry.get("payloadDigest") or "")
        previous = _existing_response(existing_entry)
        if existing_digest and existing_digest != digest:
            response = _build_response(
                request_id=request_id,
                action=action,
                account_mode=str(previous.get("accountMode") or account_mode),
                status=v2_trade_models.STATUS_FAILED,
                error=v2_trade_models.build_error(
                    v2_trade_models.ERROR_DUPLICATE_PAYLOAD_MISMATCH,
                    "相同 requestId 的 payload 不一致",
                ),
                check=previous.get("check"),
                result=previous.get("result"),
                server_time=server_time,
                idempotent=True,
            )
            return _result(command, digest, response, "交易失败", False)
        response = _build_response(
            request_id=request_id,
            action=action,
            account_mode=str(previous.get("accountMode") or account_mode),
            status=v2_trade_models.STATUS_DUPLICATE,
            error=v2_trade_models.build_error(
                v2_trade_models.ERROR_DUPLICATE,
                "重复 requestId，已按幂等返回",
            ),
            check=previous.get("check"),
            result=previous.get("result"),
            server_time=server_time,
            idempotent=True,
        )
        return _result(command, digest, response, "重复 requestId，已按幂等返回", False)

    prepare_error = prepared.get("error")
    if prepare_error is not None:
        response = _build_response(
            request_id=request_id,
            action=action,
            account_mode=account_mode,
            status=v2_trade_models.STATUS_FAILED,
            error=prepare_error,
            check=None,
            result=None,
            server_time=server_time,
            idempotent=False,
        )
        result_store(request_id, digest, response)
        return _result(command, digest, response, "交易失败", False)

    request_payload = prepared.get("request") if isinstance(prepared.get("request"), dict) else {}
    try:
        check_result = v2_trade_models.mt5_result_to_dict(check_request(request_payload))
    except Exception as exc:
        response = _build_response(
            request_id=request_id,
            action=action,
            account_mode=account_mode,
            status=v2_trade_models.STATUS_FAILED,
            error=v2_trade_models.build_error(v2_trade_models.ERROR_TIMEOUT, str(exc)),
            check=None,
            result=None,
            server_time=server_time,
            idempotent=False,
        )
        result_store(request_id, digest, response)
        return _result(command, digest, response, str(exc), False)

    check_error = v2_trade_models.error_from_retcode(
        check_result.get("retcode", -1),
        check_result.get("comment", ""),
    )
    if check_error is not None:
        response = _build_response(
            request_id=request_id,
            action=action,
            account_mode=account_mode,
            status=v2_trade_models.STATUS_FAILED,
            error=check_error,
            check=check_result,
            result=None,
            server_time=server_time,
            idempotent=False,
        )
        result_store(request_id, digest, response)
        return _result(command, digest, response, "交易失败", False)

    try:
        send_result = v2_trade_models.mt5_result_to_dict(send_request(request_payload))
    except Exception as exc:
        response = _build_response(
            request_id=request_id,
            action=action,
            account_mode=account_mode,
            status=v2_trade_models.STATUS_PENDING_CONFIRMATION,
            error=v2_trade_models.build_error(v2_trade_models.ERROR_RESULT_UNKNOWN, str(exc)),
            check=check_result,
            result=None,
            server_time=server_time,
            idempotent=False,
        )
        result_store(request_id, digest, response)
        return _result(command, digest, response, str((response.get("error") or {}).get("message") or str(exc)), False)

    send_error = v2_trade_models.error_from_retcode(
        send_result.get("retcode", -1),
        send_result.get("comment", ""),
    )
    response = _build_response(
        request_id=request_id,
        action=action,
        account_mode=account_mode,
        status=v2_trade_models.STATUS_ACCEPTED if send_error is None else v2_trade_models.STATUS_FAILED,
        error=send_error,
        check=check_result,
        result=send_result,
        server_time=server_time,
        idempotent=False,
    )
    result_store(request_id, digest, response)
    return _result(
        command,
        digest,
        response,
        "交易已受理" if send_error is None else str((response.get("error") or {}).get("message") or "交易失败"),
        send_error is None,
    )
