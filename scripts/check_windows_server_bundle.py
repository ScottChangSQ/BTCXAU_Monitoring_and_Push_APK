"""部署前一键核对 Windows 服务器部署包。"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import tempfile
import urllib.request
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts import build_windows_server_bundle as builder

DEFAULT_SOURCE_URL = "https://tradeapp.ltd/v1/source"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    """解析部署包核对脚本参数。"""
    parser = argparse.ArgumentParser(description="部署前一键核对 Windows 服务器部署包。")
    parser.add_argument(
        "--dist",
        default=str(builder.DEFAULT_OUTPUT_DIR),
        help="要检查的部署目录，默认是 dist/windows_server_bundle",
    )
    parser.add_argument(
        "--source-url",
        default=DEFAULT_SOURCE_URL,
        help="线上 source 接口，默认是 https://tradeapp.ltd/v1/source",
    )
    parser.add_argument(
        "--skip-remote",
        action="store_true",
        help="只做本地源码和部署目录核对，跳过线上版本检查",
    )
    return parser.parse_args(argv)


def load_manifest(path: Path) -> dict[str, Any]:
    """读取 bundle manifest。"""
    return json.loads(path.read_text(encoding="utf-8"))


def build_expected_manifest() -> dict[str, Any]:
    """基于当前源码临时构建一份期望部署包并返回 manifest。"""
    with tempfile.TemporaryDirectory() as temp_dir:
        output_dir = Path(temp_dir) / "windows_server_bundle"
        builder.build_bundle(output_dir)
        return load_manifest(output_dir / "bundle_manifest.json")


def compute_manifest_from_bundle_dir(bundle_dir: Path) -> dict[str, Any]:
    """按当前部署指纹口径实时计算 bundle manifest。"""
    file_hashes: dict[str, str] = {}
    for path in sorted(bundle_dir.rglob("*")):
        if not path.is_file():
            continue
        relative_path = path.relative_to(bundle_dir).as_posix()
        if relative_path in {"bundle_manifest.json", "README.md"}:
            continue
        file_hashes[relative_path] = hashlib.sha256(path.read_bytes()).hexdigest()
    fingerprint_source = json.dumps(file_hashes, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "bundleName": "windows_server_bundle",
        "bundleFingerprint": hashlib.sha256(fingerprint_source).hexdigest(),
        "fileHashes": file_hashes,
    }


def build_actual_manifest(bundle_dir: Path) -> dict[str, Any]:
    """基于当前部署目录实时计算 manifest，避免依赖旧指纹文件。"""
    return compute_manifest_from_bundle_dir(bundle_dir)


def compare_file_hashes(expected: dict[str, str], actual: dict[str, str]) -> dict[str, list[str]]:
    """比较期望部署包与当前部署目录的文件差异。"""
    expected_paths = set(expected.keys())
    actual_paths = set(actual.keys())
    changed = sorted(path for path in expected_paths & actual_paths if expected[path] != actual[path])
    missing = sorted(expected_paths - actual_paths)
    extra = sorted(actual_paths - expected_paths)
    return {
        "changed": changed,
        "missing": missing,
        "extra": extra,
    }


def inspect_local_bundle(bundle_dir: Path) -> dict[str, Any]:
    """检查本地源码和部署目录是否一致。"""
    if not bundle_dir.exists():
        return {
            "ok": False,
            "bundleDir": str(bundle_dir),
            "error": f"部署目录不存在: {bundle_dir}",
            "expectedFingerprint": "",
            "actualFingerprint": "",
            "diff": {"changed": [], "missing": [], "extra": []},
        }
    expected_manifest = build_expected_manifest()
    actual_manifest = build_actual_manifest(bundle_dir)
    diff = compare_file_hashes(expected_manifest["fileHashes"], actual_manifest["fileHashes"])
    return {
        "ok": not diff["changed"] and not diff["missing"] and not diff["extra"],
        "bundleDir": str(bundle_dir),
        "error": "",
        "expectedFingerprint": expected_manifest["bundleFingerprint"],
        "actualFingerprint": actual_manifest["bundleFingerprint"],
        "diff": diff,
    }


def inspect_remote_bundle(actual_fingerprint: str, source_url: str) -> dict[str, Any]:
    """检查线上 source 返回的指纹是否与当前部署目录一致。"""
    try:
        response = urllib.request.urlopen(source_url, timeout=10)
        payload = json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        return {
            "ok": False,
            "sourceUrl": source_url,
            "remoteFingerprint": "",
            "remoteGeneratedAt": "",
            "error": str(exc),
        }
    remote_fingerprint = str(payload.get("bundleFingerprint", "") or "")
    return {
        "ok": bool(remote_fingerprint) and remote_fingerprint == actual_fingerprint,
        "sourceUrl": source_url,
        "remoteFingerprint": remote_fingerprint,
        "remoteGeneratedAt": str(payload.get("bundleGeneratedAt", "") or ""),
        "error": "",
    }


def print_local_report(result: dict[str, Any]) -> None:
    """输出本地部署目录核对结果。"""
    if result["ok"]:
        print("本地源码与部署目录一致")
        print(f"部署目录: {result['bundleDir']}")
        print(f"bundleFingerprint: {result['actualFingerprint']}")
        return
    print("本地源码与部署目录不一致")
    print(f"部署目录: {result['bundleDir']}")
    if result["error"]:
        print(f"错误: {result['error']}")
        return
    print(f"期望指纹: {result['expectedFingerprint']}")
    print(f"当前指纹: {result['actualFingerprint']}")
    for label, key in (("内容变更", "changed"), ("缺少文件", "missing"), ("多余文件", "extra")):
        paths = result["diff"][key]
        if not paths:
            continue
        print(f"{label}:")
        for path in paths:
            print(f"- {path}")


def print_remote_report(result: dict[str, Any]) -> None:
    """输出线上指纹核对结果。"""
    if result["ok"]:
        print("线上服务器与当前部署目录一致")
        print(f"sourceUrl: {result['sourceUrl']}")
        print(f"bundleFingerprint: {result['remoteFingerprint']}")
        return
    print("线上服务器与当前部署目录不一致")
    print(f"sourceUrl: {result['sourceUrl']}")
    if result["error"]:
        print(f"错误: {result['error']}")
        return
    print(f"线上指纹: {result['remoteFingerprint']}")
    if result["remoteGeneratedAt"]:
        print(f"线上生成时间: {result['remoteGeneratedAt']}")


def main(argv: list[str] | None = None) -> int:
    """执行部署前一键核对。"""
    args = parse_args(argv)
    local_result = inspect_local_bundle(Path(args.dist))
    print_local_report(local_result)

    overall_ok = local_result["ok"]
    if not args.skip_remote:
        remote_result = inspect_remote_bundle(local_result["actualFingerprint"], args.source_url)
        print("")
        print_remote_report(remote_result)
        overall_ok = overall_ok and remote_result["ok"]
    return 0 if overall_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
