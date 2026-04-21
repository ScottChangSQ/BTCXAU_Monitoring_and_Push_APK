"""部署前一键核对脚本测试。"""

from __future__ import annotations

import io
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from scripts import build_windows_server_bundle as builder
from scripts import check_windows_server_bundle as checker


class CheckWindowsServerBundleTests(unittest.TestCase):
    """验证部署前核对脚本能准确判断本地与线上状态。"""

    def _build_bundle(self) -> Path:
        temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(temp_dir.cleanup)
        output_dir = Path(temp_dir.name) / "windows_server_bundle"
        builder.build_bundle(output_dir)
        return output_dir

    def test_inspect_local_bundle_should_report_in_sync_when_bundle_matches_source(self) -> None:
        output_dir = self._build_bundle()

        result = checker.inspect_local_bundle(output_dir)

        self.assertTrue(result["ok"])
        self.assertEqual([], result["diff"]["changed"])
        self.assertEqual([], result["diff"]["missing"])
        self.assertEqual([], result["diff"]["extra"])
        self.assertTrue(result["expectedFingerprint"])
        self.assertEqual(result["expectedFingerprint"], result["actualFingerprint"])

    def test_inspect_local_bundle_should_report_changed_files_when_bundle_drifted(self) -> None:
        output_dir = self._build_bundle()
        drifted_file = output_dir / "mt5_gateway" / "server_v2.py"
        drifted_file.write_text('print("drifted")\n', encoding="utf-8")

        result = checker.inspect_local_bundle(output_dir)

        self.assertFalse(result["ok"])
        self.assertIn("mt5_gateway/server_v2.py", result["diff"]["changed"])
        self.assertNotEqual(result["expectedFingerprint"], result["actualFingerprint"])

    def test_inspect_remote_bundle_should_report_match_when_remote_fingerprint_matches_dist(self) -> None:
        output_dir = self._build_bundle()
        local_result = checker.inspect_local_bundle(output_dir)
        payload = {
            "bundleFingerprint": local_result["actualFingerprint"],
            "bundleGeneratedAt": "2026-04-18T12:38:13.474106+08:00",
        }
        response = io.BytesIO(json.dumps(payload).encode("utf-8"))

        with mock.patch("urllib.request.urlopen", return_value=response):
            result = checker.inspect_remote_bundle(
                actual_fingerprint=local_result["actualFingerprint"],
                source_url="https://tradeapp.ltd/v1/source",
            )

        self.assertTrue(result["ok"])
        self.assertEqual(payload["bundleFingerprint"], result["remoteFingerprint"])
        self.assertEqual("https://tradeapp.ltd/v1/source", result["sourceUrl"])

    def test_parse_args_should_check_default_remote_source_when_not_skipped(self) -> None:
        args = checker.parse_args(["--dist", "dist/windows_server_bundle"])

        self.assertEqual("dist/windows_server_bundle", args.dist)
        self.assertEqual("https://tradeapp.ltd/v1/source", args.source_url)
        self.assertFalse(args.skip_remote)

    def test_main_should_return_non_zero_when_bundle_is_outdated(self) -> None:
        output_dir = self._build_bundle()
        (output_dir / "mt5_gateway" / "v2_market.py").write_text('print("old bundle")\n', encoding="utf-8")

        with mock.patch("sys.stdout", new_callable=io.StringIO) as stdout:
            exit_code = checker.main(["--dist", str(output_dir)])

        self.assertEqual(1, exit_code)
        self.assertIn("本地源码与部署目录不一致", stdout.getvalue())

    def test_main_should_skip_remote_check_when_skip_remote_flag_is_set(self) -> None:
        output_dir = self._build_bundle()

        with mock.patch.object(checker, "inspect_remote_bundle", side_effect=AssertionError("不应访问远端")), mock.patch(
            "sys.stdout",
            new_callable=io.StringIO,
        ) as stdout:
            exit_code = checker.main(["--dist", str(output_dir), "--skip-remote"])

        self.assertEqual(0, exit_code)
        self.assertNotIn("线上服务器与当前部署目录一致", stdout.getvalue())

    def test_script_entry_should_support_direct_python_execution(self) -> None:
        output_dir = self._build_bundle()
        script_path = Path(__file__).resolve().parents[1] / "check_windows_server_bundle.py"

        result = subprocess.run(
            [sys.executable, str(script_path), "--dist", str(output_dir), "--skip-remote"],
            capture_output=True,
            text=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, msg=result.stderr)
        self.assertIn("本地源码与部署目录一致", result.stdout)


if __name__ == "__main__":
    unittest.main()
