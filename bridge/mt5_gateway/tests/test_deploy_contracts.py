"""部署口径契约测试。

该文件用于确保部署文档/示例里的关键环境变量能被服务端按原样读取，
避免“文档写了但服务端静默截断/忽略”的情况。
"""

from __future__ import annotations

import importlib
import os
import unittest


class DeployContractTests(unittest.TestCase):
    def test_mt5_init_timeout_ms_should_honor_90000_ms_config(self):
        """部署样例使用 90000ms 时，服务端不应把它截断成 50000ms。"""
        import bridge.mt5_gateway.server_v2 as server_v2

        original_value = os.environ.get("MT5_INIT_TIMEOUT_MS")
        try:
            os.environ["MT5_INIT_TIMEOUT_MS"] = "90000"
            reloaded = importlib.reload(server_v2)
            self.assertEqual(90000, int(getattr(reloaded, "MT5_INIT_TIMEOUT_MS", 0)))
        finally:
            if original_value is None:
                os.environ.pop("MT5_INIT_TIMEOUT_MS", None)
            else:
                os.environ["MT5_INIT_TIMEOUT_MS"] = original_value
            importlib.reload(server_v2)

