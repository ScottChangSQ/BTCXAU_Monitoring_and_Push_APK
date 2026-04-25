"""生成唯一的 Windows 服务器部署包，避免仓库内长期维护重复副本。"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SOURCE_GATEWAY_DIR = ROOT / "bridge" / "mt5_gateway"
SOURCE_WINDOWS_DIR = ROOT / "deploy" / "tencent" / "windows"
DEFAULT_OUTPUT_DIR = ROOT / "dist" / "windows_server_bundle"

GATEWAY_FILES = (
    ".env.example",
    "admin_panel.py",
    "API.md",
    "auth_guard.py",
    "mt5_direct_login.py",
    "mt5_login_probe.py",
    "README.md",
    "requirements.txt",
    "server_v2.py",
    "start_admin_panel.ps1",
    "start_gateway.ps1",
    "v2_account.py",
    "v2_market.py",
    "v2_market_runtime.py",
    "v2_mt5_account_switch.py",
    "v2_session_crypto.py",
    "v2_session_diagnostic.py",
    "v2_session_manager.py",
    "v2_session_models.py",
    "v2_session_store.py",
    "v2_trade.py",
    "v2_trade_audit.py",
    "v2_trade_batch.py",
    "v2_trade_models.py",
    "v2_trade_service.py",
)
GATEWAY_DIRS = ("ea", "static")

WINDOWS_FILES = (
    ".env.example",
    "01_bootstrap_gateway.ps1",
    "02_register_startup_task.ps1",
    "03_run_healthcheck.ps1",
    "04_register_admin_panel_task.ps1",
    "Caddyfile",
    "Caddyfile.example",
    "deploy_bundle.cmd",
    "deploy_bundle.ps1",
    "run_admin_panel.ps1",
    "run_gateway.ps1",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="构建 Windows 服务器部署包。")
    parser.add_argument(
        "--output",
        default=str(DEFAULT_OUTPUT_DIR),
        help="部署包输出目录，默认生成到 dist/windows_server_bundle",
    )
    return parser.parse_args()


def ensure_exists(path: Path) -> None:
    if not path.exists():
        raise FileNotFoundError(f"required source path not found: {path}")


def reset_dir(path: Path) -> None:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def copy_file(source: Path, destination: Path) -> None:
    ensure_exists(source)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, destination)


def normalize_windows_newlines(content: str) -> str:
    """统一转换成 Windows 兼容的 CRLF 换行。"""
    return content.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n")


def normalize_batch_file(destination: Path) -> None:
    """确保批处理文件使用 Windows 兼容的 CRLF 换行。"""
    # 兼容历史上误写入 UTF-8 BOM 的情况，避免 cmd.exe 把首行命令识别异常。
    content = destination.read_text(encoding="utf-8-sig").lstrip("\ufeff")
    normalized = normalize_windows_newlines(content)
    with destination.open("w", encoding="utf-8", newline="") as handle:
        handle.write(normalized)


def normalize_powershell_file(destination: Path) -> None:
    """确保 PowerShell 脚本使用 Windows PowerShell 兼容的 UTF-8 BOM + CRLF。"""
    content = destination.read_text(encoding="utf-8-sig")
    normalized = normalize_windows_newlines(content)
    destination.write_text(normalized, encoding="utf-8-sig", newline="")


def copy_tree(source: Path, destination: Path) -> None:
    ensure_exists(source)
    shutil.copytree(
        source,
        destination,
        dirs_exist_ok=True,
        ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "*.pyo"),
    )


def build_bundle_manifest(output_dir: Path) -> dict:
    """为部署包生成稳定指纹，供运行时和部署验收核对来源。"""
    file_hashes: dict[str, str] = {}
    for path in sorted(output_dir.rglob("*")):
        if not path.is_file():
            continue
        relative_path = path.relative_to(output_dir).as_posix()
        if relative_path == "bundle_manifest.json":
            continue
        file_hashes[relative_path] = hashlib.sha256(path.read_bytes()).hexdigest()
    fingerprint_source = json.dumps(file_hashes, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "bundleName": "windows_server_bundle",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "bundleFingerprint": hashlib.sha256(fingerprint_source).hexdigest(),
        "fileHashes": file_hashes,
    }


def write_bundle_readme(bundle_dir: Path) -> None:
    readme = """# Windows 服务器部署包

