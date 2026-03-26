# MT5 Bridge Gateway

This bridge provides realtime account data to the Android app through HTTP API.

It supports two modes:
- `pull`: Python directly reads MT5 terminal via `MetaTrader5` package.
- `ea`: MT5 EA pushes snapshot JSON to the gateway.
- `auto` (recommended): use fresh EA data first, fallback to pull.

## API

- `GET /health`
- `GET /v1/source`
- `GET /v1/snapshot?range=1d|7d|1m|3m|1y|all`
- `POST /v1/ea/snapshot`
- Full JSON schema: `API.md`

## 1) Configure

1. Copy `.env.example` to `.env`
2. Confirm these values:
   - `MT5_LOGIN=12345678`
   - `MT5_PASSWORD=your_investor_password`
   - `MT5_SERVER=ICMarketsSC-MT5-6`
3. Optional:
   - `MT5_PATH=C:\Program Files\MetaTrader 5\terminal64.exe`
   - `MT5_SERVER_ALIASES=ICMarketsSC-MT5,ICMarketsSC-MT5-5`
   - `MT5_INIT_TIMEOUT_MS=60000`
   - `GATEWAY_MODE=auto|pull|ea`
   - `EA_INGEST_TOKEN=...` (if you want EA push authentication)

## 2) Start Gateway

```powershell
cd bridge\mt5_gateway
.\start_gateway.ps1
```

Default address: `http://127.0.0.1:8787`

## 3) EA Push Mode (optional but recommended)

1. Open MT5 MetaEditor.
2. Import `bridge/mt5_gateway/ea/MT5BridgePushEA.mq5`.
3. Attach EA to any chart.
4. In MT5: `Tools -> Options -> Expert Advisors`, enable WebRequest URL:
   - `http://127.0.0.1:8787`
5. If `EA_INGEST_TOKEN` is configured in `.env`, set the same token in EA input parameter `BridgeToken`.

## 4) Android App Connectivity

- Emulator default already configured:
  - `http://10.0.2.2:8787`
- Physical device:
  - set `MT5_GATEWAY_BASE_URL` in `gradle.properties`
  - example: `MT5_GATEWAY_BASE_URL=https://mt5-api.example.com`
  - rebuild APK after changing the value.

## 5) Tencent Cloud deployment package

- See `deploy/tencent/README.md` for:
  - Windows CVM bootstrap scripts
  - startup task registration
  - Caddy/Nginx reverse proxy samples

## Notes

- This is read-only monitoring (investor password).
- Keep MT5 terminal and gateway alive for continuous refresh.
- Response already matches app fields:
  `accountMeta`, `overviewMetrics`, `curvePoints`, `curveIndicators`, `positions`, `trades`, `statsMetrics`.
