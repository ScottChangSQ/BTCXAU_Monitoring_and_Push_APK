"""部署口径契约测试。

该文件用于确保部署文档/示例里的关键环境变量和健康检查契约不会回退，
避免“文档写了但服务端静默截断/忽略”或“坏部署仍被健康检查判绿”的情况。
"""

from __future__ import annotations

import re
import shutil
import subprocess
import unittest
from pathlib import Path


SERVER_SOURCE = Path("bridge/mt5_gateway/server_v2.py")


def _read_server_source() -> str:
    """读取服务端源码，供部署契约测试做静态断言。"""
    return SERVER_SOURCE.read_text(encoding="utf-8")


class DeployContractTests(unittest.TestCase):
    def test_gateway_source_should_not_embed_default_mt5_credentials(self):
        """服务端源码不能再内置默认 MT5 凭据，缺配置时必须显式失败。"""
        source = _read_server_source()

        self.assertIn('LOGIN = os.getenv("MT5_LOGIN", "").strip()', source)
        self.assertIn('PASSWORD = os.getenv("MT5_PASSWORD", "").strip()', source)
        self.assertIn('SERVER = os.getenv("MT5_SERVER", "").strip()', source)
        self.assertNotIn('MT5_LOGIN", "7400048"', source)
        self.assertNotIn('MT5_PASSWORD", "_fWsAeW1"', source)
        self.assertNotIn('MT5_SERVER", "ICMarketsSC-MT5-6"', source)

    def test_env_examples_should_define_non_empty_mt5_server_timezone(self):
        """部署样例必须显式给出 MT5_SERVER_TIMEZONE，避免时间轴失真。"""
        example_paths = [
            Path("bridge/mt5_gateway/.env.example"),
            Path("dist/windows_server_bundle/mt5_gateway/.env.example"),
            Path("dist/windows_server_bundle/windows/.env.example"),
        ]

        for example_path in example_paths:
            content = example_path.read_text(encoding="utf-8")
            match = re.search(r"^MT5_SERVER_TIMEZONE=(.+)$", content, re.MULTILINE)
            self.assertIsNotNone(match, f"{example_path} 缺少 MT5_SERVER_TIMEZONE")
            self.assertTrue(match.group(1).strip(), f"{example_path} 的 MT5_SERVER_TIMEZONE 不能为空")

    def test_icmarkets_examples_should_not_hardcode_seoul_timezone(self):
        """ICMarkets 示例时区不能再误写成部署机器时区首尔。"""
        example_paths = [
            Path("bridge/mt5_gateway/.env.example"),
            Path("dist/windows_server_bundle/mt5_gateway/.env.example"),
            Path("dist/windows_server_bundle/windows/.env.example"),
        ]

        for example_path in example_paths:
            content = example_path.read_text(encoding="utf-8")
            self.assertNotIn("MT5_SERVER_TIMEZONE=Asia/Seoul", content, f"{example_path} 仍把首尔时区写成 ICMarkets 示例")
            self.assertIn("MT5_SERVER_TIMEZONE=Europe/Athens", content, f"{example_path} 应改成 ICMarkets 示例时区 Europe/Athens")

    def test_gateway_readme_should_explain_mt5_timezone_is_broker_timezone(self):
        """部署文档必须明确 MT5_SERVER_TIMEZONE 指的是券商服务器时区。"""
        readme_path = Path("bridge/mt5_gateway/README.md")
        content = readme_path.read_text(encoding="utf-8")
        self.assertIn("不是部署机器所在时区", content)
        self.assertIn("Europe/Athens", content)

    def test_gateway_requirements_should_include_tzdata_for_windows_zoneinfo(self):
        """Windows 部署必须显式安装 tzdata，确保 Asia/Seoul 可被 zoneinfo 解析。"""
        requirement_paths = [
            Path("bridge/mt5_gateway/requirements.txt"),
            Path("dist/windows_server_bundle/mt5_gateway/requirements.txt"),
        ]

        for requirement_path in requirement_paths:
            content = requirement_path.read_text(encoding="utf-8")
            self.assertIn("tzdata", content, f"{requirement_path} 缺少 tzdata")

    def test_mt5_init_timeout_ms_should_allow_90000_ms_contract(self):
        """服务端源码必须允许部署契约里的 90000ms 初始化超时。"""
        source = _read_server_source()

        self.assertIn('MT5_INIT_TIMEOUT_MS = _read_bounded_env_int(', source)
        self.assertIn('"MT5_INIT_TIMEOUT_MS"', source)
        self.assertIn("default=50000", source)
        self.assertIn("maximum=120000", source)
        self.assertIn("不应把用户显式配置静默截断到 50s", source)

    def test_health_should_fail_when_mt5_timezone_missing_under_mt5_configuration(self):
        """健康检查源码必须把缺失 MT5_SERVER_TIMEZONE 收口为明确失败。"""
        source = _read_server_source().replace("\r\n", "\n").replace("\r", "\n")

        self.assertIn("def _resolve_gateway_readiness_error() -> str:", source)
        self.assertIn('return "MT5 登录凭据未配置，网关不会连接 MT5。"', source)
        self.assertIn('return "MT5_SERVER_TIMEZONE 未配置，账户接口不可用。"', source)
        self.assertIn('@app.get("/health")', source)
        self.assertIn("readiness_error = _resolve_gateway_readiness_error()", source)
        self.assertIn('info["ok"] = False', source)
        self.assertIn('info["lastError"] = readiness_error', source)

    def test_start_gateway_should_require_full_mt5_identity_env(self):
        """启动脚本必须把 MT5 登录、密码、服务器、时区都视为强依赖。"""
        source = Path("bridge/mt5_gateway/start_gateway.ps1").read_text(encoding="utf-8")

        self.assertIn('Assert-RequiredGatewayEnv -Keys @(', source)
        self.assertIn('"MT5_LOGIN"', source)
        self.assertIn('"MT5_PASSWORD"', source)
        self.assertIn('"MT5_SERVER"', source)
        self.assertIn('"MT5_SERVER_TIMEZONE"', source)

    def test_start_gateway_script_should_parse_under_windows_powershell(self):
        """启动脚本必须能被服务器实际使用的 powershell.exe 解析。"""
        powershell = shutil.which("powershell.exe")
        if not powershell:
            self.skipTest("powershell.exe not available")

        script_path = Path("bridge/mt5_gateway/start_gateway.ps1").resolve()
        command = (
            "$tokens=$null; "
            "$errors=$null; "
            f"[System.Management.Automation.Language.Parser]::ParseFile('{script_path}',[ref]$tokens,[ref]$errors) | Out-Null; "
            "if ($errors.Count -gt 0) { "
            "$errors | ForEach-Object { Write-Error ((\"{0}:{1}\" -f $_.Extent.StartLineNumber, $_.Message)) }; "
            "exit 1 "
            "}"
        )
        result = subprocess.run(
            [powershell, "-NoProfile", "-Command", command],
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )

        self.assertEqual(
            0,
            result.returncode,
            msg=(result.stdout + "\n" + result.stderr).strip(),
        )
