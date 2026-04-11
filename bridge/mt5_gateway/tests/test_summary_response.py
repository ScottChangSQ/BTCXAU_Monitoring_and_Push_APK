"""MT5 网关摘要响应测试。"""

from contextlib import contextmanager
import sys
import types
import unittest
from datetime import datetime, timezone
from pathlib import Path
from threading import Event, Thread
from unittest import mock


def _install_test_stubs():
    """为网关测试注入最小依赖桩，避免本地缺少服务端依赖时无法导入模块。"""
    if "uvicorn" not in sys.modules:
        uvicorn_stub = types.ModuleType("uvicorn")
        uvicorn_stub.run = lambda *args, **kwargs: None
        sys.modules["uvicorn"] = uvicorn_stub

    if "dotenv" not in sys.modules:
        dotenv_stub = types.ModuleType("dotenv")
        dotenv_stub.load_dotenv = lambda *args, **kwargs: None
        sys.modules["dotenv"] = dotenv_stub

    if "fastapi" not in sys.modules:
        fastapi_stub = types.ModuleType("fastapi")

        class _FastAPI:
            def __init__(self, *args, **kwargs):
                self.args = args
                self.kwargs = kwargs

            def add_middleware(self, *args, **kwargs):
                return None

            def get(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator

            def post(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator

            def websocket(self, *args, **kwargs):
                def decorator(func):
                    return func

                return decorator


        class _HTTPException(Exception):
            def __init__(self, status_code=500, detail=""):
                super().__init__(detail)
                self.status_code = status_code
                self.detail = detail

        fastapi_stub.FastAPI = _FastAPI
        fastapi_stub.HTTPException = _HTTPException
        fastapi_stub.Header = lambda default=None, **kwargs: default
        fastapi_stub.Query = lambda default=None, **kwargs: default
        fastapi_stub.Request = type("Request", (), {})
        fastapi_stub.Response = type("Response", (), {})
        fastapi_stub.WebSocket = type("WebSocket", (), {})
        fastapi_stub.WebSocketDisconnect = Exception
        sys.modules["fastapi"] = fastapi_stub

    if "fastapi.middleware.gzip" not in sys.modules:
        gzip_stub = types.ModuleType("fastapi.middleware.gzip")

        class _GZipMiddleware:
            pass

        gzip_stub.GZipMiddleware = _GZipMiddleware
        sys.modules["fastapi.middleware.gzip"] = gzip_stub

    if "websockets" not in sys.modules:
        websockets_stub = types.ModuleType("websockets")
        websockets_stub.connect = lambda *args, **kwargs: None
        sys.modules["websockets"] = websockets_stub


_install_test_stubs()

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import server_v2  # noqa: E402


@contextmanager
def _configured_mt5_server_timezone(timezone_name: str = "UTC"):
    """在测试里显式声明 MT5 服务器时区，避免依赖空配置。"""
    original_offset = getattr(server_v2, "MT5_TIME_OFFSET_MINUTES", 0)
    original_timezone = getattr(server_v2, "MT5_SERVER_TIMEZONE", "")
    try:
        server_v2.MT5_TIME_OFFSET_MINUTES = 0
        server_v2.MT5_SERVER_TIMEZONE = timezone_name
        yield
    finally:
        server_v2.MT5_TIME_OFFSET_MINUTES = original_offset
        server_v2.MT5_SERVER_TIMEZONE = original_timezone


class SummaryResponseTests(unittest.TestCase):
    """验证后台摘要响应会去掉大体积列表字段。"""

    def test_build_overview_uses_balance_equity_and_mt5_margin_fields(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def account_info():
                return types.SimpleNamespace(
                    equity=1250.0,
                    balance=1200.0,
                    margin=150.0,
                    margin_free=1100.0,
                    leverage=100,
                    margin_level=833.33,
                )

        server_v2.mt5 = _FakeMt5()
        positions = [
            {"marketValue": 3000.0, "totalPnL": 50.0, "dayPnL": 20.0},
            {"marketValue": 1000.0, "totalPnL": -10.0, "dayPnL": -5.0},
        ]
        trades = [
            {"timestamp": server_v2._now_ms(), "fee": 3.0},
        ]
        try:
            overview = server_v2._build_overview(positions, trades)
        finally:
            server_v2.mt5 = original_mt5

        values = {item["name"]: item["value"] for item in overview}
        self.assertEqual("$1,200.00", values["Total Asset"])
        self.assertEqual("$1,250.00", values["Current Equity"])
        self.assertEqual("$150.00", values["Margin"])
        self.assertEqual("$1,100.00", values["Free Fund"])

    def test_build_overview_uses_account_cumulative_pnl_instead_of_position_only(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def account_info():
                return types.SimpleNamespace(
                    equity=1184.0,
                    balance=1084.0,
                    margin=150.0,
                    margin_free=934.0,
                    leverage=100,
                    margin_level=789.33,
                )

        server_v2.mt5 = _FakeMt5()
        positions = [
            {"marketValue": 2500.0, "totalPnL": 80.0, "dayPnL": 20.0},
            {"marketValue": 1200.0, "totalPnL": 20.0, "dayPnL": 5.0},
        ]
        trades = [
            {"timestamp": server_v2._now_ms(), "profit": 60.0, "fee": -3.0, "storageFee": -2.0},
            {"timestamp": server_v2._now_ms(), "profit": 30.0, "fee": -1.0, "storageFee": 0.0},
        ]
        try:
            overview = server_v2._build_overview(positions, trades)
        finally:
            server_v2.mt5 = original_mt5

        values = {item["name"]: item["value"] for item in overview}
        self.assertEqual("+$184.00", values["Cumulative PnL"])
        self.assertEqual("+18.40%", values["Total Return"])
        self.assertEqual("+9.23%", values["Position Return"])

    def test_build_snapshot_includes_account_meta_margin_fields_for_v2_account(self):
        original_mt5 = server_v2.mt5
        original_map_positions = server_v2._map_positions
        original_map_pending_orders = server_v2._map_pending_orders
        original_progressive_history = server_v2._progressive_trade_history_deals
        original_build_curve_from_deals = getattr(server_v2, "_build_curve_from_deals", None)
        original_map_trade_deals = server_v2._map_trade_deals
        original_build_overview = server_v2._build_overview
        original_curve_indicators = server_v2._curve_indicators
        original_build_stats = server_v2._build_stats
        original_ensure_mt5 = server_v2._ensure_mt5
        original_shutdown = server_v2._shutdown_mt5

        class _FakeMt5:
            @staticmethod
            def account_info():
                return types.SimpleNamespace(
                    login=7400048,
                    server="ICMarketsSC-MT5-6",
                    currency="USD",
                    leverage=200,
                    name="demo",
                    company="demo-company",
                    balance=1200.0,
                    equity=1255.0,
                    margin=150.0,
                    margin_free=1105.0,
                    margin_level=836.66,
                    profit=55.0,
                )

        server_v2.mt5 = _FakeMt5()
        server_v2._map_positions = lambda: []
        server_v2._map_pending_orders = lambda: []
        server_v2._progressive_trade_history_deals = lambda range_key: []
        server_v2._build_curve_from_deals = lambda deals, current_positions=None, account=None: [
            {"timestamp": 1, "equity": 1255.0, "balance": 1200.0, "positionRatio": 0.0}
        ]
        server_v2._map_trade_deals = lambda deals: []
        server_v2._build_overview = lambda positions, trades: []
        server_v2._curve_indicators = lambda points: []
        server_v2._build_stats = lambda positions, trades, points: []
        server_v2._ensure_mt5 = lambda: None
        server_v2._shutdown_mt5 = lambda: None
        try:
            snapshot = server_v2._snapshot_from_mt5("all")
        finally:
            server_v2.mt5 = original_mt5
            server_v2._map_positions = original_map_positions
            server_v2._map_pending_orders = original_map_pending_orders
            server_v2._progressive_trade_history_deals = original_progressive_history
            if original_build_curve_from_deals is None:
                delattr(server_v2, "_build_curve_from_deals")
            else:
                server_v2._build_curve_from_deals = original_build_curve_from_deals
            server_v2._map_trade_deals = original_map_trade_deals
            server_v2._build_overview = original_build_overview
            server_v2._curve_indicators = original_curve_indicators
            server_v2._build_stats = original_build_stats
            server_v2._ensure_mt5 = original_ensure_mt5
            server_v2._shutdown_mt5 = original_shutdown

        account_meta = snapshot["accountMeta"]
        self.assertEqual(1200.0, account_meta["balance"])
        self.assertEqual(1255.0, account_meta["equity"])
        self.assertEqual(150.0, account_meta["margin"])
        self.assertEqual(1105.0, account_meta["freeMargin"])
        self.assertEqual(836.66, account_meta["marginLevel"])
        self.assertEqual(55.0, account_meta["profit"])

    def test_deal_time_ms_should_require_mt5_server_timezone(self):
        original_offset = getattr(server_v2, "MT5_TIME_OFFSET_MINUTES", 0)
        original_timezone = getattr(server_v2, "MT5_SERVER_TIMEZONE", "")
        try:
            server_v2.MT5_TIME_OFFSET_MINUTES = 180
            server_v2.MT5_SERVER_TIMEZONE = ""
            deal = types.SimpleNamespace(time_msc=1775151432000, time=0)

            with self.assertRaisesRegex(ValueError, "MT5_SERVER_TIMEZONE"):
                server_v2._deal_time_ms(deal)
        finally:
            server_v2.MT5_TIME_OFFSET_MINUTES = original_offset
            server_v2.MT5_SERVER_TIMEZONE = original_timezone

    def test_deal_time_ms_normalizes_mt5_server_timezone_wall_clock(self):
        original_offset = getattr(server_v2, "MT5_TIME_OFFSET_MINUTES", 0)
        original_timezone = getattr(server_v2, "MT5_SERVER_TIMEZONE", "")
        try:
            server_v2.MT5_TIME_OFFSET_MINUTES = 0
            server_v2.MT5_SERVER_TIMEZONE = "Europe/Athens"
            deal = types.SimpleNamespace(time_msc=1775818406271, time=0)

            value = server_v2._deal_time_ms(deal)

            self.assertEqual(1775807606271, value)
        finally:
            server_v2.MT5_TIME_OFFSET_MINUTES = original_offset
            server_v2.MT5_SERVER_TIMEZONE = original_timezone

    def test_map_trades_should_normalize_open_and_close_times_with_server_timezone(self):
        original_mt5 = server_v2.mt5
        original_offset = getattr(server_v2, "MT5_TIME_OFFSET_MINUTES", 0)
        original_timezone = getattr(server_v2, "MT5_SERVER_TIMEZONE", "")

        class _FakeMt5:
            @staticmethod
            def history_deals_get(from_time, to_time):
                return [
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=0,
                        volume=1.0,
                        ticket=101,
                        order=201,
                        position_id=301,
                        entry=0,
                        time=1775145828,
                        time_msc=1775145828613,
                        price=66020.6,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=1,
                        volume=1.0,
                        ticket=102,
                        order=202,
                        position_id=301,
                        entry=1,
                        time=1775149621,
                        time_msc=1775149621284,
                        price=66392.56,
                        profit=18.6,
                        commission=0.0,
                        swap=0.0,
                        comment="close",
                    ),
                ]

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        server_v2.mt5 = _FakeMt5()
        server_v2.MT5_TIME_OFFSET_MINUTES = 0
        server_v2.MT5_SERVER_TIMEZONE = "Europe/Athens"
        try:
            trades = server_v2._map_trades("1d")
        finally:
            server_v2.mt5 = original_mt5
            server_v2.MT5_TIME_OFFSET_MINUTES = original_offset
            server_v2.MT5_SERVER_TIMEZONE = original_timezone

        self.assertEqual(1, len(trades))
        self.assertEqual(1775135028613, trades[0]["openTime"])
        self.assertEqual(1775138821284, trades[0]["closeTime"])

    def test_map_trades_should_fail_when_server_timezone_missing(self):
        original_mt5 = server_v2.mt5
        original_offset = getattr(server_v2, "MT5_TIME_OFFSET_MINUTES", 0)
        original_timezone = getattr(server_v2, "MT5_SERVER_TIMEZONE", "")

        class _FakeMt5:
            @staticmethod
            def history_deals_get(from_time, to_time):
                return [
                    types.SimpleNamespace(
                        symbol="BTCUSD",
                        type=1,
                        volume=0.01,
                        ticket=1780804002,
                        order=1787265273,
                        position_id=1787265273,
                        entry=0,
                        time=1775225086,
                        time_msc=1775225086619,
                        price=66878.63,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSD",
                        type=0,
                        volume=0.01,
                        ticket=1780804031,
                        order=1787265303,
                        position_id=1787265273,
                        entry=1,
                        time=1775225142,
                        time_msc=1775225142404,
                        price=66860.77,
                        profit=0.18,
                        commission=0.0,
                        swap=0.0,
                        comment="close",
                    ),
                ]

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        server_v2.mt5 = _FakeMt5()
        server_v2.MT5_TIME_OFFSET_MINUTES = 0
        server_v2.MT5_SERVER_TIMEZONE = ""
        try:
            with self.assertRaisesRegex(ValueError, "MT5_SERVER_TIMEZONE"):
                server_v2._map_trades("1d")
        finally:
            server_v2.mt5 = original_mt5
            server_v2.MT5_TIME_OFFSET_MINUTES = original_offset
            server_v2.MT5_SERVER_TIMEZONE = original_timezone

    def test_map_trade_deals_should_order_same_second_deals_by_millisecond_timestamp(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        open_deal = types.SimpleNamespace(
            symbol="BTCUSD",
            type=0,
            volume=0.01,
            ticket=1001,
            order=2001,
            position_id=3001,
            entry=0,
            time=1775225086,
            time_msc=1775225086100,
            price=66878.63,
            profit=0.0,
            commission=0.0,
            swap=0.0,
            comment="open",
        )
        close_deal = types.SimpleNamespace(
            symbol="BTCUSD",
            type=1,
            volume=0.01,
            ticket=1002,
            order=2002,
            position_id=3001,
            entry=1,
            time=1775225086,
            time_msc=1775225086900,
            price=66860.77,
            profit=0.18,
            commission=0.0,
            swap=0.0,
            comment="close",
        )

        with _configured_mt5_server_timezone("UTC"):
            server_v2.mt5 = _FakeMt5()
            try:
                trades = server_v2._map_trade_deals([close_deal, open_deal])
            finally:
                server_v2.mt5 = original_mt5

        self.assertEqual(1, len(trades))
        self.assertEqual(1775225086100, trades[0]["openTime"])
        self.assertEqual(1775225086900, trades[0]["closeTime"])
        self.assertEqual(66878.63, trades[0]["openPrice"])
        self.assertEqual(66860.77, trades[0]["closePrice"])

    def test_map_trade_deals_should_put_open_before_close_when_millisecond_timestamp_is_identical(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        close_deal = types.SimpleNamespace(
            symbol="BTCUSD",
            type=1,
            volume=0.01,
            ticket=1002,
            order=2002,
            position_id=3001,
            entry=1,
            time=1775225086,
            time_msc=1775225086900,
            price=66860.77,
            profit=0.18,
            commission=0.0,
            swap=0.0,
            comment="close",
        )
        open_deal = types.SimpleNamespace(
            symbol="BTCUSD",
            type=0,
            volume=0.01,
            ticket=1001,
            order=2001,
            position_id=3001,
            entry=0,
            time=1775225086,
            time_msc=1775225086900,
            price=66878.63,
            profit=0.0,
            commission=0.0,
            swap=0.0,
            comment="open",
        )

        with _configured_mt5_server_timezone("UTC"):
            server_v2.mt5 = _FakeMt5()
            try:
                trades = server_v2._map_trade_deals([close_deal, open_deal])
            finally:
                server_v2.mt5 = original_mt5

        self.assertEqual(1, len(trades))
        self.assertEqual(66878.63, trades[0]["openPrice"])
        self.assertEqual(66860.77, trades[0]["closePrice"])

    def test_rebuild_ea_trade_records_should_put_open_before_close_when_timestamp_is_identical(self):
        records = [
            {
                "timestamp": 200000,
                "productName": "BTCUSD",
                "code": "BTCUSD",
                "side": "Sell",
                "price": 120.0,
                "quantity": 0.1,
                "amount": 12.0,
                "contractSize": 1.0,
                "fee": 2.0,
                "commission": -2.0,
                "profit": 8.0,
                "openTime": 200000,
                "closeTime": 200000,
                "storageFee": -3.0,
                "swap": -3.0,
                "dealTicket": 12,
                "orderId": 102,
                "positionId": 201,
                "entryType": 1,
                "dealType": 1,
                "remark": "close",
            },
            {
                "timestamp": 200000,
                "productName": "BTCUSD",
                "code": "BTCUSD",
                "side": "Buy",
                "price": 100.0,
                "quantity": 0.1,
                "amount": 10.0,
                "contractSize": 1.0,
                "fee": 1.5,
                "commission": -1.5,
                "profit": 0.0,
                "openTime": 200000,
                "closeTime": 200000,
                "storageFee": 0.0,
                "swap": 0.0,
                "dealTicket": 11,
                "orderId": 101,
                "positionId": 201,
                "entryType": 0,
                "dealType": 0,
                "remark": "open",
            },
        ]

        rebuilt = server_v2._rebuild_ea_trade_records(records)

        self.assertEqual(1, len(rebuilt))
        self.assertEqual(100.0, rebuilt[0]["openPrice"])
        self.assertEqual(120.0, rebuilt[0]["closePrice"])

    def test_rebuild_ea_trade_records_should_require_explicit_contract_size(self):
        records = [
            {
                "timestamp": 100000,
                "productName": "BTCUSD",
                "code": "BTCUSD",
                "side": "Buy",
                "price": 100.0,
                "quantity": 0.1,
                "amount": 10.0,
                "fee": 1.5,
                "commission": -1.5,
                "profit": 0.0,
                "openTime": 100000,
                "closeTime": 100000,
                "storageFee": 0.0,
                "swap": 0.0,
                "dealTicket": 11,
                "orderId": 101,
                "positionId": 201,
                "entryType": 0,
                "dealType": 0,
            }
        ]

        with self.assertRaisesRegex(RuntimeError, "ea trade missing contractSize"):
            server_v2._rebuild_ea_trade_records(records)

    def test_light_snapshot_trade_count_should_follow_mapped_trade_history(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def history_deals_total(from_time, to_time):
                raise RuntimeError("history_deals_total should not be used")

            @staticmethod
            def history_deals_get(from_time, to_time):
                return [
                    types.SimpleNamespace(
                        symbol="BTCUSD",
                        type=0,
                        volume=0.01,
                        ticket=1001,
                        order=2001,
                        position_id=3001,
                        entry=0,
                        time=1775225086,
                        time_msc=1775225086619,
                        price=66878.63,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSD",
                        type=1,
                        volume=0.01,
                        ticket=1002,
                        order=2002,
                        position_id=3001,
                        entry=1,
                        time=1775225142,
                        time_msc=1775225142404,
                        price=66860.77,
                        profit=0.18,
                        commission=0.0,
                        swap=0.0,
                        comment="close",
                    ),
                ]

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        with _configured_mt5_server_timezone("UTC"):
            server_v2.mt5 = _FakeMt5()
            try:
                trade_count = server_v2._light_snapshot_trade_count()
            finally:
                server_v2.mt5 = original_mt5

        self.assertEqual(1, trade_count)

    def test_mt5_history_window_uses_naive_local_datetimes(self):
        window_builder = getattr(server_v2, "_mt5_history_window", None)
        self.assertIsNotNone(window_builder, "缺少 _mt5_history_window，无法统一 MT5 历史查询时间口径")

        from_time, to_time = window_builder("1d")

        self.assertIsNone(from_time.tzinfo)
        self.assertIsNone(to_time.tzinfo)
        self.assertGreater(to_time, from_time)

    def test_mt5_history_window_extends_upper_bound_with_future_lookahead(self):
        original_datetime = server_v2.datetime
        original_lookahead = getattr(server_v2, "MT5_HISTORY_LOOKAHEAD_HOURS", 24)

        class _FakeDateTime(original_datetime):
            @classmethod
            def now(cls, tz=None):
                return cls(2026, 4, 3, 19, 6, 52)

        server_v2.datetime = _FakeDateTime
        server_v2.MT5_HISTORY_LOOKAHEAD_HOURS = 24
        try:
            from_time, to_time = server_v2._mt5_history_window("1d")
        finally:
            server_v2.datetime = original_datetime
            server_v2.MT5_HISTORY_LOOKAHEAD_HOURS = original_lookahead

        self.assertEqual(original_datetime(2026, 4, 2, 19, 6, 52), from_time)
        self.assertEqual(original_datetime(2026, 4, 4, 19, 6, 52), to_time)

    def test_map_trades_queries_mt5_with_naive_local_datetimes(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            captured_from = None
            captured_to = None

            @staticmethod
            def history_deals_get(from_time, to_time):
                _FakeMt5.captured_from = from_time
                _FakeMt5.captured_to = to_time
                return []

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        with _configured_mt5_server_timezone("UTC"):
            server_v2.mt5 = _FakeMt5()
            try:
                trades = server_v2._map_trades("1d")
            finally:
                server_v2.mt5 = original_mt5

        self.assertEqual([], trades)
        self.assertIsNotNone(_FakeMt5.captured_from)
        self.assertIsNotNone(_FakeMt5.captured_to)
        self.assertIsNone(_FakeMt5.captured_from.tzinfo)
        self.assertIsNone(_FakeMt5.captured_to.tzinfo)

    def test_progressive_trade_history_deals_should_expand_window_until_count_stops_growing(self):
        original_mt5 = server_v2.mt5
        original_target = getattr(server_v2, "TRADE_HISTORY_TARGET_ITEMS", 1000)

        class _FakeMt5:
            calls = []

            @staticmethod
            def history_deals_get(from_time, to_time):
                _FakeMt5.calls.append(from_time)
                if len(_FakeMt5.calls) == 1:
                    return [object()] * 100
                if len(_FakeMt5.calls) == 2:
                    return [object()] * 1200
                return [object()] * 2000

        server_v2.mt5 = _FakeMt5()
        server_v2.TRADE_HISTORY_TARGET_ITEMS = 1000
        try:
            deals = server_v2._progressive_trade_history_deals("all")
        finally:
            server_v2.mt5 = original_mt5
            server_v2.TRADE_HISTORY_TARGET_ITEMS = original_target

        self.assertEqual(4, len(_FakeMt5.calls))
        self.assertEqual(2000, len(deals))

    def test_progressive_trade_history_deals_all_should_continue_expanding_past_two_years(self):
        original_mt5 = server_v2.mt5
        original_all_days = getattr(server_v2, "SNAPSHOT_RANGE_ALL_DAYS", 36500)

        class _FakeMt5:
            calls = []

            @staticmethod
            def history_deals_get(from_time, to_time):
                _FakeMt5.calls.append(from_time)
                return [object()] * (100 * len(_FakeMt5.calls))

        server_v2.mt5 = _FakeMt5()
        server_v2.SNAPSHOT_RANGE_ALL_DAYS = 36500
        try:
            server_v2._progressive_trade_history_deals("all")
        finally:
            server_v2.mt5 = original_mt5
            server_v2.SNAPSHOT_RANGE_ALL_DAYS = original_all_days

        self.assertGreaterEqual(len(_FakeMt5.calls), 6)

    def test_ensure_mt5_should_use_authenticated_initialize_without_shutdown(self):
        original_mt5 = server_v2.mt5
        original_login = server_v2.LOGIN
        original_password = server_v2.PASSWORD
        original_server = server_v2.SERVER
        original_candidates = list(server_v2.MT5_TERMINAL_CANDIDATES)
        original_initialize = server_v2._mt5_initialize
        original_login_fn = server_v2._mt5_login
        original_shutdown = server_v2._shutdown_mt5

        calls = []

        class _FakeMt5:
            pass

        def fake_initialize(path_value, login=None, password=None, server_name=None):
            calls.append(("init", path_value, login, server_name))
            return True, "ok"

        def fake_login():
            calls.append(("login",))
            return True, "already-logged-in"

        def fake_shutdown():
            calls.append(("shutdown",))

        server_v2.mt5 = _FakeMt5()
        server_v2.LOGIN = 7400048
        server_v2.PASSWORD = "demo"
        server_v2.SERVER = "ICMarketsSC-MT5-6"
        server_v2.MT5_TERMINAL_CANDIDATES = []
        server_v2._mt5_initialize = fake_initialize
        server_v2._mt5_login = fake_login
        server_v2._shutdown_mt5 = fake_shutdown
        try:
            server_v2._ensure_mt5()
        finally:
            server_v2.mt5 = original_mt5
            server_v2.LOGIN = original_login
            server_v2.PASSWORD = original_password
            server_v2.SERVER = original_server
            server_v2.MT5_TERMINAL_CANDIDATES = original_candidates
            server_v2._mt5_initialize = original_initialize
            server_v2._mt5_login = original_login_fn
            server_v2._shutdown_mt5 = original_shutdown

        self.assertEqual([("init", None, 7400048, "ICMarketsSC-MT5-6")], calls)

    def test_map_trades_pairs_partial_close_with_fifo_open_batches(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def history_deals_get(from_time, to_time):
                return [
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=0,
                        volume=1.0,
                        ticket=101,
                        order=201,
                        position_id=301,
                        entry=0,
                        time=100,
                        price=100.0,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open-1",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=0,
                        volume=1.0,
                        ticket=102,
                        order=202,
                        position_id=301,
                        entry=0,
                        time=200,
                        price=110.0,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open-2",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=1,
                        volume=1.5,
                        ticket=103,
                        order=203,
                        position_id=301,
                        entry=1,
                        time=300,
                        price=120.0,
                        profit=30.0,
                        commission=0.0,
                        swap=0.0,
                        comment="close",
                    ),
                ]

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        with _configured_mt5_server_timezone("UTC"):
            server_v2.mt5 = _FakeMt5()
            try:
                trades = server_v2._map_trades("1d")
            finally:
                server_v2.mt5 = original_mt5

        self.assertEqual(2, len(trades))
        ordered = sorted(trades, key=lambda item: item["openTime"])
        self.assertEqual([100000, 200000], [item["openTime"] for item in ordered])
        self.assertEqual([300000, 300000], [item["closeTime"] for item in ordered])
        self.assertEqual("Buy", ordered[0]["side"])
        self.assertEqual("Buy", ordered[1]["side"])
        self.assertAlmostEqual(100.0, ordered[0]["openPrice"])
        self.assertAlmostEqual(110.0, ordered[1]["openPrice"])
        self.assertAlmostEqual(1.0, ordered[0]["quantity"])
        self.assertAlmostEqual(0.5, ordered[1]["quantity"])
        self.assertAlmostEqual(20.0, ordered[0]["profit"])
        self.assertAlmostEqual(10.0, ordered[1]["profit"])

    def test_map_trades_should_not_fallback_to_symbol_side_when_lifecycle_key_changes(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def history_deals_get(from_time, to_time):
                return [
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=0,
                        volume=1.0,
                        ticket=101,
                        order=201,
                        position_id=0,
                        entry=0,
                        time=100,
                        price=100.0,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open-without-position",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSDT",
                        type=1,
                        volume=1.0,
                        ticket=102,
                        order=202,
                        position_id=301,
                        entry=1,
                        time=300,
                        price=120.0,
                        profit=20.0,
                        commission=0.0,
                        swap=0.0,
                        comment="close-with-position",
                    ),
                ]

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        with _configured_mt5_server_timezone("UTC"):
            server_v2.mt5 = _FakeMt5()
            try:
                trades = server_v2._map_trades("1d")
            finally:
                server_v2.mt5 = original_mt5

        self.assertEqual(1, len(trades))
        trade = trades[0]
        self.assertEqual("Buy", trade["side"])
        self.assertEqual(300000, trade["openTime"])
        self.assertEqual(300000, trade["closeTime"])
        self.assertAlmostEqual(120.0, trade["openPrice"])
        self.assertAlmostEqual(120.0, trade["closePrice"])
        self.assertAlmostEqual(20.0, trade["profit"])

    def test_map_trades_maps_mt5_sell_lifecycle_into_single_trade_record(self):
        original_mt5 = server_v2.mt5

        class _FakeMt5:
            @staticmethod
            def history_deals_get(from_time, to_time):
                return [
                    types.SimpleNamespace(
                        symbol="BTCUSD",
                        type=1,
                        volume=0.01,
                        ticket=1779629211,
                        order=1786015308,
                        position_id=1786015308,
                        entry=0,
                        time=1774794820,
                        time_msc=1774794820000,
                        price=66636.06,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSD",
                        type=0,
                        volume=0.01,
                        ticket=1779633102,
                        order=1786019259,
                        position_id=1786015308,
                        entry=1,
                        time=1774813268,
                        time_msc=1774813268000,
                        price=66451.66,
                        profit=1.84,
                        commission=0.0,
                        swap=0.0,
                        comment="close",
                    ),
                ]

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        with _configured_mt5_server_timezone("UTC"):
            server_v2.mt5 = _FakeMt5()
            try:
                trades = server_v2._map_trades("7d")
            finally:
                server_v2.mt5 = original_mt5

        self.assertEqual(1, len(trades))
        trade = trades[0]
        self.assertEqual(1779633102, trade["dealTicket"])
        self.assertEqual(1786019259, trade["orderId"])
        self.assertEqual(1786015308, trade["positionId"])
        self.assertEqual("Sell", trade["side"])
        self.assertAlmostEqual(0.01, trade["quantity"])
        self.assertAlmostEqual(66636.06, trade["openPrice"])
        self.assertAlmostEqual(66451.66, trade["closePrice"])
        self.assertAlmostEqual(1.84, trade["profit"])
        self.assertEqual(1774794820000, trade["openTime"])
        self.assertEqual(1774813268000, trade["closeTime"])

    def test_normalize_snapshot_should_not_rebuild_raw_ea_deals_into_trade_lifecycle(self):
        payload = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 EA Push",
                "updatedAt": 1775303081955,
            },
            "trades": [
                {
                    "timestamp": 100000,
                    "productName": "BTCUSD",
                    "code": "BTCUSD",
                    "side": "Buy",
                    "price": 100.0,
                    "openPrice": 100.0,
                    "closePrice": 100.0,
                "quantity": 0.1,
                "amount": 10.0,
                "contractSize": 1.0,
                "fee": 1.5,
                    "commission": -1.5,
                    "profit": 0.0,
                    "openTime": 100000,
                    "closeTime": 100000,
                    "storageFee": 0.0,
                    "swap": 0.0,
                    "dealTicket": 11,
                    "orderId": 101,
                    "positionId": 201,
                    "entryType": 0,
                    "dealType": 0,
                    "remark": "open",
                },
                {
                    "timestamp": 200000,
                    "productName": "BTCUSD",
                    "code": "BTCUSD",
                    "side": "Sell",
                    "price": 120.0,
                    "openPrice": 120.0,
                    "closePrice": 120.0,
                "quantity": 0.1,
                "amount": 12.0,
                "contractSize": 1.0,
                "fee": 2.0,
                    "commission": -2.0,
                    "profit": 8.0,
                    "openTime": 200000,
                    "closeTime": 200000,
                    "storageFee": -3.0,
                    "swap": -3.0,
                    "dealTicket": 12,
                    "orderId": 102,
                    "positionId": 201,
                    "entryType": 1,
                    "dealType": 1,
                    "remark": "close",
                },
            ],
        }

        snapshot = server_v2._normalize_snapshot(payload, "MT5 EA Push")

        self.assertEqual(2, len(snapshot["trades"]))
        self.assertEqual(100000, snapshot["trades"][0]["openTime"])
        self.assertEqual(100000, snapshot["trades"][0]["closeTime"])
        self.assertEqual(200000, snapshot["trades"][1]["openTime"])
        self.assertEqual(200000, snapshot["trades"][1]["closeTime"])

    def test_normalize_snapshot_should_keep_sparse_ea_curve_points_from_payload(self):
        payload = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 EA Push",
                "updatedAt": 1775303081955,
            },
            "overviewMetrics": [
                {"name": "Current Equity", "value": "1008.00"},
                {"name": "Balance", "value": "1008.00"},
                {"name": "Leverage", "value": "100x"},
            ],
            "positions": [],
            "curvePoints": [
                {"timestamp": 200000, "equity": 1008.0, "balance": 1008.0},
            ],
            "trades": [
                {
                    "timestamp": 100000,
                    "productName": "BTCUSD",
                    "code": "BTCUSD",
                    "side": "Buy",
                    "price": 100.0,
                "quantity": 0.1,
                "amount": 10.0,
                "contractSize": 1.0,
                "fee": 1.5,
                    "commission": -1.5,
                    "profit": 0.0,
                    "openTime": 100000,
                    "closeTime": 100000,
                    "storageFee": 0.0,
                    "swap": 0.0,
                    "dealTicket": 11,
                    "orderId": 101,
                    "positionId": 201,
                    "entryType": 0,
                    "dealType": 0,
                },
                {
                    "timestamp": 200000,
                    "productName": "BTCUSD",
                    "code": "BTCUSD",
                    "side": "Sell",
                    "price": 120.0,
                "quantity": 0.1,
                "amount": 12.0,
                "contractSize": 1.0,
                "fee": 2.0,
                    "commission": -2.0,
                    "profit": 8.0,
                    "openTime": 200000,
                    "closeTime": 200000,
                    "storageFee": -3.0,
                    "swap": -3.0,
                    "dealTicket": 12,
                    "orderId": 102,
                    "positionId": 201,
                    "entryType": 1,
                    "dealType": 1,
                },
            ],
        }

        snapshot = server_v2._normalize_snapshot(payload, "MT5 EA Push")

        self.assertEqual(
            [{"timestamp": 200000, "equity": 1008.0, "balance": 1008.0}],
            snapshot["curvePoints"],
        )

    def test_normalize_snapshot_should_not_rebuild_ea_curve_points_when_source_curve_has_multiple_points(self):
        payload = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 EA Push",
                "updatedAt": 1775303081955,
                "balance": 1008.0,
                "equity": 1008.0,
                "leverage": 100,
            },
            "overviewMetrics": [
                {"name": "Current Equity", "value": "1008.00"},
                {"name": "Balance", "value": "1008.00"},
                {"name": "Leverage", "value": "100x"},
            ],
            "positions": [],
            "curvePoints": [
                {"timestamp": 100000, "equity": 9999.0, "balance": 9999.0},
                {"timestamp": 200000, "equity": 8888.0, "balance": 8888.0},
            ],
            "trades": [
                {
                    "timestamp": 100000,
                    "productName": "BTCUSD",
                    "code": "BTCUSD",
                    "side": "Buy",
                    "price": 100.0,
                    "quantity": 0.1,
                    "amount": 10.0,
                    "contractSize": 1.0,
                    "fee": 1.5,
                    "commission": -1.5,
                    "profit": 0.0,
                    "openTime": 100000,
                    "closeTime": 100000,
                    "storageFee": 0.0,
                    "swap": 0.0,
                    "dealTicket": 11,
                    "orderId": 101,
                    "positionId": 201,
                    "entryType": 0,
                    "dealType": 0,
                },
                {
                    "timestamp": 200000,
                    "productName": "BTCUSD",
                    "code": "BTCUSD",
                    "side": "Sell",
                    "price": 120.0,
                    "quantity": 0.1,
                    "amount": 12.0,
                    "contractSize": 1.0,
                    "fee": 2.0,
                    "commission": -2.0,
                    "profit": 8.0,
                    "openTime": 200000,
                    "closeTime": 200000,
                    "storageFee": -3.0,
                    "swap": -3.0,
                    "dealTicket": 12,
                    "orderId": 102,
                    "positionId": 201,
                    "entryType": 1,
                    "dealType": 1,
                },
            ],
        }

        snapshot = server_v2._normalize_snapshot(payload, "MT5 EA Push")

        self.assertEqual(
            [
                {"timestamp": 100000, "equity": 9999.0, "balance": 9999.0},
                {"timestamp": 200000, "equity": 8888.0, "balance": 8888.0},
            ],
            snapshot["curvePoints"],
        )

    def test_trim_cache_entries_locked_keeps_newest_entries_only(self):
        helper = getattr(server_v2, "_trim_cache_entries_locked", None)
        self.assertIsNotNone(helper, "缺少 _trim_cache_entries_locked，无法验证快照缓存裁剪")

        cache = {
            "1d:snapshot": {"seq": 1},
            "7d:snapshot": {"seq": 2},
            "1m:curve": {"seq": 3},
        }

        helper(cache, 2)

        self.assertEqual(["7d:snapshot", "1m:curve"], list(cache.keys()))

    def test_should_slide_snapshot_build_cache_should_always_disable_cache_sliding(self):
        helper = getattr(server_v2, "_should_slide_snapshot_build_cache", None)
        self.assertIsNotNone(helper, "缺少 _should_slide_snapshot_build_cache，无法验证缓存平滑命中")

        self.assertFalse(helper(None, 9500))
        self.assertFalse(helper({
            "builtAt": 1000,
            "snapshot": {"accountMeta": {"source": "MT5 EA Push"}},
        }, 9500))
        self.assertFalse(helper({
            "builtAt": 1000,
            "snapshot": {"accountMeta": {"source": "MT5 Python Pull"}},
        }, 9500))

    def test_build_snapshot_response_should_drop_legacy_ea_sync_state(self):
        server_v2.snapshot_sync_cache.clear()
        server_v2.snapshot_sync_cache["all:snapshot"] = {
            "seq": 9,
            "digest": "legacy",
            "snapshot": {
                "accountMeta": {"source": "MT5 EA Push", "login": "7400048"},
                "positions": [{"positionTicket": 1}],
                "pendingOrders": [],
                "trades": [],
                "curvePoints": [],
            },
            "previousSeq": 8,
            "previousSnapshot": {
                "accountMeta": {"source": "MT5 EA Push", "login": "7400048"},
                "positions": [{"positionTicket": 0}],
                "pendingOrders": [],
                "trades": [],
                "curvePoints": [],
            },
        }
        canonical_snapshot = {
            "accountMeta": {"source": "MT5 Python Pull", "login": "7400048", "server": "demo"},
            "overviewMetrics": [],
            "curvePoints": [],
            "curveIndicators": [],
            "positions": [{"positionTicket": 2}],
            "pendingOrders": [],
            "trades": [],
            "statsMetrics": [],
        }

        with mock.patch.object(server_v2, "_build_snapshot_with_cache", return_value=canonical_snapshot):
            response = server_v2._build_snapshot_response("all", since_seq=9, delta=True)

        self.assertFalse(response["isDelta"])
        self.assertEqual("MT5 Python Pull", response["accountMeta"]["source"])
        self.assertEqual(1, response["accountMeta"]["syncSeq"])
        self.assertEqual([2], [item["positionTicket"] for item in response["positions"]])

    def test_build_trades_snapshot_response_should_drop_legacy_ea_sync_state(self):
        server_v2.snapshot_sync_cache.clear()
        server_v2.snapshot_sync_cache["all:trades"] = {
            "seq": 7,
            "digest": "legacy",
            "snapshot": {
                "accountMeta": {"source": "MT5 EA Push", "login": "7400048"},
                "trades": [{"dealTicket": 1}],
            },
            "previousSeq": 6,
            "previousSnapshot": {
                "accountMeta": {"source": "MT5 EA Push", "login": "7400048"},
                "trades": [{"dealTicket": 0}],
            },
        }
        canonical_snapshot = {
            "accountMeta": {"source": "MT5 Python Pull", "login": "7400048", "server": "demo"},
            "overviewMetrics": [],
            "curvePoints": [],
            "curveIndicators": [],
            "positions": [],
            "pendingOrders": [],
            "trades": [{"dealTicket": 11}],
            "statsMetrics": [],
        }

        with mock.patch.object(server_v2, "_build_snapshot_with_cache", return_value=canonical_snapshot), mock.patch.object(
            server_v2, "_build_trade_history_with_cache", return_value=[{"dealTicket": 11}]
        ):
            response = server_v2._build_trades_snapshot_response("all", since_seq=7, delta=True)

        self.assertFalse(response["isDelta"])
        self.assertEqual("MT5 Python Pull", response["accountMeta"]["source"])
        self.assertEqual(1, response["accountMeta"]["syncSeq"])
        self.assertEqual([11], [item["dealTicket"] for item in response["trades"]])

    def test_ingest_ea_snapshot_should_not_clear_snapshot_sync_cache_when_payload_changes(self):
        server_v2.snapshot_build_cache.clear()
        server_v2.snapshot_sync_cache.clear()
        server_v2.snapshot_build_cache["7d"] = {"builtAt": 1, "snapshot": {"accountMeta": {"source": "old"}}}
        server_v2.snapshot_sync_cache["7d:snapshot"] = {"seq": 9, "snapshot": {"accountMeta": {"source": "old"}}}

        payload = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 EA Push",
                "updatedAt": 1774868116886,
            },
            "overviewMetrics": [],
            "curveIndicators": [],
            "curvePoints": [],
            "positions": [],
            "pendingOrders": [],
            "trades": [],
            "statsMetrics": [],
        }

        response = server_v2.ingest_ea_snapshot(payload, x_bridge_token=None)

        self.assertTrue(response["changed"])
        self.assertIn("7d", server_v2.snapshot_build_cache)
        self.assertIn("7d:snapshot", server_v2.snapshot_sync_cache)

    def test_build_summary_response_omits_heavy_collections(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "updatedAt": 1774868116886,
                "tradeCount": 5,
                "positionCount": 6,
            },
            "overviewMetrics": [{"name": "Total Asset", "value": "$17,854.56"}],
            "curvePoints": [{"timestamp": 1, "equity": 100.0, "balance": 100.0}],
            "curveIndicators": [{"name": "1D Return", "value": "-0.58%"}],
            "positions": [{"code": "BTCUSD", "quantity": 0.05}],
            "pendingOrders": [{"code": "XAUUSD", "pendingLots": 0.02}],
            "trades": [{"dealTicket": 1779714434, "profit": -67.17}],
            "statsMetrics": [{"name": "Cumulative Profit", "value": "-$104.25"}],
        }

        builder = getattr(server_v2, "_build_summary_response", None)
        self.assertIsNotNone(builder, "缺少 _build_summary_response，尚未实现摘要接口")

        response = builder(snapshot, 7)

        self.assertEqual("7400048", response["accountMeta"]["login"])
        self.assertEqual(7, response["accountMeta"]["syncSeq"])
        self.assertFalse(response["isDelta"])
        self.assertFalse(response["unchanged"])
        self.assertEqual(snapshot["overviewMetrics"], response["overviewMetrics"])
        self.assertEqual(snapshot["statsMetrics"], response["statsMetrics"])
        self.assertNotIn("curvePoints", response)
        self.assertNotIn("curveIndicators", response)
        self.assertNotIn("positions", response)
        self.assertNotIn("pendingOrders", response)
        self.assertNotIn("trades", response)

    def test_build_live_response_keeps_positions_only(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "updatedAt": 1774868116886,
            },
            "overviewMetrics": [{"name": "Total Asset", "value": "$17,854.56"}],
            "curvePoints": [{"timestamp": 1, "equity": 100.0, "balance": 100.0}],
            "curveIndicators": [{"name": "1D Return", "value": "-0.58%"}],
            "positions": [{"code": "BTCUSD", "quantity": 0.05}],
            "pendingOrders": [{"code": "XAUUSD", "pendingLots": 0.02}],
            "trades": [{"dealTicket": 1779714434, "profit": -67.17}],
            "statsMetrics": [{"name": "Cumulative Profit", "value": "-$104.25"}],
        }

        builder = getattr(server_v2, "_build_live_response", None)
        self.assertIsNotNone(builder, "缺少 _build_live_response，尚未实现轻实时持仓接口")

        response = builder(snapshot, 3)

        self.assertEqual(3, response["accountMeta"]["syncSeq"])
        self.assertEqual(snapshot["overviewMetrics"], response["overviewMetrics"])
        self.assertEqual(snapshot["statsMetrics"], response["statsMetrics"])
        self.assertEqual(snapshot["positions"], response["positions"])
        self.assertNotIn("curvePoints", response)
        self.assertNotIn("curveIndicators", response)
        self.assertNotIn("pendingOrders", response)
        self.assertNotIn("trades", response)

    def test_build_trades_response_keeps_trades_only(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 Python Pull",
                "updatedAt": 1774868116886,
            },
            "overviewMetrics": [{"name": "Total Asset", "value": "$17,854.56"}],
            "positions": [{"code": "BTCUSD", "quantity": 0.05}],
            "pendingOrders": [{"code": "XAUUSD", "pendingLots": 0.02}],
            "trades": [{"dealTicket": 1779714434, "profit": -67.17}],
            "statsMetrics": [{"name": "Cumulative Profit", "value": "-$104.25"}],
        }

        builder = getattr(server_v2, "_build_trades_response", None)
        self.assertIsNotNone(builder, "缺少 _build_trades_response，尚未实现成交增量接口")

        response = builder(snapshot, 5)

        self.assertEqual(5, response["accountMeta"]["syncSeq"])
        self.assertEqual(snapshot["trades"], response["trades"])
        self.assertNotIn("positions", response)
        self.assertNotIn("pendingOrders", response)
        self.assertNotIn("curvePoints", response)
        self.assertNotIn("overviewMetrics", response)

    def test_build_trades_snapshot_does_not_re_normalize_snapshot(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 EA Push",
                "updatedAt": 1774868116886,
            },
            "trades": [{"dealTicket": 1779714434, "profit": -67.17}],
        }
        original_normalize_snapshot = server_v2._normalize_snapshot

        def _unexpected_normalize(*args, **kwargs):
            raise AssertionError("trades 投影不应再次进入 _normalize_snapshot")

        server_v2._normalize_snapshot = _unexpected_normalize
        try:
            projected = server_v2._build_trades_snapshot(snapshot)
        finally:
            server_v2._normalize_snapshot = original_normalize_snapshot

        self.assertEqual(snapshot["accountMeta"], projected["accountMeta"])
        self.assertEqual(snapshot["trades"], projected["trades"])

    def test_build_curve_snapshot_does_not_re_normalize_snapshot(self):
        snapshot = {
            "accountMeta": {
                "login": "7400048",
                "server": "ICMarketsSC-MT5-6",
                "source": "MT5 EA Push",
                "updatedAt": 1774868116886,
            },
            "curvePoints": [{"timestamp": 1, "equity": 100.0, "balance": 100.0}],
            "curveIndicators": [{"name": "1D Return", "value": "-0.58%"}],
        }
        original_normalize_snapshot = server_v2._normalize_snapshot

        def _unexpected_normalize(*args, **kwargs):
            raise AssertionError("curve 投影不应再次进入 _normalize_snapshot")

        server_v2._normalize_snapshot = _unexpected_normalize
        try:
            projected = server_v2._build_curve_snapshot(snapshot)
        finally:
            server_v2._normalize_snapshot = original_normalize_snapshot

        self.assertEqual(snapshot["accountMeta"], projected["accountMeta"])
        self.assertEqual(snapshot["curvePoints"], projected["curvePoints"])
        self.assertEqual(snapshot["curveIndicators"], projected["curveIndicators"])

    def test_rebuild_curve_includes_open_positions(self):
        helper = getattr(server_v2, "_replay_curve_from_history", None)
        self.assertIsNotNone(helper, "缺少 _replay_curve_from_history，无法验证曲线重算")

        deals = [
            {
                "timestamp": 1000,
                "price": 100.0,
                "profit": 0.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 0,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "XAUUSD",
                "position_id": 1,
            },
            {
                "timestamp": 1500,
                "price": 110.0,
                "profit": 10.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 1,
                "deal_type": 1,
                "volume": 1.0,
                "symbol": "XAUUSD",
                "position_id": 1,
            },
        ]

        open_positions = [
            {
                "positionTicket": 99,
                "code": "BTCUSD",
                "productName": "BTCUSD",
                "side": "Buy",
                "quantity": 0.5,
                "costPrice": 200.0,
                "latestPrice": 210.0,
            }
        ]

        points = helper(
            deal_history=deals,
            start_balance=1000.0,
            open_positions=open_positions,
            current_balance=1010.0,
            current_equity=1030.0,
            leverage=10.0,
            contract_size_fn=lambda symbol: 1.0,
            now_ms=2000,
        )

        self.assertGreater(len(points), 2)
        self.assertAlmostEqual(points[0]["equity"] - points[0]["balance"], 5.0)
        self.assertTrue(
            any(abs(point["equity"] - point["balance"]) > 0.0 for point in points[:-1]),
            "曲线点应在有未平仓时 equity 与 balance 不一致",
        )
        self.assertIn("positionRatio", points[0])
        self.assertGreater(points[0]["positionRatio"], 0.0)
        self.assertEqual(points[-1]["balance"], 1010.0)
        self.assertEqual(points[-1]["equity"], 1030.0)
        self.assertAlmostEqual(points[-1]["positionRatio"], 10.5 / 1030.0, places=6)

    def test_resolve_open_position_ids_should_not_leave_position_open_when_same_timestamp_close_precedes_open(self):
        helper = getattr(server_v2, "_resolve_open_position_ids_from_history", None)
        self.assertIsNotNone(helper, "缺少 _resolve_open_position_ids_from_history，无法验证同时间戳生命周期顺序")

        deal_history = [
            {
                "timestamp": 1_000,
                "entry": 1,
                "deal_type": 1,
                "volume": 1.0,
                "symbol": "BTCUSD",
                "position_id": 7,
            },
            {
                "timestamp": 1_000,
                "entry": 0,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "BTCUSD",
                "position_id": 7,
            },
        ]

        open_position_ids = helper(deal_history)

        self.assertEqual(set(), open_position_ids)

    def test_rebuild_curve_should_not_preinject_position_when_position_id_already_exists_in_history(self):
        helper = getattr(server_v2, "_replay_curve_from_history", None)
        self.assertIsNotNone(helper, "缺少 _replay_curve_from_history，无法验证持仓重复注入问题")

        deals = [
            {
                "timestamp": 1_000,
                "price": 100.0,
                "profit": 0.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 0,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "BTCUSD",
                "position_id": 301,
            },
        ]
        open_positions = [
            {
                "positionId": 301,
                "positionTicket": 999,
                "code": "BTCUSD",
                "productName": "BTCUSD",
                "side": "Buy",
                "quantity": 1.0,
                "costPrice": 100.0,
                "latestPrice": 110.0,
                "marketValue": 110.0,
            }
        ]

        points = helper(
            deal_history=deals,
            start_balance=1_000.0,
            open_positions=open_positions,
            current_balance=1_000.0,
            current_equity=1_010.0,
            leverage=100.0,
            contract_size_fn=lambda symbol: 1.0,
            now_ms=2_000,
            fetch_rows_fn=lambda symbol, interval, limit, **kwargs: [],
        )

        self.assertAlmostEqual(
            points[0]["equity"],
            points[0]["balance"],
            places=6,
            msg="历史里已能重放出的持仓，不应再在窗口起点提前注入一遍",
        )

    def test_rebuild_curve_removes_closed_sell_exposure_before_later_price_updates(self):
        helper = getattr(server_v2, "_replay_curve_from_history", None)
        self.assertIsNotNone(helper, "缺少 _replay_curve_from_history，无法验证平仓后残留仓位问题")

        deals = [
            {
                "timestamp": 1000,
                "price": 100.0,
                "profit": 0.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 0,
                "deal_type": 1,
                "volume": 1.0,
                "symbol": "BTCUSD",
                "position_id": 1,
            },
            {
                "timestamp": 2000,
                "price": 101.0,
                "profit": -1.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 1,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "BTCUSD",
                "position_id": 1,
            },
            {
                "timestamp": 3000,
                "price": 120.0,
                "profit": 0.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 0,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "BTCUSD",
                "position_id": 2,
            },
        ]

        points = helper(
            deal_history=deals,
            start_balance=1000.0,
            open_positions=[],
            current_balance=999.0,
            current_equity=999.0,
            leverage=100.0,
            contract_size_fn=lambda symbol: 1.0,
            now_ms=4000,
        )

        point_after_close = next(point for point in points if point["timestamp"] == 2000)
        point_after_later_open = next(point for point in points if point["timestamp"] == 3000)

        self.assertAlmostEqual(point_after_close["equity"], 999.0)
        self.assertAlmostEqual(
            point_after_later_open["equity"],
            point_after_later_open["balance"],
            places=6,
            msg="卖单平仓后不应因为后续同品种价格更新继续残留浮盈亏",
        )

    def test_rebuild_curve_should_not_leave_position_ratio_when_same_timestamp_close_precedes_open(self):
        helper = getattr(server_v2, "_replay_curve_from_history", None)
        self.assertIsNotNone(helper, "缺少 _replay_curve_from_history，无法验证同时间戳生命周期顺序")

        deals = [
            {
                "timestamp": 1_000,
                "price": 110.0,
                "profit": 10.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 1,
                "deal_type": 1,
                "volume": 1.0,
                "symbol": "BTCUSD",
                "position_id": 9,
            },
            {
                "timestamp": 1_000,
                "price": 100.0,
                "profit": 0.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 0,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "BTCUSD",
                "position_id": 9,
            },
        ]

        points = helper(
            deal_history=deals,
            start_balance=1_000.0,
            open_positions=[],
            current_balance=1_010.0,
            current_equity=1_010.0,
            leverage=100.0,
            contract_size_fn=lambda symbol: 1.0,
            now_ms=2_000,
            fetch_rows_fn=lambda symbol, interval, limit, **kwargs: [],
        )

        self.assertAlmostEqual(0.0, points[-1]["positionRatio"], places=6)

    def test_rebuild_curve_inserts_history_samples_for_open_interval(self):
        helper = getattr(server_v2, "_replay_curve_from_history", None)
        self.assertIsNotNone(helper, "缺少 _replay_curve_from_history，无法验证持仓区间历史采样")

        deals = [
            {
                "timestamp": 1_000,
                "price": 100.0,
                "profit": 0.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 0,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "BTCUSDT",
                "position_id": 1,
            },
            {
                "timestamp": 4_000,
                "price": 104.0,
                "profit": 4.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 1,
                "deal_type": 1,
                "volume": 1.0,
                "symbol": "BTCUSDT",
                "position_id": 1,
            },
        ]

        points = helper(
            deal_history=deals,
            start_balance=1_000.0,
            open_positions=[],
            current_balance=1_004.0,
            current_equity=1_004.0,
            leverage=100.0,
            contract_size_fn=lambda symbol: 1.0,
            now_ms=5_000,
            fetch_rows_fn=lambda symbol, interval, limit, **kwargs: [
                [1_000, "100", "102", "99", "101", "0", 1_999, "0", 0],
                [2_000, "101", "106", "100", "105", "0", 2_999, "0", 0],
                [3_000, "105", "105", "102", "103", "0", 3_999, "0", 0],
            ],
        )

        sampled_points = [point for point in points if 1_000 < point["timestamp"] < 4_000]
        self.assertTrue(sampled_points, "持仓跨越历史区间时，曲线中间应插入历史价格采样点")
        self.assertTrue(
            any(abs(point["equity"] - point["balance"]) > 0.0 for point in sampled_points),
            "历史采样点应反映持仓浮盈亏，不能继续让净值等于结余",
        )

    def test_fetch_curve_price_samples_from_mt5_should_use_trade_symbol_and_normalize_server_timezone(self):
        original_mt5 = server_v2.mt5
        original_offset = getattr(server_v2, "MT5_TIME_OFFSET_MINUTES", 0)
        original_timezone = getattr(server_v2, "MT5_SERVER_TIMEZONE", "")

        captured = {}

        class _FakeMt5:
            TIMEFRAME_M1 = 1

            @staticmethod
            def symbol_select(symbol, enable):
                captured["symbol_select"] = (symbol, enable)
                return True

            @staticmethod
            def copy_rates_range(symbol, timeframe, date_from, date_to):
                captured["copy_rates_range"] = (symbol, timeframe, date_from, date_to)
                return [
                    {"time": 1775818380, "close": 101.5},
                    {"time": 1775818440, "close": 102.5},
                ]

        server_v2.mt5 = _FakeMt5()
        server_v2.MT5_TIME_OFFSET_MINUTES = 0
        server_v2.MT5_SERVER_TIMEZONE = "Europe/Athens"
        try:
            samples = server_v2._fetch_curve_price_samples("BTCUSD", 1775807600000, 1775807720000)
        finally:
            server_v2.mt5 = original_mt5
            server_v2.MT5_TIME_OFFSET_MINUTES = original_offset
            server_v2.MT5_SERVER_TIMEZONE = original_timezone

        self.assertEqual(("BTCUSD", True), captured["symbol_select"])
        self.assertEqual("BTCUSD", captured["copy_rates_range"][0])
        self.assertEqual(1, captured["copy_rates_range"][1])
        self.assertEqual(2, len(samples))
        self.assertEqual(datetime(2026, 4, 10, 10, 53, 20, tzinfo=timezone.utc), captured["copy_rates_range"][2])
        self.assertEqual(datetime(2026, 4, 10, 10, 55, 20, tzinfo=timezone.utc), captured["copy_rates_range"][3])
        self.assertEqual(1775807639999, samples[0]["timestamp"])
        self.assertEqual(101.5, samples[0]["price"])
        self.assertEqual("BTCUSD", samples[0]["symbol"])

    def test_fetch_curve_price_samples_from_mt5_should_normalize_server_timezone_wall_clock(self):
        original_mt5 = server_v2.mt5
        original_offset = getattr(server_v2, "MT5_TIME_OFFSET_MINUTES", 0)
        original_timezone = getattr(server_v2, "MT5_SERVER_TIMEZONE", "")

        captured = {}

        class _FakeMt5:
            TIMEFRAME_M1 = 1

            @staticmethod
            def symbol_select(symbol, enable):
                captured["symbol_select"] = (symbol, enable)
                return True

            @staticmethod
            def copy_rates_range(symbol, timeframe, date_from, date_to):
                captured["copy_rates_range"] = (symbol, timeframe, date_from, date_to)
                return [
                    {"time": 1775818380, "close": 101.5},
                    {"time": 1775818440, "close": 102.5},
                ]

        server_v2.mt5 = _FakeMt5()
        server_v2.MT5_TIME_OFFSET_MINUTES = 0
        server_v2.MT5_SERVER_TIMEZONE = "Europe/Athens"
        try:
            samples = server_v2._fetch_curve_price_samples("BTCUSD", 1775807600000, 1775807720000)
        finally:
            server_v2.mt5 = original_mt5
            server_v2.MT5_TIME_OFFSET_MINUTES = original_offset
            server_v2.MT5_SERVER_TIMEZONE = original_timezone

        self.assertEqual(("BTCUSD", True), captured["symbol_select"])
        self.assertEqual("BTCUSD", captured["copy_rates_range"][0])
        self.assertEqual(1, captured["copy_rates_range"][1])
        self.assertEqual(datetime(2026, 4, 10, 10, 53, 20, tzinfo=timezone.utc), captured["copy_rates_range"][2])
        self.assertEqual(datetime(2026, 4, 10, 10, 55, 20, tzinfo=timezone.utc), captured["copy_rates_range"][3])
        self.assertEqual(
            [
                {"timestamp": 1775807639999, "symbol": "BTCUSD", "price": 101.5},
                {"timestamp": 1775807699999, "symbol": "BTCUSD", "price": 102.5},
            ],
            samples,
        )

    def test_rebuild_curve_keeps_final_snapshot_point_in_non_decreasing_order(self):
        helper = getattr(server_v2, "_replay_curve_from_history", None)
        self.assertIsNotNone(helper, "缺少 _replay_curve_from_history，无法验证曲线时间顺序")

        deals = [
            {
                "timestamp": 2_000,
                "price": 100.0,
                "profit": 0.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 0,
                "deal_type": 0,
                "volume": 1.0,
                "symbol": "XAUUSD",
                "position_id": 1,
            },
            {
                "timestamp": 3_000,
                "price": 110.0,
                "profit": 10.0,
                "commission": 0.0,
                "swap": 0.0,
                "entry": 1,
                "deal_type": 1,
                "volume": 1.0,
                "symbol": "XAUUSD",
                "position_id": 1,
            },
        ]

        points = helper(
            deal_history=deals,
            start_balance=1_000.0,
            open_positions=[],
            current_balance=1_010.0,
            current_equity=1_010.0,
            leverage=100.0,
            contract_size_fn=lambda symbol: 1.0,
            now_ms=2_500,
            fetch_rows_fn=lambda symbol, interval, limit, **kwargs: [],
        )

        timestamps = [int(point["timestamp"]) for point in points]
        self.assertEqual(sorted(timestamps), timestamps)
        self.assertEqual(3_000, timestamps[-1])
        self.assertAlmostEqual(1_010.0, points[-1]["equity"])
        self.assertAlmostEqual(1_010.0, points[-1]["balance"])

    def test_health_should_return_cached_payload_when_recheck_throws(self):
        original_mt5 = server_v2.mt5
        original_cache = dict(server_v2.health_status_cache)
        original_cache_ms = getattr(server_v2, "HEALTH_CACHE_MS", 5000)
        original_now_ms = server_v2._now_ms
        original_mt5_last_connected_path = getattr(server_v2, "mt5_last_connected_path", "")

        class _HealthyMt5:
            @staticmethod
            def account_info():
                raise AssertionError("health should not call mt5.account_info()")

            @staticmethod
            def last_error():
                raise AssertionError("health should not call mt5.last_error()")

        class _BrokenMt5:
            @staticmethod
            def account_info():
                raise AssertionError("health should not call mt5.account_info()")

            @staticmethod
            def last_error():
                raise AssertionError("health should not call mt5.last_error()")

        try:
            server_v2.health_status_cache.clear()
            server_v2.HEALTH_CACHE_MS = 1
            values = iter([1_000, 1_005])
            server_v2._now_ms = lambda: next(values)
            server_v2.mt5_last_connected_path = "<auto>"

            server_v2.mt5 = _HealthyMt5()
            healthy = server_v2.health()

            server_v2.mt5 = _BrokenMt5()
            broken = server_v2.health()
        finally:
            server_v2.mt5 = original_mt5
            server_v2.health_status_cache.clear()
            server_v2.health_status_cache.update(original_cache)
            server_v2.HEALTH_CACHE_MS = original_cache_ms
            server_v2._now_ms = original_now_ms
            server_v2.mt5_last_connected_path = original_mt5_last_connected_path

        self.assertTrue(healthy["ok"])
        self.assertTrue(broken["ok"])
        self.assertTrue(healthy["mt5ProbeDeferred"])
        self.assertTrue(broken["mt5ProbeDeferred"])
        self.assertTrue(healthy["mt5Connected"])
        self.assertTrue(broken["mt5Connected"])
        self.assertIsInstance(healthy.get("bundleFingerprint"), str)
        self.assertTrue(healthy.get("bundleFingerprint"))
        self.assertIsInstance(healthy.get("bundleGeneratedAt"), str)
        self.assertTrue(healthy.get("bundleGeneratedAt"))

    def test_snapshot_from_mt5_light_should_expose_trade_count_without_building_full_history(self):
        original_mt5 = server_v2.mt5
        original_is_mt5_configured = server_v2._is_mt5_configured
        original_ensure_mt5 = server_v2._ensure_mt5
        original_shutdown_mt5 = server_v2._shutdown_mt5
        original_map_positions = server_v2._map_positions
        original_map_pending_orders = server_v2._map_pending_orders
        history_call = {"get": 0, "total": 0}

        class _Account:
            login = 7400048
            server = "ICMarketsSC-MT5-6"
            currency = "USD"
            leverage = 500
            balance = 1000.0
            equity = 1002.0
            margin = 10.0
            margin_free = 992.0
            margin_level = 10020.0
            profit = 2.0
            name = "demo"
            company = "demo"

        class _Mt5:
            @staticmethod
            def account_info():
                return _Account()

            @staticmethod
            def history_deals_total(*_args, **_kwargs):
                history_call["total"] += 1
                raise RuntimeError("history_deals_total should not be used")

            @staticmethod
            def history_deals_get(*_args, **_kwargs):
                history_call["get"] += 1
                return [
                    types.SimpleNamespace(
                        symbol="BTCUSD",
                        type=0,
                        volume=0.01,
                        ticket=1001,
                        order=2001,
                        position_id=3001,
                        entry=0,
                        time=1775225086,
                        time_msc=1775225086619,
                        price=66878.63,
                        profit=0.0,
                        commission=0.0,
                        swap=0.0,
                        comment="open",
                    ),
                    types.SimpleNamespace(
                        symbol="BTCUSD",
                        type=1,
                        volume=0.01,
                        ticket=1002,
                        order=2002,
                        position_id=3001,
                        entry=1,
                        time=1775225142,
                        time_msc=1775225142404,
                        price=66860.77,
                        profit=0.18,
                        commission=0.0,
                        swap=0.0,
                        comment="close",
                    ),
                ]

            @staticmethod
            def symbol_info(symbol):
                return types.SimpleNamespace(trade_contract_size=1.0)

        with _configured_mt5_server_timezone("UTC"):
            try:
                server_v2.mt5 = _Mt5()
                server_v2._is_mt5_configured = lambda: True
                server_v2._ensure_mt5 = lambda: None
                server_v2._shutdown_mt5 = lambda: None
                server_v2._map_positions = lambda: []
                server_v2._map_pending_orders = lambda: []

                snapshot = server_v2._snapshot_from_mt5_light()
            finally:
                server_v2.mt5 = original_mt5
                server_v2._is_mt5_configured = original_is_mt5_configured
                server_v2._ensure_mt5 = original_ensure_mt5
                server_v2._shutdown_mt5 = original_shutdown_mt5
                server_v2._map_positions = original_map_positions
                server_v2._map_pending_orders = original_map_pending_orders

        self.assertGreaterEqual(history_call["get"], 1)
        self.assertEqual(0, history_call["total"])
        self.assertEqual(1, snapshot["accountMeta"]["tradeCount"])

    def test_snapshot_from_mt5_should_reuse_single_progressive_deal_history_for_curve_and_trade_mapping(self):
        original_mt5 = server_v2.mt5
        original_is_mt5_configured = server_v2._is_mt5_configured
        original_ensure_mt5 = server_v2._ensure_mt5
        original_shutdown_mt5 = server_v2._shutdown_mt5
        original_map_positions = server_v2._map_positions
        original_map_pending_orders = server_v2._map_pending_orders
        original_progressive_history = server_v2._progressive_trade_history_deals
        original_build_curve_from_deals = getattr(server_v2, "_build_curve_from_deals", None)
        original_map_trade_deals = server_v2._map_trade_deals
        original_build_overview = server_v2._build_overview
        original_curve_indicators = server_v2._curve_indicators
        original_build_stats = server_v2._build_stats

        raw_deals = [types.SimpleNamespace(ticket=1)]
        captured_curve_inputs = []
        captured_trade_inputs = []

        class _Account:
            login = 7400048
            server = "ICMarketsSC-MT5-6"
            currency = "USD"
            leverage = 500
            balance = 1000.0
            equity = 1002.0
            margin = 10.0
            margin_free = 992.0
            margin_level = 10020.0
            profit = 2.0
            name = "demo"
            company = "demo"

        class _Mt5:
            @staticmethod
            def account_info():
                return _Account()

        def fake_build_curve_from_deals(deals, current_positions=None, account=None):
            captured_curve_inputs.append((deals, current_positions, account))
            return [{"timestamp": 1, "equity": 1002.0, "balance": 1000.0, "positionRatio": 0.0}]

        def fake_map_trade_deals(deals):
            captured_trade_inputs.append(deals)
            return [{"dealTicket": 11, "code": "BTCUSD"}]

        try:
            server_v2.mt5 = _Mt5()
            server_v2._is_mt5_configured = lambda: True
            server_v2._ensure_mt5 = lambda: None
            server_v2._shutdown_mt5 = lambda: None
            server_v2._map_positions = lambda: [{"code": "BTCUSD", "marketValue": 100.0, "totalPnL": 2.0, "dayPnL": 1.0, "positionRatio": 1.0}]
            server_v2._map_pending_orders = lambda: [{"code": "XAUUSD"}]
            server_v2._progressive_trade_history_deals = lambda range_key: raw_deals
            server_v2._build_curve_from_deals = fake_build_curve_from_deals
            server_v2._map_trade_deals = fake_map_trade_deals
            server_v2._build_overview = lambda positions, trades: [{"name": "总资产", "value": "$1000.00"}]
            server_v2._curve_indicators = lambda points: []
            server_v2._build_stats = lambda positions, trades, points: []

            snapshot = server_v2._snapshot_from_mt5("all")
        finally:
            server_v2.mt5 = original_mt5
            server_v2._is_mt5_configured = original_is_mt5_configured
            server_v2._ensure_mt5 = original_ensure_mt5
            server_v2._shutdown_mt5 = original_shutdown_mt5
            server_v2._map_positions = original_map_positions
            server_v2._map_pending_orders = original_map_pending_orders
            server_v2._progressive_trade_history_deals = original_progressive_history
            if original_build_curve_from_deals is None:
                delattr(server_v2, "_build_curve_from_deals")
            else:
                server_v2._build_curve_from_deals = original_build_curve_from_deals
            server_v2._map_trade_deals = original_map_trade_deals
            server_v2._build_overview = original_build_overview
            server_v2._curve_indicators = original_curve_indicators
            server_v2._build_stats = original_build_stats

        self.assertEqual(1, len(captured_curve_inputs))
        self.assertIs(raw_deals, captured_curve_inputs[0][0])
        self.assertEqual(1, len(captured_trade_inputs))
        self.assertIs(raw_deals, captured_trade_inputs[0])
        self.assertEqual(1, snapshot["accountMeta"]["tradeCount"])

    def test_build_account_light_snapshot_with_cache_should_single_flight_concurrent_miss(self):
        original_cache = dict(server_v2.snapshot_build_cache)
        original_now_ms = server_v2._now_ms
        original_builder = server_v2._build_account_light_snapshot

        started = Event()
        release = Event()
        build_calls = {"count": 0}
        results = []
        errors = []

        def fake_builder():
            build_calls["count"] += 1
            started.set()
            release.wait(2)
            return {
                "accountMeta": {
                    "login": "7400048",
                    "server": "ICMarketsSC-MT5-6",
                    "source": "MT5 Python Pull",
                }
            }

        def worker():
            try:
                results.append(server_v2._build_account_light_snapshot_with_cache())
            except Exception as exc:  # pragma: no cover
                errors.append(exc)

        try:
            server_v2.snapshot_build_cache.clear()
            server_v2._now_ms = lambda: 1_000
            server_v2._build_account_light_snapshot = fake_builder

            first = Thread(target=worker)
            second = Thread(target=worker)
            first.start()
            self.assertTrue(started.wait(1))
            second.start()
            release.set()
            first.join()
            second.join()
        finally:
            server_v2.snapshot_build_cache.clear()
            server_v2.snapshot_build_cache.update(original_cache)
            server_v2._now_ms = original_now_ms
            server_v2._build_account_light_snapshot = original_builder

        self.assertEqual([], errors)
        self.assertEqual(1, build_calls["count"])
        self.assertEqual(2, len(results))

    def test_build_account_light_snapshot_with_cache_should_drop_stale_result_after_session_epoch_changed(self):
        original_cache = dict(server_v2.snapshot_build_cache)
        original_builder = server_v2._build_account_light_snapshot
        original_epoch = int(getattr(server_v2, "session_snapshot_epoch", 0))

        build_calls = {"count": 0}

        def fake_builder():
            build_calls["count"] += 1
            if build_calls["count"] == 1:
                with server_v2.snapshot_cache_lock:
                    server_v2.session_snapshot_epoch += 1
                return {
                    "accountMeta": {"source": "MT5 Python Pull", "login": "old"},
                    "positions": [],
                    "pendingOrders": [],
                }
            return {
                "accountMeta": {"source": "MT5 Python Pull", "login": "new"},
                "positions": [],
                "pendingOrders": [],
            }

        try:
            server_v2.snapshot_build_cache.clear()
            server_v2._build_account_light_snapshot = fake_builder
            snapshot = server_v2._build_account_light_snapshot_with_cache()
        finally:
            server_v2.snapshot_build_cache.clear()
            server_v2.snapshot_build_cache.update(original_cache)
            server_v2._build_account_light_snapshot = original_builder
            server_v2.session_snapshot_epoch = original_epoch

        self.assertEqual(2, build_calls["count"])
        self.assertEqual("new", (snapshot.get("accountMeta") or {}).get("login"))

    def test_build_snapshot_with_cache_should_drop_stale_result_after_session_epoch_changed(self):
        original_cache = dict(server_v2.snapshot_build_cache)
        original_selector = server_v2._select_snapshot
        original_epoch = int(getattr(server_v2, "session_snapshot_epoch", 0))

        build_calls = {"count": 0}

        def fake_select_snapshot(range_key):
            self.assertEqual("all", range_key)
            build_calls["count"] += 1
            if build_calls["count"] == 1:
                with server_v2.snapshot_cache_lock:
                    server_v2.session_snapshot_epoch += 1
                return {
                    "accountMeta": {"source": "MT5 Python Pull", "login": "old"},
                    "overviewMetrics": [],
                    "curvePoints": [],
                    "curveIndicators": [],
                    "positions": [],
                    "pendingOrders": [],
                    "trades": [],
                    "statsMetrics": [],
                }
            return {
                "accountMeta": {"source": "MT5 Python Pull", "login": "new"},
                "overviewMetrics": [],
                "curvePoints": [],
                "curveIndicators": [],
                "positions": [],
                "pendingOrders": [],
                "trades": [],
                "statsMetrics": [],
            }

        try:
            server_v2.snapshot_build_cache.clear()
            server_v2._select_snapshot = fake_select_snapshot
            snapshot = server_v2._build_snapshot_with_cache("all")
        finally:
            server_v2.snapshot_build_cache.clear()
            server_v2.snapshot_build_cache.update(original_cache)
            server_v2._select_snapshot = original_selector
            server_v2.session_snapshot_epoch = original_epoch

        self.assertEqual(2, build_calls["count"])
        self.assertEqual("new", (snapshot.get("accountMeta") or {}).get("login"))

    def test_build_trade_history_with_cache_should_drop_stale_result_after_session_epoch_changed(self):
        original_cache = dict(server_v2.snapshot_build_cache)
        original_fetch_trades = server_v2._snapshot_trades_from_mt5
        original_epoch = int(getattr(server_v2, "session_snapshot_epoch", 0))
        original_mt5 = server_v2.mt5
        original_is_mt5_configured = server_v2._is_mt5_configured

        build_calls = {"count": 0}

        def fake_snapshot_trades_from_mt5(range_key):
            self.assertEqual("all", range_key)
            build_calls["count"] += 1
            if build_calls["count"] == 1:
                with server_v2.snapshot_cache_lock:
                    server_v2.session_snapshot_epoch += 1
                return [{"dealTicket": 1, "code": "BTCUSD"}]
            return [{"dealTicket": 2, "code": "XAUUSD"}]

        try:
            server_v2.snapshot_build_cache.clear()
            server_v2._snapshot_trades_from_mt5 = fake_snapshot_trades_from_mt5
            server_v2.mt5 = object()
            server_v2._is_mt5_configured = lambda: True
            trades = server_v2._build_trade_history_with_cache("all")
        finally:
            server_v2.snapshot_build_cache.clear()
            server_v2.snapshot_build_cache.update(original_cache)
            server_v2._snapshot_trades_from_mt5 = original_fetch_trades
            server_v2.session_snapshot_epoch = original_epoch
            server_v2.mt5 = original_mt5
            server_v2._is_mt5_configured = original_is_mt5_configured

        self.assertEqual(2, build_calls["count"])
        self.assertEqual([{"dealTicket": 2, "code": "XAUUSD"}], trades)

    def test_curve_point_digest_includes_position_ratio(self):
        helper = getattr(server_v2, "_normalize_digest_curve_points", None)
        self.assertIsNotNone(helper, "缺少 _normalize_digest_curve_points，无法验证曲线摘要")

        low_ratio = helper([{"timestamp": 1, "equity": 100.0, "balance": 100.0, "positionRatio": 0.10}])
        high_ratio = helper([{"timestamp": 1, "equity": 100.0, "balance": 100.0, "positionRatio": 0.60}])

        self.assertNotEqual(low_ratio, high_ratio)

    def test_select_snapshot_should_use_mt5_pull_even_when_ea_snapshot_is_fresh(self):
        original_mode = server_v2.GATEWAY_MODE
        original_is_fresh = server_v2._is_ea_snapshot_fresh
        original_snapshot_from_mt5 = server_v2._snapshot_from_mt5
        original_mt5 = server_v2.mt5
        original_is_mt5_configured = server_v2._is_mt5_configured
        original_ea_snapshot_cache = server_v2.ea_snapshot_cache

        server_v2.GATEWAY_MODE = "auto"
        server_v2.ea_snapshot_cache = {
            "accountMeta": {
                "source": "MT5 EA Push",
                "updatedAt": server_v2._now_ms(),
            }
        }
        server_v2._is_ea_snapshot_fresh = lambda: True
        server_v2.mt5 = object()
        server_v2._is_mt5_configured = lambda: True
        server_v2._snapshot_from_mt5 = lambda range_key: {
            "accountMeta": {"source": "MT5 Python Pull", "range": range_key},
            "positions": [],
            "pendingOrders": [],
            "trades": [],
            "curvePoints": [],
        }
        try:
            snapshot = server_v2._select_snapshot("all")
        finally:
            server_v2.GATEWAY_MODE = original_mode
            server_v2._is_ea_snapshot_fresh = original_is_fresh
            server_v2._snapshot_from_mt5 = original_snapshot_from_mt5
            server_v2.mt5 = original_mt5
            server_v2._is_mt5_configured = original_is_mt5_configured
            server_v2.ea_snapshot_cache = original_ea_snapshot_cache

        self.assertEqual("MT5 Python Pull", snapshot["accountMeta"]["source"])

    def test_build_account_light_snapshot_should_not_return_stale_ea_snapshot(self):
        original_mode = server_v2.GATEWAY_MODE
        original_is_fresh = server_v2._is_ea_snapshot_fresh
        original_snapshot_from_mt5_light = server_v2._snapshot_from_mt5_light
        original_mt5 = server_v2.mt5
        original_is_mt5_configured = server_v2._is_mt5_configured
        original_ea_snapshot_cache = server_v2.ea_snapshot_cache

        server_v2.GATEWAY_MODE = "auto"
        server_v2.ea_snapshot_cache = {
            "accountMeta": {
                "source": "MT5 EA Push",
                "updatedAt": server_v2._now_ms() - 999999,
            },
            "positions": [{"code": "BTCUSD"}],
            "pendingOrders": [],
        }
        server_v2._is_ea_snapshot_fresh = lambda: False
        server_v2.mt5 = None
        server_v2._is_mt5_configured = lambda: False
        server_v2._snapshot_from_mt5_light = lambda: {
            "accountMeta": {"source": "MT5 Python Pull"},
            "overviewMetrics": [{"name": "总资产", "value": "$1000.00"}],
            "curveIndicators": [],
            "statsMetrics": [],
            "positions": [],
            "pendingOrders": [],
        }
        try:
            snapshot = server_v2._build_account_light_snapshot()
        finally:
            server_v2.GATEWAY_MODE = original_mode
            server_v2._is_ea_snapshot_fresh = original_is_fresh
            server_v2._snapshot_from_mt5_light = original_snapshot_from_mt5_light
            server_v2.mt5 = original_mt5
            server_v2._is_mt5_configured = original_is_mt5_configured
            server_v2.ea_snapshot_cache = original_ea_snapshot_cache

        self.assertEqual("MT5 Python Pull", snapshot["accountMeta"]["source"])
        self.assertEqual([{"name": "总资产", "value": "$1000.00"}], snapshot["overviewMetrics"])


if __name__ == "__main__":
    unittest.main()