这个目录就是要复制到服务器上的唯一部署目录，不需要再上传整个仓库。

## 目录说明

- `mt5_gateway/`
  - MT5 网关主程序、轻量管理面板、静态页面、依赖清单、环境示例、EA 文件
- `windows/`
  - Windows 部署脚本、Caddy 反向代理配置、自启脚本

## 推荐上传路径

```text
C:\\mt5_bundle\\windows_server_bundle
```

上传后目录应类似：

```text
C:\\mt5_bundle
└─ windows_server_bundle
   ├─ mt5_gateway
   ├─ windows
   ├─ deploy_bundle.cmd
   └─ deploy_bundle.ps1
```

## 一键部署

```powershell
双击 deploy_bundle.cmd
```

或：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File C:\\mt5_bundle\\windows_server_bundle\\deploy_bundle.ps1 -Mode Gui -BundleRoot "C:\\mt5_bundle\\windows_server_bundle"
```

它会自动完成：

- 停掉旧计划任务、旧网关、旧管理面板、旧 Caddy / Nginx
- 强制释放 `80 / 443 / 2019 / 8787 / 8788`
- 检查脚本语法
- 补齐 Python 依赖
- 重新注册网关与管理面板计划任务
- 隐藏启动 Caddy
- 自动验收 `8787 / 8788 / 80 / 443(loopback SNI) / 443(public) / /admin/`

服务器端只会弹出一个状态窗口，用来显示步骤成功与否、当前状态和日志。这个窗口可以关闭，关闭后不会影响后台服务继续运行。

## caddy.exe 位置说明

部署包默认不内置 `caddy.exe`，你可以把它放在下列任一位置：

```text
C:\\mt5_bundle\\windows_server_bundle\\windows\\caddy.exe
```

或：

```text
C:\\mt5_bundle\\windows_server_bundle\\caddy.exe
```

或（兼容历史位置）：

```text
C:\\mt5_bundle\\caddy.exe
```

## 重要安全边界

远程账号会话 `/v2/session/*` 必须通过 HTTPS 公网入口开放；默认 HTTP 的 `Caddyfile` 会直接拒绝这些接口。

## 手动启动检查

```powershell
cd C:\\mt5_bundle\\windows_server_bundle\\mt5_gateway
.\\start_gateway.ps1
.\\start_admin_panel.ps1
```

```powershell
cd C:\\mt5_bundle\\windows_server_bundle
.\\windows\\03_run_healthcheck.ps1
Invoke-WebRequest http://127.0.0.1/admin/ -UseBasicParsing
```
"""
    (bundle_dir / "README.md").write_text(readme, encoding="utf-8")


def build_bundle(output_dir: Path) -> Path:
    gateway_dir = output_dir / "mt5_gateway"
    windows_dir = output_dir / "windows"
    reset_dir(output_dir)

    for file_name in GATEWAY_FILES:
        copy_file(SOURCE_GATEWAY_DIR / file_name, gateway_dir / file_name)
    for dir_name in GATEWAY_DIRS:
        copy_tree(SOURCE_GATEWAY_DIR / dir_name, gateway_dir / dir_name)

    for file_name in WINDOWS_FILES:
        source = SOURCE_WINDOWS_DIR / file_name
        if file_name.endswith(".cmd") or file_name.endswith(".ps1") and file_name.startswith("deploy_bundle"):
            destination = output_dir / file_name
            copy_file(source, destination)
        else:
            destination = windows_dir / file_name
            copy_file(source, destination)
        if file_name.endswith(".cmd"):
            normalize_batch_file(destination)
        if file_name.endswith(".ps1"):
            normalize_powershell_file(destination)

    for script in gateway_dir.rglob("*.ps1"):
        normalize_powershell_file(script)
    for script in windows_dir.rglob("*.ps1"):
        normalize_powershell_file(script)

    manifest = build_bundle_manifest(output_dir)
    (output_dir / "bundle_manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    write_bundle_readme(output_dir)
    return output_dir


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output).resolve()
    build_bundle(output_dir)
    print(f"built windows server bundle: {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
