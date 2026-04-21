"""独立 MT5 GUI 切号主链契约测试。"""

from __future__ import annotations

import itertools
import unittest
from unittest import mock

from bridge.mt5_gateway import v2_mt5_account_switch


def _repeat_clock(*values: int):
    """返回一个稳定时钟，耗尽后保持最后一个时间点。"""
    items = tuple(int(value) for value in values)
    tail = items[-1] if items else 0
    return mock.Mock(side_effect=itertools.chain(items, itertools.repeat(tail)))


def _counting_clock(*values: int, start: int | None = None, step: int = 1000):
    """返回一个持续前进的时钟，避免测试里的轮询链进入死循环。"""
    items = tuple(int(value) for value in values)
    next_start = int(start) if start is not None else (items[-1] if items else 0)
    return mock.Mock(side_effect=itertools.chain(items, itertools.count(next_start, int(step))))


class Mt5AccountSwitchFlowTests(unittest.TestCase):
    def test_should_launch_mt5_when_no_gui_window_exists(self):
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=False),
            launch_terminal=mock.Mock(return_value=None),
            wait_window_ready=mock.Mock(return_value=True),
            attach_terminal=mock.Mock(return_value=True),
            read_account=mock.Mock(
                side_effect=[
                    {"login": "11111111", "server": "Old-Server"},
                    {"login": "12345678", "server": "New-Server"},
                ]
            ),
            perform_switch=mock.Mock(return_value={"ok": False, "error": "Authorization failed"}),
            monotonic_ms=_counting_clock(0, 8000, 8000, 8000, start=8000, step=1000),
            sleep=mock.Mock(),
        )

        result = controller.switch_account(
            login="12345678",
            password="secret",
            server="New-Server",
        )

        self.assertTrue(result["ok"])
        self.assertEqual("switch_succeeded", result["stage"])
        self.assertEqual("11111111", result["baselineAccount"]["login"])
        self.assertEqual("12345678", result["finalAccount"]["login"])
        self.assertEqual("Authorization failed", result["loginError"])
        controller.launch_terminal.assert_called_once_with()
        controller.wait_window_ready.assert_called_once_with(15000)

    def test_should_fail_when_window_not_ready_within_15000ms(self):
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=False),
            launch_terminal=mock.Mock(return_value=None),
            wait_window_ready=mock.Mock(return_value=False),
            attach_terminal=mock.Mock(),
            read_account=mock.Mock(),
            perform_switch=mock.Mock(),
            monotonic_ms=_repeat_clock(0, 15000, 15000, 15000, 15000),
            sleep=mock.Mock(),
        )

        result = controller.switch_account(
            login="12345678",
            password="secret",
            server="New-Server",
        )

        self.assertFalse(result["ok"])
        self.assertEqual("window_not_found_then_window_ready_timeout", result["stage"])
        controller.attach_terminal.assert_not_called()

    def test_should_retry_attach_twice_before_fail(self):
        sleep_mock = mock.Mock()
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=True),
            launch_terminal=mock.Mock(),
            wait_window_ready=mock.Mock(),
            attach_terminal=mock.Mock(side_effect=[False, False, False]),
            read_account=mock.Mock(),
            perform_switch=mock.Mock(),
            monotonic_ms=_repeat_clock(0, 9000, 9000, 9000, 9000, 9000, 9000),
            sleep=sleep_mock,
        )

        result = controller.switch_account(login="12345678", password="secret", server="New-Server")

        self.assertFalse(result["ok"])
        self.assertEqual("attach_failed", result["stage"])
        self.assertEqual(3, controller.attach_terminal.call_count)
        self.assertEqual([mock.call(3.0), mock.call(3.0)], sleep_mock.call_args_list)

    def test_should_stop_attach_retry_when_total_budget_is_exhausted(self):
        timeout_values = []

        def _attach(timeout_ms):
            timeout_values.append(int(timeout_ms))
            return False

        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=True),
            launch_terminal=mock.Mock(),
            wait_window_ready=mock.Mock(),
            attach_terminal=mock.Mock(side_effect=_attach),
            read_account=mock.Mock(),
            perform_switch=mock.Mock(),
            monotonic_ms=_repeat_clock(0, 0, 0, 0, 120001, 120001),
            sleep=mock.Mock(),
        )

        result = controller.switch_account(login="12345678", password="secret", server="New-Server")

        self.assertFalse(result["ok"])
        self.assertEqual("attach_failed", result["stage"])
        self.assertEqual([20000], timeout_values)
        controller.sleep.assert_not_called()

    def test_should_cap_single_attach_attempt_budget_and_preserve_failure_message(self):
        timeout_values = []

        def _attach(timeout_ms):
            timeout_values.append(int(timeout_ms))
            return {
                "ok": False,
                "message": f"attach timeout={timeout_ms}",
                "detail": {"timeoutMs": int(timeout_ms)},
            }

        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=True),
            launch_terminal=mock.Mock(),
            wait_window_ready=mock.Mock(),
            attach_terminal=mock.Mock(side_effect=_attach),
            read_account=mock.Mock(),
            perform_switch=mock.Mock(),
            monotonic_ms=_repeat_clock(0, 0, 0, 0, 0, 0, 0, 0),
            sleep=mock.Mock(),
        )

        result = controller.switch_account(login="12345678", password="secret", server="New-Server")

        self.assertFalse(result["ok"])
        self.assertEqual("attach_failed", result["stage"])
        self.assertEqual([20000, 20000, 20000], timeout_values)
        self.assertIn("attach timeout=20000", result["message"])

    def test_should_fail_when_baseline_account_missing(self):
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=True),
            launch_terminal=mock.Mock(),
            wait_window_ready=mock.Mock(),
            attach_terminal=mock.Mock(return_value=True),
            read_account=mock.Mock(return_value=None),
            perform_switch=mock.Mock(),
            monotonic_ms=_repeat_clock(0, 6000, 6000, 6000, 6000, 6000),
            sleep=mock.Mock(),
        )

        result = controller.switch_account(login="12345678", password="secret", server="New-Server")

        self.assertFalse(result["ok"])
        self.assertEqual("baseline_account_read_failed", result["stage"])

    def test_should_fail_when_switch_call_raises(self):
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=True),
            launch_terminal=mock.Mock(),
            wait_window_ready=mock.Mock(),
            attach_terminal=mock.Mock(return_value=True),
            read_account=mock.Mock(return_value={"login": "11111111", "server": "Old-Server"}),
            perform_switch=mock.Mock(side_effect=RuntimeError("IPC timeout")),
            monotonic_ms=_repeat_clock(0, 5000, 5000, 5000, 5000, 5000, 5000),
            sleep=mock.Mock(),
        )

        result = controller.switch_account(login="12345678", password="secret", server="New-Server")

        self.assertFalse(result["ok"])
        self.assertEqual("switch_call_exception", result["stage"])
        self.assertIn("IPC timeout", result["message"])

    def test_should_fail_when_final_account_read_missing(self):
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=True),
            launch_terminal=mock.Mock(),
            wait_window_ready=mock.Mock(),
            attach_terminal=mock.Mock(return_value=True),
            read_account=mock.Mock(side_effect=[{"login": "11111111", "server": "Old-Server"}, None]),
            perform_switch=mock.Mock(return_value={"ok": False, "error": "Authorization failed"}),
            monotonic_ms=_repeat_clock(0, 1000, 2000, 2000, 2000, 2000, 2000),
            sleep=mock.Mock(),
        )

        result = controller.switch_account(login="12345678", password="secret", server="New-Server")

        self.assertFalse(result["ok"])
        self.assertEqual("final_account_read_failed", result["stage"])
        self.assertEqual("11111111", result["lastObservedAccount"]["login"])

    def test_should_timeout_with_login_error_and_last_observed_account(self):
        sleep_mock = mock.Mock()
        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=True),
            launch_terminal=mock.Mock(),
            wait_window_ready=mock.Mock(),
            attach_terminal=mock.Mock(return_value=True),
            read_account=mock.Mock(
                side_effect=itertools.chain(
                    [{"login": "11111111", "server": "Old-Server"}],
                    itertools.repeat({"login": "11111111", "server": "Old-Server"}),
                )
            ),
            perform_switch=mock.Mock(return_value={"ok": False, "error": "Authorization failed"}),
            monotonic_ms=_counting_clock(0, 5000, 5001, 28000, 36001, start=36001, step=5000),
            sleep=sleep_mock,
        )

        result = controller.switch_account(login="12345678", password="secret", server="New-Server")

        self.assertFalse(result["ok"])
        self.assertEqual("switch_timeout_account_not_changed", result["stage"])
        self.assertEqual("Authorization failed", result["loginError"])
        self.assertEqual("11111111", result["lastObservedAccount"]["login"])
        self.assertIn("Authorization failed", result["message"])

    def test_should_emit_realtime_stage_events_during_switch_flow(self):
        events = []

        def _report_stage(stage, status, message, detail):
            events.append(
                {
                    "stage": stage,
                    "status": status,
                    "message": message,
                    "detail": dict(detail or {}),
                }
            )

        controller = v2_mt5_account_switch.Mt5GuiController(
            detect_window=mock.Mock(return_value=True),
            launch_terminal=mock.Mock(),
            wait_window_ready=mock.Mock(),
            attach_terminal=mock.Mock(return_value=True),
            read_account=mock.Mock(
                side_effect=[
                    {"login": "11111111", "server": "Old-Server"},
                    {"login": "12345678", "server": "New-Server"},
                ]
            ),
            perform_switch=mock.Mock(return_value={"ok": True, "error": ""}),
            monotonic_ms=_counting_clock(0, 0, 1000, 1000, 2000, 2000, 3000, 4000, start=4000, step=1000),
            sleep=mock.Mock(),
            report_stage=_report_stage,
        )

        result = controller.switch_account(login="12345678", password="secret", server="New-Server")

        self.assertTrue(result["ok"])
        self.assertTrue(any(item["stage"] == "attach_attempt_start" for item in events))
        self.assertTrue(any(item["stage"] == "switch_call_start" for item in events))
        self.assertTrue(any(item["stage"] == "final_account_poll_start" for item in events))
        self.assertTrue(any(item["stage"] == "final_account_confirmed" for item in events))


if __name__ == "__main__":
    unittest.main()
